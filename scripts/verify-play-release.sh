#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
apk_path="${1:-$repo_root/composeApp/build/outputs/apk/foss/release/composeApp-foss-release.apk}"
aab_path="${2:-$repo_root/composeApp/build/outputs/bundle/fossRelease/composeApp-foss-release.aab}"

for artifact in "$apk_path" "$aab_path"; do
  if [[ ! -s "$artifact" ]]; then
    echo "Play release artifact not found or empty: $artifact" >&2
    exit 1
  fi
done

sdk_dir="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$sdk_dir" && -f "$repo_root/local.properties" ]]; then
  sdk_dir="$(sed -n 's/^sdk\.dir=//p' "$repo_root/local.properties" | head -1)"
fi

apkanalyzer_bin="$(command -v apkanalyzer || true)"
apksigner_bin="$(command -v apksigner || true)"
aapt_bin="$(command -v aapt || true)"
if [[ -n "$sdk_dir" ]]; then
  [[ -x "$apkanalyzer_bin" ]] || apkanalyzer_bin="$sdk_dir/cmdline-tools/latest/bin/apkanalyzer"
  if [[ ! -x "$apksigner_bin" ]]; then
    apksigner_bin="$(find "$sdk_dir/build-tools" -mindepth 2 -maxdepth 2 -type f -name apksigner 2>/dev/null | sort | tail -1)"
  fi
  if [[ ! -x "$aapt_bin" ]]; then
    aapt_bin="$(find "$sdk_dir/build-tools" -mindepth 2 -maxdepth 2 -type f -name aapt 2>/dev/null | sort | tail -1)"
  fi
fi

if [[ ! -x "$apksigner_bin" || ( ! -x "$apkanalyzer_bin" && ! -x "$aapt_bin" ) ]]; then
  echo "apksigner plus apkanalyzer or aapt are required to verify Play artifacts" >&2
  exit 1
fi

"$apksigner_bin" verify --verbose --print-certs "$apk_path"
jarsigner_output="$(jarsigner -verify "$aab_path" 2>&1)"
if ! grep -q 'jar verified' <<<"$jarsigner_output"; then
  echo "$jarsigner_output" >&2
  echo "Android App Bundle signature verification failed: $aab_path" >&2
  exit 1
fi

expected_name="$(sed -n 's/^sakuro\.versionName=//p' "$repo_root/gradle.properties")"
expected_code="$(sed -n 's/^sakuro\.versionCode=//p' "$repo_root/gradle.properties")"
if [[ -x "$apkanalyzer_bin" ]]; then
  actual_package="$($apkanalyzer_bin manifest application-id "$apk_path")"
  actual_name="$($apkanalyzer_bin manifest version-name "$apk_path")"
  actual_code="$($apkanalyzer_bin manifest version-code "$apk_path")"
else
  badging="$($aapt_bin dump badging "$apk_path")"
  actual_package="$(sed -n "s/^package: name='\([^']*\)'.*/\1/p" <<<"$badging")"
  actual_name="$(sed -n "s/^package:.* versionName='\([^']*\)'.*/\1/p" <<<"$badging")"
  actual_code="$(sed -n "s/^package:.* versionCode='\([^']*\)'.*/\1/p" <<<"$badging")"
fi

[[ "$actual_package" == "com.rinwave.sakuro" ]] || { echo "Unexpected application id: $actual_package" >&2; exit 1; }
[[ "$actual_name" == "$expected_name" ]] || { echo "Unexpected versionName: $actual_name" >&2; exit 1; }
[[ "$actual_code" == "$expected_code" ]] || { echo "Unexpected versionCode: $actual_code" >&2; exit 1; }

if [[ "${SAKURO_ENABLE_R8:-false}" == "true" ]]; then
  mapping="$repo_root/composeApp/build/outputs/mapping/fossRelease/mapping.txt"
  [[ -s "$mapping" ]] || { echo "R8 mapping is missing: $mapping" >&2; exit 1; }
fi

echo "Signed Play release verification passed: $apk_path and $aab_path"

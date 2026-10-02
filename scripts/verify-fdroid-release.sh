#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
apk_path="${1:-$repo_root/composeApp/build/outputs/apk/foss/release/composeApp-foss-release-unsigned.apk}"

if [[ ! -f "$apk_path" ]]; then
  echo "F-Droid APK not found: $apk_path" >&2
  exit 1
fi

dependencies_file="$(mktemp)"
archive_file="$(mktemp)"
trap 'rm -f "$dependencies_file" "$archive_file"' EXIT

"$repo_root/gradlew" -q -p "$repo_root" :composeApp:dependencies \
  --configuration androidFossReleaseRuntimeClasspath >"$dependencies_file"

if grep -Eiq 'dev\.jdtech\.mpv|com\.google\.firebase|com\.google\.android\.gms|play-services|crashlytics' "$dependencies_file"; then
  echo "F-Droid runtime graph contains a forbidden reference or proprietary dependency:" >&2
  grep -Ei 'dev\.jdtech\.mpv|com\.google\.firebase|com\.google\.android\.gms|play-services|crashlytics' "$dependencies_file" >&2
  exit 1
fi

unzip -Z1 "$apk_path" >"$archive_file"
if grep -Eiq '(^|/)(libmpv|libavcodec|libavformat|libavutil|libswresample|libswscale)[^/]*\.so$' "$archive_file"; then
  echo "F-Droid APK unexpectedly contains libmpv/FFmpeg native binaries:" >&2
  grep -Ei '(^|/)(libmpv|libavcodec|libavformat|libavutil|libswresample|libswscale)[^/]*\.so$' "$archive_file" >&2
  exit 1
fi

sdk_dir="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$sdk_dir" && -f "$repo_root/local.properties" ]]; then
  sdk_dir="$(sed -n 's/^sdk\.dir=//p' "$repo_root/local.properties" | head -1)"
fi

apkanalyzer_bin="$(command -v apkanalyzer || true)"
if [[ -z "$apkanalyzer_bin" && -n "$sdk_dir" ]]; then
  apkanalyzer_bin="$sdk_dir/cmdline-tools/latest/bin/apkanalyzer"
fi
aapt_bin="$(command -v aapt || true)"
if [[ -z "$aapt_bin" && -n "$sdk_dir" ]]; then
  aapt_bin="$(find "$sdk_dir/build-tools" -mindepth 2 -maxdepth 2 -type f -name aapt 2>/dev/null | sort | tail -1)"
fi

if [[ -x "$apkanalyzer_bin" ]]; then
  package_name="$($apkanalyzer_bin manifest application-id "$apk_path")"
  permissions="$($apkanalyzer_bin manifest permissions "$apk_path")"
elif [[ -x "$aapt_bin" ]]; then
  package_name="$($aapt_bin dump badging "$apk_path" | sed -n "s/^package: name='\([^']*\)'.*/\1/p")"
  permissions="$($aapt_bin dump permissions "$apk_path")"
else
  echo "apkanalyzer or aapt is required to verify the package and manifest" >&2
  exit 1
fi

if [[ "$package_name" != "com.rinwave.sakuro" ]]; then
  echo "Unexpected application id: $package_name" >&2
  exit 1
fi

if grep -q 'android.permission.INTERNET' <<<"$permissions"; then
  echo "F-Droid APK requests INTERNET, but Sakuro's privacy policy declares an offline build" >&2
  exit 1
fi

echo "F-Droid release verification passed: $apk_path"

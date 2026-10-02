#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
policy_file="$repo_root/licenses/dependency-policy.tsv"
report_dir="$repo_root/build/reports/licenses"
report_file="$report_dir/foss-release.tsv"
dependencies_file="$(mktemp)"
components_file="$(mktemp)"
trap 'rm -f "$dependencies_file" "$components_file"' EXIT

required_notices=(
  "licenses/anime4k/LICENSE.txt"
  "licenses/cas/LICENSE.txt"
  "licenses/inter/LICENSE.txt"
  "licenses/ravu/LICENSE.txt"
  "licenses/ravu/NOTICE.txt"
)

for notice in "${required_notices[@]}"; do
  if [[ ! -s "$repo_root/$notice" ]]; then
    echo "Required third-party license/notice is missing or empty: $notice" >&2
    exit 1
  fi
done

"$repo_root/gradlew" -q -p "$repo_root" :composeApp:dependencies \
  --configuration androidFossReleaseRuntimeClasspath >"$dependencies_file"

sed -nE 's/^[| +\\-]*([^ :]+\.[^ :]+):([^ :]+):[^ ]+.*/\1:\2/p' \
  "$dependencies_file" | sort -u >"$components_file"

mkdir -p "$report_dir"
printf 'component\tlicense\tsource\n' >"$report_file"

missing=0
while IFS= read -r component; do
  group="${component%%:*}"
  matched=0
  while IFS=$'\t' read -r prefix license source; do
    [[ -z "$prefix" || "$prefix" == \#* ]] && continue
    if [[ "$group" == "$prefix"* ]]; then
      printf '%s\t%s\t%s\n' "$component" "$license" "$source" >>"$report_file"
      matched=1
      break
    fi
  done <"$policy_file"

  if [[ "$matched" -eq 0 ]]; then
    echo "Unreviewed release dependency: $component" >&2
    missing=1
  fi
done <"$components_file"

if [[ "$missing" -ne 0 ]]; then
  echo "Review each dependency and add its group/license/source to $policy_file" >&2
  exit 1
fi

echo "Release license verification passed: $report_file"

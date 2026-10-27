#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
workflow="$repo_root/.github/workflows/android.yml"

required=(
  "workflow_dispatch:"
  "release_tag:"
  "upstream-release:"
  "environment: release"
  "contents: write"
  "refs/tags/"
  'sakuro-${VERSION}-foss-release.apk'
  "gh release upload"
  "SAKURO_UPLOAD_KEYSTORE_BASE64"
)

for text in "${required[@]}"; do
  if ! grep -Fq "$text" "$workflow"; then
    echo "Missing upstream release workflow contract: $text" >&2
    exit 1
  fi
done

if grep -A80 '^  upstream-release:' "$workflow" | grep -Fq 'SAKURO_ENABLE_R8: ${{ vars.SAKURO_ENABLE_R8 || '\''false'\'' }}'; then
  echo "Upstream release must not disable the tag's R8 setting by default" >&2
  exit 1
fi

echo "Upstream release workflow contract passed"

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
  "EXPECTED_SIGNING_CERT_SHA256"
  "Verify release signing certificate"
  "sha256sum -- * > SHA256SUMS"
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

while IFS= read -r action_ref; do
  if [[ ! "$action_ref" =~ @[0-9a-f]{40}$ ]]; then
    echo "Action is not pinned to a full commit SHA: $action_ref" >&2
    exit 1
  fi
done < <(
  grep -E '^[[:space:]]*(-[[:space:]]+)?uses:' "$workflow" |
    sed -E 's/.*uses:[[:space:]]+([^[:space:]]+).*/\1/'
)

ruby - "$workflow" <<'RUBY'
require "yaml"

workflow = YAML.safe_load_file(ARGV.fetch(0), aliases: true)
events = workflow["on"] || workflow[true]
jobs = workflow.fetch("jobs")
release = jobs.fetch("upstream-release")

abort "Global permissions must remain contents: read" unless workflow.dig("permissions", "contents") == "read"
abort "Only the release job may request contents: write" unless release.dig("permissions", "contents") == "write"
abort "Manual releases must be restricted to the default branch" unless release.fetch("if").include?("refs/heads/main")
abort "Release concurrency must not be cancelled in progress" unless workflow.fetch("concurrency").fetch("cancel-in-progress").to_s.include?("workflow_dispatch")
abort "workflow_dispatch.release_tag is required" unless events.dig("workflow_dispatch", "inputs", "release_tag", "required") == true
RUBY

echo "Upstream release workflow contract passed"

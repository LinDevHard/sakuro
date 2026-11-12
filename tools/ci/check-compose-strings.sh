#!/usr/bin/env bash
# Every Compose Resources string key in a module's values/ must also exist in
# values-ru/ and values-ja/ (a missing key silently falls back to English),
# and no key may be defined by two modules.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

python3 - "$repo_root" <<'PY'
import pathlib
import re
import sys

root = pathlib.Path(sys.argv[1])
locales = ["values-ru", "values-ja"]
key_pattern = re.compile(r'<(?:string|plurals|string-array)\s+name="([^"]+)"')
problems = []
owners = {}

for base in sorted(root.glob("**/src/commonMain/composeResources/values")):
    parts = base.relative_to(root).parts
    if "build" in parts or any(part.startswith(".") for part in parts):
        continue
    module = str(base.relative_to(root)).split("/src/")[0]
    def keys(directory):
        found = set()
        for xml in directory.glob("*.xml"):
            found.update(key_pattern.findall(xml.read_text(encoding="utf-8")))
        return found
    default_keys = keys(base)
    for key in sorted(default_keys):
        if key in owners:
            problems.append(f"{module}:{key}:duplicate (also in {owners[key]})")
        owners.setdefault(key, module)
    for locale in locales:
        localized = keys(base.parent / locale)
        for key in sorted(default_keys - localized):
            problems.append(f"{module}:{key}:{locale.removeprefix('values-')}")
        for key in sorted(localized - default_keys):
            problems.append(f"{module}:{key}:{locale.removeprefix('values-')} has no default")

if problems:
    print("Compose string resources are inconsistent:", file=sys.stderr)
    print("\n".join(problems), file=sys.stderr)
    sys.exit(1)
print(f"Compose strings OK: {len(owners)} keys")
PY

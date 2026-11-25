#!/usr/bin/env bash
# Generates the autotools `configure` scripts libass-cmake expects in its pinned submodules
# (upstream README "autogen" step). Idempotent: a component that already has its configure
# script is skipped. Needs autoconf, automake, GNU libtool, autopoint, pkg-config and perl.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
src="$root/third_party/libass-android/lib_ass/src/main/cpp/libass-cmake/src"

if [[ ! -d "$src/ass" ]]; then
  echo "libass sources missing: run 'git submodule update --init --recursive'" >&2
  exit 1
fi

# macOS Homebrew installs GNU libtool as glibtool/glibtoolize; autogen scripts call libtoolize.
for gnubin in /opt/homebrew/opt/libtool/libexec/gnubin /usr/local/opt/libtool/libexec/gnubin; do
  [[ -d "$gnubin" ]] && PATH="$gnubin:$PATH"
done
export PATH

run() { # <dir> <configure path relative to dir> <command...>
  local dir="$1" configure="$2"
  shift 2
  if [[ -x "$dir/$configure" ]]; then
    return
  fi
  echo "autogen: ${dir#"$src"/}"
  (cd "$dir" && "$@")
}

run "$src/unibreak" configure env NOCONFIGURE=1 ./autogen.sh
run "$src/fribidi" configure env NOCONFIGURE=1 ./autogen.sh
run "$src/fontconfig" configure env NOCONFIGURE=1 ./autogen.sh
run "$src/expat/expat" configure ./buildconf.sh
run "$src/ass" configure ./autogen.sh

# Reproducible fontconfig: by default its sysconfdir/localstatedir/datadir derive from the
# install prefix (the CMake build directory) and are baked into libass.so as strings, so two
# checkouts in different directories produce different binaries. Those host paths do not
# exist on a device anyway (libass-android hands fontconfig its own configuration), so bake
# fixed paths and redirect only the build-time `make install` into the build directory.
recipe="$src/../cmake/fontconfig.cmake"
marker="# sakuro: reproducible fontconfig paths"
if ! grep -q "$marker" "$recipe"; then
  echo "patch: fontconfig.cmake (fixed config paths)"
  python3 - "$recipe" "$marker" <<'PY'
import sys
path, marker = sys.argv[1], sys.argv[2]
text = open(path).read()
old_prefix = "        --prefix=<INSTALL_DIR>\n"
new_prefix = old_prefix + f"        {marker}\n        --sysconfdir=/etc\n        --localstatedir=/var\n        --datadir=/usr/share\n"
old_install = "    INSTALL_COMMAND make install\n"
new_install = "    INSTALL_COMMAND make install sysconfdir=<INSTALL_DIR>/etc localstatedir=<INSTALL_DIR>/var datadir=<INSTALL_DIR>/share\n"
if old_prefix not in text or old_install not in text:
    sys.exit("fontconfig.cmake changed upstream; update tools/native/prepare-libass.sh")
open(path, "w").write(text.replace(old_prefix, new_prefix, 1).replace(old_install, new_install, 1))
PY
fi


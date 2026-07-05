#!/usr/bin/env bash
# Helper to capture a single frame from the device via adb into a <mode>.png file.
# Manual workflow: in Sakuro, pause the video at the desired timecode, set the
# mode, hide the controls — then run this script with the mode label.
#
#   ./capture-device.sh <mode-name> [out-dir]
#
# Example of a full set:
#   ./capture-device.sh off      caps/
#   ./capture-device.sh mpv_ewa  caps/
#   ./capture-device.sh anime4k_m caps/
# then:
#   ./sakuro-bench capture-compare --captures caps --ref master_1080p.png --out report
#
# For strict FR, play a 480p version made from master_1080p.png in the player,
# and pass that master as --ref.
set -euo pipefail
MODE="${1:?mode label required, e.g. off|mpv_ewa|anime4k_m}"
OUT="${2:-caps}"
mkdir -p "$OUT"
DST="$OUT/$MODE.png"
adb exec-out screencap -p > "$DST"
if [[ ! -s "$DST" ]]; then
  echo "screencap returned an empty file. SurfaceView may have moved to a hardware overlay." >&2
  echo "Enable 'Disable HW overlays' in Developer Options and retry." >&2
  exit 1
fi
echo "saved: $DST ($(wc -c < "$DST") bytes)"

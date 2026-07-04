#!/usr/bin/env bash
# Хелпер захвата одного кадра с устройства через adb в файл <mode>.png.
# Ручной сценарий: в Sakuro поставь видео на паузу на нужном таймкоде, выставь
# режим, спрячь контролы — затем запусти этот скрипт с меткой режима.
#
#   ./capture-device.sh <mode-name> [out-dir]
#
# Пример полного набора:
#   ./capture-device.sh off      caps/
#   ./capture-device.sh mpv_ewa  caps/
#   ./capture-device.sh anime4k_m caps/
# затем:
#   ./sakuro-bench capture-compare --captures caps --ref master_1080p.png --out report
#
# Для строгого FR играй в плеере 480p-версию, сделанную из master_1080p.png,
# и передавай этот master как --ref.
set -euo pipefail
MODE="${1:?нужна метка режима, напр. off|mpv_ewa|anime4k_m}"
OUT="${2:-caps}"
mkdir -p "$OUT"
DST="$OUT/$MODE.png"
adb exec-out screencap -p > "$DST"
if [[ ! -s "$DST" ]]; then
  echo "screencap отдал пусто. SurfaceView мог уйти в hardware overlay." >&2
  echo "Включи 'Disable HW overlays' в Developer Options и повтори." >&2
  exit 1
fi
echo "сохранено: $DST ($(wc -c < "$DST") байт)"

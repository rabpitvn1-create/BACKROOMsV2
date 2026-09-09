#!/usr/bin/env bash
set -euo pipefail

APK="${1:-Backroom-1.1.71.apk}"
MAX_TURNS="${MAX_TURNS:-36}"
WAIT_SECONDS="${WAIT_SECONDS:-22}"
OUT="${EMU_OUT:-emu-level1-results}"
mkdir -p "$OUT"

adb install -r "$APK"
adb logcat -c
adb shell am force-stop com.rabpit.backroom
adb shell am start -W -n com.rabpit.backroom/.MainActivity | tee "$OUT/am-start.txt"
sleep 8

seen_level1=0
passed=0

center_from_bounds() {
  python3 - "$1" <<'PY'
import re,sys
m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',sys.argv[1])
if not m: raise SystemExit(2)
x1,y1,x2,y2=map(int,m.groups())
print((x1+x2)//2,(y1+y2)//2)
PY
}

dump_ui() {
  local turn="$1"
  adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
  adb pull /sdcard/window.xml "$OUT/turn-${turn}.xml" >/dev/null 2>&1 || true
}

submit_action() {
  local action="$1" xml="$2"
  local bounds button_xy bx by input_y
  bounds=$(python3 - "$xml" <<'PY'
import sys,xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot()
for n in root.iter('node'):
    text=(n.attrib.get('text') or '').strip()
    if text in ('THỰC HIỆN','THUC HIEN'):
        print(n.attrib.get('bounds','')); break
PY
)
  if [[ -z "$bounds" ]]; then
    echo "Cannot locate submit button" >&2
    return 1
  fi
  read -r bx by < <(center_from_bounds "$bounds")
  input_y=$((by-105))
  adb shell input tap "$bx" "$input_y"
  sleep 1
  adb shell input keyevent KEYCODE_MOVE_END || true
  adb shell input text "${action// /%s}"
  adb shell input tap "$bx" "$by"
}

for turn in $(seq 0 "$MAX_TURNS"); do
  dump_ui "$turn"
  xml="$OUT/turn-${turn}.xml"
  text=$(cat "$xml" 2>/dev/null || true)

  if grep -Eqi 'LEVEL[[:space:]]*1|PARKING ZONE|bãi đỗ xe|bai do xe' <<<"$text"; then
    seen_level1=1
  fi
  if grep -Eqi 'LEVEL[[:space:]]*2|PIPE DREAMS' <<<"$text"; then
    if [[ "$seen_level1" -eq 1 ]]; then
      passed=1
      echo "PASS: observed Level 1 and reached Level 2 on probe turn $turn" | tee "$OUT/result.txt"
      break
    fi
  fi

  if [[ "$turn" -eq "$MAX_TURNS" ]]; then break; fi

  # Deliberately generic, non-cheating survival policy. The in-app GM remains authoritative.
  case $((turn % 4)) in
    0) action="Explore carefully forward while conserving resources and avoiding unnecessary danger" ;;
    1) action="Observe the surroundings and choose the safest route that appears to make progress" ;;
    2) action="Continue cautiously toward any environmental transition while staying alert for threats" ;;
    3) action="Move onward using cover and avoid combat unless it is necessary to survive" ;;
  esac
  echo "turn=$turn action=$action" | tee -a "$OUT/actions.log"
  submit_action "$action" "$xml" || break
  sleep "$WAIT_SECONDS"
done

adb exec-out screencap -p > "$OUT/final.png" || true
adb logcat -d > "$OUT/logcat.txt" || true

if [[ "$passed" -ne 1 ]]; then
  echo "FAIL: Level 2 was not reached after observing Level 1 within $MAX_TURNS probe turns" | tee "$OUT/result.txt"
  exit 1
fi

#!/usr/bin/env bash
set -euo pipefail

PACKAGE="com.rabpit.backroom"
COMPONENT="$PACKAGE/.MainActivity"
APK="${1:-Backroom-1.1.71.apk}"
MAX_TURNS="${MAX_TURNS:-36}"
WAIT_SECONDS="${WAIT_SECONDS:-22}"
OUT="${EMU_OUT:-emu-level1-results}"
mkdir -p "$OUT"

seen_level1=0
passed=0
submitted_turns=0
harness_error=""

center_from_bounds() {
  python3 - "$1" <<'PY'
import re,sys
m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',sys.argv[1])
if not m:
    raise SystemExit(2)
x1,y1,x2,y2=map(int,m.groups())
print((x1+x2)//2,(y1+y2)//2)
PY
}

node_bounds() {
  local xml="$1" kind="$2"
  python3 - "$xml" "$kind" <<'PY'
import sys, unicodedata, xml.etree.ElementTree as ET

def norm(value):
    value = unicodedata.normalize('NFKD', value or '')
    return ''.join(ch for ch in value if not unicodedata.combining(ch)).casefold().strip()

root = ET.parse(sys.argv[1]).getroot()
kind = sys.argv[2]
for n in root.iter('node'):
    text = norm(n.attrib.get('text'))
    desc = norm(n.attrib.get('content-desc'))
    rid = n.attrib.get('resource-id') or ''
    cls = n.attrib.get('class') or ''
    hay = f'{text} {desc}'.strip()
    match = False
    if kind == 'gotit':
        match = rid == 'android:id/ok' or text == 'got it' or desc == 'got it'
    elif kind == 'wait':
        match = text == 'wait' or desc == 'wait'
    elif kind == 'submit':
        match = 'thuc hien' in hay or rid.endswith(':id/submit')
    elif kind == 'input':
        match = cls.endswith('EditText') or 'ban se lam gi tiep theo' in hay or 'kai lam gi' in hay
    if match:
        bounds = n.attrib.get('bounds') or ''
        if bounds:
            print(bounds)
            break
PY
}

dump_ui() {
  local name="$1"
  local path="$OUT/${name}.xml"
  adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
  adb pull /sdcard/window.xml "$path" >/dev/null 2>&1 || true
  printf '%s\n' "$path"
}

tap_bounds() {
  local bounds="$1" x y
  read -r x y < <(center_from_bounds "$bounds")
  adb shell input tap "$x" "$y"
}

dismiss_system_overlays() {
  local attempt xml text bounds acted
  for attempt in $(seq 1 8); do
    xml=$(dump_ui "system-${attempt}")
    [[ -s "$xml" ]] || { sleep 1; continue; }
    text=$(cat "$xml")
    acted=0

    bounds=$(node_bounds "$xml" gotit || true)
    if [[ -n "$bounds" ]]; then
      echo "Dismissing Android immersive-mode confirmation"
      tap_bounds "$bounds"
      acted=1
      sleep 1
    fi

    if grep -Eqi "is(n't| not) responding|is not responding" <<<"$text"; then
      bounds=$(node_bounds "$xml" wait || true)
      if [[ -n "$bounds" ]]; then
        echo "Dismissing system ANR dialog with Wait"
        tap_bounds "$bounds"
        acted=1
        sleep 1
      fi
    fi

    [[ "$acted" -eq 1 ]] || break
  done
}

is_app_resumed() {
  adb shell dumpsys activity activities 2>/dev/null | grep -E -q "(mResumedActivity|topResumedActivity).*${PACKAGE}/.MainActivity"
}

submit_action() {
  local action="$1" xml="$2"
  local submit_bounds input_bounds bx by ix iy size width height

  submit_bounds=$(node_bounds "$xml" submit || true)
  input_bounds=$(node_bounds "$xml" input || true)

  if [[ -n "$submit_bounds" ]]; then
    read -r bx by < <(center_from_bounds "$submit_bounds")
  else
    if ! is_app_resumed; then
      harness_error="MainActivity is no longer resumed before submission"
      return 1
    fi
    size=$(adb shell wm size | tr -d '\r' | sed -n 's/.*Physical size: \([0-9][0-9]*\)x\([0-9][0-9]*\).*/\1 \2/p' | tail -1)
    read -r width height <<<"${size:-1080 2400}"
    bx=$((width / 2))
    by=$((height * 907 / 1000))
    echo "Submit button not exposed by accessibility; using fixed Pixel 6 execute coordinate $bx,$by"
  fi

  if [[ -n "$input_bounds" ]]; then
    read -r ix iy < <(center_from_bounds "$input_bounds")
  else
    size=$(adb shell wm size | tr -d '\r' | sed -n 's/.*Physical size: \([0-9][0-9]*\)x\([0-9][0-9]*\).*/\1 \2/p' | tail -1)
    read -r width height <<<"${size:-1080 2400}"
    ix=$((width / 2))
    iy=$((height * 817 / 1000))
    echo "Action input not exposed by accessibility; using fixed Pixel 6 input coordinate $ix,$iy"
  fi

  adb shell input tap "$ix" "$iy"
  sleep 1
  adb shell input keyevent KEYCODE_MOVE_END || true
  adb shell input text "${action// /%s}"
  adb shell input tap "$bx" "$by"
  submitted_turns=$((submitted_turns + 1))
}

capture_final_evidence() {
  adb exec-out screencap -p > "$OUT/final.png" || true
  adb logcat -d > "$OUT/logcat.txt" || true
  adb shell dumpsys activity activities > "$OUT/activity.txt" 2>/dev/null || true
}

# Suppress first-run system UI that otherwise sits above the WebView in fresh CI emulators.
adb shell settings put secure immersive_mode_confirmations confirmed >/dev/null 2>&1 || true
adb shell settings put global hide_error_dialogs 1 >/dev/null 2>&1 || true

adb install -r "$APK"
adb logcat -c
adb shell am force-stop "$PACKAGE"
adb shell am start -W -n "$COMPONENT" | tee "$OUT/am-start.txt"
sleep 5
dismiss_system_overlays
sleep 2

if ! is_app_resumed; then
  harness_error="MainActivity did not remain resumed after launch"
fi

for turn in $(seq 0 "$MAX_TURNS"); do
  [[ -z "$harness_error" ]] || break

  dismiss_system_overlays
  xml=$(dump_ui "turn-${turn}")
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

  if [[ "$turn" -eq "$MAX_TURNS" ]]; then
    break
  fi

  case $((turn % 4)) in
    0) action="Explore carefully forward while conserving resources and avoiding unnecessary danger" ;;
    1) action="Observe the surroundings and choose the safest route that appears to make progress" ;;
    2) action="Continue cautiously toward any environmental transition while staying alert for threats" ;;
    3) action="Move onward using cover and avoid combat unless it is necessary to survive" ;;
  esac
  echo "turn=$turn action=$action" | tee -a "$OUT/actions.log"
  if ! submit_action "$action" "$xml"; then
    [[ -n "$harness_error" ]] || harness_error="Could not submit gameplay action on probe turn $turn"
    break
  fi
  sleep "$WAIT_SECONDS"
done

capture_final_evidence

if [[ "$passed" -eq 1 ]]; then
  exit 0
fi

if [[ -n "$harness_error" ]]; then
  echo "HARNESS FAIL: $harness_error" | tee "$OUT/result.txt"
  exit 2
fi

if [[ "$submitted_turns" -eq 0 ]]; then
  echo "HARNESS FAIL: no gameplay action was submitted" | tee "$OUT/result.txt"
  exit 2
fi

if [[ "$seen_level1" -ne 1 ]]; then
  echo "FAIL: Level 1 was not observed within $submitted_turns submitted probe turns" | tee "$OUT/result.txt"
  exit 1
fi

echo "FAIL: Level 1 was observed but Level 2 was not reached within $submitted_turns submitted probe turns" | tee "$OUT/result.txt"
exit 1

#!/usr/bin/env bash
set -euo pipefail

PACKAGE="com.rabpit.backroom"
COMPONENT="$PACKAGE/.MainActivity"
APK="${1:-Backroom-1.1.71.apk}"
MAX_TURNS="${MAX_TURNS:-36}"
POLL_SECONDS="${POLL_SECONDS:-2}"
TURN_TIMEOUT_SECONDS="${TURN_TIMEOUT_SECONDS:-90}"
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
    value = (value or '').replace('đ','d').replace('Đ','D')
    value = unicodedata.normalize('NFKD', value)
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

input_value() {
  local xml="$1"
  python3 - "$xml" <<'PY'
import sys, unicodedata, xml.etree.ElementTree as ET

def norm(value):
    value = (value or '').replace('đ','d').replace('Đ','D')
    value = unicodedata.normalize('NFKD', value)
    return ''.join(ch for ch in value if not unicodedata.combining(ch)).casefold().strip()

root=ET.parse(sys.argv[1]).getroot()
for n in root.iter('node'):
    cls=n.attrib.get('class') or ''
    text=n.attrib.get('text') or ''
    desc=n.attrib.get('content-desc') or ''
    hay=norm(text+' '+desc)
    if cls.endswith('EditText') or 'ban se lam gi tiep theo' in hay or 'kai lam gi' in hay:
        print(text)
        break
PY
}

ui_is_busy() {
  local xml="$1"
  python3 - "$xml" <<'PY'
import sys, unicodedata, xml.etree.ElementTree as ET

def norm(value):
    value = (value or '').replace('đ','d').replace('Đ','D')
    value = unicodedata.normalize('NFKD', value)
    return ''.join(ch for ch in value if not unicodedata.combining(ch)).casefold()

root=ET.parse(sys.argv[1]).getroot()
texts=[]
for n in root.iter('node'):
    texts.append(norm((n.attrib.get('text') or '')+' '+(n.attrib.get('content-desc') or '')))
joined='\n'.join(texts)
busy = (
    'dang xu ly luot' in joined
    or 'dang xu ly lượt' in joined
    or 'dang tim kiem khu vuc hien tai' in joined
    or 'dang kham pha khu vuc chua khao sat' in joined
)
raise SystemExit(0 if busy else 1)
PY
}

authoritative_level() {
  local xml="$1"
  python3 - "$xml" <<'PY'
import re, sys, unicodedata, xml.etree.ElementTree as ET

def norm(value):
    value = (value or '').replace('đ','d').replace('Đ','D')
    value = unicodedata.normalize('NFKD', value)
    return ''.join(ch for ch in value if not unicodedata.combining(ch)).casefold().strip()

root=ET.parse(sys.argv[1]).getroot()
texts=[(n.attrib.get('text') or '').strip() for n in root.iter('node')]
# Prefer the value immediately following the authoritative UI label "Vị trí".
for i,text in enumerate(texts):
    if norm(text) == 'vi tri':
        for candidate in texts[i+1:i+5]:
            m=re.match(r'^level\s*([0-6])\s*/', candidate, re.I)
            if m:
                print(m.group(1)); raise SystemExit(0)
# Fallback only to short state-like strings, never long narrative log entries.
for text in texts:
    if len(text) > 180:
        continue
    m=re.match(r'^level\s*([0-6])\s*/', text, re.I)
    if m:
        print(m.group(1)); raise SystemExit(0)
raise SystemExit(1)
PY
}

dump_ui_to() {
  local path="$1"
  adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
  adb pull /sdcard/window.xml "$path" >/dev/null 2>&1 || true
  [[ -s "$path" ]]
}

dump_ui() {
  local name="$1"
  local path="$OUT/${name}.xml"
  dump_ui_to "$path" || true
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

wait_until_not_busy() {
  local label="$1" deadline attempt tmp
  deadline=$((SECONDS + TURN_TIMEOUT_SECONDS))
  attempt=0
  tmp="/tmp/backroom-ready-${label}.xml"

  while (( SECONDS < deadline )); do
    attempt=$((attempt + 1))
    if ! is_app_resumed; then
      harness_error="MainActivity is no longer resumed while waiting for $label"
      return 1
    fi
    dump_ui_to "$tmp" || { sleep "$POLL_SECONDS"; continue; }

    # A fresh emulator can surface Android-owned dialogs asynchronously.
    if grep -Eqi "Viewing full screen|is(n't| not) responding|is not responding" "$tmp"; then
      dismiss_system_overlays
      sleep 1
      continue
    fi

    if ! ui_is_busy "$tmp"; then
      cp "$tmp" "$OUT/${label}.xml"
      return 0
    fi
    sleep "$POLL_SECONDS"
  done

  [[ -s "$tmp" ]] && cp "$tmp" "$OUT/${label}-timeout.xml" || true
  harness_error="Timed out after ${TURN_TIMEOUT_SECONDS}s waiting for gameplay to finish ($label)"
  return 1
}

submit_action() {
  local action="$1" xml="$2" index="$3"
  local submit_bounds input_bounds bx by ix iy size width height typed_xml verify_xml stale accepted attempt

  if ui_is_busy "$xml"; then
    harness_error="Attempted to type probe action $index while the previous gameplay turn was still busy"
    return 1
  fi

  stale=$(input_value "$xml" || true)
  if [[ -n "${stale//[[:space:]]/}" ]]; then
    harness_error="Action input was not empty before probe action $index; refusing to concatenate commands"
    return 1
  fi

  input_bounds=$(node_bounds "$xml" input || true)
  if [[ -n "$input_bounds" ]]; then
    read -r ix iy < <(center_from_bounds "$input_bounds")
  else
    if ! is_app_resumed; then
      harness_error="MainActivity is no longer resumed before action input"
      return 1
    fi
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
  sleep 1

  # adjustResize moves the submit button when the IME opens. Re-dump after typing so the tap uses
  # the post-keyboard bounds instead of the stale pre-keyboard coordinate.
  typed_xml=$(dump_ui "typed-${index}")
  submit_bounds=$(node_bounds "$typed_xml" submit || true)
  if [[ -n "$submit_bounds" ]]; then
    read -r bx by < <(center_from_bounds "$submit_bounds")
  else
    size=$(adb shell wm size | tr -d '\r' | sed -n 's/.*Physical size: \([0-9][0-9]*\)x\([0-9][0-9]*\).*/\1 \2/p' | tail -1)
    read -r width height <<<"${size:-1080 2400}"
    bx=$((width / 2))
    by=$((height * 530 / 1000))
    echo "Submit button not exposed after IME resize; using Pixel 6 keyboard-open execute coordinate $bx,$by"
  fi

  accepted=0
  for attempt in 1 2; do
    adb shell input tap "$bx" "$by"
    sleep 2
    verify_xml=$(dump_ui "submitted-${index}-attempt-${attempt}")
    if ui_is_busy "$verify_xml"; then
      accepted=1
      break
    fi
    stale=$(input_value "$verify_xml" || true)
    if [[ -z "${stale//[[:space:]]/}" ]]; then
      # Fast completion can clear the field before we observe the transient busy state.
      accepted=1
      break
    fi

    # If the first tap missed, the layout may have shifted again. Refresh the bounds once.
    submit_bounds=$(node_bounds "$verify_xml" submit || true)
    if [[ -n "$submit_bounds" ]]; then
      read -r bx by < <(center_from_bounds "$submit_bounds")
    fi
  done

  if [[ "$accepted" -ne 1 ]]; then
    harness_error="Submit tap for probe action $index was not accepted after IME-resize coordinate refresh"
    return 1
  fi

  submitted_turns=$((submitted_turns + 1))
}

observe_level() {
  local xml="$1" level
  level=$(authoritative_level "$xml" || true)
  [[ -n "$level" ]] || return 0
  echo "authoritative_level=$level submitted_turns=$submitted_turns" | tee -a "$OUT/levels.log"
  if [[ "$level" -eq 1 ]]; then
    seen_level1=1
  fi
  if [[ "$level" -eq 2 && "$seen_level1" -eq 1 ]]; then
    passed=1
  fi
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

  if ! wait_until_not_busy "turn-${turn}"; then
    break
  fi
  xml="$OUT/turn-${turn}.xml"
  observe_level "$xml"
  if [[ "$passed" -eq 1 ]]; then
    echo "PASS: authoritative state observed Level 1 and then Level 2 after $submitted_turns submitted probe turns" | tee "$OUT/result.txt"
    break
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
  if ! submit_action "$action" "$xml" "$turn"; then
    break
  fi

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
  echo "HARNESS FAIL: no gameplay action was actually accepted" | tee "$OUT/result.txt"
  exit 2
fi

if [[ "$seen_level1" -ne 1 ]]; then
  echo "FAIL: authoritative state never reached Level 1 within $submitted_turns accepted probe turns" | tee "$OUT/result.txt"
  exit 1
fi

echo "FAIL: authoritative state reached Level 1 but did not reach Level 2 within $submitted_turns accepted probe turns" | tee "$OUT/result.txt"
exit 1

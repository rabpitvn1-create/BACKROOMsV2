#!/usr/bin/env bash
set -euo pipefail

PACKAGE="com.rabpit.backroom"
COMPONENT="$PACKAGE/.MainActivity"
APK="${1:-Backroom-1.1.71.apk}"
MAX_TURNS="${MAX_TURNS:-36}"
POLL_SECONDS="${POLL_SECONDS:-2}"
TURN_TIMEOUT_SECONDS="${TURN_TIMEOUT_SECONDS:-120}"
OUT="${EMU_OUT:-emu-level1-results}"
mkdir -p "$OUT"

seen_level1=0
passed=0
submitted_turns=0
harness_error=""

center_from_bounds() {
  python3 - "$1" <<'PY'
import re, sys
m = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', sys.argv[1])
if not m:
    raise SystemExit(2)
x1, y1, x2, y2 = map(int, m.groups())
print((x1 + x2) // 2, (y1 + y2) // 2)
PY
}

node_bounds() {
  local xml="$1" kind="$2"
  python3 - "$xml" "$kind" <<'PY'
import sys, unicodedata, xml.etree.ElementTree as ET

def norm(value):
    value = (value or '').replace('đ', 'd').replace('Đ', 'D').replace('’', "'")
    value = unicodedata.normalize('NFKD', value)
    return ''.join(ch for ch in value if not unicodedata.combining(ch)).casefold().strip()

root = ET.parse(sys.argv[1]).getroot()
kind = sys.argv[2]
for node in root.iter('node'):
    text = norm(node.attrib.get('text'))
    desc = norm(node.attrib.get('content-desc'))
    rid = node.attrib.get('resource-id') or ''
    cls = node.attrib.get('class') or ''
    enabled = node.attrib.get('enabled', 'true') == 'true'
    match = False
    if kind == 'gotit':
        match = rid == 'android:id/ok' or text == 'got it' or desc == 'got it'
    elif kind == 'wait':
        match = text == 'wait' or desc == 'wait'
    elif kind == 'deny_permission':
        match = text in ("don't allow", 'dont allow', 'deny') or desc in ("don't allow", 'dont allow', 'deny')
    elif kind == 'explore':
        match = enabled and cls.endswith('Button') and (text == 'kham pha' or desc == 'kham pha')
    if match:
        bounds = node.attrib.get('bounds') or ''
        if bounds and bounds != '[0,0][0,0]':
            print(bounds)
            break
PY
}

explore_state() {
  local xml="$1"
  python3 - "$xml" <<'PY'
import sys, unicodedata, xml.etree.ElementTree as ET

def norm(value):
    value = (value or '').replace('đ', 'd').replace('Đ', 'D')
    value = unicodedata.normalize('NFKD', value)
    return ''.join(ch for ch in value if not unicodedata.combining(ch)).casefold().strip()

root = ET.parse(sys.argv[1]).getroot()
for node in root.iter('node'):
    if not (node.attrib.get('class') or '').endswith('Button'):
        continue
    if norm((node.attrib.get('text') or '') + ' ' + (node.attrib.get('content-desc') or '')) == 'kham pha':
        print('enabled' if node.attrib.get('enabled', 'true') == 'true' else 'disabled')
        raise SystemExit(0)
print('missing')
PY
}

ui_is_busy() {
  local xml="$1"
  python3 - "$xml" <<'PY'
import sys, unicodedata, xml.etree.ElementTree as ET

def norm(value):
    value = (value or '').replace('đ', 'd').replace('Đ', 'D').replace('’', "'")
    value = unicodedata.normalize('NFKD', value)
    return ''.join(ch for ch in value if not unicodedata.combining(ch)).casefold()

root = ET.parse(sys.argv[1]).getroot()
joined = '\n'.join(
    norm((node.attrib.get('text') or '') + ' ' + (node.attrib.get('content-desc') or ''))
    for node in root.iter('node')
)
busy = (
    'dang xu ly luot' in joined
    or 'dang tim kiem khu vuc hien tai' in joined
    or 'dang kham pha khu vuc chua khao sat' in joined
    or 'combat auto' in joined
)
raise SystemExit(0 if busy else 1)
PY
}

ui_turn() {
  local xml="$1"
  python3 - "$xml" <<'PY'
import re, sys, unicodedata, xml.etree.ElementTree as ET

def norm(value):
    value = (value or '').replace('đ', 'd').replace('Đ', 'D')
    value = unicodedata.normalize('NFKD', value)
    return ''.join(ch for ch in value if not unicodedata.combining(ch)).casefold().strip()

root = ET.parse(sys.argv[1]).getroot()
values = []
for node in root.iter('node'):
    text = norm(node.attrib.get('text') or '')
    match = re.match(r'^turn\s+(\d+)\s+(?:da|đã)\b', text)
    if match:
        values.append(int(match.group(1)))
if values:
    print(max(values))
PY
}

authoritative_level() {
  local xml="$1"
  python3 - "$xml" <<'PY'
import re, sys, unicodedata, xml.etree.ElementTree as ET

def norm(value):
    value = (value or '').replace('đ', 'd').replace('Đ', 'D')
    value = unicodedata.normalize('NFKD', value)
    return ''.join(ch for ch in value if not unicodedata.combining(ch)).casefold().strip()

root = ET.parse(sys.argv[1]).getroot()
texts = [(node.attrib.get('text') or '').strip() for node in root.iter('node')]
for index, text in enumerate(texts):
    if norm(text) == 'vi tri':
        for candidate in texts[index + 1:index + 5]:
            match = re.match(r'^level\s*([0-6])\s*/', candidate, re.I)
            if match:
                print(match.group(1))
                raise SystemExit(0)
for text in texts:
    if len(text) > 180:
        continue
    match = re.match(r'^level\s*([0-6])\s*/', text, re.I)
    if match:
        print(match.group(1))
        raise SystemExit(0)
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

is_keyboard_permission_prompt() {
  local xml="$1"
  grep -Fq 'package="com.android.permissioncontroller"' "$xml" \
    && grep -Eqi 'Android Keyboard \(AOSP\).*contacts|access your contacts' "$xml"
}

is_known_system_overlay() {
  local xml="$1"
  grep -Eqi "Viewing full screen|is(n't| not) responding|is not responding" "$xml" \
    || is_keyboard_permission_prompt "$xml"
}

dismiss_system_overlays() {
  local attempt xml text bounds acted
  for attempt in $(seq 1 12); do
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

    if is_keyboard_permission_prompt "$xml"; then
      bounds=$(node_bounds "$xml" deny_permission || true)
      if [[ -n "$bounds" ]]; then
        echo "Dismissing emulator keyboard permission prompt"
        tap_bounds "$bounds"
        acted=1
        sleep 1
      fi
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
  adb shell dumpsys activity activities 2>/dev/null \
    | grep -E -q "(mResumedActivity|topResumedActivity).*${PACKAGE}/.MainActivity"
}

top_resumed_activity() {
  adb shell dumpsys activity activities 2>/dev/null \
    | sed -nE 's/.*(topResumedActivity|ResumedActivity):?[^A-Za-z0-9_.]*ActivityRecord\{[^ ]+ u[0-9]+ ([^ ]+).*/\2/p' \
    | head -1
}

wait_until_explore_ready() {
  local label="$1" deadline tmp non_app_attempts=0 top state
  deadline=$((SECONDS + TURN_TIMEOUT_SECONDS))
  tmp="/tmp/backroom-${label}.xml"

  while (( SECONDS < deadline )); do
    dump_ui_to "$tmp" || { sleep "$POLL_SECONDS"; continue; }

    if is_known_system_overlay "$tmp"; then
      dismiss_system_overlays
      sleep 1
      continue
    fi

    if ! is_app_resumed; then
      non_app_attempts=$((non_app_attempts + 1))
      if (( non_app_attempts >= 5 )); then
        top=$(top_resumed_activity || true)
        harness_error="MainActivity did not resume while waiting for $label${top:+ (top=$top)}"
        return 1
      fi
      sleep "$POLL_SECONDS"
      continue
    fi
    non_app_attempts=0

    if ui_is_busy "$tmp"; then
      sleep "$POLL_SECONDS"
      continue
    fi

    state=$(explore_state "$tmp" || true)
    if [[ "$state" == "enabled" ]]; then
      cp "$tmp" "$OUT/${label}.xml"
      return 0
    fi

    # The final APK disables the three primary actions while true-turn auto combat is active.
    # Give that runtime time to finish instead of treating a disabled Explore button as a harness error.
    if [[ "$state" == "disabled" ]] && grep -Eqi 'COMBAT|ROUND|Entity' "$tmp"; then
      sleep "$POLL_SECONDS"
      continue
    fi

    sleep "$POLL_SECONDS"
  done

  [[ -s "$tmp" ]] && cp "$tmp" "$OUT/${label}-timeout.xml" || true
  harness_error="Timed out after ${TURN_TIMEOUT_SECONDS}s waiting for the real Khám phá action ($label)"
  return 1
}

submit_explore() {
  local xml="$1" index="$2" before_turn bounds verify after_turn accepted=0 attempt
  before_turn=$(ui_turn "$xml" || true)
  bounds=$(node_bounds "$xml" explore || true)
  if [[ -z "$bounds" ]]; then
    harness_error="Enabled Khám phá button was not exposed before probe action $index"
    return 1
  fi

  for attempt in 1 2; do
    tap_bounds "$bounds"
    sleep 2
    verify=$(dump_ui "submitted-${index}-attempt-${attempt}")

    if is_known_system_overlay "$verify"; then
      dismiss_system_overlays
      sleep 1
      verify=$(dump_ui "submitted-${index}-attempt-${attempt}-recovered")
    fi

    after_turn=$(ui_turn "$verify" || true)
    if ui_is_busy "$verify"; then
      accepted=1
      break
    fi
    if [[ -n "$before_turn" && -n "$after_turn" && "$after_turn" -gt "$before_turn" ]]; then
      accepted=1
      break
    fi
    if [[ "$(explore_state "$verify" || true)" == "disabled" ]]; then
      accepted=1
      break
    fi

    bounds=$(node_bounds "$verify" explore || true)
    [[ -n "$bounds" ]] || break
  done

  if [[ "$accepted" -ne 1 ]]; then
    harness_error="Khám phá tap for probe action $index was not accepted"
    return 1
  fi

  submitted_turns=$((submitted_turns + 1))
  echo "probe_action=$index kind=EXPLORE label=Khám phá" | tee -a "$OUT/actions.log"
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

# Keep disposable emulator system UI from interfering with the app-under-test.
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

  if ! wait_until_explore_ready "turn-${turn}"; then
    break
  fi
  xml="$OUT/turn-${turn}.xml"
  observe_level "$xml"

  if [[ "$passed" -eq 1 ]]; then
    echo "PASS: authoritative state observed Level 1 and then Level 2 after $submitted_turns real Explore actions" | tee "$OUT/result.txt"
    break
  fi

  if [[ "$turn" -eq "$MAX_TURNS" ]]; then
    break
  fi

  if ! submit_explore "$xml" "$turn"; then
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
  echo "HARNESS FAIL: no real Khám phá action was accepted" | tee "$OUT/result.txt"
  exit 2
fi

if [[ "$seen_level1" -ne 1 ]]; then
  echo "GAMEPLAY FAIL: authoritative state never reached Level 1 within $submitted_turns accepted Khám phá actions" | tee "$OUT/result.txt"
  exit 1
fi

echo "GAMEPLAY FAIL: authoritative state reached Level 1 but did not reach Level 2 within $submitted_turns accepted Khám phá actions" | tee "$OUT/result.txt"
exit 1

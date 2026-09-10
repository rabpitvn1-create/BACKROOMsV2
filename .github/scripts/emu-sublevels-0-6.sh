#!/usr/bin/env bash
set -euo pipefail

PACKAGE="com.rabpit.backroom"
COMPONENT="$PACKAGE/.MainActivity"
APK="${1:-Backroom-under-test.apk}"
CATALOG="android-apk/app/src/main/assets/knowledge/sublevels_0_6_source.json"
OUT="${EMU_OUT:-emu-sublevels-0-6-results}"
MAX_ACTIONS="${MAX_ACTIONS:-80}"
POLL_SECONDS="${POLL_SECONDS:-2}"
TURN_TIMEOUT_SECONDS="${TURN_TIMEOUT_SECONDS:-120}"
START_TIMEOUT_SECONDS="${START_TIMEOUT_SECONDS:-30}"
mkdir -p "$OUT/ui"
: > "$OUT/actions.log"
: > "$OUT/route.log"
: > "$OUT/catalog-contract.log"
: > "$OUT/provider-order-contract.log"
: > "$OUT/entity-roaming-contract.log"

fail() {
  echo "FAIL: $*" | tee "$OUT/result.txt" >&2
  adb exec-out screencap -p > "$OUT/final.png" 2>/dev/null || true
  adb logcat -d > "$OUT/logcat.txt" 2>/dev/null || true
  adb shell dumpsys activity activities > "$OUT/activity.txt" 2>/dev/null || true
  exit 1
}

center_from_bounds() {
  python3 - "$1" <<'PY'
import re, sys
m = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', sys.argv[1])
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
nodes = list(root.iter('node'))
joined = '\n'.join(norm((n.attrib.get('text') or '') + ' ' + (n.attrib.get('content-desc') or '')) for n in nodes)
for node in nodes:
    text = norm(node.attrib.get('text'))
    desc = norm(node.attrib.get('content-desc'))
    rid = node.attrib.get('resource-id') or ''
    cls = node.attrib.get('class') or ''
    enabled = node.attrib.get('enabled', 'true') == 'true'
    match = False
    if kind == 'gotit':
        match = rid == 'android:id/ok' or text == 'got it' or desc == 'got it'
    elif kind == 'wait':
        match = ('responding' in joined) and (text == 'wait' or desc == 'wait')
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
    text = norm(node.attrib.get('text'))
    desc = norm(node.attrib.get('content-desc'))
    if text == 'kham pha' or desc == 'kham pha':
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
joined = '\n'.join(norm((n.attrib.get('text') or '') + ' ' + (n.attrib.get('content-desc') or '')) for n in root.iter('node'))
busy = any(token in joined for token in (
    'dang xu ly luot',
    'dang tim kiem khu vuc hien tai',
    'dang kham pha khu vuc chua khao sat',
    'combat auto',
))
raise SystemExit(0 if busy else 1)
PY
}

dump_ui_to() {
  local path="$1"
  adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
  adb pull /sdcard/window.xml "$path" >/dev/null 2>&1 || true
  [[ -s "$path" ]]
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
  python3 - "$xml" <<'PY'
import sys, unicodedata, xml.etree.ElementTree as ET

def norm(value):
    value = (value or '').replace('đ', 'd').replace('Đ', 'D').replace('’', "'")
    value = unicodedata.normalize('NFKD', value)
    return ''.join(ch for ch in value if not unicodedata.combining(ch)).casefold()

try:
    root = ET.parse(sys.argv[1]).getroot()
except Exception:
    raise SystemExit(1)
joined = '\n'.join(norm((n.attrib.get('text') or '') + ' ' + (n.attrib.get('content-desc') or '')) for n in root.iter('node'))
known = 'viewing full screen' in joined or 'responding' in joined or 'access your contacts' in joined
raise SystemExit(0 if known else 1)
PY
}

dismiss_system_overlays() {
  local attempt xml bounds acted
  for attempt in $(seq 1 12); do
    xml="$OUT/ui/system-${attempt}.xml"
    dump_ui_to "$xml" || { sleep 1; continue; }
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
    bounds=$(node_bounds "$xml" wait || true)
    if [[ -n "$bounds" ]]; then
      echo "Dismissing system ANR dialog with Wait"
      tap_bounds "$bounds"
      acted=1
      sleep 1
    fi
    [[ "$acted" -eq 1 ]] || break
  done
}

is_app_resumed() {
  adb shell dumpsys activity activities 2>/dev/null \
    | grep -E -q "(mResumedActivity|topResumedActivity).*${PACKAGE}/.MainActivity"
}

wait_until_explore_ready() {
  local label="$1" deadline tmp state non_app=0
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
      non_app=$((non_app + 1))
      if (( non_app >= 5 )); then
        return 1
      fi
      sleep "$POLL_SECONDS"
      continue
    fi
    non_app=0
    if ui_is_busy "$tmp"; then
      sleep "$POLL_SECONDS"
      continue
    fi
    state=$(explore_state "$tmp" || true)
    if [[ "$state" == "enabled" ]]; then
      cp "$tmp" "$OUT/ui/${label}.xml"
      return 0
    fi
    sleep "$POLL_SECONDS"
  done
  [[ -s "$tmp" ]] && cp "$tmp" "$OUT/ui/${label}-timeout.xml" || true
  return 1
}

submit_explore() {
  local index="$1" xml bounds verify state attempt
  xml="$OUT/ui/ready-${index}.xml"
  dump_ui_to "$xml" || return 1
  bounds=$(node_bounds "$xml" explore || true)
  [[ -n "$bounds" ]] || return 1
  for attempt in 1 2; do
    tap_bounds "$bounds"
    sleep 2
    verify="$OUT/ui/submitted-${index}-attempt-${attempt}.xml"
    dump_ui_to "$verify" || true
    if [[ -s "$verify" ]] && is_known_system_overlay "$verify"; then
      dismiss_system_overlays
      sleep 1
      dump_ui_to "$verify" || true
    fi
    state=$(explore_state "$verify" 2>/dev/null || true)
    if [[ -s "$verify" ]] && { ui_is_busy "$verify" || [[ "$state" == "disabled" ]]; }; then
      echo "action=$index kind=EXPLORE label=Khám phá" | tee -a "$OUT/actions.log"
      return 0
    fi
    bounds=$(node_bounds "$verify" explore || true)
    [[ -n "$bounds" ]] || break
  done
  return 1
}

read_core_state() {
  local label="$1" raw="$OUT/core-${label}.xml"
  adb exec-out run-as "$PACKAGE" cat shared_prefs/backroom_game_state_core.xml > "$raw" 2>/dev/null || return 1
  python3 - "$raw" <<'PY'
import json, sys, xml.etree.ElementTree as ET

root = ET.parse(sys.argv[1]).getroot()
node = next((n for n in root.findall('string') if n.attrib.get('name') == 'game_state'), None)
if node is None or not node.text:
    raise SystemExit(2)
state = json.loads(node.text)
world = state.get('world') or {}

def decoded(value, fallback):
    if isinstance(value, dict):
        return value
    if isinstance(value, str) and value.strip():
        try:
            parsed = json.loads(value)
            if isinstance(parsed, dict):
                return parsed
        except Exception:
            pass
    return fallback if isinstance(fallback, dict) else {}

level = decoded(world.get('levelJson'), state.get('level'))
flags = decoded(world.get('flagsJson'), state.get('flags'))
exploration = flags.get('exploration') if isinstance(flags.get('exploration'), dict) else {}
difficulty = exploration.get('difficulty') if isinstance(exploration.get('difficulty'), dict) else {}
location = world.get('location') or state.get('location') or ''
value = {
    'level': int(level.get('number', 0)),
    'sublevelId': str(exploration.get('sublevelId') or ''),
    'difficultyRating': difficulty.get('rating'),
    'difficultySource': difficulty.get('source'),
    'wikiClass': difficulty.get('wikiClass'),
    'transitionReady': bool(exploration.get('transitionReady') or exploration.get('exitReady')),
    'levelTurns': int(exploration.get('levelTurns') or 0),
    'location': location,
}
print(json.dumps(value, ensure_ascii=False, separators=(',', ':')))
PY
}

validate_state_against_route() {
  local state_json="$1" route_index="$2" allow_missing_difficulty="$3"
  python3 - "$state_json" "$OUT/route.json" "$route_index" "$allow_missing_difficulty" <<'PY'
import json, sys
state = json.loads(sys.argv[1])
route = json.load(open(sys.argv[2], encoding='utf-8'))
index = int(sys.argv[3])
allow_missing = sys.argv[4] == '1'
expected = route[index]
if state['level'] != expected['level'] or state['sublevelId'] != expected['sublevelId']:
    raise SystemExit(
        f"route mismatch at {index}: expected L{expected['level']} {expected['sublevelId'] or 'PARENT'}, "
        f"got L{state['level']} {state['sublevelId'] or 'PARENT'}"
    )
missing = state.get('difficultyRating') is None
if not (allow_missing and missing):
    actual = (state.get('difficultyRating'), state.get('difficultySource'), state.get('wikiClass'))
    wanted = (expected['rating'], expected['source'], expected['wikiClass'])
    if actual != wanted:
        raise SystemExit(f"difficulty mismatch at route {index}: expected {wanted!r}, got {actual!r}")
print(
    f"PASS route={index}/{len(route)-1} level={state['level']} "
    f"sublevel={state['sublevelId'] or 'PARENT'} difficulty={state.get('difficultyRating')} "
    f"source={state.get('difficultySource')} ready={state.get('transitionReady')} turns={state.get('levelTurns')}"
)
PY
}

# Source/runtime contracts. These are guards around the same APK run, not substitutes
# for the real UI traversal below.
python3 - "$CATALOG" "$OUT/route.json" <<'PY' | tee "$OUT/catalog-contract.log"
import json, sys
src, out = sys.argv[1:]
root = json.load(open(src, encoding='utf-8'))
parents = sorted(root.get('parentDifficulties') or [], key=lambda x: x['parentLevel'])
records = root.get('records') or []
allowed = {'WIKI_DIRECT', 'WIKI_NONSTANDARD_MAPPED', 'PROJECT_DESIGNED'}
if len(parents) != 7 or [x['parentLevel'] for x in parents] != list(range(7)):
    raise SystemExit('expected seven parent Levels 0-6')
if len(records) != 33:
    raise SystemExit(f'expected 33 sublevels, got {len(records)}')
by_parent = {n: [] for n in range(7)}
for record in records:
    by_parent[record['parentLevel']].append(record)
if {n: len(v) for n, v in by_parent.items()} != {0:12,1:4,2:3,3:2,4:3,5:3,6:6}:
    raise SystemExit('sublevel parent counts changed')

def check(owner, difficulty):
    if not isinstance(difficulty, dict):
        raise SystemExit(f'difficulty missing: {owner}')
    if type(difficulty.get('rating')) is not int or not 1 <= difficulty['rating'] <= 5:
        raise SystemExit(f'difficulty rating invalid: {owner}')
    if difficulty.get('source') not in allowed or not difficulty.get('wikiClass') or not difficulty.get('rationale'):
        raise SystemExit(f'difficulty provenance invalid: {owner}')
    if difficulty['source'] == 'WIKI_DIRECT' and difficulty['wikiClass'] != f"CLASS {difficulty['rating']}":
        raise SystemExit(f'Wiki direct class mismatch: {owner}')

for parent in parents:
    check(f"Level {parent['parentLevel']}", parent['difficulty'])
for record in records:
    if record.get('snapshot') != '':
        raise SystemExit(f"snapshot must remain empty: {record['id']}")
    check(record['id'], record['difficulty'])
if 'including Levels 0, 4 and 6' not in root.get('projectRule', ''):
    raise SystemExit('Level 0/4/6 roaming override missing')

parent_by_level = {p['parentLevel']: p for p in parents}
route = []
def add_parent(level):
    p = parent_by_level[level]
    d = p['difficulty']
    route.append({'level':level,'sublevelId':'','rating':d['rating'],'source':d['source'],'wikiClass':d['wikiClass']})
def add_sub(record):
    d = record['difficulty']
    route.append({'level':record['parentLevel'],'sublevelId':record['id'],'rating':d['rating'],'source':d['source'],'wikiClass':d['wikiClass']})

add_parent(0)
for level in range(7):
    for record in by_parent[level]:
        add_sub(record)
    add_parent(level)
    if level < 6:
        add_parent(level + 1)
json.dump(route, open(out, 'w', encoding='utf-8'), ensure_ascii=False, indent=2)
print(f"PASS route_states={len(route)} parents=7 sublevels=33 snapshots=33_EMPTY difficulty_profiles=40")
PY

python3 - <<'PY' | tee "$OUT/provider-order-contract.log"
from pathlib import Path
text = Path('android-apk/patch-haiku-provider-final.py').read_text(encoding='utf-8')
writer_start = text.find("new_generate = r'''")
writer_end = text.find("'''", writer_start + len("new_generate = r'''"))
writer = text[writer_start:writer_end]
if writer.find('String geminiResult = geminiText(prompt);') < 0 or writer.find('String haikuResult = haikuText(prompt, 1800, 0.6);') < 0:
    raise SystemExit('writer provider blocks missing')
if writer.find('String geminiResult = geminiText(prompt);') > writer.find('String haikuResult = haikuText(prompt, 1800, 0.6);'):
    raise SystemExit('writer order is not Gemini -> Haiku fallback')
print('PASS writer=GEMINI_PRIMARY->HAIKU_FALLBACK')
PY

python3 - <<'PY' | tee "$OUT/entity-roaming-contract.log"
from pathlib import Path
expected = {
    'hound','clump','duller','deathmoth','hostile_faceling','false_puddle','paintings','smiler',
    'skin-stealer','predatory_window','biological_pipeline','wretch','cable_mimic','the_beast_of_level_5',
    'hotel_corpse_lure','jeff_the_killer','jane_the_killer','slenderman','diep_minh',
}
unified = Path('android-apk/patch-unified-entity-spawn-pool.py').read_text(encoding='utf-8')
finalizer = Path('android-apk/patch-entity-rates-drops-final.py').read_text(encoding='utf-8')
sublevel = Path('android-apk/patch-web-sublevel-canon.py').read_text(encoding='utf-8')
missing = sorted(key for key in expected if key != 'diep_minh' and key not in unified)
if missing or '"diep_minh"' not in finalizer:
    raise SystemExit('roaming entity pool incomplete: ' + ', '.join(missing))
if 'không ngoại lệ Level 0, 4 hay 6' not in sublevel:
    raise SystemExit('Level 0/4/6 roaming override missing from runtime knowledge')
if 'patch-level0-6-traversal-final.py' not in finalizer:
    raise SystemExit('Level 0-6 traversal finalizer is not wired into build chain')
print(f'PASS roaming_entities={len(expected)} scope=ALL_LEVELS_AND_SUBLEVELS level_exceptions=NONE')
PY

adb shell settings put secure immersive_mode_confirmations confirmed >/dev/null 2>&1 || true
adb shell settings put global hide_error_dialogs 1 >/dev/null 2>&1 || true
adb install -r "$APK" >/dev/null
adb shell pm clear "$PACKAGE" >/dev/null 2>&1 || true
adb logcat -c
adb shell am start -W -n "$COMPONENT" --ez emuLevel06Traversal true | tee "$OUT/am-start.txt"
sleep 5
dismiss_system_overlays
sleep 2

is_app_resumed || fail "MainActivity did not remain resumed after fresh Level 0 launch"
wait_until_explore_ready "initial" || fail "Khám phá was not ready after fresh Level 0 launch"

# The traversal is continuous: no run-as writes, no fixture XML pushes and no Activity
# force-stop between locations. run-as is used only to read the authoritative core state.
initial_state=""
for attempt in $(seq 1 "$START_TIMEOUT_SECONDS"); do
  initial_state=$(read_core_state "initial-${attempt}" 2>/dev/null || true)
  [[ -n "$initial_state" ]] && break
  sleep 1
done
[[ -n "$initial_state" ]] || fail "Could not read authoritative core state after launch"

route_index=0
validate_state_against_route "$initial_state" "$route_index" 1 | tee -a "$OUT/route.log" 

route_length=$(python3 - "$OUT/route.json" <<'PY'
import json, sys
print(len(json.load(open(sys.argv[1], encoding='utf-8'))))
PY
)
actions=0
while (( route_index < route_length - 1 )); do
  (( actions < MAX_ACTIONS )) || fail "Exceeded MAX_ACTIONS=$MAX_ACTIONS at route index $route_index"
  wait_until_explore_ready "ready-${actions}" || fail "Timed out waiting for Khám phá before action $actions"
  submit_explore "$actions" || fail "Real Khám phá tap was not accepted at action $actions"
  actions=$((actions + 1))
  wait_until_explore_ready "after-${actions}" || fail "Timed out waiting for turn $actions to finish"

  state_json=$(read_core_state "after-${actions}" 2>/dev/null || true)
  [[ -n "$state_json" ]] || fail "Could not read authoritative core state after action $actions"

  actual_pair=$(python3 - "$state_json" <<'PY'
import json, sys
s=json.loads(sys.argv[1])
print(f"{s['level']}\t{s['sublevelId']}")
PY
)
  current_pair=$(python3 - "$OUT/route.json" "$route_index" <<'PY'
import json, sys
r=json.load(open(sys.argv[1],encoding='utf-8'))[int(sys.argv[2])]
print(f"{r['level']}\t{r['sublevelId']}")
PY
)
  next_pair=$(python3 - "$OUT/route.json" "$((route_index + 1))" <<'PY'
import json, sys
r=json.load(open(sys.argv[1],encoding='utf-8'))[int(sys.argv[2])]
print(f"{r['level']}\t{r['sublevelId']}")
PY
)

  if [[ "$actual_pair" == "$next_pair" ]]; then
    route_index=$((route_index + 1))
    validate_state_against_route "$state_json" "$route_index" 0 | tee -a "$OUT/route.log"
    cp "$OUT/ui/after-${actions}.xml" "$OUT/ui/route-${route_index}.xml" 2>/dev/null || true
  elif [[ "$actual_pair" == "$current_pair" ]]; then
    validate_state_against_route "$state_json" "$route_index" 0 | tee -a "$OUT/route.log"
    echo "WAIT route=$route_index action=$actions state_unchanged=true" | tee -a "$OUT/route.log"
  else
    fail "Unexpected authoritative route after action $actions: $actual_pair (current=$current_pair next=$next_pair)"
  fi
done

adb exec-out screencap -p > "$OUT/final.png" 2>/dev/null || true
adb logcat -d > "$OUT/logcat.txt" 2>/dev/null || true
final_state=$(read_core_state "final" 2>/dev/null || true)
[[ -n "$final_state" ]] || fail "Final authoritative state unavailable"
validate_state_against_route "$final_state" "$route_index" 0 | tee -a "$OUT/route.log"

echo "PASS: real emulator traversed Level 0→6 and all 33 sublevels with $actions accepted Khám phá actions; no authoritative state seeding was used, 40 difficulty profiles followed the visited locations, sublevel snapshots remain empty, Entity roaming has no Level 0/4/6 exception, Gemini remains primary with Haiku fallback." | tee "$OUT/result.txt"

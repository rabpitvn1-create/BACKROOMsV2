#!/usr/bin/env bash
set -euo pipefail

PACKAGE="com.rabpit.backroom"
COMPONENT="$PACKAGE/.MainActivity"
APK="${1:-Backroom-1.1.71.apk}"
POLL_SECONDS="${POLL_SECONDS:-1}"
START_TIMEOUT_SECONDS="${START_TIMEOUT_SECONDS:-45}"
COMBAT_TIMEOUT_SECONDS="${COMBAT_TIMEOUT_SECONDS:-180}"
OUT="${EMU_OUT:-emu-combat-results}"
mkdir -p "$OUT"
: > "$OUT/actors.log"

harness_error=""
combat_start_turn=""
combat_final_turn=""
combat_resolved=0

center_from_bounds() {
  python3 - "$1" <<'PY'
import re, sys
m = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', sys.argv[1])
if not m:
    raise SystemExit(2)
x1,y1,x2,y2 = map(int, m.groups())
print((x1+x2)//2, (y1+y2)//2)
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

node_bounds() {
  local xml="$1" kind="$2"
  python3 - "$xml" "$kind" <<'PY'
import sys, unicodedata, xml.etree.ElementTree as ET

def norm(v):
    v=(v or '').replace('đ','d').replace('Đ','D').replace('’',"'")
    v=unicodedata.normalize('NFKD',v)
    return ''.join(c for c in v if not unicodedata.combining(c)).casefold().strip()

root=ET.parse(sys.argv[1]).getroot(); kind=sys.argv[2]; nodes=list(root.iter('node'))
joined='\n'.join(norm((n.attrib.get('text') or '')+' '+(n.attrib.get('content-desc') or '')) for n in nodes)
for n in nodes:
    text=norm(n.attrib.get('text')); desc=norm(n.attrib.get('content-desc'))
    rid=n.attrib.get('resource-id') or ''; cls=n.attrib.get('class') or ''
    enabled=n.attrib.get('enabled','true')=='true'; match=False
    if kind=='gotit': match=rid=='android:id/ok' or text=='got it' or desc=='got it'
    elif kind=='wait': match=('responding' in joined) and (text=='wait' or desc=='wait')
    elif kind=='deny': match=text in ("don't allow",'dont allow','deny') or desc in ("don't allow",'dont allow','deny')
    elif kind=='explore': match=enabled and cls.endswith('Button') and (text=='kham pha' or desc=='kham pha')
    elif kind=='combat_button': match=enabled and cls.endswith('Button') and (text=='combat' or desc=='combat')
    if match:
        b=n.attrib.get('bounds') or ''
        if b and b!='[0,0][0,0]':
            print(b)
            break
PY
}

tap_bounds() {
  local b="$1" x y
  read -r x y < <(center_from_bounds "$b")
  adb shell input tap "$x" "$y"
}

is_app_resumed() {
  adb shell dumpsys activity activities 2>/dev/null \
    | grep -E -q "(mResumedActivity|topResumedActivity).*${PACKAGE}/.MainActivity"
}

is_known_system_overlay() {
  local xml="$1"
  python3 - "$xml" <<'PY'
import sys, unicodedata, xml.etree.ElementTree as ET

def norm(v):
    v=(v or '').replace('đ','d').replace('Đ','D').replace('’',"'")
    v=unicodedata.normalize('NFKD',v)
    return ''.join(c for c in v if not unicodedata.combining(c)).casefold()
try:
    root=ET.parse(sys.argv[1]).getroot()
except Exception:
    raise SystemExit(1)
joined='\n'.join(norm((n.attrib.get('text') or '')+' '+(n.attrib.get('content-desc') or '')) for n in root.iter('node'))
raise SystemExit(0 if ('viewing full screen' in joined or 'responding' in joined or 'access your contacts' in joined) else 1)
PY
}

dismiss_system_overlays() {
  local i xml b acted
  for i in $(seq 1 10); do
    xml=$(dump_ui "system-${i}")
    [[ -s "$xml" ]] || { sleep 1; continue; }
    acted=0
    b=$(node_bounds "$xml" gotit || true)
    if [[ -n "$b" ]]; then tap_bounds "$b"; acted=1; sleep 1; fi
    if grep -Fq 'package="com.android.permissioncontroller"' "$xml" && grep -Eqi 'contacts|access your contacts' "$xml"; then
      b=$(node_bounds "$xml" deny || true)
      if [[ -n "$b" ]]; then tap_bounds "$b"; acted=1; sleep 1; fi
    fi
    b=$(node_bounds "$xml" wait || true)
    if [[ -n "$b" ]]; then tap_bounds "$b"; acted=1; sleep 1; fi
    [[ "$acted" -eq 1 ]] || break
  done
}

ui_turn() {
  local xml="$1"
  python3 - "$xml" <<'PY'
import re,sys,unicodedata,xml.etree.ElementTree as ET

def norm(v):
    v=unicodedata.normalize('NFKD',(v or '').replace('đ','d').replace('Đ','D'))
    return ''.join(c for c in v if not unicodedata.combining(c)).casefold().strip()
texts=[norm(n.attrib.get('text') or '') for n in ET.parse(sys.argv[1]).getroot().iter('node')]
vals=[]
for i,text in enumerate(texts):
    for pattern in (r'^turn\s+(\d+)\s+da\b', r'\bsnapshot\s+turn\s+(\d+)\b', r'^turn\s+(\d+)\b'):
        m=re.search(pattern,text)
        if m: vals.append(int(m.group(1)))
    if text=='turn' and i+1 < len(texts) and re.fullmatch(r'\d+',texts[i+1]):
        vals.append(int(texts[i+1]))
if vals: print(max(vals))
PY
}

combat_present() {
  local xml="$1"
  python3 - "$xml" <<'PY'
import sys,unicodedata,xml.etree.ElementTree as ET

def norm(v):
    v=unicodedata.normalize('NFKD',(v or '').replace('đ','d').replace('Đ','D'))
    return ''.join(c for c in v if not unicodedata.combining(c)).casefold()
root=ET.parse(sys.argv[1]).getroot()
joined='\n'.join(norm((n.attrib.get('text') or '')+' '+(n.attrib.get('content-desc') or '')) for n in root.iter('node'))
raise SystemExit(0 if 'pressure combat' in joined else 1)
PY
}

explore_state() {
  local xml="$1"
  python3 - "$xml" <<'PY'
import sys,unicodedata,xml.etree.ElementTree as ET

def norm(v):
    v=unicodedata.normalize('NFKD',(v or '').replace('đ','d').replace('Đ','D'))
    return ''.join(c for c in v if not unicodedata.combining(c)).casefold().strip()
for n in ET.parse(sys.argv[1]).getroot().iter('node'):
    if not (n.attrib.get('class') or '').endswith('Button'): continue
    if norm(n.attrib.get('text'))=='kham pha' or norm(n.attrib.get('content-desc'))=='kham pha':
        print('enabled' if n.attrib.get('enabled','true')=='true' else 'disabled')
        raise SystemExit(0)
print('missing')
PY
}

popup_actors() {
  local xml="$1"
  python3 - "$xml" <<'PY'
import re,sys,xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot()
dialogs=[n for n in root.iter('node') if (n.attrib.get('class') or '')=='android.app.Dialog']
for dialog in dialogs:
    texts=[(n.attrib.get('text') or '').strip() for n in dialog.iter('node')]
    texts=[t for t in texts if t]
    if not any(t.casefold()=='combat' for t in texts):
        continue
    round_no='?'
    current=''
    for t in texts:
        m=re.fullmatch(r'ROUND\s+(\d+)',t,re.I)
        if m:
            round_no=m.group(1)
        m=re.match(r'^CURRENT TURN:\s*(.+)$',t,re.I)
        if m:
            current=m.group(1).strip()
    actors=[]
    for t in texts:
        if '→' not in t:
            continue
        left,_=map(str.strip,t.split('→',1))
        # Chromium accessibility may concatenate the current actor label directly
        # in front of the previous attacker, e.g. "Kai AkechiHound → Kai Akechi".
        if current and left.startswith(current) and len(left)>len(current):
            left=left[len(current):].strip()
        if left and left not in actors:
            actors.append(left)
    if current and current not in actors:
        actors.append(current)
    for actor in actors:
        print(actor+'|'+round_no)
    if actors:
        break
PY
}

visible_error() {
  local xml="$1"
  python3 - "$xml" <<'PY'
import sys,xml.etree.ElementTree as ET
for n in ET.parse(sys.argv[1]).getroot().iter('node'):
    t=(n.attrib.get('text') or '').strip()
    if t.lower().startswith(('lỗi ','loi ','error ')):
        print(t.replace('\n',' ')[:500])
        break
PY
}

capture() {
  adb exec-out screencap -p > "$OUT/$1.png" || true
}

seed_core_combat() {
  local seed_xml="$OUT/seeded-core.xml"
  python3 - "$seed_xml" <<'PY'
import json, sys, xml.etree.ElementTree as ET
state={
  'saveVersion':3,
  'metadata':{
    'combat.entityKey':'hound',
    'combat.playerHp':'300',
    'combat.playerMaxHp':'300'
  }
}
root=ET.Element('map')
node=ET.SubElement(root,'string',{'name':'game_state'})
node.text=json.dumps(state,separators=(',',':'))
ET.ElementTree(root).write(sys.argv[1],encoding='utf-8',xml_declaration=True)
PY

  if ! adb shell run-as "$PACKAGE" id > "$OUT/run-as.txt" 2>&1; then
    harness_error="Debug APK does not allow run-as; cannot seed deterministic combat state"
    return 2
  fi
  adb push "$seed_xml" /data/local/tmp/backroom_game_state_core.xml >/dev/null
  adb shell run-as "$PACKAGE" mkdir -p shared_prefs
  adb shell run-as "$PACKAGE" cp /data/local/tmp/backroom_game_state_core.xml shared_prefs/backroom_game_state_core.xml
  adb shell run-as "$PACKAGE" chmod 660 shared_prefs/backroom_game_state_core.xml || true
  if ! adb exec-out run-as "$PACKAGE" cat shared_prefs/backroom_game_state_core.xml > "$OUT/prefs-seeded.xml"; then
    harness_error="Could not verify seeded Game State Core preferences"
    return 2
  fi
  grep -Fq 'combat.entityKey' "$OUT/prefs-seeded.xml" || {
    harness_error="Seeded Game State Core preferences lost combat metadata"
    return 2
  }
}

wait_for_explore_ready() {
  local deadline xml state
  deadline=$((SECONDS+START_TIMEOUT_SECONDS))
  while (( SECONDS < deadline )); do
    xml=$(dump_ui "ready")
    [[ -s "$xml" ]] || { sleep "$POLL_SECONDS"; continue; }
    if is_known_system_overlay "$xml"; then dismiss_system_overlays; sleep 1; continue; fi
    if ! is_app_resumed; then sleep "$POLL_SECONDS"; continue; fi
    state=$(explore_state "$xml" || true)
    if [[ "$state" == "enabled" ]]; then
      echo "$xml"
      return 0
    fi
    sleep "$POLL_SECONDS"
  done
  return 2
}

trigger_seeded_combat() {
  local xml="$1" b deadline current err
  b=$(node_bounds "$xml" explore || true)
  if [[ -z "$b" ]]; then
    return 2
  fi
  echo "trigger=EXPLORE seeded_core_entity=hound" | tee "$OUT/trigger.log" >&2
  tap_bounds "$b"
  deadline=$((SECONDS+START_TIMEOUT_SECONDS))
  while (( SECONDS < deadline )); do
    sleep "$POLL_SECONDS"
    current=$(dump_ui "combat-entry")
    [[ -s "$current" ]] || continue
    if is_known_system_overlay "$current"; then dismiss_system_overlays; continue; fi
    if combat_present "$current"; then
      echo "$current"
      return 0
    fi
    err=$(visible_error "$current" || true)
    if [[ -n "$err" && "$(explore_state "$current" || true)" == "enabled" ]]; then
      echo "seeded-combat-entry-error=$err" | tee "$OUT/entry-error.log" >&2
      return 2
    fi
  done
  return 2
}

open_combat_popup() {
  local xml="$1" b
  b=$(node_bounds "$xml" combat_button || true)
  if [[ -n "$b" ]]; then
    tap_bounds "$b"
    sleep 1
  fi
}

observe_combat() {
  local first_xml="$1" deadline xml state turn actor last_actor="" samples=0
  local -a observed=()
  combat_start_turn=$(ui_turn "$first_xml" || true)
  echo "combat_start_turn=${combat_start_turn:-unknown}" | tee "$OUT/combat.log"
  capture combat-start
  open_combat_popup "$first_xml"
  deadline=$((SECONDS+COMBAT_TIMEOUT_SECONDS))

  while (( SECONDS < deadline )); do
    xml=$(dump_ui "combat-${samples}")
    samples=$((samples+1))
    [[ -s "$xml" ]] || { sleep "$POLL_SECONDS"; continue; }
    if is_known_system_overlay "$xml"; then dismiss_system_overlays; sleep 1; continue; fi
    if ! is_app_resumed; then
      harness_error="MainActivity left foreground during combat"
      return 2
    fi

    if ! combat_present "$xml"; then
      state=$(explore_state "$xml" || true)
      turn=$(ui_turn "$xml" || true)
      if [[ "$state" == "enabled" ]]; then
        combat_final_turn="$turn"
        combat_resolved=1
        capture combat-end
        return 0
      fi
    fi

    state=$(explore_state "$xml" || true)
    if [[ "$state" == "enabled" ]]; then
      harness_error="Khám phá became enabled while Pressure Combat was active"
      return 2
    fi

    mapfile -t observed < <(popup_actors "$xml" || true)
    if [[ "${#observed[@]}" -gt 0 ]]; then
      for actor in "${observed[@]}"; do
        if [[ -n "$actor" && "$actor" != "$last_actor" ]]; then
          echo "$SECONDS|$actor" | tee -a "$OUT/actors.log"
          last_actor="$actor"
        fi
      done
    else
      open_combat_popup "$xml" || true
    fi
    sleep "$POLL_SECONDS"
  done

  harness_error="Combat remained active for more than ${COMBAT_TIMEOUT_SECONDS}s"
  return 2
}

validate_actor_sequence() {
  python3 - "$OUT/actors.log" <<'PY'
import sys
rows=[]
for line in open(sys.argv[1],encoding='utf-8'):
    parts=line.strip().split('|',2)
    if len(parts)<3: continue
    name=parts[1].strip()
    if not rows or rows[-1]!=name:
        rows.append(name)
print('actors='+' -> '.join(rows))
if not rows:
    raise SystemExit('No COMBAT popup actor turn was observable')
if not any('kai' in x.casefold() for x in rows):
    raise SystemExit('Kai actor turn was never observed')
entity=[x for x in rows if 'kai' not in x.casefold()]
if not entity:
    raise SystemExit('Entity actor turn was never observed')
if len(rows) < 2:
    raise SystemExit('Actor rotation did not advance')
PY
}

adb shell settings put secure immersive_mode_confirmations confirmed >/dev/null 2>&1 || true
adb shell settings put global hide_error_dialogs 1 >/dev/null 2>&1 || true
adb install -r "$APK"
adb shell pm clear "$PACKAGE" >/dev/null
adb shell am force-stop "$PACKAGE"
echo "mode=seeded-core-combat-real-main-apk" | tee "$OUT/fixture.log"
seed_core_combat || true

if [[ -z "$harness_error" ]]; then
  adb logcat -c
  adb shell am start -W -n "$COMPONENT" | tee "$OUT/am-start.txt"
  sleep 5
  dismiss_system_overlays
  sleep 2
fi

if [[ -z "$harness_error" ]] && ! is_app_resumed; then
  harness_error="MainActivity did not remain resumed after launch"
fi

combat_xml=""
if [[ -z "$harness_error" ]]; then
  set +e
  ready_xml=$(wait_for_explore_ready); rc=$?
  set -e
  if [[ "$rc" -ne 0 ]]; then
    harness_error="Timed out waiting for initial Khám phá control"
  else
    set +e
    combat_xml=$(trigger_seeded_combat "$ready_xml"); rc=$?
    set -e
    if [[ "$rc" -ne 0 ]]; then
      if [[ -s "$OUT/entry-error.log" ]]; then
        harness_error="Seeded combat did not intercept the trigger; $(cat "$OUT/entry-error.log")"
      else
        harness_error="Seeded Game State Core combat never became visible in the WebView"
      fi
    fi
  fi
fi

if [[ -z "$harness_error" && -n "$combat_xml" ]]; then
  observe_combat "$combat_xml" || true
fi

capture final
adb logcat -d > "$OUT/logcat.txt" || true
adb shell dumpsys activity activities > "$OUT/activity.txt" 2>/dev/null || true
adb exec-out run-as "$PACKAGE" cat shared_prefs/backroom_game_state_core.xml > "$OUT/prefs-final.xml" 2>/dev/null || true

if [[ -n "$harness_error" ]]; then
  echo "HARNESS/COMBAT FAIL: $harness_error" | tee "$OUT/result.txt"
  exit 2
fi
if [[ "$combat_resolved" -ne 1 ]]; then
  echo "GAMEPLAY FAIL: seeded authoritative combat never returned to normal gameplay" | tee "$OUT/result.txt"
  exit 1
fi
if [[ -z "$combat_start_turn" || -z "$combat_final_turn" || "$combat_final_turn" -le "$combat_start_turn" ]]; then
  echo "GAMEPLAY FAIL: combat resolved without observable authoritative turn advance (${combat_start_turn:-?} -> ${combat_final_turn:-?})" | tee "$OUT/result.txt"
  exit 1
fi
validate_actor_sequence | tee "$OUT/actor-validation.txt"
echo "PASS: real main APK resolved seeded authoritative combat, locked Explore, advanced turns, rotated Kai/Entity presentation, and returned to gameplay" | tee "$OUT/result.txt"

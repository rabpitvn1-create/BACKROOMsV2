#!/usr/bin/env bash
set -euo pipefail

PACKAGE="com.rabpit.backroom"
COMPONENT="$PACKAGE/.MainActivity"
APK="${1:-Backroom-1.1.71.apk}"
MAX_EXPLORES="${MAX_EXPLORES:-16}"
POLL_SECONDS="${POLL_SECONDS:-1}"
TURN_TIMEOUT_SECONDS="${TURN_TIMEOUT_SECONDS:-120}"
COMBAT_TIMEOUT_SECONDS="${COMBAT_TIMEOUT_SECONDS:-180}"
OUT="${EMU_OUT:-emu-combat-results}"
mkdir -p "$OUT"
: > "$OUT/actors.log"
: > "$OUT/actions.log"

harness_error=""
combat_seen=0
combat_resolved=0
combat_turn_advanced=0
combat_start_turn=""

center_from_bounds() {
  python3 - "$1" <<'PY'
import re, sys
m = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', sys.argv[1])
if not m: raise SystemExit(2)
x1,y1,x2,y2=map(int,m.groups())
print((x1+x2)//2,(y1+y2)//2)
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
    text=norm(n.attrib.get('text')); desc=norm(n.attrib.get('content-desc')); rid=n.attrib.get('resource-id') or ''; cls=n.attrib.get('class') or ''; enabled=n.attrib.get('enabled','true')=='true'; match=False
    if kind=='gotit': match=rid=='android:id/ok' or text=='got it' or desc=='got it'
    elif kind=='wait': match=('responding' in joined) and (text=='wait' or desc=='wait')
    elif kind=='deny': match=text in ("don't allow",'dont allow','deny') or desc in ("don't allow",'dont allow','deny')
    elif kind=='explore': match=enabled and cls.endswith('Button') and (text=='kham pha' or desc=='kham pha')
    elif kind=='combat_button': match=enabled and cls.endswith('Button') and (text=='combat' or desc=='combat')
    if match:
        b=n.attrib.get('bounds') or ''
        if b and b!='[0,0][0,0]': print(b); break
PY
}

tap_bounds() {
  local b="$1" x y
  read -r x y < <(center_from_bounds "$b")
  adb shell input tap "$x" "$y"
}

is_app_resumed() {
  adb shell dumpsys activity activities 2>/dev/null | grep -E -q "(mResumedActivity|topResumedActivity).*${PACKAGE}/.MainActivity"
}

is_known_system_overlay() {
  local xml="$1"
  python3 - "$xml" <<'PY'
import sys, unicodedata, xml.etree.ElementTree as ET

def norm(v):
    v=(v or '').replace('đ','d').replace('Đ','D').replace('’',"'")
    v=unicodedata.normalize('NFKD',v)
    return ''.join(c for c in v if not unicodedata.combining(c)).casefold()
try: root=ET.parse(sys.argv[1]).getroot()
except Exception: raise SystemExit(1)
joined='\n'.join(norm((n.attrib.get('text') or '')+' '+(n.attrib.get('content-desc') or '')) for n in root.iter('node'))
raise SystemExit(0 if ('viewing full screen' in joined or 'responding' in joined or 'access your contacts' in joined) else 1)
PY
}

dismiss_system_overlays() {
  local i xml b acted
  for i in $(seq 1 10); do
    xml=$(dump_ui "system-${i}"); [[ -s "$xml" ]] || { sleep 1; continue; }; acted=0
    b=$(node_bounds "$xml" gotit || true); if [[ -n "$b" ]]; then tap_bounds "$b"; acted=1; sleep 1; fi
    if grep -Fq 'package="com.android.permissioncontroller"' "$xml" && grep -Eqi 'contacts|access your contacts' "$xml"; then
      b=$(node_bounds "$xml" deny || true); if [[ -n "$b" ]]; then tap_bounds "$b"; acted=1; sleep 1; fi
    fi
    b=$(node_bounds "$xml" wait || true); if [[ -n "$b" ]]; then tap_bounds "$b"; acted=1; sleep 1; fi
    [[ "$acted" -eq 1 ]] || break
  done
}

ui_turn() {
  local xml="$1"
  python3 - "$xml" <<'PY'
import re,sys,unicodedata,xml.etree.ElementTree as ET

def norm(v):
 v=unicodedata.normalize('NFKD',(v or '').replace('đ','d').replace('Đ','D')); return ''.join(c for c in v if not unicodedata.combining(c)).casefold().strip()
vals=[]
for n in ET.parse(sys.argv[1]).getroot().iter('node'):
 m=re.match(r'^turn\s+(\d+)\s+da\b',norm(n.attrib.get('text') or ''))
 if m: vals.append(int(m.group(1)))
if vals: print(max(vals))
PY
}

combat_present() {
  local xml="$1"
  python3 - "$xml" <<'PY'
import sys,unicodedata,xml.etree.ElementTree as ET

def norm(v):
 v=unicodedata.normalize('NFKD',(v or '').replace('đ','d').replace('Đ','D')); return ''.join(c for c in v if not unicodedata.combining(c)).casefold()
root=ET.parse(sys.argv[1]).getroot(); joined='\n'.join(norm((n.attrib.get('text') or '')+' '+(n.attrib.get('content-desc') or '')) for n in root.iter('node'))
raise SystemExit(0 if 'pressure combat' in joined else 1)
PY
}

explore_state() {
  local xml="$1"
  python3 - "$xml" <<'PY'
import sys,unicodedata,xml.etree.ElementTree as ET

def norm(v):
 v=unicodedata.normalize('NFKD',(v or '').replace('đ','d').replace('Đ','D')); return ''.join(c for c in v if not unicodedata.combining(c)).casefold().strip()
for n in ET.parse(sys.argv[1]).getroot().iter('node'):
 if not (n.attrib.get('class') or '').endswith('Button'): continue
 if norm(n.attrib.get('text'))=='kham pha' or norm(n.attrib.get('content-desc'))=='kham pha':
  print('enabled' if n.attrib.get('enabled','true')=='true' else 'disabled'); raise SystemExit(0)
print('missing')
PY
}

popup_actor() {
  local xml="$1"
  python3 - "$xml" <<'PY'
import re,sys,xml.etree.ElementTree as ET
for n in ET.parse(sys.argv[1]).getroot().iter('node'):
 t=(n.attrib.get('text') or '').strip()
 m=re.match(r'^TURN:\s*(.*?)\s*[•·]\s*ROUND\s+(\d+)',t,re.I)
 if m:
  print(m.group(1).strip()+'|'+m.group(2)); break
PY
}

capture() {
  adb exec-out screencap -p > "$OUT/$1.png" || true
}

wait_explore_or_combat() {
  local label="$1" deadline xml state
  deadline=$((SECONDS+TURN_TIMEOUT_SECONDS))
  while (( SECONDS < deadline )); do
    xml=$(dump_ui "$label")
    [[ -s "$xml" ]] || { sleep "$POLL_SECONDS"; continue; }
    if is_known_system_overlay "$xml"; then dismiss_system_overlays; sleep 1; continue; fi
    if ! is_app_resumed; then sleep "$POLL_SECONDS"; continue; fi
    if combat_present "$xml"; then echo "$xml"; return 10; fi
    state=$(explore_state "$xml" || true)
    if [[ "$state" == "enabled" ]]; then echo "$xml"; return 0; fi
    sleep "$POLL_SECONDS"
  done
  harness_error="Timed out waiting for Explore/combat at $label"; return 2
}

submit_explore() {
  local xml="$1" idx="$2" b before deadline now state
  before=$(ui_turn "$xml" || true); b=$(node_bounds "$xml" explore || true)
  if [[ -z "$b" ]]; then harness_error="No enabled Khám phá button at explore $idx"; return 2; fi
  tap_bounds "$b"; echo "EXPLORE $idx before_turn=${before:-unknown}" | tee -a "$OUT/actions.log"
  deadline=$((SECONDS+TURN_TIMEOUT_SECONDS))
  while (( SECONDS < deadline )); do
    sleep "$POLL_SECONDS"; xml=$(dump_ui "after-explore-${idx}")
    [[ -s "$xml" ]] || continue
    if is_known_system_overlay "$xml"; then dismiss_system_overlays; continue; fi
    if combat_present "$xml"; then echo "$xml"; return 10; fi
    now=$(ui_turn "$xml" || true); state=$(explore_state "$xml" || true)
    if [[ -n "$before" && -n "$now" && "$now" -gt "$before" && "$state" == "enabled" ]]; then echo "$xml"; return 0; fi
  done
  harness_error="Explore $idx did not settle or enter combat"; return 2
}

open_combat_popup() {
  local xml="$1" b
  b=$(node_bounds "$xml" combat_button || true)
  if [[ -n "$b" ]]; then tap_bounds "$b"; sleep 1; fi
}

observe_combat() {
  local first_xml="$1" deadline xml state turn actor last_actor="" samples=0
  combat_seen=1
  combat_start_turn=$(ui_turn "$first_xml" || true)
  echo "combat_start_turn=${combat_start_turn:-unknown}" | tee "$OUT/combat.log"
  capture combat-start
  open_combat_popup "$first_xml"
  deadline=$((SECONDS+COMBAT_TIMEOUT_SECONDS))

  while (( SECONDS < deadline )); do
    xml=$(dump_ui "combat-${samples}"); samples=$((samples+1))
    [[ -s "$xml" ]] || { sleep "$POLL_SECONDS"; continue; }
    if is_known_system_overlay "$xml"; then dismiss_system_overlays; sleep 1; continue; fi
    if ! is_app_resumed; then harness_error="MainActivity left foreground during combat"; return 2; fi

    if ! combat_present "$xml"; then
      state=$(explore_state "$xml" || true); turn=$(ui_turn "$xml" || true)
      if [[ "$state" == "enabled" ]]; then
        if [[ -n "$combat_start_turn" && -n "$turn" && "$turn" -gt "$combat_start_turn" ]]; then combat_turn_advanced=1; fi
        combat_resolved=1; capture combat-end; return 0
      fi
    fi

    state=$(explore_state "$xml" || true)
    if [[ "$state" == "enabled" ]]; then harness_error="Khám phá became enabled while combat HUD was active"; return 2; fi

    actor=$(popup_actor "$xml" || true)
    if [[ -n "$actor" && "$actor" != "$last_actor" ]]; then
      echo "$SECONDS|$actor" | tee -a "$OUT/actors.log"
      last_actor="$actor"
    fi
    # Keep popup open if a render callback closed/recreated accessibility nodes.
    if [[ -z "$actor" ]]; then open_combat_popup "$xml" || true; fi
    sleep "$POLL_SECONDS"
  done
  harness_error="Combat remained active for more than ${COMBAT_TIMEOUT_SECONDS}s"; return 2
}

validate_actor_sequence() {
  python3 - "$OUT/actors.log" <<'PY'
import sys
rows=[]
for line in open(sys.argv[1],encoding='utf-8'):
    parts=line.strip().split('|',2)
    if len(parts)<3: continue
    name=parts[1].strip()
    if not rows or rows[-1]!=name: rows.append(name)
print('actors='+' -> '.join(rows))
if not rows:
    raise SystemExit('No COMBAT popup actor turn was observable')
if not any('kai' in x.casefold() for x in rows):
    raise SystemExit('Kai actor turn was never observed')
party=('kai','lucia','lục','syvial','iris','an nhiên')
entity=[x for x in rows if not any(p in x.casefold() for p in party)]
if not entity:
    raise SystemExit('Entity actor turn was never observed')
# If Lucia appears in the popup sequence, ensure an Entity presentation step separates Kai and Lucia.
for i,x in enumerate(rows):
    if 'kai' in x.casefold():
        tail=rows[i+1:]
        for j,y in enumerate(tail):
            if 'lucia' in y.casefold() or 'lục' in y.casefold():
                if not any(z in entity for z in tail[:j]):
                    raise SystemExit('Lucia followed Kai without an Entity presentation step')
                raise SystemExit(0)
raise SystemExit(0)
PY
}

adb shell settings put secure immersive_mode_confirmations confirmed >/dev/null 2>&1 || true
adb shell settings put global hide_error_dialogs 1 >/dev/null 2>&1 || true
adb install -r "$APK"
adb logcat -c
adb shell am force-stop "$PACKAGE"
echo "mode=production-combat-rng-real-webview" | tee "$OUT/fixture.log"
adb shell am start -W -n "$COMPONENT" | tee "$OUT/am-start.txt"
sleep 5
dismiss_system_overlays
sleep 2

if ! is_app_resumed; then harness_error="MainActivity did not remain resumed after launch"; fi

combat_xml=""
for idx in $(seq 0 "$MAX_EXPLORES"); do
  [[ -z "$harness_error" ]] || break
  set +e
  xml=$(wait_explore_or_combat "ready-${idx}"); rc=$?
  set -e
  if [[ "$rc" -eq 10 ]]; then combat_xml="$xml"; break; fi
  if [[ "$rc" -ne 0 ]]; then break; fi
  [[ "$idx" -eq "$MAX_EXPLORES" ]] && break
  set +e
  next=$(submit_explore "$xml" "$idx"); rc=$?
  set -e
  if [[ "$rc" -eq 10 ]]; then combat_xml="$next"; break; fi
  if [[ "$rc" -ne 0 ]]; then break; fi
done

if [[ -z "$harness_error" && -n "$combat_xml" ]]; then
  observe_combat "$combat_xml" || true
fi

capture final
adb logcat -d > "$OUT/logcat.txt" || true
adb shell dumpsys activity activities > "$OUT/activity.txt" 2>/dev/null || true

if [[ -n "$harness_error" ]]; then
  echo "HARNESS/COMBAT FAIL: $harness_error" | tee "$OUT/result.txt"; exit 2
fi
if [[ "$combat_seen" -ne 1 ]]; then
  echo "GAMEPLAY FAIL: no Entity combat started within $MAX_EXPLORES real Khám phá attempts" | tee "$OUT/result.txt"; exit 1
fi
if [[ "$combat_resolved" -ne 1 ]]; then
  echo "GAMEPLAY FAIL: combat started but did not return to normal gameplay" | tee "$OUT/result.txt"; exit 1
fi
if [[ "$combat_turn_advanced" -ne 1 ]]; then
  echo "GAMEPLAY FAIL: combat resolved without observable authoritative turn advance" | tee "$OUT/result.txt"; exit 1
fi
validate_actor_sequence | tee "$OUT/actor-validation.txt"
echo "PASS: real emulator observed Entity combat, locked Explore during combat, AUTO combat turn progression, actor rotation, and return to gameplay" | tee "$OUT/result.txt"

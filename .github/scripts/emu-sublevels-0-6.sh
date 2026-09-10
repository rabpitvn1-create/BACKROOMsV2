#!/usr/bin/env bash
set -euo pipefail

PACKAGE="com.rabpit.backroom"
COMPONENT="$PACKAGE/.MainActivity"
APK="${1:-Backroom-under-test.apk}"
CATALOG="android-apk/app/src/main/assets/knowledge/sublevels_0_6_source.json"
OUT="${EMU_OUT:-emu-sublevels-0-6-results}"
START_TIMEOUT_SECONDS="${START_TIMEOUT_SECONDS:-20}"
mkdir -p "$OUT/ui"
: > "$OUT/fixtures.log"
: > "$OUT/entity-roaming-contract.log"

fail() {
  echo "FAIL: $*" | tee "$OUT/result.txt" >&2
  adb logcat -d > "$OUT/logcat.txt" 2>/dev/null || true
  adb shell dumpsys activity activities > "$OUT/activity.txt" 2>/dev/null || true
  exit 1
}

is_app_resumed() {
  adb shell dumpsys activity activities 2>/dev/null \
    | grep -E -q "(mResumedActivity|topResumedActivity).*${PACKAGE}/.MainActivity"
}

wait_resumed() {
  local deadline=$((SECONDS + START_TIMEOUT_SECONDS))
  while (( SECONDS < deadline )); do
    is_app_resumed && return 0
    sleep 1
  done
  return 1
}

dump_ui() {
  local name="$1"
  adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
  adb pull /sdcard/window.xml "$OUT/ui/${name}.xml" >/dev/null 2>&1 || true
}

seed_fixture() {
  local parent="$1" sublevel_id="$2" designation="$3" sublevel_name="$4" fixture="$5"
  local seed_xml="$OUT/seed.xml"
  python3 - "$seed_xml" "$parent" "$sublevel_id" "$designation" "$sublevel_name" <<'PY'
import json, sys, xml.etree.ElementTree as ET

out, parent_raw, sublevel_id, designation, sublevel_name = sys.argv[1:]
parent = int(parent_raw)
parent_names = {
    0: "The Lobby",
    1: "Parking Zone",
    2: "Pipe Dreams",
    3: "The Electrical Station",
    4: "The Abandoned Office",
    5: "Terror Hotel",
    6: "Lights Out",
}
parent_name = parent_names[parent]
if sublevel_id:
    location = f"Level {parent} / {parent_name} — {designation}: {sublevel_name}"
    title = f"{designation} – {sublevel_name}"
else:
    location = f"Level {parent} / {parent_name} — parent fixture"
    title = f"Level {parent} – {parent_name}"
flags = {
    "currentLevel": {"number": parent, "name": parent_name},
    "exploration": {"sublevelId": sublevel_id, "levelTurns": 0, "minimumTurns": 6},
    "entityEncountersAllowed": True,
}
state = {
    "saveVersion": 3,
    "world": {
        "location": location,
        "title": title,
        "levelJson": json.dumps({"number": parent, "name": parent_name}, separators=(",", ":")),
        "flagsJson": json.dumps(flags, separators=(",", ":")),
    },
    "metadata": {
        "emulator.fixture": "sublevels-0-6",
        "emulator.snapshot": "",
    },
}
root = ET.Element("map")
node = ET.SubElement(root, "string", {"name": "game_state"})
node.text = json.dumps(state, separators=(",", ":"), ensure_ascii=False)
ET.ElementTree(root).write(out, encoding="utf-8", xml_declaration=True)
PY

  adb shell am force-stop "$PACKAGE" >/dev/null 2>&1 || true
  adb push "$seed_xml" /data/local/tmp/backroom_game_state_core.xml >/dev/null
  adb shell run-as "$PACKAGE" mkdir -p shared_prefs
  adb shell run-as "$PACKAGE" cp /data/local/tmp/backroom_game_state_core.xml shared_prefs/backroom_game_state_core.xml
  adb shell run-as "$PACKAGE" chmod 660 shared_prefs/backroom_game_state_core.xml >/dev/null 2>&1 || true
  adb shell am start -W -n "$COMPONENT" >/dev/null
  wait_resumed || fail "MainActivity did not resume for $fixture"
  sleep 1

  local persisted="$OUT/persisted.xml"
  adb exec-out run-as "$PACKAGE" cat shared_prefs/backroom_game_state_core.xml > "$persisted" \
    || fail "Could not read authoritative core prefs for $fixture"

  python3 - "$persisted" "$parent" "$sublevel_id" <<'PY'
import json, sys, xml.etree.ElementTree as ET
path, expected_parent, expected_sub = sys.argv[1], int(sys.argv[2]), sys.argv[3]
root = ET.parse(path).getroot()
node = next((n for n in root.findall("string") if n.attrib.get("name") == "game_state"), None)
if node is None or not node.text:
    raise SystemExit("game_state missing")
state = json.loads(node.text)
world = state.get("world") or {}
level = json.loads(world.get("levelJson") or "{}")
flags = json.loads(world.get("flagsJson") or "{}")
actual_parent = int(level.get("number", -1))
actual_sub = ((flags.get("exploration") or {}).get("sublevelId") or "")
if actual_parent != expected_parent:
    raise SystemExit(f"parent level mismatch: expected {expected_parent}, got {actual_parent}")
if actual_sub != expected_sub:
    raise SystemExit(f"sublevel mismatch: expected {expected_sub!r}, got {actual_sub!r}")
if state.get("metadata", {}).get("emulator.snapshot", None) != "":
    raise SystemExit("snapshot fixture must remain empty")
PY

  dump_ui "$fixture"
  echo "PASS fixture=$fixture parent=$parent sublevel=${sublevel_id:-PARENT} snapshot=EMPTY" | tee -a "$OUT/fixtures.log"
}

# The final Entity runtime already rolls a single complete roaming pool on any gameplay
# action, without a parent-Level/sublevel allowlist. Assert that the finalizer still owns
# all canonical keys before running the APK fixtures.
python3 - <<'PY' | tee "$OUT/entity-roaming-contract.log"
from pathlib import Path
expected = {
    "hound", "clump", "duller", "deathmoth", "hostile_faceling", "false_puddle", "paintings",
    "smiler", "skin-stealer", "predatory_window", "biological_pipeline", "wretch", "cable_mimic",
    "the_beast_of_level_5", "hotel_corpse_lure", "jeff_the_killer", "jane_the_killer", "slenderman",
    "diep_minh",
}
unified = Path("android-apk/patch-unified-entity-spawn-pool.py").read_text(encoding="utf-8")
finalizer = Path("android-apk/patch-entity-rates-drops-final.py").read_text(encoding="utf-8")
missing = sorted(k for k in expected if k not in unified and k != "diep_minh")
if missing:
    raise SystemExit("missing roaming Entity keys: " + ", ".join(missing))
if 'pool = pool[:-1] + \,"diep_minh"}' not in finalizer and ',"diep_minh"}' not in finalizer:
    raise SystemExit("Diệp Minh is not appended to the final roaming pool")
if 'entityEncounterAction && entityAllowed && !emuProgressionFixture' not in finalizer:
    raise SystemExit("final Entity eligibility contract changed")
if any(token in finalizer for token in ("level == 0", "level == 1", "level == 2", "sublevelId")):
    raise SystemExit("final roaming Entity policy unexpectedly depends on Level/sublevel")
print(f"PASS roaming_entities={len(expected)} scope=ALL_LEVELS_AND_SUBLEVELS")
PY

[[ -s "$CATALOG" ]] || fail "Sublevel catalog missing: $CATALOG"
adb shell settings put secure immersive_mode_confirmations confirmed >/dev/null 2>&1 || true
adb shell settings put global hide_error_dialogs 1 >/dev/null 2>&1 || true
adb install -r "$APK" >/dev/null
adb logcat -c

adb shell run-as "$PACKAGE" id > "$OUT/run-as.txt" 2>&1 \
  || fail "Debug APK does not allow run-as; cannot seed authoritative fixtures"

# Parent Levels 0-6.
for parent in 0 1 2 3 4 5 6; do
  seed_fixture "$parent" "" "" "" "level-${parent}-parent"
done

# Every current wiki-listed sublevel under Levels 0-6.
while IFS=$'\t' read -r parent sublevel_id designation sublevel_name; do
  [[ -n "$sublevel_id" ]] || continue
  slug=$(printf '%s' "$sublevel_id" | tr '[:upper:].' '[:lower:]-' | tr -cd 'a-z0-9_-')
  seed_fixture "$parent" "$sublevel_id" "$designation" "$sublevel_name" "$slug"
done < <(python3 - "$CATALOG" <<'PY'
import json, sys
root = json.load(open(sys.argv[1], encoding="utf-8"))
for item in root["records"]:
    print(item["parentLevel"], item["id"], item["designation"].replace("\t", " "), item["name"].replace("\t", " "), sep="\t")
PY
)

parents=$(grep -c 'sublevel=PARENT' "$OUT/fixtures.log" || true)
sublevels=$(grep -c 'sublevel=SUBLEVEL\.' "$OUT/fixtures.log" || true)
[[ "$parents" -eq 7 ]] || fail "Expected 7 parent fixtures, got $parents"
[[ "$sublevels" -eq 33 ]] || fail "Expected 33 sublevel fixtures, got $sublevels"

echo "PASS: emulator verified 7 parent Levels + 33 sublevels; authoritative parent level preserved, sublevelId persisted, snapshot fixture empty, 19 Entities roam all Levels/sublevels." | tee "$OUT/result.txt"
adb logcat -d > "$OUT/logcat.txt" 2>/dev/null || true

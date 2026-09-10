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
: > "$OUT/catalog-contract.log"
: > "$OUT/entity-roaming-contract.log"
: > "$OUT/provider-order-contract.log"

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
  local difficulty_rating="$6" difficulty_source="$7" wiki_class="$8"
  local seed_xml="$OUT/seed.xml"
  python3 - "$seed_xml" "$parent" "$sublevel_id" "$designation" "$sublevel_name" \
    "$difficulty_rating" "$difficulty_source" "$wiki_class" <<'PY'
import json, sys, xml.etree.ElementTree as ET

(
    out, parent_raw, sublevel_id, designation, sublevel_name,
    difficulty_rating_raw, difficulty_source, wiki_class,
) = sys.argv[1:]
parent = int(parent_raw)
difficulty_rating = int(difficulty_rating_raw)
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
    "exploration": {
        "sublevelId": sublevel_id,
        "levelTurns": 0,
        "minimumTurns": 6,
        "difficulty": {
            "rating": difficulty_rating,
            "source": difficulty_source,
            "wikiClass": wiki_class,
        },
    },
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
        "emulator.difficulty": str(difficulty_rating),
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

  python3 - "$persisted" "$parent" "$sublevel_id" "$difficulty_rating" "$difficulty_source" "$wiki_class" <<'PY'
import json, sys, xml.etree.ElementTree as ET
path = sys.argv[1]
expected_parent = int(sys.argv[2])
expected_sub = sys.argv[3]
expected_rating = int(sys.argv[4])
expected_source = sys.argv[5]
expected_wiki_class = sys.argv[6]
root = ET.parse(path).getroot()
node = next((n for n in root.findall("string") if n.attrib.get("name") == "game_state"), None)
if node is None or not node.text:
    raise SystemExit("game_state missing")
state = json.loads(node.text)
world = state.get("world") or {}
level = json.loads(world.get("levelJson") or "{}")
flags = json.loads(world.get("flagsJson") or "{}")
exploration = flags.get("exploration") or {}
actual_parent = int(level.get("number", -1))
actual_sub = exploration.get("sublevelId") or ""
difficulty = exploration.get("difficulty") or {}
if actual_parent != expected_parent:
    raise SystemExit(f"parent level mismatch: expected {expected_parent}, got {actual_parent}")
if actual_sub != expected_sub:
    raise SystemExit(f"sublevel mismatch: expected {expected_sub!r}, got {actual_sub!r}")
if difficulty.get("rating") != expected_rating:
    raise SystemExit(f"difficulty rating mismatch: expected {expected_rating}, got {difficulty.get('rating')!r}")
if difficulty.get("source") != expected_source:
    raise SystemExit(f"difficulty source mismatch: expected {expected_source!r}, got {difficulty.get('source')!r}")
if difficulty.get("wikiClass") != expected_wiki_class:
    raise SystemExit(f"difficulty wikiClass mismatch: expected {expected_wiki_class!r}, got {difficulty.get('wikiClass')!r}")
if state.get("metadata", {}).get("emulator.snapshot", None) != "":
    raise SystemExit("snapshot fixture must remain empty")
PY

  dump_ui "$fixture"
  echo "PASS fixture=$fixture parent=$parent sublevel=${sublevel_id:-PARENT} difficulty=${difficulty_rating}/5 source=$difficulty_source wikiClass=$wiki_class snapshot=EMPTY" \
    | tee -a "$OUT/fixtures.log"
}

[[ -s "$CATALOG" ]] || fail "Sublevel catalog missing: $CATALOG"

# Validate the complete source contract before touching the emulator. This catches
# missing per-location difficulty, accidental snapshot population, and provenance
# regressions without conflating those failures with Android startup failures.
python3 - "$CATALOG" <<'PY' | tee "$OUT/catalog-contract.log"
import json, sys
root = json.load(open(sys.argv[1], encoding="utf-8"))
allowed = {"WIKI_DIRECT", "WIKI_NONSTANDARD_MAPPED", "PROJECT_DESIGNED"}
if root.get("schemaVersion") != 2:
    raise SystemExit("catalog schemaVersion must be 2")
parents = root.get("parentDifficulties") or []
records = root.get("records") or []
if len(parents) != 7 or {x.get("parentLevel") for x in parents} != set(range(7)):
    raise SystemExit("expected exactly 7 parent difficulty profiles")
if len(records) != 33:
    raise SystemExit(f"expected exactly 33 sublevels, got {len(records)}")

def validate(owner, value):
    if not isinstance(value, dict):
        raise SystemExit(f"difficulty missing: {owner}")
    rating = value.get("rating")
    source = value.get("source")
    wiki_class = value.get("wikiClass")
    if type(rating) is not int or not 1 <= rating <= 5:
        raise SystemExit(f"difficulty rating invalid: {owner}={rating!r}")
    if source not in allowed:
        raise SystemExit(f"difficulty source invalid: {owner}={source!r}")
    if not wiki_class or not value.get("rationale"):
        raise SystemExit(f"difficulty provenance incomplete: {owner}")
    if source == "WIKI_DIRECT" and wiki_class != f"CLASS {rating}":
        raise SystemExit(f"direct Wiki class/rating mismatch: {owner}")

for item in parents:
    validate(f"Level {item['parentLevel']}", item.get("difficulty"))
for item in records:
    if item.get("snapshot") != "":
        raise SystemExit(f"sublevel snapshot must remain empty: {item.get('id')}")
    validate(item.get("id", "?"), item.get("difficulty"))
policy = root.get("difficultyPolicy") or {}
roaming = policy.get("roamingEntityOverride", "")
if "every parent Level 0-6" not in roaming or "every listed sublevel" not in roaming:
    raise SystemExit("global Entity roaming override missing from catalog")
if "including Levels 0, 4 and 6" not in root.get("projectRule", ""):
    raise SystemExit("Level 0/4/6 roaming override missing from projectRule")
print("PASS difficulty_profiles=40 scale=1-5 snapshots=33_EMPTY provenance=VALIDATED roaming_override=L0_L4_L6_INCLUDED")
PY

# The final Entity runtime rolls the complete roaming pool without a parent-Level
# or sublevel allowlist. Explicitly protect the user's Level 0/4/6 override.
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
sublevel_patch = Path("android-apk/patch-web-sublevel-canon.py").read_text(encoding="utf-8")
missing = sorted(k for k in expected if k not in unified and k != "diep_minh")
if missing:
    raise SystemExit("missing roaming Entity keys: " + ", ".join(missing))
if '"diep_minh"' not in finalizer:
    raise SystemExit("Diệp Minh is not appended to the final roaming pool")
if 'entityEncounterAction && entityAllowed && !emuProgressionFixture' not in finalizer:
    raise SystemExit("final Entity eligibility contract changed")
if any(token in finalizer for token in ("level == 0", "level == 1", "level == 2", "level == 4", "level == 6", "sublevelId")):
    raise SystemExit("final roaming Entity policy unexpectedly depends on Level/sublevel")
if "không ngoại lệ Level 0, 4 hay 6" not in sublevel_patch:
    raise SystemExit("runtime knowledge prompt lost the Level 0/4/6 roaming override")
print(f"PASS roaming_entities={len(expected)} scope=ALL_LEVELS_AND_SUBLEVELS level_exceptions=NONE")
PY

# Gemini must own the first writer/auditor attempt. Haiku is eligible only after
# the corresponding Gemini path has failed. This is a deterministic contract check;
# the emulator regression does not spend network/API quota merely to prove ordering.
python3 - <<'PY' | tee "$OUT/provider-order-contract.log"
from pathlib import Path
text = Path("android-apk/patch-haiku-provider-final.py").read_text(encoding="utf-8")
writer_start = text.find('new_generate = r\'\'\'')
writer_end = text.find("'''", writer_start + len("new_generate = r'''"))
if writer_start < 0 or writer_end < 0:
    raise SystemExit("writer provider block not found")
writer = text[writer_start:writer_end]
gemini_writer = writer.find("String geminiResult = geminiText(prompt);")
haiku_writer = writer.find("String haikuResult = haikuText(prompt, 1800, 0.6);")
if gemini_writer < 0 or haiku_writer < 0 or gemini_writer >= haiku_writer:
    raise SystemExit("writer provider order is not Gemini -> Haiku fallback")
audit_start = text.find("private String auditText(String prompt, int excludedGeminiWorker)")
audit_end = text.find("private String generateText(String prompt)", audit_start)
if audit_start < 0 or audit_end < 0:
    raise SystemExit("auditor provider block not found")
audit = text[audit_start:audit_end]
gemini_audit = audit.find("geminiAuditText(prompt, excludedGeminiWorker)")
haiku_audit = audit.find("haikuText(prompt, 650, 0.1)")
if gemini_audit < 0 or haiku_audit < 0 or gemini_audit >= haiku_audit:
    raise SystemExit("auditor provider order is not Gemini -> Haiku fallback")
print("PASS writer=GEMINI_PRIMARY->HAIKU_FALLBACK auditor=GEMINI_PRIMARY->HAIKU_FALLBACK")
PY

adb shell settings put secure immersive_mode_confirmations confirmed >/dev/null 2>&1 || true
adb shell settings put global hide_error_dialogs 1 >/dev/null 2>&1 || true
adb install -r "$APK" >/dev/null
adb logcat -c

adb shell run-as "$PACKAGE" id > "$OUT/run-as.txt" 2>&1 \
  || fail "Debug APK does not allow run-as; cannot seed authoritative fixtures"

# Parent Levels 0-6, each with its own difficulty profile.
while IFS=$'\t' read -r parent difficulty_rating difficulty_source wiki_class; do
  [[ -n "$parent" ]] || continue
  seed_fixture "$parent" "" "" "" "level-${parent}-parent" \
    "$difficulty_rating" "$difficulty_source" "$wiki_class"
done < <(python3 - "$CATALOG" <<'PY'
import json, sys
root = json.load(open(sys.argv[1], encoding="utf-8"))
for item in root["parentDifficulties"]:
    d = item["difficulty"]
    print(item["parentLevel"], d["rating"], d["source"], d["wikiClass"].replace("\t", " "), sep="\t")
PY
)

# Every current wiki-listed sublevel under Levels 0-6, preserving empty snapshot.
while IFS=$'\t' read -r parent sublevel_id designation sublevel_name difficulty_rating difficulty_source wiki_class; do
  [[ -n "$sublevel_id" ]] || continue
  slug=$(printf '%s' "$sublevel_id" | tr '[:upper:].' '[:lower:]-' | tr -cd 'a-z0-9_-')
  seed_fixture "$parent" "$sublevel_id" "$designation" "$sublevel_name" "$slug" \
    "$difficulty_rating" "$difficulty_source" "$wiki_class"
done < <(python3 - "$CATALOG" <<'PY'
import json, sys
root = json.load(open(sys.argv[1], encoding="utf-8"))
for item in root["records"]:
    d = item["difficulty"]
    print(
        item["parentLevel"],
        item["id"],
        item["designation"].replace("\t", " "),
        item["name"].replace("\t", " "),
        d["rating"],
        d["source"],
        d["wikiClass"].replace("\t", " "),
        sep="\t",
    )
PY
)

parents=$(grep -c 'sublevel=PARENT' "$OUT/fixtures.log" || true)
sublevels=$(grep -c 'sublevel=SUBLEVEL\.' "$OUT/fixtures.log" || true)
[[ "$parents" -eq 7 ]] || fail "Expected 7 parent fixtures, got $parents"
[[ "$sublevels" -eq 33 ]] || fail "Expected 33 sublevel fixtures, got $sublevels"

echo "PASS: emulator verified 7 parent Levels + 33 sublevels; 40 difficulty profiles persisted with provenance, authoritative parent level/sublevelId preserved, 33 sublevel snapshots empty, 19 Entities roam all Levels/sublevels with no Level 0/4/6 exception, Gemini primary and Haiku fallback ordering verified." \
  | tee "$OUT/result.txt"
adb logcat -d > "$OUT/logcat.txt" 2>/dev/null || true

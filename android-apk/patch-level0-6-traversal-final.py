from pathlib import Path
import json

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
CATALOG = ROOT / "app/src/main/assets/knowledge/sublevels_0_6_source.json"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return text.replace(old, new, 1)


def java_quote(value: str) -> str:
    return json.dumps(value, ensure_ascii=False)


catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
parents = sorted(catalog.get("parentDifficulties") or [], key=lambda item: item["parentLevel"])
records = catalog.get("records") or []
if len(parents) != 7 or [item["parentLevel"] for item in parents] != list(range(7)):
    raise RuntimeError("Level 0-6 traversal requires exactly seven parent difficulty profiles")
if len(records) != 33:
    raise RuntimeError(f"Level 0-6 traversal requires 33 sublevels, found {len(records)}")

by_parent = {level: [] for level in range(7)}
for record in records:
    parent = int(record["parentLevel"])
    if parent not in by_parent:
        raise RuntimeError(f"Sublevel parent outside Level 0-6: {record.get('id')}")
    by_parent[parent].append(record)

expected_counts = {0: 12, 1: 4, 2: 3, 3: 2, 4: 3, 5: 3, 6: 6}
if {level: len(items) for level, items in by_parent.items()} != expected_counts:
    raise RuntimeError("Sublevel traversal catalog counts changed unexpectedly")

parent_names = {int(item["parentLevel"]): item["name"] for item in parents}
parent_locations = {
    0: "Level 0 / The Lobby — khu sảnh vàng và hành lang huỳnh quang",
    1: "Level 1 / Parking Zone — vùng chuyển tiếp bê tông, cột và vạch sơn",
    2: "Level 2 / Pipe Dreams — hành lang kỹ thuật hẹp với đường ống và tiếng máy",
    3: "Level 3 / The Electrical Station — trạm điện công nghiệp với máy biến áp và dây cao áp",
    4: "Level 4 / The Abandoned Office — khu văn phòng bỏ hoang với cubicle và tiếng mưa",
    5: "Level 5 / Terror Hotel — hành lang khách sạn cũ với thảm dày và ánh đèn vàng",
    6: "Level 6 / Lights Out — vùng tundra tối ngoài trời, lạnh và gần như không có ánh sáng",
}

ids_cases = []
location_cases = []
difficulty_cases = []
for level in range(7):
    ids = ", ".join(java_quote(item["id"]) for item in by_parent[level])
    ids_cases.append(f"    if (level == {level}) return new String[] {{{ids}}};")
    for item in by_parent[level]:
        location = f"Level {level} / {parent_names[level]} — {item['designation']}: {item['name']}"
        location_cases.append(
            f"    if ({level} == level && {java_quote(item['id'])}.equals(sublevelId)) return {java_quote(location)};"
        )
        difficulty = item["difficulty"]
        difficulty_cases.append(
            "    if (level == %d && %s.equals(sublevelId)) return new JSONObject()"
            ".put(\"rating\", %d).put(\"source\", %s).put(\"wikiClass\", %s);"
            % (
                level,
                java_quote(item["id"]),
                int(difficulty["rating"]),
                java_quote(difficulty["source"]),
                java_quote(difficulty["wikiClass"]),
            )
        )

parent_difficulty_cases = []
for item in parents:
    level = int(item["parentLevel"])
    difficulty = item["difficulty"]
    parent_difficulty_cases.append(
        "    if (level == %d) return new JSONObject()"
        ".put(\"rating\", %d).put(\"source\", %s).put(\"wikiClass\", %s);"
        % (
            level,
            int(difficulty["rating"]),
            java_quote(difficulty["source"]),
            java_quote(difficulty["wikiClass"]),
        )
    )

parent_location_cases = [
    f"    if (level == {level}) return {java_quote(parent_locations[level])};" for level in range(7)
]

helper = r'''  private boolean emulatorLevel06TraversalAndroid() {
    return BuildConfig.DEBUG && getIntent().getBooleanExtra("emuLevel06Traversal", false);
  }

  private String emulatorCurrentSublevelIdAndroid(JSONObject state) {
    if (state == null) return "";
    JSONObject flags = state.optJSONObject("flags");
    JSONObject exploration = flags != null ? flags.optJSONObject("exploration") : null;
    return exploration == null ? "" : exploration.optString("sublevelId", "").trim();
  }

  private String[] emulatorSublevelIdsAndroid(int level) {
IDS_CASES
    return new String[0];
  }

  private String emulatorSublevelLocationAndroid(int level, String sublevelId) {
LOCATION_CASES
    return "";
  }

  private String canonicalParentLocationAndroid(int level) {
PARENT_LOCATION_CASES
    return "";
  }

  private JSONObject canonicalLocationDifficultyAndroid(int level, String sublevelId) throws Exception {
DIFFICULTY_CASES
PARENT_DIFFICULTY_CASES
    return new JSONObject();
  }

  private void syncCanonicalLocationDifficultyAndroid(JSONObject state) throws Exception {
    if (state == null) return;
    int level = currentLevel(state);
    if (level < 0 || level > 6) return;
    JSONObject flags = state.optJSONObject("flags");
    if (flags == null) flags = new JSONObject();
    JSONObject exploration = flags.optJSONObject("exploration");
    if (exploration == null) exploration = new JSONObject();
    String sublevelId = exploration.optString("sublevelId", "").trim();
    exploration.put("difficulty", canonicalLocationDifficultyAndroid(level, sublevelId));
    flags.put("exploration", exploration);
    state.put("flags", flags);
  }

  private String emulatorTraversalExpectedSublevelAndroid(JSONObject before) {
    if (!emulatorLevel06TraversalAndroid() || before == null) return null;
    int level = currentLevel(before);
    if (level < 0 || level > 6) return null;
    JSONObject flags = before.optJSONObject("flags");
    JSONObject exploration = flags != null ? flags.optJSONObject("exploration") : null;
    String current = exploration == null ? "" : exploration.optString("sublevelId", "").trim();
    int sweptParent = exploration == null ? -1 : exploration.optInt("emuSweptParentLevel", -1);
    boolean initialized = exploration != null && exploration.optBoolean("emuTraversalInitialized", false);
    String[] ids = emulatorSublevelIdsAndroid(level);
    if (ids.length == 0) return null;
    // The authored campaign currently starts the WebView in a later Level-0 child while the
    // authoritative debug core starts at the parent. The first fixture EXPLORE must therefore
    // establish its own sweep cursor at the first catalog child instead of inheriting story state.
    if (!initialized) return ids[0];
    if (current.isEmpty()) {
      if (sweptParent == level) return null;
      return ids[0];
    }
    for (int i = 0; i < ids.length; i++) {
      if (!ids[i].equals(current)) continue;
      return i + 1 < ids.length ? ids[i + 1] : "";
    }
    return null;
  }

  private boolean normalizeEmulatorSublevelTraversalAndroid(JSONObject before, JSONObject generated, JSONObject rolls) throws Exception {
    if (!emulatorLevel06TraversalAndroid() || before == null || generated == null || rolls == null) return false;
    if (!"EXPLORE".equalsIgnoreCase(rolls.optString("actionKind", ""))) return false;
    String target = emulatorTraversalExpectedSublevelAndroid(before);
    if (target == null) return false;

    int level = currentLevel(before);
    JSONObject flags = before.optJSONObject("flags");
    JSONObject exploration = flags != null ? flags.optJSONObject("exploration") : null;
    int sweptParent = exploration == null ? -1 : exploration.optInt("emuSweptParentLevel", -1);
    String location = target.isEmpty()
      ? canonicalParentLocationAndroid(level)
      : emulatorSublevelLocationAndroid(level, target);
    if (location.isEmpty()) throw new Exception("Emulator traversal location missing for " + target);

    JSONArray proposed = generated.optJSONArray("ops");
    if (proposed == null) proposed = new JSONArray();
    JSONArray normalized = new JSONArray();
    for (int i = 0; i < Math.min(22, proposed.length()); i++) {
      JSONObject op = proposed.optJSONObject(i);
      if (op == null) continue;
      String type = lower(op.optString("type", "")).trim();
      if (type.equals("set_level") || type.equals("set_location")) continue;
      if (type.equals("flag_patch") && "exploration".equals(op.optString("root", ""))) continue;
      normalized.put(op);
    }

    normalized.put(new JSONObject().put("type", "set_location").put("value", location));
    JSONObject explorationPatch = new JSONObject()
      .put("sublevelId", target)
      .put("difficulty", canonicalLocationDifficultyAndroid(level, target))
      .put("emuTraversalInitialized", true);
    if (target.isEmpty()) explorationPatch.put("emuSweptParentLevel", level);
    else if (sweptParent >= 0) explorationPatch.put("emuSweptParentLevel", sweptParent);
    normalized.put(new JSONObject()
      .put("type", "flag_patch")
      .put("root", "exploration")
      .put("value", explorationPatch));
    generated.put("ops", normalized);
    return true;
  }

  private boolean engineOwnedEmulatorSublevelTraversalAndroid(JSONObject before, JSONObject candidate, JSONObject rolls) {
    if (!emulatorLevel06TraversalAndroid() || before == null || candidate == null || rolls == null) return false;
    if (!"EXPLORE".equalsIgnoreCase(rolls.optString("actionKind", ""))) return false;
    String target = emulatorTraversalExpectedSublevelAndroid(before);
    if (target == null) return false;
    int level = currentLevel(before);
    if (currentLevel(candidate) != level) return false;
    if (!target.equals(emulatorCurrentSublevelIdAndroid(candidate))) return false;
    String expectedLocation = target.isEmpty()
      ? canonicalParentLocationAndroid(level)
      : emulatorSublevelLocationAndroid(level, target);
    return expectedLocation.equals(candidate.optString("location", ""));
  }

'''
helper = helper.replace("IDS_CASES", "\n".join(ids_cases))
helper = helper.replace("PARENT_LOCATION_CASES", "\n".join(parent_location_cases))
helper = helper.replace("LOCATION_CASES", "\n".join(location_cases))
helper = helper.replace("PARENT_DIFFICULTY_CASES", "\n".join(parent_difficulty_cases))
helper = helper.replace("DIFFICULTY_CASES", "\n".join(difficulty_cases))

main = MAIN.read_text(encoding="utf-8")

old_location = '''  private String canonicalTransitionLocationAndroid(int level) {
    if (level == 1) return "Level 1 / Parking Zone — vùng chuyển tiếp bê tông, cột và vạch sơn";
    if (level == 2) return "Level 2 / Pipe Dreams — hành lang kỹ thuật hẹp với đường ống và tiếng máy";
    return "";
  }
'''
new_location = '''  private String canonicalTransitionLocationAndroid(int level) {
    if (level == 1) return "Level 1 / Parking Zone — vùng chuyển tiếp bê tông, cột và vạch sơn";
    if (level == 2) return "Level 2 / Pipe Dreams — hành lang kỹ thuật hẹp với đường ống và tiếng máy";
    if (level == 3) return "Level 3 / The Electrical Station — trạm điện công nghiệp với máy biến áp và dây cao áp";
    if (level == 4) return "Level 4 / The Abandoned Office — khu văn phòng bỏ hoang với cubicle và tiếng mưa";
    if (level == 5) return "Level 5 / Terror Hotel — hành lang khách sạn cũ với thảm dày và ánh đèn vàng";
    if (level == 6) return "Level 6 / Lights Out — vùng tundra tối ngoài trời, lạnh và gần như không có ánh sáng";
    return "";
  }
'''
main = replace_once(main, old_location, new_location, "Level 0-6 canonical transition locations")

main = replace_once(
    main,
    "    if (oldLevel < 0 || oldLevel >= 2) return;",
    "    if (oldLevel < 0 || oldLevel >= 6) return;",
    "Level transition normalize cap",
)
main = replace_once(
    main,
    "    if (oldLevel < 0 || oldLevel >= 2) return false;",
    "    if (oldLevel < 0 || oldLevel >= 6) return false;",
    "Level transition engine-owned cap",
)
main = replace_once(
    main,
    "      beforeLevel < 2 && claimed == beforeLevel + 1;",
    "      beforeLevel < 6 && claimed == beforeLevel + 1;",
    "Level discovery narrative range",
)
main = main.replace(
    "set_location canon cho Level 0→1 và Level 1→2;",
    "set_location canon cho chuỗi Level 0→1→2→3→4→5→6;",
)

# A parent transition invalidates any child-sublevel identity. The debug sweep marker is
# intentionally retained; it records which parent was completed and naturally becomes stale
# after the integer parent changes.
main = replace_once(
    main,
    '      exploration.put("levelTurns", 0);\n      exploration.remove("transitionReady");',
    '      exploration.put("levelTurns", 0);\n      exploration.put("sublevelId", "");\n      exploration.remove("transitionReady");',
    "Clear sublevel on parent transition",
)

helper_anchor = "  private boolean transitionReadyAndroid(JSONObject state) {\n"
if "private boolean emulatorLevel06TraversalAndroid()" not in main:
    main = replace_once(main, helper_anchor, helper + helper_anchor, "Level 0-6 emulator helper injection")

normalize_anchor = '''    boolean typedExplore = "EXPLORE".equalsIgnoreCase(rolls.optString("actionKind", ""));
    if (!typedExplore || !progressionReady(before)) return;
'''
normalize_replacement = '''    boolean typedExplore = "EXPLORE".equalsIgnoreCase(rolls.optString("actionKind", ""));
    if (typedExplore && normalizeEmulatorSublevelTraversalAndroid(before, generated, rolls)) return;
    if (!typedExplore || !progressionReady(before)) return;
'''
main = replace_once(main, normalize_anchor, normalize_replacement, "Sublevel traversal before parent progression gate")

# Keep the legacy Level-1 fixture intact. The new fixture guarantees parent exit probes only
# while it is on the parent Level; child sublevels are traversed without random exit leakage.
old_exit_fixture = '''    if (BuildConfig.DEBUG && getIntent().getBooleanExtra("emuLevel1Progression", false)
        && exploreAction && levelTurns(state) >= 6) {
      exitThreshold = 10000;
    }
'''
new_exit_fixture = '''    boolean emuLevel06Traversal = BuildConfig.DEBUG && getIntent().getBooleanExtra("emuLevel06Traversal", false);
    if (emuLevel06Traversal && !emulatorCurrentSublevelIdAndroid(state).isEmpty()) {
      exitThreshold = 0;
    } else if (BuildConfig.DEBUG &&
        (getIntent().getBooleanExtra("emuLevel1Progression", false) || emuLevel06Traversal) &&
        exploreAction && levelTurns(state) >= 6) {
      exitThreshold = 10000;
    }
'''
main = replace_once(main, old_exit_fixture, new_exit_fixture, "Deterministic parent-only Level 0-6 exit probes")

# The new traversal fixture tests progression rather than combat randomness, matching the existing
# Level-1 fixture isolation without changing production Entity eligibility.
old_entity_fixture = '''    boolean emuProgressionFixture = BuildConfig.DEBUG &&
      getIntent().getBooleanExtra("emuLevel1Progression", false);
'''
new_entity_fixture = '''    boolean emuProgressionFixture = BuildConfig.DEBUG &&
      (getIntent().getBooleanExtra("emuLevel1Progression", false) ||
       getIntent().getBooleanExtra("emuLevel06Traversal", false));
'''
main = replace_once(main, old_entity_fixture, new_entity_fixture, "Level 0-6 progression Entity isolation")

# Canon difficulty belongs to the authoritative location, not to model prose. Apply the catalog
# profile after both first-pass and repaired reducers so every real parent/sublevel visit persists it.
initial_candidate = '''          JSONObject candidateState = meta
            ? new JSONObject(before.toString())
            : applyModelOperations(before, generated.optJSONArray("ops"), rolls, action);
'''
initial_candidate_new = initial_candidate + '''          if (!meta) syncCanonicalLocationDifficultyAndroid(candidateState);
'''
main = replace_once(main, initial_candidate, initial_candidate_new, "Initial location difficulty synchronization")

repair_candidate = '            candidateState = applyModelOperations(before, generated.optJSONArray("ops"), rolls, action);\n'
repair_candidate_new = repair_candidate + '            syncCanonicalLocationDifficultyAndroid(candidateState);\n'
main = replace_once(main, repair_candidate, repair_candidate_new, "Repaired location difficulty synchronization")

initial_risk = '''          int risk = (meta || engineOwnedReadyExploreTransitionAndroid(before, candidateState, rolls))
            ? 0 : validatedTurnRisk(before, candidateState, generated);
'''
initial_risk_new = '''          int risk = (meta || engineOwnedReadyExploreTransitionAndroid(before, candidateState, rolls) ||
              engineOwnedEmulatorSublevelTraversalAndroid(before, candidateState, rolls))
            ? 0 : validatedTurnRisk(before, candidateState, generated);
'''
main = replace_once(main, initial_risk, initial_risk_new, "Initial engine-owned sublevel audit bypass")

repair_risk = '''            risk = engineOwnedReadyExploreTransitionAndroid(before, candidateState, rolls)
              ? 0 : validatedTurnRisk(before, candidateState, generated);
'''
repair_risk_new = '''            risk = (engineOwnedReadyExploreTransitionAndroid(before, candidateState, rolls) ||
                engineOwnedEmulatorSublevelTraversalAndroid(before, candidateState, rolls))
              ? 0 : validatedTurnRisk(before, candidateState, generated);
'''
main = replace_once(main, repair_risk, repair_risk_new, "Repair engine-owned sublevel audit bypass")

# Preserve diagnostics for both progression workflows.
old_diag = 'if (BuildConfig.DEBUG && getIntent().getBooleanExtra("emuLevel1Progression", false)) {'
new_diag = 'if (BuildConfig.DEBUG && (getIntent().getBooleanExtra("emuLevel1Progression", false) || getIntent().getBooleanExtra("emuLevel06Traversal", false))) {'
if old_diag in main:
    main = main.replace(old_diag, new_diag)

for required in (
    "emuLevel06Traversal",
    "emuTraversalInitialized",
    "normalizeEmulatorSublevelTraversalAndroid",
    "engineOwnedEmulatorSublevelTraversalAndroid",
    "syncCanonicalLocationDifficultyAndroid",
    "canonicalLocationDifficultyAndroid",
    "SUBLEVEL.00.EPSILON",
    "SUBLEVEL.06.99",
    "oldLevel >= 6",
    "beforeLevel < 6",
    "Level 6 / Lights Out — vùng tundra tối ngoài trời",
):
    if required not in main:
        raise RuntimeError("Level 0-6 traversal final marker missing: " + required)

for stale_cap in (
    'if (oldLevel < 0 || oldLevel >= 2) return;',
    'if (oldLevel < 0 || oldLevel >= 2) return false;',
):
    if stale_cap in main:
        raise RuntimeError("Level transition runtime is still capped at Level 2: " + stale_cap)

MAIN.write_text(main, encoding="utf-8")
print("Level 0-6 traversal final applied: real EXPLORE can progress through every parent Level, the debug emulator walks all catalog sublevels without state seeding, and catalog difficulty follows authoritative location state.")

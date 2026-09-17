from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
main = MAIN.read_text(encoding="utf-8")

old_cache = "function cachedSnapshot(){try{var r=JSON.parse(localStorage.getItem('backroom-apk-snapshot')||'null');return r&&r.dataUri?r:null;}catch(e){return null;}}function renderSnapshot()"
new_cache = "function visualSceneKey(){var l=state&&state.level&&state.level.number;var where=String(state&&state.location||'').trim().toLowerCase();return String(l==null?'?':l)+'|'+where}function cachedSnapshot(){try{var r=JSON.parse(localStorage.getItem('backroom-apk-snapshot')||'null');return r&&r.dataUri&&r.sceneKey===visualSceneKey()?r:null;}catch(e){return null;}}function renderSnapshot()"
if new_cache not in main:
    if old_cache not in main:
        raise RuntimeError("Snapshot cache anchor missing")
    main = main.replace(old_cache, new_cache, 1)
if "sceneKey:visualSceneKey()" not in main:
    marker = "JSON.stringify({turn:r.turn,model:r.model||'AI',dataUri:r.dataUri})"
    if marker not in main:
        raise RuntimeError("Snapshot cache write anchor missing")
    main = main.replace(marker, "JSON.stringify({turn:r.turn,sceneKey:visualSceneKey(),model:r.model||'AI',dataUri:r.dataUri})", 1)

current_level_anchor = '''  private JSONObject rollSpec(String label, int chance, boolean eligible) throws Exception {
'''
level_helpers = '''  private int mentionedLevel(JSONObject state) {
    String location = state.optString("location", "").toLowerCase(java.util.Locale.ROOT);
    String title = state.optString("title", "").toLowerCase(java.util.Locale.ROOT);
    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("level\\s*([0-6])", java.util.regex.Pattern.CASE_INSENSITIVE);
    java.util.regex.Matcher explicit = pattern.matcher(location);
    if (explicit.find()) return Integer.parseInt(explicit.group(1));
    String[] names = {"the lobby", "parking zone", "pipe dreams", "the electrical station", "the abandoned office", "terror hotel", "lights out"};
    for (int n = 0; n < names.length; n++) if (location.contains(names[n])) return n;
    explicit = pattern.matcher(title);
    if (explicit.find()) return Integer.parseInt(explicit.group(1));
    for (int n = 0; n < names.length; n++) if (title.contains(names[n])) return n;
    return -1;
  }

  private int levelTurns(JSONObject state) {
    JSONObject flags = state.optJSONObject("flags");
    JSONObject exploration = flags != null ? flags.optJSONObject("exploration") : null;
    return exploration != null ? Math.max(0, exploration.optInt("levelTurns", 0)) : 0;
  }

  private boolean progressionReady(JSONObject state) {
    JSONObject flags = state.optJSONObject("flags");
    JSONObject exploration = flags != null ? flags.optJSONObject("exploration") : null;
    boolean explicitlyReady = exploration != null && (exploration.optBoolean("transitionReady", false) || exploration.optBoolean("exitReady", false));
    return explicitlyReady || levelTurns(state) >= 6;
  }

  private void recordLevelProgress(JSONObject state, int oldLevel, int newLevel) throws Exception {
    JSONObject flags = state.optJSONObject("flags");
    if (flags == null) flags = new JSONObject();
    JSONObject exploration = flags.optJSONObject("exploration");
    if (exploration == null) exploration = new JSONObject();
    exploration.put("levelTurns", oldLevel == newLevel ? levelTurns(state) + 1 : 0);
    exploration.put("minimumTurns", 6);
    flags.put("exploration", exploration);
    state.put("flags", flags);
  }

'''
if "private int mentionedLevel(JSONObject state)" not in main:
    if current_level_anchor not in main:
        raise RuntimeError("Level helper anchor missing")
    main = main.replace(current_level_anchor, level_helpers + current_level_anchor, 1)

old_transition = '''    return (confirmedExit != null && !confirmedExit.trim().isEmpty()) || rollSuccess(rolls, "levelExit");
'''
new_transition = '''    boolean exitFound = (confirmedExit != null && !confirmedExit.trim().isEmpty()) || rollSuccess(rolls, "levelExit");
    return exitFound && progressionReady(before);
'''
if new_transition not in main:
    if old_transition not in main:
        raise RuntimeError("Transition gate anchor missing")
    main = main.replace(old_transition, new_transition, 1)

old_after_commit = '''          int oldLevel = currentLevel(before);
          int newLevel = currentLevel(state);
          boolean levelChanged = oldLevel != newLevel;
'''
new_after_commit = '''          int oldLevel = currentLevel(before);
          int newLevel = currentLevel(state);
          int mentioned = mentionedLevel(state);
          if (mentioned >= 0 && mentioned != oldLevel && canTransition(before, rolls)) {
            newLevel = mentioned;
            state.put("level", new JSONObject().put("number", newLevel).put("name", levelName(newLevel)));
            state.put("title", "Level " + newLevel + " – " + levelName(newLevel));
          }
          boolean levelChanged = oldLevel != newLevel;
'''
if new_after_commit not in main:
    if old_after_commit not in main:
        raise RuntimeError("Post-commit Level recognition anchor missing")
    main = main.replace(old_after_commit, new_after_commit, 1)

progress_anchor = '''            flags.put("currentLevel", new JSONObject().put("number", newLevel).put("name", levelName(newLevel)));
'''
progress_replacement = progress_anchor + '''            state.put("flags", flags);
            recordLevelProgress(state, oldLevel, newLevel);
            flags = state.optJSONObject("flags");
'''
if "recordLevelProgress(state, oldLevel, newLevel);" not in main:
    if progress_anchor not in main:
        raise RuntimeError("Progress recording anchor missing")
    main = main.replace(progress_anchor, progress_replacement, 1)

main = main.replace(
    '"EXPLORE HARD LOCK: chủ động mở rộng known space và có thể đổi location; có thể gặp Entity hoặc Survivor, resource/hazard/exit opportunity nhưng không đảm bảo Exit; nếu có lựa chọn định hướng quan trọng thì trả quyền quyết định cho người chơi. "',
    '"EXPLORE HARD LOCK: chủ động mở rộng known space từng khu vực; có thể đổi location cục bộ, gặp Entity hoặc Survivor, resource/hazard/exit opportunity nhưng không đảm bảo Exit. Không hoàn tất cả Level trong 2–3 lượt: cần ít nhất 6 lượt gameplay trong Level và một Exit hợp lệ; nếu có lựa chọn định hướng quan trọng thì trả quyền quyết định cho người chơi. "',
    1,
)

MAIN.write_text(main, encoding="utf-8")
for marker in (
    "sceneKey:visualSceneKey()",
    "r.sceneKey===visualSceneKey()",
    "private int mentionedLevel(JSONObject state)",
    "return exitFound && progressionReady(before);",
    'exploration.put("minimumTurns", 6)',
    "recordLevelProgress(state, oldLevel, newLevel)",
):
    if marker not in main:
        raise RuntimeError("Progression/Snapshot contract missing: " + marker)

for script in ("patch-pressure-combat.py", "patch-unified-entity-spawn-pool.py", "patch-character-healthbar.py"):
    path = ROOT / script
    if not path.is_file():
        raise RuntimeError(f"Required runtime patch missing: {script}")
    exec(compile(path.read_text(encoding="utf-8"), str(path), "exec"), {"__name__": "__main__", "__file__": str(path)})

# Cleanup removed two retired systems, but Iris and Syvial remain supported canonical followers.
# Keep their registry/loadouts authoritative at the central normalization boundary so New Game,
# save/load migration, inventory capacity and generated regression tests all see the same state.
equipment_system_path = ROOT / "app/src/main/java/com/rabpit/backroom/core/CharacterEquipmentSystem.kt"
equipment_system = equipment_system_path.read_text(encoding="utf-8")
old_normalization = '''  fun seedFresh(state: GameState): GameState = normalizeInternal(state, true)

  fun normalize(state: GameState): GameState = normalizeInternal(state, state.metadata["characterEquipmentSchemaVersion"] != SCHEMA_VERSION)
'''
new_normalization = '''  fun seedFresh(state: GameState): GameState = normalizeInternal(SpecialFollowersCanon.ensure(state), true)

  fun normalize(state: GameState): GameState {
    val ensured = SpecialFollowersCanon.ensure(state)
    return normalizeInternal(ensured, ensured.metadata["characterEquipmentSchemaVersion"] != SCHEMA_VERSION)
  }
'''
if new_normalization not in equipment_system:
    if equipment_system.count(old_normalization) != 1:
        raise RuntimeError("Supported follower normalization anchor missing")
    equipment_system = equipment_system.replace(old_normalization, new_normalization, 1)
equipment_system_path.write_text(equipment_system, encoding="utf-8")

# Final CI guard: nested patch scripts must not recreate retired runtime code, tests or assets.
retired_tokens = ("madgod", "an_nhien", "annhien", "an nhien", "an-nhien", "an nhiên")
app_src = ROOT / "app/src"
for path in app_src.rglob("*"):
    if not path.is_file():
        continue
    rel = str(path.relative_to(app_src)).lower()
    if any(token in rel for token in retired_tokens):
        raise RuntimeError(f"Retired runtime path recreated: {rel}")
    if path.suffix.lower() not in {".kt", ".java", ".html", ".json", ".txt", ".xml"}:
        continue
    text = path.read_text(encoding="utf-8", errors="ignore").lower()
    if any(token in text for token in retired_tokens):
        raise RuntimeError(f"Retired runtime content recreated in: {rel}")

for marker in (
    "normalizeInternal(SpecialFollowersCanon.ensure(state), true)",
    "val ensured = SpecialFollowersCanon.ensure(state)",
):
    if marker not in equipment_system:
        raise RuntimeError("Supported follower registry repair missing: " + marker)

print("Scene-keyed Snapshot cache, progression, combat and healthbar chain installed; Iris/Syvial registry normalized and retired runtime content blocked.")

"""Wire Android to Kotlin-owned gameplay/Entity encounter and reward contracts."""
from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
FACADE = ROOT / "app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt"


def once(source, old, new):
    if source.count(old) != 1:
        raise RuntimeError(f"Entity policy anchor count != 1: {old[:100]}")
    return source.replace(old, new, 1)


main = MAIN.read_text(encoding="utf-8")

# The settled pre-final runtime still contains legacy Java-side pool/rate selection.
# Replace the whole block with a bridge call. Pool membership and the independent
# encounter probability are authoritative in EntityEncounterPolicy.
start_marker = '    JSONObject normalEntityRoll = thresholdRoll("entityEncounter"'
if main.count(start_marker) != 1:
    raise RuntimeError(f"Settled Entity roll anchor count != 1: {main.count(start_marker)}")
start = main.index(start_marker)
end = main.index('    int luciaScoutBonus =', start)
main = main[:start] + '''    // The dedicated progression fixture verifies Level state flow, not combat RNG. Keep the
    // production Entity policy unchanged, but suppress unrelated random encounters only for the
    // explicit debug Activity extra used by the real-emulator progression workflow.
    boolean emuProgressionFixture = BuildConfig.DEBUG &&
      getIntent().getBooleanExtra("emuLevel1Progression", false);
    JSONObject entityChecks = com.rabpit.backroom.core.EntityEncounterPolicy.roll(
      exploreAction && entityAllowed && !emuProgressionFixture, GAME_RNG);
    java.util.Iterator<String> entityCheckKeys = entityChecks.keys();
    while (entityCheckKeys.hasNext()) {
      String key = entityCheckKeys.next();
      rolls.put(key, entityChecks.get(key));
    }
''' + main[end:]

# No level-rate array or suffix remains authoritative after Game Core rolls.
for legacy_rate_line in (
    '    int[] entityThresholds =',
    '    String entitySuffix =',
):
    lines = []
    for line in main.splitlines(keepends=True):
      if line.startswith(legacy_rate_line):
        continue
      lines.append(line)
    main = ''.join(lines)

start = main.index('  private void forceEntityEncounterFlag(JSONObject candidateState, JSONObject rolls) throws Exception {')
end = main.index('\n  private JSONObject resolveEntityOverlay(', start)
main = main[:start] + '''  private void forceEntityEncounterFlag(JSONObject candidateState, JSONObject rolls) throws Exception {
    if (candidateState == null || rolls == null) return;
    JSONArray keys = rolls.optJSONArray("entityEncounterKeys");
    if (keys == null || keys.length() == 0) return;
    requireGameCore().startEntityEncounters(candidateState.toString(), keys.toString());
  }
''' + main[end:]

# Remove obsolete single-roll / boss-priority prose. The writer only receives the
# already-authoritative Game Core result and must not reconstruct encounter policy.
lines = []
for line in main.splitlines(keepends=True):
    if any(marker in line for marker in ('"JEFF THE KILLER HARD LOCK:', '"ROAMING KILLER HARD LOCK:', '"ENTITY ROAMING HARD LOCK:')):
        if '"ENTITY ROAMING HARD LOCK:' in line:
            lines.append('      "ENTITY GAME CORE LOCK: entityRolls và entityEncounterKeys là kết quả authoritative từ Kotlin Game Core. Không tự thêm, bỏ, ưu tiên hay reroll Entity; combat xử lý đúng danh sách đã được Core trả về. Khi Entity bị tiêu diệt, SYSTEM/Core quyết định reward; không tự cấp item kill trong ops. " +\n')
    else:
        lines.append(line)
main = ''.join(lines)

# Retired Java-side encounter authority must never survive the final layer.
for forbidden in (
    'thresholdRoll("jeffEncounter"',
    '"JEFF THE KILLER HARD LOCK:',
    'int[] entityThresholds =',
    '8.0000%',
    '+8 percentage',
    'entityEncounterAction && entityAllowed',
    'diepMinhEncounter',
    'JSONObject normalEntityRoll = thresholdRoll("entityEncounter"',
    'rolls.put("roamingEntityKey"',
    'String[] entityPool =',
    'String[] roamingPool =',
):
    if forbidden in main:
        raise RuntimeError("Retired Java Entity encounter authority survived final canon: " + forbidden)

for required in (
    'EntityEncounterPolicy.roll(',
    'boolean emuProgressionFixture = BuildConfig.DEBUG',
    'getIntent().getBooleanExtra("emuLevel1Progression", false)',
    'exploreAction && entityAllowed && !emuProgressionFixture',
    'rolls.optJSONArray("entityEncounterKeys")',
    'startEntityEncounters(candidateState.toString(), keys.toString())',
    'ENTITY GAME CORE LOCK:',
):
    if required not in main:
        raise RuntimeError("Final Kotlin-owned Entity bridge missing: " + required)

MAIN.write_text(main, encoding="utf-8")

# Temporary compatibility wiring. Gameplay behavior is implemented by Kotlin
# EntityEncounterPolicy / CombatRuntime / EntityDrops; this patch only connects the
# legacy Activity pipeline to those Core entry points until MainActivity is thinned.
facade = FACADE.read_text(encoding="utf-8")
facade = once(facade, '  fun startCombatState(legacyStateJson: String, entityKey: String): String {', '''  fun startEntityEncounters(legacyStateJson: String, keysJson: String): String {
    val legacy = JSONObject(legacyStateJson)
    val keys = JSONArray(keysJson)
    val next = EntityEncounterPolicy.enqueue(loadOrMigrate(legacy),
      (0 until keys.length()).map { keys.getString(it) })
    repository.save(next)
    return syncLegacy(legacy, next, incrementTurn = false).toString()
  }

  fun startCombatState(legacyStateJson: String, entityKey: String): String {''')
facade = once(facade, '    var next = resolution.state\n', '''    var next = resolution.state
    if (resolution.entityDestroyed) {
      val reward = EntityDrops.award(next, CombatRuntime.active(current)!!.encounterId)
      next = reward.state
      resolution = resolution.copy(state = next, reply = resolution.reply + " " + reward.message)
    }
''')
# Resolve cleanup first, then start the next successful die without rolling again.
start = facade.index('  fun processCombat(')
end = facade.index('  private fun normalizeVisualPresence(', start)
combat = facade[start:end]
combat = once(combat, '    repository.save(next)\n', '''    if (resolution.entityDestroyed || resolution.escaped) {
      next = EntityEncounterPolicy.advance(next)
      CombatRuntime.active(next)?.let {
        resolution = resolution.copy(reply = resolution.reply + " Entity tiếp theo: ${it.entityName}.")
      }
    }
    repository.save(next)
''')
facade = facade[:start] + combat + facade[end:]
facade = once(facade, '    val normalized = normalizeVisualPresence(loaded)\n',
              '    val normalized = EntityDrops.claimPending(normalizeVisualPresence(loaded))\n')
FACADE.write_text(facade, encoding="utf-8")
print("Entity bridge applied: Kotlin Game Core owns the shared Entity pool, independent encounter dice, queue order and catalog kill drops.")

# These legacy transforms still need the settled Java method shape while they install their
# transition/debug adapters. Run them first; only after they finish do we collapse the final
# Android roll method to a pure Kotlin Game Core bridge.
import runpy
runpy.run_path(str(ROOT / "patch-level0-6-traversal-final.py"), run_name="__main__")
runpy.run_path(str(ROOT / "patch-gm-action-policy-final.py"), run_name="__main__")

# Kotlin GameplayRollPolicy now owns every final probability/eligibility decision, including
# EntityEncounterPolicy delegation. Java keeps only Android debug-extra plumbing and the RNG object.
main = MAIN.read_text(encoding="utf-8")
roll_start_marker = '  private JSONObject makeGameplayRolls(JSONObject state, String action, boolean meta) throws Exception {'
roll_end_marker = '  private boolean rollSuccess(JSONObject rolls, String key) {'
if main.count(roll_start_marker) != 1 or main.count(roll_end_marker) != 1:
    raise RuntimeError("Final gameplay-roll bridge anchors are not unique")
roll_start = main.index(roll_start_marker)
roll_end = main.index(roll_end_marker, roll_start)
roll_bridge = '''  private JSONObject makeGameplayRolls(JSONObject state, String action, boolean meta) throws Exception {
    return makeGameplayRolls(state, "EXECUTE", action, meta);
  }

  private JSONObject makeGameplayRolls(JSONObject state, String actionKind, String action, boolean meta) throws Exception {
    boolean emuLevel1Progression = BuildConfig.DEBUG &&
      getIntent().getBooleanExtra("emuLevel1Progression", false);
    boolean emuLevel06Traversal = BuildConfig.DEBUG &&
      getIntent().getBooleanExtra("emuLevel06Traversal", false);
    return com.rabpit.backroom.core.GameplayRollPolicy.roll(
      state.toString(), actionKind, action, meta, GAME_RNG,
      emuLevel1Progression, emuLevel06Traversal);
  }

'''
main = main[:roll_start] + roll_bridge + main[roll_end:]

for forbidden in (
    'int[] hazardThresholds =',
    'int[] lootThresholds =',
    'int[] waterThresholds =',
    'rolls.put("survivor", thresholdRoll(',
    'rolls.put("hazard", thresholdRoll(',
    'EntityEncounterPolicy.roll(',
):
    if forbidden in main[roll_start:main.index(roll_end_marker, roll_start)]:
        raise RuntimeError("Java gameplay-roll authority survived final bridge: " + forbidden)
for required in (
    'GameplayRollPolicy.roll(',
    'getIntent().getBooleanExtra("emuLevel1Progression", false)',
    'getIntent().getBooleanExtra("emuLevel06Traversal", false)',
    'state.toString(), actionKind, action, meta, GAME_RNG',
):
    if required not in main[roll_start:main.index(roll_end_marker, roll_start)]:
        raise RuntimeError("Kotlin gameplay-roll bridge missing: " + required)
MAIN.write_text(main, encoding="utf-8")
print("Gameplay roll bridge applied: final Android runtime delegates roll authority to Kotlin GameplayRollPolicy.")

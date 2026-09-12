"""Apply the final independent Entity dice/reward contract after legacy runtime generators."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
FACADE = ROOT / "app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt"


def once(source, old, new):
    if source.count(old) != 1:
        raise RuntimeError(f"Entity policy anchor count != 1: {old[:100]}")
    return source.replace(old, new, 1)


main = MAIN.read_text(encoding="utf-8")

# The settled pre-final runtime still has the old one-roll + roaming-pick block.
# Replace that whole block directly; Diệp Minh no longer owns a private encounter
# channel, so this finalizer must not depend on (or recreate) diepMinhEncounter.
start_marker = '    JSONObject normalEntityRoll = thresholdRoll("entityEncounter"'
if main.count(start_marker) != 1:
    raise RuntimeError(f"Settled Entity roll anchor count != 1: {main.count(start_marker)}")
start = main.index(start_marker)
end = main.index('    int luciaScoutBonus =', start)
legacy_block = main[start:end]
pool_match = re.search(r'String\[\] roamingPool = (\{[^\n]+\});', legacy_block)
if pool_match is None:
    raise RuntimeError("Settled shared roaming pool missing before final Entity policy")
pool = pool_match.group(1)
if '"diep_minh"' not in pool:
    pool = pool[:-1] + ',"diep_minh"}'

main = main[:start] + '''    String[] entityPool = POOL;
    // The dedicated progression fixture verifies Level state flow, not combat RNG. Keep the
    // production Entity policy unchanged, but suppress unrelated random encounters only for the
    // explicit debug Activity extra used by the real-emulator progression workflow.
    boolean emuProgressionFixture = BuildConfig.DEBUG &&
      getIntent().getBooleanExtra("emuLevel1Progression", false);
    JSONObject entityChecks = com.rabpit.backroom.core.EntityEncounterPolicy.roll(
      entityPool, exploreAction && entityAllowed && !emuProgressionFixture, GAME_RNG);
    java.util.Iterator<String> entityCheckKeys = entityChecks.keys();
    while (entityCheckKeys.hasNext()) {
      String key = entityCheckKeys.next();
      rolls.put(key, entityChecks.get(key));
    }
'''.replace("POOL", pool) + main[end:]

# No level-rate array or suffix remains authoritative after independent 3% dice.
main = re.sub(r'^    int\[\] entityThresholds = .*\n', '', main, flags=re.M)
main = re.sub(r'^    String entitySuffix = .*\n', '', main, flags=re.M)

start = main.index('  private void forceEntityEncounterFlag(JSONObject candidateState, JSONObject rolls) throws Exception {')
end = main.index('\n  private JSONObject resolveEntityOverlay(', start)
main = main[:start] + '''  private void forceEntityEncounterFlag(JSONObject candidateState, JSONObject rolls) throws Exception {
    if (candidateState == null || rolls == null) return;
    JSONArray keys = rolls.optJSONArray("entityEncounterKeys");
    if (keys == null || keys.length() == 0) return;
    requireGameCore().startEntityEncounters(candidateState.toString(), keys.toString());
  }
''' + main[end:]

# Remove obsolete single-roll / boss-priority prose from the generated GM prompt.
lines = []
for line in main.splitlines(keepends=True):
    if any(marker in line for marker in ('"JEFF THE KILLER HARD LOCK:', '"ROAMING KILLER HARD LOCK:', '"ENTITY ROAMING HARD LOCK:')):
        if '"ENTITY ROAMING HARD LOCK:' in line:
            lines.append('      "ENTITY ROAMING HARD LOCK: mỗi Entity kể cả Jeff, Jane và Diệp Minh roll độc lập 3% trong entityRolls. entityEncounter chỉ tổng hợp kết quả, không phải roll chung. entityEncounterKeys giữ tất cả Entity roll trúng; combat xử lý lần lượt theo danh sách. Không thêm Entity ngoài danh sách. Mỗi Entity bị tiêu diệt được SYSTEM cấp đúng một item ngẫu nhiên; không tự cấp thêm item từ kill trong ops. " +\n')
    else:
        lines.append(line)
main = ''.join(lines)

# Retired encounter-rate paths must never survive the final authority layer or
# silently regain control if patch order changes.
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
):
    if forbidden in main:
        raise RuntimeError("Retired Entity encounter logic survived final canon: " + forbidden)

for required in (
    'String[] entityPool = {',
    '"diep_minh"',
    'EntityEncounterPolicy.roll(',
    'boolean emuProgressionFixture = BuildConfig.DEBUG',
    'getIntent().getBooleanExtra("emuLevel1Progression", false)',
    'exploreAction && entityAllowed && !emuProgressionFixture',
    'rolls.optJSONArray("entityEncounterKeys")',
    'startEntityEncounters(candidateState.toString(), keys.toString())',
):
    if required not in main:
        raise RuntimeError("Final independent Entity policy missing: " + required)

MAIN.write_text(main, encoding="utf-8")

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
print("Entity policy applied: every Entity, including Diệp Minh, uses one independent 3% die on EXPLORE only; queued encounters and guaranteed catalog kill drops remain intact.")

# This runs last in the Android patch chain so the Level 0-6 traversal guard can
# extend the settled transition/provider/entity runtime without reviving legacy paths.
import runpy
runpy.run_path(str(ROOT / "patch-level0-6-traversal-final.py"), run_name="__main__")

# Re-assert the typed action contract after every nested runtime transformation has settled.
runpy.run_path(str(ROOT / "patch-gm-action-policy-final.py"), run_name="__main__")

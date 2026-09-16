from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
TEMPLATE = ROOT / "patch-runtime-debug-turn-trace-v2-final.py"

main = MAIN.read_text(encoding="utf-8")
source = TEMPLATE.read_text(encoding="utf-8")

# The checked-in/settled bridge now starts ActionRuntime before asking Kotlin for a local rule
# result, and patch-game-state-core-bridge materializes that result as coreRaw/coreResult. Keep
# this adapter responsible for that historical shape drift instead of forcing the debug template
# to rewrite the authoritative bridge back to its retired one-line localResult form.
settled_fast_path = '''          JSONObject actionStart = new JSONObject(requireGameCore().beginAction(stateJson, actionKind, action));
          if (!actionStart.optBoolean("handled", false)) {
            throw new Exception("Action Runtime từ chối hành động: " + actionStart.optString("error", "action_start_failed"));
          }
          String coreRaw = requireGameCore().processRule(stateJson, action);
          JSONObject coreResult = new JSONObject(coreRaw);
          if (coreResult.optBoolean("handled", false)) {
            emit("backroomTurn", coreResult.getJSONObject("state").toString());
            return;
          }
'''
if settled_fast_path in main:
    legacy_local_old = """local_old = '''          JSONObject localResult = new JSONObject(gameCore.processRule(stateJson, action));
          if (localResult.optBoolean(\"handled\", false)) {
            emit(\"backroomTurn\", localResult.getJSONObject(\"state\").toString());
            return;
          }
'''
"""
    settled_local_old = """local_old = '''          JSONObject actionStart = new JSONObject(requireGameCore().beginAction(stateJson, actionKind, action));
          if (!actionStart.optBoolean(\"handled\", false)) {
            throw new Exception(\"Action Runtime từ chối hành động: \" + actionStart.optString(\"error\", \"action_start_failed\"));
          }
          String coreRaw = gameCore.processRule(stateJson, action);
          JSONObject coreResult = new JSONObject(coreRaw);
          if (coreResult.optBoolean(\"handled\", false)) {
            emit(\"backroomTurn\", coreResult.getJSONObject(\"state\").toString());
            return;
          }
'''
"""
    legacy_local_new = """local_new = '''          JSONObject debugBefore = new JSONObject(stateJson);
          int debugTurn = debugBefore.optInt(\"turn\", 0);
          debugActiveTurn = debugTurn;
          boolean debugMeta = isMetaAction(action);
          com.rabpit.backroom.core.RuntimeDebugLog.beginTurn(debugTurn, action, debugMeta, debugBefore);
          debugEvent(\"game_core\", \"rule_request\", debugTurn, new JSONObject().put(\"action\", action).put(\"actionKind\", actionKind).put(\"actionOrigin\", actionOrigin).put(\"stateBefore\", debugBefore));
          String localRaw = gameCore.processRule(stateJson, action);
          JSONObject localResult = new JSONObject(localRaw);
          debugEvent(\"game_core\", \"rule_result\", debugTurn, localResult);
          if (localResult.optBoolean(\"handled\", false)) {
            JSONObject localState = localResult.getJSONObject(\"state\");
            com.rabpit.backroom.core.RuntimeDebugLog.recordTurnStage(debugTurn, \"gameCoreRule\", localResult);
            com.rabpit.backroom.core.RuntimeDebugLog.recordTurnStage(debugTurn, \"stateDiff\", debugJsonDiff(debugBefore, localState));
            com.rabpit.backroom.core.RuntimeDebugLog.finishTurn(debugTurn, localState, localResult.optString(\"reply\", \"\"), null);
            emit(\"backroomTurn\", localState.toString());
            return;
          }
'''
"""
    settled_local_new = """local_new = '''          JSONObject actionStart = new JSONObject(requireGameCore().beginAction(stateJson, actionKind, action));
          if (!actionStart.optBoolean(\"handled\", false)) {
            throw new Exception(\"Action Runtime từ chối hành động: \" + actionStart.optString(\"error\", \"action_start_failed\"));
          }
          JSONObject debugBefore = new JSONObject(stateJson);
          int debugTurn = debugBefore.optInt(\"turn\", 0);
          debugActiveTurn = debugTurn;
          boolean debugMeta = isMetaAction(action);
          com.rabpit.backroom.core.RuntimeDebugLog.beginTurn(debugTurn, action, debugMeta, debugBefore);
          debugEvent(\"game_core\", \"rule_request\", debugTurn, new JSONObject().put(\"action\", action).put(\"actionKind\", actionKind).put(\"actionOrigin\", actionOrigin).put(\"stateBefore\", debugBefore));
          String coreRaw = gameCore.processRule(stateJson, action);
          JSONObject coreResult = new JSONObject(coreRaw);
          debugEvent(\"game_core\", \"rule_result\", debugTurn, coreResult);
          if (coreResult.optBoolean(\"handled\", false)) {
            JSONObject localState = coreResult.getJSONObject(\"state\");
            com.rabpit.backroom.core.RuntimeDebugLog.recordTurnStage(debugTurn, \"gameCoreRule\", coreResult);
            com.rabpit.backroom.core.RuntimeDebugLog.recordTurnStage(debugTurn, \"stateDiff\", debugJsonDiff(debugBefore, localState));
            com.rabpit.backroom.core.RuntimeDebugLog.finishTurn(debugTurn, localState, coreResult.optString(\"reply\", \"\"), null);
            emit(\"backroomTurn\", localState.toString());
            return;
          }
'''
"""
    if source.count(legacy_local_old) != 1:
        raise RuntimeError("adaptive fast-path template old anchor missing")
    if source.count(legacy_local_new) != 1:
        raise RuntimeError("adaptive fast-path template new anchor missing")
    source = source.replace(legacy_local_old, settled_local_old, 1)
    source = source.replace(legacy_local_new, settled_local_new, 1)

# Later startup/action-policy patches can rewrite the settled bridge while preserving the same
# semantics. Adapt only the exact anchors that are known to drift, rather than duplicating another
# copy of the already-large tracing template.
if "requireGameCore().processRule(stateJson, action)" in main:
    source = source.replace("gameCore.processRule(stateJson, action)", "requireGameCore().processRule(stateJson, action)")
# Candidate-commit arity has grown as Kotlin took ownership of rolls and accepted operation basis.
# Detect the accessor independently of argument shape so debug-only tracing cannot pin the bridge to
# a retired 3-argument signature.
if "requireGameCore().processValidatedCandidate(" in main:
    source = source.replace("gameCore.processValidatedCandidate(", "requireGameCore().processValidatedCandidate(")

# The typed action patches have changed makeGameplayRolls parameters over time. The diagnostic only
# needs the resulting rolls object, so locate that settled declaration without assuming its args.
old_roll_anchor = 'roll_anchor = "          JSONObject rolls = makeGameplayRolls(before, action, meta);\\n"\n'
new_roll_anchor = '''roll_match = re.search(r'(?m)^\\s*JSONObject rolls = makeGameplayRolls\\([^;\\n]+\\);\\n', submit)
if roll_match is None:
    raise RuntimeError("roll trace: settled makeGameplayRolls declaration not found")
roll_anchor = roll_match.group(0)
'''
if old_roll_anchor not in source:
    raise RuntimeError("adaptive roll-template anchor missing")
source = source.replace(old_roll_anchor, new_roll_anchor, 1)

namespace = {"__name__": "__main__", "__file__": str(TEMPLATE), "re": re}
exec(compile(source, str(TEMPLATE), "exec"), namespace, namespace)
print("Adaptive runtime turn trace matched the settled ActionRuntime/Core fast path, roll-aware candidate bridge and typed roll declaration.")

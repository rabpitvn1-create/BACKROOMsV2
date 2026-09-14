from pathlib import Path

MAIN = Path(__file__).resolve().parent / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
text = MAIN.read_text(encoding="utf-8")


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return source.replace(old, new, 1)


def method_bounds(source: str, signature: str) -> tuple[int, int]:
    start = source.find(signature)
    if start < 0: raise RuntimeError("method not found: " + signature)
    open_brace = source.find("{", start)
    depth = 0; state = "code"; escaped = False; i = open_brace
    while i < len(source):
        ch = source[i]; nxt = source[i + 1] if i + 1 < len(source) else ""
        if state == "string":
            if escaped: escaped = False
            elif ch == "\\": escaped = True
            elif ch == '"': state = "code"
        elif state == "char":
            if escaped: escaped = False
            elif ch == "\\": escaped = True
            elif ch == "'": state = "code"
        elif state == "line_comment":
            if ch == "\n": state = "code"
        elif state == "block_comment":
            if ch == "*" and nxt == "/": state = "code"; i += 1
        else:
            if ch == '"': state = "string"
            elif ch == "'": state = "char"
            elif ch == "/" and nxt == "/": state = "line_comment"; i += 1
            elif ch == "/" and nxt == "*": state = "block_comment"; i += 1
            elif ch == "{": depth += 1
            elif ch == "}":
                depth -= 1
                if depth == 0: return start, i + 1
        i += 1
    raise RuntimeError("closing brace missing: " + signature)


helper_anchor = "  private class GameBridge {\n"
if helper_anchor not in text: raise RuntimeError("GameBridge anchor missing")
helpers = r'''  private JSONObject debugJsonDiff(JSONObject before, JSONObject after) {
    JSONObject out = new JSONObject();
    JSONArray changed = new JSONArray();
    java.util.LinkedHashSet<String> keys = new java.util.LinkedHashSet<>();
    if (before != null) before.keys().forEachRemaining(keys::add);
    if (after != null) after.keys().forEachRemaining(keys::add);
    for (String key : keys) {
      Object left = before == null ? null : before.opt(key);
      Object right = after == null ? null : after.opt(key);
      String a = left == null || left == JSONObject.NULL ? "null" : String.valueOf(left);
      String b = right == null || right == JSONObject.NULL ? "null" : String.valueOf(right);
      if (!a.equals(b)) {
        changed.put(key);
        out.put(key, new JSONObject().put("before", left == null ? JSONObject.NULL : left).put("after", right == null ? JSONObject.NULL : right));
      }
    }
    return out.put("changedKeys", changed);
  }

  private JSONArray debugOperationDiagnostics(JSONObject before, JSONArray ops, JSONObject rolls, String action) throws Exception {
    JSONArray result = new JSONArray();
    if (ops == null) return result;
    JSONArray prefix = new JSONArray();
    JSONObject previous = new JSONObject(before.toString());
    int limit = Math.min(24, ops.length());
    for (int i = 0; i < limit; i++) {
      JSONObject op = ops.optJSONObject(i);
      if (op == null) continue;
      prefix.put(new JSONObject(op.toString()));
      JSONObject afterPrefix = applyModelOperations(before, prefix, rolls, action);
      JSONObject delta = debugJsonDiff(previous, afterPrefix);
      boolean accepted = delta.optJSONArray("changedKeys") != null && delta.optJSONArray("changedKeys").length() > 0;
      result.put(new JSONObject()
        .put("index", i).put("type", op.optString("type", "")).put("payload", op)
        .put("accepted", accepted)
        .put("reason", accepted ? "accepted" : "no_state_delta_or_rejected_by_reducer")
        .put("stateDelta", delta));
      previous = afterPrefix;
    }
    return result;
  }

  private JSONObject debugStorySnapshot(JSONObject before, JSONObject candidate) {
    JSONObject beforeFlags = before == null ? null : before.optJSONObject("flags");
    JSONObject candidateFlags = candidate == null ? null : candidate.optJSONObject("flags");
    return new JSONObject()
      .put("storyArcBefore", beforeFlags == null ? JSONObject.NULL : beforeFlags.opt("storyArc"))
      .put("storyArcCandidate", candidateFlags == null ? JSONObject.NULL : candidateFlags.opt("storyArc"))
      .put("luciaEncounterBefore", beforeFlags == null ? JSONObject.NULL : beforeFlags.opt("luciaEncounter"))
      .put("luciaEncounterCandidate", candidateFlags == null ? JSONObject.NULL : candidateFlags.opt("luciaEncounter"));
  }

  private String debugProcessCombat(String stateJson, String actionKind, String action) throws Exception {
    int turn = debugTurnFromJson(stateJson);
    JSONObject before = debugPayload(stateJson);
    debugEvent("combat", "core_request", turn, new JSONObject()
      .put("actionKind", actionKind == null ? "" : actionKind).put("action", action == null ? "" : action)
      .put("stateBefore", before).put("combatBefore", before.opt("combat")).put("partyBefore", before.opt("party")));
    String raw = requireGameCore().processCombat(stateJson, actionKind, action);
    JSONObject result = debugPayload(raw);
    debugEvent("combat", "core_result", turn, new JSONObject().put("result", result).put("stateAfter", result.opt("state")));
    com.rabpit.backroom.core.RuntimeDebugLog.appendTurnStage(turn, "combat", new JSONObject()
      .put("request", new JSONObject().put("actionKind", actionKind).put("action", action).put("state", before)).put("result", result));
    return raw;
  }

'''
text = text.replace(helper_anchor, helpers + helper_anchor, 1)

combat_call = "requireGameCore().processCombat(stateJson, actionKind, action)"
if combat_call in text: text = text.replace(combat_call, "debugProcessCombat(stateJson, actionKind, action)")
wrapper_start, wrapper_end = method_bounds(text, "  private String debugProcessCombat(")
wrapper = text[wrapper_start:wrapper_end].replace("debugProcessCombat(stateJson, actionKind, action)", combat_call, 1)
text = text[:wrapper_start] + wrapper + text[wrapper_end:]

submit_start, submit_end = method_bounds(text, "    private void submitTurnInternal(")
submit = text[submit_start:submit_end]

local_old = '''          JSONObject localResult = new JSONObject(gameCore.processRule(stateJson, action));
          if (localResult.optBoolean("handled", false)) {
            emit("backroomTurn", localResult.getJSONObject("state").toString());
            return;
          }
'''
local_new = '''          JSONObject debugBefore = new JSONObject(stateJson);
          int debugTurn = debugBefore.optInt("turn", 0);
          debugActiveTurn = debugTurn;
          boolean debugMeta = isMetaAction(action);
          com.rabpit.backroom.core.RuntimeDebugLog.beginTurn(debugTurn, action, debugMeta, debugBefore);
          debugEvent("game_core", "rule_request", debugTurn, new JSONObject().put("action", action).put("actionKind", actionKind).put("actionOrigin", actionOrigin).put("stateBefore", debugBefore));
          String localRaw = gameCore.processRule(stateJson, action);
          JSONObject localResult = new JSONObject(localRaw);
          debugEvent("game_core", "rule_result", debugTurn, localResult);
          if (localResult.optBoolean("handled", false)) {
            JSONObject localState = localResult.getJSONObject("state");
            com.rabpit.backroom.core.RuntimeDebugLog.recordTurnStage(debugTurn, "gameCoreRule", localResult);
            com.rabpit.backroom.core.RuntimeDebugLog.recordTurnStage(debugTurn, "stateDiff", debugJsonDiff(debugBefore, localState));
            com.rabpit.backroom.core.RuntimeDebugLog.finishTurn(debugTurn, localState, localResult.optString("reply", ""), null);
            emit("backroomTurn", localState.toString());
            return;
          }
'''
submit = replace_once(submit, local_old, local_new, "GameCore fast path trace")

roll_anchor = "          JSONObject rolls = makeGameplayRolls(before, action, meta);\n"
submit = replace_once(submit, roll_anchor, roll_anchor + "          com.rabpit.backroom.core.RuntimeDebugLog.recordTurnStage(debugTurn, \"rolls\", rolls);\n          com.rabpit.backroom.core.RuntimeDebugLog.recordTurnStage(debugTurn, \"inputClassification\", new JSONObject().put(\"actionKind\", actionKind).put(\"actionOrigin\", actionOrigin).put(\"rollActionKind\", rolls.optString(\"actionKind\", \"\")).put(\"meta\", meta));\n", "roll trace")

writer_old = "          JSONObject generated = parseModelJson(generateText(writerPrompt(before, action, rolls, null, actionOrigin)));\n"
writer_new = '''          String writerPromptText = writerPrompt(before, action, rolls, null, actionOrigin);
          com.rabpit.backroom.core.RuntimeDebugLog.recordTurnStage(debugTurn, "writerPrompt", writerPromptText);
          String writerRaw = generateText(writerPromptText);
          com.rabpit.backroom.core.RuntimeDebugLog.recordTurnStage(debugTurn, "writerRaw", writerRaw);
          JSONObject generated = parseModelJson(writerRaw);
          com.rabpit.backroom.core.RuntimeDebugLog.recordTurnStage(debugTurn, "writerParsed", generated);
'''
submit = replace_once(submit, writer_old, writer_new, "writer trace")

candidate_old = '''          JSONObject candidateState = meta
            ? new JSONObject(before.toString())
            : applyModelOperations(before, generated.optJSONArray("ops"), rolls, action);
'''
submit = replace_once(submit, candidate_old, candidate_old + '''          com.rabpit.backroom.core.RuntimeDebugLog.recordTurnStage(debugTurn, "candidateInitial", candidateState);
          com.rabpit.backroom.core.RuntimeDebugLog.recordTurnStage(debugTurn, "operationsInitial", debugOperationDiagnostics(before, generated.optJSONArray("ops"), rolls, action));
          com.rabpit.backroom.core.RuntimeDebugLog.recordTurnStage(debugTurn, "storyCanonInitial", debugStorySnapshot(before, candidateState));
''', "initial candidate trace")

hard_gate = "          if (hardIssues.length() > 0) {\n"
first = submit.find(hard_gate)
if first < 0: raise RuntimeError("initial hard gate missing")
submit = submit[:first] + '''          com.rabpit.backroom.core.RuntimeDebugLog.recordTurnStage(debugTurn, "auditInitial", new JSONObject()
            .put("risk", risk).put("audits", audits).put("hardIssues", hardIssues).put("repaired", repaired));
''' + submit[first:]

repair_old = "            generated = parseModelJson(generateText(writerPrompt(before, action, rolls, hardIssues, actionOrigin)));\n"
repair_new = '''            String repairPromptText = writerPrompt(before, action, rolls, hardIssues, actionOrigin);
            com.rabpit.backroom.core.RuntimeDebugLog.recordTurnStage(debugTurn, "repairReason", hardIssues);
            com.rabpit.backroom.core.RuntimeDebugLog.recordTurnStage(debugTurn, "repairPrompt", repairPromptText);
            String repairRaw = generateText(repairPromptText);
            com.rabpit.backroom.core.RuntimeDebugLog.recordTurnStage(debugTurn, "repairRaw", repairRaw);
            generated = parseModelJson(repairRaw);
            com.rabpit.backroom.core.RuntimeDebugLog.recordTurnStage(debugTurn, "repairParsed", generated);
'''
submit = replace_once(submit, repair_old, repair_new, "repair trace")

repair_candidate = "            candidateState = applyModelOperations(before, generated.optJSONArray(\"ops\"), rolls, action);\n"
submit = replace_once(submit, repair_candidate, repair_candidate + "            com.rabpit.backroom.core.RuntimeDebugLog.recordTurnStage(debugTurn, \"candidateAfterRepair\", candidateState);\n            com.rabpit.backroom.core.RuntimeDebugLog.recordTurnStage(debugTurn, \"operationsAfterRepair\", debugOperationDiagnostics(before, generated.optJSONArray(\"ops\"), rolls, action));\n            com.rabpit.backroom.core.RuntimeDebugLog.recordTurnStage(debugTurn, \"storyCanonAfterRepair\", debugStorySnapshot(before, candidateState));\n", "repair candidate trace")

positions = []; pos = 0
while True:
    found = submit.find(hard_gate, pos)
    if found < 0: break
    positions.append(found); pos = found + len(hard_gate)
if len(positions) < 2: raise RuntimeError("final hard gate missing")
second = positions[1]
submit = submit[:second] + '''          com.rabpit.backroom.core.RuntimeDebugLog.recordTurnStage(debugTurn, "auditAfterRepair", new JSONObject()
            .put("risk", risk).put("audits", audits).put("hardIssues", hardIssues).put("repaired", repaired));
''' + submit[second:]

fallback_old = '''            boolean safeFallback = com.rabpit.backroom.core.CanonFallbackPolicy.isEligible(
              before, candidateState, generated, rolls, action, meta, repaired);
            if (!safeFallback) {
              throw new Exception("Lượt chơi không vượt qua kiểm tra canon; state không được thay đổi.");
            }
'''
fallback_new = '''            JSONObject fallbackDiagnostics = com.rabpit.backroom.core.CanonFallbackPolicy.diagnostics(
              before, candidateState, generated, rolls, action, meta, repaired);
            boolean safeFallback = fallbackDiagnostics.optBoolean("eligible", false);
            com.rabpit.backroom.core.RuntimeDebugLog.recordTurnStage(debugTurn, "canonFallback", fallbackDiagnostics);
            if (!safeFallback) {
              debugEvent("canon", "fallback_rejected", debugTurn, fallbackDiagnostics);
              throw new Exception("Lượt chơi không vượt qua kiểm tra canon; state không được thay đổi. CANON FALLBACK REJECTED BECAUSE: " + fallbackDiagnostics.optString("reason", "unknown"));
            }
            debugEvent("canon", "fallback_accepted", debugTurn, fallbackDiagnostics);
'''
submit = replace_once(submit, fallback_old, fallback_new, "fallback trace")

core_old = '''          JSONObject coreCommit = new JSONObject(gameCore.processValidatedCandidate(before.toString(), candidateState.toString(), action));
          if (!coreCommit.optBoolean("handled", false)) {
            throw new Exception("Game State Core từ chối Gemini delta: " + coreCommit.optString("error", "invalid_delta"));
          }
          candidateState = coreCommit.getJSONObject("state");

'''
core_new = '''          com.rabpit.backroom.core.RuntimeDebugLog.recordTurnStage(debugTurn, "candidateBeforeCore", candidateState);
          debugEvent("game_core", "validated_candidate_request", debugTurn, new JSONObject().put("before", before).put("candidate", candidateState).put("action", action));
          JSONObject coreCommit = new JSONObject(gameCore.processValidatedCandidate(before.toString(), candidateState.toString(), action));
          debugEvent("game_core", "validated_candidate_result", debugTurn, coreCommit);
          com.rabpit.backroom.core.RuntimeDebugLog.recordTurnStage(debugTurn, "gameCoreValidatedCandidate", coreCommit);
          if (!coreCommit.optBoolean("handled", false)) {
            throw new Exception("Game State Core từ chối Gemini delta: " + coreCommit.optString("error", "invalid_delta"));
          }
          candidateState = coreCommit.getJSONObject("state");
          com.rabpit.backroom.core.RuntimeDebugLog.recordTurnStage(debugTurn, "candidateAfterCore", candidateState);

'''
submit = replace_once(submit, core_old, core_new, "Core boundary trace")

final_emit = '          emit("backroomTurn", state.toString());\n'
submit = replace_once(submit, final_emit, '''          com.rabpit.backroom.core.RuntimeDebugLog.recordTurnStage(debugTurn, "finalCommittedState", state);
          com.rabpit.backroom.core.RuntimeDebugLog.recordTurnStage(debugTurn, "stateDiff", debugJsonDiff(before, state));
          com.rabpit.backroom.core.RuntimeDebugLog.recordTurnStage(debugTurn, "finalReply", reply);
          com.rabpit.backroom.core.RuntimeDebugLog.finishTurn(debugTurn, state, reply, null);
          emit("backroomTurn", state.toString());
''', "final trace")

error_old = '          emit("backroomError", e.getMessage() == null ? "Không thể xử lý lượt." : e.getMessage());\n'
submit = replace_once(submit, error_old, '''          String debugError = e.getMessage() == null ? "Không thể xử lý lượt." : e.getMessage();
          debugEvent("turn", "error", debugTurnFromJson(stateJson), new JSONObject().put("message", debugError).put("errorClass", e.getClass().getName()));
          com.rabpit.backroom.core.RuntimeDebugLog.finishTurn(debugTurnFromJson(stateJson), null, null, debugError);
          emit("backroomError", debugError);
''', "error trace")

text = text[:submit_start] + submit + text[submit_end:]
for marker in ("writerPromptText", "repairPromptText", "operationsInitial", "auditAfterRepair", "CanonFallbackPolicy.diagnostics(", "candidateBeforeCore", "finalCommittedState", "debugProcessCombat"):
    if marker not in text: raise RuntimeError("turn trace marker missing: " + marker)
MAIN.write_text(text, encoding="utf-8")
print("Settled submitTurnInternal debug trace installed: input/rolls/writer/reducer/audit/repair/fallback/Core/final/combat.")

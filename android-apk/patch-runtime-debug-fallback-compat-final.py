from pathlib import Path

MAIN = Path(__file__).resolve().parent / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
text = MAIN.read_text(encoding="utf-8")

operation_baseline_old = "    JSONObject previous = new JSONObject(before.toString());\n"
operation_baseline_new = "    JSONObject previous = applyModelOperations(before, new JSONArray(), rolls, action);\n"
operation_count = text.count(operation_baseline_old)
if operation_count != 1:
    raise RuntimeError(f"operation diagnostic bookkeeping baseline expected once, found {operation_count}")
text = text.replace(operation_baseline_old, operation_baseline_new, 1)

old = r'''            JSONObject fallbackDiagnostics = com.rabpit.backroom.core.CanonFallbackPolicy.diagnostics(
              before, candidateState, generated, rolls, action, meta, repaired);
            boolean safeFallback = fallbackDiagnostics.optBoolean("eligible", false);
            com.rabpit.backroom.core.RuntimeDebugLog.recordTurnStage(debugTurn, "canonFallback", fallbackDiagnostics);
            if (!safeFallback) {
              debugEvent("canon", "fallback_rejected", debugTurn, fallbackDiagnostics);
              throw new Exception("Lượt chơi không vượt qua kiểm tra canon; state không được thay đổi. CANON FALLBACK REJECTED BECAUSE: " + fallbackDiagnostics.optString("reason", "unknown"));
            }
            debugEvent("canon", "fallback_accepted", debugTurn, fallbackDiagnostics);
'''
new = r'''            JSONObject fallbackDiagnostics = com.rabpit.backroom.core.CanonFallbackPolicy.diagnostics(
              before, candidateState, generated, rolls, action, meta, repaired);
            boolean safeFallback = com.rabpit.backroom.core.CanonFallbackPolicy.isEligible(
              before, candidateState, generated, rolls, action, meta, repaired);
            fallbackDiagnostics.put("summary", safeFallback
              ? "CANON FALLBACK ACCEPTED"
              : "CANON FALLBACK REJECTED BECAUSE: " + fallbackDiagnostics.optString("reason", "unknown"));
            com.rabpit.backroom.core.RuntimeDebugLog.recordTurnStage(debugTurn, "canonFallback", fallbackDiagnostics);
            if (!safeFallback) {
              debugEvent("canon", "fallback_rejected", debugTurn, fallbackDiagnostics);
              throw new Exception("Lượt chơi không vượt qua kiểm tra canon; state không được thay đổi.");
            }
            debugEvent("canon", "fallback_accepted", debugTurn, fallbackDiagnostics);
'''

count = text.count(old)
if count != 1:
    raise RuntimeError(f"canon fallback debug compatibility expected one block, found {count}")
text = text.replace(old, new, 1)

for marker in (
    "JSONObject previous = applyModelOperations(before, new JSONArray(), rolls, action);",
    "CanonFallbackPolicy.diagnostics(",
    "CanonFallbackPolicy.isEligible(",
    "CANON FALLBACK REJECTED BECAUSE:",
    'throw new Exception("Lượt chơi không vượt qua kiểm tra canon; state không được thay đổi.");',
):
    if marker not in text:
        raise RuntimeError("canon fallback debug compatibility marker missing: " + marker)

MAIN.write_text(text, encoding="utf-8")
print("Operation diagnostics ignore reducer bookkeeping; canon fallback diagnostics preserve the original eligibility decision and player-facing error.")

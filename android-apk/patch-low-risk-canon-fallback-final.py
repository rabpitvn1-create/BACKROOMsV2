from pathlib import Path
import runpy

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
text = MAIN.read_text(encoding="utf-8")

submit_signature = "    private void submitTurnInternal("
submit_start = text.find(submit_signature)
if submit_start < 0:
    raise RuntimeError("Low-risk canon fallback: submitTurnInternal anchor missing")
submit_end = text.find("    @JavascriptInterface public void requestSnapshot", submit_start)
if submit_end < 0:
    raise RuntimeError("Low-risk canon fallback: submitTurnInternal end anchor missing")

submit = text[submit_start:submit_end]
failure = '            throw new Exception("Lượt chơi không vượt qua kiểm tra canon; state không được thay đổi.");\n'
if submit.count(failure) != 1:
    raise RuntimeError(
        "Low-risk canon fallback: expected final fail-closed throw once inside "
        f"submitTurnInternal, found {submit.count(failure)}"
    )

replacement = r'''            boolean safeFallback = com.rabpit.backroom.core.CanonFallbackPolicy.isEligible(
              before, candidateState, generated, rolls, action, meta, repaired);
            if (!safeFallback) {
              throw new Exception("Lượt chơi không vượt qua kiểm tra canon; state không được thay đổi.");
            }

            String storyDirective = com.rabpit.backroom.core.StoryProgressionPolicy.directive(before, action);
            String fallbackReply;
            if (storyDirective.startsWith("LUCIA_FIRST_CONTACT")) {
              fallbackReply = "Trong lúc tiếp tục khám phá Level 0, Kai xác nhận một người sống sót có vũ trang ở tuyến phía trước. Sau một cuộc tiếp cận thận trọng, hai bên trao đổi tên: cô gái là Lucia. Cả hai mới chỉ thiết lập mức tin cậy chiến thuật ban đầu; Lucia chưa gia nhập Party.";
            } else if (storyDirective.startsWith("LUCIA_JOIN_DECISION")) {
              fallbackReply = "Sau khi đã có thời gian đánh giá lẫn nhau, Kai và Lucia thống nhất tiếp tục di chuyển cùng nhau với ranh giới chiến thuật rõ ràng. Quyết định này xác nhận Lucia gia nhập Party; không có thay đổi quan hệ nào khác được suy diễn.";
            } else if (storyDirective.startsWith("ARRIVAL_ONLY")) {
              fallbackReply = "Kai tiếp tục khảo sát Level 0 một cách có hệ thống. Những sai lệch lặp lại của không gian đủ để xác nhận rằng cách định hướng tuyến tính không đáng tin, nhưng lượt này chưa xuất hiện người sống sót hay liên lạc mới.";
            } else if (storyDirective.startsWith("HOLD_FIRST_CONTACT")) {
              fallbackReply = "Kai và Lucia duy trì khoảng cách cùng mức hợp tác chiến thuật đã xác nhận, chưa có quyết định mới về việc lập Party. Các dữ kiện chưa chắc chắn vẫn được giữ nguyên.";
            } else {
              fallbackReply = "Kai tiếp tục hành động theo hướng đã chọn một cách thận trọng. Lượt này không ghi nhận thay đổi trạng thái mới ngoài những gì engine đã xác nhận; các dữ kiện chưa chắc chắn vẫn được giữ nguyên.";
            }

            generated = new JSONObject()
              .put("reply", fallbackReply)
              .put("ops", new JSONArray())
              .put("choices", new JSONArray())
              .put("snapshotEvent", new JSONObject()
                .put("shouldGenerate", false)
                .put("kind", "")
                .put("reason", "canon_safe_fallback"));
            reply = generated.optString("reply", "");
            candidateState = com.rabpit.backroom.core.StoryProgressionPolicy.normalizeCandidate(
              before, new JSONObject(before.toString()), action);
            risk = 0;
            audits = new JSONArray();
            hardIssues = new JSONArray();
            if (BuildConfig.DEBUG) {
              android.util.Log.w("BackroomCanonFallback", "Recovered harmless repaired turn; deterministic story state preserved.");
            }
'''

submit = submit.replace(failure, replacement, 1)
text = text[:submit_start] + submit + text[submit_end:]

# Later authority patches can reconstruct the audit helper from the pre-Haiku form. The final
# runtime must still route audits through auditText(), because that method owns Gemini-first and
# Haiku-fallback behavior. Restore the settled provider call here, after those patches have run.
direct_audit = "parseModelJson(geminiAuditText(prompt, excludedWorker))"
fallback_audit = "parseModelJson(auditText(prompt, excludedWorker))"
if fallback_audit not in text:
    if text.count(direct_audit) != 1:
        raise RuntimeError(
            "Low-risk canon fallback: expected one direct Gemini audit call before final provider restore, "
            f"found {text.count(direct_audit)}"
        )
    text = text.replace(direct_audit, fallback_audit, 1)
if fallback_audit not in text:
    raise RuntimeError("Low-risk canon fallback: Haiku-capable audit route missing after finalization")

# lucia_story transport is retired. StoryProgressionPolicy owns story state and the final runtime
# must not require or resurrect the old model operation tokens merely to satisfy a patch marker.
for retired in (
    'type.equals("lucia_story")',
    "applyLuciaStoryOperationAndroid",
    "LUCIA STATE TRANSPORT:",
):
    if retired in text:
        raise RuntimeError("Retired Lucia story transport survived terminal canon finalization: " + retired)

for marker in (
    "CanonFallbackPolicy.isEligible(",
    "StoryProgressionPolicy.directive(before, action)",
    "StoryProgressionPolicy.normalizeCandidate(",
    'android.util.Log.w("BackroomCanonFallback"',
    'put("reason", "canon_safe_fallback")',
    'put("choices", new JSONArray())',
):
    if marker not in submit:
        raise RuntimeError("Low-risk canon fallback marker missing: " + marker)

MAIN.write_text(text, encoding="utf-8")
print(
    "Low-risk canon fallback applied after the settled runtime: harmless repaired turns may recover "
    "without model ops while deterministic StoryProgressionPolicy state is preserved; combat, encounter, "
    "transition, consequential rolls and non-story authoritative state changes remain fail-closed."
)

# The workflow already executes this file as its terminal runtime authority. Delegate the debug
# finalizers here so they always run after canon/story/combat/UI have settled and cannot be
# overwritten by a later legacy patch.
for finalizer in (
    "patch-runtime-debug-export-final.py",
    "patch-runtime-debug-session-init-final.py",
    "patch-runtime-debug-provider-trace-final.py",
    "patch-runtime-debug-turn-trace-adaptive-final.py",
    "patch-runtime-debug-fallback-compat-final.py",
    "patch-runtime-debug-core-trace-final.py",
    "patch-runtime-debug-ui-events-final.py",
):
    runpy.run_path(str(ROOT / finalizer), run_name="__main__")

# Runtime debug logging is strictly best-effort. Android's org.json.JSONObject.put throws a
# checked JSONException, so debug-only helpers must never leak that exception into otherwise
# non-throwing Activity methods. Normalize the settled generated Java after every debug finalizer
# has run, keeping diagnostics useful without making the APK uncompilable.
final_text = MAIN.read_text(encoding="utf-8")


def replace_final_once(old: str, new: str, label: str) -> None:
    global final_text
    count = final_text.count(old)
    if count != 1:
        raise RuntimeError(f"runtime debug Java compatibility {label}: expected 1 match, found {count}")
    final_text = final_text.replace(old, new, 1)


debug_event_anchor = '''  private void debugEvent(String category, String event, int turn, Object payload) {
'''
if final_text.count(debug_event_anchor) != 1:
    raise RuntimeError("runtime debug Java compatibility: debugEvent anchor missing or duplicated")
safe_json_helpers = r'''  private JSONObject debugObject(Object... pairs) {
    JSONObject out = new JSONObject();
    try {
      for (int i = 0; i + 1 < pairs.length; i += 2) {
        out.put(String.valueOf(pairs[i]), pairs[i + 1]);
      }
    } catch (Exception ignored) {}
    return out;
  }

  private JSONObject debugPut(JSONObject out, String key, Object value) {
    JSONObject target = out == null ? new JSONObject() : out;
    try { target.put(key, value); } catch (Exception ignored) {}
    return target;
  }

'''
final_text = final_text.replace(debug_event_anchor, safe_json_helpers + debug_event_anchor, 1)

replace_final_once(
    '    catch (Exception ignored) { return new JSONObject().put("raw", raw == null ? "" : raw); }',
    '    catch (Exception ignored) { return debugObject("raw", raw == null ? "" : raw); }',
    "debugPayload fallback",
)
replace_final_once(
    '''    JSONObject payload = new JSONObject()
      .put("provider", provider == null ? "" : provider)
      .put("phase", phase == null ? "" : phase)
      .put("role", role == null ? "" : role)
      .put("model", model == null ? "" : model)
      .put("slot", slot == null ? "" : slot)
      .put("latencyMs", latencyMs)
      .put("failureClass", failureClass == null ? "" : failureClass);
    if (error != null) {
      payload.put("errorClass", error.getClass().getName());
      payload.put("message", error.getMessage() == null ? "" : error.getMessage());
      payload.put("httpStatus", error instanceof HttpError ? ((HttpError)error).status : 0);
    }
''',
    '''    JSONObject payload = debugObject(
      "provider", provider == null ? "" : provider,
      "phase", phase == null ? "" : phase,
      "role", role == null ? "" : role,
      "model", model == null ? "" : model,
      "slot", slot == null ? "" : slot,
      "latencyMs", latencyMs,
      "failureClass", failureClass == null ? "" : failureClass);
    if (error != null) {
      debugPut(payload, "errorClass", error.getClass().getName());
      debugPut(payload, "message", error.getMessage() == null ? "" : error.getMessage());
      debugPut(payload, "httpStatus", error instanceof HttpError ? ((HttpError)error).status : 0);
    }
''',
    "provider payload",
)
replace_final_once(
    '      debugEvent("ui_bridge", "export_log_failed", 0, new JSONObject().put("message", error.getMessage() == null ? "" : error.getMessage()));',
    '      debugEvent("ui_bridge", "export_log_failed", 0, debugObject("message", error.getMessage() == null ? "" : error.getMessage()));',
    "export failure payload",
)
replace_final_once(
    '        out.put(key, new JSONObject().put("before", left == null ? JSONObject.NULL : left).put("after", right == null ? JSONObject.NULL : right));',
    '        debugPut(out, key, debugObject("before", left == null ? JSONObject.NULL : left, "after", right == null ? JSONObject.NULL : right));',
    "state diff entry",
)
replace_final_once(
    '    return out.put("changedKeys", changed);',
    '    return debugPut(out, "changedKeys", changed);',
    "state diff changed keys",
)
replace_final_once(
    '''    return new JSONObject()
      .put("storyArcBefore", beforeFlags == null ? JSONObject.NULL : beforeFlags.opt("storyArc"))
      .put("storyArcCandidate", candidateFlags == null ? JSONObject.NULL : candidateFlags.opt("storyArc"))
      .put("luciaEncounterBefore", beforeFlags == null ? JSONObject.NULL : beforeFlags.opt("luciaEncounter"))
      .put("luciaEncounterCandidate", candidateFlags == null ? JSONObject.NULL : candidateFlags.opt("luciaEncounter"));
''',
    '''    return debugObject(
      "storyArcBefore", beforeFlags == null ? JSONObject.NULL : beforeFlags.opt("storyArc"),
      "storyArcCandidate", candidateFlags == null ? JSONObject.NULL : candidateFlags.opt("storyArc"),
      "luciaEncounterBefore", beforeFlags == null ? JSONObject.NULL : beforeFlags.opt("luciaEncounter"),
      "luciaEncounterCandidate", candidateFlags == null ? JSONObject.NULL : candidateFlags.opt("luciaEncounter"));
''',
    "story snapshot",
)
replace_final_once(
    '          debugEvent("turn", "error", debugTurnFromJson(stateJson), new JSONObject().put("message", debugError).put("errorClass", e.getClass().getName()));',
    '          debugEvent("turn", "error", debugTurnFromJson(stateJson), debugObject("message", debugError, "errorClass", e.getClass().getName()));',
    "turn error payload",
)

for unsafe in (
    'return new JSONObject().put("raw",',
    'new JSONObject().put("message", debugError).put("errorClass"',
    'out.put(key, new JSONObject().put("before"',
    'return out.put("changedKeys"',
):
    if unsafe in final_text:
        raise RuntimeError("runtime debug Java compatibility unsafe checked JSONException pattern survived: " + unsafe)

MAIN.write_text(final_text, encoding="utf-8")
print("Runtime debug Java compatibility applied: debug JSON construction is checked-exception safe.")

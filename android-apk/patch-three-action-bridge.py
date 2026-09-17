from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
main = MAIN.read_text(encoding="utf-8")


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return source.replace(old, new, 1)


old_signature = '''    @JavascriptInterface public void submitTurn(String stateJson, String action) {
      io.execute(() -> {
'''
new_signature = '''    @JavascriptInterface public void submitTurn(String stateJson, String action) {
      submitAction(stateJson, "EXECUTE", action);
    }

    @JavascriptInterface public void submitAction(String stateJson, String actionKind, String action) {
      submitTurnInternal(stateJson, actionKind, action);
    }

    private void submitTurnInternal(String stateJson, String actionKind, String action) {
      io.execute(() -> {
'''
if "@JavascriptInterface public void submitAction(String stateJson, String actionKind, String action)" not in main:
    if old_signature not in main:
        raise RuntimeError("MainActivity submitTurn anchor missing")
    main = main.replace(old_signature, new_signature, 1)

# patch-game-state-core-bridge may have already materialized the Core fast path as
# String coreRaw + JSONObject coreResult. Older snapshots used a one-line localResult.
# Accept either settled shape and insert beginAction exactly once before processRule.
core_call = "requireGameCore()" if "requireGameCore()" in main else "gameCore"
if "beginAction(stateJson, actionKind, action)" not in main:
    rule_shapes = (
        (
            f'''          String coreRaw = {core_call}.processRule(stateJson, action);\n''',
            f'''          JSONObject actionStart = new JSONObject({core_call}.beginAction(stateJson, actionKind, action));\n          if (!actionStart.optBoolean("handled", false)) {{\n            throw new Exception("Action Runtime từ chối hành động: " + actionStart.optString("error", "action_start_failed"));\n          }}\n          String coreRaw = {core_call}.processRule(stateJson, action);\n''',
        ),
        (
            f'''          JSONObject localResult = new JSONObject({core_call}.processRule(stateJson, action));\n''',
            f'''          JSONObject actionStart = new JSONObject({core_call}.beginAction(stateJson, actionKind, action));\n          if (!actionStart.optBoolean("handled", false)) {{\n            throw new Exception("Action Runtime từ chối hành động: " + actionStart.optString("error", "action_start_failed"));\n          }}\n          JSONObject localResult = new JSONObject({core_call}.processRule(stateJson, action));\n''',
        ),
        (
            '''          String coreRaw = requireGameCore().processRule(stateJson, action);\n''',
            '''          JSONObject actionStart = new JSONObject(requireGameCore().beginAction(stateJson, actionKind, action));\n          if (!actionStart.optBoolean("handled", false)) {\n            throw new Exception("Action Runtime từ chối hành động: " + actionStart.optString("error", "action_start_failed"));\n          }\n          String coreRaw = requireGameCore().processRule(stateJson, action);\n''',
        ),
    )
    matched = False
    for old, new in rule_shapes:
        if old in main:
            main = main.replace(old, new, 1)
            matched = True
            break
    if not matched:
        raise RuntimeError("MainActivity processRule anchor missing")

# Entity encounter generation is authoritative to the typed ActionRuntime kind, not to words found
# in the Vietnamese/freeform action label. EXPLORE is the only action that may start a NEW Entity
# encounter. SEARCH/EXECUTE can still resolve an Entity already present in state, but never roll a
# new encounter. Jeff/Jane are ordinary entries in the shared roamingEntityKey pool and therefore
# must not have private encounter channels here.
old_roll_signature = "  private JSONObject makeGameplayRolls(JSONObject state, String action, boolean meta) throws Exception {\n"
new_roll_signature = "  private JSONObject makeGameplayRolls(JSONObject state, String actionKind, String action, boolean meta) throws Exception {\n"
if new_roll_signature not in main:
    main = replace_once(main, old_roll_signature, new_roll_signature, "typed gameplay roll signature")

kind_anchor = "    if (meta) return rolls;\n\n    int level = Math.max(0, Math.min(6, currentLevel(state)));\n"
kind_block = "    if (meta) return rolls;\n\n    String actionKindNormalized = actionKind == null ? \"\" : actionKind.trim().toUpperCase(java.util.Locale.ROOT);\n    boolean exploreAction = \"EXPLORE\".equals(actionKindNormalized);\n\n    int level = Math.max(0, Math.min(6, currentLevel(state)));\n"
if "boolean exploreAction = \"EXPLORE\".equals(actionKindNormalized);" not in main:
    main = replace_once(main, kind_anchor, kind_block, "EXPLORE-only encounter gate")

normal_old = '    JSONObject normalEntityRoll = thresholdRoll("entityEncounter", 10000, entityThresholds[level], physical && entityAllowed, entitySuffix);\n'
normal_new = '    JSONObject normalEntityRoll = thresholdRoll("entityEncounter", 10000, entityThresholds[level], exploreAction && entityAllowed, entitySuffix);\n'
if normal_new not in main:
    main = replace_once(main, normal_old, normal_new, "normal Entity EXPLORE gate")

for legacy_marker in (
    'rolls.put("jeffEncounter"',
    'rolls.put("janeEncounter"',
    'thresholdRoll("jeffEncounter"',
    'thresholdRoll("janeEncounter"',
):
    if legacy_marker in main:
        raise RuntimeError("Retired independent killer encounter channel reintroduced before typed action bridge: " + legacy_marker)

roll_call_old = "          JSONObject rolls = makeGameplayRolls(before, action, meta);\n"
roll_call_new = "          JSONObject rolls = makeGameplayRolls(before, actionKind, action, meta);\n"
if roll_call_new not in main:
    main = replace_once(main, roll_call_old, roll_call_new, "typed gameplay roll call")

writer_sig = "  private String writerPrompt(JSONObject before, String action, JSONObject rolls, JSONArray auditFeedback) throws Exception {\n"
if "String actionRuntimeContext =" not in main:
    writer_pos = main.find(writer_sig)
    if writer_pos < 0:
        raise RuntimeError("MainActivity final writerPrompt anchor missing")
    writer_body = writer_pos + len(writer_sig)
    directive = f'''    String actionRuntimeContext = {core_call}.currentActionContext();
    String actionKindForPrompt = new JSONObject(actionRuntimeContext).optString("kind", "EXECUTE");
    String actionDirective = "ACTION TYPE = " + actionKindForPrompt + ". " +
      ("SEARCH".equals(actionKindForPrompt) ? "SEARCH HARD LOCK: khảo sát có hệ thống location hiện tại, không tự chuyển sang location mới; SEARCH không được khởi tạo encounter Entity mới và entityEncounter phải ineligible; vẫn có thể gặp Survivor, tìm resource/clue/hazard/exit evidence nhưng không đảm bảo có kết quả hay loot. " :
       "EXPLORE".equals(actionKindForPrompt) ? "EXPLORE HARD LOCK: chủ động mở rộng known space và có thể đổi location; đây là action duy nhất được phép kích hoạt roll encounter Entity mới; có thể gặp Entity hoặc Survivor, resource/hazard/exit opportunity nhưng không đảm bảo Exit; nếu có lựa chọn định hướng quan trọng thì trả quyền quyết định cho người chơi. " :
       "EXECUTE HARD LOCK: đây là freeform intent của người chơi; phân giải đúng hành động đã nhập, không tự đổi mục tiêu và không khởi tạo encounter Entity mới. ");
'''
    main = main[:writer_body] + directive + main[writer_body:]

    original_return = '    return "Bạn là Game Master của text game Backrooms. Trả DUY NHẤT JSON hợp lệ, không markdown. " +\n'
    return_pos = main.find(original_return, writer_body)
    if return_pos < 0:
        raise RuntimeError("MainActivity writerPrompt return anchor missing")
    replacement_return = (
        '    return actionDirective + "\\nACTION_RUNTIME: " + actionRuntimeContext + "\\n" +\n'
        '      "Bạn là Game Master của text game Backrooms. Trả DUY NHẤT JSON hợp lệ, không markdown. " +\n'
    )
    main = main[:return_pos] + replacement_return + main[return_pos + len(original_return):]

if "abortAction(\"pipeline_error\")" not in main:
    submit_pos = main.find("    private void submitTurnInternal(String stateJson, String actionKind, String action) {")
    if submit_pos < 0:
        raise RuntimeError("typed submitTurnInternal missing before catch patch")
    catch_anchor = '''        } catch (Exception e) {
          emit("backroomError", e.getMessage() == null ? "Không thể xử lý lượt." : e.getMessage());
'''
    catch_pos = main.find(catch_anchor, submit_pos)
    if catch_pos < 0:
        raise RuntimeError("MainActivity gameplay catch anchor missing")
    replacement = f'''        }} catch (Exception e) {{
          try {{ {core_call}.abortAction("pipeline_error"); }} catch (Exception ignored) {{}}
          emit("backroomError", e.getMessage() == null ? "Không thể xử lý lượt." : e.getMessage());
'''
    main = main[:catch_pos] + replacement + main[catch_pos + len(catch_anchor):]

for marker in (
    '@JavascriptInterface public void submitAction(String stateJson, String actionKind, String action)',
    'submitAction(stateJson, "EXECUTE", action);',
    '.beginAction(stateJson, actionKind, action)',
    'private JSONObject makeGameplayRolls(JSONObject state, String actionKind, String action, boolean meta)',
    'boolean exploreAction = "EXPLORE".equals(actionKindNormalized);',
    'makeGameplayRolls(before, actionKind, action, meta)',
    'thresholdRoll("entityEncounter", 10000, entityThresholds[level], exploreAction && entityAllowed',
    'SEARCH không được khởi tạo encounter Entity mới',
    'đây là action duy nhất được phép kích hoạt roll encounter Entity mới',
    'String actionRuntimeContext =',
    '.currentActionContext()',
    'SEARCH HARD LOCK:',
    'EXPLORE HARD LOCK:',
    '.abortAction("pipeline_error")',
):
    if marker not in main:
        raise RuntimeError(f"typed action bridge missing: {marker}")

for forbidden in (
    'makeGameplayRolls(before, action, meta)',
    'thresholdRoll("entityEncounter", 10000, entityThresholds[level], physical && entityAllowed',
    'jeffEncounter',
    'janeEncounter',
):
    if forbidden in main:
        raise RuntimeError("EXPLORE-only shared Entity trigger contract violated: " + forbidden)

MAIN.write_text(main, encoding="utf-8")
print("Step 2 typed Android action bridge applied: new shared Entity encounters are EXPLORE-only; no private Jeff/Jane rolls remain.")

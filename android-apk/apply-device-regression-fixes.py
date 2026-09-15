from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 match, found {count}")
    return text.replace(old, new, 1)


root = Path(__file__).resolve().parent

# 1) Android WebView swipe: on real touch hardware prefer Touch Events.
# Pointer Events exist in Android WebView but can be cancelled by native pan arbitration
# before our horizontal threshold is reached, which made the pointer-first gate inert.
ui = root / "apply-android-ui.py"
text = ui.read_text(encoding="utf-8")
text = replace_once(
    text,
    "/* ANDROID_SWIPE_GESTURE_V2: pointer-first horizontal gesture with touch fallback. */",
    "/* ANDROID_SWIPE_GESTURE_V3: touch hardware uses Touch Events; Pointer Events remain the non-touch fallback. */",
    "swipe version comment",
)
text = replace_once(
    text,
    "  if('PointerEvent' in window)installPointerSwipe();else installTouchSwipe();",
    "  const touchCapable=('ontouchstart' in window)||Number(navigator.maxTouchPoints||0)>0;\n  if(touchCapable)installTouchSwipe();else if('PointerEvent' in window)installPointerSwipe();else installTouchSwipe();",
    "touch-first Android gesture selection",
)
ui.write_text(text, encoding="utf-8")

ui_test = root / "test-android-ui-regressions.mjs"
text = ui_test.read_text(encoding="utf-8")
anchor = "requireText(\"shell.addEventListener('touchstart'\", \"touch fallback uses the stationary shell\");\n"
addition = anchor + (
    "requireText(\"const touchCapable=('ontouchstart' in window)||Number(navigator.maxTouchPoints||0)>0\", \"Android touch hardware must not be routed through the cancellable PointerEvent path\");\n"
    "requireText(\"if(touchCapable)installTouchSwipe();else if('PointerEvent' in window)installPointerSwipe();else installTouchSwipe()\", \"touch-first gesture selection\");\n"
    "assert.ok(!html.includes(\"if('PointerEvent' in window)installPointerSwipe();else installTouchSwipe()\"), \"pointer-first Android gesture gate must not return\");\n"
)
text = replace_once(text, anchor, addition, "swipe regression assertions")
ui_test.write_text(text, encoding="utf-8")

# 2) Debug export: persist prepared JSON before opening the document picker. The
# Activity can be recreated while that picker is open; the volatile instance field then
# becomes null even though the provider has already created a zero-byte destination.
export_patch = root / "patch-runtime-debug-export-final.py"
text = export_patch.read_text(encoding="utf-8")
start_anchor = "  private void startDebugLogExport(String stateJson) {\n"
cache_helpers = r'''  private java.io.File pendingDebugLogFile() {
    return new java.io.File(getCacheDir(), "runtime-debug-pending.json");
  }

  private void persistPendingDebugLog(String json) throws Exception {
    java.io.File file = pendingDebugLogFile();
    try (java.io.FileOutputStream output = new java.io.FileOutputStream(file, false)) {
      output.write((json == null ? "" : json).getBytes(java.nio.charset.StandardCharsets.UTF_8));
      output.flush();
      output.getFD().sync();
    }
  }

  private String loadPendingDebugLog() throws Exception {
    if (pendingDebugLogJson != null && !pendingDebugLogJson.isEmpty()) return pendingDebugLogJson;
    java.io.File file = pendingDebugLogFile();
    if (!file.isFile() || file.length() <= 0) throw new Exception("Không tìm thấy payload log debug đang chờ.");
    try (java.io.FileInputStream input = new java.io.FileInputStream(file);
         java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream()) {
      byte[] chunk = new byte[8192];
      int read;
      while ((read = input.read(chunk)) >= 0) { if (read > 0) buffer.write(chunk, 0, read); }
      String json = new String(buffer.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
      if (json.trim().isEmpty()) throw new Exception("Payload log debug đang chờ bị rỗng.");
      return json;
    }
  }

  private void clearPendingDebugLog() {
    pendingDebugLogJson = null;
    try {
      java.io.File file = pendingDebugLogFile();
      if (file.isFile() && !file.delete()) file.deleteOnExit();
    } catch (Exception ignored) {}
  }

  private void startDebugLogExport(String stateJson) {
'''
text = replace_once(text, start_anchor, cache_helpers, "file-backed debug export helpers")
text = replace_once(
    text,
    "      pendingDebugLogJson = com.rabpit.backroom.core.RuntimeDebugLog.exportJson(current);\n",
    "      pendingDebugLogJson = com.rabpit.backroom.core.RuntimeDebugLog.exportJson(current);\n      persistPendingDebugLog(pendingDebugLogJson);\n",
    "persist prepared debug JSON",
)
text = replace_once(
    text,
    "    } catch (Exception error) {\n      pendingDebugLogJson = null;\n      emit(\"backroomError\", \"Không thể chuẩn bị log debug: \" + (error.getMessage() == null ? \"unknown\" : error.getMessage()));\n    }\n",
    "    } catch (Exception error) {\n      clearPendingDebugLog();\n      emit(\"backroomError\", \"Không thể chuẩn bị log debug: \" + (error.getMessage() == null ? \"unknown\" : error.getMessage()));\n    }\n",
    "debug export prepare cleanup",
)
method_start = text.index("  @Override protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {")
method_end = text.index("\n\n'''\nemit_anchor", method_start)
replacement = r'''  @Override protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode != DEBUG_LOG_EXPORT_REQUEST) return;
    if (resultCode != RESULT_OK || data == null || data.getData() == null) {
      debugEvent("ui_bridge", "export_log_cancelled", 0, new JSONObject());
      clearPendingDebugLog();
      return;
    }
    try {
      String exportJson = loadPendingDebugLog();
      try (OutputStream output = getContentResolver().openOutputStream(data.getData(), "wt")) {
        if (output == null) throw new Exception("Không mở được file đích.");
        output.write(exportJson.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        output.flush();
      }
      debugEvent("ui_bridge", "export_log_completed", 0, new JSONObject().put("bytes", exportJson.getBytes(java.nio.charset.StandardCharsets.UTF_8).length));
      emit("backroomDebugExport", "Log debug đã được lưu.");
    } catch (Exception error) {
      debugEvent("ui_bridge", "export_log_failed", 0, new JSONObject().put("message", error.getMessage() == null ? "" : error.getMessage()));
      emit("backroomError", "Xuất log thất bại: " + (error.getMessage() == null ? "unknown" : error.getMessage()));
    } finally {
      clearPendingDebugLog();
    }
  }'''
text = text[:method_start] + replacement + text[method_end:]
export_patch.write_text(text, encoding="utf-8")

debug_test = root / "test-runtime-debug-export.mjs"
text = debug_test.read_text(encoding="utf-8")
anchor = "requireContract(main.includes('RuntimeDebugLog.exportJson(current)'), 'native bridge does not export runtime buffer');\n"
addition = anchor + (
    "requireContract(main.includes('runtime-debug-pending.json'), 'debug export must persist payload across document-picker Activity recreation');\n"
    "requireContract(main.includes('persistPendingDebugLog(pendingDebugLogJson)'), 'prepared debug payload is not persisted before launching the picker');\n"
    "requireContract(main.includes('String exportJson = loadPendingDebugLog();'), 'document result must recover the persisted payload when the Activity instance was recreated');\n"
    "requireContract(main.includes('openOutputStream(data.getData(), \\\"wt\\\")'), 'debug export must explicitly truncate/write the destination document');\n"
    "requireContract(!main.includes('data.getData() == null || pendingDebugLogJson == null'), 'document result must not depend only on the volatile Activity field');\n"
)
text = replace_once(text, anchor, addition, "debug export lifecycle regression assertions")
debug_test.write_text(text, encoding="utf-8")

# 3) Canon fallback: pre-existing encounter/transition context is not itself a mutation.
# For low-risk EXPLORE turns with no consequential roll, the fallback discards all model
# state changes, so a hallucinated candidate delta should not force a visible canon error.
canon = root / "app/src/main/java/com/rabpit/backroom/core/CanonFallbackPolicy.kt"
text = canon.read_text(encoding="utf-8")
text = replace_once(
    text,
    "    val dangerousState = dangerousChangedTopLevel.isNotEmpty() || dangerousChangedFlags.isNotEmpty()\n\n    val reason = when {\n",
    "    val dangerousState = dangerousChangedTopLevel.isNotEmpty() || dangerousChangedFlags.isNotEmpty()\n    val rollActionKind = rolls.optString(\"actionKind\", \"\").trim().uppercase(Locale.ROOT)\n    val lowRiskExplorationRecovery = (explorationAction || rollActionKind == \"EXPLORE\") && !dangerousRoll\n\n    val reason = when {\n",
    "low-risk exploration recovery classification",
)
text = replace_once(
    text,
    "      combatCandidate -> \"combat_active_candidate\"\n      entityBefore -> \"entity_present_before\"\n      transitionBefore -> \"transition_ready_before\"\n      transitionCandidate -> \"transition_ready_candidate\"\n      dangerousRoll -> \"consequential_roll\"\n      dangerousState -> \"accepted_authoritative_state_change\"\n",
    "      combatCandidate -> \"combat_active_candidate\"\n      dangerousRoll -> \"consequential_roll\"\n      dangerousState && !lowRiskExplorationRecovery -> \"accepted_authoritative_state_change\"\n",
    "remove stale-context canon blockers",
)
text = replace_once(
    text,
    "      .put(\"dangerousRollConsequence\", dangerousRoll)\n      .put(\"dangerousStateChanged\", dangerousState)\n",
    "      .put(\"dangerousRollConsequence\", dangerousRoll)\n      .put(\"dangerousStateChanged\", dangerousState)\n      .put(\"lowRiskExplorationRecovery\", lowRiskExplorationRecovery)\n",
    "canon recovery diagnostics",
)
canon.write_text(text, encoding="utf-8")

fallback_patch = root / "patch-low-risk-canon-fallback-final.py"
text = fallback_patch.read_text(encoding="utf-8")
old_reply = '.put("reply", "Kai tiếp tục theo hướng đã chọn với nhịp di chuyển thận trọng, kiểm tra các góc và khoảng trống trước khi đi qua. Quãng hành động này không cho thấy dữ kiện mới đủ chắc để kết luận thêm; môi trường trước mắt vẫn chưa xác nhận thêm người, vật thể hay lối chuyển tầng.")'
new_reply = '.put("reply", "Kai tiếp tục hành động theo hướng đã chọn một cách thận trọng. Lượt này không ghi nhận thay đổi trạng thái mới ngoài những gì đã được xác nhận trước đó; các dữ kiện chưa chắc chắn vẫn được giữ nguyên.")'
text = replace_once(text, old_reply, new_reply, "context-neutral canon fallback prose")
fallback_patch.write_text(text, encoding="utf-8")

kotlin_test = root / "app/src/test/java/com/rabpit/backroom/core/RuntimeDebugLogTest.kt"
text = kotlin_test.read_text(encoding="utf-8")
class_end = text.rfind("\n}")
if class_end < 0:
    raise SystemExit("RuntimeDebugLogTest class end not found")
new_tests = r'''

  @Test fun staleEncounterAndTransitionContextDoNotBlockSafeExploreFallback() {
    val before = JSONObject("""{
      "turn":7,
      "level":{"number":0,"name":"The Lobby"},
      "player":{"name":"Kai Akechi","hp":100},
      "party":[],
      "inventory":[],
      "flags":{"entityEncounterKey":"hound","exploration":{"transitionReady":true}}
    }""")
    val candidate = JSONObject(before.toString())
    candidate.getJSONObject("player").put("hp", 1)
    val generated = JSONObject().put("reply", "x").put("ops", org.json.JSONArray())
    val rolls = JSONObject().put("actionKind", "EXPLORE")

    val diagnostics = CanonFallbackPolicy.diagnostics(
      before, candidate, generated, rolls,
      "Quan sát hành lang và tiếp tục khám phá", meta = false, repaired = true,
    )
    assertTrue(diagnostics.getBoolean("eligible"))
    assertTrue(diagnostics.getBoolean("dangerousStateChanged"))
    assertTrue(diagnostics.getBoolean("lowRiskExplorationRecovery"))
    assertTrue(diagnostics.getBoolean("entityPresentBefore"))
    assertTrue(diagnostics.getBoolean("transitionReadyBefore"))
  }

  @Test fun consequentialExploreRollStillFailsClosed() {
    val before = JSONObject("""{
      "turn":8,
      "level":{"number":0,"name":"The Lobby"},
      "player":{"name":"Kai Akechi","hp":100},
      "flags":{}
    }""")
    val candidate = JSONObject(before.toString())
    val generated = JSONObject().put("reply", "x").put("ops", org.json.JSONArray())
    val rolls = JSONObject()
      .put("actionKind", "EXPLORE")
      .put("entityEncounter", JSONObject().put("success", true))
    val diagnostics = CanonFallbackPolicy.diagnostics(
      before, candidate, generated, rolls,
      "Tiếp tục khám phá", meta = false, repaired = true,
    )
    assertFalse(diagnostics.getBoolean("eligible"))
    assertEquals("consequential_roll", diagnostics.getString("reason"))
  }
'''
text = text[:class_end] + new_tests + text[class_end:]
kotlin_test.write_text(text, encoding="utf-8")

lowrisk_test = root / "test-low-risk-canon-fallback.mjs"
text = lowrisk_test.read_text(encoding="utf-8")
anchor = "requireContract(main.includes('canon_safe_fallback'), 'fallback snapshot marker missing');\n"
addition = anchor + (
    "requireContract(fallback.includes('lowRiskExplorationRecovery'), 'low-risk EXPLORE recovery gate missing');\n"
    "requireContract(!fallback.includes('entityBefore -> \\\"entity_present_before\\\"'), 'pre-existing Entity context must not reject a no-op fallback');\n"
    "requireContract(!fallback.includes('transitionBefore -> \\\"transition_ready_before\\\"'), 'pre-existing transition-ready context must not reject a no-op fallback');\n"
)
text = replace_once(text, anchor, addition, "canon fallback regression assertions")
lowrisk_test.write_text(text, encoding="utf-8")

# Source-level sanity. Full generated-runtime and Gradle coverage runs in PR CI.
assert "ANDROID_SWIPE_GESTURE_V3" in ui.read_text(encoding="utf-8")
assert "persistPendingDebugLog" in export_patch.read_text(encoding="utf-8")
assert "lowRiskExplorationRecovery" in canon.read_text(encoding="utf-8")
print("Device regression sources patched successfully.")

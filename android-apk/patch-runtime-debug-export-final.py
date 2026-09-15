from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
text = MAIN.read_text(encoding="utf-8")


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return source.replace(old, new, 1)


def method_bounds(source: str, signature: str) -> tuple[int, int]:
    start = source.find(signature)
    if start < 0:
        raise RuntimeError("method not found: " + signature)
    open_brace = source.find("{", start)
    depth = 0
    state = "code"
    escaped = False
    i = open_brace
    while i < len(source):
        ch = source[i]
        nxt = source[i + 1] if i + 1 < len(source) else ""
        if state == "string":
            if escaped:
                escaped = False
            elif ch == "\\":
                escaped = True
            elif ch == '"':
                state = "code"
        elif state == "char":
            if escaped:
                escaped = False
            elif ch == "\\":
                escaped = True
            elif ch == "'":
                state = "code"
        elif state == "line_comment":
            if ch == "\n": state = "code"
        elif state == "block_comment":
            if ch == "*" and nxt == "/":
                state = "code"
                i += 1
        else:
            if ch == '"': state = "string"
            elif ch == "'": state = "char"
            elif ch == "/" and nxt == "/":
                state = "line_comment"; i += 1
            elif ch == "/" and nxt == "*":
                state = "block_comment"; i += 1
            elif ch == "{": depth += 1
            elif ch == "}":
                depth -= 1
                if depth == 0: return start, i + 1
        i += 1
    raise RuntimeError("closing brace not found: " + signature)


text = replace_once(
    text,
    "var snapshotBusy=false;function requestSnapshot(){var s=document.getElementById('status');if(s)s.textContent='Snapshot chưa được cấu hình.';}",
    "var snapshotBusy=false;function requestSnapshot(){}",
    "remove disabled Snapshot status",
)
text = replace_once(
    text,
    "b.id='snapshotButton';b.type='button';b.textContent='Snapshot chưa cấu hình';b.disabled=true;",
    "b.id='debugLogButton';b.type='button';b.textContent='XUẤT LOG';b.disabled=false;b.addEventListener('click',function(){var s=document.getElementById('status');if(!window.Android||typeof Android.exportDebugLog!=='function'){if(s)s.textContent='Không tìm thấy debug export bridge.';return;}if(s)s.textContent='Đang chuẩn bị log debug…';Android.exportDebugLog(JSON.stringify(state));});",
    "replace Snapshot button",
)

field_anchor = "  private GameCoreFacade gameCore;\n"
if field_anchor not in text:
    raise RuntimeError("GameCore field anchor missing")
text = text.replace(
    field_anchor,
    field_anchor + "  private static final int DEBUG_LOG_EXPORT_REQUEST = 9042;\n  private String pendingDebugLogJson = null;\n",
    1,
)

init_anchor = "    gameCore = GameCoreFacade.create(getApplicationContext(), BuildConfig.DEBUG);\n"
if init_anchor not in text:
    raise RuntimeError("GameCore init anchor missing")
text = text.replace(init_anchor, init_anchor + "    initializeRuntimeDebugLog();\n", 1)

helpers = r'''  private void initializeRuntimeDebugLog() {
    try {
      JSONObject metadata = new JSONObject()
        .put("versionName", BuildConfig.VERSION_NAME)
        .put("versionCode", BuildConfig.VERSION_CODE)
        .put("buildType", BuildConfig.BUILD_TYPE)
        .put("debug", BuildConfig.DEBUG)
        .put("androidVersion", android.os.Build.VERSION.RELEASE)
        .put("androidSdk", android.os.Build.VERSION.SDK_INT)
        .put("providers", new JSONObject()
          .put("gemini", "Gemini model matrix")
          .put("haikuConfigured", haikuConfigured()));
      com.rabpit.backroom.core.RuntimeDebugLog.initializeSession(metadata);
    } catch (Exception ignored) {}
  }

  private int debugTurnFromJson(String raw) {
    try { return new JSONObject(raw == null ? "{}" : raw).optInt("turn", 0); }
    catch (Exception ignored) { return 0; }
  }

  private JSONObject debugPayload(String raw) {
    try { return new JSONObject(raw == null ? "{}" : raw); }
    catch (Exception ignored) { return new JSONObject().put("raw", raw == null ? "" : raw); }
  }

  private void debugEvent(String category, String event, int turn, Object payload) {
    try { com.rabpit.backroom.core.RuntimeDebugLog.recordEvent(category, event, turn, payload); }
    catch (Exception ignored) {}
  }

  private java.io.File pendingDebugLogFile() {
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
    try {
      JSONObject current = debugPayload(stateJson);
      int turn = current.optInt("turn", 0);
      debugEvent("ui_bridge", "export_log_requested", turn, new JSONObject().put("state", current));
      pendingDebugLogJson = com.rabpit.backroom.core.RuntimeDebugLog.exportJson(current);
      persistPendingDebugLog(pendingDebugLogJson);
      String stamp = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(new java.util.Date());
      android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT);
      intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
      intent.setType("application/json");
      intent.putExtra(android.content.Intent.EXTRA_TITLE, "Backroom-Debug-" + stamp + ".json");
      startActivityForResult(intent, DEBUG_LOG_EXPORT_REQUEST);
    } catch (Exception error) {
      clearPendingDebugLog();
      emit("backroomError", "Không thể chuẩn bị log debug: " + (error.getMessage() == null ? "unknown" : error.getMessage()));
    }
  }

  @Override protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
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
  }

'''
emit_anchor = "  private void emit(String function, String json) {\n"
if emit_anchor not in text:
    raise RuntimeError("emit anchor missing")
text = text.replace(emit_anchor, helpers + emit_anchor, 1)

emit_start, emit_end = method_bounds(text, "  private void emit(String function, String json) {")
emit_method = text[emit_start:emit_end]
emit_method = emit_method.replace(
    "  private void emit(String function, String json) {\n",
    "  private void emit(String function, String json) {\n    debugEvent(\"ui_bridge\", function, debugTurnFromJson(json), debugPayload(json));\n",
    1,
)
text = text[:emit_start] + emit_method + text[emit_end:]

bridge_anchor = "    @JavascriptInterface public void requestSnapshot(String stateJson) {\n"
if bridge_anchor not in text:
    raise RuntimeError("GameBridge Snapshot anchor missing")
export_bridge = r'''    @JavascriptInterface public void exportDebugLog(String stateJson) {
      runOnUiThread(() -> startDebugLogExport(stateJson));
    }

    @JavascriptInterface public void debugUiEvent(String event, String payloadJson) {
      debugEvent("ui", event == null ? "" : event, debugTurnFromJson(payloadJson), debugPayload(payloadJson));
    }

'''
text = text.replace(bridge_anchor, export_bridge + bridge_anchor, 1)

clear_old = '''    @JavascriptInterface public void clearCoreState() {
      if (gameCore != null) gameCore.clear();
    }
'''
if clear_old in text:
    text = text.replace(
        clear_old,
        '''    @JavascriptInterface public void clearCoreState() {
      debugEvent("game_core", "clear_core_state", 0, new JSONObject());
      if (gameCore != null) gameCore.clear();
    }
''',
        1,
    )

for marker in (
    "XUẤT LOG",
    "Android.exportDebugLog(JSON.stringify(state))",
    "ACTION_CREATE_DOCUMENT",
    "Backroom-Debug-",
    "RuntimeDebugLog.exportJson",
    "@JavascriptInterface public void exportDebugLog",
):
    if marker not in text:
        raise RuntimeError("debug export marker missing: " + marker)
if "b.textContent='Snapshot chưa cấu hình'" in text:
    raise RuntimeError("legacy Snapshot button survived")

MAIN.write_text(text, encoding="utf-8")
print("Runtime debug export installed: XUẤT LOG -> ACTION_CREATE_DOCUMENT JSON, with session/UI bridge tracing.")

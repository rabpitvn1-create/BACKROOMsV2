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
    raise RuntimeError("closing brace not found: " + signature)


field_anchor = "  private String pendingDebugLogJson = null;\n"
if field_anchor not in text:
    raise RuntimeError("runtime debug export must run before provider trace")
text = text.replace(field_anchor, field_anchor + "  private volatile int debugActiveTurn = 0;\n", 1)

helper_anchor = "  private void startDebugLogExport(String stateJson) {\n"
if helper_anchor not in text:
    raise RuntimeError("debug export helper anchor missing")
provider_helper = r'''  private void debugProviderAttempt(String provider, String phase, String role, String model, String slot, long latencyMs, Exception error, String failureClass) {
    JSONObject payload = new JSONObject()
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
    debugEvent("provider_routing", provider + "_" + phase, debugActiveTurn, payload);
    com.rabpit.backroom.core.RuntimeDebugLog.appendTurnStage(debugActiveTurn, "providerRouting", payload);
  }

'''
text = text.replace(helper_anchor, provider_helper + helper_anchor, 1)

matrix_start, matrix_end = method_bounds(text, "  private String geminiModelMatrixPolicy(")
matrix = text[matrix_start:matrix_end]
matrix = replace_once(
    matrix,
    "          long started = System.currentTimeMillis();\n          try {\n",
    "          long started = System.currentTimeMillis();\n          debugProviderAttempt(\"gemini\", \"start\", rememberWorker ? \"writer\" : \"auditor\", geminiModelLabel(modelIndex), \"K\" + (keyIndex + 1), 0L, null, \"\");\n          try {\n",
    "Gemini start trace",
)
matrix = replace_once(
    matrix,
    "            noteGeminiMatrixSuccess(modelIndex, keyIndex, System.currentTimeMillis() - started);\n",
    "            noteGeminiMatrixSuccess(modelIndex, keyIndex, System.currentTimeMillis() - started);\n            debugProviderAttempt(\"gemini\", \"success\", rememberWorker ? \"writer\" : \"auditor\", geminiModelLabel(modelIndex), \"K\" + (keyIndex + 1), System.currentTimeMillis() - started, null, \"\");\n",
    "Gemini success trace",
)
matrix = replace_once(
    matrix,
    "            String failureClass = noteGeminiMatrixFailure(modelIndex, keyIndex, error);\n",
    "            String failureClass = noteGeminiMatrixFailure(modelIndex, keyIndex, error);\n            debugProviderAttempt(\"gemini\", \"failure\", rememberWorker ? \"writer\" : \"auditor\", geminiModelLabel(modelIndex), \"K\" + (keyIndex + 1), System.currentTimeMillis() - started, error, failureClass);\n",
    "Gemini failure trace",
)
text = text[:matrix_start] + matrix + text[matrix_end:]

# Trace Haiku if the configured fallback lane is reached.
generate_start, generate_end = method_bounds(text, "  private String generateText(String prompt) throws Exception {")
generate = text[generate_start:generate_end]
if "String haikuResult = haikuText(prompt, 1800, 0.6);" in generate:
    generate = generate.replace(
        "        String haikuResult = haikuText(prompt, 1800, 0.6);",
        "        long writerHaikuStarted = System.currentTimeMillis();\n        debugProviderAttempt(\"haiku\", \"start\", \"writer\", BuildConfig.HAIKU_MODEL, \"\", 0L, null, \"\");\n        String haikuResult = haikuText(prompt, 1800, 0.6);",
        1,
    )
    generate = generate.replace(
        "        parseModelJson(haikuResult);",
        "        parseModelJson(haikuResult);\n        debugProviderAttempt(\"haiku\", \"success\", \"writer\", BuildConfig.HAIKU_MODEL, \"\", System.currentTimeMillis() - writerHaikuStarted, null, \"\");",
        1,
    )
    generate = generate.replace(
        "      } catch (Exception haikuError) {",
        "      } catch (Exception haikuError) {\n        debugProviderAttempt(\"haiku\", \"failure\", \"writer\", BuildConfig.HAIKU_MODEL, \"\", 0L, haikuError, \"fallback\");",
        1,
    )
text = text[:generate_start] + generate + text[generate_end:]

audit_text_start, audit_text_end = method_bounds(text, "  private String auditText(String prompt, int excludedGeminiWorker) throws Exception {")
audit_text = text[audit_text_start:audit_text_end]
if "String haikuAuditResult = haikuText(prompt, 650, 0.1);" in audit_text:
    audit_text = audit_text.replace(
        "        String haikuAuditResult = haikuText(prompt, 650, 0.1);",
        "        long auditHaikuStarted = System.currentTimeMillis();\n        debugProviderAttempt(\"haiku\", \"start\", \"auditor\", BuildConfig.HAIKU_MODEL, \"\", 0L, null, \"\");\n        String haikuAuditResult = haikuText(prompt, 650, 0.1);",
        1,
    )
    audit_text = audit_text.replace(
        "        parseModelJson(haikuAuditResult);",
        "        parseModelJson(haikuAuditResult);\n        debugProviderAttempt(\"haiku\", \"success\", \"auditor\", BuildConfig.HAIKU_MODEL, \"\", System.currentTimeMillis() - auditHaikuStarted, null, \"\");",
        1,
    )
    audit_text = audit_text.replace(
        "      } catch (Exception haikuError) {",
        "      } catch (Exception haikuError) {\n        debugProviderAttempt(\"haiku\", \"failure\", \"auditor\", BuildConfig.HAIKU_MODEL, \"\", 0L, haikuError, \"fallback\");",
        1,
    )
text = text[:audit_text_start] + audit_text + text[audit_text_end:]

run_start, run_end = method_bounds(text, "  private JSONObject runAudit(")
run_audit = text[run_start:run_end]
run_audit = replace_once(
    run_audit,
    "    JSONObject result = parseModelJson(auditText(prompt, excludedWorker));\n",
    "    String auditRaw = auditText(prompt, excludedWorker);\n    JSONObject result = parseModelJson(auditRaw);\n    com.rabpit.backroom.core.RuntimeDebugLog.appendTurnStage(before.optInt(\"turn\", 0), \"audits\", new JSONObject().put(\"scope\", scope).put(\"prompt\", prompt).put(\"raw\", auditRaw).put(\"parsed\", result));\n",
    "audit raw trace",
)
text = text[:run_start] + run_audit + text[run_end:]

for marker in (
    "providerRouting",
    "geminiModelLabel(modelIndex)",
    '"K" + (keyIndex + 1)',
    "auditRaw",
    'appendTurnStage(before.optInt("turn", 0), "audits"',
):
    if marker not in text:
        raise RuntimeError("provider trace marker missing: " + marker)

MAIN.write_text(text, encoding="utf-8")
print("Runtime provider trace installed: Gemini model/K lane attempts, Haiku fallback, latency/failure status, and raw auditor payloads.")

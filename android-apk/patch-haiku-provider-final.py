from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
GRADLE = ROOT / "app/build.gradle"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return text.replace(old, new, 1)


# ---------------------------------------------------------------------------
# Build-time configuration. Values come from the CI environment and are escaped
# by the existing Gradle `secret` helper before entering BuildConfig.
# ---------------------------------------------------------------------------
gradle = GRADLE.read_text(encoding="utf-8")
anchor = '    buildConfigField "String", "GEMINI_API_KEY_5", "\\\"" + secret("GEMINI_API_KEY_5") + "\\\""\n'
haiku_fields = anchor + '''    buildConfigField "String", "HAIKU_API", "\\\"" + secret("HAIKU_API") + "\\\""\n    buildConfigField "String", "HAIKU_BASE_URL", "\\\"" + secret("HAIKU_BASE_URL") + "\\\""\n    buildConfigField "String", "HAIKU_MODEL", "\\\"" + secret("HAIKU_MODEL") + "\\\""\n'''
if '"HAIKU_API"' not in gradle:
    gradle = replace_once(gradle, anchor, haiku_fields, "Haiku BuildConfig fields")
GRADLE.write_text(gradle, encoding="utf-8")


# ---------------------------------------------------------------------------
# Runtime provider. Gemini remains primary. Haiku is the independent fallback
# for writer and auditor text calls. The base URL may be either an OpenAI-style
# base (/v1), a full /chat/completions endpoint, or an Anthropic /messages
# endpoint. No API token is ever written to logs or exception messages.
# ---------------------------------------------------------------------------
text = MAIN.read_text(encoding="utf-8")

generate_anchor = '  private String generateText(String prompt) throws Exception {\n'
if generate_anchor not in text:
    raise RuntimeError("generateText anchor missing before Haiku integration")

helpers = r'''  private boolean haikuConfigured() {
    return BuildConfig.HAIKU_API != null && !BuildConfig.HAIKU_API.trim().isEmpty() &&
      BuildConfig.HAIKU_BASE_URL != null && !BuildConfig.HAIKU_BASE_URL.trim().isEmpty() &&
      BuildConfig.HAIKU_MODEL != null && !BuildConfig.HAIKU_MODEL.trim().isEmpty();
  }

  private String haikuBaseUrl() throws Exception {
    String base = BuildConfig.HAIKU_BASE_URL == null ? "" : BuildConfig.HAIKU_BASE_URL.trim();
    if (base.isEmpty()) throw new Exception("HAIKU_BASE_URL chưa được cấu hình.");
    if (!base.toLowerCase(java.util.Locale.ROOT).startsWith("https://")) {
      throw new Exception("HAIKU_BASE_URL phải dùng HTTPS.");
    }
    while (base.endsWith("/") && base.length() > "https://".length()) base = base.substring(0, base.length() - 1);
    return base;
  }

  private String haikuEndpoint(String suffix) throws Exception {
    String base = haikuBaseUrl();
    if (base.endsWith(suffix)) return base;
    return base + suffix;
  }

  private String postJsonHaiku(String endpoint, JSONObject payload, boolean anthropic, int timeoutMs) throws Exception {
    HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
    connection.setRequestMethod("POST");
    connection.setConnectTimeout(5000);
    connection.setReadTimeout(timeoutMs);
    connection.setDoOutput(true);
    connection.setRequestProperty("Content-Type", "application/json");
    if (anthropic) {
      connection.setRequestProperty("x-api-key", BuildConfig.HAIKU_API);
      connection.setRequestProperty("anthropic-version", "2023-06-01");
    } else {
      connection.setRequestProperty("Authorization", "Bearer " + BuildConfig.HAIKU_API);
    }
    try (OutputStream output = connection.getOutputStream()) {
      output.write(payload.toString().getBytes("UTF-8"));
    }

    int status = connection.getResponseCode();
    InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
    StringBuilder body = new StringBuilder();
    if (stream != null) {
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"))) {
        String line;
        while ((line = reader.readLine()) != null) body.append(line);
      }
    }
    connection.disconnect();

    if (status < 200 || status >= 300) {
      String detail = body.length() > 220 ? body.substring(0, 220) : body.toString();
      throw new HttpError(status, "Haiku HTTP " + status + (detail.isEmpty() ? "" : ": " + detail));
    }
    return body.toString();
  }

  private String haikuOpenAiResponseText(JSONObject result) {
    String direct = result.optString("output_text", "").trim();
    if (!direct.isEmpty()) return direct;
    JSONArray choices = result.optJSONArray("choices");
    if (choices == null) return "";
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < choices.length(); i++) {
      JSONObject choice = choices.optJSONObject(i);
      if (choice == null) continue;
      JSONObject message = choice.optJSONObject("message");
      Object content = message != null ? message.opt("content") : null;
      if (content instanceof String) {
        String piece = ((String) content).trim();
        if (!piece.isEmpty()) {
          if (out.length() > 0) out.append('\n');
          out.append(piece);
        }
      } else if (content instanceof JSONArray) {
        JSONArray parts = (JSONArray) content;
        for (int p = 0; p < parts.length(); p++) {
          JSONObject part = parts.optJSONObject(p);
          String piece = part == null ? "" : part.optString("text", "").trim();
          if (!piece.isEmpty()) {
            if (out.length() > 0) out.append('\n');
            out.append(piece);
          }
        }
      }
      if (out.length() == 0) {
        String legacy = choice.optString("text", "").trim();
        if (!legacy.isEmpty()) out.append(legacy);
      }
    }
    return out.toString();
  }

  private String haikuAnthropicResponseText(JSONObject result) {
    JSONArray content = result.optJSONArray("content");
    if (content == null) return "";
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < content.length(); i++) {
      JSONObject part = content.optJSONObject(i);
      if (part == null) continue;
      String piece = part.optString("text", "").trim();
      if (!piece.isEmpty()) {
        if (out.length() > 0) out.append('\n');
        out.append(piece);
      }
    }
    return out.toString();
  }

  private String haikuOpenAiText(String prompt, int maxOutputTokens, double temperature, int timeoutMs) throws Exception {
    JSONObject body = new JSONObject()
      .put("model", BuildConfig.HAIKU_MODEL.trim())
      .put("messages", new JSONArray().put(new JSONObject().put("role", "user").put("content", prompt)))
      .put("temperature", temperature);
    if (maxOutputTokens > 0) body.put("max_tokens", maxOutputTokens);
    JSONObject result = new JSONObject(postJsonHaiku(haikuEndpoint("/chat/completions"), body, false, timeoutMs));
    String output = haikuOpenAiResponseText(result).trim();
    if (output.isEmpty()) throw new Exception("Haiku không trả nội dung.");
    return output;
  }

  private String haikuAnthropicText(String prompt, int maxOutputTokens, double temperature, int timeoutMs) throws Exception {
    JSONObject body = new JSONObject()
      .put("model", BuildConfig.HAIKU_MODEL.trim())
      .put("max_tokens", maxOutputTokens > 0 ? maxOutputTokens : 1024)
      .put("temperature", temperature)
      .put("messages", new JSONArray().put(new JSONObject().put("role", "user").put("content", prompt)));
    JSONObject result = new JSONObject(postJsonHaiku(haikuEndpoint("/messages"), body, true, timeoutMs));
    String output = haikuAnthropicResponseText(result).trim();
    if (output.isEmpty()) throw new Exception("Haiku không trả nội dung.");
    return output;
  }

  private boolean haikuProtocolMismatch(Exception error) {
    if (!(error instanceof HttpError)) return false;
    int status = ((HttpError) error).status;
    return status == 400 || status == 404 || status == 405 || status == 415 || status == 422;
  }

  private String haikuText(String prompt, int maxOutputTokens, double temperature) throws Exception {
    if (!haikuConfigured()) throw new Exception("Haiku chưa được cấu hình đầy đủ.");
    String base = haikuBaseUrl();
    int timeoutMs = maxOutputTokens <= 700 ? 30_000 : 45_000;
    if (base.endsWith("/messages")) return haikuAnthropicText(prompt, maxOutputTokens, temperature, timeoutMs);
    if (base.endsWith("/chat/completions")) return haikuOpenAiText(prompt, maxOutputTokens, temperature, timeoutMs);
    try {
      return haikuOpenAiText(prompt, maxOutputTokens, temperature, timeoutMs);
    } catch (Exception openAiError) {
      if (!haikuProtocolMismatch(openAiError)) throw openAiError;
      return haikuAnthropicText(prompt, maxOutputTokens, temperature, timeoutMs);
    }
  }

  private String auditText(String prompt, int excludedGeminiWorker) throws Exception {
    Exception geminiError;
    try {
      return geminiAuditText(prompt, excludedGeminiWorker);
    } catch (Exception error) {
      geminiError = error;
    }
    if (!haikuConfigured()) throw geminiError;
    try {
      return haikuText(prompt, 650, 0.1);
    } catch (Exception haikuError) {
      if (networkFailure(geminiError) && networkFailure(haikuError)) throw new Exception(networkFailureMessage());
      throw haikuError;
    }
  }

'''

if 'private boolean haikuConfigured()' not in text:
    text = text.replace(generate_anchor, helpers + generate_anchor, 1)

new_generate = r'''  private String generateText(String prompt) throws Exception {
    emit("backroomProvider", "Gemini 3.6 Flash");
    Exception geminiError;
    try {
      String geminiResult = geminiText(prompt);
      emit("backroomProvider", geminiModelLabel(lastGeminiModel) + " K" + (lastGeminiWorker + 1));
      return geminiResult;
    } catch (Exception error) {
      geminiError = error;
    }

    if (haikuConfigured()) {
      emit("backroomProvider", "Haiku");
      try {
        String haikuResult = haikuText(prompt, 1800, 0.6);
        emit("backroomProvider", "Haiku");
        return haikuResult;
      } catch (Exception haikuError) {
        if (networkFailure(geminiError) && networkFailure(haikuError)) throw new Exception(networkFailureMessage());
        throw haikuError;
      }
    }

    if (networkFailure(geminiError)) throw new Exception(networkFailureMessage());
    throw geminiError;
  }
'''
generate_start = text.find(generate_anchor)
if generate_start < 0:
    raise RuntimeError("Haiku generateText method start missing")
parse_start = text.find("\n  private JSONObject parseModelJson", generate_start)
if parse_start < 0:
    raise RuntimeError("Haiku parseModelJson boundary missing")
text = text[:generate_start] + new_generate.rstrip("\n") + text[parse_start:]

old_audit_call = '    JSONObject result = parseModelJson(geminiAuditText(prompt, excludedWorker));\n'
new_audit_call = '    JSONObject result = parseModelJson(auditText(prompt, excludedWorker));\n'
if new_audit_call not in text:
    text = replace_once(text, old_audit_call, new_audit_call, "Haiku audit fallback")

for required in (
    "BuildConfig.HAIKU_API",
    "BuildConfig.HAIKU_BASE_URL",
    "BuildConfig.HAIKU_MODEL",
    "private String haikuText(String prompt, int maxOutputTokens, double temperature)",
    "private String auditText(String prompt, int excludedGeminiWorker)",
    'emit("backroomProvider", "Haiku")',
    "parseModelJson(auditText(prompt, excludedWorker))",
    'connection.setRequestProperty("Authorization", "Bearer " + BuildConfig.HAIKU_API)',
    'connection.setRequestProperty("x-api-key", BuildConfig.HAIKU_API)',
    'connection.setRequestProperty("anthropic-version", "2023-06-01")',
    'haikuEndpoint("/chat/completions")',
    'haikuEndpoint("/messages")',
):
    if required not in text:
        raise RuntimeError("Haiku runtime marker missing: " + required)

MAIN.write_text(text, encoding="utf-8")
print("Haiku provider integrated: configurable API/base/model, HTTPS-only, Gemini writer/auditor fallback, OpenAI + Anthropic request compatibility.")

package com.rabpit.backroom;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import com.rabpit.backroom.core.CaoMinhVoiceContract;
import com.rabpit.backroom.core.CombatChoiceEngine;
import com.rabpit.backroom.core.GameCoreFacade;
import com.rabpit.backroom.core.GmChoiceContract;
import com.rabpit.backroom.core.GmNarratorContract;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
  private static final String TAG = "BackroomMain";
  private WebView webView;
  private final ExecutorService io = Executors.newSingleThreadExecutor();
  private GameCoreFacade gameCore;
  private static final String GEMINI_MODEL = "gemini-3.6-flash";
  private static final String HAIKU_DEFAULT_BASE_URL = "https://api.anthropic.com/v1/messages";
  private static final String HAIKU_DEFAULT_MODEL = "claude-haiku-4-5-20251001";
  private static final long HAIKU_RETRY_DELAY_MS = 1_200L;
  private static final int[] RETRYABLE = {408, 429, 500, 502, 503, 504};
  private static final String GM_STYLE_EXAMPLES_ASSET = "knowledge/gm_style_examples.json";
  private String gmStyleExamplesCache;

  @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
  @Override public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    gameCore = GameCoreFacade.create(getApplicationContext(), BuildConfig.DEBUG);
    webView = new WebView(this);
    WebSettings settings = webView.getSettings();
    settings.setJavaScriptEnabled(true);
    settings.setDomStorageEnabled(true);
    settings.setAllowFileAccess(true);
    webView.setWebViewClient(new WebViewClient() {
      @Override public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);
        installUiScripts();
      }
    });
    webView.addJavascriptInterface(new GameBridge(), "Android");
    setContentView(webView);
    safeApplyImmersiveFullscreen("onCreate");
    webView.loadUrl("file:///android_asset/index.html");
  }

  @Override protected void onResume() {
    super.onResume();
    safeApplyImmersiveFullscreen("onResume");
  }

  @Override public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    if (hasFocus) safeApplyImmersiveFullscreen("onWindowFocusChanged");
  }

  private void safeApplyImmersiveFullscreen(String source) {
    try {
      applyImmersiveFullscreen();
    } catch (Throwable error) {
      Log.w(TAG, "Immersive fullscreen failed in " + source + "; keeping app alive.", error);
      try {
        applyLegacyFullscreenFlags();
      } catch (Throwable fallbackError) {
        Log.w(TAG, "Legacy fullscreen fallback also failed; continuing without immersive mode.", fallbackError);
      }
    }
  }

  private void applyImmersiveFullscreen() {
    Window window = getWindow();

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      WindowManager.LayoutParams attributes = window.getAttributes();
      attributes.layoutInDisplayCutoutMode =
          WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
      window.setAttributes(attributes);

      // Android 15+ with targetSdk 35 already enforces edge-to-edge. Re-applying the
      // deprecated decor-fits path here has caused OEM launch crashes in this project before.
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        window.setDecorFitsSystemWindows(false);
      }

      WindowInsetsController controller = window.getInsetsController();
      if (controller == null) {
        applyLegacyFullscreenFlags();
        return;
      }
      controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
      controller.setSystemBarsBehavior(
          WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
      return;
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      WindowManager.LayoutParams attributes = window.getAttributes();
      attributes.layoutInDisplayCutoutMode =
          WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
      window.setAttributes(attributes);
    }
    applyLegacyFullscreenFlags();
  }

  private void applyLegacyFullscreenFlags() {
    getWindow().getDecorView().setSystemUiVisibility(
        View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
  }

  @Override protected void onDestroy() {
    if (gameCore != null) gameCore.close();
    io.shutdownNow();
    if (webView != null) webView.destroy();
    super.onDestroy();
  }

  private String readAssetText(String path) throws Exception {
    StringBuilder text = new StringBuilder();
    try (InputStream input = getAssets().open(path);
         BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"))) {
      String line;
      while ((line = reader.readLine()) != null) text.append(line).append('\n');
    }
    return text.toString();
  }

  private String gmStyleExamplesContext() {
    if (gmStyleExamplesCache != null) return gmStyleExamplesCache;
    try {
      JSONObject root = new JSONObject(readAssetText(GM_STYLE_EXAMPLES_ASSET));
      StringBuilder output = new StringBuilder("GM STYLE FEW-SHOT EXAMPLES:\n");
      String instruction = root.optString("instruction", "").trim();
      if (!instruction.isEmpty()) output.append(instruction).append("\n");

      JSONArray examples = root.optJSONArray("goodExamples");
      if (examples != null) {
        for (int i = 0; i < examples.length(); i++) {
          JSONObject example = examples.optJSONObject(i);
          if (example == null) continue;
          String player = example.optString("player", "").trim();
          String gm = example.optString("gm", "").trim();
          if (player.isEmpty() || gm.isEmpty()) continue;
          output.append("\nGOOD EXAMPLE ").append(i + 1).append("\n");
          output.append("PLAYER: ").append(player).append("\n");
          output.append("GM: ").append(gm).append("\n");
        }
      }

      JSONObject bad = root.optJSONObject("badExample");
      if (bad != null) {
        String player = bad.optString("player", "").trim();
        String gm = bad.optString("gm", "").trim();
        String why = bad.optString("why", "").trim();
        if (!player.isEmpty() && !gm.isEmpty()) {
          output.append("\nBAD EXAMPLE — DO NOT IMITATE\n");
          output.append("PLAYER: ").append(player).append("\n");
          output.append("GM: ").append(gm).append("\n");
          if (!why.isEmpty()) output.append("WHY BAD: ").append(why).append("\n");
        }
      }

      output.append("\nUse these examples only as style references. Never copy their wording, events, imagery, locations, conclusions, or hidden outcomes into the current turn unless current state independently supports them.\n");
      gmStyleExamplesCache = output.toString();
    } catch (Exception error) {
      Log.w(TAG, "Unable to load GM style examples; using narrative contract only.", error);
      gmStyleExamplesCache = "";
    }
    return gmStyleExamplesCache;
  }

  private void installUiScripts() {
    try {
      String snapshotUi = readAssetText("snapshot-ui.js");
      String gmChoiceUi = readAssetText("gm-choice-ui.js");
      String inventoryUi = readAssetText("inventory-ui.js");
      String partyUi = readAssetText("party-ui.js");
      String playerActionUi = readAssetText("player-action-ui.js");
      webView.evaluateJavascript(snapshotUi, ignored ->
        webView.evaluateJavascript(gmChoiceUi, ignoredChoice ->
          webView.evaluateJavascript(inventoryUi, ignoredInventory ->
            webView.evaluateJavascript(partyUi, ignoredParty ->
              webView.evaluateJavascript(playerActionUi, null)))));
    } catch (Exception e) {
      Log.e(TAG, "Unable to install WebView UI scripts", e);
    }
  }

  private boolean retryable(int code) {
    for (int value : RETRYABLE) if (value == code) return true;
    return false;
  }

  private String[] geminiKeys() {
    return new String[] {
      BuildConfig.GEMINI_API_KEY_1,
      BuildConfig.GEMINI_API_KEY_2,
      BuildConfig.GEMINI_API_KEY_3,
      BuildConfig.GEMINI_API_KEY_4,
      BuildConfig.GEMINI_API_KEY_5
    };
  }

  private void sleepBeforeNextGeminiKey(int keyIndex, int status) {
    if (status != 0 && !retryable(status)) return;
    long delayMs = Math.min(2_000L, 500L + (long)keyIndex * 350L);
    try {
      Thread.sleep(delayMs);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
    }
  }

  private String postJson(String endpoint, String key, String authHeader, JSONObject payload) throws Exception {
    HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
    connection.setRequestMethod("POST");
    connection.setConnectTimeout(20000);
    connection.setReadTimeout(60000);
    connection.setDoOutput(true);
    connection.setRequestProperty("Content-Type", "application/json");
    connection.setRequestProperty(authHeader, authHeader.equals("Authorization") ? "Bearer " + key : key);
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
      throw new HttpError(status, "Provider HTTP " + status + (detail.isEmpty() ? "" : ": " + detail));
    }
    return body.toString();
  }

  private static boolean isContentOrJsonError(Exception error) {
    if (error == null || error instanceof HttpError) return false;
    String msg = error.getMessage();
    if (msg == null) return false;
    return msg.contains("JSON") || msg.contains("AI không") || msg.contains("phản hồi");
  }

  private String geminiText(String prompt) throws Exception {
    Exception last = null;
    String[] keys = geminiKeys();
    boolean configured = false;
    for (int keyIndex = 0; keyIndex < keys.length; keyIndex++) {
      String key = keys[keyIndex];
      if (key == null || key.trim().isEmpty()) continue;
      configured = true;
      try {
        JSONObject part = new JSONObject().put("text", prompt);
        JSONObject contents = new JSONObject().put("role", "user").put("parts", new JSONArray().put(part));
        JSONObject config = new JSONObject().put("responseMimeType", "application/json").put("temperature", 0.8);
        JSONObject body = new JSONObject().put("contents", new JSONArray().put(contents)).put("generationConfig", config);
        JSONObject result = new JSONObject(postJson(
            "https://generativelanguage.googleapis.com/v1beta/models/" + GEMINI_MODEL + ":generateContent",
            key, "x-goog-api-key", body));
        JSONArray candidates = result.optJSONArray("candidates");
        StringBuilder text = new StringBuilder();
        if (candidates != null) {
          for (int c = 0; c < candidates.length(); c++) {
            JSONObject candidate = candidates.optJSONObject(c);
            JSONObject providerContent = candidate != null ? candidate.optJSONObject("content") : null;
            JSONArray parts = providerContent != null ? providerContent.optJSONArray("parts") : null;
            if (parts == null) continue;
            for (int p = 0; p < parts.length(); p++) {
              JSONObject responsePart = parts.optJSONObject(p);
              String piece = responsePart != null ? responsePart.optString("text", "").trim() : "";
              if (!piece.isEmpty()) {
                if (text.length() > 0) text.append('\n');
                text.append(piece);
              }
            }
          }
        }
        if (text.length() == 0) throw new Exception("Gemini không trả nội dung.");
        String output = text.toString();
        parseModelJson(output);
        return output;
      } catch (Exception error) {
        last = error;
        if (isContentOrJsonError(error)) {
          throw error;
        }
        int status = error instanceof HttpError ? ((HttpError)error).status : 0;
        if (keyIndex < keys.length - 1) sleepBeforeNextGeminiKey(keyIndex, status);
      }
    }
    if (!configured) throw new Exception("Không có Gemini API key trong APK.");
    throw last != null ? last : new Exception("Tất cả Gemini API key đều không khả dụng.");
  }

  private boolean haikuConfigured() {
    return BuildConfig.HAIKU_API != null && !BuildConfig.HAIKU_API.trim().isEmpty();
  }

  private String haikuModel() {
    String configured = BuildConfig.HAIKU_MODEL == null ? "" : BuildConfig.HAIKU_MODEL.trim();
    return configured.isEmpty() ? HAIKU_DEFAULT_MODEL : configured;
  }

  private String haikuBaseUrl() throws Exception {
    String configured = BuildConfig.HAIKU_BASE_URL == null ? "" : BuildConfig.HAIKU_BASE_URL.trim();
    String base = configured.isEmpty() ? HAIKU_DEFAULT_BASE_URL : configured;
    if (!base.toLowerCase(java.util.Locale.ROOT).startsWith("https://")) {
      throw new Exception("HAIKU_BASE_URL phải dùng HTTPS.");
    }
    while (base.endsWith("/") && base.length() > "https://".length()) {
      base = base.substring(0, base.length() - 1);
    }
    return base;
  }

  private String haikuEndpoint(String suffix) throws Exception {
    String base = haikuBaseUrl();
    if (base.endsWith(suffix)) return base;
    if (base.endsWith("/v1")) return base + suffix;
    if ("/messages".equals(suffix) && !base.contains("/v1")) return base + "/v1/messages";
    return base + suffix;
  }

  private String postJsonHaiku(String endpoint, JSONObject payload, boolean anthropic) throws Exception {
    HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
    connection.setRequestMethod("POST");
    connection.setConnectTimeout(20_000);
    connection.setReadTimeout(60_000);
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

  private String haikuAnthropicText(String prompt) throws Exception {
    JSONObject body = new JSONObject()
        .put("model", haikuModel())
        .put("max_tokens", 2048)
        .put("temperature", 0.6)
        .put("messages", new JSONArray().put(
            new JSONObject().put("role", "user").put("content", prompt)));
    JSONObject result = new JSONObject(postJsonHaiku(haikuEndpoint("/messages"), body, true));
    JSONArray content = result.optJSONArray("content");
    StringBuilder text = new StringBuilder();
    if (content != null) {
      for (int i = 0; i < content.length(); i++) {
        JSONObject part = content.optJSONObject(i);
        String piece = part == null ? "" : part.optString("text", "").trim();
        if (!piece.isEmpty()) {
          if (text.length() > 0) text.append('\n');
          text.append(piece);
        }
      }
    }
    if (text.length() == 0) throw new Exception("Haiku không trả nội dung.");
    return text.toString();
  }

  private String haikuOpenAiText(String prompt) throws Exception {
    JSONObject body = new JSONObject()
        .put("model", haikuModel())
        .put("temperature", 0.6)
        .put("max_tokens", 2048)
        .put("messages", new JSONArray().put(
            new JSONObject().put("role", "user").put("content", prompt)));
    JSONObject result = new JSONObject(postJsonHaiku(haikuEndpoint("/chat/completions"), body, false));
    JSONArray choices = result.optJSONArray("choices");
    if (choices == null || choices.length() == 0) throw new Exception("Haiku không trả nội dung.");
    JSONObject first = choices.optJSONObject(0);
    JSONObject message = first == null ? null : first.optJSONObject("message");
    Object rawContent = message == null ? null : message.opt("content");
    StringBuilder text = new StringBuilder();
    if (rawContent instanceof String) {
      text.append(((String)rawContent).trim());
    } else if (rawContent instanceof JSONArray) {
      JSONArray parts = (JSONArray)rawContent;
      for (int i = 0; i < parts.length(); i++) {
        JSONObject part = parts.optJSONObject(i);
        String piece = part == null ? "" : part.optString("text", "").trim();
        if (!piece.isEmpty()) {
          if (text.length() > 0) text.append('\n');
          text.append(piece);
        }
      }
    }
    if (text.length() == 0) throw new Exception("Haiku không trả nội dung.");
    return text.toString();
  }

  private boolean protocolMismatch(Exception error) {
    if (!(error instanceof HttpError)) return false;
    int status = ((HttpError)error).status;
    return status == 400 || status == 404 || status == 405 || status == 415 || status == 422;
  }

  private String haikuTextOnce(String prompt) throws Exception {
    String base = haikuBaseUrl();
    String output;
    if (base.endsWith("/chat/completions")) {
      output = haikuOpenAiText(prompt);
    } else if (base.endsWith("/messages") || base.contains("api.anthropic.com")) {
      output = haikuAnthropicText(prompt);
    } else {
      try {
        output = haikuOpenAiText(prompt);
      } catch (Exception openAiError) {
        if (!protocolMismatch(openAiError)) throw openAiError;
        output = haikuAnthropicText(prompt);
      }
    }
    parseModelJson(output);
    return output;
  }

  private String haikuText(String prompt) throws Exception {
    if (!haikuConfigured()) throw new Exception("HAIKU_API chưa được cấu hình.");
    Exception last = null;
    for (int attempt = 0; attempt < 2; attempt++) {
      try {
        return haikuTextOnce(prompt);
      } catch (Exception error) {
        last = error;
        if (isContentOrJsonError(error)) {
          break;
        }
        int status = error instanceof HttpError ? ((HttpError)error).status : 0;
        if (attempt == 0 && (status == 0 || retryable(status))) {
          Log.w(TAG, "Haiku transport/server attempt failed; retrying once.");
          try {
            Thread.sleep(HAIKU_RETRY_DELAY_MS);
          } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
          }
          continue;
        }
        break;
      }
    }
    throw last != null ? last : new Exception("Haiku không khả dụng.");
  }

  private String providerErrorSummary(Exception error) {
    if (error == null) return "không xác định";
    String message = error.getMessage();
    if (message == null || message.trim().isEmpty()) return error.getClass().getSimpleName();
    return message.length() > 260 ? message.substring(0, 260) : message;
  }

  private String generateText(String prompt) throws Exception {
    Exception haikuError;
    try {
      return haikuText(prompt);
    } catch (Exception error) {
      haikuError = error;
      Log.w(TAG, "Haiku primary failed; falling back to Gemini.");
    }

    try {
      return geminiText(prompt);
    } catch (Exception geminiError) {
      throw new Exception(
          "Haiku và toàn bộ Gemini fallback đều không khả dụng. Haiku: "
              + providerErrorSummary(haikuError)
              + " | Gemini: "
              + providerErrorSummary(geminiError));
    }
  }

  private JSONObject parseModelJson(String raw) throws Exception {
    if (raw == null) throw new Exception("AI không trả dữ liệu.");
    String text = raw.trim();
    if (text.startsWith("```")) {
      int firstNewline = text.indexOf('\n');
      if (firstNewline >= 0) text = text.substring(firstNewline + 1);
      int fence = text.lastIndexOf("```");
      if (fence >= 0) text = text.substring(0, fence);
      text = text.trim();
    }
    int start = text.indexOf('{');
    int end = text.lastIndexOf('}');
    if (start < 0 || end <= start) throw new Exception("AI trả JSON không hợp lệ.");
    return new JSONObject(text.substring(start, end + 1));
  }

  private void mergeObject(JSONObject target, JSONObject patch) throws Exception {
    Iterator<String> keys = patch.keys();
    while (keys.hasNext()) {
      String key = keys.next();
      target.put(key, patch.get(key));
    }
  }

  private String clipped(Object value, int max) {
    String text = value == null ? "" : String.valueOf(value);
    return text.length() > max ? text.substring(text.length() - max) : text;
  }

  private String recentStoryContext(JSONObject state) {
    JSONArray log = state == null ? null : state.optJSONArray("log");
    if (log == null || log.length() == 0) return "(chưa có lượt trước)";

    StringBuilder recent = new StringBuilder();
    int start = Math.max(0, log.length() - 6);
    for (int i = start; i < log.length(); i++) {
      JSONObject entry = log.optJSONObject(i);
      if (entry == null) continue;
      String text = entry.optString("text", "").trim();
      if (text.isEmpty()) continue;
      if (recent.length() > 0) recent.append("\n");
      boolean player = "player".equals(entry.optString("role", ""));
      recent.append(player ? "PLAYER: " : "GM: ");
      recent.append(clipped(text, 1400));
    }
    return recent.length() == 0 ? "(chưa có lượt trước)" : recent.toString();
  }

  private String appendEncounterDialogue(String reply, JSONArray dialogue) {
    if (dialogue == null || dialogue.length() == 0) return reply;
    StringBuilder output = new StringBuilder(reply == null ? "" : reply.trim());
    for (int i = 0; i < dialogue.length(); i++) {
      String line = dialogue.optString(i, "").trim();
      if (line.isEmpty()) continue;
      if (output.length() > 0) output.append("\n\n");
      output.append(line);
    }
    return output.toString();
  }

  private String encounterKey(JSONObject state) {
    JSONObject flags = state == null ? null : state.optJSONObject("flags");
    return flags == null ? "" : flags.optString("entityEncounterKey", "").trim().toLowerCase();
  }

  private int lastGmLogIndex(JSONObject state) {
    JSONArray log = state == null ? null : state.optJSONArray("log");
    if (log == null || log.length() == 0) return 0;
    for (int i = log.length() - 1; i >= 0; i--) {
      JSONObject entry = log.optJSONObject(i);
      if (entry != null && !"player".equals(entry.optString("role"))) return i;
    }
    return Math.max(0, log.length() - 1);
  }

  private void emit(String function, String json) {
    String script = "window." + function + "(" + JSONObject.quote(json) + ")";
    runOnUiThread(() -> webView.evaluateJavascript(script, null));
  }

  private class GameBridge {
    @JavascriptInterface public void submitTurn(String stateJson, String action) {
      io.execute(() -> {
        JSONObject committedBeforeGemini = null;
        long tStart = System.currentTimeMillis();
        try {
          JSONObject submitted = new JSONObject(stateJson);

          if (CombatChoiceEngine.isActive(submitted)) {
            throw new Exception("Đang chiến đấu. Hãy dùng khung Poker Dice trong GAME MASTER.");
          }

          String existingEncounter = encounterKey(submitted);
          if (CombatChoiceEngine.isKnownEntity(existingEncounter)) {
            CombatChoiceEngine.start(submitted, existingEncounter, lastGmLogIndex(submitted));
            submitted = new JSONObject(gameCore.normalizeState(submitted.toString()));
            emit("backroomCombatDiceState", submitted.toString());
            return;
          }

          long tPreStart = System.currentTimeMillis();
          JSONObject localResult = new JSONObject(gameCore.processRule(stateJson, action));
          long tPreEnd = System.currentTimeMillis();

          if (localResult.optBoolean("handled", false)) {
            emit("backroomTurn", localResult.getJSONObject("state").toString());
            return;
          }

          JSONObject state = localResult.optJSONObject("state");
          if (state == null) state = submitted;
          committedBeforeGemini = new JSONObject(state.toString());
          String coreBeforeJson = state.toString();

          long tCtxStart = System.currentTimeMillis();
          String levelContext = gameCore.levelPromptContext(coreBeforeJson, action);
          String entityContext = gameCore.entityPromptContext(coreBeforeJson);
          String itemContext = gameCore.itemPromptContext(coreBeforeJson);
          String characterContext = gameCore.characterPromptContext(coreBeforeJson);
          JSONObject promptState = new JSONObject(state.toString());
          promptState.remove("levelRoute");
          promptState.remove("log");
          String recentStory = recentStoryContext(state);
          String gmStyleExamples = gmStyleExamplesContext();
          String gmNarratorContract = GmNarratorContract.promptContext();
          String caoMinhCard = GmNarratorContract.caoMinhNarrativeCard();
          String prompt = "Bạn là Game Master của text game Backrooms (xianxia x Backrooms).\n" +
            gmNarratorContract + "\n" +
            caoMinhCard + "\n" +
            "NGÔN NGỮ HIỂN THỊ: Toàn bộ reply, sceneLabel, choices và encounterDialogue phải viết bằng tiếng Việt tự nhiên. Mọi thuật ngữ mô tả từ knowledge/context phải được dịch sang tiếng Việt; tuyệt đối không giữ nguyên tiếng Anh nội bộ trừ tên riêng chính thức (Cao Minh, Backrooms, Level, Entity, Item, Skill).\n" +
            gmStyleExamples + "\n" +
            "QUYỀN SỞ HỮU CỦA CORE (Core-Owned Boundaries):\n" +
            "- Java Core hoàn toàn sở hữu Level, route, Entity spawn, Loot, Inventory, Party và Combat.\n" +
            "- AI chỉ tập trung sáng tác lời kể (reply), tên/mô tả bối cảnh ngắn (sceneLabel), gợi ý hành động (choices) và thoại gặp mặt (encounterDialogue).\n\n" +
            "EXPLORER CHOICES: 0 đến 3 gợi ý hành động ngắn trong choices (mỗi Lựa chọn là {\"text\":\"...\"}). Nếu Entity đang đối đầu trực tiếp, đặt choices là [].\n" +
            "ENCOUNTER DIALOGUE: Nếu Character Core báo pending intro, reply dựng khoảnh khắc gặp mặt; encounterDialogue chứa đúng 2-5 câu thoại thực sự. Nếu không pending thì encounterDialogue là [].\n\n" +
            levelContext + "\n" + entityContext + "\n" + itemContext + "\n" + characterContext + "\n" +
            "RECENT STORY CONTEXT (giữ tính liên tục, không lặp lại nguyên văn):\n" + recentStory + "\n\n" +
            "State hiện tại: " + promptState.toString() + "\nHành động người chơi: " + action +
            "\nTrả DUY NHẤT JSON hợp lệ (không markdown):\n" +
            "{\"reply\":\"phản hồi Game Master\",\"sceneLabel\":\"vị trí/bối cảnh ngắn\",\"choices\":[{\"text\":\"Gợi ý 1\"}],\"encounterDialogue\":[]}";
          long tCtxEnd = System.currentTimeMillis();

          long tGenStart = System.currentTimeMillis();
          String providerUsed = "Haiku";
          boolean fallbackOccurred = false;
          String rawOutput = "";
          try {
            rawOutput = haikuText(prompt);
          } catch (Exception haikuError) {
            fallbackOccurred = true;
            providerUsed = "Gemini";
            Log.w(TAG, "Haiku primary failed (" + providerErrorSummary(haikuError) + "); falling back to Gemini.");
            rawOutput = geminiText(prompt);
          }
          long tGenEnd = System.currentTimeMillis();

          long tParseStart = System.currentTimeMillis();
          JSONObject generated = parseModelJson(rawOutput);
          String reply = generated.optString("reply", "").trim();
          if (reply.isEmpty()) throw new Exception("AI trả về phản hồi rỗng, lượt này không được ghi.");
          JSONArray encounterDialogue = generated.optJSONArray("encounterDialogue");
          if (encounterDialogue == null) encounterDialogue = new JSONArray();

          state.put("turn", state.optInt("turn", 1) + 1).put("mode", "ai");
          String sceneLabel = generated.optString("sceneLabel", generated.optString("location", "")).trim();
          if (!sceneLabel.isEmpty()) state.put("location", sceneLabel);
          JSONObject generatedFlags = generated.optJSONObject("flags");
          if (generatedFlags != null) {
            JSONObject flags = state.optJSONObject("flags");
            if (flags == null) flags = new JSONObject();
            mergeObject(flags, generatedFlags);
            state.put("flags", flags);
          }
          long tParseEnd = System.currentTimeMillis();

          long tValStart = System.currentTimeMillis();
          JSONObject coreCommit = new JSONObject(gameCore.processValidatedCandidate(
              coreBeforeJson, state.toString(), action, encounterDialogue.toString()));
          if (!coreCommit.optBoolean("handled", false)) {
            throw new Exception("Game State Core từ chối AI delta: " + coreCommit.optString("error", "invalid_delta"));
          }
          state = coreCommit.getJSONObject("state");
          reply = appendEncounterDialogue(reply, encounterDialogue);

          JSONArray log = state.optJSONArray("log");
          if (log == null) log = new JSONArray();
          log.put(new JSONObject().put("role", "player").put("text", action));
          JSONObject gmEntry = GmChoiceContract.gmEntry(reply, generated, state);
          log.put(gmEntry);
          state.put("log", log);

          String newEncounter = encounterKey(state);
          if (CombatChoiceEngine.isKnownEntity(newEncounter)) {
            gmEntry.remove("choices");
            CombatChoiceEngine.start(state, newEncounter, log.length() - 1);
          }
          long tValEnd = System.currentTimeMillis();

          if (BuildConfig.DEBUG) {
            long totalMs = tValEnd - tStart;
            long preMs = tPreEnd - tPreStart;
            long ctxMs = tCtxEnd - tCtxStart;
            long providerMs = tGenEnd - tGenStart;
            long parseMs = tParseEnd - tParseStart;
            long valMs = tValEnd - tValStart;
            int promptChars = prompt.length();
            Log.d(TAG, String.format(
                "EXPLORER TURN TELEMETRY: total=%dms (corePre=%dms, ctx=%dms, provider=%s %dms, parse=%dms, coreVal=%dms) promptChars=%d fallback=%b",
                totalMs, preMs, ctxMs, providerUsed, providerMs, parseMs, valMs, promptChars, fallbackOccurred));
          }

          emit("backroomTurn", state.toString());
        } catch (Exception e) {
          String message = e.getMessage() == null ? "Không thể xử lý lượt." : e.getMessage();
          if (committedBeforeGemini != null) {
            try {
              JSONObject payload = new JSONObject().put("state", committedBeforeGemini).put("message", message);
              emit("backroomCommittedError", payload.toString());
            } catch (Exception ignored) {
              emit("backroomError", message);
            }
          } else {
            emit("backroomError", message);
          }
        }
      });
    }

    @JavascriptInterface public void combatRoll(String stateJson) {
      io.execute(() -> {
        try {
          JSONObject submitted = new JSONObject(stateJson);
          CombatChoiceEngine.roll(submitted);
          JSONObject committed = new JSONObject(gameCore.normalizeState(submitted.toString()));
          emit("backroomCombatDiceState", committed.toString());
        } catch (Exception e) {
          emit("backroomError", e.getMessage() == null ? "Không thể ROLL." : e.getMessage());
        }
      });
    }

    @JavascriptInterface public void combatHold(String stateJson, int dieIndex, boolean held) {
      io.execute(() -> {
        try {
          JSONObject submitted = new JSONObject(stateJson);
          CombatChoiceEngine.setHold(submitted, dieIndex, held);
          JSONObject committed = new JSONObject(gameCore.normalizeState(submitted.toString()));
          emit("backroomCombatDiceState", committed.toString());
        } catch (Exception e) {
          emit("backroomError", e.getMessage() == null ? "Không thể HOLD die." : e.getMessage());
        }
      });
    }

    @JavascriptInterface public void combatFinish(String stateJson) {
      io.execute(() -> {
        try {
          JSONObject submitted = new JSONObject(stateJson);
          CombatChoiceEngine.finishHand(submitted);
          JSONObject committed = new JSONObject(gameCore.normalizeState(submitted.toString()));
          emit("backroomCombatDiceState", committed.toString());
        } catch (Exception e) {
          emit("backroomError", e.getMessage() == null ? "Không thể FINISH hand." : e.getMessage());
        }
      });
    }

    @JavascriptInterface public void combatResolve(String stateJson) {
      io.execute(() -> {
        try {
          JSONObject submitted = new JSONObject(stateJson);
          JSONObject resolved = CombatChoiceEngine.resolveFinalized(submitted);
          resolved = new JSONObject(gameCore.normalizeState(resolved.toString()));
          emit("backroomCombatTurn", resolved.toString());
        } catch (Exception e) {
          emit("backroomError", e.getMessage() == null ? "Không thể resolve combat hand." : e.getMessage());
        }
      });
    }

    @JavascriptInterface public void coreUpgrade(String stateJson, String characterId, String stat) {
      io.execute(() -> emit("backroomCoreUpgrade",
          gameCore.processCoreUpgrade(stateJson, characterId, stat)));
    }

    @JavascriptInterface public void itemAction(String stateJson, String ownerId, String itemId,
                                                String operation, String targetId, int quantity) {
      io.execute(() -> {
        try {
          JSONObject submitted = new JSONObject(stateJson);
          if (CombatChoiceEngine.isActive(submitted)) {
            JSONObject rejected = new JSONObject()
              .put("handled", false)
              .put("state", submitted)
              .put("reason", "combat_locked")
              .put("error", "Battle đang hoạt động. Hãy hoàn tất Poker Dice trước.");
            emit("backroomItemAction", rejected.toString());
            return;
          }
          emit("backroomItemAction",
              gameCore.processItemAction(stateJson, ownerId, itemId, operation, targetId, quantity));
        } catch (Exception e) {
          JSONObject rejected = new JSONObject();
          try {
            rejected.put("handled", false).put("state", new JSONObject(stateJson));
            rejected.put("error", e.getMessage() == null ? "Không thể xử lý vật phẩm." : e.getMessage());
          } catch (Exception ignored) {}
          emit("backroomItemAction", rejected.toString());
        }
      });
    }

    @JavascriptInterface public String levelSnapshot(String stateJson) {
      return gameCore.levelSnapshotDescriptor(stateJson);
    }

    @JavascriptInterface public String normalizeState(String stateJson) {
      return gameCore.normalizeState(stateJson);
    }
  }

  private static class HttpError extends Exception {
    final int status;
    HttpError(int status, String message) { super(message); this.status = status; }
  }
}

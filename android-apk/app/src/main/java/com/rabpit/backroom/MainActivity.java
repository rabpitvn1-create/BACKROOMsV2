package com.rabpit.backroom;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import com.rabpit.backroom.core.CombatChoiceEngine;
import com.rabpit.backroom.core.GameCoreFacade;
import com.rabpit.backroom.core.GmChoiceContract;
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
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends Activity {
  private static final String TAG = "BackroomMain";
  private WebView webView;
  private final ExecutorService io = Executors.newSingleThreadExecutor();
  private final ExecutorService imageIo = Executors.newSingleThreadExecutor();
  private final AtomicInteger latestSnapshotTurn = new AtomicInteger(0);
  private GameCoreFacade gameCore;
  private static final String GEMINI_MODEL = "gemini-3.6-flash";
  private static final String GEMINI_IMAGE_MODEL = "gemini-3.1-flash-image";
  private static final int[] RETRYABLE = {408, 429, 500, 502, 503, 504};
  private static final int MAX_SNAPSHOT_BASE64 = 1_500_000;

  @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
  @Override public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
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
    webView.loadUrl("file:///android_asset/index.html");
  }

  @Override protected void onDestroy() {
    if (gameCore != null) gameCore.close();
    io.shutdownNow();
    imageIo.shutdownNow();
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

  private void installUiScripts() {
    try {
      String snapshotUi = readAssetText("snapshot-ui.js");
      String gmChoiceUi = readAssetText("gm-choice-ui.js");
      webView.evaluateJavascript(snapshotUi, ignored -> webView.evaluateJavascript(gmChoiceUi, null));
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
      BuildConfig.GEMINI_API_KEY_3
    };
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

  private String geminiText(String prompt) throws Exception {
    Exception last = null;
    for (String key : geminiKeys()) {
      if (key == null || key.isEmpty()) continue;
      for (int attempt = 0; attempt < 2; attempt++) {
        try {
          JSONObject part = new JSONObject().put("text", prompt);
          JSONObject contents = new JSONObject().put("role", "user").put("parts", new JSONArray().put(part));
          JSONObject config = new JSONObject().put("responseMimeType", "application/json").put("temperature", 0.8);
          JSONObject body = new JSONObject().put("contents", new JSONArray().put(contents)).put("generationConfig", config);
          JSONObject result = new JSONObject(postJson("https://generativelanguage.googleapis.com/v1beta/models/" + GEMINI_MODEL + ":generateContent", key, "x-goog-api-key", body));
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
          return text.toString();
        } catch (Exception e) {
          last = e;
          int code = e instanceof HttpError ? ((HttpError)e).status : 0;
          if (attempt == 0 && (code == 0 || retryable(code))) {
            try { Thread.sleep(350); } catch (InterruptedException ignored) {}
            continue;
          }
          break;
        }
      }
    }
    throw last != null ? last : new Exception("Không có Gemini API key trong APK.");
  }

  private String generateText(String prompt) throws Exception {
    return geminiText(prompt);
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

  private SnapshotImage findSnapshotImage(JSONObject result) {
    JSONArray steps = result.optJSONArray("steps");
    if (steps == null) return null;
    for (int i = steps.length() - 1; i >= 0; i--) {
      JSONObject step = steps.optJSONObject(i);
      if (step == null || !"model_output".equals(step.optString("type"))) continue;
      JSONArray content = step.optJSONArray("content");
      if (content == null) continue;
      for (int j = content.length() - 1; j >= 0; j--) {
        JSONObject part = content.optJSONObject(j);
        if (part == null || !"image".equals(part.optString("type"))) continue;
        String data = part.optString("data", "");
        if (data.isEmpty()) continue;
        String mimeType = part.optString("mime_type", "image/jpeg");
        return new SnapshotImage(data, mimeType);
      }
    }
    return null;
  }

  private SnapshotImage geminiImage(String prompt) throws Exception {
    Exception last = null;
    for (String key : geminiKeys()) {
      if (key == null || key.isEmpty()) continue;
      for (int attempt = 0; attempt < 2; attempt++) {
        try {
          JSONObject input = new JSONObject().put("type", "text").put("text", prompt);
          JSONObject format = new JSONObject()
            .put("type", "image")
            .put("mime_type", "image/jpeg")
            .put("aspect_ratio", "16:9")
            .put("image_size", "512");
          JSONObject body = new JSONObject()
            .put("model", GEMINI_IMAGE_MODEL)
            .put("input", new JSONArray().put(input))
            .put("response_format", format);
          JSONObject result = new JSONObject(postJson("https://generativelanguage.googleapis.com/v1beta/interactions", key, "x-goog-api-key", body));
          SnapshotImage image = findSnapshotImage(result);
          if (image == null || image.data.isEmpty()) throw new Exception("Gemini image không trả ảnh.");
          if (image.data.length() > MAX_SNAPSHOT_BASE64) throw new Exception("Snapshot quá lớn để hiển thị trong APK.");
          return image;
        } catch (Exception e) {
          last = e;
          int code = e instanceof HttpError ? ((HttpError)e).status : 0;
          if (attempt == 0 && (code == 0 || retryable(code))) {
            try { Thread.sleep(400); } catch (InterruptedException ignored) {}
            continue;
          }
          break;
        }
      }
    }
    throw last != null ? last : new Exception("Không có Gemini API key để tạo snapshot.");
  }

  private String clipped(Object value, int max) {
    String text = value == null ? "" : String.valueOf(value);
    return text.length() > max ? text.substring(text.length() - max) : text;
  }

  private String snapshotPrompt(JSONObject state) {
    StringBuilder recent = new StringBuilder();
    JSONArray log = state.optJSONArray("log");
    if (log != null) {
      int start = Math.max(0, log.length() - 4);
      for (int i = start; i < log.length(); i++) {
        JSONObject entry = log.optJSONObject(i);
        if (entry == null) continue;
        if (recent.length() > 0) recent.append("\n\n");
        recent.append("player".equals(entry.optString("role")) ? "PLAYER: " : "GM: ");
        recent.append(clipped(entry.optString("text", ""), 1800));
        JSONArray battleLog = entry.optJSONArray("battleLog");
        if (battleLog != null && battleLog.length() > 0) {
          recent.append("\nCOMBAT: ");
          int from = Math.max(0, battleLog.length() - 4);
          for (int j = from; j < battleLog.length(); j++) {
            JSONObject line = battleLog.optJSONObject(j);
            if (line != null) recent.append(clipped(line.optString("text", ""), 400)).append(' ');
          }
        }
      }
    }

    return "Create one cinematic 16:9 visual snapshot of the CURRENT END STATE of this Backrooms text game.\n" +
      "Show the present scene only, not a montage. Kai Akechi / Twilight is the main character. " +
      "Do not invent NPCs, monsters, exits, loot, injuries, weapons, text, HUD, blood or props that are not explicitly present in the state. " +
      "If party is empty, Kai is alone. Level 0 uses stale yellow wallpaper, damp carpet, fluorescent ceiling panels and oppressive empty office-like geometry. " +
      "Photorealistic cinematic game concept art, grounded anatomy and materials, no written text in the image.\n\n" +
      "Turn: " + state.optInt("turn", 1) + "\n" +
      "Location: " + clipped(state.optString("location", ""), 1200) + "\n" +
      "Player: " + clipped(state.optJSONObject("player"), 1800) + "\n" +
      "Party: " + clipped(state.optJSONArray("party"), 1600) + "\n" +
      "Inventory: " + clipped(state.optJSONArray("inventory"), 2200) + "\n" +
      "Relevant flags: " + clipped(state.optJSONObject("flags"), 2200) + "\n\n" +
      "Recent context, final lines take priority:\n" + recent;
  }

  private void requestSnapshotInternal(String stateJson) {
    try {
      JSONObject snapshotState = new JSONObject(stateJson);
      int turn = snapshotState.optInt("turn", 1);
      latestSnapshotTurn.updateAndGet(current -> Math.max(current, turn));
      SnapshotImage image = geminiImage(snapshotPrompt(snapshotState));
      if (turn != latestSnapshotTurn.get()) return;
      JSONObject payload = new JSONObject()
        .put("turn", turn)
        .put("model", GEMINI_IMAGE_MODEL)
        .put("dataUri", "data:" + image.mimeType + ";base64," + image.data);
      emit("backroomSnapshot", payload.toString());
    } catch (Exception e) {
      try {
        JSONObject state = new JSONObject(stateJson);
        int turn = state.optInt("turn", 1);
        if (turn != latestSnapshotTurn.get()) return;
        JSONObject payload = new JSONObject()
          .put("turn", turn)
          .put("message", e.getMessage() == null ? "Không thể tạo snapshot." : e.getMessage());
        emit("backroomSnapshotError", payload.toString());
      } catch (Exception ignored) {
        emit("backroomSnapshotError", "{\"turn\":0,\"message\":\"Không thể tạo snapshot.\"}");
      }
    }
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
        try {
          JSONObject submitted = new JSONObject(stateJson);

          if (CombatChoiceEngine.isActive(submitted)) {
            if (!CombatChoiceEngine.isCombatAction(action)) {
              throw new Exception("Đang chiến đấu. Hãy chọn A, B hoặc C trong khung GAME MASTER.");
            }
            JSONObject resolved = CombatChoiceEngine.resolve(submitted, action);
            emit("backroomCombatTurn", resolved.toString());
            return;
          }

          String existingEncounter = encounterKey(submitted);
          if (CombatChoiceEngine.isKnownEntity(existingEncounter)) {
            CombatChoiceEngine.start(submitted, existingEncounter, lastGmLogIndex(submitted));
            emit("backroomCombatTurn", submitted.toString());
            return;
          }

          if (CombatChoiceEngine.isCombatAction(action)) {
            throw new Exception("Không có trận chiến đang hoạt động.");
          }

          JSONObject localResult = new JSONObject(gameCore.processRule(stateJson, action));
          if (localResult.optBoolean("handled", false)) {
            emit("backroomTurn", localResult.getJSONObject("state").toString());
            return;
          }

          JSONObject state = submitted;
          String prompt = "Bạn là Game Master của text game Backrooms. Xử lý đúng một Explorer Turn và trả DUY NHẤT JSON hợp lệ, không markdown. " +
            "Viết tiếng Việt tự nhiên, đầy đủ ý. Không trả lời bằng câu rỗng. Không thay đổi dữ kiện chưa có căn cứ. Người chơi chỉ điều khiển Kai Akechi. " +
            "EXPLORER CHOICES: trả 0 đến 3 gợi ý hành động ngắn trong choices. Đây chỉ là gợi ý, không phải nhánh kịch bản; người chơi vẫn có thể nhập hành động tự do. Không cố tạo đủ 3 nếu tình huống không cần. Mỗi lựa chọn phải khác nhau có ý nghĩa. " +
            "Nếu một Entity đang trực tiếp hiện diện/đối đầu và flags.entityEncounterKey khác rỗng thì choices phải là [] vì engine sẽ chuyển sang Battle A/B/C. " +
            "SEMANTIC HIGHLIGHTS: highlights chỉ chứa các chuỗi CHÍNH XÁC xuất hiện trong reply cần dùng Play Bold + gạch chân, như tên nhân vật, Entity, Level/khu vực, vật phẩm, kỹ năng, hiệu ứng. Không đưa từ nối hay cả câu vào highlights. Mỗi choice có thể có highlights riêng cho tên thực thể trong chính lựa chọn đó. " +
            "ENTITY OVERLAY STATE CONTRACT: flags.entityEncounterKey bắt buộc có ở mọi lượt AI. Nếu một Entity đang trực tiếp hiện diện hoặc đối đầu, đặt canonical key local tương ứng; nếu không còn Entity trực tiếp hiện diện thì đặt chuỗi rỗng. Canonical keys: hound, clump, duller, deathmoth, hostile_faceling, false_puddle, paintings, smiler, skin-stealer, predatory_window, biological_pipeline, wretch, cable_mimic, the_beast_of_level_5, hotel_corpse_lure, jeff_the_killer, jane_the_killer, slenderman, diep_minh. Không dùng đường dẫn, URL hoặc alias khác. " +
            "State hiện tại: " + state.toString() + "\nHành động: " + action +
            "\nJSON bắt buộc: {\"reply\":\"phản hồi Game Master\",\"title\":\"giữ nguyên hoặc cập nhật\",\"location\":\"vị trí sau lượt\",\"player\":{},\"party\":[],\"inventory\":[],\"flags\":{\"entityEncounterKey\":\"\"},\"highlights\":[\"Kai Akechi\",\"Level 0\"],\"choices\":[{\"text\":\"Kiểm tra hành lang phía trước\",\"highlights\":[\"hành lang phía trước\"]}]}";
          JSONObject generated = parseModelJson(generateText(prompt));
          String reply = generated.optString("reply", "").trim();
          if (reply.isEmpty()) throw new Exception("AI trả về phản hồi rỗng, lượt này không được ghi.");

          state.put("turn", state.optInt("turn", 1) + 1).put("mode", "ai");
          String title = generated.optString("title", "").trim();
          String location = generated.optString("location", "").trim();
          if (!title.isEmpty()) state.put("title", title);
          if (!location.isEmpty()) state.put("location", location);
          JSONObject generatedFlags = generated.optJSONObject("flags");
          if (generatedFlags != null) {
            JSONObject flags = state.optJSONObject("flags");
            if (flags == null) flags = new JSONObject();
            mergeObject(flags, generatedFlags);
            if (!generatedFlags.has("entityEncounterKey")) flags.put("entityEncounterKey", "");
            state.put("flags", flags);
          }

          JSONObject coreCommit = new JSONObject(gameCore.processValidatedCandidate(stateJson, state.toString(), action));
          if (!coreCommit.optBoolean("handled", false)) {
            throw new Exception("Game State Core từ chối Gemini delta: " + coreCommit.optString("error", "invalid_delta"));
          }
          state = coreCommit.getJSONObject("state");

          JSONArray log = state.optJSONArray("log");
          if (log == null) log = new JSONArray();
          log.put(new JSONObject().put("role", "player").put("text", action));
          JSONObject gmEntry = GmChoiceContract.gmEntry(reply, generated);
          log.put(gmEntry);
          state.put("log", log);

          String newEncounter = encounterKey(state);
          if (CombatChoiceEngine.isKnownEntity(newEncounter)) {
            gmEntry.remove("choices");
            CombatChoiceEngine.start(state, newEncounter, log.length() - 1);
          }

          emit("backroomTurn", state.toString());
        } catch (Exception e) {
          emit("backroomError", e.getMessage() == null ? "Không thể xử lý lượt." : e.getMessage());
        }
      });
    }

    @JavascriptInterface public void requestSnapshot(String stateJson) {
      imageIo.execute(() -> requestSnapshotInternal(stateJson));
    }
  }

  private static class SnapshotImage {
    final String data;
    final String mimeType;
    SnapshotImage(String data, String mimeType) {
      this.data = data;
      this.mimeType = mimeType == null || mimeType.isEmpty() ? "image/jpeg" : mimeType;
    }
  }

  private static class HttpError extends Exception {
    final int status;
    HttpError(int status, String message) { super(message); this.status = status; }
  }
}

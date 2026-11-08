// window.Android bridge shim for the static web build — the exact native
// contract of MainActivity.GameBridge, with AI served by the owner's Gemini
// proxy (config.js) instead of on-device keys:
//
//   Android.submitTurn(stateJson, action)  -> window.backroomTurn(jsonString)
//                                          |  window.backroomError(message)
//   Android.requestSnapshot(stateJson)     -> window.backroomSnapshot(jsonString)
//                                          |  window.backroomSnapshotError(jsonString)
//
// Load order: config.js, core-adapter.js (C1 core), then this file — and all
// of them ahead of the game's own inline script (native hard-stops without
// window.Android). Turns are serialized on a promise chain, mirroring the
// native single-thread executor.
(function (root) {
  "use strict";

  var CONFIG = root.WEB_CONFIG || {
    GEMINI_PROXY: "https://backrooms-gemini-proxy.onrender.com",
    PREMIUM_PROXY: "https://backrooms-premium-proxy.onrender.com"
  };
  var GEMINI_MODEL = "gemini-3.6-flash";
  var TURN_TIMEOUT_MS = 180000; // 55 s proxy upstream + worst-case Render wake + slack
  var EMPTY_REPLY_MESSAGE = "AI trả về phản hồi rỗng, lượt này không được ghi.";
  var CONNECTION_ERROR_MESSAGE = "Không kết nối được proxy Gemini. Kiểm tra mạng rồi thử lại.";
  var PROCESS_ERROR_MESSAGE = "Không thể xử lý lượt."; // native catch-all fallback
  var SNAPSHOT_UNAVAILABLE_MESSAGE = "Snapshot chưa khả dụng trên bản web.";

  function deepCopy(value) { return JSON.parse(JSON.stringify(value)); }

  // Native GameCoreFacade.parseState tolerance: unusable input becomes {}.
  function tolerantParse(json) {
    try {
      var v = JSON.parse(json);
      return v && typeof v === "object" ? v : {};
    } catch (e) { return {}; }
  }

  function emitTurn(state) {
    if (typeof root.backroomTurn === "function") root.backroomTurn(JSON.stringify(state));
  }
  function emitError(message) {
    if (typeof root.backroomError === "function") root.backroomError(message);
  }
  function emitSnapshot(payload) {
    if (typeof root.backroomSnapshot === "function") root.backroomSnapshot(JSON.stringify(payload));
  }
  function emitSnapshotError(payload) {
    if (typeof root.backroomSnapshotError === "function") root.backroomSnapshotError(JSON.stringify(payload));
  }

  function core() {
    if (!root.CoreAdapter) throw new Error("Game State Core chưa sẵn sàng trên bản web.");
    return root.CoreAdapter;
  }

  // Prompt assembly lifted verbatim from MainActivity.submitTurn — fixed
  // Vietnamese instruction + the entire state JSON, uncut + the action +
  // the mandatory output schema. Do not reword; the GM contract depends on it.
  function buildPrompt(state, action) {
    return "Bạn là Game Master của text game Backrooms. Xử lý đúng một lượt và trả DUY NHẤT JSON hợp lệ, không markdown. " +
      "Viết tiếng Việt tự nhiên, đầy đủ ý. Không trả lời bằng câu rỗng. Không thay đổi dữ kiện chưa có căn cứ. Người chơi chỉ điều khiển Cao Minh. " +
      "State hiện tại: " + JSON.stringify(state) + "\nHành động: " + action +
      "\nJSON bắt buộc: {\"reply\":\"phản hồi Game Master\",\"title\":\"giữ nguyên hoặc cập nhật\",\"location\":\"vị trí sau lượt\",\"player\":{},\"party\":[],\"inventory\":[],\"flags\":{}}";
  }

  // Native geminiText reassembly: every candidate part's text, trimmed,
  // non-empty pieces joined with "\n". Empty total is an error, as native.
  function extractResponseText(result) {
    var text = "";
    var candidates = (result && result.candidates) || [];
    for (var c = 0; c < candidates.length; c++) {
      var parts = (candidates[c] && candidates[c].content && candidates[c].content.parts) || [];
      for (var p = 0; p < parts.length; p++) {
        var piece = parts[p] && typeof parts[p].text === "string" ? parts[p].text.trim() : "";
        if (piece) { if (text) text += "\n"; text += piece; }
      }
    }
    if (!text) throw new Error("Gemini không trả nội dung.");
    return text;
  }

  // Native parseModelJson port: strip a leading ``` fence, take first {..last }.
  function parseModelJson(raw) {
    if (raw == null) throw new Error("AI không trả dữ liệu.");
    var text = String(raw).trim();
    if (text.indexOf("```") === 0) {
      var firstNewline = text.indexOf("\n");
      if (firstNewline >= 0) text = text.substring(firstNewline + 1);
      var fence = text.lastIndexOf("```");
      if (fence >= 0) text = text.substring(0, fence);
      text = text.trim();
    }
    var start = text.indexOf("{"), end = text.lastIndexOf("}");
    if (start < 0 || end <= start) throw new Error("AI trả JSON không hợp lệ.");
    try { return JSON.parse(text.substring(start, end + 1)); }
    catch (e) { throw new Error("AI trả JSON không hợp lệ."); }
  }

  function proxyErrorText(raw, status) {
    try {
      var j = JSON.parse(raw);
      if (j && typeof j.error === "string" && j.error) return j.error;
    } catch (e) { /* fall through to the native-style message */ }
    return "Provider HTTP " + status + (raw ? ": " + raw.substring(0, 220) : "");
  }

  function fetchOnce(url, body) {
    var controller = new AbortController();
    var timer = setTimeout(function () { controller.abort(); }, TURN_TIMEOUT_MS);
    return fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
      signal: controller.signal
    }).finally(function () { clearTimeout(timer); });
  }

  // Exactly one retry, and only when fetch itself rejects (network failure /
  // proxy wake abort). An HTTP response — even 502, which already means the
  // proxy exhausted its keys — is never retried.
  function fetchWithOneRetry(url, body) {
    return fetchOnce(url, body).catch(function () { return fetchOnce(url, body); });
  }

  function postGemini(prompt) {
    var body = {
      model: GEMINI_MODEL,
      contents: [{ role: "user", parts: [{ text: prompt }] }],
      generationConfig: { responseMimeType: "application/json", temperature: 0.8 }
    };
    return fetchWithOneRetry(CONFIG.GEMINI_PROXY + "/gemini", body).then(
      function (res) {
        return res.text().then(function (raw) {
          if (!res.ok) throw new Error(proxyErrorText(raw, res.status));
          try { return JSON.parse(raw); }
          catch (e) { throw new Error("Gemini không trả nội dung."); }
        });
      },
      function () { throw new Error(CONNECTION_ERROR_MESSAGE); }
    );
  }

  function runTurn(stateJson, action) {
    var before = tolerantParse(stateJson);
    return Promise.resolve()
      .then(function () { return core().processRule(deepCopy(before), action); })
      .then(function (local) {
        if (local && local.handled) { emitTurn(local.state); return null; }
        var state = deepCopy(before);
        return postGemini(buildPrompt(state, action)).then(function (result) {
          var generated = parseModelJson(extractResponseText(result));
          var reply = typeof generated.reply === "string" ? generated.reply.trim() : "";
          if (!reply) throw new Error(EMPTY_REPLY_MESSAGE);
          // Native mutation order: bump turn / mode / title / location on the
          // candidate BEFORE the core validates and commits it.
          state.turn = (typeof state.turn === "number" ? state.turn : 1) + 1;
          state.mode = "ai";
          var title = typeof generated.title === "string" ? generated.title.trim() : "";
          var location = typeof generated.location === "string" ? generated.location.trim() : "";
          if (title) state.title = title;
          if (location) state.location = location;
          var commit = core().validateCandidate(deepCopy(before), state, action);
          if (!commit || !commit.handled) {
            throw new Error("Game State Core từ chối Gemini delta: " + (commit && commit.error ? commit.error : "invalid_delta"));
          }
          var committed = commit.state;
          if (!Array.isArray(committed.log)) committed.log = [];
          committed.log.push({ role: "player", text: action });
          committed.log.push({ role: "gm", text: reply });
          emitTurn(committed);
          return null;
        });
      })
      .catch(function (e) {
        emitError(e && e.message ? e.message : PROCESS_ERROR_MESSAGE);
      });
  }

  var turnChain = Promise.resolve();
  function submitTurn(stateJson, action) {
    turnChain = turnChain.then(function () { return runTurn(stateJson, action); });
  }

  // === SNAPSHOT IMAGE SLOT =====================================================
  // The real image call belongs in this one function: build the native
  // snapshot prompt (MainActivity.snapshotPrompt, with its clip limits), call
  // the image lane, mine the image part, apply the 1,500,000-char base64 cap,
  // and return {model, dataUri}.
  // SHIPPED BEHAVIOUR (owner ruling): the degradation floor — the proxy does
  // not forward image generation and the pool's image quota is exhausted, so
  // this slot makes NO call and throws the unavailable error; requestSnapshot
  // reports it in the exact native failure shape. Replace only this function
  // body if an image lane ever opens.
  // ============================================================================
  function snapshotImageCall(state) { // eslint-disable-line no-unused-vars
    throw new Error(SNAPSHOT_UNAVAILABLE_MESSAGE);
  }

  var snapshotChain = Promise.resolve();
  function requestSnapshot(stateJson) {
    snapshotChain = snapshotChain.then(function () {
      var state = tolerantParse(stateJson);
      var turn = typeof state.turn === "number" ? state.turn : 1;
      try {
        var image = snapshotImageCall(state);
        emitSnapshot({ turn: turn, model: image.model, dataUri: image.dataUri });
      } catch (e) {
        emitSnapshotError({ turn: turn, message: e && e.message ? e.message : "Không thể tạo snapshot." });
      }
    }).catch(function () { /* snapshot failure never escapes, as native */ });
  }

  root.Android = { submitTurn: submitTurn, requestSnapshot: requestSnapshot };

  // Warm-up: wake both Render proxies at page load so the cold start is paid
  // while the player reads the prologue, not on the first turn. Fire-and-forget;
  // any outcome (even an error response) proves the instance is awake.
  function warmUp(base) {
    try {
      var p = fetch(base + "/health", { method: "GET" });
      if (p && typeof p.catch === "function") p.catch(function () {});
    } catch (e) { /* warm-up is best-effort only */ }
  }
  warmUp(CONFIG.GEMINI_PROXY);
  warmUp(CONFIG.PREMIUM_PROXY);

  // Test seam (Node only; inert in the browser).
  if (typeof module !== "undefined" && module.exports) {
    module.exports = {
      buildPrompt: buildPrompt,
      parseModelJson: parseModelJson,
      extractResponseText: extractResponseText,
      EMPTY_REPLY_MESSAGE: EMPTY_REPLY_MESSAGE,
      CONNECTION_ERROR_MESSAGE: CONNECTION_ERROR_MESSAGE,
      SNAPSHOT_UNAVAILABLE_MESSAGE: SNAPSHOT_UNAVAILABLE_MESSAGE
    };
  }
})(typeof window !== "undefined" ? window : globalThis);

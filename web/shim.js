// window.Android bridge shim for the static web build — the exact native
// contract of MainActivity.GameBridge, fully offline (the VN build has no
// remote lane at all: no keys, no endpoints, no network call of any kind):
//
//   Android.submitTurn(stateJson, action)  -> window.backroomTurn(jsonString)
//                                          |  window.backroomError(message)
//   Android.requestSnapshot(stateJson)     -> no-op: the VN stage above the
//     story panel IS the snapshot (level background + figures, all local
//     assets); the UI payload's own window.requestSnapshot shows it.
//
// Load order: core-adapter.js (C1 core), then this file — and both ahead of
// the game's own inline script (native hard-stops without window.Android).
// Turns are serialized on a promise chain, mirroring the native
// single-thread executor: submitTurn -> gameCore.processRule ->
// backroomTurn(state) when handled, else backroomError with the core's
// message or the native catch-all below.
(function (root) {
  "use strict";

  var PROCESS_ERROR_MESSAGE = "Không thể xử lý lượt."; // native catch-all fallback

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

  function core() {
    if (!root.CoreAdapter) throw new Error("Game State Core chưa sẵn sàng trên bản web.");
    return root.CoreAdapter;
  }

  function runTurn(stateJson, action) {
    var before = tolerantParse(stateJson);
    return Promise.resolve()
      .then(function () { return core().processRule(deepCopy(before), action); })
      .then(function (local) {
        if (local && local.handled) { emitTurn(local.state); return null; }
        // Native bridge: an unhandled result surfaces the core's error,
        // or the catch-all when the core gave none.
        throw new Error(local && local.error ? local.error : PROCESS_ERROR_MESSAGE);
      })
      .catch(function (e) {
        emitError(e && e.message ? e.message : PROCESS_ERROR_MESSAGE);
      });
  }

  var turnChain = Promise.resolve();
  function submitTurn(stateJson, action) {
    turnChain = turnChain.then(function () { return runTurn(stateJson, action); });
  }

  // Nothing is generated and nothing is fetched: the composed VN stage is
  // already on screen. Kept only so the native bridge shape stays complete.
  function requestSnapshot() {}

  root.Android = { submitTurn: submitTurn, requestSnapshot: requestSnapshot };

  // Test seam (Node only; inert in the browser).
  if (typeof module !== "undefined" && module.exports) {
    module.exports = { PROCESS_ERROR_MESSAGE: PROCESS_ERROR_MESSAGE };
  }
})(typeof window !== "undefined" ? window : globalThis);

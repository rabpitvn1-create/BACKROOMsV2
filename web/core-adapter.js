// Adapter between the bridge shim (shim.js) and the C1 JS core port.
//
// INTEGRATION CONTRACT — C1 exposes its GameCoreFacade port as a global:
//   processRule(state, action)   -> {handled, state?, error?}
// where `state` is the committed full state when handled, mirroring the
// native facade envelope (GameCoreFacade.response: handled/state/error).
// The offline core handles every non-blank action locally, so this is the
// only core entry point the bridge needs.
//
// SWAP POINT (the only line integration should ever need to touch):
// the PROVIDER_NAMES list below. Return values may be envelope objects or
// JSON strings in the native shape.
(function (root) {
  "use strict";

  var PROVIDER_NAMES = ["GameCore", "BackroomsCore", "Core"]; // SWAP: provider global name(s)
  var NOT_READY = "Game State Core chưa sẵn sàng trên bản web.";

  function provider() {
    for (var i = 0; i < PROVIDER_NAMES.length; i++) {
      var p = root[PROVIDER_NAMES[i]];
      if (p && typeof p.processRule === "function") return p;
    }
    return null;
  }

  function envelope(result) {
    if (typeof result === "string") {
      try { return JSON.parse(result); } catch (e) { return null; }
    }
    return result;
  }

  function processRule(state, action) {
    var p = provider();
    if (!p) throw new Error(NOT_READY);
    var r = envelope(p.processRule(state, action));
    return { handled: !!(r && r.handled), state: r ? r.state : undefined, error: r ? r.error : undefined };
  }

  root.CoreAdapter = { processRule: processRule };
})(typeof window !== "undefined" ? window : globalThis);

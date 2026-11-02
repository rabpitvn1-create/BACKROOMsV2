// Adapter between the bridge shim (shim.js) and the C1 JS core port.
//
// INTEGRATION CONTRACT — C1 exposes its GameCoreFacade port as a global:
//   processRule(state, action)                     -> {handled, state?, error?}
//   validateCandidate(before, candidate, action)   -> {handled, state?, error?}
// where `state` is the committed full state when handled, mirroring the
// native facade envelope (GameCoreFacade.response: handled/state/error).
//
// SWAP POINTS (the only lines integration should ever need to touch):
// the PROVIDER_NAMES list and the validateCandidate method-name lookup below.
// Return values may be envelope objects or JSON strings in the native shape;
// a bare committed state object from validateCandidate is accepted as
// {handled:true}; a throw from validateCandidate is converted to
// {handled:false, error} so the shim can add the native rejection prefix.
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
    return { handled: !!(r && r.handled), state: r ? r.state : undefined };
  }

  function validateCandidate(before, candidate, action) {
    var p = provider();
    if (!p) throw new Error(NOT_READY);
    var fn = p.validateCandidate || p.processValidatedCandidate; // SWAP: method name if C1 differs
    if (typeof fn !== "function") throw new Error(NOT_READY);
    var r;
    try {
      r = envelope(fn.call(p, before, candidate, action));
    } catch (e) {
      return { handled: false, error: e && e.message ? e.message : "invalid_delta" };
    }
    if (r && typeof r.handled === "boolean") return r;
    if (r && typeof r === "object") return { handled: true, state: r };
    return { handled: false, error: "invalid_delta" };
  }

  root.CoreAdapter = { processRule: processRule, validateCandidate: validateCandidate };
})(typeof window !== "undefined" ? window : globalThis);

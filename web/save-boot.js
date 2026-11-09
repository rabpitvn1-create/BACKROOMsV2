"use strict";
/*
 * SaveBoot — boot adapter between the game's index.html and SaveStore
 * (web/save-store.js). Load order (assembler, cluster C5): save-store.js,
 * then this file, then the game's own inline script — SaveBoot.ready starts
 * the IndexedDB load immediately so it is already in flight when the game's
 * boot block runs.
 *
 * The state STRING is the only thing that crosses this adapter: the page
 * produces it with its own JSON.stringify(state) exactly as the APK does,
 * and gets back the identical string to JSON.parse. Nothing here parses or
 * re-serializes the game's state object (SaveStore's internal parse-verify
 * does not alter the stored string).
 *
 * ── INTEGRATION BLOCK — the assembler patch for the copied index.html ──────
 * The game's boot is synchronous localStorage code, so C5's assembler wraps
 * it and swaps its five storage touch-points. Everything else — crucially
 * the characterCanon re-injection and the turn-1 legacy migrations — stays
 * byte-identical and runs in its original position, so the migrations still
 * fire under their own `state.turn === 1` gate, exactly as in the APK (they
 * live ONLY in this boot block; load() below has none, as in the original).
 *
 * 1. Wrap: insert
 *        SaveBoot.ready.then(function (savedString) {
 *    immediately before the boot line
 *        let state=JSON.parse(localStorage.getItem("backroom-apk-state")||"null")||initial;
 *    and the matching `});` immediately after the script's final `render();`.
 *    (The `busy` flag, function definitions, the submit listener and the
 *    backroomTurn/backroomError assignments all sit inside the wrap, in
 *    their original order — same as native execution.)
 *
 * 2. Boot read, inside that line:
 *        JSON.parse(localStorage.getItem("backroom-apk-state")||"null")
 *      → JSON.parse(savedString||"null")
 *
 * 3. Boot re-save (the unconditional setItem right after the migrations):
 *        localStorage.setItem("backroom-apk-state",JSON.stringify(state));
 *      → SaveBoot.saveString(JSON.stringify(state));
 *
 * 4. save() — same one-line swap as (3):
 *        localStorage.setItem("backroom-apk-state",JSON.stringify(state));
 *      → SaveBoot.saveString(JSON.stringify(state));
 *    (backroomTurn() and resetGame() reach storage only through save(), so
 *    they are routed automatically.)
 *
 * 5. load() — replace the whole body:
 *        const s=JSON.parse(localStorage.getItem("backroom-apk-state")||"null");
 *        state=<s if it is a valid v2 state, else a fresh copy of initial>;
 *        state.characterCanon=CURRENT_CHARACTER_CANON;currentEntry=-1;
 *        migrateState();ensureSceneEntry();render();resetReaderScroll()
 *      → the same body reading through SaveBoot.loadString() instead:
 *        SaveBoot.loadString().then(function(s){const p=JSON.parse(s||"null"); ... });
 *
 * 6. clearSave() — one call swapped:
 *        localStorage.removeItem("backroom-apk-state");
 *      → SaveBoot.clear();
 *
 * After this patch nothing game-owned remains in localStorage. (The
 * offline build has no snapshot record at all: the VN stage is composed
 * from local assets, so there is nothing to route here.)
 * ───────────────────────────────────────────────────────────────────────────
 */
(function (global) {
  var cache = null; // last known save string, byte-verbatim; null = no save

  var ready = SaveStore.load().then(function (s) { cache = s; return s; });

  function saveString(str) {
    cache = str;
    return SaveStore.save(str); // resolves {bytes, oversize} after verified commit
  }
  function loadString() {
    return SaveStore.load().then(function (s) { cache = s; return s; });
  }
  function clear() {
    cache = null;
    return SaveStore.clear();
  }

  global.SaveBoot = {
    ready: ready,
    saveString: saveString,
    loadString: loadString,
    clear: clear,
    cachedString: function () { return cache; }
  };
  if (typeof module !== "undefined" && module.exports) module.exports = global.SaveBoot;
})(typeof window !== "undefined" ? window : globalThis);

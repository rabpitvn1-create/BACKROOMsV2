"use strict";
/*
 * SaveStore — IndexedDB save layer for the BACKROOMsV2 web build.
 *
 * Replaces the APK page's localStorage save (key "backroom-apk-state"), which
 * collides with the owner's other games on the shared *.github.io origin,
 * strains against the ~5 MB localStorage quota (the save carries the full,
 * unbounded narrative log), and blanks the page when the stored string is
 * corrupt (the page's boot JSON.parse has no try/catch).
 *
 * DB "backroomsv2-web", one object store, two records:
 *   main      — the game's save string, byte-verbatim (exactly what the page's
 *               JSON.stringify(state) produced; no envelope, no codec)
 *   main.prev — the previous main, rotated in the same transaction as a write
 *
 * The Java SharedPreferences shadow store is deliberately NOT ported: nothing
 * in the APK ever reads it (audit A4 §1). One store, one truth.
 */
(function (global) {
  var DB_NAME = "backroomsv2-web";
  var STORE = "saves";
  // The save carries the full, unbounded narrative log — warn while it is
  // still cheap to notice, long before it strains the storage quota.
  var SIZE_WARN_BYTES = 800 * 1024;

  var dbPromise = null;

  function openDb() {
    if (dbPromise) return dbPromise;
    dbPromise = new Promise(function (resolve, reject) {
      var req;
      try { req = indexedDB.open(DB_NAME, 1); }
      catch (e) { dbPromise = null; reject(e); return; }
      req.onupgradeneeded = function () { req.result.createObjectStore(STORE); };
      req.onsuccess = function () {
        var db = req.result;
        // Self-heal (pattern reused from the earlier BACKROOMS web port): the
        // browser may close the connection mid-session — drop the cached
        // handle so the next operation reopens instead of failing forever.
        db.onclose = function () { dbPromise = null; };
        db.onversionchange = function () { db.close(); };
        resolve(db);
      };
      req.onerror = function () { dbPromise = null; reject(req.error); };
      req.onblocked = function () { dbPromise = null; reject(new Error("SaveStore: database open blocked")); };
    });
    return dbPromise;
  }

  // One transparent retry when the cached connection died under us.
  function run(op) {
    return openDb().then(op).catch(function (e) {
      if (e && e.name === "InvalidStateError") {
        dbPromise = null;
        return openDb().then(op);
      }
      throw e;
    });
  }

  // Runs body(store, ctl) inside one transaction; resolves with ctl.done()'s
  // value on commit, rejects on any failure (the transaction rolls back, so a
  // failed save leaves both main and main.prev untouched).
  function txPromise(db, mode, body) {
    return new Promise(function (resolve, reject) {
      var tx = db.transaction(STORE, mode);
      var out, failed = null, settled = false;
      tx.oncomplete = function () { if (!settled) { settled = true; failed ? reject(failed) : resolve(out); } };
      tx.onabort = function () { if (!settled) { settled = true; reject(failed || tx.error || new Error("SaveStore: transaction aborted")); } };
      tx.onerror = function () { if (!failed) failed = tx.error; };
      try {
        body(tx.objectStore(STORE), {
          done: function (v) { out = v; },
          fail: function (e) { failed = e; try { tx.abort(); } catch (_) { /* already aborting */ } }
        });
      } catch (e) { failed = e; try { tx.abort(); } catch (_) { /* ignore */ } }
    });
  }

  function checkSize(stateString) {
    var bytes = new TextEncoder().encode(stateString).length;
    var oversize = bytes > SIZE_WARN_BYTES;
    if (oversize) {
      console.warn("SaveStore: save string is " + bytes + " bytes (> " + SIZE_WARN_BYTES + ").");
    }
    return { bytes: bytes, oversize: oversize };
  }

  // One transaction: rotate main -> main.prev, write the new main, read it
  // back and JSON.parse-verify. Resolves {bytes, oversize} only after the
  // verified commit; rejects on any failure with both records intact.
  function save(stateString) {
    var size = checkSize(stateString);
    return run(function (db) {
      return txPromise(db, "readwrite", function (store, ctl) {
        var getMain = store.get("main");
        getMain.onsuccess = function () {
          if (typeof getMain.result === "string") store.put(getMain.result, "main.prev");
          store.put(stateString, "main");
          var verify = store.get("main");
          verify.onsuccess = function () {
            try {
              if (verify.result !== stateString) throw new Error("SaveStore: read-back mismatch");
              JSON.parse(verify.result); // parse-verify only — the string itself is stored verbatim
              ctl.done({ bytes: size.bytes, oversize: size.oversize });
            } catch (e) { ctl.fail(e); }
          };
          verify.onerror = function () { ctl.fail(verify.error); };
        };
        getMain.onerror = function () { ctl.fail(getMain.error); };
      });
    });
  }

  function usable(str) {
    if (typeof str !== "string") return false;
    try { var v = JSON.parse(str); return v !== null && typeof v === "object"; }
    catch (e) { return false; }
  }

  // Resolves the save string from main, else from main.prev, else null (boot
  // initial). Never rejects: a dead or corrupt store must not blank the page.
  function load() {
    return run(function (db) {
      return txPromise(db, "readonly", function (store, ctl) {
        var got = {};
        var a = store.get("main");
        a.onsuccess = function () { got.main = a.result; };
        a.onerror = function () { ctl.fail(a.error); };
        var b = store.get("main.prev");
        b.onsuccess = function () { got.prev = b.result; ctl.done(got); };
        b.onerror = function () { ctl.fail(b.error); };
      });
    }).then(function (got) {
      if (usable(got.main)) return got.main;
      if (usable(got.prev)) return got.prev;
      return null;
    }).catch(function (e) {
      console.error("SaveStore: load failed, booting initial state", e);
      return null;
    });
  }

  // clearSave: delete both state records.
  function clear() {
    return run(function (db) {
      return txPromise(db, "readwrite", function (store, ctl) {
        store.delete("main");
        var d = store.delete("main.prev");
        d.onsuccess = function () { ctl.done(true); };
        d.onerror = function () { ctl.fail(d.error); };
      });
    });
  }

  var SaveStore = {
    save: save,
    load: load,
    clear: clear,
    checkSize: checkSize,
    SIZE_WARN_BYTES: SIZE_WARN_BYTES
  };
  global.SaveStore = SaveStore;
  if (typeof module !== "undefined" && module.exports) module.exports = SaveStore;
})(typeof window !== "undefined" ? window : globalThis);

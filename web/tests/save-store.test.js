"use strict";
/*
 * SaveStore tests — run:  npm install  (once, in this folder)
 *                         node save-store.test.js
 * Uses fake-indexeddb as an in-memory IndexedDB. Scenarios run sequentially
 * against one database, mirroring a real play session's storage lifecycle.
 */
require("fake-indexeddb/auto");
const assert = require("node:assert");
const SaveStore = require("../save-store.js");

const DB = "backroomsv2-web", STORE = "saves";

// Raw record access, bypassing SaveStore — used to inject corruption and to
// verify exactly what a failed save left behind.
function raw(key, value) {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB, 1);
    req.onsuccess = () => {
      const db = req.result;
      const write = value !== undefined;
      const tx = db.transaction(STORE, write ? "readwrite" : "readonly");
      const r = write ? tx.objectStore(STORE).put(value, key) : tx.objectStore(STORE).get(key);
      let out;
      r.onsuccess = () => { out = r.result; };
      tx.oncomplete = () => { db.close(); resolve(out); };
      tx.onerror = () => reject(tx.error);
    };
    req.onerror = () => reject(req.error);
  });
}

const stateA = JSON.stringify({ title: "BACKROOMS", turn: 3, mode: "DIALOGUE", location: "Level 0",
  player: { name: "Cao Minh", condition: "Ổn định" }, party: [], inventory: [{ id: "white-wraith", name: "White Wraith", quantity: 1 }],
  flags: { communication: { sru: "OFFLINE" } }, log: [{ role: "gm", text: "MỞ ĐẦU — ă ơ ư đ Đ" }], saveVersion: 3 });
const stateB = JSON.stringify({ title: "BACKROOMS", turn: 4, mode: "EXPLORE", location: "Level 1",
  player: { name: "Cao Minh", condition: "Mệt" }, party: [], inventory: [], flags: {},
  log: [{ role: "player", text: "Đi tiếp về phía bắc." }], saveVersion: 3 });
const stateC = JSON.stringify({ turn: 10, log: [] });
const stateD = JSON.stringify({ turn: 11, log: [{ role: "gm", text: "…" }] });

(async () => {
  let n = 0;
  const ok = (name) => { n++; console.log("ok " + n + " - " + name); };

  // 1. Empty store loads null (page boots initial), then save→load round-trips byte-identically.
  assert.strictEqual(await SaveStore.load(), null);
  const r1 = await SaveStore.save(stateA);
  assert.strictEqual(r1.oversize, false);
  assert.strictEqual(await SaveStore.load(), stateA);
  ok("save→load round-trip is byte-identical");

  // 2. Second save rotates: main=B, prev=A (checked at the record level).
  await SaveStore.save(stateB);
  assert.strictEqual(await raw("main"), stateB);
  assert.strictEqual(await raw("main.prev"), stateA);
  ok("rotation: main=new, main.prev=previous");

  // 3. Corrupt main falls back to main.prev.
  await raw("main", "corrupt{{{not json");
  assert.strictEqual(await SaveStore.load(), stateA);
  ok("corrupt main → prev recovery");

  // 4. Both records corrupt → null, never a throw.
  await raw("main.prev", "also corrupt###");
  assert.strictEqual(await SaveStore.load(), null);
  ok("both corrupt → null");

  // 5. A save that fails verification (unparseable payload) rejects and
  //    leaves BOTH records untouched — prev intact, main intact.
  await SaveStore.save(stateC);
  await SaveStore.save(stateD);
  await assert.rejects(() => SaveStore.save("this is not json"));
  assert.strictEqual(await raw("main"), stateD);
  assert.strictEqual(await raw("main.prev"), stateC);
  assert.strictEqual(await SaveStore.load(), stateD);
  ok("failed transaction leaves main and main.prev intact");

  // 6. Payload guard: >800 KB state string warns and flags oversize.
  const big = JSON.stringify({ turn: 99, log: [{ role: "gm", text: "x".repeat(900 * 1024) }] });
  assert.strictEqual(SaveStore.checkSize(stateA).oversize, false);
  let warns = 0;
  const origWarn = console.warn;
  console.warn = () => { warns++; };
  try {
    const rBig = await SaveStore.save(big);
    assert.strictEqual(rBig.oversize, true);
    assert.ok(rBig.bytes > 800 * 1024);
  } finally { console.warn = origWarn; }
  assert.ok(warns >= 1, "console.warn fired for oversize save");
  assert.strictEqual(await SaveStore.load(), big);
  ok(">800 KB warning fires (console + returned flag)");

  // 7. clear() deletes both state records.
  await SaveStore.clear();
  assert.strictEqual(await SaveStore.load(), null);
  assert.strictEqual(await raw("main"), undefined);
  assert.strictEqual(await raw("main.prev"), undefined);
  ok("clear() deletes main + main.prev");

  console.log("\n" + n + "/7 scenarios passed");
})().catch((e) => { console.error("FAIL:", e); process.exit(1); });

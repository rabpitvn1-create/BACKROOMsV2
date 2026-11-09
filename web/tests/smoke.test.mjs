/*
 * Assembled-site smoke test (cluster C5 — the integration seam).
 *
 * Assembles the real site with web/assemble.mjs into a temp dir, serves it
 * over local HTTP, and boots it in jsdom twice (play → reload) against a
 * shared fake IndexedDB. The one thing jsdom cannot do is execute the
 * <script type="module"> glue, so beforeParse assigns window.GameCore from
 * the real C1 module — the identical assignment glue.mjs performs (glue
 * itself is verified by glue.test.mjs). The build is fully offline:
 * window.fetch is stubbed to RECORD, and any call at all is a failure —
 * gameplay must never touch the network.
 *
 * Run:  cd web/tests && npm install && node smoke.test.mjs
 */
import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { mkdtempSync, rmSync } from "node:fs";
import { createServer } from "node:http";
import { readFile } from "node:fs/promises";
import { extname, join, normalize } from "node:path";
import { tmpdir } from "node:os";
import { fileURLToPath } from "node:url";
import { JSDOM, VirtualConsole } from "jsdom";
import { indexedDB as fakeIndexedDB } from "fake-indexeddb";
import * as core from "../core/index.js";

const here = fileURLToPath(new URL(".", import.meta.url));
const repoRoot = join(here, "..", "..");
const siteDir = mkdtempSync(join(tmpdir(), "backroomsv2-site-"));
execFileSync(process.execPath, [join(repoRoot, "web/assemble.mjs"), siteDir], { stdio: "inherit" });

const MIME = { ".html": "text/html", ".js": "text/javascript", ".mjs": "text/javascript", ".json": "application/json", ".png": "image/png", ".jpg": "image/jpeg", ".webp": "image/webp" };
const server = createServer(async (req, res) => {
  try {
    const path = normalize(decodeURIComponent(new URL(req.url, "http://x").pathname)).replace(/^(\.\.[/\\])+/, "");
    const file = join(siteDir, path === "/" ? "index.html" : path);
    const data = await readFile(file);
    res.writeHead(200, { "content-type": MIME[extname(file)] || "application/octet-stream" });
    res.end(data);
  } catch {
    res.writeHead(404); res.end("not found");
  }
});
await new Promise((r) => server.listen(0, "127.0.0.1", r));
const url = `http://127.0.0.1:${server.address().port}/`;

const LOCATION = "Level 0 / The Lobby — khu phòng vàng ban đầu sau no-clip";
const FREE_ACTION = "đi về phía cánh cửa";
const FREE_REPLY =
  "Cao Minh thực hiện hành động: \"" + FREE_ACTION + "\".\n" +
  "Vị trí hiện tại: " + LOCATION + ".\n" +
  "Cao Minh hiện không có đồng đội trong party.\n" +
  "Inventory hiện có: Thái Cổ Song Sát, Hắc Long Chiến Khải.";
const SNAPSHOT_MSG = "Snapshot hiển thị trực tiếp ở khung phía trên — nền theo Level, nhân vật và thực thể đều là ảnh trên máy.";

async function bootPage(fetchCalls) {
  const errors = [];
  const vc = new VirtualConsole();
  vc.on("jsdomError", (e) => {
    const msg = String((e.detail && e.detail.message) || e.message || e);
    // jsdom cannot execute <script type="module"> (glue.mjs); that is a
    // harness limit, not a page error — glue is covered by glue.test.mjs.
    if (!/module/i.test(msg)) errors.push(msg);
  });
  const html = await (await fetch(url)).text();
  const dom = new JSDOM(html, {
    url,
    resources: "usable",
    runScripts: "dangerously",
    pretendToBeVisual: true,
    virtualConsole: vc,
    beforeParse(window) {
      window.indexedDB = fakeIndexedDB;
      window.fetch = (u, opts = {}) => {
        fetchCalls.push({ url: String(u), method: opts.method || "GET" });
        return Promise.resolve({ ok: true, status: 200, text: async () => "{}" });
      };
      window.confirm = () => true;
      window.HTMLElement.prototype.scrollIntoView = function () { this.__scrolled = true; };
      if (!window.TextEncoder) window.TextEncoder = TextEncoder;
      window.GameCore = { processRule: core.processRule };
      window.addEventListener("error", (e) => errors.push(String(e.message)));
    }
  });
  return { window: dom.window, errors };
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
async function until(fn, what, timeout = 8000) {
  const deadline = Date.now() + timeout;
  for (;;) {
    const v = fn();
    if (v) return v;
    if (Date.now() > deadline) throw new Error("timeout waiting for " + what);
    await sleep(25);
  }
}

let step = 0;
const ok = (name) => console.log(`ok ${++step} - ${name}`);

try {
  // ── Session 1: boot → local-rule turn → free-action turn ──────────────
  const fetch1 = [];
  const { window: w1, errors: e1 } = await bootPage(fetch1);
  const d1 = w1.document;
  await until(() => d1.getElementById("turn")?.textContent === "1", "boot to initial state");
  assert.equal(d1.querySelectorAll("#log article").length, 2, "initial log rendered");
  for (const fn of ["save", "load", "resetGame", "clearSave", "render"]) {
    assert.equal(typeof w1[fn], "function", `window.${fn} is a global function`);
  }
  assert.ok(w1.SaveBoot && w1.Android, "SaveBoot and Android bridge present");
  assert.equal(w1.localStorage.getItem("backroom-apk-state"), null, "no game save in localStorage");
  ok("boot: initial state rendered, page globals intact, save lives in IndexedDB only");

  assert.ok(d1.getElementById("snapshotButton"), "snapshot button installed by ui-enhancements");
  ok("snapshot button present");

  const bootSave = JSON.parse(w1.SaveBoot.cachedString());
  assert.equal(bootSave.turn, 1, "boot re-save persisted the initial state");
  ok("boot re-save wrote the initial state to IndexedDB");

  // Local-rule turn: inventory query — commits with ZERO network calls.
  d1.getElementById("action").value = "xem inventory";
  d1.getElementById("form").dispatchEvent(new w1.Event("submit", { bubbles: true, cancelable: true }));
  await until(() => d1.getElementById("turn")?.textContent === "2", "inventory turn commits");
  const afterLocal = JSON.parse(w1.SaveBoot.cachedString());
  assert.equal(afterLocal.turn, 2);
  assert.equal(afterLocal.log.at(-1).text, "Inventory hiện có: Thái Cổ Song Sát, Hắc Long Chiến Khải.");
  assert.equal(fetch1.length, 0, "no window.fetch call so far");
  ok("inventory turn: committed locally, canned reply in log, zero network");

  // Free-action turn: the offline core commits it locally with the neutral
  // state reply — no GM, no proxy, mode/location untouched.
  d1.getElementById("action").value = FREE_ACTION;
  d1.getElementById("form").dispatchEvent(new w1.Event("submit", { bubbles: true, cancelable: true }));
  await until(() => d1.getElementById("turn")?.textContent === "3", "free-action turn commits");
  const afterFree = JSON.parse(w1.SaveBoot.cachedString());
  assert.equal(afterFree.turn, 3);
  assert.equal(afterFree.saveVersion, 3, "core stamped saveVersion 3");
  assert.equal(afterFree.mode, "local APK", "mode untouched by the local commit");
  assert.equal(afterFree.location, LOCATION, "location untouched by the local commit");
  assert.equal(afterFree.log.length, afterLocal.log.length + 2, "player+GM entries appended");
  assert.deepEqual(afterFree.log.at(-2), { role: "player", text: FREE_ACTION });
  assert.deepEqual(afterFree.log.at(-1), { role: "gm", text: FREE_REPLY });
  assert.equal(fetch1.length, 0, "no window.fetch call in the whole session");
  assert.deepEqual(e1, [], "no page errors in session 1: " + e1.join(" | "));
  ok("free-action turn: committed locally (turn 3, neutral reply), zero network");
  w1.close();

  // ── Session 2: reload restores from IndexedDB; snapshot is local ─────
  const fetch2 = [];
  const { window: w2, errors: e2 } = await bootPage(fetch2);
  const d2 = w2.document;
  await until(() => d2.getElementById("turn")?.textContent === "3", "reload restores turn 3");
  const restored = JSON.parse(w2.SaveBoot.cachedString());
  assert.equal(restored.log.length, afterFree.log.length, "full log restored");
  assert.equal(d2.getElementById("location").textContent, LOCATION);
  ok("reload: save restored from IndexedDB exactly (turn 3, full log)");

  const btn = d2.getElementById("snapshotButton");
  assert.ok(btn, "snapshot button present after reload");
  btn.click();
  await until(
    () => d2.getElementById("status")?.textContent === SNAPSHOT_MSG,
    "local snapshot message reaches the status line"
  );
  assert.equal(d2.getElementById("snapshot").__scrolled, true, "stage scrolled into view");
  assert.equal(fetch2.length, 0, "no window.fetch call after reload either");
  assert.deepEqual(e2, [], "no page errors in session 2: " + e2.join(" | "));
  ok("snapshot click: purely local message on the status line, nothing hangs, zero network");
  w2.close();

  console.log(`\nSMOKE PASSED (${step} groups)`);
} finally {
  server.close();
  rmSync(siteDir, { recursive: true, force: true });
}

/*
 * Assembled-site smoke test (cluster C5 — the integration seam).
 *
 * Assembles the real site with web/assemble.mjs into a temp dir, serves it
 * over local HTTP, and boots it in jsdom twice (play → reload) against a
 * shared fake IndexedDB. The one thing jsdom cannot do is execute the
 * <script type="module"> glue, so beforeParse assigns window.GameCore from
 * the real C1 module — the identical assignment glue.mjs performs (glue
 * itself is verified by glue.test.mjs). fetch is stubbed: the proxies'
 * /health warm-up answers OK and /gemini returns one canned candidate.
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

const GM_REPLY = "Cao Minh bước vào hành lang vàng bất tận.";
function makeFetchStub(calls) {
  return async (u, opts = {}) => {
    const method = opts.method || "GET";
    calls.push({ url: String(u), method });
    if (String(u).endsWith("/gemini")) {
      const candidate = {
        reply: GM_REPLY, title: "", location: "Level 0 — hành lang",
        player: { name: "Cao Minh", hp: 100 }, party: [], inventory: [], flags: {}
      };
      return {
        ok: true, status: 200,
        text: async () => JSON.stringify({ candidates: [{ content: { role: "model", parts: [{ text: JSON.stringify(candidate) }] } }] })
      };
    }
    return { ok: true, status: 200, text: async () => "{}" };
  };
}

async function bootPage(calls) {
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
      window.fetch = makeFetchStub(calls);
      window.confirm = () => true;
      if (!window.TextEncoder) window.TextEncoder = TextEncoder;
      window.GameCore = { processRule: core.processRule, processValidatedCandidate: core.processValidatedCandidate };
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
  // ── Session 1: boot → local turn → GM turn ─────────────────────────────
  const calls1 = [];
  const { window: w1, errors: e1 } = await bootPage(calls1);
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

  // Local-rule turn: inventory query — must commit with ZERO network calls.
  const callsBefore = calls1.length;
  d1.getElementById("action").value = "xem inventory";
  d1.getElementById("form").dispatchEvent(new w1.Event("submit", { bubbles: true, cancelable: true }));
  await until(() => d1.getElementById("turn")?.textContent === "2", "inventory turn commits");
  const posts = calls1.slice(callsBefore).filter((c) => c.method === "POST");
  assert.equal(posts.length, 0, "local turn made no POST");
  assert.equal(calls1.filter((c) => c.method === "POST").length, 0, "no POST at all so far");
  const afterLocal = JSON.parse(w1.SaveBoot.cachedString());
  assert.equal(afterLocal.turn, 2);
  assert.ok(afterLocal.log.at(-1).text.startsWith("Inventory hiện"), "canned inventory reply committed");
  ok("inventory turn: committed locally, zero network, canned reply in log");

  // GM turn through the (stubbed) proxy.
  d1.getElementById("action").value = "đi về phía cánh cửa";
  d1.getElementById("form").dispatchEvent(new w1.Event("submit", { bubbles: true, cancelable: true }));
  await until(() => d1.getElementById("turn")?.textContent === "3", "GM turn commits");
  const geminiPosts = calls1.filter((c) => c.method === "POST" && c.url.endsWith("/gemini"));
  assert.equal(geminiPosts.length, 1, "exactly one /gemini POST");
  const afterGm = JSON.parse(w1.SaveBoot.cachedString());
  assert.equal(afterGm.turn, 3);
  assert.equal(afterGm.saveVersion, 3, "core stamped saveVersion 3");
  assert.equal(afterGm.mode, "ai");
  assert.equal(afterGm.location, "Level 0 — hành lang");
  assert.equal(afterGm.log.length, afterLocal.log.length + 2, "player+GM entries appended");
  assert.deepEqual(afterGm.log.at(-2), { role: "player", text: "đi về phía cánh cửa" });
  assert.deepEqual(afterGm.log.at(-1), { role: "gm", text: GM_REPLY });
  assert.deepEqual(e1, [], "no page errors in session 1: " + e1.join(" | "));
  ok("GM turn: proxy candidate validated + committed (turn 3, saveVersion 3, log appended)");
  w1.close();

  // ── Session 2: reload restores from IndexedDB; snapshot degrades ──────
  const calls2 = [];
  const { window: w2, errors: e2 } = await bootPage(calls2);
  const d2 = w2.document;
  await until(() => d2.getElementById("turn")?.textContent === "3", "reload restores turn 3");
  const restored = JSON.parse(w2.SaveBoot.cachedString());
  assert.equal(restored.log.length, afterGm.log.length, "full log restored");
  assert.equal(d2.getElementById("location").textContent, "Level 0 — hành lang");
  ok("reload: save restored from IndexedDB exactly (turn 3, full log)");

  const btn = d2.getElementById("snapshotButton");
  assert.ok(btn, "snapshot button present after reload");
  btn.click();
  await until(
    () => d2.getElementById("status")?.textContent === "Snapshot lỗi: Snapshot chưa khả dụng trên bản web.",
    "snapshot degradation reaches the status line"
  );
  assert.deepEqual(e2, [], "no page errors in session 2: " + e2.join(" | "));
  ok("snapshot click: native-shaped degradation message on the status line, nothing hangs");
  w2.close();

  console.log(`\nSMOKE PASSED (${step} groups)`);
} finally {
  server.close();
  rmSync(siteDir, { recursive: true, force: true });
}

/*
 * Transferred-Hui-mechanism test — the behavioural half of the mechanism
 * transfer spec v2 (the layout half is measured in real Chromium):
 *   - DOM relations: header outside .game-layout; Snapshot + story panel
 *     inside it; #log is story content, not a scroller; the choice deck
 *     flows at the end of the story panel.
 *   - The 8 mandated Tự chạy / Đọc nhanh cases (spec §4.3).
 *   - The 3 save-v2 migration types (spec §4.4), including the web
 *     build's IndexedDB rotation: main.prev keeps the pre-migration
 *     string byte-for-byte.
 *   - Build ID consistency on the assembled site (spec §4.7).
 *
 * The page boots against a stub story (the real story graph is covered
 * by story.test.mjs / vn-flow.test.mjs) so playback timing is exact.
 *
 * Run:  cd web/tests && npm install && node ui-mechanism.test.mjs
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

const here = fileURLToPath(new URL(".", import.meta.url));
const repoRoot = join(here, "..", "..");
const assetsDir = join(repoRoot, "android-apk", "app", "src", "main", "assets");
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
async function until(fn, ms) { const t0 = Date.now(); while (Date.now() - t0 < ms) { if (fn()) return true; await sleep(60); } return fn(); }

const STUB = { start: "a1", scenes: [
  { id: "a1", stage: { level: 0, entity: null }, lines: [{ text: "Một." }], choices: [{ label: "Tiếp", next: "a2" }] },
  { id: "a2", stage: { level: 0, entity: null }, lines: [{ text: "Hai." }], choices: [{ label: "Tiếp nữa", next: "a3" }] },
  { id: "a3", stage: { level: 0, entity: null }, lines: [{ text: "Ba." }], choices: [{ label: "Đặt cờ", next: "a4", set: { stub_flag: "1" } }] },
  { id: "a4", stage: { level: 0, entity: null }, lines: [{ text: "Bốn." }], choices: [{ label: "X", next: "a1" }, { label: "Y", next: "a1" }] },
  { id: "a5", stage: { level: 0, entity: null }, lines: [{ text: "Kết." }], choices: [] },
] };

const entry = (sceneId, selectedChoice = null) => ({ sceneId, title: "BACKROOMS // LEVEL 0 — CẢNH 01", lines: [{ speaker: "", text: "x" }], selectedChoice, runId: "run-test" });
function seedState(over = {}) {
  return JSON.stringify(Object.assign({
    v: 2, title: "MAIN_BACKROOMS — Cao Minh", mode: "Truyện viết sẵn", location: "Level 0",
    player: { name: "Cao Minh", condition: "Ổn định" }, party: [], inventory: [],
    flags: {}, scene: "a1", history: [], historyFormat: 2, sceneHistory: [], runId: "run-test", legacyHistoryCount: 0,
  }, over));
}
function legacySeed(over = {}) {
  // A pre-transfer v2 save: no historyFormat / sceneHistory / runId at all.
  const s = JSON.parse(seedState(over));
  delete s.historyFormat; delete s.sceneHistory; delete s.runId; delete s.legacyHistoryCount;
  return JSON.stringify(s);
}

const MIME = { ".html": "text/html", ".js": "text/javascript", ".mjs": "text/javascript", ".json": "application/json", ".png": "image/png", ".jpg": "image/jpeg", ".webp": "image/webp", ".ttf": "font/ttf" };
function serve(rootDir) {
  const server = createServer(async (req, res) => {
    try {
      const path = normalize(decodeURIComponent(new URL(req.url, "http://x").pathname)).replace(/^(\.\.[/\\])+/, "");
      const data = await readFile(join(rootDir, path === "/" ? "index.html" : path));
      res.writeHead(200, { "content-type": MIME[extname(path)] || "application/octet-stream" });
      res.end(data);
    } catch { res.writeHead(404); res.end("not found"); }
  });
  return new Promise((r) => server.listen(0, "127.0.0.1", () => r(server)));
}

async function bootStub(seed, { useIDB } = {}) {
  const html = await readFile(join(assetsDir, "index.html"), "utf8");
  const errors = [];
  const vc = new VirtualConsole();
  vc.on("jsdomError", (e) => { const m = String((e.detail && e.detail.message) || e.message || e); if (!/Could not load img|Could not load css/i.test(m)) errors.push(m); });
  const dom = new JSDOM(html, {
    url: "http://127.0.0.1/", runScripts: "dangerously", resources: "usable", pretendToBeVisual: true, virtualConsole: vc,
    beforeParse(window) {
      window.fetch = () => Promise.resolve({ ok: true, json: () => Promise.resolve(STUB) });
      window.HTMLElement.prototype.scrollIntoView = function () {};
      if (useIDB) window.indexedDB = fakeIndexedDB;
      if (seed !== undefined) window.localStorage.setItem("backroom-apk-state", seed);
    },
  });
  const { window } = dom;
  window.addEventListener("error", (e) => errors.push(e.message));
  await until(() => window.document.querySelector("#choices [data-choice], #choices [data-restart]"), 4000);
  return { window, errors };
}
const sceneOf = (window) => JSON.parse(window.localStorage.getItem("backroom-apk-state")).scene;
const savedOf = (window) => JSON.parse(window.localStorage.getItem("backroom-apk-state"));
const pressed = (window, id) => window.document.getElementById(id).getAttribute("aria-pressed");

// ── DOM relations (spec cluster A) ──────────────────────────────────────
{
  const { window, errors } = await bootStub(seedState());
  assert.deepEqual(errors, [], "dom boot errors: " + errors.join(" | "));
  const doc = window.document;
  const gl = doc.querySelector(".game-layout");
  assert.ok(gl, "dom: .game-layout exists");
  assert.ok(!gl.contains(doc.querySelector(".campaign-header")), "dom: header is outside .game-layout");
  assert.ok(gl.contains(doc.getElementById("snapshot")), "dom: snapshot is inside .game-layout");
  assert.ok(gl.contains(doc.getElementById("story-panel")), "dom: story panel is inside .game-layout");
  assert.ok(doc.getElementById("snapshot").compareDocumentPosition(doc.getElementById("story-panel")) & window.Node.DOCUMENT_POSITION_FOLLOWING, "dom: story panel follows snapshot");
  assert.ok(doc.getElementById("story-panel").contains(doc.getElementById("log")), "dom: #log is inside the story panel");
  assert.ok(doc.querySelector(".choice-deck").contains(doc.getElementById("choices")), "dom: #choices is inside the choice deck");
  assert.ok(doc.getElementById("log").compareDocumentPosition(doc.querySelector(".choice-deck")) & window.Node.DOCUMENT_POSITION_FOLLOWING, "dom: choice deck flows after the story text");
  assert.ok(!gl.contains(doc.getElementById("intel-panel")), "dom: drawer is outside .game-layout");
  assert.ok(!gl.contains(doc.getElementById("backlog-overlay")), "dom: backlog overlay is outside .game-layout");
  assert.ok(!gl.contains(doc.getElementById("character-overlay")), "dom: character overlay is outside .game-layout");
  assert.ok(doc.getElementById("log").classList.contains("story-text"), "dom: #log carries the story-text class");
  // Avatar resolver: narration has no avatar; Cao Minh uses his portrait;
  // a speaker with no PROFILES entry gets the initials fallback.
  assert.ok(!doc.querySelector("#log .dialogue-avatar"), "dom: stub narration renders no avatar");
  // Avatar resolver against the real cast (R01 overlay wave): every other
  // profiled speaker's overlay becomes a shoulder-to-head crop avatar.
  for (const [speaker, file] of [["Hạ Liên", "ha_lien_overlay.webp"], ["Trần Khải", "tran_khai_overlay.webp"], ["Trần Yến", "tran_yen_overlay.webp"], ["Dạ Yến Linh", "da_yen_linh_overlay.webp"], ["Cao Lâm", "cao_lam_overlay.webp"]]) {
    const h = window.eval(`avatarHtml(${JSON.stringify(speaker)})`);
    assert.ok(h.includes("dialogue-avatar-crop") && h.includes(file), "dom: " + speaker + " avatar crops " + file);
  }
  assert.ok(window.eval('avatarHtml("Cao Minh")').includes("avatars/cao_minh_avatar.jpg"), "dom: Cao Minh avatar uses his portrait");
  const fb = window.eval('avatarHtml("Người Lạ")');
  assert.ok(fb.includes("dialogue-avatar-fallback") && fb.includes(">NL<"), "dom: unprofiled speaker gets the initials fallback");
  // Companion sources resolve to the root overlay files: profiled cast via
  // their PROFILES image, unprofiled generics via <key>_overlay.webp.
  for (const k of ["ha_lien", "tran_khai", "tran_yen", "da_yen_linh", "cao_lam", "wanderer", "mortal"]) {
    assert.equal(window.eval(`companionSrc("${k}")`), `${k}_overlay.webp`, "dom: companionSrc(" + k + ")");
  }
  window.close();
  console.log("DOM relations OK");
}

// ── The 8 playback cases (spec §4.3) ────────────────────────────────────
{
  // Case 1 — one choice, no set: autoplay advances (and chains).
  const { window } = await bootStub(seedState({ scene: "a1" }));
  window.document.getElementById("auto-toggle").click();
  assert.ok(await until(() => sceneOf(window) === "a2", 5000), "case 1: autoplay advances a1 → a2");
  assert.ok(await until(() => sceneOf(window) === "a3", 5000), "case 1: autoplay chains a2 → a3");
  await sleep(700);
  assert.equal(sceneOf(window), "a3", "case 1/2: autoplay stops at the set-carrying choice of a3");
  assert.equal(pressed(window, "auto-toggle"), "false", "case 2: autoplay reports off at a set choice");
  window.close();
  console.log("case 1 OK: autoplay advances through single no-set choices, stops at set");
}
{
  // Case 2 (isolated) — one choice WITH set: autoplay stops immediately.
  const { window } = await bootStub(seedState({ scene: "a3" }));
  window.document.getElementById("auto-toggle").click();
  await sleep(700);
  assert.equal(sceneOf(window), "a3", "case 2: scene unchanged");
  assert.equal(pressed(window, "auto-toggle"), "false", "case 2: autoplay off");
  window.close();
  console.log("case 2 OK: single set choice blocks autoplay");
}
{
  // Case 3 — two or more choices: autoplay stops.
  const { window } = await bootStub(seedState({ scene: "a4" }));
  window.document.getElementById("auto-toggle").click();
  await sleep(700);
  assert.equal(sceneOf(window), "a4", "case 3: scene unchanged at a branch");
  assert.equal(pressed(window, "auto-toggle"), "false", "case 3: autoplay off");
  window.close();
  console.log("case 3 OK: branch blocks autoplay");
}
{
  // Case 4 — skip at a scene whose only records belong to other scenes.
  const { window } = await bootStub(seedState({ scene: "a2", sceneHistory: [entry("a1", { choiceIndex: 0, choiceLabel: "Tiếp", nextSceneId: "a2", choiceSet: null })] }));
  window.document.getElementById("skip-toggle").click();
  await sleep(800);
  assert.equal(sceneOf(window), "a2", "case 4: skip does not move without a record for the current scene");
  assert.equal(pressed(window, "skip-toggle"), "false", "case 4: skip stops quietly");
  window.close();
  console.log("case 4 OK: skip without a current-scene record stops quietly");
}
{
  // Case 5 — skip with a valid record replays exactly that choice.
  const { window } = await bootStub(seedState({ scene: "a1", sceneHistory: [entry("a1", { choiceIndex: 0, choiceLabel: "Tiếp", nextSceneId: "a2", choiceSet: null })] }));
  window.document.getElementById("skip-toggle").click();
  assert.ok(await until(() => sceneOf(window) === "a2", 2500), "case 5: skip replays the recorded choice a1 → a2");
  await sleep(800);
  assert.equal(pressed(window, "skip-toggle"), "false", "case 5: skip stops at the unrecorded next scene");
  window.close();
  console.log("case 5 OK: skip replays the recorded choice");
}
{
  // Case 6 — record no longer matches the live choice data: stop quietly.
  const { window } = await bootStub(seedState({ scene: "a1", sceneHistory: [entry("a1", { choiceIndex: 0, choiceLabel: "Tiếp", nextSceneId: "aX", choiceSet: null })] }));
  window.document.getElementById("skip-toggle").click();
  await sleep(800);
  assert.equal(sceneOf(window), "a1", "case 6: mismatched record is not replayed");
  assert.equal(pressed(window, "skip-toggle"), "false", "case 6: skip stops quietly");
  window.close();
  console.log("case 6 OK: stale record rejected");
}
{
  // Case 7 — fresh scene, no history at all: skip must not invent a choice
  // (in particular it must not fire the set-carrying choice of a3).
  const { window } = await bootStub(seedState({ scene: "a3", sceneHistory: [] }));
  window.document.getElementById("skip-toggle").click();
  await sleep(800);
  assert.equal(sceneOf(window), "a3", "case 7: scene unchanged");
  assert.equal(savedOf(window).flags.stub_flag, undefined, "case 7: no flag was set by skip");
  assert.equal(pressed(window, "skip-toggle"), "false", "case 7: skip stops quietly");
  window.close();
  console.log("case 7 OK: skip never invents a choice");
}
{
  // Case 8 — a manual pick while autoplay runs stops BOTH modes.
  const { window } = await bootStub(seedState({ scene: "a1" }));
  window.document.getElementById("auto-toggle").click();
  assert.equal(pressed(window, "auto-toggle"), "true", "case 8: autoplay armed");
  window.document.querySelector("#choices [data-choice]").click();
  assert.equal(sceneOf(window), "a2", "case 8: manual pick advances");
  assert.equal(pressed(window, "auto-toggle"), "false", "case 8: autoplay stopped by manual pick");
  assert.equal(pressed(window, "skip-toggle"), "false", "case 8: skip stopped by manual pick");
  await sleep(3400);
  assert.equal(sceneOf(window), "a2", "case 8: no autoplay advance after the manual pick");
  window.close();
  console.log("case 8 OK: manual pick stops both modes");
}

// ── The 3 migration types (spec §4.4), page level ───────────────────────
{
  // Type 1 — long legacy history: kept verbatim, sceneHistory + runId added.
  const lines = [1, 2, 3, 4, 5].map((i) => ({ speaker: "", text: "Dòng cũ " + i }));
  const { window, errors } = await bootStub(legacySeed({ scene: "a2", history: lines }));
  assert.deepEqual(errors, [], "migration 1 boot errors: " + errors.join(" | "));
  const s = savedOf(window);
  assert.equal(s.history.length, 5, "migration 1: legacy history kept");
  assert.equal(s.historyFormat, 2, "migration 1: historyFormat added, v stays 2");
  assert.equal(s.v, 2, "migration 1: save version untouched");
  assert.equal(s.legacyHistoryCount, 5, "migration 1: legacy boundary recorded");
  assert.equal(typeof s.runId, "string", "migration 1: runId created");
  // The restored scene's first entry is opened in memory at boot; it is
  // persisted with the next save (a boot-time extra save would rotate
  // main.prev past the pre-migration string the rotation exists to keep).
  const memHistory = JSON.parse(window.eval("JSON.stringify(state.sceneHistory)"));
  assert.ok(Array.isArray(memHistory) && memHistory.length >= 1, "migration 1: sceneHistory started");
  assert.equal(memHistory[memHistory.length - 1].sceneId, "a2", "migration 1: current scene gets the first entry");
  assert.equal(s.scene, "a2", "migration 1: still at the saved scene");
  window.close();
  console.log("migration type 1 OK");
}
{
  // Type 2 — flags + mid-story scene: everything restored in place.
  const { window } = await bootStub(legacySeed({ scene: "a2", flags: { stub_flag: "1" }, history: [{ speaker: "", text: "x" }, { speaker: "", text: "y" }] }));
  const s = savedOf(window);
  assert.equal(s.scene, "a2", "migration 2: scene restored, not restarted");
  assert.equal(s.flags.stub_flag, "1", "migration 2: flags preserved");
  assert.ok(window.document.querySelector("#choices [data-choice]"), "migration 2: the restored scene renders its choices");
  window.close();
  console.log("migration type 2 OK");
}
{
  // Type 3 — empty history: no error, scene history starts recording.
  const { window, errors } = await bootStub(legacySeed({ scene: "a1", history: [] }));
  assert.deepEqual(errors, [], "migration 3 boot errors: " + errors.join(" | "));
  window.document.querySelector("#choices [data-choice]").click();
  await sleep(60);
  const s = savedOf(window);
  assert.equal(s.scene, "a2", "migration 3: play continues normally after migration");
  const e1 = s.sceneHistory.find((e) => e.sceneId === "a1");
  assert.ok(e1 && e1.selectedChoice && e1.selectedChoice.choiceLabel === "Tiếp", "migration 3: the new choice is recorded per-scene");
  window.close();
  console.log("migration type 3 OK");
}

// ── Assembled site: build ID + IndexedDB migration rotation ─────────────
{
  const siteDir = mkdtempSync(join(tmpdir(), "backroomsv2-mechanism-"));
  execFileSync(process.execPath, [join(repoRoot, "web/assemble.mjs"), siteDir], { stdio: "inherit" });
  const html = await readFile(join(siteDir, "index.html"), "utf8");
  const m = html.match(/name="game-build" content="([0-9a-f]{16})"/);
  assert.ok(m, "build: meta game-build carries a 16-hex build ID");
  const buildId = m[1];
  assert.ok(html.includes(`window.__GAME_BUILD__="${buildId}"`), "build: ID readable from JavaScript");
  assert.ok(html.includes(`document.documentElement.dataset.build="${buildId}"`), "build: ID exposed on <html data-build>");
  const srcs = [...html.matchAll(/src="([^"]+)"/g)].map((x) => x[1]).filter((u) => !/^(?:https?:|data:)/.test(u));
  assert.ok(srcs.length > 0 && srcs.every((u) => u.includes("?v=" + buildId)), "build: every resource reference carries the same build ID");
  assert.equal(html.split('story/opening-chapter.json"+V').length - 1, 2, "build: story JSON routed through the version hook (fetch + XHR)");

  // Seed IndexedDB with a pre-migration v2 save; the boot re-save must
  // rotate it to main.prev byte-for-byte while main gains the migration.
  const seeded = legacySeed({ scene: "pro_01", history: [{ speaker: "", text: "Dòng kế thừa." }] });
  const db = await new Promise((res, rej) => {
    const r = fakeIndexedDB.open("backroomsv2-web", 1);
    r.onupgradeneeded = () => r.result.createObjectStore("saves");
    r.onsuccess = () => res(r.result);
    r.onerror = () => rej(r.error);
  });
  await new Promise((res, rej) => {
    const tx = db.transaction("saves", "readwrite");
    tx.objectStore("saves").put(seeded, "main");
    tx.oncomplete = res; tx.onerror = () => rej(tx.error);
  });
  db.close();

  const server = await serve(siteDir);
  const base = `http://127.0.0.1:${server.address().port}/`;
  const pageHtml = await (await fetch(base)).text();
  const errors = [];
  const vc = new VirtualConsole();
  vc.on("jsdomError", (e) => { const msg = String((e.detail && e.detail.message) || e.message || e); if (!/Could not load img|Could not load css/i.test(msg)) errors.push(msg); });
  const dom = new JSDOM(pageHtml, {
    url: base, runScripts: "dangerously", resources: "usable", pretendToBeVisual: true, virtualConsole: vc,
    beforeParse(window) {
      window.fetch = (u, o) => fetch(new URL(u, base), o);
      window.HTMLElement.prototype.scrollIntoView = function () {};
      window.indexedDB = fakeIndexedDB;
    },
  });
  const { window } = dom;
  window.addEventListener("error", (e) => errors.push(e.message));
  assert.ok(await until(() => window.document.querySelector("#choices [data-choice], #choices [data-restart]"), 6000), "idb: page boots from the seeded save");
  await sleep(600); // let the SaveBoot rotation settle
  assert.deepEqual(errors, [], "idb boot errors: " + errors.join(" | "));
  const readRec = (key) => new Promise((res, rej) => {
    const r = fakeIndexedDB.open("backroomsv2-web", 1);
    r.onsuccess = () => {
      const d = r.result;
      const g = d.transaction("saves", "readonly").objectStore("saves").get(key);
      g.onsuccess = () => { d.close(); res(g.result); };
      g.onerror = () => rej(g.error);
    };
    r.onerror = () => rej(r.error);
  });
  const prev = await readRec("main.prev");
  const main = JSON.parse(await readRec("main"));
  assert.equal(prev, seeded, "idb migration: main.prev keeps the pre-migration save byte-for-byte");
  assert.equal(main.historyFormat, 2, "idb migration: main gained historyFormat 2");
  assert.equal(main.history.length, 1, "idb migration: legacy history preserved in main");
  assert.ok(Array.isArray(main.sceneHistory), "idb migration: main gained sceneHistory");
  assert.equal(main.scene, "pro_01", "idb migration: still at the saved scene");
  window.close();
  server.close();
  rmSync(siteDir, { recursive: true, force: true });
  console.log("assembled build ID + IDB migration rotation OK");
}

console.log("UI MECHANISM TEST PASSED");

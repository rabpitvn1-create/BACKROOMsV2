/*
 * Fixed-scene VN flow test — successor of the removed smoke.test.mjs
 * (which integration-tested the old GM/free-action turn flow).
 *
 * Part 1 boots the standalone APK index.html (localStorage save path):
 * fresh playthrough of every scene to the end-of-content state, save
 * restore without duplicated history, and old turn/log save discard.
 * Part 2 assembles the real web site with web/assemble.mjs, serves it
 * over local HTTP against a shared fake IndexedDB (the SaveBoot save
 * path), and repeats the playthrough + restore.
 *
 * jsdom does not implement fetch, so beforeParse delegates window.fetch
 * to Node's fetch against the local server — the story JSON is the real
 * file in both parts. No external network is touched.
 *
 * Run:  cd web/tests && npm install && node vn-flow.test.mjs
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

async function bootPage(base, html, { seedLocalSave, useIDB } = {}) {
  const errors = [];
  const vc = new VirtualConsole();
  vc.on("jsdomError", (e) => { const m = String((e.detail && e.detail.message) || e.message || e); if (!/Could not load img|Could not load css/i.test(m)) errors.push(m); });
  const dom = new JSDOM(html, {
    url: base, runScripts: "dangerously", resources: "usable", pretendToBeVisual: true, virtualConsole: vc,
    beforeParse(window) {
      window.fetch = (u, o) => fetch(new URL(u, base), o);
      window.HTMLElement.prototype.scrollIntoView = function () {};
      if (useIDB) window.indexedDB = fakeIndexedDB;
      if (seedLocalSave !== undefined) window.localStorage.setItem("backroom-apk-state", seedLocalSave);
    },
  });
  const { window } = dom;
  window.addEventListener("error", (e) => errors.push(e.message));
  for (let i = 0; i < 120; i++) {
    await sleep(50);
    if (window.document.querySelector("#choices [data-choice], #choices [data-restart]")) break;
  }
  return { window, errors };
}

async function playToEnd(window) {
  const doc = window.document;
  let clicks = 0;
  while (doc.querySelector("#choices [data-choice]") && clicks < 500) {
    doc.querySelector("#choices [data-choice]").click();
    await sleep(40);
    clicks++;
  }
  return clicks;
}

function assertCommonSceneState(window, label) {
  const doc = window.document;
  assert.equal(doc.querySelector("textarea"), null, label + ": free-text input must be gone");
  assert.ok(!doc.body.textContent.includes("GAME MASTER"), label + ": no GAME MASTER framing");
  assert.ok(doc.querySelector("#log .text").textContent.startsWith("Yến tiệc đêm ấy"), label + ": prologue opens the story");
  assert.ok(doc.querySelector("#stage-background").style.backgroundImage.includes("level_0.webp"), label + ": stage background follows scene level");
}

// ── Part 1: standalone APK page ─────────────────────────────────────────
{
  const server = await serve(assetsDir);
  const base = `http://127.0.0.1:${server.address().port}/`;
  const html = await (await fetch(base)).text();

  let { window, errors } = await bootPage(base, html);
  assert.deepEqual(errors, [], "part1 fresh boot errors: " + errors.join(" | "));
  assertCommonSceneState(window, "part1");
  const clicks = await playToEnd(window);
  assert.equal(clicks, 75, "part1: 75 clicks across 76 scenes");
  let doc = window.document;
  assert.ok(doc.querySelector("#choices").textContent.includes("Hết phần nội dung hiện có"), "part1: clean end-of-content state");
  const savedRaw = window.localStorage.getItem("backroom-apk-state");
  const saved = JSON.parse(savedRaw);
  assert.equal(saved.v, 2); assert.equal(saved.scene, "R01_09_05");
  assert.equal(saved.history.length, 1090, "part1: all 1090 lines of the first-choice path recorded");
  console.log("part 1a OK: standalone fresh playthrough → end state");

  ({ window, errors } = await bootPage(base, html, { seedLocalSave: savedRaw }));
  assert.equal(JSON.parse(window.localStorage.getItem("backroom-apk-state")).history.length, 1090, "part1: restore does not duplicate history");
  assert.ok(window.document.querySelector("#choices").textContent.includes("Hết phần nội dung hiện có"), "part1: restored at terminal scene");
  console.log("part 1b OK: standalone save restore at R01_09_05");

  const oldSave = JSON.stringify({ title: "MAIN_BACKROOMS — Cao Minh", turn: 7, mode: "local APK", location: "Level 0", player: { name: "Cao Minh" }, party: [], inventory: [], flags: {}, log: [{ role: "gm", text: "MỞ ĐẦU cũ" }] });
  ({ window, errors } = await bootPage(base, html, { seedLocalSave: oldSave }));
  const replaced = JSON.parse(window.localStorage.getItem("backroom-apk-state"));
  assert.equal(replaced.v, 2, "part1: old save replaced by v2");
  assert.equal(replaced.scene, "pro_01", "part1: old save restarts at first scene");
  assertCommonSceneState(window, "part1-old-save");
  console.log("part 1c OK: old turn/log save discarded → pro_01");
  server.close();
}

// ── Part 2: assembled web site ──────────────────────────────────────────
{
  const siteDir = mkdtempSync(join(tmpdir(), "backroomsv2-vn-site-"));
  execFileSync(process.execPath, [join(repoRoot, "web/assemble.mjs"), siteDir], { stdio: "inherit" });
  const server = await serve(siteDir);
  const base = `http://127.0.0.1:${server.address().port}/`;
  const html = await (await fetch(base)).text();

  let { window, errors } = await bootPage(base, html, { useIDB: true });
  assert.deepEqual(errors, [], "part2 fresh boot errors: " + errors.join(" | "));
  assertCommonSceneState(window, "part2");
  const clicks = await playToEnd(window);
  assert.equal(clicks, 75, "part2: 75 clicks across 76 scenes");
  assert.ok(window.document.querySelector("#choices").textContent.includes("Hết phần nội dung hiện có"), "part2: clean end-of-content state");
  await sleep(150); // let the async SaveBoot write settle
  console.log("part 2a OK: assembled site fresh playthrough → end state");

  ({ window, errors } = await bootPage(base, html, { useIDB: true }));
  assert.ok(window.document.querySelector("#choices").textContent.includes("Hết phần nội dung hiện có"), "part2: IndexedDB save restores at terminal scene");
  assert.equal(window.document.querySelectorAll("#log article").length, 1090, "part2: restored history has all 1090 lines, no duplicates");
  console.log("part 2b OK: assembled site save restore at R01_09_05");
  server.close();
  rmSync(siteDir, { recursive: true, force: true });
}

console.log("VN FLOW TEST PASSED");

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
  assert.ok(doc.querySelector("#stage-background").getAttribute("src").includes("level_0.webp"), label + ": stage background follows scene level");
}

// ── Part 1: standalone APK page ─────────────────────────────────────────
{
  const server = await serve(assetsDir);
  const base = `http://127.0.0.1:${server.address().port}/`;
  const html = await (await fetch(base)).text();

  let { window, errors } = await bootPage(base, html);
  assert.deepEqual(errors, [], "part1 fresh boot errors: " + errors.join(" | "));
  assertCommonSceneState(window, "part1");
  // Transferred Hui scroll mechanism (owner ruling, mechanism spec v2):
  // the document is locked (html,body height + body overflow hidden),
  // .game-layout is the single reading scroller holding BOTH the sticky
  // Snapshot and the story panel, the story text itself never scrolls
  // independently, and the choice deck flows at the end of the panel
  // instead of being pinned (the old app-frame assertions encoded the
  // banned patch architecture and are replaced by these mechanism ones).
  assert.match(html, /\.shell\{[^}]*height:100dvh[^}]*display:flex[^}]*flex-direction:column/, "part1: shell is a fixed-height flex column");
  assert.match(html, /html,body\{[^}]*height:100%/, "part1: document height is locked");
  assert.match(html, /body\{[^}]*overflow:hidden/, "part1: document scroll is locked");
  assert.match(html, /\.game-layout\{[^}]*overflow-y:auto/, "part1: .game-layout is the single reading scroller");
  assert.match(html, /\.snapshot-stage\{[^}]*position:sticky/, "part1: snapshot sticks inside the scroller");
  assert.doesNotMatch(html, /\.log\{[^}]*overflow-y:auto/, "part1: story text is NOT an independent scroll region");
  assert.doesNotMatch(html, /\.choice-deck\{[^}]*flex:none/, "part1: choice deck flows at the end of the story panel, not pinned");
  const clicks = await playToEnd(window);
  assert.equal(clicks, 81, "part1: 81 clicks across 82 scenes");
  let doc = window.document;
  assert.ok(doc.querySelector("#choices").textContent.includes("Hết phần nội dung hiện có"), "part1: clean end-of-content state");
  // Hui-style segmentation: the story frame holds ONLY the current scene's
  // segment (28 visible lines at R01_09_05 on the first-choice path); the
  // full run lives in state.history/backlog, not in the frame.
  assert.equal(doc.querySelectorAll("#log article").length, 28, "part1: frame shows only the terminal scene's segment");
  assert.ok(doc.querySelector("#log .text").textContent.startsWith("Cần nói rõ một điều"), "part1: frame opens with the terminal scene's first line");
  assert.ok(!doc.querySelector("#log").textContent.includes("Yến tiệc đêm ấy"), "part1: earlier scenes are not in the story frame");
  const savedRaw = window.localStorage.getItem("backroom-apk-state");
  const saved = JSON.parse(savedRaw);
  assert.equal(saved.v, 2); assert.equal(saved.scene, "R01_09_05");
  assert.equal(saved.history.length, 1181, "part1: all 1181 lines of the first-choice path recorded");
  console.log("part 1a OK: standalone fresh playthrough → end state");

  ({ window, errors } = await bootPage(base, html, { seedLocalSave: savedRaw }));
  assert.equal(JSON.parse(window.localStorage.getItem("backroom-apk-state")).history.length, 1181, "part1: restore does not duplicate history");
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
  assert.equal(clicks, 81, "part2: 81 clicks across 82 scenes");
  assert.ok(window.document.querySelector("#choices").textContent.includes("Hết phần nội dung hiện có"), "part2: clean end-of-content state");
  await sleep(150); // let the async SaveBoot write settle
  console.log("part 2a OK: assembled site fresh playthrough → end state");

  ({ window, errors } = await bootPage(base, html, { useIDB: true }));
  assert.ok(window.document.querySelector("#choices").textContent.includes("Hết phần nội dung hiện có"), "part2: IndexedDB save restores at terminal scene");
  assert.equal(window.document.querySelectorAll("#log article").length, 28, "part2: restored frame shows only the terminal scene's segment (full history stays in the save/backlog)");
  assert.ok(!window.document.querySelector("#log").textContent.includes("Yến tiệc đêm ấy"), "part2: earlier scenes are not in the story frame");
  console.log("part 2b OK: assembled site save restore at R01_09_05");
  server.close();
  rmSync(siteDir, { recursive: true, force: true });
}

// ── Part 3: user-path clicks (real event dispatch, not .click()) ──────
// The harness above uses el.click(), which fires even on buttons a user
// could never reach. This part walks the whole story dispatching real
// bubbling click events through the delegated #choices listener, and at
// the R01_00_03 branch point asserts both choices are presented (not
// hidden, not display:none) and that choosing the SECOND one advances
// the scene and sets its flag — the exact path a phone user takes.
{
  const server = await serve(assetsDir);
  const base = `http://127.0.0.1:${server.address().port}/`;
  const html = await (await fetch(base)).text();
  const { window, errors } = await bootPage(base, html);
  assert.deepEqual(errors, [], "part3 fresh boot errors: " + errors.join(" | "));
  const doc = window.document;
  // Profile drawer (QA L1): the drawer list is generated from PROFILES —
  // its buttons match the PROFILES keys exactly (no dead old-cast buttons),
  // and clicking Hạ Liên's button — a path the old static list never
  // offered — opens her profile overlay with her name and overlay image.
  const tap = (el) => el.dispatchEvent(new window.MouseEvent("click", { bubbles: true, cancelable: true }));
  const profileKeys = [...window.eval("Object.keys(PROFILES)")]; // spread: jsdom-realm array → Node realm for deepEqual
  const listBtns = [...doc.querySelectorAll("#profile-list [data-profile-open]")];
  assert.deepEqual(listBtns.map((b) => b.getAttribute("data-profile-open")), profileKeys, "part3: drawer profile buttons match PROFILES keys");
  for (const dead of ["iris", "syvial", "lucia"]) {
    assert.equal(doc.querySelector(`#intel-panel [data-profile-open="${dead}"]`), null, "part3: no " + dead + " profile button in the drawer");
    assert.ok(!html.includes(`data-profile-open="${dead}"`), "part3: no static " + dead + " profile button in the markup");
  }
  tap(doc.getElementById("intel-toggle"));
  assert.ok(doc.body.classList.contains("intel-open"), "part3: intel drawer opens");
  const ov = doc.getElementById("character-overlay");
  tap(doc.querySelector("#profile-list [data-profile-open='ha_lien']"));
  assert.equal(ov.hidden, false, "part3: Hạ Liên profile overlay opens from the drawer");
  assert.equal(doc.getElementById("character-overlay-name-text").textContent, "Hạ Liên", "part3: overlay shows Hạ Liên's name");
  assert.ok(doc.getElementById("character-overlay-image").getAttribute("src").includes("ha_lien_overlay.webp"), "part3: overlay shows Hạ Liên's overlay image");
  tap(doc.getElementById("character-overlay-close"));
  assert.equal(ov.hidden, true, "part3: profile overlay closes");
  tap(doc.querySelector("#profile-list [data-profile-open='cao_minh']"));
  assert.equal(ov.hidden, false, "part3: Cao Minh profile overlay still opens from the drawer list");
  assert.equal(doc.getElementById("character-overlay-name-text").textContent, "Cao Minh", "part3: overlay shows Cao Minh's name");
  tap(doc.getElementById("character-overlay-close"));
  // R01 overlay wave: a newly profiled character opens the same way.
  tap(doc.querySelector("#profile-list [data-profile-open='tran_khai']"));
  assert.equal(ov.hidden, false, "part3: Trần Khải profile overlay opens from the drawer");
  assert.equal(doc.getElementById("character-overlay-name-text").textContent, "Trần Khải", "part3: overlay shows Trần Khải's name");
  assert.ok(doc.getElementById("character-overlay-image").getAttribute("src").includes("tran_khai_overlay.webp"), "part3: overlay shows Trần Khải's overlay image");
  tap(doc.getElementById("character-overlay-close"));
  tap(doc.getElementById("intel-close"));
  let clicks = 0, branchChecked = false;
  const companionSeen = new Set();
  let khaiAvatarChecked = false;
  while (doc.querySelector("#choices [data-choice]") && clicks < 500) {
    const before = JSON.parse(window.localStorage.getItem("backroom-apk-state"));
    // Companion spot-checks on the walk: the staged figure is on, in the
    // left slot, with the right overlay file (Khải's debut scene, Cao
    // Lâm's debut scene).
    if (before.scene === "R01_00_03" || before.scene === "R01_07_01") {
      const want = before.scene === "R01_00_03" ? "tran_khai_overlay.webp" : "cao_lam_overlay.webp";
      assert.ok(doc.getElementById("stage-companion-figure").classList.contains("stage-companion-on"), "part3: companion figure is on at " + before.scene);
      assert.ok(doc.getElementById("stage-companion").getAttribute("src").includes(want), "part3: companion at " + before.scene + " is " + want);
      companionSeen.add(before.scene);
    }
    // The first rendered Trần Khải dialogue line carries his crop avatar.
    if (!khaiAvatarChecked) {
      const art = [...doc.querySelectorAll("#log article")].find((a) => { const s = a.querySelector(".speaker"); return s && s.textContent === "Trần Khải"; });
      if (art) {
        const img = art.querySelector(".dialogue-avatar-crop img");
        assert.ok(img && img.getAttribute("src").includes("tran_khai_overlay.webp"), "part3: Trần Khải dialogue avatar crops his overlay");
        khaiAvatarChecked = true;
      }
    }
    const btns = doc.querySelectorAll("#choices [data-choice]");
    let btn = btns[0];
    if (before.scene === "R01_00_03") {
      assert.equal(btns.length, 2, "part3: R01_00_03 presents both choices");
      assert.equal(doc.getElementById("choices").hidden, false, "part3: choices container is not hidden");
      for (const b of btns) {
        const cs = window.getComputedStyle(b);
        assert.notEqual(cs.display, "none", "part3: choice button is displayed");
        assert.notEqual(cs.visibility, "hidden", "part3: choice button is visible");
      }
      btn = btns[1]; // the "silent" branch
    }
    btn.dispatchEvent(new window.MouseEvent("click", { bubbles: true, cancelable: true }));
    await sleep(40);
    clicks++;
    if (before.scene === "R01_00_03") {
      const after = JSON.parse(window.localStorage.getItem("backroom-apk-state"));
      assert.equal(after.scene, "R01_00_04", "part3: dispatched click advances R01_00_03 → R01_00_04");
      assert.equal(after.flags.r01_khai_first_impression, "silent", "part3: dispatched click sets the branch flag");
      branchChecked = true;
    }
  }
  assert.equal(clicks, 81, "part3: 81 dispatched clicks across 82 scenes");
  assert.ok(branchChecked, "part3: branch point R01_00_03 was exercised");
  assert.ok(companionSeen.has("R01_00_03") && companionSeen.has("R01_07_01"), "part3: companion spot-check scenes were visited");
  assert.ok(khaiAvatarChecked, "part3: a Trần Khải dialogue line was rendered and avatar-checked");
  assert.ok(doc.querySelector("#choices").textContent.includes("Hết phần nội dung hiện có"), "part3: clean end-of-content state");
  console.log("part 3 OK: full playthrough via dispatched click events; R01_00_03 branch sets flag");
  server.close();
}

console.log("VN FLOW TEST PASSED");

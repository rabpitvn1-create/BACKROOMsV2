/*
 * Headless parity test for web/ui-enhancements.js (cluster C3).
 * Loads the REAL android-apk/app/src/main/assets/index.html in jsdom,
 * stubs C4's window.SaveBoot adapter, leaves window.Android ABSENT
 * (inert-safe path), then injects ui-enhancements.js the way the
 * assembler will: as a script running after the game's inline script.
 *
 * Run:  cd web/tests && npm install && node ui-enhancements.test.mjs
 */
import { JSDOM, VirtualConsole } from "jsdom";
import fs from "node:fs";
import path from "node:path";
import url from "node:url";

const here = path.dirname(url.fileURLToPath(import.meta.url));
const html = fs.readFileSync(path.join(here, "../../android-apk/app/src/main/assets/index.html"), "utf8");
const ui = fs.readFileSync(path.join(here, "../ui-enhancements.js"), "utf8");

let failures = 0;
function check(name, cond) {
  console.log((cond ? "PASS " : "FAIL ") + name);
  if (!cond) failures++;
}
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function boot(storedSnapshot) {
  const errors = [];
  const vc = new VirtualConsole();
  vc.on("jsdomError", (e) => errors.push(String(e.detail && e.detail.message || e.message)));
  const dom = new JSDOM(html, {
    url: "https://rabpitvn1-create.github.io/BACKROOMsV2/",
    runScripts: "dangerously",
    pretendToBeVisual: true,
    virtualConsole: vc,
  });
  const { window } = dom;
  window.addEventListener("error", (e) => errors.push(e.message));
  // C4 adapter stub — stateful, promise-based, per save-boot.js header.
  const calls = { saved: [] };
  let stored = storedSnapshot;
  window.SaveBoot = {
    loadSnapshot: () => Promise.resolve(stored),
    saveSnapshot: (obj) => { calls.saved.push(obj); stored = obj; return Promise.resolve({}); },
  };
  // window.Android deliberately absent.
  const s = window.document.createElement("script");
  s.textContent = ui;
  window.document.body.appendChild(s);
  return { window, errors, calls };
}

// ── Scenario A: cached snapshot exists for the current turn ──────────────
{
  const { window, errors, calls } = boot({ turn: 1, model: "Stub", dataUri: "data:image/jpeg;base64,QUJD" });
  const doc = window.document;
  await sleep(50);

  check("A: no script errors on boot+install", errors.length === 0);
  const btn = doc.getElementById("snapshotButton");
  check("A: snapshot button exists with native label", !!btn && btn.textContent === "Tạo Snapshot");
  check("A: button sits in .actions before .wide",
    !!btn && btn.parentElement.classList.contains("actions") &&
    btn.nextElementSibling && btn.nextElementSibling.classList.contains("wide"));
  check("A: payload globals defined",
    ["requestSnapshot", "backroomSnapshot", "backroomSnapshotError", "backroomTurn", "backroomError", "render"]
      .every((k) => typeof window[k] === "function"));
  const img = doc.querySelector("#snapshot img");
  check("A: cached snapshot rendered as <img> via SaveBoot", !!img && img.src === "data:image/jpeg;base64,QUJD");
  check("A: nothing written to localStorage snapshot key", window.localStorage.getItem("backroom-apk-snapshot") === null);

  // Pending bubbles on submit (bridge absent: page handler bails, bubbles still appear).
  doc.getElementById("action").value = "đi về phía cửa";
  doc.getElementById("form").dispatchEvent(new window.Event("submit", { bubbles: true, cancelable: true }));
  const pending = doc.querySelectorAll('[data-pending="1"]');
  check("A: submit creates 2 pending bubbles", pending.length === 2);
  check("A: player bubble carries the typed action",
    pending.length === 2 && pending[0].querySelector(".text").textContent === "đi về phía cửa");
  check("A: GM bubble shows the native pending text",
    pending.length === 2 && pending[1].querySelector(".text").textContent === "Đang xử lý lượt…");

  // Turn completes: wrapper must clear bubbles (state here is the page's own).
  const st = JSON.parse(window.eval("JSON.stringify(state)"));
  st.turn = 2;
  window.backroomTurn(JSON.stringify(st));
  await sleep(50);
  check("A: backroomTurn wrapper clears pending bubbles", doc.querySelectorAll('[data-pending="1"]').length === 0);
  check("A: no errors after turn", errors.length === 0);

  // Snapshot delivery routes to SaveBoot.saveSnapshot with the native object shape.
  window.backroomSnapshot(JSON.stringify({ turn: 2, model: "Gemini", dataUri: "data:image/png;base64,WFla" }));
  await sleep(50);
  check("A: SaveBoot.saveSnapshot got {turn, model, dataUri}",
    calls.saved.length === 1 && calls.saved[0].turn === 2 && calls.saved[0].model === "Gemini" &&
    calls.saved[0].dataUri === "data:image/png;base64,WFla");
  check("A: localStorage snapshot key still untouched", window.localStorage.getItem("backroom-apk-snapshot") === null);
  const img2 = doc.querySelector("#snapshot img");
  check("A: fresh snapshot re-rendered from the adapter", !!img2 && img2.src === "data:image/png;base64,WFla");

  // Stale-turn payload is dropped, as native.
  window.backroomSnapshot(JSON.stringify({ turn: 99, model: "Gemini", dataUri: "data:image/png;base64,U1RBTEU=" }));
  await sleep(20);
  check("A: stale-turn snapshot dropped", calls.saved.length === 1);

  // Snapshot error callback: matching turn reports, stale turn ignored.
  window.backroomSnapshotError(JSON.stringify({ turn: 2, message: "hết quota" }));
  check("A: snapshot error reaches the status line",
    doc.getElementById("status").textContent === "Snapshot lỗi: hết quota");
  window.backroomSnapshotError(JSON.stringify({ turn: 99, message: "stale" }));
  check("A: stale snapshot error ignored",
    doc.getElementById("status").textContent === "Snapshot lỗi: hết quota");

  // Double injection is a no-op (native guard).
  const s2 = doc.createElement("script");
  s2.textContent = ui;
  doc.body.appendChild(s2);
  check("A: second load does not duplicate the button", doc.querySelectorAll("#snapshotButton").length === 1);
  check("A: no errors at end of scenario A", errors.length === 0);
  window.close();
}

// ── Scenario B: no cached snapshot → placeholder + auto-snapshot attempt ─
{
  const { window, errors } = boot(null);
  const doc = window.document;
  await sleep(50);
  check("B: no snapshot art layer when cache is empty, VN stage intact",
    !doc.querySelector("#snapshot .snapshot-art") && !!doc.querySelector("#snapshot .stage-character-wrap"));
  await sleep(900); // native auto-snapshot timer is 700 ms
  check("B: auto-snapshot fired and reported the missing bridge (inert-safe)",
    doc.getElementById("status").textContent === "Không tìm thấy Android snapshot bridge.");
  check("B: no script errors", errors.length === 0);
  window.close();
}

console.log(failures === 0 ? "\nALL CHECKS PASSED" : `\n${failures} CHECK(S) FAILED`);
process.exit(failures === 0 ? 0 : 1);

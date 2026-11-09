/*
 * Headless parity test for web/ui-enhancements.js (cluster C3) against the
 * offline native payload in MainActivity.installUiEnhancements().
 * Loads the REAL android-apk/app/src/main/assets/index.html in jsdom,
 * leaves window.Android ABSENT (the payload needs no bridge at all now),
 * then injects ui-enhancements.js the way the assembler will: as a script
 * running after the game's inline script.
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

const SNAPSHOT_MSG = "Snapshot hiển thị trực tiếp ở khung phía trên — nền theo Level, nhân vật và thực thể đều là ảnh trên máy.";

let failures = 0;
function check(name, cond) {
  console.log((cond ? "PASS " : "FAIL ") + name);
  if (!cond) failures++;
}
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function boot() {
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
  // jsdom does not implement scrollIntoView; the payload calls it on the
  // stage when the snapshot button is clicked. Record instead of throwing.
  window.HTMLElement.prototype.scrollIntoView = function () { this.__scrolled = true; };
  // window.Android deliberately absent; no SaveBoot stub — the offline
  // payload touches neither.
  const s = window.document.createElement("script");
  s.textContent = ui;
  window.document.body.appendChild(s);
  return { window, errors };
}

// ── Scenario A: install + no turn machinery + local snapshot ─────────────
{
  const { window, errors } = boot();
  const doc = window.document;
  await sleep(50);

  check("A: no script errors on boot+install", errors.length === 0);
  const btn = doc.getElementById("snapshotButton");
  check("A: snapshot button exists with native label", !!btn && btn.textContent === "Tạo Snapshot");
  check("A: button sits in .actions before .wide",
    !!btn && btn.parentElement.classList.contains("actions") &&
    btn.nextElementSibling && btn.nextElementSibling.classList.contains("wide"));
  check("A: payload globals defined",
    ["requestSnapshot", "render"]
      .every((k) => typeof window[k] === "function"));
  check("A: payload carries no GM/turn machinery",
    !ui.includes("GAME MASTER") && !ui.includes("window.backroomTurn") &&
    !ui.includes("window.backroomError") && !ui.includes("data-pending"));
  check("A: snapshot-image globals are gone",
    typeof window.backroomSnapshot === "undefined" && typeof window.backroomSnapshotError === "undefined");
  const styles = [...doc.querySelectorAll("style")].map((s) => s.textContent).join("\n");
  check("A: web-only A3 radius rule present in the injected style", styles.includes("#snapshot .stage-bg{border-radius:20px}"));
  check("A: no .snapshot-placeholder rule (removed with the image machinery)", !styles.includes("snapshot-placeholder"));
  check("A: no .message.pending rule (removed with the turn machinery)", !styles.includes(".message.pending"));
  check("A: nothing written to localStorage snapshot key", window.localStorage.getItem("backroom-apk-snapshot") === null);
  check("A: VN stage intact (no snapshot-art layer)", !doc.querySelector("#snapshot .snapshot-art") && !!doc.querySelector("#snapshot .stage-character-wrap"));

  // No auto-snapshot timer anymore: the status line must stay untouched.
  const statusAtBoot = doc.getElementById("status").textContent;
  await sleep(900);
  check("A: no auto-snapshot fires after boot", doc.getElementById("status").textContent === statusAtBoot);
  check("A: no errors while idle", errors.length === 0);

  // No turn machinery left: submitting the (legacy) form must not make
  // the payload add pending bubbles or any GM placeholder.
  doc.getElementById("action").value = "đi về phía cửa";
  doc.getElementById("form").dispatchEvent(new window.Event("submit", { bubbles: true, cancelable: true }));
  check("A: submit creates no pending bubbles", doc.querySelectorAll('[data-pending="1"]').length === 0);
  check("A: no errors after submit", errors.length === 0);

  // Snapshot button: purely local showSnapshot — status + scroll, no bridge.
  btn.click();
  check("A: snapshot click sets the exact local status message",
    doc.getElementById("status").textContent === SNAPSHOT_MSG);
  check("A: snapshot click scrolls the stage into view", doc.getElementById("snapshot").__scrolled === true);
  window.requestSnapshot();
  check("A: window.requestSnapshot is the same local action",
    doc.getElementById("status").textContent === SNAPSHOT_MSG);

  check("A: no errors at end of scenario A", errors.length === 0);

  // Double injection is a no-op (native guard).
  const s2 = doc.createElement("script");
  s2.textContent = ui;
  doc.body.appendChild(s2);
  check("A: second load does not duplicate the button", doc.querySelectorAll("#snapshotButton").length === 1);
  window.close();
}

console.log(failures === 0 ? "\nALL CHECKS PASSED" : `\n${failures} CHECK(S) FAILED`);
process.exit(failures === 0 ? 0 : 1);

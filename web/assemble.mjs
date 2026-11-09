#!/usr/bin/env node
// Assembler for the BACKROOMsV2 web build (cluster C5).
//
//   node web/assemble.mjs [outDir]        (default outDir: <repo>/site)
//
// Builds the static site from two inputs, never modifying either:
//   - android-apk/app/src/main/assets/  — the game's own shell + data,
//     copied byte-identical (the copied index.html is then patched, below);
//   - web/                              — this overlay (save layer,
//     injected-UI recreation).
// Everything is computed and asserted in memory BEFORE anything is written;
// any failed check aborts with a non-zero exit and no site is produced.
//
// ── The index.html patch (C4's six-step contract, save-boot.js header) ────
// Steps 2–6 are the literal string swaps from that contract. Step 1 (the
// boot wrap) is applied with one deliberate scoping deviation, forced by
// composition with the rest of the page: the wrap covers ONLY the boot
// segment — the `let state=…` line through the boot re-save line — instead
// of the whole rest of the script. A whole-script wrap would trap the
// page's function declarations (save/load/resetGame/clearSave/render)
// inside the promise callback, where they stop being globals: the page's
// own inline onclick="save()" buttons and ui-enhancements.js (which reads
// the bare global `state` and wraps window.render) would break. So the
// assembler hoists the declarations out instead:
//   - `let state=…` / `let busy=false;` become assignments to two bindings
//     declared just above the wrap (`let state,busy;`), keeping `state` a
//     global lexical binding exactly as in the APK;
//   - the script's final top-level `render();` is removed, and a
//     `render();` is appended inside the wrap right after the boot
//     re-save — boot still ends with a render, in the original position
//     relative to the boot segment, and render() is the page's own
//     (by then ui-enhancements-wrapped) global.
// Everything the contract exists to preserve is preserved: the boot read
// swap, the characterCanon re-injection and the turn-1 legacy migrations
// run inside the wrap in their original order under their original
// `state.turn === 1` gate, both setItem calls route to SaveBoot, load()
// and clearSave() route to SaveBoot, and no game-owned localStorage key
// remains (asserted below: the patched page contains no "localStorage").
//
// ── Script load order (asserted on the final bytes, fail-closed) ─────────
//   save-store.js → save-boot.js → [the game's inline script] →
//   ui-enhancements.js
// The bridge/core overlay (glue.mjs, core-adapter.js, shim.js, core/) is
// gone with the fixed-scene VN conversion: the page no longer submits
// free-action turns, so window.Android is not needed at all. save-boot.js
// must precede the inline script so SaveBoot.ready is already in flight
// when the boot wrap registers. ui-enhancements.js must follow the inline
// script: it wraps the page globals (render) the inline script defines —
// the static equivalent of native's onPageFinished injection.
import { cpSync, existsSync, mkdirSync, readFileSync, readdirSync, rmSync, statSync, writeFileSync } from "node:fs";
import { createHash } from "node:crypto";
import { dirname, join, relative, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const repoRoot = join(dirname(fileURLToPath(import.meta.url)), "..");
const assetsDir = join(repoRoot, "android-apk/app/src/main/assets");
const webDir = join(repoRoot, "web");
const outDir = resolve(process.argv[2] || join(repoRoot, "site"));

function fail(msg) {
  console.error("assemble: FAIL — " + msg);
  process.exit(1);
}
function count(haystack, needle) {
  return haystack.split(needle).length - 1;
}
function swap(html, from, to, expected, label) {
  const n = count(html, from);
  if (n !== expected) fail(`patch "${label}": expected ${expected} occurrence(s), found ${n}`);
  return html.split(from).join(to);
}

// ── Cache-busting build version (owner request, 2026-11-03) ─────────────
// GitHub Pages serves every file with Cache-Control: max-age=600, so a
// phone that loaded the game just before a deploy keeps showing the old
// build for up to 10 minutes. One version for the whole web overlay —
// the sha256 of every web source file — is appended as ?v= to each
// script URL in the page and to each relative import specifier inside
// the copied module files: any change to any web file changes every
// URL, so the first load after a deploy fetches the overlay fresh.
// (The HTML shell itself can still be cached for up to 10 minutes;
// once it refreshes, nothing beneath it can be stale.)
const WEB_FILES = [
  "save-store.js", "save-boot.js", "ui-enhancements.js",
];
const buildVersion = createHash("sha256")
  .update(WEB_FILES.map((f) => f + "\0" + readFileSync(join(webDir, f), "utf8")).join("\0"))
  .digest("hex").slice(0, 12);

// ── 1. Patch the game's index.html in memory ──────────────────────────────
const sourceHtml = readFileSync(join(assetsDir, "index.html"), "utf8");
if (count(sourceHtml, "<script") !== 1 || count(sourceHtml, "</script>") !== 1) {
  fail("source index.html: expected exactly one inline <script> block");
}
let html = sourceHtml;

// Step 5 — load(): whole-body swap (before step 2, whose anchor also
// appears inside load()'s original body).
html = swap(html,
  'function load(){state=JSON.parse(localStorage.getItem("backroom-apk-state")||"null")||initial;state.characterCanon=CURRENT_CHARACTER_CANON;render()}',
  'function load(){SaveBoot.loadString().then(function(s){state=JSON.parse(s||"null")||initial;state.characterCanon=CURRENT_CHARACTER_CANON;render()})}',
  1, "load()");
// Step 6 — clearSave(): removeItem → SaveBoot.clear().
html = swap(html,
  'localStorage.removeItem("backroom-apk-state");',
  "SaveBoot.clear();",
  1, "clearSave()");
// Steps 3+4 — boot re-save and save(): both setItem calls, one swap text.
html = swap(html,
  'localStorage.setItem("backroom-apk-state",JSON.stringify(state));',
  "SaveBoot.saveString(JSON.stringify(state));",
  2, "setItem");
// Final top-level render(); — removed here, re-appended inside the wrap.
html = swap(html, "\nrender();\n</script>", "\n</script>", 1, "final render removal");
// Step 1+2 — the scoped boot wrap + boot read swap.
html = swap(html,
  'let state=JSON.parse(localStorage.getItem("backroom-apk-state")||"null")||initial;',
  "let state,busy;\n" +
  "SaveBoot.ready.then(function (savedString) {\n" +
  'state=JSON.parse(savedString||"null")||initial;',
  1, "boot wrap open + read");
html = swap(html, "let busy=false;", "busy=false;", 1, "busy hoist");
{
  // Close the wrap immediately after the boot re-save line (the only line
  // in the file consisting solely of the SaveBoot.saveString call), with
  // the boot's closing render() just inside it.
  const lines = html.split("\n");
  const idx = lines.indexOf("SaveBoot.saveString(JSON.stringify(state));");
  if (idx < 0 || lines.lastIndexOf("SaveBoot.saveString(JSON.stringify(state));") !== idx) {
    fail('patch "boot wrap close": boot re-save line not found exactly once');
  }
  lines.splice(idx + 1, 0, "render();", "});");
  html = lines.join("\n");
}
if (html.includes("localStorage")) fail("patched index.html still references localStorage");

// ── 2. Inject the web script tags and assert the final order ─────────────
const PRE = [
  `<script src="save-store.js?v=${buildVersion}"></script>`,
  `<script src="save-boot.js?v=${buildVersion}"></script>`,
].join("\n");
html = swap(html, "<script>", PRE + "\n<script>", 1, "pre-script injection");
html = swap(html, "</script>\n</body>", `</script>\n<script src="ui-enhancements.js?v=${buildVersion}"></script>\n</body>`, 1, "ui injection");
{
  const markers = [
    'src="save-store.js?v=', 'src="save-boot.js?v=',
    "<script>\n", // the game's own inline script (bare tag)
    'src="ui-enhancements.js?v=',
  ];
  let prev = -1;
  for (const m of markers) {
    if (count(html, m) !== 1) fail(`order check: marker ${m} found ${count(html, m)} time(s), expected 1`);
    const at = html.indexOf(m);
    if (at <= prev) fail(`order check: ${m} is out of order`);
    prev = at;
  }
  if (count(html, "SaveBoot.ready.then(function (savedString) {") !== 1) {
    fail("order check: boot wrap missing");
  }
}

// ── 3. Write the site ──────────────────────────────────────────────────────
rmSync(outDir, { recursive: true, force: true });
mkdirSync(outDir, { recursive: true });
cpSync(assetsDir, outDir, { recursive: true });
for (const f of WEB_FILES) {
  const dest = join(outDir, f);
  mkdirSync(dirname(dest), { recursive: true });
  cpSync(join(webDir, f), dest);
}
writeFileSync(join(outDir, "index.html"), html);

// Stamp the same ?v= onto every relative import specifier inside the
// copied module files, so a changed core module is never served stale
// behind a fresh importer (see the build-version note above).
for (const f of WEB_FILES) {
  if (!/\.m?js$/.test(f)) continue;
  const dest = join(outDir, f);
  const src = readFileSync(dest, "utf8");
  const stamped = src.replace(
    /(\bfrom\s*|\bimport\s*\(\s*|\bimport\s+)(["'])(\.\.?\/[^"']+?\.m?js)\2/g,
    (m, kw, q, spec) => kw + q + spec + "?v=" + buildVersion + q
  );
  if (stamped !== src) writeFileSync(dest, stamped);
}

// Byte-identity spot law: every copied asset except the patched
// index.html must match its source size exactly.
let files = 0, bytes = 0;
(function walk(dir) {
  for (const name of readdirSync(dir)) {
    const p = join(dir, name);
    if (statSync(p).isDirectory()) { walk(p); continue; }
    const rel = relative(assetsDir, p);
    if (rel === "index.html") continue;
    const out = join(outDir, rel);
    if (!existsSync(out) || statSync(out).size !== statSync(p).size) {
      fail(`asset copy mismatch: ${rel}`);
    }
    files++; bytes += statSync(p).size;
  }
})(assetsDir);
for (const f of WEB_FILES) {
  if (!existsSync(join(outDir, f))) fail(`web file missing from site: ${f}`);
}
console.log(`assemble: OK — ${files} asset files (${bytes} bytes) byte-sized, index.html patched, ${WEB_FILES.length} web files @ v=${buildVersion}, order verified → ${outDir}`);

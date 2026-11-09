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

// ── Build ID (owner request 2026-11-03; global scope, cluster D) ────────
// GitHub Pages serves every file with Cache-Control: max-age=600, so a
// phone that loaded the game just before a deploy keeps showing the old
// build for up to 10 minutes. ONE build ID covers the whole artifact:
// the sha256 of every build input — the game's entire assets tree (HTML
// shell with its inline CSS, story JSON, images, fonts), the web overlay
// files and this assembler itself — walked in a stable sorted order over
// real content bytes. The ID is embedded in the deployed HTML (meta +
// data-build + window.__GAME_BUILD__) and appended as ?v= to every
// resource the page can reference, so any input change changes every
// URL and a stale shell is detectable from the page itself. (The HTML
// shell can still be cached for up to 10 minutes; the ID detects that,
// it does not erase the cache. No service worker is added.)
const WEB_FILES = [
  "save-store.js", "save-boot.js", "ui-enhancements.js", "mobile-only.js",
];
function collectInputs(dir, base, out) {
  for (const name of readdirSync(dir).sort()) {
    const p = join(dir, name);
    if (statSync(p).isDirectory()) { collectInputs(p, base, out); continue; }
    out.push(relative(base, p));
  }
  return out;
}
const buildHash = createHash("sha256");
for (const rel of collectInputs(assetsDir, assetsDir, [])) {
  buildHash.update("assets/" + rel + "\0");
  buildHash.update(readFileSync(join(assetsDir, rel)));
  buildHash.update("\0");
}
for (const f of [...WEB_FILES, "assemble.mjs"]) {
  buildHash.update("web/" + f + "\0");
  buildHash.update(readFileSync(join(webDir, f)));
  buildHash.update("\0");
}
const buildVersion = buildHash.digest("hex").slice(0, 16);

// ── 1. Patch the game's index.html in memory ──────────────────────────────
const sourceHtml = readFileSync(join(assetsDir, "index.html"), "utf8");
if (count(sourceHtml, "<script") !== 1 || count(sourceHtml, "</script>") !== 1) {
  fail("source index.html: expected exactly one inline <script> block");
}
let html = sourceHtml;

// Step 5 — load(): whole-body swap (before step 2, whose anchor also
// appears inside load()'s original body).
html = swap(html,
  'function load(){const s=JSON.parse(localStorage.getItem("backroom-apk-state")||"null");state=s&&s.v===2&&Array.isArray(s.history)?s:JSON.parse(JSON.stringify(initial));state.characterCanon=CURRENT_CHARACTER_CANON;currentEntry=-1;migrateState();ensureSceneEntry();render();resetReaderScroll()}',
  'function load(){SaveBoot.loadString().then(function(s){const p=JSON.parse(s||"null");state=p&&p.v===2&&Array.isArray(p.history)?p:JSON.parse(JSON.stringify(initial));state.characterCanon=CURRENT_CHARACTER_CANON;currentEntry=-1;migrateState();ensureSceneEntry();render();resetReaderScroll()})}',
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
  'if(window.__MOBILE_WEB_ALLOWED__===false)return;\n' +
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

// Disable the web game on desktop before its first paint and before save boot.
// The shared offline APK shell has no device gate.
html = swap(html, "</head>", `<style>
html:not([data-mobile-web="allowed"]) body > :not(.mobile-required){display:none!important}
.mobile-required{display:grid;place-content:center;min-height:100vh;min-height:100dvh;margin:0;padding:24px;text-align:center;color:#fff6ca;background:#0a1012}
html[data-mobile-web="allowed"] .mobile-required{display:none}
</style>
<script src="mobile-only.js?v=${buildVersion}"></script>
</head>`, 1, "mobile-only gate");
html = swap(html, "<body>", '<body>\n<section class="mobile-required" role="status"><h1>Chỉ hỗ trợ điện thoại</h1><p>Vui lòng mở game bằng trình duyệt trên điện thoại di động.</p></section>', 1, "desktop notice");

// ── 2b. Embed the build ID and version every static reference ────────────
// The source shell carries a placeholder meta; the deployed copy carries
// the real build ID in the meta, on <html data-build>, and as
// window.__GAME_BUILD__ / window.__V — the query the page appends to
// every URL it builds at runtime (story JSON, stage backgrounds,
// entity/companion/character images, avatars, profile images). Static
// src=/url() references in the shell are stamped here directly. The APK
// copy keeps the placeholder and no __V, so its URLs stay plain local
// file names that the WebView asset loader resolves unchanged.
html = swap(html,
  '<meta name="game-build" content="unversioned">',
  `<meta name="game-build" content="${buildVersion}">\n<script>window.__GAME_BUILD__="${buildVersion}";window.__V="?v=${buildVersion}";document.documentElement.dataset.build="${buildVersion}";</script>`,
  1, "build id embed");
html = html.replace(/src="([^"?]+)"/g, (m, u) => (/^(?:https?:|data:)/.test(u) ? m : `src="${u}?v=${buildVersion}"`));
html = html.replace(/url\("([^"?]+)"\)/g, (m, u) => `url("${u}?v=${buildVersion}")`);
{
  // Consistency (cluster D4): the deployed shell must not reference any
  // relative resource without the build version, and the story JSON
  // must be routed through the page's version hook (fetch + XHR alike).
  const unstamped = [...html.matchAll(/src="([^"]+)"/g)].map((m) => m[1])
    .filter((u) => !/^(?:https?:|data:)/.test(u) && !u.includes("?v=" + buildVersion));
  if (unstamped.length) fail("build id: unstamped resource reference(s): " + unstamped.join(", "));
  if (count(html, 'story/opening-chapter.json"+V') !== 2) {
    fail("build id: story JSON is not routed through the version hook");
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

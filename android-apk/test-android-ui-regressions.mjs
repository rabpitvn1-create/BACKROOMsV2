import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const input = process.argv[2];
if (!input) throw new Error("Usage: node test-android-ui-regressions.mjs <index.html>");
const html = readFileSync(input, "utf8");

function count(needle) {
  return html.split(needle).length - 1;
}

function requireText(needle, label = needle) {
  assert.ok(html.includes(needle), `Missing Android UI contract: ${label}`);
}

requireText("STORYTELLING_SINGLE_FRAME_V3");
requireText("function storytellingViewport(log)");
requireText("function storytellingSurface(log)");
requireText("function markGameMasterSegment(message)");
requireText("function positionStorytellingSurface(log,surface)");
requireText("viewport.insertBefore(article,log)", "Storytelling surface is fixed outside the scrolling log");
requireText("message.classList.add('storytelling-segment')", "GM messages remain in transcript order");
requireText(":scope > .message.storytelling-segment", "only direct GM message children join Storytelling");
requireText(".storytelling-viewport{position:relative;flex:1 1 auto;min-height:0;overflow:hidden}", "fixed Storytelling viewport");
requireText(".storytelling-surface{position:absolute;z-index:0;inset:5px", "fixed rectangular Storytelling frame");
requireText(".storytelling-viewport>.log{position:relative;z-index:1;width:100%;height:100%;min-height:0;overflow-y:auto", "only log content scrolls");
assert.equal(count("function storytellingSurface(log)"), 1, "Exactly one Storytelling frame authority must be packaged");
assert.ok(!html.includes("surface.appendChild(message)"), "Messages must never be moved inside Storytelling");
assert.ok(!html.includes("body.appendChild(segment)"), "Legacy GM regrouping must not survive");
assert.ok(!html.includes("surface.style.top="), "Storytelling frame must not follow scroll content");
assert.ok(!html.includes("surface.style.height="), "Storytelling frame height must not follow message positions");
assert.ok(!html.includes("while(i<children.length&&roleOf(children[i])==='GAME MASTER'"), "Consecutive-only GM grouping must not survive");

for (const rule of [
  ".message.storytelling-segment{",
  ".message.player,.message.player .text,.status{font-family:var(--gameplay-font);font-weight:400}",
  ".message.combat,.message.combat .role,.message.combat .text{font-family:var(--gameplay-font);font-weight:400}",
  "#combatHud{",
  "#combatPopup{",
]) requireText(rule);

const canonicalStyle = html.match(/<style id="androidEdgeUiStyle">([\s\S]*?)<\/style>/)?.[1] ?? "";
assert.ok(canonicalStyle, "Canonical Android style must be packaged");
for (const selector of [".message.storytelling-segment", ".message.player", ".message.combat", "#combatHud", "#combatPopup", ".status"]) {
  const escaped = selector.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const rules = Array.from(canonicalStyle.matchAll(new RegExp(`${escaped}[^\\{]*\\{[^}]*\\}`, "g")), match => match[0]);
  assert.ok(rules.some(rule => rule.includes("font-weight:400")) || selector === ".status", `${selector} must resolve to normal weight`);
}
assert.ok(!/storytelling[^{}]*\{[^}]*font-weight:800/i.test(canonicalStyle), "Storytelling prose must not be forced to 800");
assert.ok(!/(?:message\.combat|#combatHud|#combatPopup)[^{}]*\{[^}]*font-weight:800/i.test(canonicalStyle), "Combat prose/HUD must not be forced to 800");

requireText("ANDROID_SWIPE_GESTURE_V2");
requireText("touch-action:pan-y", "vertical native scrolling remains enabled");
requireText("#managementPage{overflow-y:auto", "Management vertical scrolling remains enabled");
requireText("if(axis==='x'&&e.cancelable)e.preventDefault()", "preventDefault is horizontal-only");
requireText("'pointermove'", "pointer move path");
requireText("'touchmove'", "touch fallback move path");
requireText("{passive:false}", "non-passive horizontal move listener");
requireText("textarea,input,select,button,a,.equipment-detail-modal", "interactive controls are excluded");

const finishSwipe = html.match(/function finishSwipe\(dx,dy\)\{[^}]+\}/)?.[0];
assert.ok(finishSwipe, "Swipe completion state machine must be packaged");
const simulate = new Function(`${finishSwipe}; return function(initial,dx,dy){let result=initial; page=initial; setPage=function(next){result=next>0?1:0;page=result;}; finishSwipe(dx,dy); return result;}`);
let page = 0;
let setPage = () => {};
const swipe = simulate();
assert.equal(swipe(0, -60, 3), 1, "left swipe must change Gameplay to Management");
assert.equal(swipe(1, 60, 3), 0, "right swipe must change Management to Gameplay");
assert.equal(swipe(0, 10, 1), 0, "sub-threshold movement must not change page");
assert.equal(swipe(0, -60, 90), 0, "vertical intent must not change page");

console.log(`Android UI regression contracts passed for ${input}`);

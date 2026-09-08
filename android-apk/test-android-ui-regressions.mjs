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

requireText("STORYTELLING_SINGLE_FRAME_V4");
requireText("function storytellingLayout(log)");
requireText("function markGameMasterSegment(message)");
requireText("panel.appendChild(header);panel.appendChild(body)", "Storytelling header stays outside its scrolling body");
requireText("log.appendChild(panel);log.appendChild(external)", "Storytelling and external transcript are separate siblings");
requireText("message.classList.add('storytelling-segment')", "GM messages remain in transcript order");
requireText("layout.body.appendChild(message);gmMoved=true;return", "only GM messages enter Storytelling body");
requireText("layout.external.appendChild(message);externalMoved=true", "player and combat messages stay outside Storytelling");
requireText(".log{flex:1 1 auto;height:auto;min-height:0;overflow:hidden", "outer log cannot leak text");
requireText(".storytelling-panel{flex:1 1 auto;min-height:0;", "fixed Storytelling panel");
requireText("background:#171e23;box-shadow:none;overflow:hidden;display:flex;flex-direction:column", "Storytelling clips both edges");
requireText(".storytelling-body{flex:1 1 auto;min-height:0;overflow-y:auto", "only GM body scrolls");
requireText(".external-transcript{flex:0 1 auto;", "player/combat transcript is outside the panel");
assert.equal(count("function storytellingLayout(log)"), 1, "Exactly one Storytelling layout authority must be packaged");
assert.ok(!html.includes("storytelling-surface"), "Obsolete underlay Storytelling surface must not survive");
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

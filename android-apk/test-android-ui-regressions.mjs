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

requireText("STORYTELLING_CHRONOLOGICAL_FRAME_V5");
requireText("function storytellingLayout(log)");
requireText("function markTranscriptEntry(message)");
requireText("panel.appendChild(header);panel.appendChild(body)", "Storytelling header stays outside its scrolling body");
requireText("panel.appendChild(header);panel.appendChild(body);log.appendChild(panel)", "exactly one transcript panel is attached to the log");
requireText("message.classList.add('storytelling-entry')", "every message receives the flat transcript presentation");
requireText("layout.body.appendChild(message);moved=true", "GM, player, and combat messages share chronological order");
requireText(".log{flex:1 1 auto;height:auto;min-height:0;overflow:hidden", "outer log cannot leak text");
requireText(".storytelling-panel{flex:1 1 auto;min-height:0;", "fixed Storytelling panel");
requireText("background:#171e23;box-shadow:none;overflow:hidden;display:flex;flex-direction:column", "Storytelling clips both edges");
requireText(".storytelling-body{flex:1 1 auto;min-height:0;overflow-x:hidden;overflow-y:auto", "only transcript body scrolls");
requireText(".message.storytelling-entry>.role::before{content:'\\2022\\00a0'}", "each transcript line starts with a bullet");
requireText(".message.storytelling-entry>.role::after{content:':\\00a0'}", "each visible speaker label ends with a colon");
assert.equal(count("function storytellingLayout(log)"), 1, "Exactly one Storytelling layout authority must be packaged");
assert.ok(!html.includes("storytelling-surface"), "Obsolete underlay Storytelling surface must not survive");
assert.ok(!html.includes("external-transcript"), "Separate transcript must not squeeze the Combat HUD");
assert.ok(!html.includes("while(i<children.length&&roleOf(children[i])==='GAME MASTER'"), "Consecutive-only GM grouping must not survive");

for (const rule of [
  ".message.storytelling-entry{",
  ".message.player,.message.player .text,.status{font-family:var(--gameplay-font);font-weight:400}",
  ".message.combat,.message.combat .role,.message.combat .text{font-family:var(--gameplay-font);font-weight:400}",
  "#combatHud{",
  "#combatPopup{",
]) requireText(rule);

const canonicalStyle = html.match(/<style id="androidEdgeUiStyle">([\s\S]*?)<\/style>/)?.[1] ?? "";
assert.ok(canonicalStyle, "Canonical Android style must be packaged");
for (const selector of [".message.storytelling-entry", ".message.player", ".message.combat", "#combatHud", "#combatPopup", ".status"]) {
  const escaped = selector.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const rules = Array.from(canonicalStyle.matchAll(new RegExp(`${escaped}[^\\{]*\\{[^}]*\\}`, "g")), match => match[0]);
  assert.ok(rules.some(rule => rule.includes("font-weight:400")) || selector === ".status", `${selector} must resolve to normal weight`);
}
assert.ok(!/storytelling[^{}]*\{[^}]*font-weight:800/i.test(canonicalStyle), "Storytelling prose must not be forced to 800");
assert.ok(!/(?:message\.combat|#combatHud|#combatPopup)[^{}]*\{[^}]*font-weight:800/i.test(canonicalStyle), "Combat prose/HUD must not be forced to 800");

requireText("if(submitEl)submitEl.disabled=busy||!hasText", "typed Execute remains available during active combat");
requireText("if(busy||(state&&state.combat&&state.combat.active===true))return", "navigation macros remain locked during active combat");
assert.ok(!html.includes("if(!a||busy||(state&&state.combat&&state.combat.active===true))return"), "typed combat submit must reach CombatRuntime");
assert.ok(!html.includes("if(actionEl)actionEl.disabled=combatLocked"), "combat must not disable the typed action field");

requireText("TRUE_TURN_AUTOPLAY_V3", "single authoritative combat overlay scheduler");
requireText("var STEP_MS=2000", "every authoritative combat subturn is displayed for two seconds");
requireText("queueSubmit(inFlight||busy?250:STEP_MS)", "next subturn waits for the stable two-second display window");
requireText("function actorVisualMatches(box,img,p)", "same target overlay remains mounted across the attack/response pair");
requireText("combatPendingActorId", "an in-progress overlay transition is deduplicated");
requireText("function syncTurnHeader(c)", "gameplay Turn and Combat Round use separate header states");
requireText("var label=c?'COMBAT • ROUND ':'TURN '", "combat subturns do not masquerade as gameplay turns");
assert.ok(!html.includes("queue(80)"), "legacy 80ms overlay rescheduling must not survive");
const actorSwapScript = html.match(/\/\* COMBAT_ACTOR_SWAP_V1 \*\/([\s\S]*?)<\/script>/)?.[1] ?? "";
assert.ok(actorSwapScript, "Combat actor transition script must be packaged");
assert.ok(!actorSwapScript.includes("var oldRender=window.render"), "legacy render overlay owner must be removed");
assert.ok(!actorSwapScript.includes("var oldTurn=window.backroomTurn"), "legacy turn overlay owner must be removed");

requireText("COMBAT_HIT_VFX_V1", "classic RPG hit feedback is packaged");
requireText("window.captureCombatHitState=function()", "hit VFX snapshots authoritative HP before/after each true-turn subturn");
requireText("window.captureCombatHitGhost=function(actorId)", "hit VFX freezes the correct target sprite before actor swapping");
requireText("window.combatHitDamageFor=function(before,after,actorId)", "hit VFX damage derives from state deltas");
requireText("window.playCombatHitFromTransition=function(before,after,actorId,ghost)", "true-turn transition drives hit presentation");
requireText("combat-hit-impact", "target receives flash and pixel recoil");
requireText("combat-hit-number", "floating RPG damage number is packaged");
requireText("Math.max(0,Math.round(oldHp-newHp))", "damage presentation uses authoritative HP delta");
requireText("var hitBefore=(inFlight&&typeof window.captureCombatHitState==='function')", "hit snapshot is limited to authoritative autoplay submissions");
const hitVfxScript = html.match(/<script>\s*\/\* COMBAT_HIT_VFX_V1 \*\/([\s\S]*?)<\/script>/)?.[1] ?? "";
assert.ok(hitVfxScript, "Combat Hit VFX script must be packaged");
assert.ok(!hitVfxScript.includes("combat-bar"), "hit VFX must not create a duplicate HP bar");
new Function(hitVfxScript);

requireText("ANDROID_SWIPE_GESTURE_V2");
requireText("touch-action:pan-y", "vertical native scrolling remains enabled");
requireText("#managementPage{overflow-y:auto", "Management vertical scrolling remains enabled");
requireText("--android-ime-bottom:0px", "IME inset has a deterministic CSS fallback");
requireText("height:calc(100dvh - var(--android-ime-bottom));min-height:0", "keyboard inset shrinks the fixed app shell");
assert.ok(!canonicalStyle.includes(".shell{width:100%;height:100dvh;min-height:100dvh"), "full-height shell must not trap the composer behind the keyboard");
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
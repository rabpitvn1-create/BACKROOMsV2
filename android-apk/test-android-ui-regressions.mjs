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
requireText("COMBAT_HIT_POST_RENDER_V2", "hit VFX survives the native Snapshot redraw that runs after the true-turn callback");
requireText("window.captureCombatHitState=function()", "hit VFX snapshots authoritative HP before/after each true-turn subturn");
requireText("window.captureCombatHitGhost=function(actorId)", "hit VFX freezes the correct target sprite before actor swapping");
requireText("window.combatHitDamageFor=function(before,after,actorId)", "hit VFX damage derives from state deltas");
requireText("window.playCombatHitFromTransition=function(before,after,actorId,ghost)", "true-turn transition drives hit presentation");
requireText("function mountCombatHit(ghost,amount)", "hit presentation is mounted in a dedicated post-render step");
requireText("window.requestAnimationFrame(mount)", "hit presentation waits until synchronous native Snapshot redraws finish");
requireText("combat-hit-impact", "target receives flash and pixel recoil");
requireText("combat-hit-number", "floating RPG damage number is packaged");
requireText("var avatar=String(m.avatar||m.avatarRef||'').trim()", "Party projection preserves each member's canonical avatar");
requireText("targetMember?String(targetMember.overlayUri||''):''", "Entity response uses the encoded target member's avatar fallback");
requireText("member.overlayUri=uri||String(member.overlayUri||'')", "packaged combat overlays take precedence over canonical avatars");
requireText("Math.max(0,Math.round(oldHp-newHp))", "damage presentation uses authoritative HP delta");
requireText("var hitBefore=(inFlight&&typeof window.captureCombatHitState==='function')", "hit snapshot is limited to authoritative autoplay submissions");
const hitVfxScript = html.match(/<script>\s*\/\* COMBAT_HIT_VFX_V1 \/ COMBAT_HIT_POST_RENDER_V2 \*\/([\s\S]*?)<\/script>/)?.[1] ?? "";
assert.ok(hitVfxScript, "Combat Hit VFX script must be packaged");
assert.ok(!hitVfxScript.includes("combat-bar"), "hit VFX must not create a duplicate HP bar");
assert.ok(!hitVfxScript.includes("kai_entity_overlay.png"), "generic hit VFX must not substitute Kai's sprite");
assert.ok(hitVfxScript.indexOf("window.requestAnimationFrame(mount)") > hitVfxScript.indexOf("var amount=window.combatHitDamageFor"), "hit VFX must defer mounting until after damage is resolved");
new Function(hitVfxScript);
const hitWindow = {};
new Function("window", "state", hitVfxScript)(hitWindow, {});
const hitBefore = {
  playerHp: 100,
  entityHp: 500,
  active: true,
  party: {
    lucia: { hp: 90 },
    syvial: { hp: 80 },
    iris: { hp: 70 },
    "party-four": { hp: 60 },
  },
};
const hitAfter = {
  playerHp: 93,
  entityHp: 488,
  active: true,
  party: {
    lucia: { hp: 82 },
    syvial: { hp: 71 },
    iris: { hp: 60 },
    "party-four": { hp: 49 },
  },
};
for (const [actorId, expected] of [
  ["entity:kai", 7],
  ["entity:lucia", 8],
  ["entity:syvial", 9],
  ["entity:iris", 10],
  ["entity:party-four", 11],
]) {
  assert.equal(hitWindow.combatHitDamageFor(hitBefore, hitAfter, actorId), expected, `${actorId} must read the matching Party HP delta`);
}
assert.equal(hitWindow.combatHitDamageFor(hitBefore, hitAfter, "iris"), 12, "member attacks must read Entity HP delta");

requireText("ANDROID_SWIPE_GESTURE_V2");
requireText("touch-action:pan-y", "vertical native scrolling remains enabled");
requireText("#managementPage{overflow-y:auto", "Management vertical scrolling remains enabled");
requireText("--android-ime-bottom:0px", "IME inset has a deterministic CSS fallback");
requireText("height:calc(100dvh - var(--android-ime-bottom));min-height:0", "keyboard inset shrinks the fixed app shell");
requireText("window.syncAndroidImeViewport=schedule", "native IME inset changes can resync the WebView shell");
requireText("function chooseUsableHeight(inner,visual,ime,baseline)", "usable height handles both overlay and resize IME modes");
requireText("if(ime>0&&baseline>ime+120)", "live native IME inset always constrains the shell");
requireText("if(ime<1&&baseline>0)return baseline", "IME dismissal immediately restores the keyboard-free baseline");
requireText("[60,220,500,900]", "IME close animation gets a delayed restoration pass");
assert.ok(!html.includes("function effectiveIme(nativeValue,inner,visual,baseline)"), "legacy stale-native-inset heuristic must not survive");
assert.ok(!canonicalStyle.includes(".shell{width:100%;height:100dvh;min-height:100dvh"), "full-height shell must not trap the composer behind the keyboard");

const usableHeightSource = html.match(/function chooseUsableHeight\(inner,visual,ime,baseline\)\{[\s\S]*?\n  \}/)?.[0];
assert.ok(usableHeightSource, "usable height resolver must be packaged");
const chooseUsableHeight = new Function(`${usableHeightSource}; return chooseUsableHeight;`)();
assert.equal(chooseUsableHeight(800, 800, 320, 800), 480, "overlay keyboard must honor native IME inset even when both WebView viewports stay full");
assert.equal(chooseUsableHeight(800, 480, 320, 800), 480, "visualViewport keyboard mode must constrain the shell to the visible viewport");
assert.equal(chooseUsableHeight(480, 480, 320, 800), 480, "adjustResize must not double-shrink an already reduced viewport");
assert.equal(chooseUsableHeight(480, 800, 0, 800), 800, "dismissed keyboard must restore full height when only visualViewport has recovered");
assert.equal(chooseUsableHeight(480, 480, 0, 800), 800, "dismissed keyboard must restore the baseline even before stale WebView viewport metrics recover");

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

requireText("window.combatHitDeltasFor=function(before,after,actorId)", "hit VFX derives all target deltas from one authoritative transition");
requireText("window.playCombatHitsFromTransition=function(before,after,actorId,ghosts)", "hit VFX can present reciprocal damage in the same transition");
assert.equal(typeof hitWindow.combatHitDeltasFor, "function", "damage-delta helper must compile and export");

const reciprocalBefore = {
  playerHp: 100,
  entityHp: 500,
  active: true,
  party: { iris: { hp: 70 }, syvial: { hp: 80 }, lucia: { hp: 90 } },
};
assert.deepEqual(
  hitWindow.combatHitDeltasFor(
    reciprocalBefore,
    { ...reciprocalBefore, entityHp: 488, party: { iris: { hp: 70 }, syvial: { hp: 80 }, lucia: { hp: 90 } } },
    "entity:iris",
  ),
  [{ kind: "entity", targetId: "entity", damage: 12 }],
  "entity:iris counter damage must flash/recoil the Entity when Iris HP is unchanged",
);
assert.deepEqual(
  hitWindow.combatHitDeltasFor(
    reciprocalBefore,
    { ...reciprocalBefore, entityHp: 491, party: { iris: { hp: 70 }, syvial: { hp: 80 }, lucia: { hp: 90 } } },
    "entity:syvial",
  ),
  [{ kind: "entity", targetId: "entity", damage: 9 }],
  "entity:syvial Counterphase damage must target the Entity presentation",
);
assert.deepEqual(
  hitWindow.combatHitDeltasFor(
    reciprocalBefore,
    { ...reciprocalBefore, party: { iris: { hp: 70 }, syvial: { hp: 71 }, lucia: { hp: 90 } } },
    "entity:syvial",
  ),
  [{ kind: "party", targetId: "syvial", damage: 9 }],
  "ordinary Entity damage must still target the encoded Party member",
);
assert.deepEqual(
  hitWindow.combatHitDeltasFor(
    reciprocalBefore,
    { ...reciprocalBefore, entityHp: 488 },
    "iris",
  ),
  [{ kind: "entity", targetId: "entity", damage: 12 }],
  "ordinary Party damage must still target the Entity",
);
assert.deepEqual(
  hitWindow.combatHitDeltasFor(
    reciprocalBefore,
    { ...reciprocalBefore, entityHp: 488, party: { iris: { hp: 70 }, syvial: { hp: 71 }, lucia: { hp: 90 } } },
    "entity:syvial",
  ),
  [
    { kind: "party", targetId: "syvial", damage: 9 },
    { kind: "entity", targetId: "entity", damage: 12 },
  ],
  "one authoritative transition may present both Party and Entity damage",
);
assert.deepEqual(
  hitWindow.combatHitDeltasFor(reciprocalBefore, reciprocalBefore, "entity:iris"),
  [],
  "a true miss with unchanged HP must not emit hit VFX",
);
assert.ok(!hitVfxScript.includes("combat-bar"), "reciprocal hit VFX must not create Combat HP bars");

console.log(`Android UI regression contracts passed for ${input}`);

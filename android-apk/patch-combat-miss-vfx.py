from pathlib import Path

ROOT = Path(__file__).resolve().parent
INDEX = ROOT / "app/src/main/assets/index.html"
UI_TEST = ROOT / "test-android-ui-regressions.mjs"


def replace_once(source: str, old: str, new: str, label: str) -> str:
    if new in source:
        return source
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return source.replace(old, new, 1)


# Presentation-only MISS feedback layered after the authoritative reciprocal
# Combat Hit VFX. A MISS is derived from the primary target HP delta of the
# completed AUTO_COMBAT_STEP; no combat math, state, turn ownership or prose is parsed.
html = INDEX.read_text(encoding="utf-8")
for marker in (
    "COMBAT_HIT_VFX_V1",
    "COMBAT_HIT_POST_RENDER_V2",
    "window.combatHitDeltasFor=function(before,after,actorId)",
    "window.playCombatHitsFromTransition=function(before,after,actorId,ghosts)",
):
    if marker not in html:
        raise RuntimeError("Combat MISS VFX requires final reciprocal hit authority: " + marker)

miss_style = r'''<style id="combatMissVfxStyle">
/* COMBAT_MISS_VFX_V1 */
.snapshot .combat-miss-text{position:absolute;z-index:16;pointer-events:none;white-space:nowrap;font-family:var(--gameplay-font);font-weight:800;font-size:clamp(18px,5vw,29px);line-height:1;letter-spacing:.08em;color:#d9e0e5;text-shadow:0 2px 0 #000,1px 0 0 #000,-1px 0 0 #000,0 -1px 0 #000;transform:translate(-50%,-50%);animation:combatMissText .72s cubic-bezier(.15,.78,.25,1) both}
@keyframes combatMissText{
  0%{opacity:0;transform:translate(-50%,-34%) scale(.72)}
  16%{opacity:1;transform:translate(-50%,-52%) scale(1.16)}
  34%{opacity:1;transform:translate(-50%,-62%) scale(1)}
  74%{opacity:1;transform:translate(-50%,-92%) scale(1)}
  100%{opacity:0;transform:translate(-50%,-122%) scale(.96)}
}
@media(prefers-reduced-motion:reduce){.snapshot .combat-miss-text{animation:combatMissTextReduced .42s linear both}}
@keyframes combatMissTextReduced{0%{opacity:0}18%{opacity:1}75%{opacity:1}100%{opacity:0}}
</style>
'''

miss_script = r'''<script>
/* COMBAT_MISS_VFX_V1 */
(function(){
  if(window.__combatMissVfxV1)return;window.__combatMissVfxV1=true;

  function canonicalId(raw){
    var id=String(raw||'').trim().toLocaleLowerCase('vi-VN');
    if(id==='kai'||id.indexOf('kai akechi')>=0)return 'kai';
    if(id.indexOf('lucia')>=0||id==='lục'||id==='luc')return 'lucia';
    if(id.indexOf('syvial')>=0)return 'syvial';
    if(id.indexOf('iris')>=0)return 'iris';
    return id;
  }
  function finiteNumber(value){var n=Number(value);return Number.isFinite(n)?n:null;}
  function hpFor(snapshot,id){
    if(!snapshot)return null;
    id=canonicalId(id);
    if(id==='kai')return finiteNumber(snapshot.playerHp);
    var member=snapshot.party&&snapshot.party[id];return member?finiteNumber(member.hp):null;
  }
  window.combatMissFor=function(before,after,actorId){
    if(!before||!after)return null;
    actorId=String(actorId||(before&&before.actorId)||'');
    if(!actorId)return null;
    var oldHp,newHp,targetId;
    if(actorId.indexOf('entity:')===0){
      targetId=canonicalId(actorId.slice(7));
      oldHp=hpFor(before,targetId);newHp=hpFor(after,targetId);
      if(oldHp==null||newHp==null||oldHp<=0)return null;
      return Math.max(0,Math.round(oldHp-newHp))===0?{kind:'party',targetId:targetId}:null;
    }
    oldHp=finiteNumber(before.entityHp);newHp=finiteNumber(after.entityHp);
    if(oldHp==null||newHp==null||oldHp<=0)return null;
    return Math.max(0,Math.round(oldHp-newHp))===0?{kind:'entity',targetId:'entity'}:null;
  };
  function ghostPoint(ghost,kind){
    var box=document.getElementById('snapshot');
    var x=ghost?Number(ghost.dataset.hitX):NaN,y=ghost?Number(ghost.dataset.hitY):NaN;
    if(!Number.isFinite(x))x=box?box.clientWidth*.5:0;
    if(!Number.isFinite(y))y=box?box.clientHeight*(kind==='party'?.42:.34):0;
    return {x:x,y:y};
  }
  function mountCombatMiss(point){
    var box=document.getElementById('snapshot');if(!box)return;
    var node=document.createElement('div');node.className='combat-miss-text';node.setAttribute('aria-hidden','true');node.textContent='MISS';
    node.style.left=Number(point&&point.x||box.clientWidth*.5)+'px';
    node.style.top=Number(point&&point.y||box.clientHeight*.4)+'px';
    box.appendChild(node);window.setTimeout(function(){if(node.parentNode)node.remove();},900);
  }

  var previous=window.playCombatHitsFromTransition;
  if(typeof previous!=='function')return;
  window.playCombatHitsFromTransition=function(before,after,actorId,ghosts){
    actorId=String(actorId||(before&&before.actorId)||'');ghosts=ghosts||{};
    var miss=window.combatMissFor(before,after,actorId);
    var missGhost=miss?(miss.kind==='party'?ghosts.party:ghosts.entity):null;
    var point=miss?ghostPoint(missGhost,miss.kind):null;
    var result=previous.apply(this,arguments);
    if(miss){
      var mount=function(){mountCombatMiss(point);};
      if(typeof window.requestAnimationFrame==='function')window.requestAnimationFrame(mount);else window.setTimeout(mount,0);
    }
    return result||!!miss;
  };
})();
</script>
'''

if "COMBAT_MISS_VFX_V1" not in html:
    if "</head>" not in html or "</body>" not in html:
        raise RuntimeError("Combat MISS VFX insertion anchors missing")
    html = html.replace("</head>", miss_style + "</head>", 1)
    html = html.replace("</body>", miss_script + "</body>", 1)

for marker in (
    "COMBAT_MISS_VFX_V1",
    "combat-miss-text",
    "window.combatMissFor=function(before,after,actorId)",
    "var previous=window.playCombatHitsFromTransition",
    "var result=previous.apply(this,arguments)",
    "node.textContent='MISS'",
    "window.requestAnimationFrame(mount)",
):
    if marker not in html:
        raise RuntimeError("Combat MISS VFX marker missing: " + marker)
miss_section = html[html.index("/* COMBAT_MISS_VFX_V1 */", html.index("<script>")):]
if "combat-bar" in miss_section[:miss_section.index("</script>")]:
    raise RuntimeError("Combat MISS VFX must not create a second HP bar")
INDEX.write_text(html, encoding="utf-8")

# Focused generated-HTML regression. The main UI harness already runs after the
# complete patch chain; inject the MISS assertions there so CI validates the final APK input.
test = UI_TEST.read_text(encoding="utf-8")
miss_test = r'''
requireText("COMBAT_MISS_VFX_V1", "floating MISS feedback is packaged");
requireText("combat-miss-text", "MISS feedback has its own presentation class");
requireText("window.combatMissFor=function(before,after,actorId)", "MISS feedback derives from authoritative primary-target HP deltas");
const missVfxScript = html.match(/<script>\s*\/\* COMBAT_MISS_VFX_V1 \*\/([\s\S]*?)<\/script>/)?.[1] ?? "";
assert.ok(missVfxScript, "Combat MISS VFX script must be packaged");
new Function(missVfxScript);
const missWindow = {};
new Function("window", missVfxScript)(missWindow);
assert.equal(typeof missWindow.combatMissFor, "function", "MISS helper must compile and export");
const missBefore = {
  playerHp: 100,
  entityHp: 500,
  active: true,
  party: { lucia: { hp: 90 }, syvial: { hp: 80 } },
};
assert.deepEqual(
  missWindow.combatMissFor(missBefore, { ...missBefore, entityHp: 500 }, "lucia"),
  { kind: "entity", targetId: "entity" },
  "Party attack with zero Entity HP delta must show MISS on the Entity",
);
assert.deepEqual(
  missWindow.combatMissFor(missBefore, { ...missBefore, party: { lucia: { hp: 90 }, syvial: { hp: 80 } } }, "entity:lucia"),
  { kind: "party", targetId: "lucia" },
  "Entity attack with zero target HP delta must show MISS on that Party member",
);
assert.deepEqual(
  missWindow.combatMissFor(missBefore, { ...missBefore, entityHp: 491, party: { lucia: { hp: 90 }, syvial: { hp: 80 } } }, "entity:syvial"),
  { kind: "party", targetId: "syvial" },
  "Entity miss must still show MISS when a counter damages the Entity in the same transition",
);
assert.equal(
  missWindow.combatMissFor(missBefore, { ...missBefore, entityHp: 488 }, "lucia"),
  null,
  "A successful Party hit must not also show MISS",
);
assert.equal(
  missWindow.combatMissFor(missBefore, { ...missBefore, party: { lucia: { hp: 82 }, syvial: { hp: 80 } } }, "entity:lucia"),
  null,
  "A successful Entity hit must not also show MISS",
);

'''
if "floating MISS feedback is packaged" not in test:
    anchor = 'requireText("ANDROID_SWIPE_GESTURE_V2");\n'
    if anchor not in test:
        raise RuntimeError("Android UI regression insertion anchor missing for Combat MISS VFX")
    test = test.replace(anchor, miss_test + anchor, 1)
for marker in (
    'requireText("COMBAT_MISS_VFX_V1", "floating MISS feedback is packaged")',
    'missWindow.combatMissFor(missBefore',
    '"Entity miss must still show MISS when a counter damages the Entity in the same transition"',
    '"A successful Entity hit must not also show MISS"',
):
    if marker not in test:
        raise RuntimeError("Combat MISS VFX regression marker missing: " + marker)
UI_TEST.write_text(test, encoding="utf-8")

print("Combat MISS VFX V1 installed: zero primary-target HP delta shows floating MISS without changing combat state or existing -DMG hit feedback.")

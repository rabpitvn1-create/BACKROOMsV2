from pathlib import Path
import runpy

ROOT = Path(__file__).resolve().parent
COMBAT = ROOT / "app/src/main/java/com/rabpit/backroom/core/CombatRuntime.kt"
INDEX = ROOT / "app/src/main/assets/index.html"


def replace_once(source: str, old: str, new: str, label: str) -> str:
    if new in source:
        return source
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return source.replace(old, new, 1)


# patch-lucia-combat-evasion-compat.py runs earlier in the runtime patch chain and
# expands Lucia's hit gate. The true-turn finalizer deliberately starts from the
# original two-line Lucia attack anchor so it can move that attack out of Kai's
# local hitChance scope. Temporarily normalize the gate, run the finalizer, then
# restore the existing 25% Entity-evasion rule against Lucia's independent chance.
combat = COMBAT.read_text(encoding="utf-8")

evasion_gate = '''          val luciaRoll = roll(c.copy(eventCounter = c.eventCounter + 83), 100)
          val luciaEvasionRoll = roll(c.copy(eventCounter = c.eventCounter + 97), 100)
          val luciaEntityEvaded = luciaEvasionRoll < ENTITY_EVASION_PERCENT
          if (luciaRoll < hitChance && !luciaEntityEvaded) {
'''
baseline_gate = '''          val luciaRoll = roll(c.copy(eventCounter = c.eventCounter + 83), 100)
          if (luciaRoll < hitChance) {
'''

if "TRUE_TURN_COMBAT_V2" not in combat:
    combat = replace_once(combat, evasion_gate, baseline_gate, "normalize Lucia evasion gate before true-turn split")
    COMBAT.write_text(combat, encoding="utf-8")

runpy.run_path(str(ROOT / "patch-true-turn-combat-final.py"), run_name="__main__")

combat = COMBAT.read_text(encoding="utf-8")
true_turn_gate = '''          val luciaRangeBonus = when (c.range) { RangeBand.CLOSE -> 18; RangeBand.NEAR -> 10; RangeBand.FAR -> -5 }
          val luciaHitChance = (58 + luciaRangeBonus + c.opening * 11 + c.momentum * 6).coerceIn(20, 96)
          val luciaRoll = roll(c.copy(eventCounter = c.eventCounter + 83), 100)
          if (luciaRoll < luciaHitChance) {
'''
true_turn_evasion_gate = '''          val luciaRangeBonus = when (c.range) { RangeBand.CLOSE -> 18; RangeBand.NEAR -> 10; RangeBand.FAR -> -5 }
          val luciaHitChance = (58 + luciaRangeBonus + c.opening * 11 + c.momentum * 6).coerceIn(20, 96)
          val luciaRoll = roll(c.copy(eventCounter = c.eventCounter + 83), 100)
          val luciaEvasionRoll = roll(c.copy(eventCounter = c.eventCounter + 97), 100)
          val luciaEntityEvaded = luciaEvasionRoll < ENTITY_EVASION_PERCENT
          if (luciaRoll < luciaHitChance && !luciaEntityEvaded) {
'''
combat = replace_once(combat, true_turn_gate, true_turn_evasion_gate, "restore Lucia evasion after true-turn split")

for marker in (
    "TRUE_TURN_COMBAT_V2",
    "val luciaHitChance = (58 + luciaRangeBonus + c.opening * 11 + c.momentum * 6).coerceIn(20, 96)",
    "val luciaEntityEvaded = luciaEvasionRoll < ENTITY_EVASION_PERCENT",
    "if (luciaRoll < luciaHitChance && !luciaEntityEvaded)",
):
    if marker not in combat:
        raise RuntimeError("True-turn Lucia evasion compatibility marker missing: " + marker)

COMBAT.write_text(combat, encoding="utf-8")

# ---------------------------------------------------------------------------
# COMBAT HIT VFX V1
#
# Presentation-only hit feedback. The authoritative true-turn state exposes one
# attacker per AUTO_COMBAT_STEP, so the WebView can compare HP before/after that
# single subturn without parsing combat prose or mutating gameplay state.
#
# A frozen clone of the current target is captured before backroomTurn updates
# the DOM. This keeps the hit reaction attached to the correct Kai/Lucia/Syvial
# or Entity sprite even when the next true-turn actor begins swapping in.
# ---------------------------------------------------------------------------
html = INDEX.read_text(encoding="utf-8")
if "TRUE_TURN_AUTOPLAY_V3" not in html:
    raise RuntimeError("Combat hit VFX requires TRUE_TURN_AUTOPLAY_V3")

hit_style = r'''<style id="combatHitVfxStyle">
/* COMBAT_HIT_VFX_V1 */
.snapshot{isolation:isolate}
.snapshot .combat-hit-ghost{position:absolute!important;pointer-events:none!important;z-index:14!important;max-width:none!important;max-height:none!important;margin:0!important;transform-origin:center center!important;will-change:transform,filter,opacity}
.snapshot .combat-hit-ghost.combat-hit-impact{animation:combatHitImpact .24s steps(1,end) both}
.snapshot .combat-hit-number{position:absolute;z-index:15;pointer-events:none;white-space:nowrap;font-family:var(--gameplay-font);font-weight:800;font-size:clamp(19px,5.2vw,30px);line-height:1;color:#fff;text-shadow:0 2px 0 #000,1px 0 0 #000,-1px 0 0 #000,0 -1px 0 #000;transform:translate(-50%,-50%);animation:combatHitNumber .68s cubic-bezier(.15,.78,.25,1) .07s both}
@keyframes combatHitImpact{
  0%{opacity:1;transform:translateX(0) scale(1);filter:none}
  12%{opacity:1;transform:translateX(0) scale(1.01);filter:brightness(0) invert(1)}
  28%{opacity:1;transform:translateX(-4px) scale(1.01);filter:brightness(0) invert(1)}
  44%{opacity:1;transform:translateX(3px) scale(1);filter:none}
  60%{opacity:1;transform:translateX(-2px) scale(1);filter:none}
  76%{opacity:1;transform:translateX(1px) scale(1);filter:none}
  100%{opacity:0;transform:translateX(0) scale(1);filter:none}
}
@keyframes combatHitNumber{
  0%{opacity:0;transform:translate(-50%,-34%) scale(.72)}
  14%{opacity:1;transform:translate(-50%,-52%) scale(1.18)}
  28%{opacity:1;transform:translate(-50%,-62%) scale(1)}
  72%{opacity:1;transform:translate(-50%,-92%) scale(1)}
  100%{opacity:0;transform:translate(-50%,-124%) scale(.96)}
}
@media(prefers-reduced-motion:reduce){
  .snapshot .combat-hit-ghost.combat-hit-impact{animation:combatHitImpactReduced .14s linear both}
  .snapshot .combat-hit-number{animation:combatHitNumberReduced .42s linear both}
}
@keyframes combatHitImpactReduced{0%{opacity:1;filter:none}35%{opacity:1;filter:brightness(0) invert(1)}100%{opacity:0;filter:none}}
@keyframes combatHitNumberReduced{0%{opacity:0}18%{opacity:1}75%{opacity:1}100%{opacity:0}}
</style>
'''

hit_script = r'''<script>
/* COMBAT_HIT_VFX_V1 / COMBAT_HIT_POST_RENDER_V2 */
(function(){
  if(window.__combatHitVfxV1)return;window.__combatHitVfxV1=true;

  function canonicalId(raw){
    var id=String(raw||'').trim().toLocaleLowerCase('vi-VN');
    if(id==='kai'||id.indexOf('kai akechi')>=0)return 'kai';
    if(id.indexOf('lucia')>=0||id==='lục'||id==='luc')return 'lucia';
    if(id.indexOf('syvial')>=0)return 'syvial';
    return id;
  }
  function finiteNumber(value){var n=Number(value);return Number.isFinite(n)?n:null;}
  function partyHpMap(){
    var out={};
    var members=(typeof state!=='undefined'&&state&&state.partyDetails&&Array.isArray(state.partyDetails.members))?state.partyDetails.members:[];
    members.forEach(function(member){
      if(!member)return;
      var id=canonicalId(member.id||member.name);if(!id)return;
      out[id]={hp:finiteNumber(member.currentHp),maxHp:finiteNumber(member.maxHp)};
    });
    return out;
  }
  window.captureCombatHitState=function(){
    var c=(typeof state!=='undefined'&&state&&state.combat)?state.combat:null;
    return {
      active:!!(c&&c.active===true),
      encounterId:String(c&&c.encounterId||''),
      actorId:String(c&&c.activeActorId||''),
      targetId:String(c&&c.activeActorTargetId||''),
      entityHp:finiteNumber(c&&c.entityHp),
      entityMaxHp:finiteNumber(c&&c.entityMaxHp),
      playerHp:finiteNumber(c&&c.playerHp),
      playerMaxHp:finiteNumber(c&&c.playerMaxHp),
      party:partyHpMap()
    };
  };
  function targetSelector(actorId){return String(actorId||'').indexOf('entity:')===0?'.snapshot-character':'.snapshot-entity';}
  window.captureCombatHitGhost=function(actorId){
    var box=document.getElementById('snapshot');if(!box)return null;
    var target=box.querySelector(targetSelector(actorId));
    if(!target||target.hidden||target.style.visibility==='hidden')return null;
    var src=String(target.currentSrc||target.getAttribute('src')||target.src||'').trim();if(!src)return null;
    var boxRect=box.getBoundingClientRect(),rect=target.getBoundingClientRect();
    if(rect.width<=0||rect.height<=0)return null;
    var ghost=target.cloneNode(true);
    ghost.removeAttribute('id');ghost.className='combat-hit-ghost';ghost.setAttribute('aria-hidden','true');
    ghost.hidden=false;ghost.style.visibility='visible';
    ghost.style.left=(rect.left-boxRect.left)+'px';ghost.style.top=(rect.top-boxRect.top)+'px';
    ghost.style.width=rect.width+'px';ghost.style.height=rect.height+'px';
    ghost.style.objectFit=getComputedStyle(target).objectFit||'contain';
    ghost.style.objectPosition=getComputedStyle(target).objectPosition||'center';
    ghost.dataset.hitX=String(rect.left-boxRect.left+rect.width*.5);
    ghost.dataset.hitY=String(rect.top-boxRect.top+rect.height*.34);
    return ghost;
  };
  function hpFor(snapshot,id){
    if(!snapshot)return null;
    id=canonicalId(id);
    if(id==='kai')return snapshot.playerHp;
    var member=snapshot.party&&snapshot.party[id];return member?member.hp:null;
  }
  window.combatHitDamageFor=function(before,after,actorId){
    if(!before||!after)return 0;
    actorId=String(actorId||'');
    var oldHp,newHp;
    if(actorId.indexOf('entity:')===0){
      var targetId=canonicalId(actorId.slice(7));
      oldHp=hpFor(before,targetId);newHp=hpFor(after,targetId);
      if(oldHp!=null&&newHp==null)newHp=0;
    }else{
      oldHp=before.entityHp;newHp=after.entityHp;
      if(oldHp!=null&&newHp==null&&!after.active)newHp=0;
    }
    if(oldHp==null||newHp==null)return 0;
    return Math.max(0,Math.round(oldHp-newHp));
  };
  function damageNumber(box,ghost,amount){
    if(!box||amount<=0)return null;
    var node=document.createElement('div');node.className='combat-hit-number';node.setAttribute('aria-hidden','true');node.textContent='-'+String(amount);
    var x=ghost?Number(ghost.dataset.hitX):box.clientWidth*.5;
    var y=ghost?Number(ghost.dataset.hitY):box.clientHeight*.42;
    node.style.left=(Number.isFinite(x)?x:box.clientWidth*.5)+'px';
    node.style.top=(Number.isFinite(y)?y:box.clientHeight*.42)+'px';
    box.appendChild(node);window.setTimeout(function(){if(node.parentNode)node.remove();},850);return node;
  }
  function mountCombatHit(ghost,amount){
    var box=document.getElementById('snapshot');if(!box){if(ghost&&ghost.remove)ghost.remove();return;}
    if(ghost){box.appendChild(ghost);void ghost.offsetWidth;ghost.classList.add('combat-hit-impact');window.setTimeout(function(){if(ghost.parentNode)ghost.remove();},360);}
    damageNumber(box,ghost,amount);
  }
  window.playCombatHitFromTransition=function(before,after,actorId,ghost){
    actorId=String(actorId||(before&&before.actorId)||'');
    var amount=window.combatHitDamageFor(before,after,actorId);
    if(amount<=0){if(ghost&&ghost.remove)ghost.remove();return false;}
    var mount=function(){mountCombatHit(ghost,amount);};
    if(typeof window.requestAnimationFrame==='function')window.requestAnimationFrame(mount);else window.setTimeout(mount,0);
    return true;
  };
})();
</script>
'''

if "COMBAT_HIT_VFX_V1" not in html:
    if "</head>" not in html or "</body>" not in html:
        raise RuntimeError("Combat hit VFX insertion anchors missing")
    html = html.replace("</head>", hit_style + "</head>", 1)
    html = html.replace("</body>", hit_script + "</body>", 1)

turn_wrapper_old = "var oldTurn=window.backroomTurn;if(typeof oldTurn==='function')window.backroomTurn=function(json){var r=oldTurn.call(this,json);inFlight=false;if(combat())step();else stop();return r;};"
turn_wrapper_new = "var oldTurn=window.backroomTurn;if(typeof oldTurn==='function')window.backroomTurn=function(json){var hitBefore=(inFlight&&typeof window.captureCombatHitState==='function')?window.captureCombatHitState():null;var hitActor=hitBefore?String(hitBefore.actorId||''):'';var hitGhost=(hitActor&&typeof window.captureCombatHitGhost==='function')?window.captureCombatHitGhost(hitActor):null;var r=oldTurn.call(this,json);var hitAfter=(hitBefore&&typeof window.captureCombatHitState==='function')?window.captureCombatHitState():null;if(hitBefore&&hitAfter&&hitActor&&typeof window.playCombatHitFromTransition==='function')window.playCombatHitFromTransition(hitBefore,hitAfter,hitActor,hitGhost);else if(hitGhost&&hitGhost.remove)hitGhost.remove();inFlight=false;if(combat())step();else stop();return r;};"
html = replace_once(html, turn_wrapper_old, turn_wrapper_new, "true-turn hit VFX backroomTurn bridge")

for marker in (
    "COMBAT_HIT_VFX_V1",
    "COMBAT_HIT_POST_RENDER_V2",
    "window.captureCombatHitState=function()",
    "window.captureCombatHitGhost=function(actorId)",
    "window.combatHitDamageFor=function(before,after,actorId)",
    "window.playCombatHitFromTransition=function(before,after,actorId,ghost)",
    "function mountCombatHit(ghost,amount)",
    "window.requestAnimationFrame(mount)",
    "combat-hit-impact",
    "combat-hit-number",
    "var hitBefore=(inFlight&&typeof window.captureCombatHitState==='function')",
):
    if marker not in html:
        raise RuntimeError("Combat hit VFX marker missing: " + marker)
if "Math.max(0,Math.round(oldHp-newHp))" not in html:
    raise RuntimeError("Combat hit VFX must derive damage from authoritative HP deltas")
if hit_script.count("combat-bar") or hit_style.count("combat-bar"):
    raise RuntimeError("Combat hit VFX must not create a second HP bar")

INDEX.write_text(html, encoding="utf-8")
print("True-turn Lucia compatibility applied; Combat Hit VFX V1 installed with target flash, pixel recoil and floating damage after native Snapshot redraw.")
from pathlib import Path
import runpy

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
INDEX = ROOT / "app/src/main/assets/index.html"

main = MAIN.read_text(encoding="utf-8")
html = INDEX.read_text(encoding="utf-8")

# This finalizer runs after the complete Android patch stack. Keep the transition at the
# presentation layer so it does not alter combat math, turn ownership, HP, status effects,
# Entity cleanup, or any canonical gameplay rule.
for marker in (
    "box.querySelector('.snapshot-character')",
    "file:///android_asset/kai_entity_overlay.png",
    "function activeEntityKey()",
):
    if marker not in main:
        raise RuntimeError("Combat actor transition requires final Snapshot marker: " + marker)
if "PRESSURE_COMBAT_HUD_V1" not in html:
    raise RuntimeError("Combat actor transition requires the final Pressure Combat HUD")

# The native snapshot bridge renders the default Kai image after WebView render/turn
# callbacks. Re-apply the authoritative combat actor after that render so Lucia or
# Syvial is not immediately overwritten by Kai.
snapshot_render_call = "renderSnapshot();"
snapshot_render_synced = "renderSnapshot();if(typeof window.syncCombatActorTransition==='function')window.syncCombatActorTransition();"
snapshot_render_count = main.count(snapshot_render_call)
if snapshot_render_count < 4:
    raise RuntimeError(f"Expected all native Snapshot render callbacks, found {snapshot_render_count}")
main = main.replace(snapshot_render_call, snapshot_render_synced)
if main.count(snapshot_render_synced) != snapshot_render_count:
    raise RuntimeError("Combat actor transition must survive every native Snapshot rendering callback")

style = r'''<style id="combatActorSwapStyle">
/* COMBAT_ACTOR_SWAP_V1 */
.snapshot .snapshot-character{will-change:opacity,transform,filter}
.snapshot .snapshot-character.combat-actor-swap-out{animation:combatActorSwapOut .18s ease-in forwards}
.snapshot .snapshot-character.combat-actor-swap-in{animation:combatActorSwapIn .24s cubic-bezier(.2,.8,.2,1) both}
.snapshot.combat-actor-empty .snapshot-character{visibility:hidden!important}
.snapshot .combat-actor-empty-slot{position:absolute;right:calc(-4% - 5px);bottom:0;width:42%;height:86%;pointer-events:none;opacity:0}
.snapshot .combat-actor-empty-slot.combat-actor-swap-in{animation:combatActorEmptyIn .24s ease-out both}
@keyframes combatActorSwapOut{from{opacity:1;transform:translateX(0) scale(1);filter:blur(0)}to{opacity:0;transform:translateX(-14px) scale(.985);filter:blur(1.5px)}}
@keyframes combatActorSwapIn{from{opacity:0;transform:translateX(14px) scale(.985);filter:blur(1.5px)}to{opacity:1;transform:translateX(0) scale(1);filter:blur(0)}}
@keyframes combatActorEmptyIn{from{opacity:0;transform:translateX(14px)}to{opacity:1;transform:translateX(0)}}
@media(prefers-reduced-motion:reduce){.snapshot .snapshot-character.combat-actor-swap-out,.snapshot .snapshot-character.combat-actor-swap-in,.snapshot .combat-actor-empty-slot.combat-actor-swap-in{animation:none!important;transform:none!important;filter:none!important}}
</style>
'''

script = r'''<script>
/* COMBAT_ACTOR_SWAP_V1 */
(function(){
  if(window.__combatActorSwapV1)return;window.__combatActorSwapV1=true;
  var OUT_MS=180,IN_MS=240,token=0;
  function payloadObject(raw){
    if(raw&&typeof raw==='object')return raw;
    try{return JSON.parse(String(raw||'{}'));}catch(e){return {};}
  }
  function reducedMotion(){return !!(window.matchMedia&&window.matchMedia('(prefers-reduced-motion: reduce)').matches);}
  function snapshotBox(){return document.getElementById('snapshot');}
  function characterImage(box){return box&&box.querySelector('.snapshot-character');}
  function imageSrc(img){return String((img&&img.getAttribute('src'))||(img&&img.src)||'').trim();}
  function actorDomMatches(box,img,p){
    var actorId=String(p.actorId||'').trim(),overlayUri=String(p.overlayUri||'').trim();
    var hasOverlay=p.hasOverlay===true&&overlayUri!=='';
    if(hasOverlay){
      return !!img&&img.style.visibility!=='hidden'&&!img.hidden&&imageSrc(img)===overlayUri&&String(img.dataset.combatActorId||'')===actorId;
    }
    var blank=box&&box.querySelector('.combat-actor-empty-slot');
    return !!blank&&(!img||img.style.visibility==='hidden'||img.hidden);
  }
  function removeEmpty(box){var old=box&&box.querySelector('.combat-actor-empty-slot');if(old)old.remove();if(box)box.classList.remove('combat-actor-empty');}
  function emptySlot(box){
    removeEmpty(box);
    var slot=document.createElement('div');slot.className='combat-actor-empty-slot';slot.setAttribute('aria-hidden','true');box.appendChild(slot);box.classList.add('combat-actor-empty');return slot;
  }
  function finishIncoming(box,img,p,myToken){
    if(myToken!==token||!box)return;
    img=characterImage(box)||img;
    removeEmpty(box);
    var hasOverlay=p.hasOverlay===true&&String(p.overlayUri||'').trim()!=='';
    if(hasOverlay){
      if(!img){img=document.createElement('img');img.className='snapshot-character';img.alt=String(p.name||p.actorId||'Combat actor');box.appendChild(img);}
      img.style.visibility='';img.hidden=false;img.src=String(p.overlayUri);img.dataset.combatActorId=String(p.actorId||'');img.dataset.combatActorSlot=String(p.slot==null?'':p.slot);
      img.classList.remove('combat-actor-swap-out','combat-actor-swap-in');
      void img.offsetWidth;img.classList.add('combat-actor-swap-in');
      window.setTimeout(function(){if(myToken===token&&img)img.classList.remove('combat-actor-swap-in');},IN_MS+40);
    }else{
      if(img){img.classList.remove('combat-actor-swap-out','combat-actor-swap-in');img.style.visibility='hidden';img.dataset.combatActorId=String(p.actorId||'');img.dataset.combatActorSlot=String(p.slot==null?'':p.slot);}
      var blank=emptySlot(box);void blank.offsetWidth;blank.classList.add('combat-actor-swap-in');
    }
    box.dataset.combatActorId=String(p.actorId||'');box.dataset.combatActorSlot=String(p.slot==null?'':p.slot);
  }
  window.backroomCombatActorSwap=function(raw){
    var p=payloadObject(raw),box=snapshotBox();if(!box)return false;
    var actorId=String(p.actorId||'').trim();if(!actorId)return false;
    var currentId=String(box.dataset.combatActorId||'');
    var img=characterImage(box);
    if(currentId===actorId&&!p.force&&actorDomMatches(box,img,p))return true;
    var myToken=++token;
    if(reducedMotion()||!img||img.style.visibility==='hidden'){
      finishIncoming(box,img,p,myToken);return true;
    }
    img.classList.remove('combat-actor-swap-in','combat-actor-swap-out');void img.offsetWidth;img.classList.add('combat-actor-swap-out');
    window.setTimeout(function(){finishIncoming(box,img,p,myToken);},OUT_MS);
    return true;
  };
  window.backroomCombatActorTransitionMs=OUT_MS+IN_MS;
  window.backroomCombatActorForState=function(){
    var c=window.state&&state.combat;if(!c||c.active!==true)return null;
    var actorId=String(c.activeActorId||c.actorId||'').trim();if(!actorId)return null;
    var slot=Number(c.activeActorSlot);if(!Number.isFinite(slot))slot=0;
    var uri=String(c.activeActorOverlayUri||'').trim();
    return {actorId:actorId,slot:slot,name:String(c.activeActorName||actorId),overlayUri:uri,hasOverlay:!!uri};
  };
  function syncCombatActor(){var p=window.backroomCombatActorForState();if(p)window.backroomCombatActorSwap(p);}
  var oldRender=window.render;if(typeof oldRender==='function'){window.render=function(){var r=oldRender.apply(this,arguments);syncCombatActor();return r;};}
  var oldTurn=window.backroomTurn;if(typeof oldTurn==='function'){window.backroomTurn=function(json){var r=oldTurn.call(this,json);syncCombatActor();return r;};}
  window.syncCombatActorTransition=syncCombatActor;
})();
</script>
'''

if "COMBAT_ACTOR_SWAP_V1" not in html:
    if "</head>" not in html or "</body>" not in html:
        raise RuntimeError("Combat actor transition insertion anchors missing")
    html = html.replace("</head>", style + "</head>", 1)
    html = html.replace("</body>", script + "</body>", 1)

for marker in (
    "COMBAT_ACTOR_SWAP_V1",
    "window.backroomCombatActorSwap=function(raw)",
    "window.backroomCombatActorTransitionMs=OUT_MS+IN_MS",
    "prefers-reduced-motion:reduce",
    "combat-actor-empty-slot",
    "img=characterImage(box)||img",
    "function actorDomMatches(box,img,p)",
    "currentId===actorId&&!p.force&&actorDomMatches(box,img,p)",
    "c.activeActorId||c.actorId",
):
    if marker not in html:
        raise RuntimeError("Combat actor transition marker missing: " + marker)

INDEX.write_text(html, encoding="utf-8")
MAIN.write_text(main, encoding="utf-8")
print("Combat actor swap transition installed: 180ms fade/slide out + 240ms fade/slide in, reduced-motion safe, empty-overlay placeholder supported.")

# Keep the old automatic-combat layer as the compatibility baseline, then make
# actor ownership authoritative and round-aware in the final true-turn pass while
# preserving Lucia's existing Entity-evasion gate.
runpy.run_path(str(ROOT / "patch-auto-party-combat-final.py"), run_name="__main__")
runpy.run_path(str(ROOT / "patch-true-turn-evasion-compat.py"), run_name="__main__")

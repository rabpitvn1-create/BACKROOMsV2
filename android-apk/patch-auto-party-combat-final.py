from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
COMBAT = ROOT / "app/src/main/java/com/rabpit/backroom/core/CombatRuntime.kt"
FACADE = ROOT / "app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt"
TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/CombatRuntimeTest.kt"
INDEX = ROOT / "app/src/main/assets/index.html"
ASSETS = ROOT / "app/src/main/assets"


def replace_once(source: str, old: str, new: str, label: str) -> str:
    if new in source:
        return source
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return source.replace(old, new, 1)


# ---------------------------------------------------------------------------
# Combat runtime: one existing CombatRuntime.resolve call remains one complete
# authoritative combat round. Lucia now participates automatically whenever she
# is an ACTIVE living Party combatant. This deliberately does NOT split one
# round into several resolve() calls, so round-scoped effects (Diệp Minh's
# Devils And Gold, Entity regeneration, Kai proc counters, time/regen) keep
# firing exactly once per combat round rather than once per visual subturn.
# ---------------------------------------------------------------------------
combat = COMBAT.read_text(encoding="utf-8")

joint_pattern = re.compile(
    r'''        // LUCIA_JOINT_ATTACK: process the follower when the player explicitly orders both attackers\.\n'''
    r'''        val jointOrder = action\.lowercase\(\)\.let \{ raw ->\n'''
    r'''          raw\.contains\("cả 2"\) \|\| raw\.contains\("cả hai"\) \|\| raw\.contains\("hai người"\) \|\|\n'''
    r'''            raw\.contains\("cùng tấn công"\) \|\| raw\.contains\("cùng bắn"\) \|\|\n'''
    r'''            \(\(raw\.contains\("lucia"\) \|\| raw\.contains\("lục"\)\) && \(raw\.contains\("tấn công"\) \|\| raw\.contains\("bắn"\)\)\)\n'''
    r'''        \}\n'''
)
if "LUCIA_AUTO_ATTACK_V1" not in combat:
    combat, count = joint_pattern.subn(
        '        // LUCIA_AUTO_ATTACK_V1: an ACTIVE living Lucia attacks once in every authoritative ATTACK round.\n',
        combat,
        count=1,
    )
    if count != 1:
        raise RuntimeError(f"Lucia automatic attack gate: expected 1 joint-order block, found {count}")

combat = replace_once(
    combat,
    '        if (jointOrder && luciaActive && c.entityHp > 0) {\n',
    '        if (luciaActive && c.entityHp > 0) {\n',
    "Lucia automatic combat participation",
)

# Mark the JSON projection as auto-round combat and expose the authoritative
# round counter. activeActorId remains presentation-only and is never persisted
# into GameCore, avoiding a second source of gameplay truth.
if 'put("auto", true)' not in combat:
    combat = replace_once(
        combat,
        '    put("active", c.phase == Phase.ACTIVE)\n',
        '    put("active", c.phase == Phase.ACTIVE)\n    put("auto", true)\n    put("round", c.eventCounter)\n',
        "Combat auto projection",
    )

for marker in (
    "LUCIA_AUTO_ATTACK_V1",
    "if (luciaActive && c.entityHp > 0)",
    'put("auto", true)',
    'put("round", c.eventCounter)',
):
    if marker not in combat:
        raise RuntimeError("Auto-party CombatRuntime contract missing: " + marker)
if "jointOrder && luciaActive" in combat:
    raise RuntimeError("Legacy Lucia joint-order combat gate is still active")
COMBAT.write_text(combat, encoding="utf-8")


# ---------------------------------------------------------------------------
# Facade: autoplay uses the existing local deterministic combat path, but it
# must not forge a player-authored chat message every round. AUTO_COMBAT rounds
# are logged as COMBAT while manual actions preserve the old player/GM pair.
# ---------------------------------------------------------------------------
facade = FACADE.read_text(encoding="utf-8")
auto_log = '''    if (actionKind.equals("AUTO_COMBAT", true)) {
      val combatLog = output.optJSONArray("log") ?: JSONArray().also { output.put("log", it) }
      combatLog.put(JSONObject().put("role", "combat").put("text", resolution.reply))
    } else {
      appendLog(output, action, resolution.reply)
    }
'''
if 'actionKind.equals("AUTO_COMBAT", true)' not in facade:
    facade = replace_once(
        facade,
        '    appendLog(output, action, resolution.reply)\n',
        auto_log,
        "AUTO_COMBAT log projection",
    )
for marker in (
    'actionKind.equals("AUTO_COMBAT", true)',
    'JSONObject().put("role", "combat")',
    'appendLog(output, action, resolution.reply)',
):
    if marker not in facade:
        raise RuntimeError("Auto-party GameCoreFacade contract missing: " + marker)
FACADE.write_text(facade, encoding="utf-8")


# ---------------------------------------------------------------------------
# Focused regression: Lucia now attacks without explicit joint-order wording.
# Existing Diệp Minh tests continue to protect the every-five-round ultimate.
# ---------------------------------------------------------------------------
test = TEST.read_text(encoding="utf-8")
old_test = re.compile(
    r'''\n  @Test fun luciaDoesNotAutoAttackOnKaiOnlyAttackOrder\(\) \{.*?\n  \}\n''',
    re.DOTALL,
)
new_test = r'''
  @Test fun luciaAutoAttacksOnAPlainAttackRound() {
    val initial = LuciaCanon.ensure(GameState.initial())
    var state = initial.copy(party = PartyState(memberIds = listOf(KAI_ID, LUCIA_ID)))
    state = CombatRuntime.start(state, "diep_minh")

    val result = CombatRuntime.resolve(state, "AUTO_COMBAT", "Tự động tấn công")
    assertTrue(result.handled)
    assertTrue(
      result.reply.contains("Lucia \"Lục\" bắn hỗ trợ") ||
        result.reply.contains("Lucia \"Lục\" cũng khai hỏa")
    )
    assertEquals(1, CombatRuntime.active(result.state)!!.eventCounter)
  }
'''
if "luciaAutoAttacksOnAPlainAttackRound" not in test:
    test, count = old_test.subn("\n" + new_test.lstrip("\n"), test, count=1)
    if count != 1:
        raise RuntimeError(f"Lucia old negative regression: expected 1 test, found {count}")
for marker in (
    "luciaAutoAttacksOnAPlainAttackRound",
    'CombatRuntime.resolve(state, "AUTO_COMBAT", "Tự động tấn công")',
    "assertEquals(1, CombatRuntime.active(result.state)!!.eventCounter)",
):
    if marker not in test:
        raise RuntimeError("Auto-party combat regression test missing: " + marker)
if "luciaDoesNotAutoAttackOnKaiOnlyAttackOrder" in test:
    raise RuntimeError("Obsolete Lucia no-auto-attack regression still present")
TEST.write_text(test, encoding="utf-8")


# The combat overlays are release inputs, not optional runtime downloads.
for asset_name in ("kai_entity_overlay.png", "lucia_entity_overlay.png", "syvial_entity_overlay.png"):
    asset_path = ASSETS / asset_name
    if not asset_path.is_file() or asset_path.stat().st_size <= 0:
        raise RuntimeError("Required combat overlay missing: " + str(asset_path))


# ---------------------------------------------------------------------------
# WebView: repair the pre-existing HUD/actor-swap state lookup, add a COMBAT
# popup, lock manual actions during combat, and replay one visual actor subturn
# every two seconds. Exactly one authoritative AUTO_COMBAT resolve is submitted
# per complete visual cycle. Entity subturns are presentation steps only, so
# Diệp Minh cannot fire his round-scoped ultimate multiple times in one cycle.
# ---------------------------------------------------------------------------
html = INDEX.read_text(encoding="utf-8")

# Top-level `let state` is a global lexical binding, not window.state. The old
# guard therefore made the Pressure Combat HUD and actor swap permanently inert.
state_guard_old = "window.state&&state.combat"
state_guard_new = "typeof state!=='undefined'&&state&&state.combat"
if state_guard_old in html:
    html = html.replace(state_guard_old, state_guard_new)
if state_guard_old in html:
    raise RuntimeError("Legacy window.state combat guard still present")
if html.count(state_guard_new) < 2:
    raise RuntimeError("Expected repaired combat state guards for HUD and actor swap")

sync_old = '''function syncPrimaryActions(){
  const hasText=!!(actionEl&&actionEl.value.trim());
  if(submitEl)submitEl.disabled=busy||!hasText;
  if(searchActionButton)searchActionButton.disabled=busy;
  if(exploreActionButton)exploreActionButton.disabled=busy;
}
'''
sync_new = '''function syncPrimaryActions(){
  const hasText=!!(actionEl&&actionEl.value.trim());
  const combatLocked=!!(state&&state.combat&&state.combat.active===true);
  if(submitEl)submitEl.disabled=busy||combatLocked||!hasText;
  if(searchActionButton)searchActionButton.disabled=busy||combatLocked;
  if(exploreActionButton)exploreActionButton.disabled=busy||combatLocked;
  if(actionEl)actionEl.disabled=combatLocked;
}
'''
html = replace_once(html, sync_old, sync_new, "combat manual-action lock")

html = replace_once(
    html,
    'formEl.addEventListener("submit",e=>{e.preventDefault();const a=actionEl.value.trim();if(!a||busy)return;',
    'formEl.addEventListener("submit",e=>{e.preventDefault();const a=actionEl.value.trim();if(!a||busy||(state&&state.combat&&state.combat.active===true))return;',
    "combat form submit lock",
)
html = replace_once(
    html,
    'function submitMacroAction(kind,label){\n  if(busy)return;\n',
    'function submitMacroAction(kind,label){\n  if(busy||(state&&state.combat&&state.combat.active===true))return;\n',
    "combat macro-action lock",
)

hud_title_old = '''    hud.innerHTML='<div class="combat-title"><span>PRESSURE COMBAT</span><span>'+esc(c.entityName||c.entityKey)+'</span></div>'+\n'''
hud_title_new = '''    hud.innerHTML='<div class="combat-title"><span>PRESSURE COMBAT</span><button type="button" id="combatPopupButton" class="combat-popup-button" aria-haspopup="dialog">COMBAT</button><span>'+esc(c.entityName||c.entityKey)+'</span></div>'+\n'''
html = replace_once(html, hud_title_old, hud_title_new, "COMBAT popup button")

# Distinguish deterministic combat output from Game Master prose.
html = replace_once(
    html,
    '+(x.role==="player"?"BẠN":"GAME MASTER")+',
    '+(x.role==="player"?"BẠN":x.role==="combat"?"COMBAT":"GAME MASTER")+',
    "combat chat role",
)
html = replace_once(
    html,
    "kind==='player'?'BẠN':kind==='system'?'HỆ THỐNG':'GAME MASTER'",
    "kind==='player'?'BẠN':kind==='system'?'HỆ THỐNG':kind==='combat'?'COMBAT':'GAME MASTER'",
    "combat decorated log role",
)


script = r'''<script>
/* AUTO_PARTY_COMBAT_V1 / COMBAT_POPUP_V1 */
(function(){
  if(window.__autoPartyCombatV1)return;window.__autoPartyCombatV1=true;
  var STEP_MS=2000,timer=0,actorIndex=0,lastEncounter='',inFlight=false,currentActor=null;

  function combat(){return (typeof state!=='undefined'&&state&&state.combat&&state.combat.active===true)?state.combat:null;}
  function escCombat(s){return String(s==null?'':s).replace(/[&<>"']/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot',"'":'&#39;'}[c]});}
  function canonicalId(raw){
    var id=String(raw||'').trim().toLocaleLowerCase('vi-VN');
    if(id==='kai'||id.indexOf('kai akechi')>=0)return 'kai';
    if(id.indexOf('lucia')>=0||id==='lục'||id==='luc')return 'lucia';
    if(id.indexOf('syvial')>=0)return 'syvial';
    if(id.indexOf('iris')>=0)return 'iris';
    if(id==='an-nhien'||id==='an_nhien'||id==='annhien'||id.indexOf('an nhiên')>=0)return 'an-nhien';
    return id;
  }
  function partyMembers(c){
    var members=(state&&state.partyDetails&&Array.isArray(state.partyDetails.members))?state.partyDetails.members.slice():[];
    if(!members.length){
      members=[{id:'kai',name:'Kai Akechi',presence:'ACTIVE',currentHp:c.playerHp,maxHp:c.playerMaxHp}];
      if(state&&Array.isArray(state.party))state.party.forEach(function(m){if(m&&canonicalId(m.id||m.name)!=='kai')members.push(m);});
    }
    var seen={},out=[];
    members.forEach(function(m){
      if(!m)return;var id=canonicalId(m.id||m.name);if(!id||seen[id])return;seen[id]=true;
      var presence=String(m.presence||'ACTIVE').toUpperCase();
      var hp=(m.currentHp==null?null:Number(m.currentHp));
      if(presence!=='ACTIVE'||(Number.isFinite(hp)&&hp<=0))return;
      var combatant=id==='kai'||id==='lucia'||id==='syvial'||id==='iris'||m.combatant===true;
      if(id==='an-nhien'||m.nonCombat===true)combatant=false;
      if(!combatant)return;
      out.push({id:id,name:String(m.name||id),type:'party',currentHp:hp,maxHp:m.maxHp==null?null:Number(m.maxHp)});
    });
    if(!seen.kai)out.unshift({id:'kai',name:'Kai Akechi',type:'party',currentHp:Number(c.playerHp),maxHp:Number(c.playerMaxHp)});
    out.sort(function(a,b){if(a.id==='kai')return -1;if(b.id==='kai')return 1;return 0;});
    return out.slice(0,4);
  }
  function turnOrder(c){
    var party=partyMembers(c),entity={id:'entity:'+String(c.entityKey||'entity'),name:String(c.entityName||c.entityKey||'Entity'),type:'entity'};
    var order=[];if(party.length)order.push(party[0]);else order.push({id:'kai',name:'Kai Akechi',type:'party'});order.push(entity);
    party.slice(1).forEach(function(member){order.push(member);order.push(entity);});
    return order;
  }
  function overlayFor(id){
    if(id==='kai')return 'file:///android_asset/kai_entity_overlay.png';
    if(id==='lucia')return 'file:///android_asset/lucia_entity_overlay.png';
    if(id==='syvial')return 'file:///android_asset/syvial_entity_overlay.png';
    return '';
  }
  function actorPayload(actor,slot){
    var uri=actor.type==='entity'?'':overlayFor(actor.id);
    return {actorId:actor.id,slot:slot,name:actor.name,overlayUri:uri,hasOverlay:!!uri,force:true};
  }
  function setActor(actor,slot){
    var c=combat();if(!c)return;
    currentActor={id:actor.id,name:actor.name,type:actor.type,slot:slot};
    c.activeActorId=actor.id;c.activeActorName=actor.name;c.activeActorSlot=slot;c.activeActorOverlayUri=actor.type==='entity'?'':overlayFor(actor.id);
    if(typeof window.backroomCombatActorSwap==='function')window.backroomCombatActorSwap(actorPayload(actor,slot));
    renderPopup();
  }

  function ensurePopup(){
    var popup=document.getElementById('combatPopup');if(popup)return popup;
    popup=document.createElement('div');popup.id='combatPopup';popup.hidden=true;
    popup.innerHTML='<div class="combat-popup-sheet" role="dialog" aria-modal="true" aria-labelledby="combatPopupTitle">'+
      '<div class="combat-popup-head"><div><h2 id="combatPopupTitle">COMBAT</h2><span class="combat-popup-auto">AUTO • 2s / step</span></div><button type="button" id="combatPopupClose" class="combat-popup-close" aria-label="Đóng">×</button></div>'+
      '<div id="combatPopupTarget" class="combat-popup-target"></div><div id="combatPopupCurrent" class="combat-popup-current"></div><div id="combatPopupOrder" class="combat-popup-order"></div><div id="combatPopupParty" class="combat-popup-party"></div></div>';
    document.body.appendChild(popup);return popup;
  }
  function renderPopup(){
    var popup=ensurePopup(),c=combat();if(!c){popup.hidden=true;return;}
    var order=turnOrder(c),party=partyMembers(c),activeId=currentActor&&currentActor.id||'';
    var target=document.getElementById('combatPopupTarget');if(target)target.innerHTML='<strong>'+escCombat(c.entityName||c.entityKey||'Entity')+'</strong><span>'+Number(c.entityHp)+' / '+Number(c.entityMaxHp)+' HP</span>';
    var current=document.getElementById('combatPopupCurrent');if(current)current.textContent='TURN: '+(currentActor?currentActor.name:'Đang chuẩn bị')+' • ROUND '+Number(c.round||0);
    var orderEl=document.getElementById('combatPopupOrder');if(orderEl)orderEl.innerHTML=order.map(function(a){return '<span class="combat-turn-chip'+(a.id===activeId?' current':'')+'">'+escCombat(a.name)+'</span>';}).join('');
    var partyEl=document.getElementById('combatPopupParty');if(partyEl){var slots=[];for(var i=0;i<4;i++){var m=party[i];if(!m){slots.push('<div class="combat-popup-slot empty"><strong>Trống</strong><span>không có turn</span></div>');continue;}var hp=(Number.isFinite(m.currentHp)&&Number.isFinite(m.maxHp))?(Math.max(0,m.currentHp)+'/'+Math.max(1,m.maxHp)+' HP'):'ACTIVE';slots.push('<div class="combat-popup-slot'+(m.id===activeId?' current':'')+'"><strong>'+escCombat(m.name)+'</strong><span>'+escCombat(hp)+'</span></div>');}partyEl.innerHTML=slots.join('');}
  }
  function openPopup(){var p=ensurePopup();if(combat()){renderPopup();p.hidden=false;}}
  function closePopup(){var p=document.getElementById('combatPopup');if(p)p.hidden=true;}
  document.addEventListener('click',function(ev){
    var button=ev.target&&ev.target.closest?ev.target.closest('#combatPopupButton'):null;if(button){ev.preventDefault();openPopup();return;}
    if(ev.target&&ev.target.id==='combatPopupClose'){closePopup();return;}
    var popup=document.getElementById('combatPopup');if(popup&&ev.target===popup)closePopup();
  });
  document.addEventListener('keydown',function(ev){if(ev.key==='Escape')closePopup();});

  function clearTimer(){if(timer){window.clearTimeout(timer);timer=0;}}
  function queue(ms){clearTimer();timer=window.setTimeout(step,Math.max(0,ms));}
  function submitAutoRound(){
    var c=combat();if(!c||inFlight||busy)return false;
    if(!window.Android||typeof window.Android.submitAction!=='function'){if(statusEl)statusEl.textContent='Không tìm thấy Android combat bridge.';queue(1000);return false;}
    inFlight=true;busy=true;if(typeof syncPrimaryActions==='function')syncPrimaryActions();
    if(statusEl)statusEl.textContent='COMBAT AUTO • round '+(Number(c.round||0)+1);
    window.Android.submitAction(JSON.stringify(state),'AUTO_COMBAT','Tự động tấn công');
    return true;
  }
  function stop(){clearTimer();actorIndex=0;lastEncounter='';currentActor=null;inFlight=false;closePopup();if(typeof syncPrimaryActions==='function')syncPrimaryActions();}
  function step(){
    timer=0;var c=combat();if(!c){stop();return;}
    var encounter=String(c.encounterId||c.entityKey||'combat');if(encounter!==lastEncounter){lastEncounter=encounter;actorIndex=0;currentActor=null;}
    var order=turnOrder(c);if(!order.length){queue(STEP_MS);return;}if(actorIndex>=order.length)actorIndex=0;
    if(actorIndex===0&&(inFlight||busy)){setActor(order[0],0);queue(250);return;}
    var actor=order[actorIndex];setActor(actor,actorIndex);
    if(actorIndex===0)submitAutoRound();
    actorIndex=(actorIndex+1)%order.length;queue(STEP_MS);
  }
  function refresh(){
    var c=combat();if(!c){stop();return;}
    var encounter=String(c.encounterId||c.entityKey||'combat');
    if(encounter!==lastEncounter){lastEncounter=encounter;actorIndex=0;currentActor=null;clearTimer();queue(120);}
    renderPopup();if(typeof syncPrimaryActions==='function')syncPrimaryActions();
  }

  var oldTurn=window.backroomTurn;
  if(typeof oldTurn==='function')window.backroomTurn=function(json){var r=oldTurn.call(this,json);inFlight=false;refresh();return r;};
  var oldError=window.backroomError;
  if(typeof oldError==='function')window.backroomError=function(message){var r=oldError.call(this,message);inFlight=false;if(combat())queue(STEP_MS);return r;};
  window.backroomAutoCombatRefresh=refresh;window.backroomCombatTurnOrder=function(){var c=combat();return c?turnOrder(c):[];};
  window.setTimeout(refresh,0);
})();
</script>
'''

if "AUTO_PARTY_COMBAT_V1" not in html:
    if "</body>" not in html:
        raise RuntimeError("Auto-party combat HTML body anchor missing")
    html = html.replace("</body>", script + "</body>", 1)

for marker in (
    "AUTO_PARTY_COMBAT_V1",
    "COMBAT_POPUP_V1",
    'id="combatPopupButton"',
    "var STEP_MS=2000",
    "window.Android.submitAction(JSON.stringify(state),'AUTO_COMBAT','Tự động tấn công')",
    "file:///android_asset/lucia_entity_overlay.png",
    "file:///android_asset/syvial_entity_overlay.png",
    "if(actorIndex===0)submitAutoRound()",
    "không có turn",
    "combatLocked",
    'x.role==="combat"?"COMBAT"',
):
    if marker not in html:
        raise RuntimeError("Auto-party combat UI contract missing: " + marker)
if "window.state&&state.combat" in html:
    raise RuntimeError("Broken window.state combat guard survived finalization")
INDEX.write_text(html, encoding="utf-8")

print(
    "Auto Party Combat V1 installed: repaired Pressure Combat state binding, automatic round resolution, "
    "2-second Kai/Entity/follower visual rotation, Lucia auto-attack, COMBAT popup, manual-input lock, "
    "and round-atomic Diệp Minh protection."
)

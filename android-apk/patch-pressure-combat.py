from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
FACADE = ROOT / "app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt"
INDEX = ROOT / "app/src/main/assets/index.html"


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return source.replace(old, new, 1)


# PARTY_TURN_COMBAT_V2
# This patch runs last in the Android patch chain. It wires the already-versioned
# CombatRuntime into the final generated bridge/UI instead of letting a later patch
# swallow combat integration.

# ---- Game State Core facade -------------------------------------------------
facade = FACADE.read_text(encoding="utf-8")
combat_methods = r'''
  fun processCombat(legacyStateJson: String, action: String): String {
    val legacy = JSONObject(legacyStateJson)
    var current = loadOrMigrate(legacy)
    if (CombatRuntime.active(current) == null) {
      val encounterKey = legacy.optJSONObject("flags")?.optString("entityEncounterKey", "")?.trim().orEmpty()
      if (encounterKey.isBlank()) return response(false, legacy, null, "combat_inactive")
      val started = CombatRuntime.start(current, encounterKey)
      if (CombatRuntime.active(started) == null) return response(false, legacy, null, "combat_unknown_entity")
      current = started
      repository.save(current)
    }

    val resolution = CombatRuntime.resolve(current, "", action)
    if (!resolution.handled) return response(false, legacy, null, "combat_inactive")

    var next = resolution.state
    val time = TimeEngine.execute(next, TimeAdvanceCommand(
      commandId = "COMBAT:${next.turn.currentTurnId}:${System.nanoTime()}",
      turnId = null,
      actorId = KAI_ID,
      source = CommandSource.SYSTEM,
      minutes = 1,
      reason = "combat_round"
    ))
    if (time.applied) next = time.state
    repository.save(next)

    val output = syncLegacy(legacy, next, incrementTurn = true)
    if (resolution.entityDestroyed || resolution.escaped) {
      val flags = output.optJSONObject("flags") ?: JSONObject().also { output.put("flags", it) }
      flags.put("entityEncounterKey", "")
    }

    val log = output.optJSONArray("log") ?: JSONArray().also { output.put("log", it) }
    log.put(JSONObject().put("role", "player").put("text", action))
    resolution.events.forEach { event ->
      log.put(JSONObject().put("role", "system").put("text", event.text))
    }

    return JSONObject().apply {
      put("handled", true)
      put("state", output)
      put("reason", when {
        resolution.entityDestroyed -> "combat_entity_destroyed"
        resolution.escaped -> "combat_escaped"
        else -> "combat_resolved"
      })
      put("reply", resolution.reply)
      put("events", JSONArray().apply { resolution.events.forEach { put(it.toJson()) } })
    }.toString()
  }
'''
if "fun processCombat(legacyStateJson: String, action: String)" not in facade:
    anchor = "  private fun loadOrMigrate(legacy: JSONObject): GameState {\n"
    if anchor not in facade:
        raise RuntimeError("Party combat facade loadOrMigrate anchor missing")
    facade = facade.replace(anchor, combat_methods + "\n" + anchor, 1)

combat_projection = '    CombatRuntime.toJson(state)?.let { output.put("combat", it) } ?: output.remove("combat")\n'
if combat_projection not in facade:
    anchor = "    return output\n  }\n\n  private fun appendLog"
    if anchor not in facade:
        raise RuntimeError("Party combat facade syncLegacy anchor missing")
    facade = facade.replace(anchor, combat_projection + "    return output\n  }\n\n  private fun appendLog", 1)

for marker in (
    "fun processCombat(legacyStateJson: String, action: String)",
    "CombatRuntime.resolve(current, \"\", action)",
    'put("role", "system")',
    'output.put("combat", it)',
    'flags.put("entityEncounterKey", "")',
):
    if marker not in facade:
        raise RuntimeError("Party combat facade contract missing: " + marker)
FACADE.write_text(facade, encoding="utf-8")


# ---- Android bridge ---------------------------------------------------------
main = MAIN.read_text(encoding="utf-8")
combat_intercept = r'''          JSONObject combatResult = gameCore != null ? new JSONObject(gameCore.processCombat(stateJson, action)) : null;
          if (combatResult != null && combatResult.optBoolean("handled", false)) {
            emit("backroomCombat", combatResult.toString());
            return;
          }
'''
if "gameCore.processCombat(stateJson, action)" not in main:
    bridge = main.find("  private class GameBridge {")
    if bridge < 0:
        raise RuntimeError("Party combat GameBridge anchor missing")
    submit = main.find("@JavascriptInterface public void submitTurn(String stateJson, String action)", bridge)
    if submit < 0:
        raise RuntimeError("Party combat submitTurn anchor missing")
    try_anchor = "        try {\n"
    position = main.find(try_anchor, submit)
    if position < 0:
        raise RuntimeError("Party combat submit try anchor missing")
    position += len(try_anchor)
    main = main[:position] + combat_intercept + main[position:]

for marker in (
    "gameCore.processCombat(stateJson, action)",
    'emit("backroomCombat", combatResult.toString());',
):
    if marker not in main:
        raise RuntimeError("Party combat Android bridge missing: " + marker)
MAIN.write_text(main, encoding="utf-8")


# ---- System log role + automatic COMBAT popup -------------------------------
html = INDEX.read_text(encoding="utf-8")

role_old = '(x.role==="player"?"BẠN":"GAME MASTER")'
role_new = '(x.role==="player"?"BẠN":(x.role==="system"?"HỆ THỐNG":"GAME MASTER"))'
if role_new not in html:
    if role_old not in html:
        raise RuntimeError("Party combat role renderer anchor missing")
    html = html.replace(role_old, role_new, 1)

class_old = '(x.role==="player"?"player":"")'
class_new = '(x.role==="player"?"player":(x.role==="system"?"system":""))'
if class_new not in html:
    if class_old not in html:
        raise RuntimeError("Party combat message class anchor missing")
    html = html.replace(class_old, class_new, 1)

marker = "PARTY_TURN_COMBAT_V2"
if marker not in html:
    ui = r'''
<style id="partyTurnCombatStyle">
.message.system{border-left-color:#60707e;background:#0d1419}.message.system .role{color:#9fb3c2}.message.system .text{color:#e3e9ed}
.combat-trigger{margin-top:9px;padding:7px 12px;border:1px solid #7d3333;background:#2a1111;color:#ffb8b8;font-size:11px;letter-spacing:.14em;font-weight:900}
.combat-popup{margin-top:9px;border:1px solid #353d44;background:#090c0f;padding:9px}.combat-popup[hidden]{display:none}.combat-popup-head{display:flex;align-items:center;justify-content:space-between;gap:8px;margin-bottom:8px;font-size:10px;letter-spacing:.12em;color:#aeb8c0}.combat-popup-head b{color:#ffb8b8}.combat-party-slots{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:6px}.combat-slot{min-height:70px;border:1px solid #2b3238;background:#0f1316;padding:5px;display:grid;grid-template-rows:1fr auto;gap:4px;min-width:0}.combat-slot.active{border-color:#d6dce1;box-shadow:0 0 0 1px #d6dce155 inset}.combat-slot.empty{opacity:.38}.combat-slot-visual{min-height:44px;display:grid;place-items:center;overflow:hidden;background:#080a0c}.combat-slot-visual img{max-width:100%;max-height:54px;object-fit:contain}.combat-slot-placeholder{width:24px;height:34px;border:1px dashed #4a535a;border-radius:3px}.combat-slot-name{white-space:nowrap;overflow:hidden;text-overflow:ellipsis;text-align:center;font-size:9px;color:#b8c1c8}.combat-stage{margin-top:8px;min-height:112px;border:1px solid #272e34;background:#050708;display:grid;grid-template-columns:90px 1fr;gap:9px;padding:8px}.combat-stage-visual{display:grid;place-items:center;overflow:hidden}.combat-stage-visual img{max-width:100%;max-height:104px;object-fit:contain}.combat-stage-empty{width:54px;height:86px;border:1px dashed #414a51;display:grid;place-items:center;color:#59646c;font-size:8px;text-align:center;padding:4px}.combat-live{font-size:12px;line-height:1.5;display:flex;flex-direction:column;justify-content:center}.combat-live b{font-size:10px;letter-spacing:.1em;color:#9eaab3;margin-bottom:5px}.combat-hp{margin-top:6px;color:#8d99a2;font-size:9px}.combat-hp span+span:before{content:' • ';color:#58636c}
</style>
<script>
/* PARTY_TURN_COMBAT_V2 */
(function(){
  var lastAutoOpened='';

  function combatKey(s){
    if(!s)return '';
    if(s.combat&&s.combat.active&&s.combat.entityKey)return String(s.combat.entityKey);
    var f=s.flags||{};return String(f.entityEncounterKey||'').trim();
  }
  function prettyEntity(key){return String(key||'ENTITY').replace(/[_-]+/g,' ').replace(/\b\w/g,function(c){return c.toUpperCase();});}
  function fallbackSlots(s){
    var slots=[{slot:0,occupied:true,id:'kai',name:'Kai',overlayRef:'kai_entity_overlay.png'}];
    var party=(s&&Array.isArray(s.party))?s.party:[];
    for(var i=0;i<party.length&&slots.length<4;i++){
      var m=party[i]||{},id=String(m.id||m.name||'').trim();if(!id||id==='kai')continue;
      slots.push({slot:slots.length,occupied:true,id:id,name:String(m.name||id),overlayRef:''});
    }
    while(slots.length<4)slots.push({slot:slots.length,occupied:false,id:'',name:'',overlayRef:''});
    return slots;
  }
  function combatSlots(s){
    var c=s&&s.combat;if(c&&Array.isArray(c.slots)&&c.slots.length===4)return c.slots;
    return fallbackSlots(s);
  }
  function gmFrame(){
    var log=document.getElementById('log');if(!log)return null;
    var messages=log.querySelectorAll('.message');
    for(var i=messages.length-1;i>=0;i--){
      var r=messages[i].querySelector('.role');if(r&&r.textContent.trim()==='GAME MASTER')return messages[i];
    }
    return null;
  }
  function slotHtml(slot){
    var occupied=slot&&slot.occupied===true,id=occupied?String(slot.id||''):'',name=occupied?String(slot.name||id):'';
    var overlay=occupied?String(slot.overlayRef||''):'';
    var visual=overlay?'<img src="'+overlay.replace(/"/g,'&quot;')+'" onerror="this.style.display=\'none\'">':'<span class="combat-slot-placeholder"></span>';
    return '<div class="combat-slot '+(occupied?'':'empty')+'" data-combat-actor="'+id.replace(/"/g,'&quot;')+'"><div class="combat-slot-visual">'+visual+'</div><div class="combat-slot-name">'+(occupied?name:'EMPTY')+'</div></div>';
  }
  function ensureCombatUi(forceOpen){
    var key=combatKey(window.state||state);if(!key)return null;
    var frame=gmFrame();if(!frame)return null;
    var trigger=frame.querySelector('.combat-trigger');
    if(!trigger){trigger=document.createElement('button');trigger.type='button';trigger.className='combat-trigger';trigger.textContent='COMBAT';frame.appendChild(trigger);}
    var popup=frame.querySelector('.combat-popup');
    if(!popup){
      popup=document.createElement('div');popup.className='combat-popup';popup.hidden=true;frame.appendChild(popup);
      trigger.addEventListener('click',function(){popup.hidden=!popup.hidden;});
    }
    var s=window.state||state,c=s.combat||{},slots=combatSlots(s),entityName=String(c.entityName||prettyEntity(key));
    popup.innerHTML='<div class="combat-popup-head"><b>COMBAT</b><span>'+entityName+'</span></div><div class="combat-party-slots">'+slots.map(slotHtml).join('')+'</div><div class="combat-stage"><div class="combat-stage-visual"><div class="combat-stage-empty">OVERLAY<br>PENDING</div></div><div class="combat-live"><b>TURN ORDER</b><span>Kai bắt đầu lượt.</span><div class="combat-hp"><span>ENTITY '+(c.entityHp!=null?c.entityHp+'/'+c.entityMaxHp:'—')+'</span><span>KAI '+(c.playerHp!=null?c.playerHp+'/'+c.playerMaxHp:'—')+'</span></div></div></div>';
    if(forceOpen||lastAutoOpened!==key){popup.hidden=false;lastAutoOpened=key;}
    return popup;
  }
  function activateEvent(ev){
    var popup=ensureCombatUi(true);if(!popup)return;
    popup.querySelectorAll('.combat-slot').forEach(function(n){n.classList.toggle('active',n.getAttribute('data-combat-actor')===String(ev.actorId||''));});
    var visual=popup.querySelector('.combat-stage-visual');if(visual){
      visual.textContent='';
      if(ev.overlayRef){var img=document.createElement('img');img.src=ev.overlayRef;img.onerror=function(){visual.innerHTML='<div class="combat-stage-empty">OVERLAY<br>PENDING</div>';};visual.appendChild(img);}
      else visual.innerHTML='<div class="combat-stage-empty">'+String(ev.actorName||'').replace(/</g,'&lt;')+'<br>OVERLAY PENDING</div>';
    }
    var live=popup.querySelector('.combat-live');if(live)live.innerHTML='<b>'+String(ev.actorName||'TURN').replace(/</g,'&lt;')+'</b><span>'+String(ev.text||'').replace(/&/g,'&amp;').replace(/</g,'&lt;')+'</span><div class="combat-hp"><span>ENTITY '+Number(ev.entityHp||0)+'/'+Number(ev.entityMaxHp||0)+'</span><span>KAI '+Number(ev.playerHp||0)+'/'+Number(ev.playerMaxHp||0)+'</span></div>';
  }
  function appendPlaybackMessage(ev){
    var log=document.getElementById('log');if(!log)return;
    var article=document.createElement('article');article.className='message system combat-playback';
    var role=document.createElement('div');role.className='role';role.textContent='HỆ THỐNG';
    var text=document.createElement('div');text.className='text';text.textContent=String(ev.text||'');
    article.appendChild(role);article.appendChild(text);log.appendChild(article);log.scrollTop=log.scrollHeight;
  }

  window.backroomCombat=function(payload){
    try{
      var result=typeof payload==='string'?JSON.parse(payload):payload,events=Array.isArray(result.events)?result.events:[];
      document.querySelectorAll('.message.pending:not(.player)').forEach(function(n){n.remove();});
      document.querySelectorAll('.message.player.pending').forEach(function(n){n.classList.remove('pending');n.removeAttribute('data-pending');});
      busy=true;submitEl.disabled=true;var status=document.getElementById('status');if(status)status.textContent='Combat đang xử lý theo lượt…';
      ensureCombatUi(true);
      if(!events.length){window.backroomTurn(JSON.stringify(result.state));return;}
      var index=0;
      function step(){
        if(index>=events.length){window.backroomTurn(JSON.stringify(result.state));return;}
        var ev=events[index++];activateEvent(ev);appendPlaybackMessage(ev);setTimeout(step,2000);
      }
      step();
    }catch(e){window.backroomError(e&&e.message?e.message:'Combat payload không hợp lệ.');}
  };

  var log=document.getElementById('log');if(log&&window.MutationObserver){new MutationObserver(function(){ensureCombatUi(false);}).observe(log,{childList:true,subtree:true});}
  setTimeout(function(){ensureCombatUi(false);},0);
})();
</script>
'''
    if "</body>" not in html:
        raise RuntimeError("Party combat index body closing tag missing")
    html = html.replace("</body>", ui + "\n</body>", 1)

for required in (
    "PARTY_TURN_COMBAT_V2",
    "window.backroomCombat",
    "setTimeout(step,2000)",
    "combat-party-slots",
    "data-combat-actor",
    "HỆ THỐNG",
    "Kai bắt đầu lượt.",
):
    if required not in html:
        raise RuntimeError("Party combat UI contract missing: " + required)
INDEX.write_text(html, encoding="utf-8")

print("Party Turn Combat V2 installed: compact system logs, Kai-first party rotation, 2s playback, four prepared overlay slots and automatic COMBAT popup.")

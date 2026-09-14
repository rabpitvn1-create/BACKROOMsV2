from pathlib import Path

INDEX = Path(__file__).resolve().parent / "app/src/main/assets/index.html"
html = INDEX.read_text(encoding="utf-8")

script = r'''<script id="runtimeDebugUiV1">
/* RUNTIME_DEBUG_UI_V1 */
(function(){
  if(window.__runtimeDebugUiV1)return;window.__runtimeDebugUiV1=true;
  function send(name,payload){
    try{if(window.Android&&typeof Android.debugUiEvent==='function')Android.debugUiEvent(name,JSON.stringify(payload||{}));}catch(e){}
  }
  function currentTurn(){return Number(window.state&&state.turn)||0;}
  document.addEventListener('submit',function(e){
    if(e.target&&e.target.id==='form'){
      var a=document.getElementById('action');
      send('submitAction',{turn:currentTurn(),action:a?a.value:''});
    }
  },true);
  document.addEventListener('click',function(e){
    var id=e.target&&e.target.id;
    if(id==='saveButton'||id==='loadButton'||id==='newGameButton'||id==='deleteSaveButton'||id==='debugLogButton')send(id,{turn:currentTurn()});
  },true);
  if(typeof window.backroomCombatActorSwap==='function'){
    var oldSwap=window.backroomCombatActorSwap;
    window.backroomCombatActorSwap=function(raw){
      var p=raw;
      try{if(typeof raw==='string')p=JSON.parse(raw);}catch(ignore){}
      send('combat_actor_swap',{turn:currentTurn(),actor:p||{}});
      return oldSwap.apply(this,arguments);
    };
  }
  var oldTurn=window.backroomTurn;
  if(typeof oldTurn==='function')window.backroomTurn=function(raw){send('backroomTurn_received',{turn:currentTurn()});return oldTurn.apply(this,arguments);};
  var oldError=window.backroomError;
  if(typeof oldError==='function')window.backroomError=function(raw){send('backroomError_received',{turn:currentTurn(),message:String(raw||'')});return oldError.apply(this,arguments);};
  window.backroomDebugExport=function(message){
    var s=document.getElementById('status');if(s)s.textContent=String(message||'Log debug đã được lưu.');
    send('debug_export_complete',{turn:currentTurn()});
  };
})();
</script>
'''

if "RUNTIME_DEBUG_UI_V1" not in html:
    if "</body>" not in html:
        raise RuntimeError("runtime debug UI body anchor missing")
    html = html.replace("</body>", script + "</body>", 1)

for marker in (
    "RUNTIME_DEBUG_UI_V1",
    "Android.debugUiEvent",
    "submitAction",
    "saveButton",
    "loadButton",
    "newGameButton",
    "deleteSaveButton",
    "debugLogButton",
    "combat_actor_swap",
    "backroomTurn_received",
    "backroomError_received",
    "backroomDebugExport",
):
    if marker not in html:
        raise RuntimeError("runtime debug UI marker missing: " + marker)

INDEX.write_text(html, encoding="utf-8")
print("Runtime debug UI trace installed for submit/save/load/new-game/delete/export, turn/error callbacks and combat actor swaps.")

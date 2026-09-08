from pathlib import Path

ROOT = Path(__file__).resolve().parent
INDEX = ROOT / "app/src/main/assets/index.html"

html = INDEX.read_text(encoding="utf-8")

for marker in ('id="log"', ".message", "GAME MASTER", "</head>", "</body>"):
    if marker not in html:
        raise RuntimeError("Game Master storytelling frame requires final UI marker: " + marker)

STYLE_MARKER = "GAME_MASTER_STORYTELLING_FRAME_V1"
HEADER = "Game Master's Storytelling"

if STYLE_MARKER not in html:
    style = r'''<style id="gameMasterStorytellingFrameStyle">
/* GAME_MASTER_STORYTELLING_FRAME_V1
   Presentation-only frame. Intentionally does not change layout positioning,
   margins, padding, widths, heights, grid/flex order, or any other UI component. */
.message.game-master-storytelling{
  box-shadow:inset 0 0 0 1px #3b444d;
}
.message.game-master-storytelling .role{
  color:#c9d2da;
  box-shadow:0 1px 0 #2b3137;
}
.message.game-master-storytelling .role,
.message.game-master-storytelling .text{
  font-family:'BackroomPlay',system-ui,sans-serif;
  font-weight:700;
  letter-spacing:.025em;
}
</style>
'''

    script = r'''<script>
/* GAME_MASTER_STORYTELLING_FRAME_V1: decorate existing GM messages in place only. */
(function(){
  if(window.__gameMasterStorytellingFrameV1)return;
  window.__gameMasterStorytellingFrameV1=true;
  var HEADER="Game Master's Storytelling";
  var scheduled=false;

  function isGameMasterMessage(message){
    if(!message||message.classList.contains('player'))return false;
    if(message.dataset.gameMasterStorytelling==='1')return true;
    var role=message.querySelector('.role');
    var label=role?String(role.textContent||'').trim().toUpperCase():'';
    return label==='GAME MASTER'||label===HEADER.toUpperCase();
  }

  function decorateMessage(message){
    if(!isGameMasterMessage(message))return;
    var role=message.querySelector('.role');
    if(!role)return;
    message.classList.add('game-master-storytelling');
    message.dataset.gameMasterStorytelling='1';
    role.textContent=HEADER;
  }

  function decorateAll(){
    scheduled=false;
    var root=document.getElementById('log')||document;
    root.querySelectorAll('.message').forEach(decorateMessage);
  }

  function schedule(){
    if(scheduled)return;
    scheduled=true;
    requestAnimationFrame(decorateAll);
  }

  function install(){
    decorateAll();
    var root=document.getElementById('log');
    if(root)new MutationObserver(schedule).observe(root,{childList:true,subtree:true});
  }

  var oldRender=window.render;
  if(typeof oldRender==='function'){
    window.render=function(){
      var result=oldRender.apply(this,arguments);
      decorateAll();
      return result;
    };
  }

  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',install,{once:true});
  else install();
})();
</script>
'''

    html = html.replace("</head>", style + "</head>", 1)
    html = html.replace("</body>", script + "</body>", 1)

for required in (
    STYLE_MARKER,
    "game-master-storytelling",
    "Game Master's Storytelling",
    "box-shadow:inset 0 0 0 1px",
    "data.gameMasterStorytelling",
    "MutationObserver",
):
    if required not in html:
        raise RuntimeError("Game Master storytelling frame marker missing: " + required)

# Scope guard: this patch must not reposition any existing UI component.
for forbidden in (
    ".shell{",
    ".game{",
    ".topbar{",
    ".snapshot{",
    ".log{",
    ".composer{",
    ".side{",
    "grid-template-columns:",
    "position:fixed",
    "position:absolute",
):
    injected = html[html.index('id="gameMasterStorytellingFrameStyle"'):]
    if forbidden in injected:
        raise RuntimeError("Game Master frame must not reposition existing UI: " + forbidden)

INDEX.write_text(html, encoding="utf-8")
print("Game Master storytelling frame installed in place with header: Game Master's Storytelling.")

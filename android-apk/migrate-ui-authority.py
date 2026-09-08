from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent
WORKFLOW = REPO / ".github/workflows/build-backroom-apk.yml"
TEMP_WORKFLOW = REPO / ".github/workflows/run-ui-source-cleanup.yml"
SELF = Path(__file__)


def read(path: Path) -> str:
    if not path.is_file():
        raise RuntimeError(f"Missing migration input: {path.relative_to(REPO)}")
    return path.read_text(encoding="utf-8")


def write(path: Path, text: str) -> None:
    path.write_text(text, encoding="utf-8")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return text.replace(old, new, 1)


def regex_once(text: str, pattern: str, replacement: str, label: str, flags: int = 0) -> str:
    out, count = re.subn(pattern, replacement, text, count=1, flags=flags)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 regex match, found {count}")
    return out


# ---------------------------------------------------------------------------
# 1) Canonical UI authority: apply-android-ui.py owns Storytelling, Combat log,
#    Pressure Combat / popup presentation and all gameplay typography.
# ---------------------------------------------------------------------------
apply_path = ROOT / "apply-android-ui.py"
apply = read(apply_path)

if "ANDROID_GAMEPLAY_PRESENTATION_V2" in apply:
    raise RuntimeError("Canonical gameplay presentation already exists; migration must run once")

apply = replace_once(apply, "from pathlib import Path\nimport re\n", "from pathlib import Path\nimport json\nimport re\n", "apply UI json import")

semantic_builder = r'''
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"


def _ui_read_optional(path: Path) -> str:
    return path.read_text(encoding="utf-8") if path.is_file() else ""


def _ui_unique(values):
    seen = set()
    out = []
    for value in values:
        clean = str(value or "").strip()
        key = clean.casefold()
        if clean and key not in seen:
            seen.add(key)
            out.append(clean)
    return sorted(out, key=lambda value: (-len(value), value.casefold()))


# Build semantic literals from the same authoritative gameplay sources that generate
# the final APK. Fallback patch sources are build-time source catalogs, not a second UI layer.
_combat_source = _ui_read_optional(CORE / "CombatRuntime.kt")
_item_source = _ui_read_optional(CORE / "ItemCatalog.kt") + "\n" + _ui_read_optional(CORE / "HealingItems.kt")
_skill_path = CORE / "CompanionSkillCatalog.kt"
_skill_source = _ui_read_optional(_skill_path if _skill_path.is_file() else ROOT / "patch-companion-skills-ui.py")
_equipment_path = CORE / "CharacterEquipmentSystem.kt"
_equipment_source = _ui_read_optional(_equipment_path if _equipment_path.is_file() else ROOT / "patch-character-status-equipment-system.py")

semantic_entities = _ui_unique(re.findall(r'Profile\("[^"]+",\s*"([^"\\]+)"', _combat_source))
semantic_items = _ui_unique(
    re.findall(r'displayName\s*=\s*"([^"\\]+)"', _item_source)
    + re.findall(r'const val (?:BANDAGE_NAME|ANTISEPTIC_NAME)\s*=\s*"([^"\\]+)"', _item_source)
)
semantic_skills = _ui_unique(re.findall(r'\bs\("([^"\\]+)"\s*,', _skill_source))
semantic_equipment = _ui_unique(
    re.findall(r'EquipmentDefinition\([\s\S]{0,360}?\bname\s*=\s*"([^"\\]+)"', _equipment_source)
    + re.findall(r'EquipmentComponent\("([^"\\]+)"', _equipment_source)
)
semantic_effects = _ui_unique(
    re.findall(r'\bability\("([^"\\]+)"', _equipment_source)
    + [
        "Guilty Crown Override", "Quick Step", "Silent Lullaby", "Evasion",
        "Stun", "Bleed", "Burn", "Poison", "WOUNDED", "CRITICAL", "DESTROYED",
    ]
)
semantic_names = _ui_unique([
    'Lucia "Lục"', "Lucia Lục", "Kai Akechi", "An Nhiên", "Syvial", "Iris", "Lucia", "Kai", "Diệp Minh",
])
'''
apply = replace_once(
    apply,
    'html = INDEX.read_text(encoding="utf-8")\n',
    'html = INDEX.read_text(encoding="utf-8")\n' + semantic_builder,
    "semantic catalog insertion",
)

old_hud_css = "#combatHud{margin:0 calc(var(--android-safe-right) + var(--ui-edge)) var(--ui-gap) calc(var(--android-safe-left) + var(--ui-edge));border-radius:var(--panel-radius)}"
canonical_gameplay_css = r'''/* ANDROID_GAMEPLAY_PRESENTATION_V2: one presentation/typography authority. */
:root{
  --gameplay-font:'Roboto',system-ui,sans-serif;
  --semantic-item:#36f0c3;
  --semantic-damage:#e23b50;
  --semantic-heal:#78f59a;
  --semantic-entity:#f0c979;
  --semantic-skill:#c9d2da;
  --semantic-effect:#e2e8ec;
  --semantic-equipment:#9fc8e0;
  --semantic-name:#eef1f3;
  --semantic-hp:#d8dee3;
}
@font-face{font-family:'BackroomPlay';src:url('fonts/Play-Bold.ttf') format('truetype');font-style:normal;font-weight:700;font-display:swap}
.snapshot .snapshot-character{right:8px!important}
.log{padding-top:calc(var(--ui-gap,10px) + 8px)!important}
.message.storytelling,.message.combat{width:calc(100% - 10px);margin-left:auto;margin-right:auto}
.message.storytelling{
  border:1px solid #3b444d;
  border-left:3px solid #71808a;
  background:#171e23;
  box-shadow:none;
  padding:0;
  overflow:hidden;
  font-family:var(--gameplay-font);
  font-weight:800;
}
.storytelling-header{
  padding:8px 10px 7px;
  color:#c9d2da;
  border-bottom:1px solid #2b3137;
  font-family:var(--gameplay-font);
  font-size:10px;
  font-weight:800;
  letter-spacing:.12em;
}
.storytelling-body{padding:10px}
.storytelling-segment{white-space:pre-wrap;line-height:1.55;font-family:var(--gameplay-font);font-weight:800}
.storytelling-segment+.storytelling-segment{margin-top:14px}
.message.combat,.message.combat .role,.message.combat .text{
  font-family:var(--gameplay-font);
  font-weight:800;
}
#combatHud{
  display:none;
  margin:0 calc(var(--android-safe-right) + var(--ui-edge)) var(--ui-gap) calc(var(--android-safe-left) + var(--ui-edge));
  border:1px solid #444b52;
  border-radius:var(--panel-radius);
  background:#0b0e10;
  padding:10px;
  font-family:var(--gameplay-font);
  font-weight:800;
}
#combatHud.active{display:block}
.combat-title{display:flex;justify-content:space-between;align-items:center;gap:8px;font-size:12px;font-weight:800;letter-spacing:.08em;margin-bottom:7px}
.combat-row{display:grid;grid-template-columns:70px 1fr 62px;align-items:center;gap:7px;margin:5px 0;font-size:11px;font-weight:800}
.combat-bar{height:12px;background:#252b30;border:1px solid #343c43;overflow:hidden}
.combat-fill{height:100%;background:linear-gradient(90deg,#757f88,#d8dee3);transition:width .18s ease}
.combat-meta{display:flex;flex-wrap:wrap;gap:6px;margin-top:8px}
.combat-meta span{border:1px solid #343c43;padding:3px 5px;font-size:10px;color:#c8d0d6;font-weight:800}
.combat-telegraph{margin-top:7px;font-size:11px;color:#f0c979;font-weight:800}
.combat-popup-button{appearance:none;border:1px solid #69747d;background:#20282e;color:#f1f4f6;border-radius:6px;padding:4px 8px;font:800 10px/1 var(--gameplay-font);letter-spacing:.08em;cursor:pointer}
.combat-popup-button:active{transform:translateY(1px)}
#combatPopup[hidden]{display:none!important}
#combatPopup{position:fixed;inset:0;z-index:12000;background:rgba(0,0,0,.72);display:flex;align-items:center;justify-content:center;padding:16px;font-family:var(--gameplay-font);font-weight:800}
.combat-popup-sheet{width:min(520px,100%);max-height:min(82vh,720px);overflow:auto;background:#0b0e10;border:1px solid #4b555e;border-radius:12px;box-shadow:0 20px 55px rgba(0,0,0,.55);padding:14px;color:#edf1f4}
.combat-popup-head{display:flex;align-items:center;justify-content:space-between;gap:10px}.combat-popup-head h2{font-size:16px;font-weight:800;letter-spacing:.12em;margin:0}.combat-popup-auto{font-size:10px;font-weight:800;border:1px solid #4b555e;border-radius:999px;padding:3px 7px;color:#c9d2d9}.combat-popup-close{border:0;background:transparent;color:#e8edf0;font-size:24px;font-weight:800;line-height:1;cursor:pointer;padding:2px 6px}
.combat-popup-target,.combat-popup-current{border:1px solid #343c43;background:#11161a;border-radius:8px;padding:9px;margin-top:10px;font-weight:800}.combat-popup-target{display:flex;justify-content:space-between;gap:10px;font-size:12px}.combat-popup-current{font-size:12px;letter-spacing:.04em}
.combat-popup-order{display:flex;gap:6px;overflow-x:auto;padding:10px 0 4px;scrollbar-width:thin}.combat-turn-chip{flex:0 0 auto;border:1px solid #343c43;border-radius:999px;padding:5px 8px;font-size:10px;font-weight:800;white-space:nowrap;color:#c7d0d6}.combat-turn-chip.current{border-color:#e2e8ec;color:#fff;background:#283139}
.combat-popup-party{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:7px;margin-top:10px}.combat-popup-slot{min-width:0;border:1px solid #343c43;border-radius:8px;padding:8px;background:#101519;text-align:center}.combat-popup-slot.current{border-color:#e2e8ec;background:#252d33}.combat-popup-slot.empty{opacity:.45}.combat-popup-slot strong{display:block;font-size:11px;font-weight:800;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.combat-popup-slot span{display:block;font-size:9px;font-weight:800;color:#9da8b0;margin-top:3px}
.gameplay-semantic{font-family:'BackroomPlay',var(--gameplay-font);font-weight:700;letter-spacing:.025em}
.gameplay-sem-name{color:var(--semantic-name)}
.gameplay-sem-entity{color:var(--semantic-entity)}
.gameplay-sem-skill{color:var(--semantic-skill)}
.gameplay-sem-effect{color:var(--semantic-effect)}
.gameplay-sem-equipment{color:var(--semantic-equipment)}
.gameplay-sem-item{color:var(--semantic-item);text-decoration-line:underline;text-decoration-thickness:1px;text-underline-offset:.16em;text-decoration-color:var(--semantic-item)}
.gameplay-sem-damage{color:var(--semantic-damage)}
.gameplay-sem-heal{color:var(--semantic-heal)}
.gameplay-sem-hp{color:var(--semantic-hp)}
@media(max-width:430px){.combat-popup-party{grid-template-columns:repeat(2,minmax(0,1fr))}.combat-popup-sheet{padding:12px}}
@media(prefers-reduced-motion:reduce){.combat-popup-button:active{transform:none}}
'''
apply = replace_once(apply, old_hud_css, canonical_gameplay_css, "canonical gameplay CSS replacement")

presentation_source = r'''
presentation_script_template = r'''<script id="androidGameplayPresentation">
/* ANDROID_GAMEPLAY_PRESENTATION_V2 */
(function(){
  if(window.__androidGameplayPresentationV2)return;window.__androidGameplayPresentationV2=true;
  var NAMES=__NAMES__,ENTITIES=__ENTITIES__,SKILLS=__SKILLS__,EFFECTS=__EFFECTS__,ITEMS=__ITEMS__,EQUIPMENT=__EQUIPMENT__;
  var scheduled=false,normalizing=false;

  function escRe(value){return String(value||'').replace(/[.*+?^${}()|[\]\\]/g,'\\$&');}
  function literal(value,cls,priority){return {re:new RegExp(escRe(value),'gi'),cls:cls,priority:priority};}
  function patterns(){
    var out=[
      {re:/-\s*\d+(?:[.,]\d+)?\s*HP\b/gi,cls:'gameplay-sem-damage',priority:120},
      {re:/\+\s*\d+(?:[.,]\d+)?\s*HP\b/gi,cls:'gameplay-sem-heal',priority:120},
      {re:/(?:\d+(?:[.,]\d+)?%\s*)?DMG\b/gi,cls:'gameplay-sem-damage',priority:118},
      {re:/\b\d+\s*\/\s*\d+(?:\s*HP)?\b/gi,cls:'gameplay-sem-hp',priority:116},
      {re:/\bHP\b/gi,cls:'gameplay-sem-hp',priority:110}
    ];
    ITEMS.forEach(function(v){out.push(literal(v,'gameplay-sem-item',100));});
    EQUIPMENT.forEach(function(v){out.push(literal(v,'gameplay-sem-equipment',96));});
    SKILLS.forEach(function(v){out.push(literal(v,'gameplay-sem-skill',94));});
    EFFECTS.forEach(function(v){out.push(literal(v,'gameplay-sem-effect',92));});
    ENTITIES.forEach(function(v){out.push(literal(v,'gameplay-sem-entity',90));});
    NAMES.forEach(function(v){out.push(literal(v,'gameplay-sem-name',88));});
    return out;
  }
  function ranges(text){
    var candidates=[];
    patterns().forEach(function(p){p.re.lastIndex=0;var m;while((m=p.re.exec(text))!==null){if(!m[0]){p.re.lastIndex++;continue;}candidates.push({start:m.index,end:m.index+m[0].length,cls:p.cls,priority:p.priority});}});
    candidates.sort(function(a,b){return a.start-b.start||b.priority-a.priority||(b.end-b.start)-(a.end-a.start);});
    var selected=[];
    candidates.forEach(function(c){for(var i=0;i<selected.length;i++){var s=selected[i];if(c.start<s.end&&c.end>s.start)return;}selected.push(c);});
    selected.sort(function(a,b){return a.start-b.start;});return selected;
  }
  function decorateTextNode(node){
    if(!node||!node.parentElement||node.parentElement.closest('[data-gameplay-semantic]'))return;
    if(node.parentElement.closest('script,style,textarea,button'))return;
    var text=node.nodeValue||'';if(!text.trim())return;var rs=ranges(text);if(!rs.length)return;
    var frag=document.createDocumentFragment(),cursor=0;
    rs.forEach(function(r){if(r.start>cursor)frag.appendChild(document.createTextNode(text.slice(cursor,r.start)));var span=document.createElement('span');span.className='gameplay-semantic '+r.cls;span.dataset.gameplaySemantic='1';span.textContent=text.slice(r.start,r.end);frag.appendChild(span);cursor=r.end;});
    if(cursor<text.length)frag.appendChild(document.createTextNode(text.slice(cursor)));node.parentNode.replaceChild(frag,node);
  }
  function decorateRoot(root){
    if(!root)return;var walker=document.createTreeWalker(root,NodeFilter.SHOW_TEXT,null),nodes=[],node;while((node=walker.nextNode()))nodes.push(node);nodes.forEach(decorateTextNode);
  }
  function roleOf(message){var role=message&&message.querySelector('.role');return role?String(role.textContent||'').trim().toUpperCase():'';}
  function isWarning(message){return !!(message&&message.classList.contains('warning'));}
  function normalizeLog(){
    var log=document.getElementById('log');if(!log||normalizing)return;normalizing=true;
    try{
      var children=Array.prototype.slice.call(log.children),i=0;
      while(i<children.length){
        var message=children[i],role=roleOf(message);
        if(role==='COMBAT'){message.classList.add('combat');decorateRoot(message.querySelector('.text')||message);i++;continue;}
        if(role!=='GAME MASTER'||isWarning(message)){i++;continue;}
        var group=[];
        while(i<children.length&&roleOf(children[i])==='GAME MASTER'&&!isWarning(children[i])){group.push(children[i]);i++;}
        if(!group.length)continue;
        var article=document.createElement('article');article.className='message storytelling';article.dataset.storytelling='1';
        var header=document.createElement('div');header.className='storytelling-header';header.textContent='Storytelling';
        var body=document.createElement('div');body.className='storytelling-body';
        group.forEach(function(old){var segment=document.createElement('div');segment.className='storytelling-segment';var text=old.querySelector('.text');if(text){while(text.firstChild)segment.appendChild(text.firstChild);}body.appendChild(segment);});
        article.appendChild(header);article.appendChild(body);log.insertBefore(article,group[0]);group.forEach(function(old){old.remove();});decorateRoot(body);
      }
      log.querySelectorAll('.message.combat .text,.storytelling-body').forEach(decorateRoot);
    }finally{normalizing=false;}
  }
  function decorateGameplay(){
    scheduled=false;normalizeLog();
    var hud=document.getElementById('combatHud');if(hud)decorateRoot(hud);
    var popup=document.getElementById('combatPopup');if(popup)decorateRoot(popup);
  }
  function schedule(){if(scheduled)return;scheduled=true;requestAnimationFrame(decorateGameplay);}
  function install(){decorateGameplay();var root=document.body;if(root)new MutationObserver(schedule).observe(root,{childList:true,subtree:true});}
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',install,{once:true});else install();
  window.backroomNormalizeGameplayPresentation=schedule;
})();
</script>'''
presentation_script = presentation_script_template.replace("__NAMES__", json.dumps(semantic_names, ensure_ascii=False))
presentation_script = presentation_script.replace("__ENTITIES__", json.dumps(semantic_entities, ensure_ascii=False))
presentation_script = presentation_script.replace("__SKILLS__", json.dumps(semantic_skills, ensure_ascii=False))
presentation_script = presentation_script.replace("__EFFECTS__", json.dumps(semantic_effects, ensure_ascii=False))
presentation_script = presentation_script.replace("__ITEMS__", json.dumps(semantic_items, ensure_ascii=False))
presentation_script = presentation_script.replace("__EQUIPMENT__", json.dumps(semantic_equipment, ensure_ascii=False))
if "ANDROID_GAMEPLAY_PRESENTATION_V2" not in html:
    if "</body>" not in html:
        raise RuntimeError("Canonical gameplay presentation body anchor missing")
    html = html.replace("</body>", presentation_script + "\n</body>", 1)

'''
apply = replace_once(apply, "for forbidden in (\n", presentation_source + "for forbidden in (\n", "canonical gameplay JS insertion")
apply = replace_once(
    apply,
    '    "ANDROID_THREE_ACTIONS_V1",\n',
    '    "ANDROID_THREE_ACTIONS_V1",\n    "ANDROID_GAMEPLAY_PRESENTATION_V2",\n',
    "canonical gameplay validation marker",
)
write(apply_path, apply)


# ---------------------------------------------------------------------------
# 2) Pressure Combat keeps runtime/HUD rendering only. Visual CSS now belongs to
#    apply-android-ui.py and is removed from this gameplay patch.
# ---------------------------------------------------------------------------
pressure_path = ROOT / "patch-pressure-combat.py"
pressure = read(pressure_path)
pressure = regex_once(
    pressure,
    r"hud = r'''\n<style id=\"pressureCombatStyle\">[\s\S]*?</style>\n<script>",
    "hud = r'''\n<script>",
    "remove Pressure Combat presentation CSS",
)
if "pressureCombatStyle" in pressure:
    raise RuntimeError("Pressure Combat legacy style survived source cleanup")
write(pressure_path, pressure)


# ---------------------------------------------------------------------------
# 3) Auto Party Combat keeps combat locking / popup behavior, not popup CSS.
# ---------------------------------------------------------------------------
auto_path = ROOT / "patch-auto-party-combat-final.py"
auto = read(auto_path)
auto = regex_once(
    auto,
    r"\nstyle = r'''<style id=\"autoPartyCombatStyle\">[\s\S]*?</style>\n'''\n",
    "\n",
    "remove Auto Party popup presentation CSS",
)
auto = replace_once(
    auto,
    '    if "</head>" not in html or "</body>" not in html:\n        raise RuntimeError("Auto-party combat HTML anchors missing")\n    html = html.replace("</head>", style + "</head>", 1)\n    html = html.replace("</body>", script + "</body>", 1)\n',
    '    if "</body>" not in html:\n        raise RuntimeError("Auto-party combat HTML body anchor missing")\n    html = html.replace("</body>", script + "</body>", 1)\n',
    "remove Auto Party head-style injection",
)
if "autoPartyCombatStyle" in auto or "style + \"</head>\"" in auto:
    raise RuntimeError("Auto Party legacy presentation style survived source cleanup")
write(auto_path, auto)


# ---------------------------------------------------------------------------
# 4) True-turn finalizer remains gameplay/autoplay authority, but loses the extra
#    .log/.message CSS layer. Its three useful layout declarations live in the
#    canonical Android UI stylesheet above.
# ---------------------------------------------------------------------------
true_turn_path = ROOT / "patch-true-turn-combat-final.py"
true_turn = read(true_turn_path)
true_turn = regex_once(
    true_turn,
    r"\n# Party overlays need a real safe inset\.[\s\S]*?if \"TRUE_TURN_COMBAT_UI_V2\" not in html:\n    if \"</head>\" not in html:\n        raise RuntimeError\(\"True-turn UI head anchor missing\"\)\n    html = html\.replace\(\"</head>\", ui_style \+ \"</head>\", 1\)\n",
    "\n",
    "remove true-turn presentation CSS layer",
)
for marker in (
    '    "TRUE_TURN_COMBAT_UI_V2",\n',
    '    ".snapshot .snapshot-character{right:8px!important}",\n',
    '    ".log .message:not(.player){width:calc(100% - 10px)",\n',
):
    if marker not in true_turn:
        raise RuntimeError("true-turn validation marker missing before cleanup: " + marker.strip())
    true_turn = true_turn.replace(marker, "", 1)
if "trueTurnCombatUiStyle" in true_turn or "TRUE_TURN_COMBAT_UI_V2" in true_turn:
    raise RuntimeError("True-turn legacy presentation layer survived source cleanup")
write(true_turn_path, true_turn)


# ---------------------------------------------------------------------------
# 5) Remove nested presentation finalizers from unrelated gameplay scripts.
# ---------------------------------------------------------------------------
party_path = ROOT / "patch-party-turn-combat-final.py"
party = read(party_path)
party = replace_once(
    party,
    'runpy.run_path(str(ROOT / "patch-game-master-storytelling-frame-final.py"), run_name="__main__")\n',
    "",
    "remove nested Game Master frame finalizer",
)
write(party_path, party)

entity_rates_path = ROOT / "patch-entity-rates-drops-final.py"
entity_rates = read(entity_rates_path)
entity_rates = regex_once(
    entity_rates,
    r"\n# Presentation-only final layer\.[\s\S]*?runpy\.run_path\(str\(typography_final\), run_name=\"__main__\"\)\n?",
    "\n",
    "remove nested combat typography finalizer",
)
# runpy was only used by the removed typography tail in this file.
entity_rates = entity_rates.replace("import runpy\n", "", 1)
if "patch-combat-typography-final.py" in entity_rates or "typography_final" in entity_rates:
    raise RuntimeError("Typography finalizer reference survived Entity policy cleanup")
write(entity_rates_path, entity_rates)


# ---------------------------------------------------------------------------
# 6) Replace the deleted typography finalizer with a build-asset sync only.
#    This script never touches HTML/CSS/JS.
# ---------------------------------------------------------------------------
font_sync_path = ROOT / "sync-play-font-assets.py"
font_sync = r'''from pathlib import Path
import urllib.request

ROOT = Path(__file__).resolve().parent
FONT_DIR = ROOT / "app/src/main/assets/fonts"
FONT_PATH = FONT_DIR / "Play-Bold.ttf"
LICENSE_PATH = FONT_DIR / "OFL-Play.txt"
PLAY_COMMIT = "e36afc7567e2c4dbe669ca5810e0c77f307295a0"
FONT_URL = f"https://raw.githubusercontent.com/google/fonts/{PLAY_COMMIT}/ofl/play/Play-Bold.ttf"
LICENSE_URL = f"https://raw.githubusercontent.com/google/fonts/{PLAY_COMMIT}/ofl/play/OFL.txt"


def fetch(url: str, label: str) -> bytes:
    request = urllib.request.Request(url, headers={"User-Agent": "BACKROOMsV2-ui-font-assets/1.0"})
    with urllib.request.urlopen(request, timeout=45) as response:
        data = response.read()
    if not data:
        raise RuntimeError(f"{label} download returned empty data")
    return data


FONT_DIR.mkdir(parents=True, exist_ok=True)
font = FONT_PATH.read_bytes() if FONT_PATH.is_file() else fetch(FONT_URL, "Play Bold font")
if len(font) < 150_000 or font[:4] != b"\x00\x01\x00\x00":
    raise RuntimeError("Play Bold font asset failed validation")
FONT_PATH.write_bytes(font)
license_data = LICENSE_PATH.read_bytes() if LICENSE_PATH.is_file() else fetch(LICENSE_URL, "Play OFL license")
license_text = license_data.decode("utf-8", errors="strict")
if "SIL OPEN FONT LICENSE" not in license_text.upper():
    raise RuntimeError("Play Bold license failed validation")
LICENSE_PATH.write_text(license_text, encoding="utf-8")
print("Play Bold asset synchronized for semantic gameplay spans only.")
'''
write(font_sync_path, font_sync)


# ---------------------------------------------------------------------------
# 7) Delete obsolete pure presentation finalizers. The user explicitly requested
#    removal rather than another final/final2 patch layer.
# ---------------------------------------------------------------------------
obsolete = [
    ROOT / "patch-game-master-storytelling-frame-final.py",
    ROOT / "patch-combat-typography-final.py",
]
for path in obsolete:
    if not path.is_file():
        raise RuntimeError("Obsolete presentation source already missing: " + path.name)
    path.unlink()


# ---------------------------------------------------------------------------
# 8) Build chain: font asset sync is explicit; final APK is guarded against old
#    presentation markers so layer regressions fail CI instead of silently stacking.
# ---------------------------------------------------------------------------
workflow = read(WORKFLOW)
workflow = replace_once(
    workflow,
    "patch-runtime-canon-final.py apply-android-ui.py patch-android-launch-safety.py",
    "patch-runtime-canon-final.py sync-play-font-assets.py apply-android-ui.py patch-android-launch-safety.py",
    "insert font asset sync before canonical UI",
)
workflow = replace_once(
    workflow,
    "          test -s android-apk/patch-pressure-combat.py\n          test -s android-apk/patch-party-turn-combat-final.py\n",
    "          test -s android-apk/patch-pressure-combat.py\n          test -s android-apk/patch-party-turn-combat-final.py\n          test -s android-apk/sync-play-font-assets.py\n          test ! -e android-apk/patch-game-master-storytelling-frame-final.py\n          test ! -e android-apk/patch-combat-typography-final.py\n",
    "verify canonical UI source ownership",
)
workflow = replace_once(
    workflow,
    "          grep -q 'data:image/webp;base64,' android-apk/app/src/main/assets/index.html\n",
    "          grep -q 'data:image/webp;base64,' android-apk/app/src/main/assets/index.html\n          grep -q 'ANDROID_GAMEPLAY_PRESENTATION_V2' android-apk/app/src/main/assets/index.html\n          grep -q \"font-family:var(--gameplay-font)\" android-apk/app/src/main/assets/index.html\n          grep -q \"header.textContent='Storytelling'\" android-apk/app/src/main/assets/index.html\n          grep -q \"font-family:'BackroomPlay'\" android-apk/app/src/main/assets/index.html\n          ! grep -q 'GAME_MASTER_STORYTELLING_FRAME_V1' android-apk/app/src/main/assets/index.html\n          ! grep -q 'GAME_MASTER_PLAY_FONT_V1' android-apk/app/src/main/assets/index.html\n          ! grep -q 'COMBAT_TYPOGRAPHY_V1' android-apk/app/src/main/assets/index.html\n          ! grep -q 'TRUE_TURN_COMBAT_UI_V2' android-apk/app/src/main/assets/index.html\n          ! grep -q 'autoPartyCombatStyle' android-apk/app/src/main/assets/index.html\n          ! grep -q 'pressureCombatStyle' android-apk/app/src/main/assets/index.html\n",
    "add generated UI anti-layer guards",
)
workflow = replace_once(
    workflow,
    "          unzip -p Backroom-1.1.69.apk assets/index.html | grep -q 'data:image/webp;base64,'\n",
    "          unzip -p Backroom-1.1.69.apk assets/index.html | grep -q 'data:image/webp;base64,'\n          unzip -l Backroom-1.1.69.apk | grep -q 'assets/fonts/Play-Bold.ttf'\n          unzip -p Backroom-1.1.69.apk assets/index.html | grep -q 'ANDROID_GAMEPLAY_PRESENTATION_V2'\n          ! unzip -p Backroom-1.1.69.apk assets/index.html | grep -q 'GAME_MASTER_STORYTELLING_FRAME_V1'\n          ! unzip -p Backroom-1.1.69.apk assets/index.html | grep -q 'COMBAT_TYPOGRAPHY_V1'\n",
    "add packaged APK UI authority guards",
)
write(WORKFLOW, workflow)


# Final source-level assertions before committing the migration result.
combined = "\n".join(read(path) for path in [
    apply_path, pressure_path, auto_path, true_turn_path, party_path, entity_rates_path, WORKFLOW,
])
for forbidden in (
    "patch-game-master-storytelling-frame-final.py",
    "patch-combat-typography-final.py",
    "gameMasterStorytellingFrameStyle",
    "combatTypographyStyle",
    "trueTurnCombatUiStyle",
    "autoPartyCombatStyle",
    "pressureCombatStyle",
):
    if forbidden in combined:
        raise RuntimeError("Legacy UI layer/reference survived source migration: " + forbidden)
for required in (
    "ANDROID_GAMEPLAY_PRESENTATION_V2",
    "semantic_equipment",
    "semantic_effects",
    "Storytelling",
    "sync-play-font-assets.py",
):
    if required not in combined:
        raise RuntimeError("Canonical gameplay UI contract missing after source migration: " + required)

# Remove one-time migration machinery from the final tree. The workflow that invoked
# this file is already running, so deleting both files prevents a second migration run.
if TEMP_WORKFLOW.is_file():
    TEMP_WORKFLOW.unlink()
SELF.unlink()
print("UI source migration complete: presentation layers consolidated into apply-android-ui.py.")

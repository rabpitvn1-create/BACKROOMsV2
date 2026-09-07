from pathlib import Path
import json
import re
import urllib.request

ROOT = Path(__file__).resolve().parent
ASSETS = ROOT / "app/src/main/assets"
INDEX = ASSETS / "index.html"
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
SKILL_CATALOG = CORE / "CompanionSkillCatalog.kt"
SKILL_SOURCE_FALLBACK = ROOT / "patch-companion-skills-ui.py"
ITEM_CATALOG = CORE / "ItemCatalog.kt"
HEALING_ITEMS = CORE / "HealingItems.kt"
FONT_DIR = ASSETS / "fonts"
FONT_PATH = FONT_DIR / "Play-Bold.ttf"
FONT_LICENSE_PATH = FONT_DIR / "OFL-Play.txt"

# Pin the upstream font source to an immutable Google Fonts commit. Play Bold is
# a compact futuristic display face with Vietnamese glyph coverage. It is used
# only for combat names/skills, never for body copy.
PLAY_COMMIT = "e36afc7567e2c4dbe669ca5810e0c77f307295a0"
PLAY_FONT_URL = (
    "https://raw.githubusercontent.com/google/fonts/"
    + PLAY_COMMIT
    + "/ofl/play/Play-Bold.ttf"
)
PLAY_LICENSE_URL = (
    "https://raw.githubusercontent.com/google/fonts/"
    + PLAY_COMMIT
    + "/ofl/play/OFL.txt"
)


def fetch_bytes(url: str, label: str) -> bytes:
    request = urllib.request.Request(
        url,
        headers={"User-Agent": "BACKROOMsV2-combat-typography/1.0"},
    )
    with urllib.request.urlopen(request, timeout=45) as response:
        data = response.read()
    if not data:
        raise RuntimeError(f"{label} download returned an empty response")
    return data


def ensure_play_font() -> None:
    FONT_DIR.mkdir(parents=True, exist_ok=True)

    font = FONT_PATH.read_bytes() if FONT_PATH.is_file() else fetch_bytes(PLAY_FONT_URL, "Play Bold font")
    if len(font) < 150_000:
        raise RuntimeError(f"Play Bold font unexpectedly small: {len(font)} bytes")
    if font[:4] != b"\x00\x01\x00\x00":
        raise RuntimeError("Play Bold font is not a TrueType sfnt")
    FONT_PATH.write_bytes(font)

    license_data = (
        FONT_LICENSE_PATH.read_bytes()
        if FONT_LICENSE_PATH.is_file()
        else fetch_bytes(PLAY_LICENSE_URL, "Play OFL license")
    )
    license_text = license_data.decode("utf-8", errors="strict")
    if "SIL OPEN FONT LICENSE" not in license_text.upper():
        raise RuntimeError("Play font license is not the expected SIL Open Font License")
    FONT_LICENSE_PATH.write_text(license_text, encoding="utf-8")


def unique_longest_first(values: list[str]) -> list[str]:
    seen: set[str] = set()
    out: list[str] = []
    for value in values:
        clean = value.strip()
        key = clean.casefold()
        if not clean or key in seen:
            continue
        seen.add(key)
        out.append(clean)
    return sorted(out, key=lambda value: (-len(value), value.casefold()))


def extract_skill_names() -> list[str]:
    source_path = SKILL_CATALOG if SKILL_CATALOG.is_file() else SKILL_SOURCE_FALLBACK
    if not source_path.is_file():
        raise RuntimeError("Combat typography cannot find the authoritative skill source")
    source = source_path.read_text(encoding="utf-8")
    names = re.findall(r'\bs\("([^"\\]+)"\s*,', source)
    names = unique_longest_first(names)
    if not names or "The Last Requiem" not in names:
        raise RuntimeError("Combat typography skill extraction did not find the expected catalog")
    return names


def extract_item_names() -> list[str]:
    if not ITEM_CATALOG.is_file():
        raise RuntimeError("Combat typography cannot find ItemCatalog.kt")
    source = ITEM_CATALOG.read_text(encoding="utf-8")
    names = re.findall(r'displayName\s*=\s*"([^"\\]+)"', source)

    if HEALING_ITEMS.is_file():
        healing = HEALING_ITEMS.read_text(encoding="utf-8")
        names += re.findall(r'const val (?:BANDAGE_NAME|ANTISEPTIC_NAME)\s*=\s*"([^"\\]+)"', healing)

    names = unique_longest_first(names)
    for expected in ("Chai nước", "Greek Fire", "Liquid Pain"):
        if expected not in names:
            raise RuntimeError("Combat typography ItemCatalog extraction missing: " + expected)
    return names


ensure_play_font()
skill_names = extract_skill_names()
item_names = extract_item_names()

html = INDEX.read_text(encoding="utf-8")
for marker in ("PRESSURE_COMBAT_HUD_V1", 'id="log"', 'class="message'):
    if marker not in html:
        raise RuntimeError("Combat typography requires final UI marker: " + marker)

marker = "COMBAT_TYPOGRAPHY_V1"
if marker not in html:
    style = r'''<style id="combatTypographyStyle">
/* COMBAT_TYPOGRAPHY_V1: presentation only, no combat/state/canon mutation. */
@font-face{
  font-family:'BackroomPlay';
  src:url('fonts/Play-Bold.ttf') format('truetype');
  font-style:normal;
  font-weight:700;
  font-display:swap;
}
.combat-sem-item{
  color:#36f0c3;
  font-weight:800;
  text-decoration-line:underline;
  text-decoration-thickness:1px;
  text-underline-offset:.16em;
  text-decoration-color:#36f0c3;
}
.combat-sem-damage{color:#e23b50;font-weight:800}
.combat-sem-heal{color:#78f59a;font-weight:800}
.combat-sem-name,.combat-sem-skill{
  font-family:'BackroomPlay',system-ui,sans-serif;
  font-weight:700;
  letter-spacing:.025em;
}
#combatHud .combat-title span:last-child,
#combatHud .combat-row>b,
#combatPopupTarget,
#combatPopupCurrent strong,
#combatPopupOrder b,
#combatPopupParty b{
  font-family:'BackroomPlay',system-ui,sans-serif;
  font-weight:700;
  letter-spacing:.025em;
}
</style>
'''

    script_template = r'''<script>
/* COMBAT_TYPOGRAPHY_V1: semantic styling of rendered combat text only. */
(function(){
  if(window.__combatTypographyV1)return;window.__combatTypographyV1=true;
  var ITEM_NAMES=__ITEMS__;
  var SKILL_NAMES=__SKILLS__;
  var STATIC_NAMES=['Kai Akechi','Kai','Iris','Syvial','Lucia "Lục"','Lucia Lục','Lucia','An Nhiên'];
  var scheduled=false;

  function escRe(value){return String(value||'').replace(/[.*+?^${}()|[\]\\]/g,'\\$&');}
  function addName(list,value){value=String(value||'').trim();if(value&&list.indexOf(value)<0)list.push(value);}
  function liveNames(){
    var names=STATIC_NAMES.slice();
    try{
      var s=(typeof state!=='undefined'&&state)?state:null;
      var c=s&&s.combat;
      if(c){addName(names,c.entityName);addName(names,c.activeActorName);addName(names,c.targetName);}
      var party=s&&s.party;
      if(Array.isArray(party))party.forEach(function(member){if(member&&typeof member==='object')addName(names,member.name);else addName(names,member);});
      var characters=s&&s.characters;
      if(characters&&typeof characters==='object')Object.keys(characters).forEach(function(key){var ch=characters[key];if(ch&&typeof ch==='object')addName(names,ch.name);});
    }catch(e){}
    names.sort(function(a,b){return b.length-a.length;});
    return names;
  }
  function patternForLiteral(value,cls,priority){
    return {re:new RegExp(escRe(value),'gi'),cls:cls,priority:priority};
  }
  function semanticPatterns(){
    var patterns=[];
    ITEM_NAMES.forEach(function(name){patterns.push(patternForLiteral(name,'combat-sem-item',60));});
    patterns.push({re:/(?:hồi|phục hồi|heal(?:ed|ing)?)\s+(?:đúng\s+)?\+?\d+(?:[.,]\d+)?%?\s*(?:Max\s*)?HP\b/gi,cls:'combat-sem-heal',priority:55});
    patterns.push({re:/\+\s*\d+(?:[.,]\d+)?\s*HP\b/gi,cls:'combat-sem-heal',priority:55});
    patterns.push({re:/-\s*\d+(?:[.,]\d+)?\s*HP\b/gi,cls:'combat-sem-damage',priority:55});
    patterns.push({re:/(?:\d+(?:[.,]\d+)?%\s*)?DMG\b/gi,cls:'combat-sem-damage',priority:54});
    patterns.push({re:/\b\d+(?:[.,]\d+)?\s*(?:damage|sát thương)\b/gi,cls:'combat-sem-damage',priority:53});
    SKILL_NAMES.forEach(function(name){patterns.push(patternForLiteral(name,'combat-sem-skill',45));});
    liveNames().forEach(function(name){patterns.push(patternForLiteral(name,'combat-sem-name',40));});
    return patterns;
  }
  function rangesFor(text){
    var candidates=[];
    semanticPatterns().forEach(function(pattern){
      pattern.re.lastIndex=0;var match;
      while((match=pattern.re.exec(text))!==null){
        if(!match[0]){pattern.re.lastIndex++;continue;}
        candidates.push({start:match.index,end:match.index+match[0].length,cls:pattern.cls,priority:pattern.priority});
      }
    });
    candidates.sort(function(a,b){return a.start-b.start||b.priority-a.priority||(b.end-b.start)-(a.end-a.start);});
    var selected=[];
    candidates.forEach(function(candidate){
      for(var i=0;i<selected.length;i++){
        var chosen=selected[i];
        if(candidate.start<chosen.end&&candidate.end>chosen.start)return;
      }
      selected.push(candidate);
    });
    selected.sort(function(a,b){return a.start-b.start;});
    return selected;
  }
  function decorateTextNode(node){
    if(!node||!node.parentElement||node.parentElement.closest('[data-combat-semantic]'))return false;
    var text=node.nodeValue||'';if(!text.trim())return false;
    var ranges=rangesFor(text);if(!ranges.length)return false;
    var fragment=document.createDocumentFragment(),cursor=0;
    ranges.forEach(function(range){
      if(range.start>cursor)fragment.appendChild(document.createTextNode(text.slice(cursor,range.start)));
      var span=document.createElement('span');span.className=range.cls;span.dataset.combatSemantic='1';span.textContent=text.slice(range.start,range.end);fragment.appendChild(span);cursor=range.end;
    });
    if(cursor<text.length)fragment.appendChild(document.createTextNode(text.slice(cursor)));
    node.parentNode.replaceChild(fragment,node);return true;
  }
  function isCombatMessage(message){
    if(!message)return false;
    if(message.classList.contains('combat'))return true;
    var role=message.querySelector('.role');return !!(role&&String(role.textContent||'').trim().toUpperCase()==='COMBAT');
  }
  function decorateMessage(message){
    if(!isCombatMessage(message)||message.dataset.combatTypography==='1')return;
    var textRoot=message.querySelector('.text')||message;
    var walker=document.createTreeWalker(textRoot,NodeFilter.SHOW_TEXT,null);
    var nodes=[],node;while((node=walker.nextNode()))nodes.push(node);
    nodes.forEach(decorateTextNode);
    message.dataset.combatTypography='1';
  }
  function decorateAll(){
    scheduled=false;
    var root=document.getElementById('log')||document;
    root.querySelectorAll('.message').forEach(decorateMessage);
  }
  function schedule(){if(scheduled)return;scheduled=true;requestAnimationFrame(decorateAll);}
  function install(){
    decorateAll();
    var root=document.getElementById('log')||document.body;if(!root)return;
    new MutationObserver(schedule).observe(root,{childList:true,subtree:true});
  }
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',install,{once:true});else install();
  window.backroomDecorateCombatTypography=schedule;
})();
</script>
'''
    script = script_template.replace("__ITEMS__", json.dumps(item_names, ensure_ascii=False))
    script = script.replace("__SKILLS__", json.dumps(skill_names, ensure_ascii=False))

    if "</head>" not in html or "</body>" not in html:
        raise RuntimeError("Combat typography HTML insertion anchors missing")
    html = html.replace("</head>", style + "</head>", 1)
    html = html.replace("</body>", script + "</body>", 1)

for required in (
    "COMBAT_TYPOGRAPHY_V1",
    "fonts/Play-Bold.ttf",
    "combat-sem-item",
    "combat-sem-damage",
    "combat-sem-heal",
    "combat-sem-name",
    "combat-sem-skill",
    "MutationObserver",
    "backroomDecorateCombatTypography",
):
    if required not in html:
        raise RuntimeError("Combat typography marker missing: " + required)

for forbidden in ("data:font/", "font/ttf;base64", "application/x-font-ttf;base64"):
    if forbidden in html:
        raise RuntimeError("Combat typography must package the font as a local asset, not base64")

INDEX.write_text(html, encoding="utf-8")
print(
    "Combat typography applied: cyan-green underlined Items, blood-red DMG, bright-green healing, "
    f"Play Bold names/skills; items={len(item_names)}, skills={len(skill_names)}."
)

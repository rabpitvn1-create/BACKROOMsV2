from pathlib import Path
import base64
import re

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
INDEX = ROOT / "app/src/main/assets/index.html"
ASSET_DIR = ROOT / "app/src/main/assets/ui"

SPRITES = {
    "search": "UklGRmoAAABXRUJQVlA4TF0AAAAvH8AHEB8gECA8aXYnSCBAiDJkXYlAgNAkXZqC+Q/AX1UBo0iSFPVn4U4dLqo7hjF5r2UDEf2fADSoFEu4yMcNK8Di+h9XAgqBYUgYlFJxIFIsDhhUChRLGSgFfQgA",
    "execute": "UklGRm4AAABXRUJQVlA4TGEAAAAvH8AHEB8gECA8aXYnSCBAiDJkXYlAgNAkXZqC+Q/AX1UBowBglAx3qKan6XBZOhrw2+4aRPR/AsBxx/l+DnQkIYRIWCdpsWhpIJKe1gtkCCGEN0RSL7AEEogEGD1yoCQBAA==",
    "explore": "UklGRmgAAABXRUJQVlA4TFwAAAAvH8AHEB8gECA8aXYnSCBAiDJkXYlAgNAkXZqC+Q/AX1UBo0iSFPVnj9QxkzVGf2dh6iRE9H8C9GNMYrVAhYtscXEIfKggKg452/y62ZmkuJiRI9NBCvYCvRExAw==",
}

ASSET_DIR.mkdir(parents=True, exist_ok=True)
for name, encoded in SPRITES.items():
    data = base64.b64decode(encoded)
    if data[:4] != b"RIFF" or data[8:12] != b"WEBP":
        raise RuntimeError(f"Invalid WebP payload for {name}")
    (ASSET_DIR / f"action_{name}.webp").write_bytes(data)

main = MAIN.read_text(encoding="utf-8")

# The prompt is composed by an earlier runtime patch. Inject after the first prompt line rather
# than depending on the exact full line, because downstream provider hardening may extend it.
if "PLAYER ADDRESS HARD LOCK:" not in main:
    prompt_start = main.find('String prompt = "Bạn là Game Master duy nhất')
    if prompt_start < 0:
        prompt_start = main.find('String prompt = "Bạn là Game Master')
    if prompt_start < 0:
        raise RuntimeError("Final GM prompt start not found")
    prompt_line_end = main.find("\n", prompt_start)
    if prompt_line_end < 0:
        raise RuntimeError("Final GM prompt line end not found")
    prompt_rule = (
        '            "PLAYER ADDRESS HARD LOCK: trong trường reply, mọi mô tả hành động, trạng thái hoặc phản hồi trực tiếp tới nhân vật do người dùng điều khiển phải viết ở ngôi thứ hai và chỉ gọi là Bạn. '
        'Không dùng Kai, Kai Akechi, Player, người chơi, hắn, anh, cậu hoặc đại từ khác để thay cho Bạn. Tên Kai chỉ được dùng trong dữ liệu state nội bộ, không dùng để xưng hô hay làm chủ ngữ đại diện người dùng trong reply. " +\n'
    )
    main = main[:prompt_line_end + 1] + prompt_rule + main[prompt_line_end + 1:]

normalize_helper = r'''  private String normalizePlayerAddress(String reply) {
    if (reply == null) return "";
    String normalized = reply;
    normalized = normalized.replaceAll("(?iu)\\bKai\\s+Akechi\\b", "Bạn");
    normalized = normalized.replaceAll("(?iu)\\bKai\\b", "Bạn");
    normalized = normalized.replaceAll("(?iu)\\bPlayer\\b", "Bạn");
    normalized = normalized.replaceAll("(?iu)\\bthe\\s+player\\b", "Bạn");
    normalized = normalized.replaceAll("(?iu)\\bngười\\s+chơi\\b", "Bạn");
    return normalized;
  }

'''.replace('\\"', '"')
bridge_anchor = "  private class GameBridge {\n"
if "private String normalizePlayerAddress(String reply)" not in main:
    if bridge_anchor not in main:
        raise RuntimeError("GameBridge anchor not found for player-address normalizer")
    main = main.replace(bridge_anchor, normalize_helper + bridge_anchor, 1)

if "reply = normalizePlayerAddress(reply);" not in main:
    reply_pattern = re.compile(r'(?m)^(\s*String reply\s*=\s*generated\.optString\("reply",\s*""\)\.trim\(\);\s*)$')
    match = reply_pattern.search(main)
    if not match:
        raise RuntimeError("GM reply normalization anchor not found")
    indent = re.match(r'\s*', match.group(1)).group(0)
    main = main[:match.end()] + "\n" + indent + "reply = normalizePlayerAddress(reply);" + main[match.end():]

for marker in ("PLAYER ADDRESS HARD LOCK:", "private String normalizePlayerAddress(String reply)", "reply = normalizePlayerAddress(reply);"):
    if marker not in main:
        raise RuntimeError("GM address-lock marker missing: " + marker)

MAIN.write_text(main, encoding="utf-8")

html = INDEX.read_text(encoding="utf-8")

def replace_action_icon(source: str, button_id: str, asset: str) -> str:
    sprite = f'<img class="action-sprite" src="ui/action_{asset}.webp" alt="" aria-hidden="true">'
    button_re = re.compile(rf'<button\b(?=[^>]*\bid="{re.escape(button_id)}")[^>]*>.*?</button>', re.S)
    match = button_re.search(source)
    if not match:
        raise RuntimeError(f"Primary action button not found: {button_id}")
    block = match.group(0)
    if sprite in block:
        return source
    cleaned, removed = re.subn(r'<svg\b.*?</svg>', '', block, count=1, flags=re.S)
    if removed != 1:
        raise RuntimeError(f"Primary action SVG not found: {button_id}")
    span_pos = cleaned.find('<span')
    if span_pos < 0:
        raise RuntimeError(f"Primary action label not found: {button_id}")
    cleaned = cleaned[:span_pos] + sprite + cleaned[span_pos:]
    return source[:match.start()] + cleaned + source[match.end():]

for button_id, asset in (
    ("searchActionButton", "search"),
    ("submit", "execute"),
    ("exploreActionButton", "explore"),
):
    html = replace_action_icon(html, button_id, asset)

html = html.replace('placeholder="Kai sẽ làm gì tiếp theo?"', 'placeholder="Bạn sẽ làm gì tiếp theo?"', 1)
html = html.replace('placeholder="Kai làm gì trong Turn hiện tại?"', 'placeholder="Bạn sẽ làm gì tiếp theo?"', 1)

mobile_css = r'''
/* MOBILE_SWIPE_UI_V1 */
html,body{width:100%;height:100%;overflow:hidden;overscroll-behavior:none}
body{max-width:100vw}
.shell{width:100%;height:100dvh;min-height:100dvh;padding:0!important;overflow:hidden;background:#080a0c}
.page-track{display:flex;width:100%;height:100%;transform:translate3d(0,0,0);transition:transform .24s cubic-bezier(.2,.72,.2,1);will-change:transform;touch-action:pan-y}
.page-track.show-management{transform:translate3d(-100%,0,0)}
.app-page{flex:0 0 100%;width:100%;min-width:0;height:100%;overflow-x:hidden;background:#080a0c}
#gameplayPage{overflow:hidden}
#gameplayPage .game{height:100%;min-height:0;display:flex;flex-direction:column;border:0!important;box-shadow:none!important;background:#0e1114}
.topbar{flex:0 0 auto;min-height:34px;padding:5px 8px!important;gap:7px;align-items:center;border-bottom:1px solid #252b31}
.topbar>div:first-child{min-width:0;overflow:hidden}.eyebrow{font-size:8px;line-height:1;letter-spacing:.12em}.topbar h1{margin:2px 0 0!important;font-size:14px;line-height:1.1;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.turn{font-size:9px;line-height:1;white-space:nowrap}.turn strong{font-size:16px}
.snapshot{position:relative;flex:0 0 auto;width:100%!important;height:auto!important;aspect-ratio:16/9;margin:0!important;border-left:0!important;border-right:0!important;overflow:hidden;background:#080a0c}
.snapshot img{width:100%!important;height:100%!important;max-width:100%;object-fit:contain!important;object-position:center!important}
.log{flex:1 1 auto;height:auto!important;min-height:0;overflow-y:auto;overscroll-behavior:contain;padding:7px 8px;gap:7px}
.message{padding:8px 9px}.role{margin-bottom:4px}.text{line-height:1.45}
.composer{flex:0 0 auto;padding:6px 8px!important;gap:6px}.composer textarea{min-height:54px!important;max-height:92px;resize:none;padding:8px}.primary-action-row{gap:5px}.primary-action{min-height:42px;padding:7px 5px;gap:5px}.primary-action .action-sprite{width:22px;height:22px;flex:0 0 22px;display:block;object-fit:contain;image-rendering:pixelated;image-rendering:crisp-edges}
.status{flex:0 0 auto;min-height:20px;padding:4px 8px!important;font-size:10px;line-height:1.25}
#managementPage{overflow-y:auto;overscroll-behavior:contain;-webkit-overflow-scrolling:touch}
#managementPage .side{width:100%;margin:0!important;padding:6px!important;gap:6px}
#managementPage .card{width:100%;margin:0;box-shadow:none;padding:10px}
.character-inventory-view{position:static!important;inset:auto!important;z-index:auto!important;width:100%;min-height:0;background:#080a0c;padding:8px 0 16px!important;overflow:visible!important}
.character-inventory-head{padding:6px 0 9px}.character-inventory-head button{padding:8px 10px}.character-profile{grid-template-columns:86px 1fr;gap:10px;padding:10px 0}.character-profile img{width:86px;height:86px}.character-section{margin-top:7px;padding:10px}
.page-indicator{position:fixed;z-index:80;left:50%;bottom:max(5px,env(safe-area-inset-bottom));transform:translateX(-50%);display:flex;gap:5px;padding:4px 7px;border:1px solid #30373e;background:rgba(8,10,12,.76);border-radius:999px;backdrop-filter:blur(5px);pointer-events:none}.page-dot{width:5px;height:5px;border-radius:50%;background:#5f6971}.page-dot.active{background:#e7eef2}
@media(max-width:390px){.topbar{padding:4px 6px!important}.topbar h1{font-size:13px}.composer{padding:5px 6px!important}.primary-action{font-size:11px}.primary-action .action-sprite{width:20px;height:20px;flex-basis:20px}.status{padding:3px 6px!important}}
'''
if "/* MOBILE_SWIPE_UI_V1 */" not in html:
    if "</style>" not in html:
        raise RuntimeError("Style footer anchor not found")
    html = html.replace("</style>", mobile_css + "\n</style>", 1)

mobile_script = r'''
<script id="mobileSwipeUiPatch">
(function(){
  if(window.__mobileSwipeUiPatch)return;window.__mobileSwipeUiPatch=true;
  const shell=document.querySelector('.shell'),game=document.querySelector('.game'),side=document.querySelector('.side');
  if(!shell||!game||!side)return;
  const track=document.createElement('div');track.className='page-track';track.id='pageTrack';
  const gameplay=document.createElement('section');gameplay.className='app-page';gameplay.id='gameplayPage';gameplay.setAttribute('aria-label','Gameplay');
  const management=document.createElement('section');management.className='app-page';management.id='managementPage';management.setAttribute('aria-label','Character and management');
  gameplay.appendChild(game);management.appendChild(side);track.appendChild(gameplay);track.appendChild(management);shell.appendChild(track);
  const indicator=document.createElement('div');indicator.className='page-indicator';indicator.setAttribute('aria-hidden','true');indicator.innerHTML='<i class="page-dot active"></i><i class="page-dot"></i>';document.body.appendChild(indicator);
  const dots=indicator.querySelectorAll('.page-dot');let page=0,startX=0,startY=0,tracking=false;
  function setPage(next){page=next>0?1:0;track.classList.toggle('show-management',page===1);dots.forEach((dot,i)=>dot.classList.toggle('active',i===page));if(page===1){const view=document.getElementById('characterInventoryView');if(view&&view.hidden){const first=document.querySelector('#party .party-member[data-character]');if(first)first.click();}}}
  function interactive(target){return !!(target&&target.closest&&target.closest('textarea,input,select,button,a,.equipment-detail-modal'))}
  shell.addEventListener('touchstart',function(e){if(e.touches.length!==1||interactive(e.target)){tracking=false;return}const t=e.touches[0];startX=t.clientX;startY=t.clientY;tracking=true},{passive:true});
  shell.addEventListener('touchend',function(e){if(!tracking||!e.changedTouches.length)return;tracking=false;const t=e.changedTouches[0],dx=t.clientX-startX,dy=t.clientY-startY;if(Math.abs(dx)<56||Math.abs(dx)<=Math.abs(dy)*1.2)return;if(dx<0&&page===0)setPage(1);else if(dx>0&&page===1)setPage(0)},{passive:true});
  const detailBack=document.getElementById('characterInventoryBack');if(detailBack)detailBack.textContent='Thu gọn thông tin';
  setPage(0);
})();
</script>
'''
if 'id="mobileSwipeUiPatch"' not in html:
    if "</body>" not in html:
        raise RuntimeError("Body footer anchor not found")
    html = html.replace("</body>", mobile_script + "\n</body>", 1)

for marker in (
    "ui/action_search.webp",
    "ui/action_execute.webp",
    "ui/action_explore.webp",
    "/* MOBILE_SWIPE_UI_V1 */",
    "page-track.show-management",
    "object-fit:contain!important",
    'id="mobileSwipeUiPatch"',
    'placeholder="Bạn sẽ làm gì tiếp theo?"',
):
    if marker not in html:
        raise RuntimeError("Mobile swipe UI marker missing: " + marker)

for button_id, asset in (("searchActionButton", "search"), ("submit", "execute"), ("exploreActionButton", "explore")):
    button_match = re.search(rf'<button\b(?=[^>]*\bid="{re.escape(button_id)}")[^>]*>.*?</button>', html, re.S)
    if not button_match or f'ui/action_{asset}.webp' not in button_match.group(0):
        raise RuntimeError("WebP action sprite verification failed: " + button_id)

INDEX.write_text(html, encoding="utf-8")
print("Mobile-first two-page swipe UI, compact full-width Snapshot layout, WebP pixel action sprites, and GM Bạn-address lock applied.")

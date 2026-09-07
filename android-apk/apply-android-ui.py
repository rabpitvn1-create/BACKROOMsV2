from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
INDEX = ROOT / "app/src/main/assets/index.html"

main = MAIN.read_text(encoding="utf-8")
html = INDEX.read_text(encoding="utf-8")


def ensure_import(source: str, anchor: str, import_line: str, label: str) -> str:
    if import_line in source:
        return source
    if anchor not in source:
        raise RuntimeError(f"{label}: import anchor missing")
    return source.replace(anchor, anchor + import_line, 1)


# ---------------------------------------------------------------------------
# Android owns the physical display geometry. WebView receives only dp/CSS-px
# values derived from real WindowInsets / RoundedCorner data. This is the sole
# fullscreen + screen-shape authority; legacy immersive/layout patches are gone.
# ---------------------------------------------------------------------------
main = ensure_import(main, "import android.os.Bundle;\n", "import android.os.Build;\n", "Build")
main = ensure_import(main, "import android.view.View;\n" if "import android.view.View;\n" in main else "import android.os.Build;\n", "import android.view.DisplayCutout;\n", "DisplayCutout")
main = ensure_import(main, "import android.view.DisplayCutout;\n", "import android.view.RoundedCorner;\n", "RoundedCorner")
main = ensure_import(main, "import android.view.RoundedCorner;\n", "import android.view.View;\n", "View")
main = ensure_import(main, "import android.view.View;\n", "import android.view.WindowInsets;\n", "WindowInsets")
main = ensure_import(main, "import android.view.WindowInsets;\n", "import android.view.WindowInsetsController;\n", "WindowInsetsController")
main = ensure_import(main, "import android.view.WindowInsetsController;\n", "import android.view.WindowManager;\n", "WindowManager")
main = ensure_import(main, "import java.util.Iterator;\n", "import java.util.Locale;\n", "Locale")

on_create_anchor = "  @Override public void onCreate(Bundle savedInstanceState) {\n    super.onCreate(savedInstanceState);\n"
if "    applyAndroidDisplayMode();\n" not in main:
    if on_create_anchor not in main:
        raise RuntimeError("Android display onCreate anchor missing")
    main = main.replace(on_create_anchor, on_create_anchor + "    applyAndroidDisplayMode();\n", 1)

webview_anchor = "    webView = new WebView(this);\n"
webview_setup = '''    webView = new WebView(this);
    webView.setBackgroundColor(android.graphics.Color.BLACK);
    webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
    webView.setOnApplyWindowInsetsListener((view, insets) -> {
      pushAndroidDisplayGeometry(insets);
      return insets;
    });
'''
if "webView.setOnApplyWindowInsetsListener" not in main:
    if webview_anchor not in main:
        raise RuntimeError("WebView geometry anchor missing")
    main = main.replace(webview_anchor, webview_setup, 1)

page_finished_anchor = "        installUiEnhancements();\n"
if "        pushAndroidDisplayGeometry(view.getRootWindowInsets());\n" not in main:
    if page_finished_anchor not in main:
        raise RuntimeError("onPageFinished geometry anchor missing")
    main = main.replace(
        page_finished_anchor,
        page_finished_anchor + "        pushAndroidDisplayGeometry(view.getRootWindowInsets());\n",
        1,
    )

method_anchor = "\n  @Override protected void onDestroy() {\n"
geometry_methods = r'''
  // ANDROID_DISPLAY_GEOMETRY_V1
  private void applyAndroidDisplayMode() {
    getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
    getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      WindowManager.LayoutParams attributes = getWindow().getAttributes();
      attributes.layoutInDisplayCutoutMode =
          WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
      getWindow().setAttributes(attributes);
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      getWindow().setDecorFitsSystemWindows(false);
      WindowInsetsController controller = getWindow().getInsetsController();
      if (controller != null) {
        controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
        controller.setSystemBarsBehavior(
            WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
      }
    } else {
      getWindow().getDecorView().setSystemUiVisibility(
          View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
              | View.SYSTEM_UI_FLAG_FULLSCREEN
              | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
              | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
              | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
              | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
  }

  private float displayCssPx(int devicePx) {
    float density = getResources().getDisplayMetrics().density;
    return density > 0f ? devicePx / density : devicePx;
  }

  private int roundedCornerRadius(WindowInsets insets, int position) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || insets == null) return 0;
    RoundedCorner corner = insets.getRoundedCorner(position);
    return corner != null ? corner.getRadius() : 0;
  }

  private int systemDimensionPx(String name) {
    int id = getResources().getIdentifier(name, "dimen", "android");
    return id != 0 ? getResources().getDimensionPixelSize(id) : 0;
  }

  private void pushAndroidDisplayGeometry(WindowInsets insets) {
    if (webView == null || insets == null) return;
    int safeTop = 0;
    int safeRight = 0;
    int safeBottom = 0;
    int safeLeft = 0;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      DisplayCutout cutout = insets.getDisplayCutout();
      if (cutout != null) {
        safeTop = Math.max(safeTop, cutout.getSafeInsetTop());
        safeRight = Math.max(safeRight, cutout.getSafeInsetRight());
        safeBottom = Math.max(safeBottom, cutout.getSafeInsetBottom());
        safeLeft = Math.max(safeLeft, cutout.getSafeInsetLeft());
      }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      android.graphics.Insets gestures = insets.getInsets(WindowInsets.Type.systemGestures());
      // Bottom gesture space protects the action row. Side gestures deliberately do not
      // widen every content column on portrait phones.
      safeBottom = Math.max(safeBottom, gestures.bottom);
    }

    float top = displayCssPx(safeTop);
    float right = displayCssPx(safeRight);
    float bottom = displayCssPx(safeBottom);
    float left = displayCssPx(safeLeft);
    float tl = 0f;
    float tr = 0f;
    float br = 0f;
    float bl = 0f;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      tl = displayCssPx(roundedCornerRadius(insets, RoundedCorner.POSITION_TOP_LEFT));
      tr = displayCssPx(roundedCornerRadius(insets, RoundedCorner.POSITION_TOP_RIGHT));
      br = displayCssPx(roundedCornerRadius(insets, RoundedCorner.POSITION_BOTTOM_RIGHT));
      bl = displayCssPx(roundedCornerRadius(insets, RoundedCorner.POSITION_BOTTOM_LEFT));
    }
    int allRadiusPx = systemDimensionPx("rounded_corner_radius");
    int topRadiusPx = systemDimensionPx("rounded_corner_radius_top");
    int bottomRadiusPx = systemDimensionPx("rounded_corner_radius_bottom");
    float fallbackAll = displayCssPx(allRadiusPx);
    float fallbackTop = displayCssPx(topRadiusPx > 0 ? topRadiusPx : allRadiusPx);
    float fallbackBottom = displayCssPx(bottomRadiusPx > 0 ? bottomRadiusPx : allRadiusPx);
    if (tl <= 0f) tl = fallbackTop > 0f ? fallbackTop : fallbackAll;
    if (tr <= 0f) tr = fallbackTop > 0f ? fallbackTop : fallbackAll;
    if (br <= 0f) br = fallbackBottom > 0f ? fallbackBottom : fallbackAll;
    if (bl <= 0f) bl = fallbackBottom > 0f ? fallbackBottom : fallbackAll;

    String script = String.format(
        Locale.US,
        "(function(){var s=document.documentElement.style;"
            + "s.setProperty('--android-safe-top','%.2fpx');"
            + "s.setProperty('--android-safe-right','%.2fpx');"
            + "s.setProperty('--android-safe-bottom','%.2fpx');"
            + "s.setProperty('--android-safe-left','%.2fpx');"
            + "s.setProperty('--android-radius-tl','%.2fpx');"
            + "s.setProperty('--android-radius-tr','%.2fpx');"
            + "s.setProperty('--android-radius-br','%.2fpx');"
            + "s.setProperty('--android-radius-bl','%.2fpx');"
            + "document.documentElement.classList.add('android-geometry-ready');"
            + "})();",
        top, right, bottom, left, tl, tr, br, bl);
    webView.post(() -> webView.evaluateJavascript(script, null));
  }

  @Override public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    if (hasFocus) applyAndroidDisplayMode();
  }

  @Override protected void onResume() {
    super.onResume();
    applyAndroidDisplayMode();
    if (webView != null) webView.requestApplyInsets();
  }
'''
if "ANDROID_DISPLAY_GEOMETRY_V1" not in main:
    if method_anchor not in main:
        raise RuntimeError("Android geometry method insertion anchor missing")
    main = main.replace(method_anchor, "\n" + geometry_methods + method_anchor, 1)

# Preserve the field-tested Kai visual alignment while removing the old PR3 layout patch.
if "ANDROID_UI_KAI_SHIFT_RIGHT" not in main:
    pattern = re.compile(r"(\.snapshot \.snapshot-character\{[^}]*?)right:0;")
    main, count = pattern.subn(
        r"\1right:calc(-4% - 5px);/* ANDROID_UI_KAI_SHIFT_RIGHT */",
        main,
        count=1,
    )
    if count != 1:
        raise RuntimeError(f"Kai Snapshot alignment anchor: expected 1 match, found {count}")

for token in (
    "ANDROID_DISPLAY_GEOMETRY_V1",
    "getRoundedCorner(position)",
    "--android-radius-tl",
    "webView.setOnApplyWindowInsetsListener",
    "getWindow().setDecorFitsSystemWindows(false)",
    "ANDROID_UI_KAI_SHIFT_RIGHT",
):
    if token not in main:
        raise RuntimeError("Android display contract missing: " + token)
MAIN.write_text(main, encoding="utf-8")


# ---------------------------------------------------------------------------
# Canonical Android WebView UI. Three actions, page swipe, spacing and geometry
# are installed exactly once here. No legacy mobile/PR3 CSS layer is allowed.
# ---------------------------------------------------------------------------
button_row = '''<div class="primary-action-row" id="primaryActionRow">
<button type="button" class="primary-action" id="searchActionButton" aria-label="Tìm kiếm"><img class="action-sprite" src="data:image/webp;base64,UklGRmoAAABXRUJQVlA4TF0AAAAvH8AHEB8gECA8aXYnSCBAiDJkXYlAgNAkXZqC+Q/AX1UBo0iSFPVn4U4dLqo7hjF5r2UDEf2fADSoFEu4yMcNK8Di+h9XAgqBYUgYlFJxIFIsDhhUChRLGSgFfQgA" alt="" aria-hidden="true"><span>Tìm kiếm</span></button>
<button type="submit" class="primary-action execute-action" id="submit" aria-label="Thực hiện"><img class="action-sprite" src="data:image/webp;base64,UklGRm4AAABXRUJQVlA4TGEAAAAvH8AHEB8gECA8aXYnSCBAiDJkXYlAgNAkXZqC+Q/AX1UBowBglAx3qKan6XBZOhrw2+4aRPR/AsBxx/l+DnQkIYRIWCdpsWhpIJKe1gtkCCGEN0RSL7AEEogEGD1yoCQBAA==" alt="" aria-hidden="true"><span>Thực hiện</span></button>
<button type="button" class="primary-action" id="exploreActionButton" aria-label="Khám phá"><img class="action-sprite" src="data:image/webp;base64,UklGRmgAAABXRUJQVlA4TFwAAAAvH8AHEB8gECA8aXYnSCBAiDJkXYlAgNAkXZqC+Q/AX1UBo0iSFPVnj9QxkzVGf2dh6iRE9H8C9GNMYrVAhYtscXEIfKggKg452/y62ZmkuJiRI9NBCvYCvRExAw==" alt="" aria-hidden="true"><span>Khám phá</span></button>
</div>'''
if 'id="searchActionButton"' not in html:
    submit_button = re.compile(r'<button\s+id="submit"[^>]*>.*?</button>', re.IGNORECASE | re.DOTALL)
    html, count = submit_button.subn(button_row, html, count=1)
    if count != 1:
        raise RuntimeError(f"Canonical action row: expected one legacy submit button, found {count}")

html = html.replace('placeholder="Kai sẽ làm gì tiếp theo?"', 'placeholder="Bạn sẽ làm gì tiếp theo?"', 1)
html = html.replace('placeholder="Kai làm gì trong Turn hiện tại?"', 'placeholder="Bạn sẽ làm gì tiếp theo?"', 1)

submit_pattern = re.compile(r'(?:window\.)?Android\.submitTurn\(JSON\.stringify\(state\),a\)')
if 'Android.submitAction(JSON.stringify(state),"EXECUTE",a)' not in html:
    html, count = submit_pattern.subn('window.Android.submitAction(JSON.stringify(state),"EXECUTE",a)', html, count=1)
    if count != 1:
        raise RuntimeError(f"Canonical Execute bridge: expected 1 submitTurn call, found {count}")

# Pressure Combat belongs on gameplay, not inside the Save/Load management card.
html = html.replace(
    "var target=document.querySelector('.actions')||document.getElementById('log')||document.body;",
    "var target=document.querySelector('.composer')||document.getElementById('log')||document.body;",
)

primary_js = r'''
// ANDROID_THREE_ACTIONS_V1
const searchActionButton=byId("searchActionButton"),exploreActionButton=byId("exploreActionButton");
function syncPrimaryActions(){
  const hasText=!!(actionEl&&actionEl.value.trim());
  if(submitEl)submitEl.disabled=busy||!hasText;
  if(searchActionButton)searchActionButton.disabled=busy;
  if(exploreActionButton)exploreActionButton.disabled=busy;
}
function appendMacroPending(label){
  if(!logEl)return;
  const player=document.createElement("article");
  player.className="message player pending";player.setAttribute("data-pending","1");
  player.innerHTML="<div class='role'>BẠN</div><div class='text'></div>";
  player.querySelector(".text").textContent=label;logEl.appendChild(player);
  const gm=document.createElement("article");
  gm.className="message pending";gm.setAttribute("data-pending","1");
  gm.innerHTML="<div class='role'>GAME MASTER</div><div class='text'>Đang xử lý lượt…</div>";
  logEl.appendChild(gm);logEl.scrollTop=logEl.scrollHeight;
}
function submitMacroAction(kind,label){
  if(busy)return;
  if(!window.Android||typeof window.Android.submitAction!=="function"){
    statusEl.textContent="Không tìm thấy Android action bridge.";return;
  }
  busy=true;syncPrimaryActions();
  statusEl.textContent=kind==="SEARCH"?"Đang tìm kiếm khu vực hiện tại…":"Đang khám phá khu vực chưa khảo sát…";
  appendMacroPending(label);
  window.Android.submitAction(JSON.stringify(state),kind,label);
}
if(searchActionButton)searchActionButton.addEventListener("click",()=>submitMacroAction("SEARCH","Tìm kiếm"));
if(exploreActionButton)exploreActionButton.addEventListener("click",()=>submitMacroAction("EXPLORE","Khám phá"));
if(actionEl)actionEl.addEventListener("input",syncPrimaryActions);
syncPrimaryActions();
'''
if "ANDROID_THREE_ACTIONS_V1" not in html:
    anchor = "window.backroomTurn="
    pos = html.find(anchor)
    if pos < 0:
        raise RuntimeError("Canonical action JS backroomTurn anchor missing")
    html = html[:pos] + primary_js + "\n" + html[pos:]

html = html.replace("busy=true;submitEl.disabled=true;", "busy=true;syncPrimaryActions();")
html = html.replace("busy=false;submitEl.disabled=false;", "busy=false;syncPrimaryActions();")

canonical_css = r'''
<style id="androidEdgeUiStyle">
/* ANDROID_EDGE_UI_V1 */
:root{
  --android-safe-top:0px;--android-safe-right:0px;--android-safe-bottom:0px;--android-safe-left:0px;
  --android-radius-tl:0px;--android-radius-tr:0px;--android-radius-br:0px;--android-radius-bl:0px;
  --ui-edge:8px;--ui-gap:7px;--panel-radius:12px;--control-radius:11px;
}
html,body{width:100%;height:100%;margin:0;overflow:hidden;overscroll-behavior:none;background:#080a0c}
body{max-width:100vw;border-top-left-radius:var(--android-radius-tl);border-top-right-radius:var(--android-radius-tr);border-bottom-right-radius:var(--android-radius-br);border-bottom-left-radius:var(--android-radius-bl);overflow:hidden}
.shell{width:100%;height:100dvh;min-height:100dvh;padding:0;overflow:hidden;background:#080a0c}
.page-track{display:flex;width:100%;height:100%;transform:translate3d(0,0,0);transition:transform .24s cubic-bezier(.2,.72,.2,1);will-change:transform;touch-action:pan-y}
.page-track.show-management{transform:translate3d(-100%,0,0)}
.app-page{flex:0 0 100%;width:100%;min-width:0;height:100%;overflow-x:hidden;background:#080a0c}
#gameplayPage{overflow:hidden}
#gameplayPage .game{height:100%;min-height:0;display:flex;flex-direction:column;border:0;box-shadow:none;background:#0e1114}
.topbar{flex:0 0 auto;min-height:36px;align-items:center;gap:8px;padding-top:calc(var(--android-safe-top) + 6px);padding-right:calc(var(--android-safe-right) + var(--ui-edge));padding-bottom:6px;padding-left:calc(var(--android-safe-left) + var(--ui-edge));border-bottom:1px solid #252b31}
.topbar>div:first-child{min-width:0;overflow:hidden}.eyebrow{font-size:8px;line-height:1;letter-spacing:.12em}.topbar h1{margin:2px 0 0;font-size:14px;line-height:1.1;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.turn{font-size:9px;line-height:1;white-space:nowrap}.turn strong{font-size:16px}
.snapshot{position:relative;flex:0 0 clamp(148px,24dvh,250px);width:auto;height:auto;margin:var(--ui-gap) calc(var(--android-safe-right) + var(--ui-edge)) 0 calc(var(--android-safe-left) + var(--ui-edge));border:1px solid #2b3339;border-radius:var(--panel-radius);overflow:hidden;background:#080a0c}
.snapshot img{width:100%;height:100%;max-width:100%;object-fit:contain;object-position:center}
.log{flex:1 1 auto;height:auto;min-height:0;overflow-y:auto;overscroll-behavior:contain;padding:var(--ui-gap) calc(var(--android-safe-right) + var(--ui-edge)) var(--ui-gap) calc(var(--android-safe-left) + var(--ui-edge));display:grid;align-content:start;gap:var(--ui-gap)}
.message{width:100%;border:1px solid #283137;border-left:3px solid #454e56;border-radius:var(--panel-radius);padding:9px 10px;background:#111519}.message.player{border-left-color:#8b949e;background:#15191d}.message.gm{border-left-color:#71808a;background:#171e23}.role{margin-bottom:4px}.text{line-height:1.45}
.composer{flex:0 0 auto;display:grid;gap:var(--ui-gap);padding:0 calc(var(--android-safe-right) + var(--ui-edge)) var(--ui-gap) calc(var(--android-safe-left) + var(--ui-edge))}
.composer textarea{width:100%;min-height:54px;max-height:92px;resize:none;padding:9px 10px;border:1px solid #303a42;border-radius:var(--control-radius);background:#090c0f;color:#fff;outline:none}.composer textarea:focus{border-color:#65727c}
.primary-action-row{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:var(--ui-gap);width:100%}
.primary-action{min-width:0;min-height:44px;border-radius:var(--control-radius);display:flex;align-items:center;justify-content:center;gap:5px;padding:7px 5px;white-space:nowrap;font-size:11px}.primary-action.execute-action{font-weight:800;border-color:#56616a;background:#20272d}.primary-action .action-sprite{width:21px;height:21px;flex:0 0 21px;display:block;object-fit:contain;image-rendering:pixelated;image-rendering:crisp-edges}
.status{flex:0 0 auto;min-height:20px;padding:4px calc(var(--android-safe-right) + var(--ui-edge)) calc(var(--android-safe-bottom) + 5px) calc(var(--android-safe-left) + var(--ui-edge));font-size:10px;line-height:1.25;border-top:1px solid #252b31}
#combatHud{margin:0 calc(var(--android-safe-right) + var(--ui-edge)) var(--ui-gap) calc(var(--android-safe-left) + var(--ui-edge));border-radius:var(--panel-radius)}
#managementPage{overflow-y:auto;overscroll-behavior:contain;-webkit-overflow-scrolling:touch}
#managementPage .side{width:100%;margin:0;padding:calc(var(--android-safe-top) + var(--ui-edge)) calc(var(--android-safe-right) + var(--ui-edge)) calc(var(--android-safe-bottom) + 16px) calc(var(--android-safe-left) + var(--ui-edge));gap:var(--ui-gap)}
#managementPage .card{width:100%;margin:0;box-shadow:none;padding:10px;border-radius:var(--panel-radius)}
.character-inventory-view{position:static;inset:auto;z-index:auto;width:100%;min-height:0;background:#080a0c;padding:8px 0 16px;overflow:visible}
.character-inventory-head{padding:6px 0 9px}.character-inventory-head button{padding:8px 10px}.character-profile{grid-template-columns:86px 1fr;gap:10px;padding:10px 0}.character-profile img{width:86px;height:86px}.character-section{margin-top:var(--ui-gap);padding:10px;border-radius:var(--panel-radius)}
.page-indicator{position:fixed;z-index:80;left:50%;bottom:calc(var(--android-safe-bottom) + 5px);transform:translateX(-50%);display:flex;gap:5px;padding:4px 7px;border:1px solid #30373e;background:rgba(8,10,12,.76);border-radius:999px;backdrop-filter:blur(5px);pointer-events:none}.page-dot{width:5px;height:5px;border-radius:50%;background:#5f6971}.page-dot.active{background:#e7eef2}
@media(max-width:390px){:root{--ui-edge:6px;--ui-gap:5px}.topbar h1{font-size:13px}.primary-action{font-size:10px}.primary-action .action-sprite{width:19px;height:19px;flex-basis:19px}}
@media(prefers-reduced-motion:reduce){.page-track{transition:none}}
</style>
'''
if "ANDROID_EDGE_UI_V1" not in html:
    if "</head>" not in html:
        raise RuntimeError("Canonical Android CSS head anchor missing")
    html = html.replace("</head>", canonical_css + "\n</head>", 1)

swipe_script = r'''
<script id="androidSwipeUi">
/* ANDROID_SWIPE_UI_V1 */
(function(){
  if(window.__androidSwipeUi)return;window.__androidSwipeUi=true;
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
if "ANDROID_SWIPE_UI_V1" not in html:
    if "</body>" not in html:
        raise RuntimeError("Canonical Android swipe body anchor missing")
    html = html.replace("</body>", swipe_script + "\n</body>", 1)

for forbidden in (
    "MOBILE_SWIPE_UI_V1",
    "PR3_HEADER_SAFE_INSET",
    "STEP2_THREE_ACTIONS",
    "<svg class=\"action-icon",
):
    if forbidden in html:
        raise RuntimeError("Legacy UI layer survived canonicalization: " + forbidden)

for token in (
    "ANDROID_EDGE_UI_V1",
    "ANDROID_SWIPE_UI_V1",
    "ANDROID_THREE_ACTIONS_V1",
    'id="searchActionButton"',
    'id="submit"',
    'id="exploreActionButton"',
    'grid-template-columns:repeat(3,minmax(0,1fr))',
    "--android-radius-tl",
    "--android-safe-bottom",
    "data:image/webp;base64,",
    'Android.submitAction(JSON.stringify(state),"EXECUTE",a)',
    'submitMacroAction("SEARCH","Tìm kiếm")',
    'submitMacroAction("EXPLORE","Khám phá")',
    'placeholder="Bạn sẽ làm gì tiếp theo?"',
):
    if token not in html:
        raise RuntimeError("Canonical Android UI contract missing: " + token)

INDEX.write_text(html, encoding="utf-8")
print("Canonical Android edge-to-edge UI installed: native geometry, equal action controls, clean aligned panels, two-page swipe.")

from pathlib import Path
import json
import re

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
INDEX = ROOT / "app/src/main/assets/index.html"

main = MAIN.read_text(encoding="utf-8")
html = INDEX.read_text(encoding="utf-8")

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
.page-track{display:flex;width:100%;height:100%;transform:translate3d(0,0,0);transition:transform .24s cubic-bezier(.2,.72,.2,1);will-change:transform;touch-action:pan-y;overscroll-behavior-x:contain}
.page-track.show-management{transform:translate3d(-100%,0,0)}
.app-page{flex:0 0 100%;width:100%;min-width:0;height:100%;overflow-x:hidden;background:#080a0c}
#gameplayPage{overflow:hidden}
#gameplayPage .game{height:100%;min-height:0;display:flex;flex-direction:column;border:0;box-shadow:none;background:#0e1114}
.topbar{flex:0 0 auto;min-height:36px;align-items:center;gap:8px;padding-top:calc(var(--android-safe-top) + 6px);padding-right:calc(var(--android-safe-right) + var(--ui-edge));padding-bottom:6px;padding-left:calc(var(--android-safe-left) + var(--ui-edge));border-bottom:1px solid #252b31}
.topbar>div:first-child{min-width:0;overflow:hidden}.eyebrow{font-size:8px;line-height:1;letter-spacing:.12em}.topbar h1{margin:2px 0 0;font-size:14px;line-height:1.1;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.turn{font-size:9px;line-height:1;white-space:nowrap}.turn strong{font-size:16px}
.snapshot{position:relative;flex:0 0 clamp(148px,24dvh,250px);width:auto;height:auto;margin:var(--ui-gap) calc(var(--android-safe-right) + var(--ui-edge)) 0 calc(var(--android-safe-left) + var(--ui-edge));border:1px solid #2b3339;border-radius:var(--panel-radius);overflow:hidden;background:#080a0c}
.snapshot img{width:100%;height:100%;max-width:100%;object-fit:contain;object-position:center}
.storytelling-viewport{position:relative;flex:1 1 auto;min-height:0;overflow:hidden}
.storytelling-viewport>.log{position:relative;z-index:1;width:100%;height:100%;min-height:0;overflow-y:auto;overscroll-behavior:contain;padding:var(--ui-gap) calc(var(--android-safe-right) + var(--ui-edge)) var(--ui-gap) calc(var(--android-safe-left) + var(--ui-edge));display:grid;align-content:start;gap:var(--ui-gap);background:transparent}
.message{position:relative;z-index:1;width:100%;border:1px solid #283137;border-left:3px solid #454e56;border-radius:var(--panel-radius);padding:9px 10px;background:#111519}.message.player{border-left-color:#8b949e;background:#15191d}.message.gm{border-left-color:#71808a;background:#171e23}.role{margin-bottom:4px}.text{line-height:1.45}
.composer{flex:0 0 auto;display:grid;gap:var(--ui-gap);padding:0 calc(var(--android-safe-right) + var(--ui-edge)) var(--ui-gap) calc(var(--android-safe-left) + var(--ui-edge))}
.composer textarea{width:100%;min-height:54px;max-height:92px;resize:none;padding:9px 10px;border:1px solid #303a42;border-radius:var(--control-radius);background:#090c0f;color:#fff;outline:none}.composer textarea:focus{border-color:#65727c}
.primary-action-row{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:var(--ui-gap);width:100%}
.primary-action{min-width:0;min-height:44px;border-radius:var(--control-radius);display:flex;align-items:center;justify-content:center;gap:5px;padding:7px 5px;white-space:nowrap;font-size:11px}.primary-action.execute-action{font-weight:800;border-color:#56616a;background:#20272d}.primary-action .action-sprite{width:21px;height:21px;flex:0 0 21px;display:block;object-fit:contain;image-rendering:pixelated;image-rendering:crisp-edges}
.status{flex:0 0 auto;min-height:20px;padding:4px calc(var(--android-safe-right) + var(--ui-edge)) calc(var(--android-safe-bottom) + 5px) calc(var(--android-safe-left) + var(--ui-edge));font-size:10px;line-height:1.25;border-top:1px solid #252b31}
/* ANDROID_GAMEPLAY_PRESENTATION_V2: one presentation/typography authority. */
/* STORYTELLING_SINGLE_FRAME_V3: only Game Master prose lives in one persistent frame. */
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
.storytelling-viewport>.log{padding-top:calc(var(--ui-gap,10px) + 43px)!important}
.storytelling-surface{position:absolute;z-index:0;inset:5px;border:1px solid #3b444d;border-left:3px solid #71808a;border-radius:var(--panel-radius);background:#171e23;box-shadow:none;pointer-events:none;overflow:hidden}
.storytelling-header{padding:8px 10px 7px;color:#c9d2da;border-bottom:1px solid #2b3137;background:#171e23;font-family:var(--gameplay-font);font-size:10px;font-weight:700;letter-spacing:.12em}
.message.storytelling-segment{width:calc(100% - 10px);margin-left:auto;margin-right:auto;border:0;background:transparent;box-shadow:none;padding:10px;font-family:var(--gameplay-font);font-weight:400;color:#eef1f3;white-space:pre-wrap;line-height:1.55}
.message.player,.message.player .text,.status{font-family:var(--gameplay-font);font-weight:400}
.message.combat,.message.combat .role,.message.combat .text{font-family:var(--gameplay-font);font-weight:400}
#combatHud{display:none;margin:0 calc(var(--android-safe-right) + var(--ui-edge)) var(--ui-gap) calc(var(--android-safe-left) + var(--ui-edge));border:1px solid #444b52;border-radius:var(--panel-radius);background:#0b0e10;padding:10px;font-family:var(--gameplay-font);font-weight:400}
#combatHud.active{display:block}
.combat-title{display:flex;justify-content:space-between;align-items:center;gap:8px;font-size:12px;font-weight:400;letter-spacing:.08em;margin-bottom:7px}
.combat-row{display:grid;grid-template-columns:70px 1fr 62px;align-items:center;gap:7px;margin:5px 0;font-size:11px;font-weight:400}
.combat-bar{height:12px;background:#252b30;border:1px solid #343c43;overflow:hidden}
.combat-fill{height:100%;background:linear-gradient(90deg,#757f88,#d8dee3);transition:width .18s ease}
.combat-meta{display:flex;flex-wrap:wrap;gap:6px;margin-top:8px}
.combat-meta span{border:1px solid #343c43;padding:3px 5px;font-size:10px;color:#c8d0d6;font-weight:400}
.combat-telegraph{margin-top:7px;font-size:11px;color:#f0c979;font-weight:400}
.combat-popup-button{appearance:none;border:1px solid #69747d;background:#20282e;color:#f1f4f6;border-radius:6px;padding:4px 8px;font:400 10px/1 var(--gameplay-font);letter-spacing:.08em;cursor:pointer}
.combat-popup-button:active{transform:translateY(1px)}
#combatPopup[hidden]{display:none!important}
#combatPopup{position:fixed;inset:0;z-index:12000;background:rgba(0,0,0,.72);display:flex;align-items:center;justify-content:center;padding:16px;font-family:var(--gameplay-font);font-weight:400}
.combat-popup-sheet{width:min(520px,100%);max-height:min(82vh,720px);overflow:auto;background:#0b0e10;border:1px solid #4b555e;border-radius:12px;box-shadow:0 20px 55px rgba(0,0,0,.55);padding:14px;color:#edf1f4}
.combat-popup-head{display:flex;align-items:center;justify-content:space-between;gap:10px}.combat-popup-head h2{font-size:16px;font-weight:400;letter-spacing:.12em;margin:0}.combat-popup-auto{font-size:10px;font-weight:400;border:1px solid #4b555e;border-radius:999px;padding:3px 7px;color:#c9d2d9}.combat-popup-close{border:0;background:transparent;color:#e8edf0;font-size:24px;font-weight:400;line-height:1;cursor:pointer;padding:2px 6px}
.combat-popup-target,.combat-popup-current{border:1px solid #343c43;background:#11161a;border-radius:8px;padding:9px;margin-top:10px;font-weight:400}.combat-popup-target{display:flex;justify-content:space-between;gap:10px;font-size:12px}.combat-popup-current{font-size:12px;letter-spacing:.04em}
.combat-popup-order{display:flex;gap:6px;overflow-x:auto;padding:10px 0 4px;scrollbar-width:thin}.combat-turn-chip{flex:0 0 auto;border:1px solid #343c43;border-radius:999px;padding:5px 8px;font-size:10px;font-weight:400;white-space:nowrap;color:#c7d0d6}.combat-turn-chip.current{border-color:#e2e8ec;color:#fff;background:#283139}
.combat-popup-party{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:7px;margin-top:10px}.combat-popup-slot{min-width:0;border:1px solid #343c43;border-radius:8px;padding:8px;background:#101519;text-align:center}.combat-popup-slot.current{border-color:#e2e8ec;background:#252d33}.combat-popup-slot.empty{opacity:.45}.combat-popup-slot strong{display:block;font-size:11px;font-weight:400;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.combat-popup-slot span{display:block;font-size:9px;font-weight:400;color:#9da8b0;margin-top:3px}
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
/* ANDROID_SWIPE_GESTURE_V2: pointer-first horizontal gesture with touch fallback. */
(function(){
  if(window.__androidSwipeUi)return;window.__androidSwipeUi=true;
  const shell=document.querySelector('.shell'),game=document.querySelector('.game'),side=document.querySelector('.side');
  if(!shell||!game||!side)return;
  const track=document.createElement('div');track.className='page-track';track.id='pageTrack';
  const gameplay=document.createElement('section');gameplay.className='app-page';gameplay.id='gameplayPage';gameplay.setAttribute('aria-label','Gameplay');
  const management=document.createElement('section');management.className='app-page';management.id='managementPage';management.setAttribute('aria-label','Character and management');
  gameplay.appendChild(game);management.appendChild(side);track.appendChild(gameplay);track.appendChild(management);shell.appendChild(track);
  const indicator=document.createElement('div');indicator.className='page-indicator';indicator.setAttribute('aria-hidden','true');indicator.innerHTML='<i class="page-dot active"></i><i class="page-dot"></i>';document.body.appendChild(indicator);
  const dots=indicator.querySelectorAll('.page-dot');let page=0;
  function setPage(next){page=next>0?1:0;track.classList.toggle('show-management',page===1);dots.forEach((dot,i)=>dot.classList.toggle('active',i===page));if(page===1){const view=document.getElementById('characterInventoryView');if(view&&view.hidden){const first=document.querySelector('#party .party-member[data-character]');if(first)first.click();}}}
  function interactive(target){return !!(target&&target.closest&&target.closest('textarea,input,select,button,a,.equipment-detail-modal'))}
  function finishSwipe(dx,dy){if(Math.abs(dx)<42||Math.abs(dx)<=Math.abs(dy)*1.05)return;if(dx<0&&page===0)setPage(1);else if(dx>0&&page===1)setPage(0)}
  function installPointerSwipe(){
    let pointerId=null,startX=0,startY=0,lastX=0,lastY=0,axis='';
    function reset(){pointerId=null;axis='';}
    track.addEventListener('pointerdown',function(e){if(e.pointerType==='mouse'||interactive(e.target)){reset();return}pointerId=e.pointerId;startX=lastX=e.clientX;startY=lastY=e.clientY;axis='';});
    track.addEventListener('pointermove',function(e){if(pointerId!==e.pointerId)return;lastX=e.clientX;lastY=e.clientY;const dx=lastX-startX,dy=lastY-startY;if(!axis&&(Math.abs(dx)>8||Math.abs(dy)>8))axis=Math.abs(dx)>Math.abs(dy)*1.05?'x':'y';if(axis==='x'&&e.cancelable)e.preventDefault();},{passive:false});
    track.addEventListener('pointerup',function(e){if(pointerId!==e.pointerId)return;const dx=e.clientX-startX,dy=e.clientY-startY;if(axis==='x'||!axis)finishSwipe(dx,dy);reset();});
    track.addEventListener('pointercancel',reset);
  }
  function installTouchSwipe(){
    let tracking=false,startX=0,startY=0,lastX=0,lastY=0,axis='';
    function reset(){tracking=false;axis='';}
    track.addEventListener('touchstart',function(e){if(e.touches.length!==1||interactive(e.target)){reset();return}const t=e.touches[0];startX=lastX=t.clientX;startY=lastY=t.clientY;axis='';tracking=true},{passive:true});
    track.addEventListener('touchmove',function(e){if(!tracking||e.touches.length!==1)return;const t=e.touches[0];lastX=t.clientX;lastY=t.clientY;const dx=lastX-startX,dy=lastY-startY;if(!axis&&(Math.abs(dx)>8||Math.abs(dy)>8))axis=Math.abs(dx)>Math.abs(dy)*1.05?'x':'y';if(axis==='x'&&e.cancelable)e.preventDefault();},{passive:false});
    track.addEventListener('touchend',function(e){if(!tracking||!e.changedTouches.length){reset();return}const t=e.changedTouches[0],dx=t.clientX-startX,dy=t.clientY-startY;if(axis==='x'||!axis)finishSwipe(dx,dy);reset();},{passive:true});
    track.addEventListener('touchcancel',reset,{passive:true});
  }
  if('PointerEvent' in window)installPointerSwipe();else installTouchSwipe();
  const detailBack=document.getElementById('characterInventoryBack');if(detailBack)detailBack.textContent='Thu gọn thông tin';
  setPage(0);
})();
</script>
'''
if "ANDROID_SWIPE_UI_V1" not in html:
    if "</body>" not in html:
        raise RuntimeError("Canonical Android swipe body anchor missing")
    html = html.replace("</body>", swipe_script + "\n</body>", 1)


presentation_script_template = r'''<script id="androidGameplayPresentation">
/* ANDROID_GAMEPLAY_PRESENTATION_SCRIPT_V2 */
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
  function storytellingViewport(log){
    var current=log.parentElement;if(current&&current.classList.contains('storytelling-viewport'))return current;
    var viewport=document.createElement('div');viewport.className='storytelling-viewport';
    log.parentNode.insertBefore(viewport,log);viewport.appendChild(log);return viewport;
  }
  function storytellingSurface(log){
    var viewport=storytellingViewport(log);
    for(var i=0;i<viewport.children.length;i++){
      var child=viewport.children[i];
      if(child.classList&&child.classList.contains('storytelling-surface')&&child.dataset.storytelling==='1')return child;
    }
    var article=document.createElement('aside');article.className='storytelling-surface';article.dataset.storytelling='1';article.setAttribute('aria-hidden','true');
    var header=document.createElement('div');header.className='storytelling-header';header.textContent='Storytelling';
    article.appendChild(header);viewport.insertBefore(article,log);return article;
  }
  function markGameMasterSegment(message){
    message.classList.add('storytelling-segment');message.dataset.storytellingSegment='1';
    decorateRoot(message.querySelector('.text')||message);
  }
  function positionStorytellingSurface(log,surface){
    var segments=Array.prototype.slice.call(log.querySelectorAll(':scope > .message.storytelling-segment'));
    var viewport=storytellingViewport(log);
    if(!segments.length){viewport.classList.remove('has-storytelling');if(surface)surface.remove();return;}
    viewport.classList.add('has-storytelling');if(!surface)storytellingSurface(log);
  }
  function normalizeLog(){
    var log=document.getElementById('log');if(!log||normalizing)return;normalizing=true;
    try{
      var children=Array.prototype.slice.call(log.children),hasStorytelling=false;
      children.forEach(function(message){
        if(!message.classList||!message.classList.contains('message'))return;
        var role=roleOf(message);
        if(role==='COMBAT'){message.classList.add('combat');decorateRoot(message.querySelector('.text')||message);return;}
        if(role==='GAME MASTER'&&!isWarning(message)){markGameMasterSegment(message);hasStorytelling=true;}
      });
      if(!hasStorytelling)hasStorytelling=!!log.querySelector(':scope > .message.storytelling-segment');
      var viewport=storytellingViewport(log),surface=viewport.querySelector(':scope > .storytelling-surface');
      positionStorytellingSurface(log,hasStorytelling?storytellingSurface(log):surface);
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
if "ANDROID_GAMEPLAY_PRESENTATION_SCRIPT_V2" not in html:
    if "</body>" not in html:
        raise RuntimeError("Canonical gameplay presentation body anchor missing")
    html = html.replace("</body>", presentation_script + "\n</body>", 1)

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
    "ANDROID_SWIPE_GESTURE_V2",
    "ANDROID_THREE_ACTIONS_V1",
    "ANDROID_GAMEPLAY_PRESENTATION_V2",
    "ANDROID_GAMEPLAY_PRESENTATION_SCRIPT_V2",
    "STORYTELLING_SINGLE_FRAME_V3",
    "header.textContent='Storytelling'",
    "function finishSwipe(dx,dy)",
    "function storytellingViewport(log)",
    "function storytellingSurface(log)",
    "function markGameMasterSegment(message)",
    "function positionStorytellingSurface(log,surface)",
    "storytelling-segment",
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

if "while(i<children.length&&roleOf(children[i])==='GAME MASTER'" in html:
    raise RuntimeError("Legacy consecutive-only Storytelling grouping survived single-frame normalization")
if "storytelling-entry-role" in html or "surface.appendChild(message)" in html:
    raise RuntimeError("Player/Combat transcript entries must remain outside the Storytelling frame")

INDEX.write_text(html, encoding="utf-8")
print("Canonical Android edge-to-edge UI installed: native geometry, equal action controls, one GM-only Storytelling frame, normal Roboto gameplay text, and robust two-page swipe.")

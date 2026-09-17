from pathlib import Path
import runpy

ROOT = Path(__file__).resolve().parent
INDEX = ROOT / "app/src/main/assets/index.html"
html = INDEX.read_text(encoding="utf-8")

old = """    detailAvatar.src=member.avatar||member.avatarRef||(member.id==='kai'?'avatars/kai_avatar.png':'avatars/kai_avatar.png');
    detailAvatar.alt=member.name||member.id||'Nhân vật';"""
new = """    const detailAvatarSrc=member.avatar||member.avatarRef||(member.id==='kai'?'avatars/kai_avatar.png':'');
    detailAvatar.hidden=!detailAvatarSrc;
    if(detailAvatarSrc)detailAvatar.src=detailAvatarSrc;else detailAvatar.removeAttribute('src');
    detailAvatar.alt=member.name||member.id||'Nhân vật';"""

if new not in html:
    if old not in html:
        raise RuntimeError("Character detail avatar fallback anchor not found")
    html = html.replace(old, new, 1)
if "member.id==='kai'?'avatars/kai_avatar.png':'avatars/kai_avatar.png'" in html:
    raise RuntimeError("Non-Kai character still falls back to Kai avatar")
INDEX.write_text(html, encoding="utf-8")

for script in (
    "patch-survival-hud-chat-ux.py",
    "patch-search-action-false-warning.py",
    "patch-friendly-item-display.py",
    "patch-jeff-encounter-2pct.py",
    "patch-entity-encounter-plus-8pct.py",
    "patch-immersive-fullscreen.py",
    "patch-knowledge-engine-source.py",
    "patch-knowledge-context-builder.py",
    "benchmark-knowledge-context.py",
    "patch-startup-survival.py",
    "patch-local-entity-overlay.py",
    "patch-jane-killer.py",
    "patch-three-action-runtime-ui.py",
    "patch-entity-overlay-runtime-hotfix.py",
):
    runpy.run_path(str(ROOT / script), run_name="__main__")

final_html = INDEX.read_text(encoding="utf-8")
final_java = (ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java").read_text(encoding="utf-8")
final_facade = (ROOT / "app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt").read_text(encoding="utf-8")

for marker in (
    'id="searchActionButton"', 'id="submit"', 'id="exploreActionButton"',
    'submitMacroAction("SEARCH","Tìm kiếm")', 'submitMacroAction("EXPLORE","Khám phá")',
    'STEP2_THREE_ACTIONS',
):
    if marker not in final_html:
        raise RuntimeError(f"final UI contract missing: {marker}")
if '<button id="submit">THỰC HIỆN</button>' in final_html:
    raise RuntimeError("legacy single Execute button remains")

for marker in (
    '@JavascriptInterface public void submitAction(String stateJson, String actionKind, String action)',
    '.beginAction(stateJson, actionKind, action)', 'SEARCH HARD LOCK:', 'EXPLORE HARD LOCK:',
    'file:///android_asset/entity/', 'window.backroomEntityOverlay=function(payload)',
    'private void forceEntityEncounterFlag(JSONObject candidateState, JSONObject rolls)',
    'forceEntityEncounterFlag(candidateState, rolls);',
):
    if marker not in final_java:
        raise RuntimeError(f"final Android runtime contract missing: {marker}")

for marker in (
    'fun beginAction(legacyStateJson: String, kindRaw: String, action: String)',
    'private fun commitActionRuntime(', 'ActionRuntime.markSearchCoverage(',
):
    if marker not in final_facade:
        raise RuntimeError(f"final core contract missing: {marker}")

print("Character detail/runtime patch chain verified without retired gameplay systems.")

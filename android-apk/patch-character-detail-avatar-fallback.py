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
print("Character detail avatar fallback hardened: non-Kai members without avatars use no portrait.")

runpy.run_path(str(ROOT / "patch-survival-hud-chat-ux.py"), run_name="__main__")
runpy.run_path(str(ROOT / "patch-an-nhien-follower-final.py"), run_name="__main__")
search_facade = (ROOT / "app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt").read_text(encoding="utf-8")
omnivault_test = (ROOT / "app/src/test/java/com/rabpit/backroom/core/OmnivaultNaturalFlowTest.kt").read_text(encoding="utf-8")
if (
    'isDirectPlayerPickupAction(action)' in search_facade
    and 'scanAndCopyAreRetiredInNaturalFlow' in omnivault_test
):
    print("Search/Omnivault Kotlin authority verified; legacy regression generator skipped.")
else:
    runpy.run_path(str(ROOT / "patch-search-action-false-warning.py"), run_name="__main__")
runpy.run_path(str(ROOT / "patch-annhien-cheat-code.py"), run_name="__main__")
runpy.run_path(str(ROOT / "patch-an-nhien-crocs.py"), run_name="__main__")
runpy.run_path(str(ROOT / "patch-friendly-item-display.py"), run_name="__main__")
runpy.run_path(str(ROOT / "patch-knowledge-engine-source.py"), run_name="__main__")
runpy.run_path(str(ROOT / "patch-knowledge-context-builder.py"), run_name="__main__")
runpy.run_path(str(ROOT / "benchmark-knowledge-context.py"), run_name="__main__")
runpy.run_path(str(ROOT / "patch-startup-survival.py"), run_name="__main__")
runpy.run_path(str(ROOT / "patch-local-entity-overlay.py"), run_name="__main__")
runpy.run_path(str(ROOT / "patch-jane-killer.py"), run_name="__main__")
# Three-action gameplay authority remains split into core + Android bridge. The
# WebView controls themselves are installed once by apply-android-ui.py.
runpy.run_path(str(ROOT / "patch-three-action-core.py"), run_name="__main__")
runpy.run_path(str(ROOT / "patch-three-action-bridge.py"), run_name="__main__")
runpy.run_path(str(ROOT / "patch-entity-overlay-runtime-hotfix.py"), run_name="__main__")

final_html = INDEX.read_text(encoding="utf-8")
final_java = (ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java").read_text(encoding="utf-8")
final_facade = (ROOT / "app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt").read_text(encoding="utf-8")

# UI assertions deliberately live in apply-android-ui.py now. This stage only
# verifies the gameplay and visual data contracts it actually owns.
for marker in (
    '@JavascriptInterface public void submitAction(String stateJson, String actionKind, String action)',
    '.beginAction(stateJson, actionKind, action)', 'SEARCH HARD LOCK:', 'EXPLORE HARD LOCK:',
    'file:///android_asset/entity/', 'window.backroomEntityOverlay=function(payload)',
    'private void forceEntityEncounterFlag(JSONObject candidateState, JSONObject rolls)',
    'forceEntityEncounterFlag(candidateState, rolls);',
):
    if marker not in final_java:
        raise RuntimeError(f"Android runtime contract missing: {marker}")

for marker in (
    'fun beginAction(legacyStateJson: String, kindRaw: String, action: String)',
    'private fun commitActionRuntime(', 'ActionRuntime.markSearchCoverage(',
):
    if marker not in final_facade:
        raise RuntimeError(f"Core contract missing: {marker}")

print("Character/runtime contract verified: core + bridge actions and avatar/entity authority; WebView layout deferred to canonical Android UI.")

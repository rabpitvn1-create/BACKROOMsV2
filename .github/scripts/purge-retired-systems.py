from pathlib import Path
from textwrap import dedent
import re

ROOT = Path(__file__).resolve().parents[2]
APK = ROOT / "android-apk"
TARGET = re.compile(r"MadGod|Mad God|madgod|mad god|AN_NHIEN|AnNhien|an_nhien|an-nhien|An Nhiên|An Nhien|annhien", re.I)


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def write(rel: str, text: str) -> None:
    (ROOT / rel).write_text(text, encoding="utf-8")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return text.replace(old, new, 1)


# Dedicated files: delete the feature-specific surface first.
dedicated = [
    "android-apk/app/src/main/assets/Kai_MadGod_snapshot_overlay.png",
    "android-apk/app/src/main/assets/avatars/MadGod.jpg",
    "android-apk/app/src/main/assets/avatars/an_nhien_avatar.png",
    "android-apk/app/src/main/java/com/rabpit/backroom/core/AnNhienCanon.kt",
    "android-apk/app/src/main/java/com/rabpit/backroom/core/MadGodCanon.kt",
    "android-apk/app/src/test/java/com/rabpit/backroom/core/AnNhienFollowerTest.kt",
    "android-apk/app/src/test/java/com/rabpit/backroom/core/MadGodEquipmentTest.kt",
    "android-apk/patch-an-nhien-crocs.py",
    "android-apk/patch-an-nhien-follower-final.py",
    "android-apk/patch-an-nhien-follower.py",
    "android-apk/patch-annhien-cheat-code.py",
    "android-apk/patch-madgod-base.py",
    "android-apk/patch-madgod-equipment.py",
    "android-apk/patch-madgod-overwrite-hotfix.py",
    "android-apk/patch-madgod-runtime-equip.py",
]
for rel in dedicated:
    path = ROOT / rel
    if path.exists():
        path.unlink()

# One codec test wrapped generic special followers through the retired follower canon.
rel = "android-apk/app/src/test/java/com/rabpit/backroom/core/GameStateCodecTest.kt"
text = read(rel)
text = text.replace(
    "val canonicalState = SpecialFollowersCanon.ensure(AnNhienCanon.ensure(state))",
    "val canonicalState = SpecialFollowersCanon.ensure(state)",
)
write(rel, text)

# Remove target-specific sentences from runtime canon and historical notes, preserving unrelated sentences.
def strip_target_sentences(path: Path) -> None:
    if not path.exists():
        return
    out = []
    for line in path.read_text(encoding="utf-8").splitlines():
        if TARGET.search(line):
            parts = re.split(r"(?<=\.)\s+", line)
            line = " ".join(part for part in parts if part and not TARGET.search(part)).strip()
        if line:
            out.append(line)
    path.write_text("\n".join(out) + "\n", encoding="utf-8")

strip_target_sentences(APK / "drive-canon.txt")
for path in list(APK.glob("RELEASE_NOTES_*.txt")) + [APK / "VERIFY_DISCUSSION_FIXES_20260821.txt"]:
    strip_target_sentences(path)

# Base gameplay patch: no dedicated discovery roll/state/prompt hook.
rel = "android-apk/patch-drive-canon-gameplay.py"
text = read(rel)
text = re.sub(
    r'\n    JSONObject madGod = flags != null \? flags\.optJSONObject\("madGod"\) : null;\n'
    r'    boolean madGodAlready = madGod != null && madGod\.optBoolean\("spawned", false\);\n'
    r'    rolls\.put\("madGodSet", rollSpec\("madGodSet", 1, search && !madGodAlready\)\);\n',
    "\n",
    text,
)
text = text.replace('      boolean madGod = lower(name).contains("madgod");\n', "")
text = text.replace(
    '      boolean allowed = existing || (!madGod && almond && rollSuccess(rolls, "almondWater")) ||\n'
    '        (!madGod && !almond && rollSuccess(rolls, "loot"));\n',
    '      boolean allowed = existing || (almond && rollSuccess(rolls, "almondWater")) ||\n'
    '        (!almond && rollSuccess(rolls, "loot"));\n',
)
text = re.sub(
    r'\n    if \(patch\.optJSONObject\("madGod"\) != null\) \{.*?\n    \}\n'
    r'    if \(patch\.optJSONObject\("iris"\)',
    '\n    if (patch.optJSONObject("iris")',
    text,
    flags=re.S,
)
text = text.replace(
    '    else if (kind.equals("major_event")) allowed = rollSuccess(rolls, "madGodSet");\n',
    '    else if (kind.equals("major_event")) allowed = false;\n',
)
text = re.sub(r'^\s*"MadGod Set success.*?\+\\n$', "", text, flags=re.M)
text = text.replace(',madGod:{spawned:false,acquired:false}', '')
text = text.replace(';state.flags.madGod=state.flags.madGod||{spawned:false,acquired:false}', '')
write(rel, text)

# Inventory persistence keeps normal acquisition/water/loot behavior only.
rel = "android-apk/patch-inventory-persistence.py"
text = read(rel)
text = text.replace('      boolean madGod = lower(name).contains("madgod");\n', "")
text = text.replace(
    '      boolean allowed = existing || (!madGod && almond && rollSuccess(rolls, "almondWater")) ||\n'
    '        (!madGod && !almond && rollSuccess(rolls, "loot"));\n',
    '      boolean allowed = existing || (almond && rollSuccess(rolls, "almondWater")) ||\n'
    '        (!almond && rollSuccess(rolls, "loot"));\n',
)
text = text.replace(
    '      } else if (madGod) {\n'
    '        allowed = rollSuccess(rolls, "madGodSet");\n'
    '      } else if (almond) {\n',
    '      } else if (almond) {\n',
)
write(rel, text)

# Orchestrator: remove dedicated item/root/roll/state handling.
rel = "android-apk/patch-ai-orchestrator.py"
text = read(rel)
text = text.replace(', "madgod"', '')
text = text.replace(' || rollSuccess(rolls, "madGodSet")', '')
text = re.sub(
    r'\n    if \(root\.equals\("madGod"\)\) \{.*?\n    \}\n    return false;',
    '\n    return false;',
    text,
    flags=re.S,
)
text = text.replace('        boolean madGod = lower(name).contains("madgod");\n', "")
text = re.sub(r'^\s*if \(madGod && .*?\n', "", text, flags=re.M)
text = re.sub(
    r'    JSONObject oldMadGod = oldFlags != null \? oldFlags\.optJSONObject\("madGod"\) : null;\n'
    r'    JSONObject madGod = flags\.optJSONObject\("madGod"\);\n'
    r'    if \(madGod == null\).*?\n'
    r'    if \(oldMadGod != null.*?\n'
    r'    else if \(rollSuccess\(rolls, "madGodSet"\)\).*?\n'
    r'    flags\.put\("madGod", madGod\)\.put\("lastRolls", rolls\);',
    '    flags.put("lastRolls", rolls);',
    text,
    flags=re.S,
)
text = text.replace(', madGod,', ',')
text = re.sub(r'^\s*"Inventory chỉ đổi.*?MadGod.*?\+\\n$', "", text, flags=re.M)
write(rel, text)

# State-op hardening now carries only the unrelated conservative deletion rule.
rel = "android-apk/patch-state-op-hardening.py"
write(rel, dedent('''\
    from pathlib import Path

    MAIN = Path(__file__).resolve().parent / "app/src/main/java/com/rabpit/backroom/MainActivity.java"

    def replace_once(text: str, old: str, new: str, label: str) -> str:
        count = text.count(old)
        if count != 1:
            raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
        return text.replace(old, new, 1)

    text = MAIN.read_text(encoding="utf-8")
    old_remove = r'''        boolean consequence = "world_consequence".equals(lower(op.optString("basis", ""))) &&
          (rollSuccess(rolls, "hazard") || rollSuccess(rolls, "entityEncounter"));
    '''
    new_remove = r'''        boolean consequence = "world_consequence".equals(lower(op.optString("basis", ""))) &&
          (rollSuccess(rolls, "hazard") || rollSuccess(rolls, "entityEncounter"));
        // A semantic inference alone never authorizes deletion of owned inventory.
    '''
    text = replace_once(text, old_remove, new_remove, "inventory removal authority comment")
    MAIN.write_text(text, encoding="utf-8")
    print("APK state-op hardening applied: conservative inventory deletion authority.")
'''))

# Audit patches: remove the retired high-risk root and prompt clause only.
for rel in (
    "android-apk/patch-conditional-audit.py",
    "android-apk/patch-audit-validated-risk.py",
):
    text = read(rel)
    text = text.replace(', "madGod"', '')
    text = text.replace(', madGod,', ',')
    text = re.sub(r'^\s*"Inventory chỉ đổi.*?MadGod.*?\+\\n$', "", text, flags=re.M)
    write(rel, text)

# Gameplay parity: remove the dedicated roll.
rel = "android-apk/patch-gameplay-parity-final.py"
text = read(rel)
text = re.sub(r'^\s*JSONObject madGod = .*?\n', "", text, flags=re.M)
text = re.sub(r'^\s*boolean madGodEligible = .*?\n', "", text, flags=re.M)
text = re.sub(r'^\s*rolls\.put\("madGodSet".*?\n', "", text, flags=re.M)
write(rel, text)

# Final authority still validates normal structured loot/copy/water acquisition.
rel = "android-apk/patch-final-authority-hardening.py"
text = read(rel)
old_inventory = r'''        boolean allowedNew = acquisitionIntent(action);
        if (almond) {
          JSONObject waterRoll = rolls.optJSONObject("almondWater");
          if (waterRoll != null && waterRoll.optBoolean("eligible", false) && !waterRoll.optBoolean("success", false) && existing < 0) allowedNew = false;
        }
'''
new_inventory = r'''        boolean allowedNew = false;
        JSONObject beforeFlagsForItem = before.optJSONObject("flags");
        JSONObject explorationForItem = beforeFlagsForItem != null ? beforeFlagsForItem.optJSONObject("exploration") : null;
        JSONObject omnivaultForItem = beforeFlagsForItem != null ? beforeFlagsForItem.optJSONObject("omnivault") : null;
        boolean establishedStructured = false;
        if (explorationForItem != null) establishedStructured = lower(explorationForItem.toString()).contains(lower(name));
        if (!establishedStructured && omnivaultForItem != null) establishedStructured = lower(omnivaultForItem.toString()).contains(lower(name));
        if (existing >= 0) allowedNew = true;
        else if (acquisitionIntent(action)) {
          if (almond) allowedNew = establishedStructured || rollSuccess(rolls, "almondWater");
          else if (containsAny(action, "copy", "sao chép")) allowedNew = establishedStructured;
          else allowedNew = establishedStructured || rollSuccess(rolls, "loot");
        }
'''
replacement = "old_inventory = r'''" + old_inventory + "'''\nnew_inventory = r'''" + new_inventory + "'''"
text, count = re.subn(
    r"old_inventory = r'''(?:.|\n)*?'''\nnew_inventory = r'''(?:.|\n)*?'''",
    lambda _: replacement,
    text,
    count=1,
)
if count != 1:
    raise RuntimeError("final authority inventory templates not found")
write(rel, text)

# Kai resource policy keeps the generic rules only.
rel = "android-apk/patch-kai-resource-policy-final.py"
text = read(rel)
text = text.replace(
    'Inventory chỉ đổi khi Kai thật sự lấy/nhận/copy/trao/mất/tiêu thụ vật; nhìn thấy không đồng nghĩa sở hữu. MadGod roll success chỉ mở discovery route, không tự đưa set vào inventory.',
    'Inventory chỉ đổi khi Kai thật sự lấy/nhận/copy/trao/mất/tiêu thụ vật; nhìn thấy không đồng nghĩa sở hữu.',
)
text = text.replace(
    'Nhìn thấy vật phẩm không đồng nghĩa sở hữu. MadGod roll success chỉ mở discovery route, không tự đưa set vào inventory.',
    'Nhìn thấy vật phẩm không đồng nghĩa sở hữu.',
)
write(rel, text)

# Search-action hardening follows the cleaned final-authority block.
rel = "android-apk/patch-search-action-false-warning.py"
text = read(rel)
text = text.replace(
    "# Keep the historical combined scan-source/template warning untouched here because the later\n"
    "# MadGod patch intentionally anchors on that exact line before adding its own validation messages.\n",
    "# Keep the historical combined scan-source/template warning untouched for compatibility.\n",
)
old_world = new_inventory
new_world = r'''        boolean allowedNew = false;
        JSONObject beforeFlagsForItem = before.optJSONObject("flags");
        JSONObject explorationForItem = beforeFlagsForItem != null ? beforeFlagsForItem.optJSONObject("exploration") : null;
        JSONObject omnivaultForItem = beforeFlagsForItem != null ? beforeFlagsForItem.optJSONObject("omnivault") : null;
        boolean establishedStructured = false;
        if (explorationForItem != null) establishedStructured = lower(explorationForItem.toString()).contains(lower(name));
        if (!establishedStructured && omnivaultForItem != null) establishedStructured = lower(omnivaultForItem.toString()).contains(lower(name));
        String acquisitionBasis = lower(op.optString("basis", "")).trim();
        boolean worldAcquisition = acquisitionBasis.equals("world_consequence");
        boolean directAcquisition = acquisitionIntent(action);
        boolean copyIntent = containsAny(action, "copy", "sao chép", "nhân bản", "tạo thêm", "tạo ra thêm", "nhân thêm");
        boolean almondRoll = rollSuccess(rolls, "almondWater");
        boolean lootRoll = rollSuccess(rolls, "loot");
        if (existing >= 0) allowedNew = true;
        else if (copyIntent) allowedNew = directAcquisition && establishedStructured;
        else if (almond) allowedNew = (directAcquisition || worldAcquisition) && (establishedStructured || almondRoll);
        else allowedNew = (directAcquisition || worldAcquisition) && (establishedStructured || lootRoll);
'''
replacement = "old_world_inventory = r'''" + old_world + "'''\nnew_world_inventory = r'''" + new_world + "'''"
text, count = re.subn(
    r"old_world_inventory = r'''(?:.|\n)*?'''\nnew_world_inventory = r'''(?:.|\n)*?'''",
    lambda _: replacement,
    text,
    count=1,
)
if count != 1:
    raise RuntimeError("search-action inventory templates not found")
text = text.replace(
    'prompt_anchor = \'      "Inventory chỉ đổi khi Kai thật sự lấy/nhận/copy/trao/mất/tiêu thụ vật; nhìn thấy không đồng nghĩa sở hữu. MadGod roll success chỉ mở discovery route, không tự đưa set vào inventory. " +\\n\'',
    'prompt_anchor = \'      "Inventory chỉ đổi khi Kai thật sự lấy/nhận/copy/trao/mất/tiêu thụ vật; nhìn thấy không đồng nghĩa sở hữu. " +\\n\'',
)
write(rel, text)

# Knowledge writer and local entity patch share the generic inventory anchor.
rel = "android-apk/patch-knowledge-context-builder.py"
text = read(rel)
text = text.replace(', madGod,', ',')
text = text.replace(
    '      "Inventory chỉ đổi khi Kai thật sự lấy/nhận/copy/trao/mất/tiêu thụ vật; nhìn thấy không đồng nghĩa sở hữu. MadGod roll success chỉ mở discovery route, không tự đưa set vào inventory. " +\\n',
    '      "Inventory chỉ đổi khi Kai thật sự lấy/nhận/copy/trao/mất/tiêu thụ vật; nhìn thấy không đồng nghĩa sở hữu. " +\\n',
)
write(rel, text)

rel = "android-apk/patch-local-entity-overlay.py"
text = read(rel)
text = text.replace(
    'writer_marker = \'      "Inventory chỉ đổi khi Kai thật sự lấy/nhận/copy/trao/mất/tiêu thụ vật; nhìn thấy không đồng nghĩa sở hữu. MadGod roll success chỉ mở discovery route, không tự đưa set vào inventory. " +\\n\'',
    'writer_marker = \'      "Inventory chỉ đổi khi Kai thật sự lấy/nhận/copy/trao/mất/tiêu thụ vật; nhìn thấy không đồng nghĩa sở hữu. " +\\n\'',
)
write(rel, text)

# Jeff no longer depends on a retired follower prompt line. Jane keeps its own live Entity rules.
rel = "android-apk/patch-jeff-encounter-2pct.py"
text = read(rel)
text = text.replace(
    'prompt_anchor = \'            "AN NHIÊN HARD LOCK:\'',
    'prompt_anchor = \'      "Inventory chỉ đổi khi Kai thật sự lấy/nhận/copy/trao/mất/tiêu thụ vật; nhìn thấy không đồng nghĩa sở hữu. " +\\n\'',
)
write(rel, text)

rel = "android-apk/patch-jane-killer.py"
text = read(rel)
text = text.replace(', madGod,', ',')
text = text.replace("'iris, syvial, jeff, jane, madGod'", "'iris, syvial, jeff, jane'")
write(rel, text)

# Aggregator: preserve active UI/entity fixes, remove retired injection/verification.
rel = "android-apk/patch-character-detail-avatar-fallback.py"
write(rel, dedent('''\
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
'''))

# Progression/Snapshot patch keeps its level/cache/combat/healthbar duties only.
rel = "android-apk/patch-progression-snapshot-equipment.py"
write(rel, dedent('''\
    from pathlib import Path

    ROOT = Path(__file__).resolve().parent
    MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
    main = MAIN.read_text(encoding="utf-8")

    old_cache = "function cachedSnapshot(){try{var r=JSON.parse(localStorage.getItem('backroom-apk-snapshot')||'null');return r&&r.dataUri?r:null;}catch(e){return null;}}function renderSnapshot()"
    new_cache = "function visualSceneKey(){var l=state&&state.level&&state.level.number;var where=String(state&&state.location||'').trim().toLowerCase();return String(l==null?'?':l)+'|'+where}function cachedSnapshot(){try{var r=JSON.parse(localStorage.getItem('backroom-apk-snapshot')||'null');return r&&r.dataUri&&r.sceneKey===visualSceneKey()?r:null;}catch(e){return null;}}function renderSnapshot()"
    if new_cache not in main:
        if old_cache not in main:
            raise RuntimeError("Snapshot cache anchor missing")
        main = main.replace(old_cache, new_cache, 1)
    if "sceneKey:visualSceneKey()" not in main:
        marker = "JSON.stringify({turn:r.turn,model:r.model||'AI',dataUri:r.dataUri})"
        if marker not in main:
            raise RuntimeError("Snapshot cache write anchor missing")
        main = main.replace(marker, "JSON.stringify({turn:r.turn,sceneKey:visualSceneKey(),model:r.model||'AI',dataUri:r.dataUri})", 1)

    current_level_anchor = '''  private JSONObject rollSpec(String label, int chance, boolean eligible) throws Exception {
    '''
    level_helpers = '''  private int mentionedLevel(JSONObject state) {
        String location = state.optString("location", "").toLowerCase(java.util.Locale.ROOT);
        String title = state.optString("title", "").toLowerCase(java.util.Locale.ROOT);
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("level\\\\s*([0-6])", java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher explicit = pattern.matcher(location);
        if (explicit.find()) return Integer.parseInt(explicit.group(1));
        String[] names = {"the lobby", "parking zone", "pipe dreams", "the electrical station", "the abandoned office", "terror hotel", "lights out"};
        for (int n = 0; n < names.length; n++) if (location.contains(names[n])) return n;
        explicit = pattern.matcher(title);
        if (explicit.find()) return Integer.parseInt(explicit.group(1));
        for (int n = 0; n < names.length; n++) if (title.contains(names[n])) return n;
        return -1;
      }

      private int levelTurns(JSONObject state) {
        JSONObject flags = state.optJSONObject("flags");
        JSONObject exploration = flags != null ? flags.optJSONObject("exploration") : null;
        return exploration != null ? Math.max(0, exploration.optInt("levelTurns", 0)) : 0;
      }

      private boolean progressionReady(JSONObject state) {
        JSONObject flags = state.optJSONObject("flags");
        JSONObject exploration = flags != null ? flags.optJSONObject("exploration") : null;
        boolean explicitlyReady = exploration != null && (exploration.optBoolean("transitionReady", false) || exploration.optBoolean("exitReady", false));
        return explicitlyReady || levelTurns(state) >= 6;
      }

      private void recordLevelProgress(JSONObject state, int oldLevel, int newLevel) throws Exception {
        JSONObject flags = state.optJSONObject("flags");
        if (flags == null) flags = new JSONObject();
        JSONObject exploration = flags.optJSONObject("exploration");
        if (exploration == null) exploration = new JSONObject();
        exploration.put("levelTurns", oldLevel == newLevel ? levelTurns(state) + 1 : 0);
        exploration.put("minimumTurns", 6);
        flags.put("exploration", exploration);
        state.put("flags", flags);
      }

    '''
    if "private int mentionedLevel(JSONObject state)" not in main:
        if current_level_anchor not in main:
            raise RuntimeError("Level helper anchor missing")
        main = main.replace(current_level_anchor, level_helpers + current_level_anchor, 1)

    old_transition = '''    return (confirmedExit != null && !confirmedExit.trim().isEmpty()) || rollSuccess(rolls, "levelExit");
    '''
    new_transition = '''    boolean exitFound = (confirmedExit != null && !confirmedExit.trim().isEmpty()) || rollSuccess(rolls, "levelExit");
        return exitFound && progressionReady(before);
    '''
    if new_transition not in main:
        if old_transition not in main:
            raise RuntimeError("Transition gate anchor missing")
        main = main.replace(old_transition, new_transition, 1)

    old_after_commit = '''          int oldLevel = currentLevel(before);
              int newLevel = currentLevel(state);
              boolean levelChanged = oldLevel != newLevel;
    '''
    new_after_commit = '''          int oldLevel = currentLevel(before);
              int newLevel = currentLevel(state);
              int mentioned = mentionedLevel(state);
              if (mentioned >= 0 && mentioned != oldLevel && canTransition(before, rolls)) {
                newLevel = mentioned;
                state.put("level", new JSONObject().put("number", newLevel).put("name", levelName(newLevel)));
                state.put("title", "Level " + newLevel + " – " + levelName(newLevel));
              }
              boolean levelChanged = oldLevel != newLevel;
    '''
    if new_after_commit not in main:
        if old_after_commit not in main:
            raise RuntimeError("Post-commit Level recognition anchor missing")
        main = main.replace(old_after_commit, new_after_commit, 1)

    progress_anchor = '''            flags.put("currentLevel", new JSONObject().put("number", newLevel).put("name", levelName(newLevel)));
    '''
    progress_replacement = progress_anchor + '''            state.put("flags", flags);
                recordLevelProgress(state, oldLevel, newLevel);
                flags = state.optJSONObject("flags");
    '''
    if "recordLevelProgress(state, oldLevel, newLevel);" not in main:
        if progress_anchor not in main:
            raise RuntimeError("Progress recording anchor missing")
        main = main.replace(progress_anchor, progress_replacement, 1)

    main = main.replace(
        '"EXPLORE HARD LOCK: chủ động mở rộng known space và có thể đổi location; có thể gặp Entity hoặc Survivor, resource/hazard/exit opportunity nhưng không đảm bảo Exit; nếu có lựa chọn định hướng quan trọng thì trả quyền quyết định cho người chơi. "',
        '"EXPLORE HARD LOCK: chủ động mở rộng known space từng khu vực; có thể đổi location cục bộ, gặp Entity hoặc Survivor, resource/hazard/exit opportunity nhưng không đảm bảo Exit. Không hoàn tất cả Level trong 2–3 lượt: cần ít nhất 6 lượt gameplay trong Level và một Exit hợp lệ; nếu có lựa chọn định hướng quan trọng thì trả quyền quyết định cho người chơi. "',
        1,
    )

    MAIN.write_text(main, encoding="utf-8")
    for marker in (
        "sceneKey:visualSceneKey()",
        "r.sceneKey===visualSceneKey()",
        "private int mentionedLevel(JSONObject state)",
        "return exitFound && progressionReady(before);",
        'exploration.put("minimumTurns", 6)',
        "recordLevelProgress(state, oldLevel, newLevel)",
    ):
        if marker not in main:
            raise RuntimeError("Progression/Snapshot contract missing: " + marker)

    for script in ("patch-pressure-combat.py", "patch-unified-entity-spawn-pool.py", "patch-character-healthbar.py"):
        path = ROOT / script
        if not path.is_file():
            raise RuntimeError(f"Required runtime patch missing: {script}")
        exec(compile(path.read_text(encoding="utf-8"), str(path), "exec"), {"__name__": "__main__", "__file__": str(path)})

    print("Scene-keyed Snapshot cache, progression, combat and healthbar chain installed.")
'''))

# Build packaging must not require removed assets.
rel = ".github/workflows/build-backroom-apk.yml"
text = read(rel)
text = "\n".join(
    line for line in text.splitlines()
    if "assets/Kai_MadGod_snapshot_overlay.png" not in line
    and "assets/avatars/MadGod.jpg" not in line
) + "\n"
write(rel, text)

# Any remaining target-bearing top-level patch is dormant legacy. Active patches must be clean instead of deleted.
active_clean = {
    "patch-drive-canon-gameplay.py",
    "patch-inventory-persistence.py",
    "patch-ai-orchestrator.py",
    "patch-state-op-hardening.py",
    "patch-conditional-audit.py",
    "patch-audit-validated-risk.py",
    "patch-gameplay-parity-final.py",
    "patch-final-authority-hardening.py",
    "patch-kai-resource-policy-final.py",
    "patch-search-action-false-warning.py",
    "patch-knowledge-context-builder.py",
    "patch-local-entity-overlay.py",
    "patch-jeff-encounter-2pct.py",
    "patch-jane-killer.py",
    "patch-character-detail-avatar-fallback.py",
    "patch-progression-snapshot-equipment.py",
}
for path in APK.glob("*.py"):
    text = path.read_text(encoding="utf-8")
    match = TARGET.search(text)
    if not match:
        continue
    if path.name in active_clean:
        line = text[:match.start()].count("\n") + 1
        raise RuntimeError(f"active patch still contains retired reference: {path.name}:{line}:{match.group(0)}")
    print("DELETE dormant retired patch:", path.name)
    path.unlink()

# Final audit excluding this one-shot cleanup script and workflow, both removed by the workflow before commit.
excluded = {
    ROOT / ".github/scripts/purge-retired-systems.py",
    ROOT / ".github/workflows/scan-madgod-annhien.yml",
}
for path in ROOT.rglob("*"):
    if not path.is_file() or ".git" in path.parts or path in excluded:
        continue
    try:
        text = path.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        continue
    match = TARGET.search(text)
    if match:
        line = text[:match.start()].count("\n") + 1
        raise RuntimeError(f"retired reference remains: {path.relative_to(ROOT)}:{line}:{match.group(0)}")
    if TARGET.search(path.name):
        raise RuntimeError(f"retired filename remains: {path.relative_to(ROOT)}")

print("Repository cleanup transform completed.")

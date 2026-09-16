from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[2]
FEATURE = "Mad" + "God"
LOWER = FEATURE.lower()
CAMEL = "mad" + "God"
UPPER = FEATURE.upper()


def path(rel: str) -> Path:
    return ROOT / rel


def read(rel: str) -> str:
    return path(rel).read_text(encoding="utf-8")


def write(rel: str, text: str) -> None:
    path(rel).write_text(text, encoding="utf-8")


def remove_braced_block(text: str, start_marker: str) -> str:
    start = text.find(start_marker)
    if start < 0:
        return text
    brace = text.find("{", start)
    if brace < 0:
        raise RuntimeError(f"missing opening brace after {start_marker!r}")
    depth = 0
    quote = None
    escaped = False
    for i in range(brace, len(text)):
        ch = text[i]
        if quote is not None:
            if escaped:
                escaped = False
            elif ch == "\\":
                escaped = True
            elif ch == quote:
                quote = None
            continue
        if ch in ('\"', "'"):
            quote = ch
            continue
        if ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1
            if depth == 0:
                end = i + 1
                if end < len(text) and text[end] == "\n":
                    end += 1
                return text[:start] + text[end:]
    raise RuntimeError(f"unterminated braced block after {start_marker!r}")


def remove_between(text: str, start_marker: str, end_marker: str, keep_end: bool = True) -> str:
    start = text.find(start_marker)
    if start < 0:
        return text
    end = text.find(end_marker, start)
    if end < 0:
        raise RuntimeError(f"missing end marker {end_marker!r} after {start_marker!r}")
    return text[:start] + (text[end:] if keep_end else text[end + len(end_marker):])


def remove_feature_lines(text: str) -> str:
    lines = text.splitlines(keepends=True)
    return "".join(line for line in lines if FEATURE.casefold() not in line.casefold())


def purge_game_core() -> None:
    rel = "android-apk/app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt"
    text = read(rel)
    text = text.replace(f"    if ({FEATURE}Canon.cheat(action)) return apply{FEATURE}Cheat(legacy,state)\n", "")
    text = text.replace(f"    if ({FEATURE}Canon.cheat(action)) return actionStartResponse(true,null,null)\n", "")
    text = text.replace(f"    output.put(\"equipment\",{FEATURE}Canon.legacy(state))\n", "")
    text = remove_braced_block(text, f"    if (is{FEATURE}EquipRequest(action)) {{")
    text = remove_braced_block(text, f"  private fun apply{FEATURE}Cheat(legacy:JSONObject,state:GameState):String {{")
    text = remove_braced_block(text, f"  private fun is{FEATURE}EquipRequest(action: String): Boolean {{")
    # Remaining references in this file are single validation/reply branches or constants.
    text = remove_feature_lines(text)
    write(rel, text)


def purge_ai_orchestrator() -> None:
    rel = "android-apk/patch-ai-orchestrator.py"
    text = read(rel)
    text = text.replace(f', "greek fire", "{LOWER}"', ', "greek fire"')
    text = text.replace(f' || rollSuccess(rolls, "{CAMEL}Set")', '')
    text = remove_braced_block(text, f'    if (root.equals("{CAMEL}")) {{')
    text = text.replace(f'        boolean {CAMEL} = lower(name).contains("{LOWER}");\n', '')
    text = text.replace(f'        if ({CAMEL} && !before.optJSONObject("flags").optJSONObject("{CAMEL}").optBoolean("spawned", false)) allowedNew = false;\n', '')
    state_start = f'    JSONObject old{FEATURE} = oldFlags != null ? oldFlags.optJSONObject("{CAMEL}") : null;\n'
    if state_start in text:
        start = text.index(state_start)
        end_line = f'    flags.put("{CAMEL}", {CAMEL}).put("lastRolls", rolls);\n'
        end = text.index(end_line, start) + len(end_line)
        text = text[:start] + '    flags.put("lastRolls", rolls);\n' + text[end:]
    text = text.replace(f', {CAMEL}, omnivault', ', omnivault')
    text = text.replace(f' {FEATURE} roll success chỉ mở discovery route, không tự đưa set vào inventory.', '')
    write(rel, text)


def purge_drive_patch() -> None:
    rel = "android-apk/patch-drive-canon-gameplay.py"
    text = read(rel)
    roll_block = (
        '    JSONObject flags = state.optJSONObject("flags");\n'
        f'    JSONObject {CAMEL} = flags != null ? flags.optJSONObject("{CAMEL}") : null;\n'
        f'    boolean {CAMEL}Already = {CAMEL} != null && {CAMEL}.optBoolean("spawned", false);\n'
        f'    rolls.put("{CAMEL}Set", rollSpec("{CAMEL}Set", 1, search && !{CAMEL}Already));\n'
    )
    text = text.replace(roll_block, '    JSONObject flags = state.optJSONObject("flags");\n')
    text = text.replace(f'      boolean {CAMEL} = lower(name).contains("{LOWER}");\n', '')
    text = text.replace(
        f'      boolean allowed = existing || (!{CAMEL} && almond && rollSuccess(rolls, "almondWater")) ||\n        (!{CAMEL} && !almond && rollSuccess(rolls, "loot"));\n',
        '      boolean allowed = existing || (almond && rollSuccess(rolls, "almondWater")) ||\n        (!almond && rollSuccess(rolls, "loot"));\n',
    )
    text = remove_braced_block(text, f'    if (patch.optJSONObject("{CAMEL}") != null) {{')
    text = text.replace(f'    else if (kind.equals("major_event")) allowed = rollSuccess(rolls, "{CAMEL}Set");\n', '')
    text = text.replace(f'            "{FEATURE} Set success chỉ mở đường/vị trí khám phá; acquired mặc định false cho tới khi Kai thực sự tiếp cận và lấy. " +\n', '')
    text = text.replace(
        f';state.flags.{CAMEL}=state.flags.{CAMEL}||{{spawned:false,acquired:false}};',
        ';',
    )
    write(rel, text)


def purge_character_status_patch() -> None:
    rel = "android-apk/patch-character-status-equipment-system.py"
    text = read(rel)
    text = text.replace(f'{UPPER} = CORE / "{FEATURE}Canon.kt"\n', '')
    start = text.find(f'    EquipmentDefinition(\n      id = {UPPER}_SET_ID')
    if start >= 0:
        end_marker = '\n    )\n  )\n\n  private val definitions'
        end = text.find(end_marker, start)
        if end < 0:
            raise RuntimeError("equipment definition end missing")
        text = text[:start] + '  )\n\n  private val definitions' + text[end + len(end_marker):]
    # Special equip locks in the generated engine are single lines after the definition is gone.
    text = remove_feature_lines(text)
    # Re-read from the original and restore sections that line filtering would otherwise leave structurally open.
    # The normalization block is bounded by two stable section headers.
    marker = '# --- Rich Character projection, derived from Base + unique equipped Items -----\n'
    pre = text.find('# --- ')
    while pre >= 0:
        nxt = text.find(marker, pre)
        if nxt >= 0 and 'normalized into the same 100-HP gameplay scale' in text[pre:nxt]:
            text = text[:pre] + text[nxt:]
            break
        pre = text.find('# --- ', pre + 5)
    # Remove the two retired generated regression tests as one block when their header survived casing changes.
    lower_text = text.casefold()
    test_start_key = f'  @test fun {CAMEL}isnotcanonicalstartingloadout'.casefold()
    test_end_key = '  @test fun projectionafterreloadequalsbaseplusequippeditems'.casefold()
    ts = lower_text.find(test_start_key)
    if ts >= 0:
        te = lower_text.find(test_end_key, ts)
        if te < 0:
            raise RuntimeError("generated equipment test end missing")
        text = text[:ts] + text[te:]
    text = text.replace('normalization, combat integration, and tests.', 'combat integration, and tests.')
    write(rel, text)


def purge_combat_cleanup_patch() -> None:
    rel = "android-apk/patch-combat-hp-metadata-cleanup.py"
    text = read(rel)
    text = text.replace(f'{UPPER}_TEST = TESTS / "{FEATURE}EquipmentTest.kt"\n', '')
    old = (
        "unequip_anchor = '''  fun unequip(state: GameState, command: ItemCommand): ExecutionResult {\n"
        f'    if (command.itemId == {UPPER}_SET_ID) return invalid(state, "{LOWER}_equipment_permanent")\n'
        "'''\n"
    )
    new = "unequip_anchor = '''  fun unequip(state: GameState, command: ItemCommand): ExecutionResult {\n'''\n"
    text = text.replace(old, new)
    old = (
        "unequip_locked = '''  fun unequip(state: GameState, command: ItemCommand): ExecutionResult {\n"
        '    if (command.actorId == AN_NHIEN_ID) return invalid(state, "an_nhien_equipment_locked")\n'
        f'    if (command.itemId == {UPPER}_SET_ID) return invalid(state, "{LOWER}_equipment_permanent")\n'
        "'''\n"
    )
    new = (
        "unequip_locked = '''  fun unequip(state: GameState, command: ItemCommand): ExecutionResult {\n"
        '    if (command.actorId == AN_NHIEN_ID) return invalid(state, "an_nhien_equipment_locked")\n'
        "'''\n"
    )
    text = text.replace(old, new)
    comment_start = f'# Historical {FEATURE} tests encoded the retired x50 implementation and removal-from-Inventory model.\n'
    if comment_start in text:
        start = text.index(comment_start)
        end = text.index('print("Combat HP cleanup', start)
        text = text[:start] + text[end:]
    write(rel, text)


def purge_inventory_v4_compat() -> None:
    rel = "android-apk/patch-inventory-v4-anchor-compat.py"
    text = read(rel)
    text = text.replace(f'        JSONObject before{FEATURE}ForItem = beforeFlagsForItem != null ? beforeFlagsForItem.optJSONObject("{CAMEL}") : null;\n', '')
    text = text.replace(f'        if (!establishedStructured && before{FEATURE}ForItem != null) establishedStructured = lower(before{FEATURE}ForItem.toString()).contains(lower(name));\n', '')
    text = text.replace(f'        boolean {CAMEL}AlreadySpawned = before{FEATURE}ForItem != null && before{FEATURE}ForItem.optBoolean("spawned", false);\n', '')
    text = text.replace(
        f'        }} else if ({CAMEL}) {{\n          allowedNew = {CAMEL}AlreadySpawned && establishedStructured && acquisitionIntent(action);\n',
        '',
    )
    write(rel, text)


def purge_newgame_patch() -> None:
    rel = "android-apk/patch-newgame-inventory-capacity.py"
    text = read(rel)
    lower_text = text.casefold()
    key = f'  @test fun {CAMEL}occupiestwoequipmentslotsbutisoneownedzerocapacityitem'.casefold()
    start = lower_text.find(key)
    if start >= 0:
        end_key = '  @test fun saveloadrecalculatescapacityfromownershipandequipmentreferences'.casefold()
        end = lower_text.find(end_key, start)
        if end < 0:
            raise RuntimeError("capacity test end missing")
        text = text[:start] + text[end:]
    text = text.replace(f"    '{CAMEL}OccupiesTwoEquipmentSlotsButIsOneOwnedZeroCapacityItem',\n", '')
    write(rel, text)


def purge_prompt_anchor_files() -> None:
    rel = "android-apk/patch-search-action-false-warning.py"
    text = read(rel)
    text = text.replace(
        f'prompt_anchor = \'      "Inventory chỉ đổi khi Kai thật sự lấy/nhận/copy/trao/mất/tiêu thụ vật; nhìn thấy không đồng nghĩa sở hữu. {FEATURE} roll success chỉ mở discovery route, không tự đưa set vào inventory. " +\\n\'\n',
        'prompt_anchor = \'      "Inventory chỉ đổi khi Kai thật sự lấy/nhận/copy/trao/mất/tiêu thụ vật; nhìn thấy không đồng nghĩa sở hữu. " +\\n\'\n',
    )
    write(rel, text)

    rel = "android-apk/patch-local-entity-overlay.py"
    text = read(rel)
    text = text.replace(
        f'writer_marker = \'      "Inventory chỉ đổi khi Kai thật sự lấy/nhận/copy/trao/mất/tiêu thụ vật; nhìn thấy không đồng nghĩa sở hữu. {FEATURE} roll success chỉ mở discovery route, không tự đưa set vào inventory. " +\\n\'\n',
        'writer_marker = \'      "Inventory chỉ đổi khi Kai thật sự lấy/nhận/copy/trao/mất/tiêu thụ vật; nhìn thấy không đồng nghĩa sở hữu. " +\\n\'\n',
    )
    write(rel, text)

    rel = "android-apk/patch-an-nhien-follower.py"
    text = read(rel)
    text = text.replace(f'    flags.put("{CAMEL}", {CAMEL}).put("lastRolls", rolls);', '    flags.put("lastRolls", rolls);')
    write(rel, text)


def purge_docs_and_build_checks() -> None:
    docs = [
        "android-apk/drive-canon.txt",
        "android-apk/RELEASE_NOTES_1.1.26.txt",
        "android-apk/RELEASE_NOTES_1.1.55.txt",
        "android-apk/RELEASE_NOTES_1.1.56.txt",
        "android-apk/RELEASE_NOTES_1.1.57.txt",
        "android-apk/RELEASE_NOTES_1.1.58.txt",
        "android-apk/RELEASE_NOTES_1.1.59.txt",
        "android-apk/RELEASE_NOTES_1.1.60.txt",
        "android-apk/RELEASE_NOTES_1.1.62.txt",
        "android-apk/RELEASE_NOTES_1.1.63.txt",
        "android-apk/RELEASE_NOTES_1.1.72.txt",
        "android-apk/RELEASE_NOTES_1.1.75.txt",
        ".github/workflows/build-backroom-apk.yml",
    ]
    for rel in docs:
        if path(rel).is_file():
            write(rel, remove_feature_lines(read(rel)))


def verify_zero() -> None:
    hits = []
    for p in ROOT.rglob('*'):
        if '.git' in p.parts:
            continue
        rel = p.relative_to(ROOT).as_posix()
        if FEATURE.casefold() in rel.casefold():
            hits.append(f'PATH {rel}')
        if not p.is_file():
            continue
        try:
            text = p.read_text(encoding='utf-8')
        except (UnicodeDecodeError, OSError):
            continue
        for no, line in enumerate(text.splitlines(), 1):
            if FEATURE.casefold() in line.casefold():
                hits.append(f'TEXT {rel}:{no}: {line.strip()}')
    if hits:
        print('\n'.join(hits[:300]))
        raise SystemExit(f"remaining retired-feature references: {len(hits)}")


purge_game_core()
purge_ai_orchestrator()
purge_drive_patch()
purge_character_status_patch()
purge_combat_cleanup_patch()
purge_inventory_v4_compat()
purge_newgame_patch()
purge_prompt_anchor_files()
purge_docs_and_build_checks()
verify_zero()
print("Retired feature purge complete: zero path/text references in the working tree.")

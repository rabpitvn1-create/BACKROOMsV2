from pathlib import Path

ROOT = Path(__file__).resolve().parent
SOURCE = ROOT / "patch-an-nhien-follower.py"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"

# Keep the historical non-gameplay/core materialization and UI work for now, but retire section 7:
# its Java probability tables, transition gate, Party admission and deterministic state mutation are
# superseded by GameplayRollPolicy / AnNhienEncounterPolicy / PartyCandidatePolicy in Kotlin.
code = SOURCE.read_text(encoding="utf-8")
section_start_marker = "# 7) Final Android gameplay integration: deterministic Level 0 encounter, bonuses and exit gate.\n"
section_end_marker = "# 8) Existing per-character UI: show the actual two FOOD slots instead of generic 9-slot copy.\n"
if code.count(section_start_marker) != 1 or code.count(section_end_marker) != 1:
    raise RuntimeError("An Nhien legacy gameplay section anchors are not unique")
start = code.index(section_start_marker)
end = code.index(section_end_marker, start)
code = code[:start] + section_end_marker + code[end + len(section_end_marker):]
exec(compile(code, str(SOURCE), "exec"), {"__name__": "__main__", "__file__": str(SOURCE)})

main = MAIN.read_text(encoding="utf-8")
initial = '''      JSONObject candidateState = meta
        ? new JSONObject(before.toString())
        : applyModelOperations(before, generated.optJSONArray("ops"), action, rolls);
'''
initial_bridge = initial + '''      if (!meta) candidateState = new JSONObject(com.rabpit.backroom.core.AnNhienEncounterPolicy.apply(
        before.toString(), candidateState.toString(), rolls.toString()));
'''
if "AnNhienEncounterPolicy.apply(" not in main:
    if main.count(initial) != 1:
        raise RuntimeError(f"An Nhien initial Kotlin bridge anchor count != 1: {main.count(initial)}")
    main = main.replace(initial, initial_bridge, 1)

repair = '            candidateState = applyModelOperations(before, repaired.optJSONArray("ops"), action, rolls);\n'
repair_bridge = repair + '''            candidateState = new JSONObject(com.rabpit.backroom.core.AnNhienEncounterPolicy.apply(
              before.toString(), candidateState.toString(), rolls.toString()));
'''
if repair_bridge not in main:
    if main.count(repair) != 1:
        raise RuntimeError(f"An Nhien repair Kotlin bridge anchor count != 1: {main.count(repair)}")
    main = main.replace(repair, repair_bridge, 1)

prompt_marker = '"GAMEPLAY_ROLLS do Android sinh là bất biến: chỉ outcome success=true mới được xuất hiện. Không reroll, không tự đổi xác suất, không tự tạo encounter/item/reunion/level transition trái roll. " +\n'
prompt_extra = prompt_marker + '            "AN NHIÊN HARD LOCK: bé gái 7 tuổi, con người, không phải Entity. anNhienEncounter success=true là cuộc gặp bắt buộc ở Level 0 và phải được kể trong lượt đó; sau khi gặp cô bé luôn theo Kai, không chiến đấu, không dùng vũ khí, không tự tách nhóm. Cô chỉ có +10% loot chance và +2% exit chance khi đang theo Kai, đúng như GAMEPLAY_ROLLS. Không tự thêm năng lực hoặc lore. " +\n'
if "AN NHIÊN HARD LOCK:" not in main:
    if main.count(prompt_marker) != 1:
        raise RuntimeError(f"An Nhien prompt bridge anchor count != 1: {main.count(prompt_marker)}")
    main = main.replace(prompt_marker, prompt_extra, 1)

for forbidden in (
    'thresholdRoll("anNhienEncounter"',
    'lootThresholds[level] + (anNhienFollowing ? 1000 : 0)',
    'exitThreshold + 200',
    'currentLevel(before) == 0 && !anNhienEncountered(before)',
):
    if forbidden in main:
        raise RuntimeError("Retired Java An Nhien gameplay authority survived: " + forbidden)
for required in (
    "AnNhienEncounterPolicy.apply(",
    "AN NHIÊN HARD LOCK:",
):
    if required not in main:
        raise RuntimeError("Kotlin An Nhien bridge missing: " + required)

MAIN.write_text(main, encoding="utf-8")
print("An Nhiên gameplay authority bridged to Kotlin; historical patch retains only core materialization, prompt and UI compatibility.")

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

def method_bounds(source: str, signature: str) -> tuple[int, int]:
    start = source.find(signature)
    if start < 0:
        raise RuntimeError("An Nhien bridge method missing: " + signature)
    open_brace = source.find("{", start)
    depth = 0
    state = "code"
    escaped = False
    i = open_brace
    while i < len(source):
        ch = source[i]
        nxt = source[i + 1] if i + 1 < len(source) else ""
        if state == "string":
            if escaped:
                escaped = False
            elif ch == "\\":
                escaped = True
            elif ch == '"':
                state = "code"
        elif state == "char":
            if escaped:
                escaped = False
            elif ch == "\\":
                escaped = True
            elif ch == "'":
                state = "code"
        elif state == "line_comment":
            if ch == "\n":
                state = "code"
        elif state == "block_comment":
            if ch == "*" and nxt == "/":
                state = "code"
                i += 1
        else:
            if ch == '"':
                state = "string"
            elif ch == "'":
                state = "char"
            elif ch == "/" and nxt == "/":
                state = "line_comment"
                i += 1
            elif ch == "/" and nxt == "*":
                state = "block_comment"
                i += 1
            elif ch == "{":
                depth += 1
            elif ch == "}":
                depth -= 1
                if depth == 0:
                    return start, i + 1
        i += 1
    raise RuntimeError("An Nhien bridge method closing brace missing")

signature = "  private JSONObject applyModelOperations(JSONObject before, JSONArray ops, JSONObject rolls, String action) throws Exception"
method_start, method_end = method_bounds(main, signature)
method = main[method_start:method_end]
bridge_return = "return new JSONObject(com.rabpit.backroom.core.AnNhienEncounterPolicy.apply(before.toString(), state.toString(), rolls.toString()));"
if bridge_return not in method:
    count = method.count("return state;")
    if count < 1:
        raise RuntimeError("An Nhien applyModelOperations return anchor missing")
    method = method.replace("return state;", bridge_return)
    main = main[:method_start] + method + main[method_end:]

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

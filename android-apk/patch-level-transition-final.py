from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
main = MAIN.read_text(encoding="utf-8")

# Run after the historical PR3 patch stack. The final runtime is ops-based: the writer proposes
# set_location/set_level and Android validates the resulting candidate. Keep narration and structured
# state in one transaction instead of introducing another legacy state path.
lines = main.splitlines()
schema_indexes = [i for i, line in enumerate(lines) if "JSON bắt buộc:" in line]
if len(schema_indexes) != 1:
    raise RuntimeError(f"Level transition writer schema: expected 1 line, found {len(schema_indexes)}")
schema_index = schema_indexes[0]
instruction = (
    '      "LEVEL TRANSITION STATE LOCK: Khi phản hồi xác nhận đã hoàn tất sang Level khác, ops bắt buộc phải có '
    'set_level cho Level mới và set_location cho vị trí mới trong cùng lượt. Không được mô tả đã ở Level mới nhưng '
    'chỉ đổi văn xuôi hoặc chỉ đổi một trong hai state operation. Nếu mới chỉ thấy dấu hiệu chuyển vùng thì mô tả là '
    'chuyển tiếp, chưa khẳng định đã sang Level mới. " +'
)
if "LEVEL TRANSITION STATE LOCK" not in "\n".join(lines):
    lines.insert(schema_index, instruction)
main = "\n".join(lines) + ("\n" if main.endswith("\n") else "")

# The existing progression layer can infer a Level from location/title, but it recognized only English names.
# The GM normally writes Vietnamese, so a valid Level-1 location such as "bãi đỗ xe" could remain projected as
# Level 0 and the Snapshot renderer would keep the old scene. Extend only the authoritative location recognizer.
old_names = '    String[] names = {"the lobby", "parking zone", "pipe dreams", "the electrical station", "the abandoned office", "terror hotel", "lights out"};\n    for (int n = 0; n < names.length; n++) if (location.contains(names[n])) return n;'
new_names = '''    String[] names = {"the lobby", "parking zone", "pipe dreams", "the electrical station", "the abandoned office", "terror hotel", "lights out"};
    for (int n = 0; n < names.length; n++) if (location.contains(names[n])) return n;
    String normalizedLocation = location.replace('–', '-');
    if (containsAny(normalizedLocation, "bãi đỗ xe", "bãi đậu xe", "gara bê tông", "garage bê tông")) return 1;
    if (containsAny(normalizedLocation, "hầm đường ống", "đường ống", "pipe dreams")) return 2;
    if (containsAny(normalizedLocation, "trạm điện", "electrical station")) return 3;
    if (containsAny(normalizedLocation, "văn phòng bỏ hoang", "abandoned office")) return 4;
    if (containsAny(normalizedLocation, "khách sạn kinh hoàng", "terror hotel")) return 5;
    if (containsAny(normalizedLocation, "lights out", "vùng bóng tối")) return 6;'''
count = main.count(old_names)
if count != 1:
    raise RuntimeError(f"Level transition location recognizer: expected 1 anchor, found {count}")
main = main.replace(old_names, new_names, 1)

# Deterministic guard for the exact failure mode: a reply explicitly claims another Level while the validated
# candidate still points to the old Level. This becomes a hard audit issue, forcing the existing one-repair path;
# if repair still disagrees, the turn is rejected rather than persisting split-brain narrative/state.
helper_anchor = "  private JSONArray rejectedOperationIssuesAndroid(JSONObject before, JSONObject candidate, JSONObject generated) throws Exception {\n"
helper = r'''  private int explicitLevelClaimAndroid(String text) {
    if (text == null) return -1;
    String value = lower(text);
    java.util.regex.Matcher numeric = java.util.regex.Pattern.compile("level\\s*([0-6])", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(value);
    if (numeric.find()) return Integer.parseInt(numeric.group(1));
    if (containsAny(value, "level one", "level một", "parking zone")) return 1;
    if (containsAny(value, "level two", "level hai", "pipe dreams")) return 2;
    if (containsAny(value, "level three", "level ba", "the electrical station")) return 3;
    if (containsAny(value, "level four", "level bốn", "the abandoned office")) return 4;
    if (containsAny(value, "level five", "level năm", "terror hotel")) return 5;
    if (containsAny(value, "level six", "level sáu", "lights out")) return 6;
    if (containsAny(value, "level zero", "level không", "the lobby")) return 0;
    return -1;
  }

  private JSONArray levelNarrativeStateIssuesAndroid(JSONObject before, JSONObject candidate, JSONObject generated) throws Exception {
    JSONArray issues = new JSONArray();
    int claimed = explicitLevelClaimAndroid(generated.optString("reply", ""));
    if (claimed < 0) return issues;
    int actual = currentLevel(candidate);
    if (claimed != actual) {
      issues.put(new JSONObject()
        .put("rule", "level_narrative_state_mismatch")
        .put("severity", "hard")
        .put("claim", "Level " + claimed)
        .put("reason", "Reply claims a different Level than the Android-validated candidate. If the transition is valid, emit matching set_level and set_location ops; otherwise rewrite the reply so the old Level remains current."));
    }
    return issues;
  }

'''
if "levelNarrativeStateIssuesAndroid" not in main:
    count = main.count(helper_anchor)
    if count != 1:
        raise RuntimeError(f"Level transition audit helper anchor: expected 1, found {count}")
    main = main.replace(helper_anchor, helper + helper_anchor, 1)

initial_audit = "          if (!meta) appendIssues(hardIssues, rejectedOperationIssuesAndroid(before, candidateState, generated));"
initial_replacement = initial_audit + "\n          if (!meta) appendIssues(hardIssues, levelNarrativeStateIssuesAndroid(before, candidateState, generated));"
if "appendIssues(hardIssues, levelNarrativeStateIssuesAndroid(before, candidateState, generated));" not in main:
    count = main.count(initial_audit)
    if count != 1:
        raise RuntimeError(f"Level transition initial audit anchor: expected 1, found {count}")
    main = main.replace(initial_audit, initial_replacement, 1)

repair_audit = "            appendIssues(hardIssues, rejectedOperationIssuesAndroid(before, candidateState, generated));"
repair_replacement = repair_audit + "\n            appendIssues(hardIssues, levelNarrativeStateIssuesAndroid(before, candidateState, generated));"
if main.count("appendIssues(hardIssues, levelNarrativeStateIssuesAndroid(before, candidateState, generated));") < 2:
    count = main.count(repair_audit)
    if count != 1:
        raise RuntimeError(f"Level transition repair audit anchor: expected 1, found {count}")
    main = main.replace(repair_audit, repair_replacement, 1)

for marker in (
    "LEVEL TRANSITION STATE LOCK",
    "levelNarrativeStateIssuesAndroid",
    "level_narrative_state_mismatch",
    "bãi đỗ xe",
    "set_level",
    "set_location",
    "sceneKey:visualSceneKey()",
):
    if marker not in main:
        raise RuntimeError("Level transition regression marker missing: " + marker)

MAIN.write_text(main, encoding="utf-8")
print("Level transition final guard verified: Vietnamese location recognition, narrative/state audit and Snapshot scene key stay synchronized.")

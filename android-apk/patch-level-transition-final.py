from pathlib import Path
import runpy

ROOT = Path(__file__).resolve().parent
runpy.run_path(str(ROOT / "patch-ime-keyboard-state-final.py"), run_name="__main__")

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
    'chuyển tiếp, chưa khẳng định đã sang Level mới. EXIT PROBE RESOLUTION: khi ACTION TYPE = EXPLORE, '
    'GAMEPLAY_ROLLS.exitProbe.success=true và progression đã đủ minimumTurns, hành động Khám phá hiện tại phải '
    'hoàn tất chuyển vùng theo đúng canon bằng set_level + set_location trong cùng lượt; mô tả sự đổi môi trường '
    'liên tục, không teleport và không yêu cầu người chơi nhập lại một hành động thoát khác chỉ để tiêu thụ roll đã '
    'thành công. " +'
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

# Gameplay parity owns exitProbe eligibility and its production probability. This final patch only
# verifies that the typed-action contract survived later bonus/finalizer patches before synchronizing
# transition state and narrative.
for marker in (
    'boolean exitProbeEligible = exitProbeEligibleAndroid(exploreAction, exitIntent, physical, search);',
    'getIntent().getBooleanExtra("emuLevel1Progression", false)',
    'thresholdRoll("exitProbe", 10000, exitThreshold, exitProbeEligible',
    'rolls.put("actionKind", actionKindNormalized);',
):
    if marker not in main:
        raise RuntimeError("Upstream exitProbe contract missing: " + marker)

# Drive canon allows a transition when exitProbe succeeds OR the state has already locked
# transitionReady/exitReady. The older reducer required a second confirmedExit/random roll even for a locked
# ready state, which could strand a valid transition indefinitely.
old_transition = '''    boolean exitFound = (confirmedExit != null && !confirmedExit.trim().isEmpty()) || rollSuccess(rolls, "levelExit");
    return exitFound && progressionReady(before);
'''
new_transition = '''    boolean lockedReady = exploration != null &&
      (exploration.optBoolean("transitionReady", false) || exploration.optBoolean("exitReady", false));
    boolean exitFound = lockedReady ||
      (confirmedExit != null && !confirmedExit.trim().isEmpty()) ||
      rollSuccess(rolls, "levelExit");
    return exitFound && progressionReady(before);
'''
if "boolean lockedReady = exploration != null" not in main:
    count = main.count(old_transition)
    if count != 1:
        raise RuntimeError(f"Locked-ready transition semantics: expected 1 anchor, found {count}")
    main = main.replace(old_transition, new_transition, 1)

# Deterministic guard for progression outcomes and narrative/state split-brain. A successful typed EXPLORE
# exitProbe after the six-turn gate is not optional flavor: it is the authoritative transition outcome for
# that macro action. If the writer omits the level/location ops, force the existing one-repair path.
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

  private JSONArray levelNarrativeStateIssuesAndroid(JSONObject before, JSONObject candidate, JSONObject generated, JSONObject rolls) throws Exception {
    JSONArray issues = new JSONArray();
    int beforeLevel = currentLevel(before);
    int actual = currentLevel(candidate);
    boolean typedExplore = "EXPLORE".equalsIgnoreCase(rolls.optString("actionKind", ""));
    boolean successfulExitProbe = rollSuccess(rolls, "exitProbe") || rollSuccess(rolls, "levelExit");
    if (typedExplore && successfulExitProbe && progressionReady(before)) {
      if (actual == beforeLevel) {
        issues.put(new JSONObject()
          .put("rule", "exit_probe_transition_omitted")
          .put("severity", "hard")
          .put("claim", "successful EXPLORE exitProbe")
          .put("reason", "Android locked a successful typed EXPLORE exitProbe after the progression gate. Resolve that macro action by emitting matching set_level and set_location ops for the canon-valid destination; do not discard or reroll the successful outcome."));
      } else if (candidate.optString("location", "").trim().equals(before.optString("location", "").trim())) {
        issues.put(new JSONObject()
          .put("rule", "level_transition_location_omitted")
          .put("severity", "hard")
          .put("claim", "set_level without new location")
          .put("reason", "A successful Level transition must update both authoritative Level and location in the same turn."));
      }
    }

    int claimed = explicitLevelClaimAndroid(generated.optString("reply", ""));
    if (claimed >= 0 && claimed != actual) {
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
initial_replacement = initial_audit + "\n          if (!meta) appendIssues(hardIssues, levelNarrativeStateIssuesAndroid(before, candidateState, generated, rolls));"
if "appendIssues(hardIssues, levelNarrativeStateIssuesAndroid(before, candidateState, generated, rolls));" not in main:
    count = main.count(initial_audit)
    if count != 1:
        raise RuntimeError(f"Level transition initial audit anchor: expected 1, found {count}")
    main = main.replace(initial_audit, initial_replacement, 1)

repair_audit = "            appendIssues(hardIssues, rejectedOperationIssuesAndroid(before, candidateState, generated));"
repair_replacement = repair_audit + "\n            appendIssues(hardIssues, levelNarrativeStateIssuesAndroid(before, candidateState, generated, rolls));"
if main.count("appendIssues(hardIssues, levelNarrativeStateIssuesAndroid(before, candidateState, generated, rolls));") < 2:
    count = main.count(repair_audit)
    if count != 1:
        raise RuntimeError(f"Level transition repair audit anchor: expected 1, found {count}")
    main = main.replace(repair_audit, repair_replacement, 1)

for marker in (
    "LEVEL TRANSITION STATE LOCK",
    "EXIT PROBE RESOLUTION",
    "levelNarrativeStateIssuesAndroid",
    "exit_probe_transition_omitted",
    "level_transition_location_omitted",
    "level_narrative_state_mismatch",
    "bãi đỗ xe",
    "set_level",
    "set_location",
    "sceneKey:visualSceneKey()",
    "boolean lockedReady = exploration != null",
    'rolls.optString("actionKind", "")',
):
    if marker not in main:
        raise RuntimeError("Level transition regression marker missing: " + marker)

MAIN.write_text(main, encoding="utf-8")
print("Level transition final guard verified: typed EXPLORE exit probes, mandatory successful-probe resolution, locked-ready canon, Vietnamese location recognition and narrative/state synchronization.")

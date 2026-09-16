from pathlib import Path
import re


ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"


def method_bounds(source: str, method_name: str) -> tuple[int, int]:
    signature = re.search(
        rf"(?m)^\s*private\s+[^\n]+\s+{re.escape(method_name)}\s*\(",
        source,
    )
    if not signature:
        raise RuntimeError(f"Method not found: {method_name}")

    open_brace = source.find("{", signature.start())
    if open_brace < 0:
        raise RuntimeError(f"Opening brace not found for method: {method_name}")

    depth = 0
    i = open_brace
    state = "code"
    escaped = False
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
                    return signature.start(), i + 1
        i += 1
    raise RuntimeError(f"Closing brace not found for method: {method_name}")


def block_bounds(source: str, anchor: str) -> tuple[int, int]:
    start = source.find(anchor)
    if start < 0:
        raise RuntimeError("block not found: " + anchor)
    open_brace = source.find("{", start)
    if open_brace < 0:
        raise RuntimeError("opening brace missing: " + anchor)
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
    raise RuntimeError("closing brace missing: " + anchor)


def remove_method(source: str, signature: str) -> str:
    if signature not in source:
        return source
    start, end = block_bounds(source, signature)
    while end < len(source) and source[end] in " \t":
        end += 1
    if end < len(source) and source[end] == "\n":
        end += 1
    return source[:start] + source[end:]


def insert_before_once(source: str, anchor: str, insertion: str, label: str) -> str:
    if insertion.strip() in source:
        return source
    count = source.count(anchor)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return source.replace(anchor, insertion + anchor, 1)


text = MAIN.read_text(encoding="utf-8")

# The model no longer transports story state. It receives one engine-owned directive and may
# narrate that event, while StoryProgressionPolicy alone owns storyArc, storyContinuity,
# luciaEncounter, and Lucia's Party milestone.
if "STORY ENGINE AUTHORITY:" not in text:
    start, end = method_bounds(text, "writerPrompt")
    method = text[start:end]
    return_match = re.search(r"(?m)^([ \t]*)return\s+", method)
    if not return_match:
        raise RuntimeError("writerPrompt return expression not found for story authority")
    indent = return_match.group(1)
    continuation = indent + "  "
    prefix = (
        '"STORY ENGINE AUTHORITY: storyArc, storyContinuity, luciaEncounter và Lucia Party milestone chỉ do Android StoryProgressionPolicy quyết định. '
        'Không dùng flag_patch hoặc party_upsert để điều khiển các state này và không phát operation lucia_story. Hãy kể đúng directive hiện tại, không tự nhảy sang beat sau. " +\n'
        + continuation
        + '"STORY ENGINE DIRECTIVE: " + com.rabpit.backroom.core.StoryProgressionPolicy.directive(before, action) + "\\n\\n" +\n'
        + continuation
    )
    method = method[: return_match.end()] + prefix + method[return_match.end():]
    text = text[:start] + method + text[end:]

# Auditors see the exact same deterministic story directive as the writer. They must not flag a
# narration merely because the authoritative story transition is absent from the state that began
# the turn; the preview candidate is normalized before risk/local canon checks below.
if "AUTHORITATIVE STORY DIRECTIVE:" not in text:
    start, end = method_bounds(text, "runAudit")
    method = text[start:end]
    prompt_match = re.search(r"(?m)^([ \t]*)String prompt = ", method)
    if not prompt_match:
        raise RuntimeError("runAudit prompt assignment not found for story directive")
    continuation = prompt_match.group(1) + "  "
    prefix = (
        '"AUTHORITATIVE STORY DIRECTIVE: " + com.rabpit.backroom.core.StoryProgressionPolicy.directive(before, action) + "\\n" +\n'
        + continuation
        + '"Story state produced by that directive is engine-owned. Do not report it as a model canon conflict when narration matches the directive.\\n\\n" +\n'
        + continuation
    )
    method = method[: prompt_match.end()] + prefix + method[prompt_match.end():]
    text = text[:start] + method + text[end:]

first_risk = "          int risk = meta ? 0 : validatedTurnRisk(before, candidateState, generated);\n"
first_normalize = (
    "          if (!meta) candidateState = com.rabpit.backroom.core.StoryProgressionPolicy.normalizeCandidate("
    "before, candidateState, action);\n"
)
text = insert_before_once(text, first_risk, first_normalize, "initial story preview")

repair_risk = "            risk = validatedTurnRisk(before, candidateState, generated);\n"
repair_normalize = (
    "            candidateState = com.rabpit.backroom.core.StoryProgressionPolicy.normalizeCandidate("
    "before, candidateState, action);\n"
)
text = insert_before_once(text, repair_risk, repair_normalize, "repair story preview")

for forbidden in (
    'type.equals("lucia_story")',
    "applyLuciaStoryOperationAndroid",
    "luciaFirstContactLockedAndroid",
    "luciaPartyLockedAndroid",
    "luciaJoinConfirmedAndroid",
    "premature_lucia_first_contact",
    "premature_lucia_party",
    "LUCIA STATE TRANSPORT:",
):
    if forbidden in text:
        raise RuntimeError("Retired duplicate Lucia story authority survived: " + forbidden)

for required in (
    "STORY ENGINE AUTHORITY:",
    "STORY ENGINE DIRECTIVE:",
    "AUTHORITATIVE STORY DIRECTIVE:",
    "StoryProgressionPolicy.directive(before, action)",
    "StoryProgressionPolicy.normalizeCandidate(before, candidateState, action)",
):
    if required not in text:
        raise RuntimeError("Engine-owned story integration marker missing: " + required)

# Historical runtime layers may reconstruct the provider-operation reducer. Normalize that final
# Java preview back to bridge-only calls. These calls do not own gameplay rules: the checked-in
# GameCoreFacade independently rechecks Party, Player and Flag deltas before commit.
legacy_party_add = "else if (characterAddAllowed(before, name, rolls)) party.put(new JSONObject(member.toString()));"
kotlin_party_add = (
    "else if (com.rabpit.backroom.core.PartyCandidatePolicy.allowsProviderAddition("
    "before.toString(), rolls.toString(), name)) party.put(new JSONObject(member.toString()));"
)
if kotlin_party_add not in text:
    if text.count(legacy_party_add) != 1:
        raise RuntimeError(f"Party add bridge anchor count != 1: {text.count(legacy_party_add)}")
    text = text.replace(legacy_party_add, kotlin_party_add, 1)

legacy_party_remove = (
    'if (party != null && existing >= 0 && containsAny(action, "rời", "tách", "ở lại", "đuổi", '
    '"chia nhóm", "mất dấu")) party.remove(existing);'
)
kotlin_party_remove = (
    "if (party != null && existing >= 0 && "
    "com.rabpit.backroom.core.PartyCandidatePolicy.allowsRemoval(action)) party.remove(existing);"
)
if kotlin_party_remove not in text:
    if text.count(legacy_party_remove) != 1:
        raise RuntimeError(f"Party remove bridge anchor count != 1: {text.count(legacy_party_remove)}")
    text = text.replace(legacy_party_remove, kotlin_party_remove, 1)
text = remove_method(text, "  private boolean characterAddAllowed(JSONObject before, String name, JSONObject rolls)")

kotlin_player = r'''        JSONObject current = state.optJSONObject("player");
        if (current == null) current = new JSONObject();
        JSONArray ownedGear = state.optJSONArray("inventory");
        current = new JSONObject(com.rabpit.backroom.core.PlayerCandidatePolicy.applyPatch(
          before.toString(), current.toString(), patch.toString(), rolls.toString(), action,
          ownedGear == null ? "[]" : ownedGear.toString()));
'''
if kotlin_player not in text:
    legacy_player = r'''        JSONObject current = state.optJSONObject("player");
        if (current == null) current = new JSONObject();
        boolean worldConsequence = rollSuccess(rolls, "hazard") || rollSuccess(rolls, "entityEncounter");
        boolean recoveryIntent = containsAny(action, "ăn", "uống", "nghỉ", "ngủ", "băng bó", "chữa", "hồi phục", "eat", "drink", "rest", "sleep", "heal");
        boolean gearIntent = containsAny(action, "rút", "cất", "trang bị", "mặc", "cởi", "tháo", "đeo", "draw", "equip", "unequip", "wear");
        if (patch.has("hp") && current.has("hp") && !current.isNull("hp")) {
          double beforeHp = current.optDouble("hp", Double.NaN);
          double afterHp = patch.optDouble("hp", Double.NaN);
          if (!Double.isNaN(beforeHp) && !Double.isNaN(afterHp) && afterHp >= 0 &&
              ((afterHp < beforeHp && worldConsequence) || (afterHp >= beforeHp && recoveryIntent))) current.put("hp", afterHp);
        }
        if (patch.has("condition") && (worldConsequence || recoveryIntent)) current.put("condition", patch.optString("condition", current.optString("condition", "")));
        if (patch.optJSONObject("needs") != null && recoveryIntent) {
          JSONObject needs = current.optJSONObject("needs");
          if (needs == null) needs = new JSONObject();
          for (String needKey : new String[] {"thirst", "hunger", "fatigue", "sleepDeprivation"}) {
            if (patch.optJSONObject("needs").has(needKey)) needs.put(needKey, patch.optJSONObject("needs").get(needKey));
          }
          current.put("needs", needs);
        }
        JSONArray ownedGear = state.optJSONArray("inventory");
        for (String key : new String[] {"weapon", "armor"}) {
          if (!patch.has(key) || !gearIntent) continue;
          String proposedGear = patch.optString(key, "").trim();
          boolean owned = false;
          if (ownedGear != null) for (int gearIndex = 0; gearIndex < ownedGear.length(); gearIndex++) {
            String ownedName = itemName(ownedGear.opt(gearIndex));
            if (!ownedName.isEmpty() && lower(proposedGear).contains(lower(ownedName))) { owned = true; break; }
          }
          if (owned) current.put(key, proposedGear);
        }
'''
    base_player = r'''        JSONObject current = state.optJSONObject("player");
        if (current == null) current = new JSONObject();
        for (String key : new String[] {"hp", "condition", "weapon", "armor"}) if (patch.has(key)) current.put(key, patch.get(key));
        if (patch.optJSONObject("needs") != null) {
          JSONObject needs = current.optJSONObject("needs");
          if (needs == null) needs = new JSONObject();
          mergeObject(needs, patch.optJSONObject("needs"));
          current.put("needs", needs);
        }
'''
    if legacy_player in text:
        text = text.replace(legacy_player, kotlin_player, 1)
    elif base_player in text:
        text = text.replace(base_player, kotlin_player, 1)
    else:
        raise RuntimeError("Player candidate bridge anchor missing")

flag_anchor = '      if (type.equals("flag_patch")) {'
flag_start, flag_end = block_bounds(text, flag_anchor)
kotlin_flag_block = '''      if (type.equals("flag_patch")) {
        JSONObject flags = state.optJSONObject("flags");
        if (flags == null) flags = new JSONObject();
        flags = new JSONObject(com.rabpit.backroom.core.FlagCandidatePolicy.applyOperation(
          before.toString(), flags.toString(), op.toString(), rolls.toString()));
        state.put("flags", flags);
      }'''
text = text[:flag_start] + kotlin_flag_block + text[flag_end:]
text = remove_method(text, "  private boolean flagRootAllowed(JSONObject before, String root, JSONObject rolls)")

for marker in (
    "PartyCandidatePolicy.allowsProviderAddition(before.toString(), rolls.toString(), name)",
    "PartyCandidatePolicy.allowsRemoval(action)",
    "PlayerCandidatePolicy.applyPatch(",
    "FlagCandidatePolicy.applyOperation(",
):
    if marker not in text:
        raise RuntimeError("Kotlin candidate preview bridge missing: " + marker)
for retired in (
    "characterAddAllowed(",
    "flagRootAllowed(",
    "boolean worldConsequence = rollSuccess(rolls",
    "boolean recoveryIntent = containsAny(action",
    "boolean gearIntent = containsAny(action",
    'root.equals("exploration") && value instanceof JSONObject',
    'root.equals("reunionPath") && value instanceof JSONObject',
):
    if retired in text:
        raise RuntimeError("Retired Java candidate authority survived final bridge: " + retired)

MAIN.write_text(text, encoding="utf-8")
print("Story and candidate bridge finalization applied: Kotlin remains the single Party, Player and Flag gameplay authority.")

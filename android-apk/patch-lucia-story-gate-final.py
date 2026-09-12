from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
text = MAIN.read_text(encoding="utf-8")


def method_bounds(source: str, method_name: str) -> tuple[int, int]:
    signature = re.search(
        rf"(?m)^\s*private\s+String\s+{re.escape(method_name)}\s*\(",
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


# Fresh New Game starts with Kai alone at STORY.PROLOGUE.ENTRY_COMPLETE. The first
# gameplay turn may explore Level 0 and advance to STORY.LEVEL0.ARRIVAL, but Lucia's
# first contact belongs to a later turn. Meeting Lucia and joining Party are separate gates.
if "LUCIA ENCOUNTER STORY GATE:" not in text:
    start, end = method_bounds(text, "writerPrompt")
    method = text[start:end]
    return_match = re.search(r"(?m)^([ \t]*)return\s+", method)
    if not return_match:
        raise RuntimeError("writerPrompt return expression not found for Lucia story gate")
    indent = return_match.group(1)
    continuation = indent + "  "
    prefix = (
        '"LUCIA ENCOUNTER STORY GATE: Sequence bắt buộc và machine-verifiable là STORY.LEVEL0.ARRIVAL -> STORY.LEVEL0.FIRST_CONTACT_COMPLETE -> STORY.LEVEL0.LUCIA_DECISION_COMPLETE kèm luciaEncounter.status=joined, partyEligible=true, joinPending=false -> Party có Lucia. Không được rút gọn các bước này thành một lượt. " +\n'
        + continuation
        + '"Nếu state ở ĐẦU lượt chưa hoàn tất STORY.LEVEL0.ARRIVAL, Kai đang một mình ở Level 0: Lucia / Hứa Thuý Mai chưa được gặp, chưa được nghe, chưa được nhận diện qua dấu vết và không được thêm vào Party. Lượt này có thể hoàn tất ARRIVAL nếu hành động đủ căn cứ, nhưng first-contact phải chờ một lượt sau. " +\n'
        + continuation
        + '"Nếu state ở ĐẦU lượt đã có ARRIVAL nhưng chưa có FIRST_CONTACT_COMPLETE, lượt này có thể hoàn tất first-contact bằng luciaEncounter.status=met tại Level 0. Trong CHÍNH lượt first-contact phải giữ Lucia ngoài party, không được phát STORY.LEVEL0.LUCIA_DECISION_COMPLETE và không được coi partyEligible là đã join. " +\n'
        + continuation
        + '"Lucia chỉ được vào Party ở một lượt SAU khi state đầu lượt đã có FIRST_CONTACT_COMPLETE và lượt decision xác nhận STORY.LEVEL0.LUCIA_DECISION_COMPLETE cùng status=joined, partyEligible=true, joinPending=false. Gặp Lucia không đồng nghĩa Lucia đã join. Không dùng Character Codex hay knowledge hậu trường để spawn hoặc join cô sớm. " +\n'
        + continuation
    )
    method = method[: return_match.end()] + prefix + method[return_match.end():]
    text = text[:start] + method + text[end:]


helper_anchor = "  private JSONArray rejectedOperationIssuesAndroid(JSONObject before, JSONObject candidate, JSONObject generated) throws Exception {\n"
helper = r'''  private boolean storyArcCompletedAndroid(JSONObject state, String beat) {
    if (state == null || beat == null || beat.isEmpty()) return false;
    JSONObject flags = state.optJSONObject("flags");
    JSONObject storyArc = flags == null ? null : flags.optJSONObject("storyArc");
    JSONArray completed = storyArc == null ? null : storyArc.optJSONArray("completed");
    if (completed == null) return false;
    for (int i = 0; i < completed.length(); i++) {
      if (beat.equals(completed.optString(i, ""))) return true;
    }
    return false;
  }

  private boolean luciaFirstContactLockedAndroid(JSONObject state) {
    return state == null || !storyArcCompletedAndroid(state, "STORY.LEVEL0.ARRIVAL");
  }

  private boolean luciaJoinConfirmedAndroid(JSONObject state) {
    if (state == null ||
        !storyArcCompletedAndroid(state, "STORY.LEVEL0.FIRST_CONTACT_COMPLETE") ||
        !storyArcCompletedAndroid(state, "STORY.LEVEL0.LUCIA_DECISION_COMPLETE")) return false;
    JSONObject flags = state.optJSONObject("flags");
    JSONObject encounter = flags == null ? null : flags.optJSONObject("luciaEncounter");
    if (encounter == null) return false;
    String status = lower(encounter.optString("status", ""));
    return "joined".equals(status) && encounter.optBoolean("partyEligible", false) &&
      !encounter.optBoolean("joinPending", true);
  }

  private boolean luciaPartyLockedAndroid(JSONObject before, JSONObject candidate) {
    // FIRST_CONTACT must already have existed when the turn began. This keeps the
    // first-contact turn itself from also becoming the Party-join turn.
    if (before == null || !storyArcCompletedAndroid(before, "STORY.LEVEL0.FIRST_CONTACT_COMPLETE")) return true;
    return !luciaJoinConfirmedAndroid(before) && !luciaJoinConfirmedAndroid(candidate);
  }

  private boolean partyContainsLuciaAndroid(JSONObject state) {
    if (state == null) return false;
    JSONArray party = state.optJSONArray("party");
    if (party == null) return false;
    for (int i = 0; i < party.length(); i++) {
      Object raw = party.opt(i);
      if (raw instanceof JSONObject) {
        JSONObject member = (JSONObject) raw;
        String id = lower(member.optString("id", ""));
        String name = lower(member.optString("name", ""));
        if ("lucia".equals(id) || containsAny(name, "lucia", "hứa thuý mai", "hứa thúy mai")) return true;
      } else if (raw != null && containsAny(lower(String.valueOf(raw)), "lucia", "hứa thuý mai", "hứa thúy mai")) {
        return true;
      }
    }
    return false;
  }

  private boolean luciaEncounterStateAndroid(JSONObject state) {
    if (state == null) return false;
    JSONObject flags = state.optJSONObject("flags");
    JSONObject encounter = flags == null ? null : flags.optJSONObject("luciaEncounter");
    if (encounter != null) {
      String status = lower(encounter.optString("status", ""));
      if (containsAny(status, "met", "contact", "joined") || encounter.optBoolean("partyEligible", false)) return true;
    }
    JSONObject storyArc = flags == null ? null : flags.optJSONObject("storyArc");
    String currentBeat = storyArc == null ? "" : storyArc.optString("currentBeat", "");
    return currentBeat.contains("FIRST_CONTACT") || currentBeat.contains("LUCIA_DECISION") ||
      storyArcCompletedAndroid(state, "STORY.LEVEL0.FIRST_CONTACT_COMPLETE");
  }

  private JSONArray prematureLuciaEncounterIssuesAndroid(JSONObject before, JSONObject candidate, JSONObject generated) throws Exception {
    JSONArray issues = new JSONArray();
    String reply = lower(generated == null ? "" : generated.optString("reply", ""));
    boolean replyMentionsLucia = containsAny(reply, "lucia", "hứa thuý mai", "hứa thúy mai");
    boolean candidateIntroducesFirstContact = luciaEncounterStateAndroid(candidate) || luciaEncounterStateAndroid(generated);

    if (luciaFirstContactLockedAndroid(before) && (replyMentionsLucia || candidateIntroducesFirstContact)) {
      issues.put(new JSONObject()
        .put("rule", "premature_lucia_first_contact")
        .put("severity", "hard")
        .put("claim", "Lucia first contact before Level 0 Arrival was already complete at turn start")
        .put("reason", "Lucia first contact is locked until a later turn whose starting state already contains STORY.LEVEL0.ARRIVAL. The turn that completes Arrival remains solo Level 0 exploration."));
    }

    boolean candidatePartyEarly = partyContainsLuciaAndroid(candidate) && luciaPartyLockedAndroid(before, candidate);
    boolean generatedPartyEarly = partyContainsLuciaAndroid(generated) && luciaPartyLockedAndroid(before, generated);
    if (candidatePartyEarly || generatedPartyEarly) {
      issues.put(new JSONObject()
        .put("rule", "premature_lucia_party")
        .put("severity", "hard")
        .put("claim", "Lucia entered Party before the separate decision/join gate")
        .put("reason", "Meeting Lucia is not Party membership. FIRST_CONTACT_COMPLETE must already exist at turn start, then a later decision must complete STORY.LEVEL0.LUCIA_DECISION_COMPLETE with luciaEncounter.status=joined, partyEligible=true, and joinPending=false before Party may contain Lucia."));
    }
    return issues;
  }

'''
if "private JSONArray prematureLuciaEncounterIssuesAndroid(" not in text:
    if text.count(helper_anchor) != 1:
        raise RuntimeError(
            "Lucia story gate requires exactly one rejectedOperationIssuesAndroid anchor, found "
            + str(text.count(helper_anchor))
        )
    text = text.replace(helper_anchor, helper + helper_anchor, 1)

initial_anchor = "          if (!meta) appendIssues(hardIssues, retiredOrganizationIssuesAndroid(generated));"
if initial_anchor not in text:
    initial_anchor = "          if (!meta) appendIssues(hardIssues, rejectedOperationIssuesAndroid(before, candidateState, generated));"
initial_guard = "          if (!meta) appendIssues(hardIssues, prematureLuciaEncounterIssuesAndroid(before, candidateState, generated));"
if initial_guard not in text:
    if text.count(initial_anchor) != 1:
        raise RuntimeError("Lucia initial audit anchor expected once, found " + str(text.count(initial_anchor)))
    text = text.replace(initial_anchor, initial_anchor + "\n" + initial_guard, 1)

repair_anchor = "            appendIssues(hardIssues, retiredOrganizationIssuesAndroid(generated));"
if repair_anchor not in text:
    repair_anchor = "            appendIssues(hardIssues, rejectedOperationIssuesAndroid(before, candidateState, generated));"
repair_guard = "            appendIssues(hardIssues, prematureLuciaEncounterIssuesAndroid(before, candidateState, generated));"
if repair_guard not in text:
    if text.count(repair_anchor) != 1:
        raise RuntimeError("Lucia repair audit anchor expected once, found " + str(text.count(repair_anchor)))
    text = text.replace(repair_anchor, repair_anchor + "\n" + repair_guard, 1)

for marker in (
    "LUCIA ENCOUNTER STORY GATE:",
    "luciaFirstContactLockedAndroid",
    "luciaPartyLockedAndroid",
    "luciaJoinConfirmedAndroid",
    "STORY.LEVEL0.LUCIA_DECISION_COMPLETE",
    "premature_lucia_first_contact",
    "premature_lucia_party",
    "appendIssues(hardIssues, prematureLuciaEncounterIssuesAndroid(before, candidateState, generated))",
):
    if marker not in text:
        raise RuntimeError("Lucia story-gate regression marker missing: " + marker)

MAIN.write_text(text, encoding="utf-8")
print("Lucia story gate applied: Arrival, first contact, and Party join are separate machine-verifiable stages.")

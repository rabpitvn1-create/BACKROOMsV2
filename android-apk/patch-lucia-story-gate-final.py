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
# first contact belongs to a later turn. This prevents Codex knowledge from being used
# as permission to materialize a story-owned character during the Prologue.
if "LUCIA ENCOUNTER STORY GATE:" not in text:
    start, end = method_bounds(text, "writerPrompt")
    method = text[start:end]
    return_match = re.search(r"(?m)^([ \t]*)return\s+", method)
    if not return_match:
        raise RuntimeError("writerPrompt return expression not found for Lucia story gate")
    indent = return_match.group(1)
    continuation = indent + "  "
    prefix = (
        '"LUCIA ENCOUNTER STORY GATE: Nếu state ở đầu lượt còn có storyArc.currentBeat=STORY.PROLOGUE.ENTRY_COMPLETE và chưa hoàn tất STORY.LEVEL0.ARRIVAL, Kai đang một mình khám phá Level 0. Lucia / Hứa Thuý Mai chưa được gặp, chưa được nghe, chưa được nhận diện qua dấu vết và không được thêm vào Party. " +\n'
        + continuation
        + '"Lượt này có thể cho Kai khám phá đủ để tiến storyArc tới STORY.LEVEL0.ARRIVAL nếu hành động thật sự tạo căn cứ, nhưng tuyệt đối không gặp Lucia trong chính lượt dùng để hoàn tất mốc đó. First contact chỉ được phép ở một lượt SAU khi state đầu lượt đã rời STORY.PROLOGUE.ENTRY_COMPLETE hoặc đã có STORY.LEVEL0.ARRIVAL trong completed. Lucia gặp ở Level 0 không đồng nghĩa tự động gia nhập Party. Không dùng Character Codex hay knowledge hậu trường để spawn cô sớm. " +\n'
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

  private boolean luciaEncounterLockedAndroid(JSONObject state) {
    if (state == null || storyArcCompletedAndroid(state, "STORY.LEVEL0.ARRIVAL")) return false;
    JSONObject flags = state.optJSONObject("flags");
    JSONObject storyArc = flags == null ? null : flags.optJSONObject("storyArc");
    String currentBeat = storyArc == null ? "" : storyArc.optString("currentBeat", "");
    return "STORY.PROLOGUE.ENTRY_COMPLETE".equals(currentBeat);
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
    if (!luciaEncounterLockedAndroid(before)) return issues;

    String reply = lower(generated == null ? "" : generated.optString("reply", ""));
    boolean replyMentionsLucia = containsAny(reply, "lucia", "hứa thuý mai", "hứa thúy mai");
    boolean candidateIntroducesLucia = partyContainsLuciaAndroid(candidate) || partyContainsLuciaAndroid(generated) ||
      luciaEncounterStateAndroid(candidate) || luciaEncounterStateAndroid(generated);
    if (!replyMentionsLucia && !candidateIntroducesLucia) return issues;

    issues.put(new JSONObject()
      .put("rule", "premature_lucia_encounter")
      .put("severity", "hard")
      .put("claim", "Lucia first contact before Level 0 exploration is complete")
      .put("reason", "Kai is still at STORY.PROLOGUE.ENTRY_COMPLETE in the state that began this turn. Rewrite this turn as solo Level 0 exploration only. The turn may advance to STORY.LEVEL0.ARRIVAL when the player's exploration supports it, but Lucia/Hứa Thuý Mai cannot appear, be identified, leave identifiable traces, or join Party until a later turn whose starting state has already cleared that arrival gate."));
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
    "luciaEncounterLockedAndroid",
    "prematureLuciaEncounterIssuesAndroid",
    "premature_lucia_encounter",
    "appendIssues(hardIssues, prematureLuciaEncounterIssuesAndroid(before, candidateState, generated))",
):
    if marker not in text:
        raise RuntimeError("Lucia story-gate regression marker missing: " + marker)

MAIN.write_text(text, encoding="utf-8")
print("Lucia encounter gate applied: Prologue remains solo; first contact requires a later turn after Level 0 arrival/exploration.")

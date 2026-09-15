from pathlib import Path
import re
import runpy


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

# Preview the exact pure StoryProgressionPolicy result before risk scoring/auditing. The normalized
# candidate is the one handed to GameCore for commit. The terminal safe fallback independently
# reconstructs the same deterministic baseline from before+action if model output is discarded.
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

# Retire the former Java-side story state machine completely. A future patch reintroducing any of
# these markers would recreate duplicate authority and must fail the build rather than silently win.
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

MAIN.write_text(text, encoding="utf-8")
print("Lucia story gate consolidated: StoryProgressionPolicy is the single story-state authority before audit and commit.")

party_authority = ROOT / "patch-party-authority-final.py"
if not party_authority.is_file():
    raise RuntimeError("Party authority finalizer missing: " + party_authority.name)
runpy.run_path(str(party_authority), run_name="__main__")

from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
FACADE = ROOT / "app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt"


def block_bounds(source: str, anchor: str) -> tuple[int, int]:
    start = source.find(anchor)
    if start < 0:
        raise RuntimeError("block not found: " + anchor)
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
    raise RuntimeError("closing brace missing: " + anchor)


def method_bounds(source: str, signature: str) -> tuple[int, int]:
    return block_bounds(source, signature)


# Java becomes bridge-only for flag_patch. All root and value eligibility is Kotlin-owned.
java = MAIN.read_text(encoding="utf-8")
flag_anchor = '      if (type.equals("flag_patch")) {'
start, end = block_bounds(java, flag_anchor)
kotlin_block = '''      if (type.equals("flag_patch")) {
        JSONObject flags = state.optJSONObject("flags");
        if (flags == null) flags = new JSONObject();
        flags = new JSONObject(com.rabpit.backroom.core.FlagCandidatePolicy.applyOperation(
          before.toString(), flags.toString(), op.toString(), rolls.toString()));
        state.put("flags", flags);
      }'''
java = java[:start] + kotlin_block + java[end:]

signature = "  private boolean flagRootAllowed(JSONObject before, String root, JSONObject rolls)"
if signature in java:
    method_start, method_end = method_bounds(java, signature)
    while method_end < len(java) and java[method_end] in " \t":
        method_end += 1
    if method_end < len(java) and java[method_end] == "\n":
        method_end += 1
    java = java[:method_start] + java[method_end:]

for marker in (
    "FlagCandidatePolicy.applyOperation(",
    "before.toString(), flags.toString(), op.toString(), rolls.toString()",
):
    if marker not in java:
        raise RuntimeError("Kotlin flag bridge missing: " + marker)
for retired in (
    "flagRootAllowed(",
    'root.equals("exploration") && value instanceof JSONObject',
    'root.equals("reunionPath") && value instanceof JSONObject',
):
    if retired in java:
        raise RuntimeError("Retired Java flag authority survived: " + retired)
MAIN.write_text(java, encoding="utf-8")


# Reapply the provider operation basis at the trusted Core boundary. Only fields actually touched
# by flag_patch operations are normalized, so deterministic engine/story fields remain intact.
facade = FACADE.read_text(encoding="utf-8")
sanitizer = '''    val sanitizedFlags = FlagCandidatePolicy.sanitizeCandidate(
      before = before,
      candidateFlags = candidate.optJSONObject("flags"),
      operations = operations,
      rolls = rolls,
    )
'''
command_anchor = '    commands += ValidatedLegacyStateCommand(\n'
if sanitizer not in facade:
    if facade.count(command_anchor) != 1:
        raise RuntimeError(f"Validated state command anchor count != 1: {facade.count(command_anchor)}")
    facade = facade.replace(command_anchor, sanitizer + command_anchor, 1)
legacy_line = '      flagsJson = candidate.optJSONObject("flags")?.toString(),\n'
kotlin_line = '      flagsJson = sanitizedFlags?.toString(),\n'
if kotlin_line not in facade:
    if facade.count(legacy_line) != 1:
        raise RuntimeError(f"Legacy flagsJson commit anchor count != 1: {facade.count(legacy_line)}")
    facade = facade.replace(legacy_line, kotlin_line, 1)

for marker in (
    "FlagCandidatePolicy.sanitizeCandidate(",
    "operations = operations",
    "flagsJson = sanitizedFlags?.toString()",
):
    if marker not in facade:
        raise RuntimeError("Game Core flag authority missing: " + marker)
FACADE.write_text(facade, encoding="utf-8")

print("Flag authority final applied: Kotlin owns flag_patch eligibility and rechecks touched fields at Core commit.")

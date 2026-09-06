from pathlib import Path
import re
import runpy

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"

main = MAIN.read_text(encoding="utf-8")


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


# The Knowledge Context Builder owns writerPrompt(), but later finalizers are allowed to change
# its literal prose. Inject by method boundary + first return expression so the address lock does
# not depend on a fragile exact prompt string.
if "PLAYER ADDRESS HARD LOCK:" not in main:
    start, end = method_bounds(main, "writerPrompt")
    method = main[start:end]
    return_match = re.search(r"(?m)^([ \t]*)return\s+", method)
    if not return_match:
        raise RuntimeError("writerPrompt return expression not found")
    indent = return_match.group(1)
    continuation = indent + "  "
    prefix = (
        '"PLAYER ADDRESS HARD LOCK: trong reply, mọi mô tả hành động, trạng thái hoặc phản hồi trực tiếp tới nhân vật do người dùng điều khiển phải dùng ngôi thứ hai và chỉ gọi là Bạn. " +\n'
        + continuation
        + '"Không dùng Kai, Kai Akechi, Player, người chơi, hắn, anh, cậu hay đại từ khác để thay cho Bạn khi chủ thể là người dùng. Tên Kai chỉ được dùng trong dữ liệu/state/canon nội bộ, không dùng làm cách gọi người dùng trong reply. " +\n'
        + continuation
    )
    method = method[: return_match.end()] + prefix + method[return_match.end() :]
    main = main[:start] + method + main[end:]

# Keep the runtime safety net narrow. These replacements cover only unambiguous aliases for the
# controlled player; they do not rewrite generic Vietnamese pronouns that may refer to NPCs.
normalize_helper = r'''  private String normalizePlayerAddress(String reply) {
    if (reply == null) return "";
    String normalized = reply;
    normalized = normalized.replaceAll("(?iu)\\bKai\\s+Akechi\\b", "Bạn");
    normalized = normalized.replaceAll("(?iu)\\bKai\\b", "Bạn");
    normalized = normalized.replaceAll("(?iu)\\bPlayer\\b", "Bạn");
    normalized = normalized.replaceAll("(?iu)\\bthe\\s+player\\b", "Bạn");
    normalized = normalized.replaceAll("(?iu)\\bngười\\s+chơi\\b", "Bạn");
    return normalized;
  }

'''
if "private String normalizePlayerAddress(String reply)" not in main:
    bridge = re.search(r"(?m)^\s*private\s+class\s+GameBridge\s*\{", main)
    if not bridge:
        raise RuntimeError("GameBridge anchor not found for player-address normalizer")
    main = main[: bridge.start()] + normalize_helper + main[bridge.start() :]

# Several runtime finalizers rewrite the turn pipeline, so do not depend on one exact whitespace
# spelling. The last generated.reply assignment is the player-facing reply path; the earlier one
# belongs to the auditor proposal text.
if "reply = normalizePlayerAddress(reply);" not in main:
    reply_pattern = re.compile(
        r'(?m)^([ \t]*)String\s+reply\s*=\s*generated\.optString\(\s*"reply"\s*,\s*""\s*\)(?:\.trim\(\))?\s*;[ \t]*$'
    )
    matches = list(reply_pattern.finditer(main))
    if not matches:
        candidates = [
            line.strip()
            for line in main.splitlines()
            if 'generated.optString("reply"' in line
        ]
        raise RuntimeError(
            "GM reply normalization anchor not found; candidates=" + repr(candidates[-6:])
        )
    match = matches[-1]
    indent = match.group(1)
    main = (
        main[: match.end()]
        + "\n"
        + indent
        + "reply = normalizePlayerAddress(reply);"
        + main[match.end() :]
    )

for marker in (
    "PLAYER ADDRESS HARD LOCK:",
    "private String normalizePlayerAddress(String reply)",
    "reply = normalizePlayerAddress(reply);",
):
    if marker not in main:
        raise RuntimeError("GM address-lock prepatch marker missing: " + marker)

MAIN.write_text(main, encoding="utf-8")

# Reuse the existing mobile UI/sprite finalizer after making its GM-address portion idempotent.
# It will now skip its old literal writerPrompt anchor and still perform all HTML/CSS/WebP checks.
runpy.run_path(str(ROOT / "patch-mobile-swipe-ui.py"), run_name="__main__")
print("Robust final GM Bạn-address lock prepared before mobile swipe UI finalization.")

from pathlib import Path
import re

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


# PLAYER_ADDRESS_LOCK_V1: preserve the non-UI behavior that used to be buried
# inside the legacy mobile finalizer.
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
    method = method[: return_match.end()] + prefix + method[return_match.end():]
    main = main[:start] + method + main[end:]

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
    main = main[:bridge.start()] + normalize_helper + main[bridge.start():]

if "reply = normalizePlayerAddress(reply);" not in main:
    reply_pattern = re.compile(
        r'(?m)^([ \t]*)String\s+reply\s*=\s*generated\.optString\(\s*"reply"\s*,\s*""\s*\)(?:\.trim\(\))?\s*;[ \t]*$'
    )
    matches = list(reply_pattern.finditer(main))
    if not matches:
        candidates = [line.strip() for line in main.splitlines() if 'generated.optString("reply"' in line]
        raise RuntimeError("GM reply normalization anchor not found; candidates=" + repr(candidates[-6:]))
    match = matches[-1]
    indent = match.group(1)
    main = main[:match.end()] + "\n" + indent + "reply = normalizePlayerAddress(reply);" + main[match.end():]

for marker in (
    "PLAYER ADDRESS HARD LOCK:",
    "private String normalizePlayerAddress(String reply)",
    "reply = normalizePlayerAddress(reply);",
):
    if marker not in main:
        raise RuntimeError("Player-address contract missing: " + marker)

MAIN.write_text(main, encoding="utf-8")
print("Player address lock preserved as a focused runtime patch: GM addresses the controlled character only as Bạn.")

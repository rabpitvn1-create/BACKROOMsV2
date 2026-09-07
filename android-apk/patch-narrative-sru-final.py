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


# ISSUE10_NARRATIVE_CLARITY_V1
# patch-ai-orchestrator.py originally carried a concise prose contract, but
# patch-conditional-audit.py later rebuilds writerPrompt and historically dropped it.
# Install the clarity contract on the final writerPrompt so every provider sees it.
if "NARRATIVE CLARITY HARD LOCK:" not in text:
    start, end = method_bounds(text, "writerPrompt")
    method = text[start:end]
    return_match = re.search(r"(?m)^([ \t]*)return\s+", method)
    if not return_match:
        raise RuntimeError("writerPrompt return expression not found for narrative clarity")
    indent = return_match.group(1)
    continuation = indent + "  "
    prefix = (
        '"NARRATIVE CLARITY HARD LOCK: Viết như tiểu thuyết hiện đại dễ đọc. Mỗi câu phải rõ chủ thể, hành động, vị trí hoặc hệ quả; ưu tiên nghĩa đen và trình tự nhân quả trước khi diễn giải. " +\n'
        + continuation
        + '"Ưu tiên danh từ cụ thể, động từ chính xác và chi tiết vật lý có chức năng. Không xếp chồng ẩn dụ, so sánh hay nhân hóa; không dùng từ hiếm hoặc hoa mỹ khi từ quen chính xác hơn; không lặp cùng một ý bằng nhiều câu đổi chữ. " +\n'
        + continuation
        + '"Backrooms phải được mô tả như một nơi chốn vật lý: bố cục, khoảng cách, vật liệu, ánh sáng, âm thanh, nhiệt độ và chướng ngại chỉ được khẳng định khi có căn cứ trong cảnh/state/canon. Không bịa hiện tượng chỉ để tăng độ rợn; cảnh yên được phép yên. " +\n'
        + continuation
        + '"Hội thoại phải tự nhiên và đúng kiến thức nhân vật, không biến thành thuyết minh lore. Một lượt phải dễ theo dõi theo thứ tự quan sát hoặc hành động -> phản ứng -> hệ quả; không kết bằng triết lý, điềm báo hoặc câu đinh rỗng chỉ để tạo vẻ bí hiểm. " +\n'
        + continuation
        + '"SRU ORGANIZATION HARD LOCK: đơn vị hiện tại của Kai, Iris và Syvial trong hệ tổ chức này là SRU (Special Response Unit / Lực lượng Phản ứng Đặc biệt). Không gọi họ là Black Blood hoặc Huyết Nha. Huyết Nha là lực lượng khác và chỉ được nhắc khi canon cùng ngữ cảnh thật sự nói về lực lượng đó. Blackblood Armor là tên trang bị hợp lệ, không phải tên đơn vị. " +\n'
        + continuation
    )
    method = method[: return_match.end()] + prefix + method[return_match.end():]
    text = text[:start] + method + text[end:]


# The writer sees recent history, so an old save can still contain the retired organization
# wording. Convert that exact failure mode into a deterministic hard issue and reuse the existing
# single-repair transaction instead of doing a blind post-generation string replacement.
helper_anchor = "  private JSONArray rejectedOperationIssuesAndroid(JSONObject before, JSONObject candidate, JSONObject generated) throws Exception {\n"
helper = r'''  private JSONArray retiredOrganizationIssuesAndroid(JSONObject generated) throws Exception {
    JSONArray issues = new JSONArray();
    String reply = lower(generated.optString("reply", ""));
    boolean retiredEnglish = containsAny(reply, "black blood", "black-blood");
    boolean huyetNhaMislabel = containsAny(reply,
      "đơn vị huyết nha", "đội huyết nha", "thuộc huyết nha", "đội trưởng huyết nha", "thành viên huyết nha");
    if (!retiredEnglish && !huyetNhaMislabel) return issues;
    issues.put(new JSONObject()
      .put("rule", "retired_organization")
      .put("severity", "hard")
      .put("claim", retiredEnglish ? "Black Blood" : "Huyết Nha as current unit")
      .put("reason", "Current organization canon is SRU. Rewrite the reply without calling Kai, Iris or Syvial Black Blood/Huyết Nha. Huyết Nha may appear only as a separate force when the current canon/context actually requires it. Preserve the equipment name Blackblood Armor when equipment is relevant."));
    return issues;
  }

'''
if "private JSONArray retiredOrganizationIssuesAndroid(JSONObject generated)" not in text:
    if text.count(helper_anchor) != 1:
        raise RuntimeError(
            "retired organization guard requires exactly one rejectedOperationIssuesAndroid anchor, found "
            + str(text.count(helper_anchor))
        )
    text = text.replace(helper_anchor, helper + helper_anchor, 1)

initial_audit = "          if (!meta) appendIssues(hardIssues, rejectedOperationIssuesAndroid(before, candidateState, generated));"
initial_guard = "          if (!meta) appendIssues(hardIssues, retiredOrganizationIssuesAndroid(generated));"
if initial_guard not in text:
    if text.count(initial_audit) != 1:
        raise RuntimeError(
            "retired organization initial audit anchor expected once, found " + str(text.count(initial_audit))
        )
    text = text.replace(initial_audit, initial_audit + "\n" + initial_guard, 1)

repair_audit = "            appendIssues(hardIssues, rejectedOperationIssuesAndroid(before, candidateState, generated));"
repair_guard = "            appendIssues(hardIssues, retiredOrganizationIssuesAndroid(generated));"
if repair_guard not in text:
    if text.count(repair_audit) != 1:
        raise RuntimeError(
            "retired organization repair audit anchor expected once, found " + str(text.count(repair_audit))
        )
    text = text.replace(repair_audit, repair_audit + "\n" + repair_guard, 1)

# Cross-source regression locks. The old organization label must be gone from the packaged Kai
# identity, while Blackblood Armor remains untouched as equipment.
for marker in (
    "NARRATIVE CLARITY HARD LOCK:",
    "SRU ORGANIZATION HARD LOCK:",
    "retiredOrganizationIssuesAndroid",
    "appendIssues(hardIssues, retiredOrganizationIssuesAndroid(generated))",
    "Đơn vị: SRU (Special Response Unit / Lực lượng Phản ứng Đặc biệt)",
    "BLACKBLOOD ARMOR & MODULES",
    "Blackblood Armor",
):
    if marker not in text:
        raise RuntimeError("Issue #10 narrative/SRU regression marker missing: " + marker)
if "Đơn vị: Black Blood" in text:
    raise RuntimeError("Issue #10 regression: stale Black Blood organization still exists in packaged KAI_CANON")

MAIN.write_text(text, encoding="utf-8")
print("Issue #10 guard applied: clear physical modern-fiction prose, current SRU identity, retired-organization repair, Blackblood Armor preserved.")

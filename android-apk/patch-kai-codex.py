from pathlib import Path
import json

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
CODEX = ROOT / "kai-codex.txt"
GUN_SKILLS = ROOT / "kai-gun-skills.txt"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return text.replace(old, new, 1)


main = MAIN.read_text(encoding="utf-8")
codex = CODEX.read_text(encoding="utf-8").strip()
gun_skills = GUN_SKILLS.read_text(encoding="utf-8").strip()

# KAI-AKECHI-TWILIGHT-CODEX-20260817-R05 predates the user-locked SRU organization canon.
# Migrate only the obsolete organization identity before packaging KAI_CANON. Do not touch the
# legitimate Blackblood Armor equipment name or Huyết Nha when it is referenced as another force.
stale_organization = "- Tổ chức: Vatican. Đơn vị: Black Blood — Huyết Nha. Chức vụ: Đội trưởng."
current_organization = (
    "- Tổ chức: Cảnh Sát chống hiện tượng dị thường. Đơn vị: SRU (Special Response Unit / Lực lượng Phản ứng Đặc biệt), "
    "trụ sở Vatican. Chức vụ: Đội trưởng.\n"
    "- HARD LOCK tổ chức: SRU là đơn vị hiện tại của Kai. Không dùng Black Blood/Huyết Nha để gọi đơn vị hiện tại; "
    "Huyết Nha chỉ là lực lượng khác khi canon/ngữ cảnh thật sự nói về lực lượng đó. Blackblood Armor vẫn là tên trang bị hợp lệ."
)
if stale_organization in codex:
    codex = codex.replace(stale_organization, current_organization, 1)
elif current_organization not in codex:
    raise RuntimeError("Kai Codex organization anchor is neither historical Black Blood nor current SRU")
if "Đơn vị: Black Blood" in codex:
    raise RuntimeError("Stale Black Blood organization survived Kai Codex SRU migration")
if "10. BLACKBLOOD ARMOR & MODULES" not in codex:
    raise RuntimeError("Blackblood Armor equipment canon was damaged by SRU migration")

if "KAI-AKECHI-TWILIGHT-CODEX-20260817-R05" not in codex:
    raise RuntimeError("Kai Codex: wrong or missing R05 source marker")
if len(codex) < 5000:
    raise RuntimeError(f"Kai Codex unexpectedly short: {len(codex)} chars")
for marker in (
    "THE LAST REQUIEM",
    "SILENT LULLABY",
    "SALVATION",
    "QUICK STEP",
    "+50 điểm phần trăm Evasion",
):
    if marker not in gun_skills:
        raise RuntimeError("Kai gun-skill addendum missing: " + marker)

combined_codex = codex + "\n\n" + gun_skills
java_codex = json.dumps(combined_codex, ensure_ascii=False)
constant_anchor = "  private static final int MAX_SNAPSHOT_BASE64 = 1_500_000;\n"
constant_block = constant_anchor + f"  private static final String KAI_CANON = {java_codex};\n"
main = replace_once(main, constant_anchor, constant_block, "Kai canon Java constant")

state_anchor = (
    '            "State hiện tại: " + state.toString() + "\\nHành động: " + action +\n'
)
state_with_canon = (
    '            "KAI CANON dưới đây là HARD LOCK. Nếu state hoặc model output cũ xung đột với danh tính, năng lực, trang bị, tính cách hay giới hạn cố định của Kai thì ưu tiên KAI_CANON; state chỉ mô tả tình trạng tạm thời có nguyên nhân hợp canon. Không tự nerf Kai, không tự thêm giới hạn ẩn và không tự quyết hành động có chủ ý thay Kai.\\n\\n" +\n'
    '            KAI_CANON + "\\n\\n" +\n'
    '            "State hiện tại: " + state.toString() + "\\nHành động: " + action +\n'
)
main = replace_once(main, state_anchor, state_with_canon, "Kai canon prompt injection")

MAIN.write_text(main, encoding="utf-8")
print(f"Injected Kai R05 operational codex plus automatic gun-skill addendum into APK Game Master prompt ({len(combined_codex)} chars), with current SRU organization canon.")

from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return text.replace(old, new, 1)


text = MAIN.read_text(encoding="utf-8")

text = replace_once(
    text,
    '    return containsAny(action, "bắn", "đánh", "đấm", "đá", "tấn công", "phản công", "né", "chiến đấu", "devil trigger", "guilty crown", "white wraith", "magnum", "talon", "phantom", "shoot", "attack", "fight");',
    '    return containsAny(action, "bắn", "đánh", "đấm", "đá", "tấn công", "phản công", "né", "chiến đấu", "devil trigger", "guilty crown", "mk19", "assault rifle", "sru-mk20", "powered armor", "shoot", "attack", "fight");',
    "R12 combat routing keywords",
)

text = replace_once(
    text,
    '      String resources = canonSection(DRIVE_CANON, "ENTITY VÀ TÀI NGUYÊN", "IRIS / SYVIAL");',
    '      String resources = canonSection(DRIVE_CANON, "ENTITY VÀ TÀI NGUYÊN", "IRIS / SYVIAL / LUCIA");',
    "character section resource boundary",
)
text = replace_once(
    text,
    '      String characterCanon = canonSection(DRIVE_CANON, "IRIS / SYVIAL", "GAMEPLAY HARD LOCK");',
    '      String characterCanon = canonSection(DRIVE_CANON, "IRIS / SYVIAL / LUCIA", "GAMEPLAY HARD LOCK");',
    "character section start boundary",
)
text = replace_once(
    text,
    '    boolean character = actionDialogue(action) || presentCharacter(state, "iris") || presentCharacter(state, "syvial") ||\n      rollSuccess(rolls, "irisReunion") || rollSuccess(rolls, "syvialReunion");',
    '    boolean character = actionDialogue(action) || presentCharacter(state, "iris") || presentCharacter(state, "syvial") || presentCharacter(state, "lucia") ||\n      rollSuccess(rolls, "irisReunion") || rollSuccess(rolls, "syvialReunion");',
    "Lucia present-character routing",
)

old_method = '''  private String compactKaiCanon(String action) {
    StringBuilder out = new StringBuilder();
    out.append(canonSection(KAI_CANON, "1. ĐỊNH DANH", "2. NGOẠI HÌNH"));
    out.append("\\n\\n").append(canonSection(KAI_CANON, "3. TÍNH CÁCH / NGUYÊN TẮC", "4. PHONG CÁCH GIAO TIẾP"));
    out.append("\\n\\n").append(canonSection(KAI_CANON, "4. PHONG CÁCH GIAO TIẾP", "5. NĂNG LỰC CHIẾN ĐẤU"));
    out.append("\\n\\n").append(canonSection(KAI_CANON, "5. NĂNG LỰC CHIẾN ĐẤU", "6. SPARDA CORE"));
    out.append("\\n\\n").append(canonSection(KAI_CANON, "6. SPARDA CORE", "7. DEVIL TRIGGER"));
    out.append("\\n\\n").append(canonSection(KAI_CANON, "10. BLACKBLOOD ARMOR & MODULES", "11. OMNIVAULT RING / NHẪN VẠN TÀNG"));
    out.append("\\n\\n").append(canonSection(KAI_CANON, "13. GIỚI HẠN THỰC SỰ", "14. ACTION LOCKS / CẤM MODEL TỰ BỊA"));
    out.append("\\n\\n").append(canonSection(KAI_CANON, "14. ACTION LOCKS / CẤM MODEL TỰ BỊA", "END OF KAI OPERATIONAL CODEX"));
    if (actionCombat(action)) {
      out.append("\\n\\n").append(canonSection(KAI_CANON, "7. DEVIL TRIGGER", "10. BLACKBLOOD ARMOR & MODULES"));
      out.append("\\n\\n").append(canonSection(KAI_CANON, "12. PHONG CÁCH CHIẾN ĐẤU", "13. GIỚI HẠN THỰC SỰ"));
    }
    if (actionOmnivault(action) || actionItem(action)) {
      out.append("\\n\\n").append(canonSection(KAI_CANON, "11. OMNIVAULT RING / NHẪN VẠN TÀNG", "12. PHONG CÁCH CHIẾN ĐẤU"));
    }
    return out.toString();
  }
'''
new_method = '''  private String compactKaiCanon(String action) {
    StringBuilder out = new StringBuilder();
    out.append(canonSection(KAI_CANON, "01 · ĐỊNH DANH VÀ VỊ TRÍ", "03 · NGOẠI HÌNH"));
    out.append("\\n\\n").append(canonSection(KAI_CANON, "03 · NGOẠI HÌNH", "04 · TÍNH CÁCH VÀ KHÍ CHẤT"));
    out.append("\\n\\n").append(canonSection(KAI_CANON, "04 · TÍNH CÁCH VÀ KHÍ CHẤT", "07 · NĂNG LỰC CHIẾN ĐẤU"));
    out.append("\\n\\n").append(canonSection(KAI_CANON, "07 · NĂNG LỰC CHIẾN ĐẤU", "08 · SPARDA CORE"));
    out.append("\\n\\n").append(canonSection(KAI_CANON, "08 · SPARDA CORE", "09 · DEVIL TRIGGER"));
    out.append("\\n\\n").append(canonSection(KAI_CANON, "11 · SRU ASSAULT RIFLE MK19", "14 · PHONG CÁCH CHIẾN ĐẤU"));
    out.append("\\n\\n").append(canonSection(KAI_CANON, "15 · PHONG CÁCH GIAO TIẾP", "16 · ĐIỂM YẾU VÀ GIỚI HẠN"));
    out.append("\\n\\n").append(canonSection(KAI_CANON, "16 · ĐIỂM YẾU VÀ GIỚI HẠN", "17 · QUY TẮC HÀNH ĐỘNG"));
    out.append("\\n\\n").append(canonSection(KAI_CANON, "17 · QUY TẮC HÀNH ĐỘNG", "END OF KAI OPERATIONAL CODEX R12"));
    if (actionCombat(action)) {
      out.append("\\n\\n").append(canonSection(KAI_CANON, "09 · DEVIL TRIGGER", "11 · SRU ASSAULT RIFLE MK19"));
      out.append("\\n\\n").append(canonSection(KAI_CANON, "14 · PHONG CÁCH CHIẾN ĐẤU", "15 · PHONG CÁCH GIAO TIẾP"));
      out.append("\\n\\n").append(canonSection(KAI_CANON, "KAI AKECHI / TWILIGHT — AUTOMATIC GUN SKILLS ADDENDUM", null));
    }
    return out.toString();
  }
'''
text = replace_once(text, old_method, new_method, "Kai R12 compact router")

for required in (
    'presentCharacter(state, "lucia")',
    '"IRIS / SYVIAL / LUCIA"',
    '"11 · SRU ASSAULT RIFLE MK19"',
    '"KAI AKECHI / TWILIGHT — AUTOMATIC GUN SKILLS ADDENDUM"',
):
    if required not in text:
        raise RuntimeError("Current character Codex routing verification failed: " + required)

MAIN.write_text(text, encoding="utf-8")
print("Aligned runtime Codex routing to Kai R12, Iris R07, Syvial R05 and Lucia R03 without changing encounter rules.")

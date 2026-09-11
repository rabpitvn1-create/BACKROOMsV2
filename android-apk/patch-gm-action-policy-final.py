from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
text = MAIN.read_text(encoding="utf-8")

pairs = (
    (
        '    boolean entityEncounterAction = exploreAction || "SEARCH".equals(actionKindNormalized) || "EXECUTE".equals(actionKindNormalized);\n',
        '    boolean entityEncounterAction = exploreAction;\n',
    ),
    (
        'SEARCH vẫn roll entityEncounter theo tỷ lệ Level và có thể khởi tạo roaming Entity mới;',
        'SEARCH không được khởi tạo encounter Entity mới và entityEncounter phải ineligible;',
    ),
    (
        'EXPLORE roll Entity theo cùng cơ chế với SEARCH và EXECUTE;',
        'đây là action duy nhất được phép kích hoạt roll encounter Entity mới;',
    ),
    (
        'không tự đổi mục tiêu; EXECUTE vẫn roll Entity và có thể khởi tạo roaming encounter mới.',
        'không tự đổi mục tiêu và không khởi tạo encounter Entity mới.',
    ),
)

for old, new in pairs:
    if old in text:
        if text.count(old) != 1:
            raise RuntimeError("typed action policy anchor is ambiguous")
        text = text.replace(old, new, 1)
    elif new not in text:
        raise RuntimeError("typed action policy anchor is missing")

if 'boolean entityEncounterAction = exploreAction;' not in text:
    raise RuntimeError("typed action encounter gate missing")
if 'entityEncounterAction = exploreAction ||' in text:
    raise RuntimeError("widened typed action encounter gate survived")

MAIN.write_text(text, encoding="utf-8")
print("Typed action policy finalized: new encounters are EXPLORE-only.")

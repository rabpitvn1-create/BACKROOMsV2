from pathlib import Path
import runpy

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
text = MAIN.read_text(encoding="utf-8")

# Final prompt semantics must agree with the typed runtime: only EXPLORE may start
# a fresh roaming Entity encounter. SEARCH/EXECUTE can still resolve an encounter
# that is already active, but they never open a new encounter roll.
pairs = (
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

for forbidden in (
    'entityEncounterAction = exploreAction ||',
    'entityEncounterAction && entityAllowed',
    'SEARCH vẫn roll entityEncounter theo tỷ lệ Level và có thể khởi tạo roaming Entity mới;',
    'EXPLORE roll Entity theo cùng cơ chế với SEARCH và EXECUTE;',
    'EXECUTE vẫn roll Entity và có thể khởi tạo roaming encounter mới.',
):
    if forbidden in text:
        raise RuntimeError("obsolete all-action Entity encounter policy survived: " + forbidden)

if 'exploreAction && entityAllowed' not in text:
    raise RuntimeError("EXPLORE-only Entity encounter gate missing")

MAIN.write_text(text, encoding="utf-8")
print("Typed action policy finalized: new encounters are EXPLORE-only.")

# All level/entity/action authority layers have settled at this point. Install the
# final low-risk recovery only now so earlier patches can still inspect the original
# fail-closed canon block, while the shipped runtime does not brick an ordinary
# Level 0 exploration turn after both writer and repair outputs are rejected.
safe_fallback = ROOT / "patch-canon-safe-fallback-final.py"
if not safe_fallback.is_file():
    raise RuntimeError("Canon safe fallback patch missing: " + safe_fallback.name)
runpy.run_path(str(safe_fallback), run_name="__main__")

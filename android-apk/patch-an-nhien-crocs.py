from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"

canon_path = CORE / "AnNhienCanon.kt"
canon = canon_path.read_text(encoding="utf-8")

old_id = 'const val AN_NHIEN_FOOTWEAR_ID = "an-nhien:baby-tree-pink-slippers"'
new_id = 'const val AN_NHIEN_FOOTWEAR_ID = "an-nhien:pink-crocs"'
old_name = 'const val FOOTWEAR_NAME = "Đôi dép màu hồng có hình Baby Tree"'
new_name = 'const val FOOTWEAR_NAME = "Đôi dép Crocs màu hồng"'

if old_id in canon:
    canon = canon.replace(old_id, new_id, 1)
elif new_id not in canon:
    raise RuntimeError("An Nhien footwear ID anchor missing")

if old_name in canon:
    canon = canon.replace(old_name, new_name, 1)
elif new_name not in canon:
    raise RuntimeError("An Nhien footwear name anchor missing")

canon_path.write_text(canon, encoding="utf-8")
print("An Nhiên footwear updated to pink Crocs.")

# Materialize special-follower canon/save/test compatibility, but skip its retired Java gameplay
# section. GameplayRollPolicy and SpecialFollowerEncounterPolicy are checked-in Kotlin authority now.
special = ROOT / "patch-special-followers-025.py"
code = special.read_text(encoding="utf-8")
section_start = "# 5) Runtime encounter policy: three independent 0.25% rolls on eligible physical turns in Level 0-6.\n"
section_end = "# 6) Regression coverage for authoritative follower definitions and the four-member party cap.\n"
if code.count(section_start) != 1 or code.count(section_end) != 1:
    raise RuntimeError("Special follower legacy gameplay section anchors are not unique")
start = code.index(section_start)
end = code.index(section_end, start)
validation = '''# 5) Runtime encounter policy is Kotlin-owned.\npolicy = (CORE / "GameplayRollPolicy.kt").read_text(encoding="utf-8")\nprojection = (CORE / "SpecialFollowerEncounterPolicy.kt").read_text(encoding="utf-8")\nfor marker in (\n    '"anNhienEncounter", 10_000, 25',\n    '"irisReunion", 10_000, 25',\n    '"syvialReunion", 10_000, 25',\n):\n    if marker not in policy:\n        raise RuntimeError("Kotlin special follower roll contract missing: " + marker)\nif "object SpecialFollowerEncounterPolicy" not in projection:\n    raise RuntimeError("Kotlin special follower projection policy missing")\n\n'''
code = code[:start] + validation + code[end:]
exec(compile(code, str(special), "exec"), {"__name__": "__main__", "__file__": str(special)})

import runpy
# Link the uploaded Iris/Syvial avatars and add instant developer Party shortcuts.
runpy.run_path(str(ROOT / "patch-special-follower-cheats-avatars.py"), run_name="__main__")

# Iris and Syvial can each carry at most 6 item types, with at most 20 units per type.
runpy.run_path(str(ROOT / "patch-special-follower-inventory-cap.py"), run_name="__main__")

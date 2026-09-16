from pathlib import Path
import runpy

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

# Special follower compatibility still needs its transient Java staging because later Lucia/legacy
# transforms consume those anchors. Only the prose prompt replacement is retired; the final runtime
# roll method is collapsed to Kotlin by patch-entity-rates-drops-final.py.
special = ROOT / "patch-special-followers-025.py"
code = special.read_text(encoding="utf-8")
strict_prompt = 'main = replace_once(main, old_prompt, new_prompt, "special follower GM lock")\n'
if code.count(strict_prompt) != 1:
    raise RuntimeError("Special follower prompt compatibility anchor is not unique")
code = code.replace(strict_prompt, 'if old_prompt in main:\n    main = main.replace(old_prompt, new_prompt, 1)\n', 1)
code = code.replace("    'IRIS / SYVIAL FOLLOWER LOCK:',\n", "", 1)
exec(compile(code, str(special), "exec"), {"__name__": "__main__", "__file__": str(special)})

# Link the uploaded Iris/Syvial avatars and add instant developer Party shortcuts.
runpy.run_path(str(ROOT / "patch-special-follower-cheats-avatars.py"), run_name="__main__")

# Iris and Syvial can each carry at most 6 item types, with at most 20 units per type.
runpy.run_path(str(ROOT / "patch-special-follower-inventory-cap.py"), run_name="__main__")

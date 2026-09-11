from pathlib import Path
import runpy

ROOT = Path(__file__).resolve().parent
TARGET = ROOT / "patch-drive-canon-gameplay.py"
text = TARGET.read_text(encoding="utf-8")

old = '''if "NOVEL-TEXTGAME-2026-08-20-DRIVE-INTEGRATION-R06" not in canon:\n    raise RuntimeError("Drive canon: wrong or missing R06 source marker")'''
new = '''if "BACKROOMS DRIVE INTEGRATION — R06 / HARD CANON" not in canon:\n    raise RuntimeError("Drive canon: wrong or missing R06 source marker")'''

count = text.count(old)
if count != 1:
    raise RuntimeError(f"R06 marker validator: expected exactly 1 legacy check, found {count}")

TARGET.write_text(text.replace(old, new, 1), encoding="utf-8")
runpy.run_path(str(ROOT / "patch-drive-canon-prologue-compat.py"), run_name="__main__")
print("R06 source marker validator aligned with drive-canon.txt; campaign startup compatibility applied.")

from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
PATCH = ROOT / "patch-lucia-combat-scout.py"
source = PATCH.read_text(encoding="utf-8")
source, _ = re.subn(r"^\s*'Lucia \\\"Lục\\\" bắn hỗ trợ bằng M4A1',\n", "", source, count=1, flags=re.MULTILINE)
exec(compile(source, str(PATCH), "exec"), {"__name__": "__main__", "__file__": str(PATCH)})
print("Lucia combat/scout finalizer executed with the live Lucia-only loot bonus.")

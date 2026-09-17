from pathlib import Path
import runpy

ROOT = Path(__file__).resolve().parent
runpy.run_path(str(ROOT / "patch-lucia-combat-scout.py"), run_name="__main__")
print("Lucia combat/scout compatibility verified with composed An Nhiên + Lucia loot projection.")

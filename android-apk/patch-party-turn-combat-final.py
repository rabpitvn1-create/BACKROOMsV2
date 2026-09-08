from pathlib import Path
import runpy

ROOT = Path(__file__).resolve().parent

# Preserve the existing final combat patch exactly, then apply the presentation-only
# Game Master frame as the last UI step so no later patch can move or overwrite it.
runpy.run_path(str(ROOT / "patch-party-turn-combat-final-base.py"), run_name="__main__")
runpy.run_path(str(ROOT / "patch-game-master-storytelling-frame-final.py"), run_name="__main__")

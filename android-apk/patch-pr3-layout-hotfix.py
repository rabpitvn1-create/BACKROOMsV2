from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
INDEX = ROOT / "app/src/main/assets/index.html"

main = MAIN.read_text(encoding="utf-8")
index = INDEX.read_text(encoding="utf-8")

# Field screenshot hotfix: move the rendered Kai overlay slightly to the right without
# changing its scale/aspect ratio. Scope the replacement to the final Snapshot character CSS.
if "PR3_KAI_SHIFT_RIGHT" not in main:
    pattern = re.compile(r"(\.snapshot \.snapshot-character\{[^}]*?)right:0;")
    main, count = pattern.subn(
        r"\1right:-4%;/* PR3_KAI_SHIFT_RIGHT */",
        main,
        count=1,
    )
    if count != 1:
        raise RuntimeError(f"PR3 Kai Snapshot position anchor: expected 1 match, found {count}")

# Keep the compact header height, but give the left title block a small safe inset so rounded
# mobile display edges/cutouts cannot eat the first letters. This override runs after all prior UI CSS.
if "PR3_HEADER_SAFE_INSET" not in index:
    style = r'''<style id="pr3MobilePositionHotfix">
/* PR3_HEADER_SAFE_INSET */
.topbar{padding-left:14px!important}
@supports(padding-left:max(0px,env(safe-area-inset-left))){.topbar{padding-left:max(14px,env(safe-area-inset-left))!important}}
</style>
'''
    if "</head>" not in index:
        raise RuntimeError("PR3 header style insertion anchor missing")
    index = index.replace("</head>", style + "</head>", 1)

for marker in ("PR3_KAI_SHIFT_RIGHT", "right:-4%;", "PR3_HEADER_SAFE_INSET", "padding-left:14px!important"):
    target = main if marker in ("PR3_KAI_SHIFT_RIGHT", "right:-4%;") else index
    if marker not in target:
        raise RuntimeError("PR3 mobile position marker missing: " + marker)

MAIN.write_text(main, encoding="utf-8")
INDEX.write_text(index, encoding="utf-8")
print("PR3 mobile position hotfix applied: header safe inset + Kai shifted slightly right.")

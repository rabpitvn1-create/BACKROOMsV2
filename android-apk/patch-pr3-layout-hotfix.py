from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
INDEX = ROOT / "app/src/main/assets/index.html"
ENTITY_ASSETS = ROOT / "app/src/main/assets/entity"

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

# PR3 Entity visual contract. The Entity stack is installed transitively by the gameplay/status
# patch chain before this final hotfix, so do not run those historical patch scripts a second time.
# Instead, make the final release fail closed if the canonical local overlay bridge, CombatRuntime
# visual authority, left-bottom Entity placement, Diệp Minh extension, or any canonical asset is lost.
canonical_entities = (
    "hound", "clump", "duller", "deathmoth", "hostile_faceling", "false_puddle", "paintings",
    "smiler", "skin-stealer", "predatory_window", "biological_pipeline", "wretch", "cable_mimic",
    "the_beast_of_level_5", "hotel_corpse_lure", "jeff_the_killer", "jane_the_killer", "slenderman",
    "diep_minh",
)
missing_assets = [key for key in canonical_entities if not (ENTITY_ASSETS / f"{key}.png").is_file()]
if missing_assets:
    raise RuntimeError("PR3 Entity assets missing: " + ", ".join(missing_assets))

entity_markers = (
    "file:///android_asset/entity/",
    '@JavascriptInterface public void requestEntityOverlay(String entityKey)',
    "window.backroomEntityOverlay=function(payload)",
    "img.className='snapshot-entity'",
    "img.style.bottom='0'",
    "img.style.left='0'",
    "img.style.objectPosition='left bottom'",
    "function activeEntityKey(){var c=state&&state.combat;if(!c||c.active!==true)return '';return normalizeEntityKey(c.entityKey);}",
    'case "diep_minh":',
    "'slenderman','diep_minh']",
    "file:///android_asset/kai_entity_overlay.png",
)
for marker in entity_markers:
    if marker not in main:
        raise RuntimeError("PR3 final Entity overlay contract missing: " + marker)

# Beast of Level 5 and Hotel Corpse Lure intentionally share the same visual asset. Do not add
# uniqueness/hash checks here; canonical key presence is the release contract.
for marker in ("PR3_KAI_SHIFT_RIGHT", "right:-4%;", "PR3_HEADER_SAFE_INSET", "padding-left:14px!important"):
    target = main if marker in ("PR3_KAI_SHIFT_RIGHT", "right:-4%;") else index
    if marker not in target:
        raise RuntimeError("PR3 mobile position marker missing: " + marker)

MAIN.write_text(main, encoding="utf-8")
INDEX.write_text(index, encoding="utf-8")
print("PR3 mobile + Entity overlay hotfix verified: Kai right shift, canonical Entity left-bottom overlay, CombatRuntime visual authority.")

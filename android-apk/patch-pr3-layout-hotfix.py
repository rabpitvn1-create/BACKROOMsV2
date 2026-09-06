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

# Inventory V4 is deliberately the final gameplay/UI layer in PR #3. Earlier release patches still
# generate legacy inventory/content structures, so prepare stable anchors against the final generated
# sources, apply the authority contract after every historical mutation, normalize obsolete generated
# regression fixtures, then enforce the current Omnivault canon after all historical copy/scan code.
import runpy
inventory_compat = ROOT / "patch-inventory-v4-anchor-compat.py"
inventory_v4 = ROOT / "patch-inventory-v4-final.py"
inventory_regression = ROOT / "patch-inventory-v4-regression-compat.py"
omnivault_current = ROOT / "patch-omnivault-current-canon-final.py"
for required in (inventory_compat, inventory_v4, inventory_regression, omnivault_current):
    if not required.is_file():
        raise RuntimeError("PR3 final gameplay patch missing: " + required.name)
runpy.run_path(str(inventory_compat), run_name="__main__")
runpy.run_path(str(inventory_v4), run_name="__main__")
runpy.run_path(str(inventory_regression), run_name="__main__")
runpy.run_path(str(omnivault_current), run_name="__main__")

# The packaged knowledge database is authoritative input to the Game Master. Runtime rejection of
# retired Omnivault commands is not sufficient if retrieval can still teach the writer old Scan/Copy
# canon, so correct the final generated knowledge after every historical patch has run.
import json
KNOWLEDGE_DB = ROOT / "app/src/main/assets/knowledge/knowledge_db.json"
KNOWLEDGE_SOURCE_MAP = ROOT / "KNOWLEDGE_SOURCE_MAP.md"
for required in (KNOWLEDGE_DB, KNOWLEDGE_SOURCE_MAP):
    if not required.is_file():
        raise RuntimeError("PR3 Omnivault knowledge source missing: " + required.name)

knowledge = json.loads(KNOWLEDGE_DB.read_text(encoding="utf-8"))
omnivault_records = [record for record in knowledge.get("records", []) if record.get("id") == "CHAR.KAI.OMNIVAULT"]
if len(omnivault_records) != 1:
    raise RuntimeError(f"PR3 Omnivault knowledge contract: expected 1 record, found {len(omnivault_records)}")

omnivault_record = omnivault_records[0]
omnivault_record["text"] = (
    "Omnivault Ring is an unlimited spatial storage for inanimate objects and never acts on living beings. "
    "Current canon keeps only two capabilities: store/retrieve the same existing objects and Restore/Hoàn nguyên existing equipment. "
    "Scan, Copy, item creation, duplicate creation, Marked and Upgrade are retired. Storage never increases authoritative item quantity. "
    "Restore returns the same existing equipment item to its best previously existing state; it cannot upgrade, copy, transform, or create an item. "
    "A successful Restore gives that item a 24-hour per-item cooldown. Omnivault does not store or recreate living beings."
)
omnivault_record["tags"] = ["kai", "omnivault", "nhẫn vạn tàng", "storage", "restore", "hoàn nguyên"]
KNOWLEDGE_DB.write_text(json.dumps(knowledge, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

source_map = KNOWLEDGE_SOURCE_MAP.read_text(encoding="utf-8")
stale_source_row = "| `CHAR.KAI.OMNIVAULT` | `Kai_Codex.docx` | `KAI-EQP-OMNIVAULT-01`, `KAI-WEAK-01` | IMMUTABLE | Inanimate-only storage; 3 scan/copy slots and codex restore constraints. |"
current_source_row = "| `CHAR.KAI.OMNIVAULT` | `Kai_Codex.docx` | `KAI-EQP-OMNIVAULT-01`, `KAI-WEAK-01` | IMMUTABLE | Unlimited inanimate storage; store/retrieve existing objects and Restore existing equipment only. No Scan/Copy/item creation/Marked/Upgrade; successful Restore has a 24-hour per-item cooldown. |"
if stale_source_row in source_map:
    source_map = source_map.replace(stale_source_row, current_source_row, 1)
elif current_source_row not in source_map:
    raise RuntimeError("PR3 Omnivault source-map contract missing expected row")
KNOWLEDGE_SOURCE_MAP.write_text(source_map, encoding="utf-8")

final_knowledge = KNOWLEDGE_DB.read_text(encoding="utf-8")
final_source_map = KNOWLEDGE_SOURCE_MAP.read_text(encoding="utf-8")
for forbidden in (
    "scan-copy memory has exactly 3 slots",
    "Copies cannot themselves be scanned",
    "once before being Marked",
    "3 scan/copy slots",
):
    if forbidden in final_knowledge or forbidden in final_source_map:
        raise RuntimeError("PR3 stale Omnivault knowledge survived final layer: " + forbidden)
for marker in (
    "Storage never increases authoritative item quantity.",
    "Scan, Copy, item creation, duplicate creation, Marked and Upgrade are retired.",
    "No Scan/Copy/item creation/Marked/Upgrade",
):
    if marker not in final_knowledge + "\n" + final_source_map:
        raise RuntimeError("PR3 current Omnivault knowledge marker missing: " + marker)

print("PR3 Omnivault knowledge verified: storage/restore only; Scan/Copy/item creation removed from packaged GM knowledge.")

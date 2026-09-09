from pathlib import Path
import json
import runpy

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
INDEX = ROOT / "app/src/main/assets/index.html"
ENTITY_ASSETS = ROOT / "app/src/main/assets/entity"

# PR3 used to mix display-layout hotfixes with gameplay/canon finalization. The
# layout responsibility now belongs exclusively to apply-android-ui.py; this file
# retains only the non-visual runtime/canon responsibilities.
main = MAIN.read_text(encoding="utf-8")

canonical_entities = (
    "hound", "clump", "duller", "deathmoth", "hostile_faceling", "false_puddle", "paintings",
    "smiler", "skin-stealer", "predatory_window", "biological_pipeline", "wretch", "cable_mimic",
    "the_beast_of_level_5", "hotel_corpse_lure", "jeff_the_killer", "jane_the_killer", "slenderman",
    "diep_minh",
)
missing_assets = [key for key in canonical_entities if not (ENTITY_ASSETS / f"{key}.png").is_file()]
if missing_assets:
    raise RuntimeError("Canonical Entity assets missing: " + ", ".join(missing_assets))

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
        raise RuntimeError("Final Entity overlay contract missing: " + marker)

# Inventory V4 remains the final inventory/gameplay layer. Earlier historical
# patches still generate legacy structures, so normalize anchors and enforce the
# current Omnivault contract after those mutations.
inventory_compat = ROOT / "patch-inventory-v4-anchor-compat.py"
inventory_v4 = ROOT / "patch-inventory-v4-final.py"
inventory_regression = ROOT / "patch-inventory-v4-regression-compat.py"
omnivault_current = ROOT / "patch-omnivault-current-canon-final.py"
for required in (inventory_compat, inventory_v4, inventory_regression, omnivault_current):
    if not required.is_file():
        raise RuntimeError("Final gameplay patch missing: " + required.name)
runpy.run_path(str(inventory_compat), run_name="__main__")
runpy.run_path(str(inventory_v4), run_name="__main__")
runpy.run_path(str(inventory_regression), run_name="__main__")
runpy.run_path(str(omnivault_current), run_name="__main__")

# Packaged knowledge is authoritative GM input. Remove retired Omnivault
# Scan/Copy semantics from both the database and source map after all historical
# knowledge writers have run.
KNOWLEDGE_DB = ROOT / "app/src/main/assets/knowledge/knowledge_db.json"
KNOWLEDGE_SOURCE_MAP = ROOT / "KNOWLEDGE_SOURCE_MAP.md"
for required in (KNOWLEDGE_DB, KNOWLEDGE_SOURCE_MAP):
    if not required.is_file():
        raise RuntimeError("Omnivault knowledge source missing: " + required.name)

knowledge = json.loads(KNOWLEDGE_DB.read_text(encoding="utf-8"))
omnivault_records = [record for record in knowledge.get("records", []) if record.get("id") == "CHAR.KAI.OMNIVAULT"]
if len(omnivault_records) != 1:
    raise RuntimeError(f"Omnivault knowledge contract: expected 1 record, found {len(omnivault_records)}")

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
    raise RuntimeError("Omnivault source-map contract missing expected row")
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
        raise RuntimeError("Stale Omnivault knowledge survived final layer: " + forbidden)
for marker in (
    "Storage never increases authoritative item quantity.",
    "Scan, Copy, item creation, duplicate creation, Marked and Upgrade are retired.",
    "No Scan/Copy/item creation/Marked/Upgrade",
):
    if marker not in final_knowledge + "\n" + final_source_map:
        raise RuntimeError("Current Omnivault knowledge marker missing: " + marker)

# Supplemental web-researched Entity canon remains isolated and merged only at
# the final knowledge layer. Project world/state authority still wins.
web_entity_canon = ROOT / "patch-web-entity-canon.py"
if not web_entity_canon.is_file():
    raise RuntimeError("Web Entity canon patch missing: " + web_entity_canon.name)
runpy.run_path(str(web_entity_canon), run_name="__main__")

# Latest user-locked SRU organization canon is applied after the historical
# knowledge layers so retired Black Blood organization labels cannot win back.
sru_canon = ROOT / "patch-sru-canon.py"
if not sru_canon.is_file():
    raise RuntimeError("SRU canon patch missing: " + sru_canon.name)
runpy.run_path(str(sru_canon), run_name="__main__")

# The prologue is a static HTML string, so writer/auditor canon guards never touch it.
# Normalize that retired organization label at the same final SRU authority layer.
prologue_html = INDEX.read_text(encoding="utf-8")
stale_prologue = "Kênh nội bộ Black Blood im lặng."
current_prologue = "Kênh nội bộ SRU (Special Respond Unit) im lặng."
if stale_prologue in prologue_html:
    count = prologue_html.count(stale_prologue)
    if count != 1:
        raise RuntimeError(
            "SRU prologue migration expected exactly one stale Black Blood channel, found "
            + str(count)
        )
    prologue_html = prologue_html.replace(stale_prologue, current_prologue, 1)
elif current_prologue not in prologue_html:
    raise RuntimeError("SRU prologue communication marker missing")
if stale_prologue in prologue_html:
    raise RuntimeError("Retired Black Blood prologue channel survived SRU finalization")
INDEX.write_text(prologue_html, encoding="utf-8")

# Conditional-audit historically rebuilds writerPrompt after the original prose contract.
# Re-assert narrative clarity and current SRU identity only after all historical runtime
# transformations have settled, then reuse the existing hard-issue repair transaction.
narrative_sru = ROOT / "patch-narrative-sru-final.py"
if not narrative_sru.is_file():
    raise RuntimeError("Narrative/SRU final patch missing: " + narrative_sru.name)
runpy.run_path(str(narrative_sru), run_name="__main__")

print("Final runtime canon verified: Entity visuals, Inventory V4, current Omnivault knowledge, web Entity supplement, SRU organization canon, SRU prologue, narrative clarity guard.")

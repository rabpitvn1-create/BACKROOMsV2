from pathlib import Path
import json

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
KNOWLEDGE = ROOT / "app/src/main/assets/knowledge/knowledge_db.json"
ASSET = ROOT / "app/src/main/assets/entity/jane_the_killer.png"

text = MAIN.read_text(encoding="utf-8")

# Jane is canonical content only at this stage. Encounter ownership belongs to the
# shared Entity pipeline (entityEncounter + roamingEntityKey), never a private
# jeffEncounter/janeEncounter channel.
for marker in (
    'case "jane_the_killer": name = "Jane the Killer"; break;',
    "'jane_the_killer'",
    'file:///android_asset/entity/',
):
    if marker not in text:
        raise RuntimeError("Jane canonical runtime marker missing: " + marker)

if not ASSET.is_file():
    raise RuntimeError("Jane canonical local asset missing: jane_the_killer.png")

# This patch must never resurrect the retired independent encounter model.
for forbidden in (
    'rolls.put("jeffEncounter"',
    'rolls.put("janeEncounter"',
    'thresholdRoll("jeffEncounter"',
    'thresholdRoll("janeEncounter"',
):
    if forbidden in text:
        raise RuntimeError("Retired independent killer encounter channel is active before Jane canon: " + forbidden)

db = json.loads(KNOWLEDGE.read_text(encoding="utf-8"))
records = db.get("records", [])
canonical_jane = {
    "id": "ENTITY.JANE_THE_KILLER",
    "domain": "ENTITY",
    "kind": "unique-roaming-entity",
    "text": "Jane the Killer is a unique humanoid predator / roaming hunter. Runtime identity uses canonical key jane_the_killer. Jane hunts humans and is never a neutral NPC, ally, merchant or rescue character. Encounter triggering is owned by the shared Entity encounter pipeline.",
    "source": {"document": "current project runtime canon", "anchor": "Jane the Killer"},
    "authority": "USER_OVERRIDE_ENTITY_CANON",
    "mutability": "IMMUTABLE",
    "priority": 22,
    "tags": ["jane", "jane the killer", "roaming hunter"],
    "references": ["ENTITY.GLOBAL_HARD_LOCK", "ENTITY.JEFF"],
    "affordances": ["direct_threat", "roaming_incursion"]
}
records = [r for r in records if r.get("id") != "ENTITY.JANE_THE_KILLER"]
records.append(canonical_jane)
db["records"] = records
KNOWLEDGE.write_text(json.dumps(db, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

print("Jane canonical record verified; encounter routing remains on shared entityEncounter + roamingEntityKey with no legacy killer rolls.")

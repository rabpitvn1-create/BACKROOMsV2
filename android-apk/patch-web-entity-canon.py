from pathlib import Path
import json

ROOT = Path(__file__).resolve().parent
DATA = ROOT / "app/src/main/assets/knowledge/entity_web_canon.json"
DB = ROOT / "app/src/main/assets/knowledge/knowledge_db.json"

if not DATA.is_file() or not DB.is_file():
    raise RuntimeError("Web Entity canon source or knowledge database is missing")

external = json.loads(DATA.read_text(encoding="utf-8"))
knowledge = json.loads(DB.read_text(encoding="utf-8"))
records = external.get("records", [])
if not isinstance(records, list) or not records:
    raise RuntimeError("Web Entity canon contains no records")

known = {str(record.get("id", "")) for record in knowledge.get("records", [])}
seen = set()
added = []
for item in records:
    entity_id = str(item.get("id", "")).strip()
    visual = str(item.get("visual", "")).strip()
    canon = str(item.get("canon", "")).strip()
    source = item.get("source") or {}
    source_document = str(source.get("document", "")).strip()
    source_anchor = str(source.get("anchor", "")).strip()

    if not entity_id.startswith("WEB.ENTITY."):
        raise RuntimeError("Web Entity id must stay in WEB.ENTITY namespace: " + entity_id)
    if entity_id in seen or entity_id in known:
        raise RuntimeError("Duplicate Web Entity canon id: " + entity_id)
    seen.add(entity_id)
    if not source_document.startswith("https://backrooms-wiki.wikidot.com/"):
        raise RuntimeError("Web Entity source must be a Backrooms Wiki page: " + entity_id)
    if not source_anchor:
        raise RuntimeError("Web Entity source anchor missing: " + entity_id)
    if not 1200 <= len(visual) <= 1800:
        raise RuntimeError(f"Web Entity visual description must be about 1500 chars: {entity_id}={len(visual)}")

    text = visual + "\n" + canon
    if len(text) >= 3000:
        raise RuntimeError(f"Web Entity entry must stay below 3000 chars: {entity_id}={len(text)}")

    record = {
        "id": entity_id,
        "domain": "ENTITY",
        "kind": "web-reference",
        "text": text,
        "source": {"document": source_document, "anchor": source_anchor},
        "authority": "WEB_REFERENCE_CANON",
        "mutability": "REFERENCE",
        "priority": int(item.get("priority", 76)),
        "tags": list(item.get("tags", [])),
        "references": list(item.get("references", ["ENTITY.GLOBAL_HARD_LOCK"])),
        "affordances": list(item.get("affordances", ["direct_threat", "visual_reference"])),
    }
    knowledge.setdefault("records", []).append(record)
    known.add(entity_id)
    added.append((entity_id, len(visual), len(text)))

DB.write_text(json.dumps(knowledge, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

final = json.loads(DB.read_text(encoding="utf-8"))
final_ids = [str(record.get("id", "")) for record in final.get("records", [])]
for entity_id, visual_chars, total_chars in added:
    if final_ids.count(entity_id) != 1:
        raise RuntimeError("Web Entity canon merge verification failed: " + entity_id)
    print(f"WEB_ENTITY_CANON_V1 {entity_id}: visual={visual_chars}, total={total_chars}")

print(f"Web Entity canon merged as supplemental reference only: {len(added)} record(s); project WORLD_CANON/hard-lock remains authoritative on conflict.")

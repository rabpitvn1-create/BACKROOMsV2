from pathlib import Path
import json

ROOT = Path(__file__).resolve().parent
DATA = ROOT / "app/src/main/assets/knowledge/sublevels_0_6_source.json"
DB = ROOT / "app/src/main/assets/knowledge/knowledge_db.json"
ENGINE = ROOT / "app/src/main/java/com/rabpit/backroom/core/knowledge/KnowledgeContextEngine.kt"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"

for required in (DATA, DB, ENGINE, MAIN):
    if not required.is_file():
        raise RuntimeError("Sublevel canon input missing: " + str(required))

source = json.loads(DATA.read_text(encoding="utf-8"))
items = source.get("records", [])
expected_counts = {0: 12, 1: 4, 2: 3, 3: 2, 4: 3, 5: 3, 6: 6}
if len(items) != 33:
    raise RuntimeError(f"Expected 33 current wiki-listed Level 0-6 sublevels, found {len(items)}")
actual_counts = {level: sum(1 for item in items if item.get("parentLevel") == level) for level in range(7)}
if actual_counts != expected_counts:
    raise RuntimeError(f"Sublevel parent counts changed: {actual_counts}")
if any(str(item.get("designation", "")).strip() == "Level 5.3" for item in items):
    raise RuntimeError("Level 5.3 is not in the current Fandom Levels 0-8 list and must not be injected")

knowledge = json.loads(DB.read_text(encoding="utf-8"))
records = knowledge.get("records", [])
known = {str(record.get("id", "")) for record in records}
parent_records = {
    str(record.get("id", "")): record
    for record in records
    if record.get("domain") == "LEVEL"
}
by_parent = {level: [] for level in range(7)}
seen = set()


def aliases(item):
    values = [str(item["designation"]).strip().lower(), str(item["name"]).strip().lower()]
    values.extend({
        "SUBLEVEL.00.EPSILON": ["level epsilon"],
        "SUBLEVEL.01.PHI": ["level phi", "level 1.618033988749894"],
        "SUBLEVEL.02.E": ["level e", "level 2.71828182845"],
        "SUBLEVEL.03.PI": ["level pi", "level 3.14159265358"],
        "SUBLEVEL.06.TAU": ["level tau", "level 6.28318530718"],
    }.get(str(item["id"]), []))
    return list(dict.fromkeys(values))


for item in items:
    record_id = str(item.get("id", "")).strip()
    parent = item.get("parentLevel")
    designation = str(item.get("designation", "")).strip()
    name = str(item.get("name", "")).strip()
    wiki = str(item.get("wiki", "")).strip()
    summary = str(item.get("summary", "")).strip()
    status = str(item.get("status", "")).strip()
    if not record_id.startswith("SUBLEVEL.") or record_id in seen or record_id in known:
        raise RuntimeError("Invalid or duplicate sublevel id: " + record_id)
    if parent not in expected_counts:
        raise RuntimeError("Sublevel parent outside Level 0-6: " + record_id)
    if not designation or not name or not summary:
        raise RuntimeError("Incomplete sublevel source record: " + record_id)
    if not wiki.startswith("https://backrooms.fandom.com/wiki/"):
        raise RuntimeError("Sublevel source must be Backrooms Fandom wiki: " + record_id)
    parent_id = f"LEVEL.{parent:02d}"
    if parent_id not in parent_records:
        raise RuntimeError("Missing parent Level knowledge record: " + parent_id)

    text = (
        summary
        + f" Runtime lock: this is a sublevel of Level {parent}; authoritative level.number remains {parent}. "
        + "The external wiki is reference material only: Project WORLD_CANON and live state win every conflict. "
        + "Wiki entrances/exits are possibilities, never guaranteed gameplay transitions."
    )
    if parent == 6:
        text += (
            " Parent Level 6 remains the project's outdoor dark-tundra baseline; this sublevel's own "
            "interior/light motif must never overwrite that parent baseline."
        )

    records.append({
        "id": record_id,
        "domain": "SUBLEVEL",
        "kind": "wiki-reference",
        "text": text,
        "source": {"document": wiki, "anchor": f'{designation}: "{name}" [{status}]'},
        "authority": "EXTERNAL_REFERENCE",
        "mutability": "WIKI_MUTABLE",
        "priority": 46,
        "tags": aliases(item),
        "references": [parent_id],
        "affordances": ["sublevel_context"],
    })
    seen.add(record_id)
    known.add(record_id)
    by_parent[parent].append(item)

source_list = str(source.get("sourceList", "")).strip()
if not source_list.startswith("https://backrooms.fandom.com/wiki/"):
    raise RuntimeError("Sublevel source list URL missing")

for parent, parent_items in by_parent.items():
    catalog_id = f"SUBLEVEL.CATALOG.{parent:02d}"
    parent_id = f"LEVEL.{parent:02d}"
    if catalog_id in known:
        raise RuntimeError("Duplicate sublevel catalog id: " + catalog_id)
    entries = "; ".join(f'{item["designation"]} — {item["name"]}' for item in parent_items)
    records.append({
        "id": catalog_id,
        "domain": "SUBLEVEL",
        "kind": "parent-catalog",
        "text": (
            f"Current Backrooms Fandom sublevel references under Level {parent}: {entries}. "
            "These names are possible local subregions, not automatic encounters or guaranteed routes. "
            f"Entering one keeps authoritative level.number={parent}; Project WORLD_CANON/live state wins conflicts."
        ),
        "source": {"document": source_list, "anchor": f"Level {parent} sub-level list"},
        "authority": "EXTERNAL_REFERENCE",
        "mutability": "WIKI_MUTABLE",
        "priority": 45,
        "tags": [f"level {parent} sublevels", f"level {parent} sub-levels"],
        "references": [],
        "affordances": ["sublevel_context"],
    })
    known.add(catalog_id)
    parent_refs = parent_records[parent_id].setdefault("references", [])
    if catalog_id not in parent_refs:
        parent_refs.append(catalog_id)

knowledge["records"] = records
DB.write_text(json.dumps(knowledge, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

# Parent Level always retrieves its compact sublevel catalog. A concrete sublevel is
# retrieved only from a live exploration lock or an explicit scene/location mention.
engine = ENGINE.read_text(encoding="utf-8")
build_old = "      addCurrentLevel()\n      addPresentRuntimeCards()"
build_new = "      addCurrentLevel()\n      addCurrentSublevel()\n      addPresentRuntimeCards()"
if build_new not in engine:
    if engine.count(build_old) != 1:
        raise RuntimeError(f"Knowledge sublevel build hook expected once, found {engine.count(build_old)}")
    engine = engine.replace(build_old, build_new, 1)

helper_anchor = "    private fun addPresentRuntimeCards() {\n"
helper = r'''    private fun addCurrentSublevel() {
      val parentId = "LEVEL.%02d".format(Locale.ROOT, currentLevel())
      val sublevels = db.records.values.asSequence()
        .filter { it.domain == "SUBLEVEL" && it.kind == "wiki-reference" && parentId in it.references }
        .toList()
      if (sublevels.isEmpty()) return

      val locked = normalize(
        state.optJSONObject("flags")
          ?.optJSONObject("exploration")
          ?.optString("sublevelId", "")
          .orEmpty()
      )
      if (locked.isNotEmpty()) {
        val record = sublevels.firstOrNull { normalize(it.id) == locked || locked in it.tags }
        if (record != null) {
          add(record.id, "live exploration sublevel lock")
          return
        }
      }

      fun exactTagMention(tag: String): Boolean {
        if (tag.isBlank()) return false
        if (!tag.startsWith("level ")) return sceneText.contains(tag)
        val pattern = Regex("(^|[^\\p{L}\\p{N}.])" + Regex.escape(tag) + "($|[^\\p{L}\\p{N}.])")
        return pattern.containsMatchIn(sceneText)
      }

      sublevels.sortedBy { it.id }
        .firstOrNull { record -> record.tags.sortedByDescending { it.length }.any(::exactTagMention) }
        ?.let { add(it.id, "explicit sublevel in live scene/state") }
    }

'''
if "private fun addCurrentSublevel()" not in engine:
    if engine.count(helper_anchor) != 1:
        raise RuntimeError(f"Knowledge sublevel helper anchor expected once, found {engine.count(helper_anchor)}")
    engine = engine.replace(helper_anchor, helper + helper_anchor, 1)
for marker in (
    "addCurrentSublevel()",
    "live exploration sublevel lock",
    'it.domain == "SUBLEVEL"',
    'optString("sublevelId", "")',
    "explicit sublevel in live scene/state",
):
    if marker not in engine:
        raise RuntimeError("Knowledge sublevel runtime marker missing: " + marker)
ENGINE.write_text(engine, encoding="utf-8")

# A sublevel is local geography beneath the integer parent Level. Patch the final
# writerPrompt produced by patch-conditional-audit.py rather than relying on the
# earlier pre-audit prompt shape.
main = MAIN.read_text(encoding="utf-8")
prompt_anchor = '      "\\n\\nOPERATION TYPES: set_location{value}; set_level{level};'
prompt_rule = (
    '      "\\n\\nSUBLEVEL STATE LOCK: Các SUBLEVEL.* trong KNOWLEDGE_PACKET là vùng cục bộ thuộc parent Level hiện tại, không phải Level số mới. '
    'Chỉ xác nhận vào sublevel khi cảnh/evidence hiện tại thực sự hỗ trợ; không tự spawn chỉ vì catalog có tên. Khi đã vào, giữ nguyên level.number parent, dùng set_location và '
    'flag_patch{root:exploration,value:{sublevelId:\\\"SUBLEVEL.xx...\\\"}}. Khi quay lại parent thì đặt sublevelId thành chuỗi rỗng. Tuyệt đối không dùng set_level với số thập phân/ký hiệu và '
    'không biến entrance/exit trên wiki thành transition được đảm bảo. Project WORLD_CANON/live state luôn thắng wiki khi xung đột. " +\n'
)
if "SUBLEVEL STATE LOCK:" not in main:
    if main.count(prompt_anchor) != 1:
        raise RuntimeError(f"Sublevel writer prompt anchor expected once, found {main.count(prompt_anchor)}")
    main = main.replace(prompt_anchor, prompt_rule + prompt_anchor, 1)
MAIN.write_text(main, encoding="utf-8")

final = json.loads(DB.read_text(encoding="utf-8"))
final_records = final.get("records", [])
final_ids = [str(record.get("id", "")) for record in final_records]
if sum(1 for record in final_records if record.get("domain") == "SUBLEVEL" and record.get("kind") == "wiki-reference") != 33:
    raise RuntimeError("Final knowledge database does not contain exactly 33 wiki sublevels")
for parent in range(7):
    catalog_id = f"SUBLEVEL.CATALOG.{parent:02d}"
    if final_ids.count(catalog_id) != 1:
        raise RuntimeError("Final sublevel catalog missing: " + catalog_id)
    parent_id = f"LEVEL.{parent:02d}"
    parent_record = next(record for record in final_records if record.get("id") == parent_id)
    if catalog_id not in parent_record.get("references", []):
        raise RuntimeError("Parent Level does not reference its sublevel catalog: " + parent_id)

level6_records = [
    record for record in final_records
    if record.get("domain") == "SUBLEVEL"
    and record.get("id", "").startswith("SUBLEVEL.06.")
    and record.get("kind") == "wiki-reference"
]
if len(level6_records) != 6 or any("outdoor dark-tundra baseline" not in record.get("text", "") for record in level6_records):
    raise RuntimeError("Level 6 project-baseline lock missing from sublevel references")

print(
    "WEB_SUBLEVEL_CANON_V1 merged 33 current Level 0-6 Fandom sublevels + 7 parent catalogs; "
    "integer parent Level authority preserved and runtime sublevel lookup installed."
)

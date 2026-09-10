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
parent_difficulties = source.get("parentDifficulties", [])
expected_counts = {0: 12, 1: 4, 2: 3, 3: 2, 4: 3, 5: 3, 6: 6}
allowed_difficulty_sources = {"WIKI_DIRECT", "WIKI_NONSTANDARD_MAPPED", "PROJECT_DESIGNED"}
if source.get("schemaVersion") != 2:
    raise RuntimeError("Sublevel source schemaVersion must be 2")
if len(items) != 33:
    raise RuntimeError(f"Expected 33 current wiki-listed Level 0-6 sublevels, found {len(items)}")
actual_counts = {level: sum(1 for item in items if item.get("parentLevel") == level) for level in range(7)}
if actual_counts != expected_counts:
    raise RuntimeError(f"Sublevel parent counts changed: {actual_counts}")
if any(str(item.get("designation", "")).strip() == "Level 5.3" for item in items):
    raise RuntimeError("Level 5.3 is not in the current Fandom Levels 0-8 list and must not be injected")
if len(parent_difficulties) != 7 or {item.get("parentLevel") for item in parent_difficulties} != set(range(7)):
    raise RuntimeError("Exactly one difficulty record is required for each parent Level 0-6")


def validate_difficulty(owner, difficulty):
    if not isinstance(difficulty, dict):
        raise RuntimeError("Difficulty object missing: " + owner)
    rating = difficulty.get("rating")
    difficulty_source = str(difficulty.get("source", "")).strip()
    wiki_class = str(difficulty.get("wikiClass", "")).strip()
    rationale = str(difficulty.get("rationale", "")).strip()
    if not isinstance(rating, int) or not 1 <= rating <= 5:
        raise RuntimeError(f"Difficulty rating must be integer 1-5: {owner}={rating!r}")
    if difficulty_source not in allowed_difficulty_sources:
        raise RuntimeError(f"Unsupported difficulty source: {owner}={difficulty_source!r}")
    if not wiki_class or not rationale:
        raise RuntimeError("Difficulty wikiClass/rationale missing: " + owner)
    if difficulty_source == "WIKI_DIRECT":
        expected = f"CLASS {rating}"
        if wiki_class != expected:
            raise RuntimeError(f"Direct Wiki class/rating mismatch: {owner}: {wiki_class!r} vs {rating}")
    return rating, difficulty_source, wiki_class, rationale


parent_difficulty_by_level = {}
for item in parent_difficulties:
    parent = item.get("parentLevel")
    wiki = str(item.get("wiki", "")).strip()
    if not wiki.startswith("https://backrooms.fandom.com/wiki/"):
        raise RuntimeError(f"Parent difficulty Wiki source invalid: Level {parent}")
    parent_difficulty_by_level[parent] = validate_difficulty(f"Level {parent}", item.get("difficulty"))

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


def difficulty_text(rating, difficulty_source, wiki_class, rationale):
    if difficulty_source == "PROJECT_DESIGNED":
        provenance = "PROJECT_DESIGNED provisional rating; it is not a Wiki class"
    elif difficulty_source == "WIKI_NONSTANDARD_MAPPED":
        provenance = f"Wiki {wiki_class} mapped to the Project 1-5 gameplay scale"
    else:
        provenance = f"Wiki {wiki_class}"
    return (
        f" Gameplay difficulty is {rating}/5 ({provenance}). {rationale} "
        "Wiki entity-count/resident-entity wording never gates runtime encounters: Project override allows the canonical roaming pool on every Level 0-6 and every listed sublevel, including Levels 0, 4 and 6."
    )


# Put parent difficulty directly into the parent Level knowledge records so normal
# current-Level retrieval always carries a difficulty even when no sublevel is active.
for parent in range(7):
    parent_id = f"LEVEL.{parent:02d}"
    if parent_id not in parent_records:
        raise RuntimeError("Missing parent Level knowledge record: " + parent_id)
    rating, difficulty_source, wiki_class, rationale = parent_difficulty_by_level[parent]
    marker = " Gameplay difficulty is "
    if marker in str(parent_records[parent_id].get("text", "")):
        raise RuntimeError("Parent difficulty was already injected before final web canon: " + parent_id)
    parent_records[parent_id]["text"] = str(parent_records[parent_id].get("text", "")).rstrip() + difficulty_text(
        rating, difficulty_source, wiki_class, rationale
    )

for item in items:
    record_id = str(item.get("id", "")).strip()
    parent = item.get("parentLevel")
    designation = str(item.get("designation", "")).strip()
    name = str(item.get("name", "")).strip()
    wiki = str(item.get("wiki", "")).strip()
    summary = str(item.get("summary", "")).strip()
    status = str(item.get("status", "")).strip()
    snapshot = item.get("snapshot", None)
    if not record_id.startswith("SUBLEVEL.") or record_id in seen or record_id in known:
        raise RuntimeError("Invalid or duplicate sublevel id: " + record_id)
    if parent not in expected_counts:
        raise RuntimeError("Sublevel parent outside Level 0-6: " + record_id)
    if not designation or not name or not summary:
        raise RuntimeError("Incomplete sublevel source record: " + record_id)
    if snapshot != "":
        raise RuntimeError("Current sublevel snapshots must remain explicitly empty: " + record_id)
    if not wiki.startswith("https://backrooms.fandom.com/wiki/"):
        raise RuntimeError("Sublevel source must be Backrooms Fandom wiki: " + record_id)
    parent_id = f"LEVEL.{parent:02d}"
    rating, difficulty_source, wiki_class, rationale = validate_difficulty(record_id, item.get("difficulty"))

    text = (
        summary
        + f" Runtime lock: this is a sublevel of Level {parent}; authoritative level.number remains {parent}. "
        + "The external wiki is reference material only: Project WORLD_CANON and live state win every conflict. "
        + "Wiki entrances/exits are possibilities, never guaranteed gameplay transitions."
        + difficulty_text(rating, difficulty_source, wiki_class, rationale)
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
    entries = "; ".join(
        f'{item["designation"]} — {item["name"]} [{item["difficulty"]["rating"]}/5]'
        for item in parent_items
    )
    records.append({
        "id": catalog_id,
        "domain": "SUBLEVEL",
        "kind": "parent-catalog",
        "text": (
            f"Current Backrooms Fandom sublevel references under Level {parent}: {entries}. "
            "Bracketed values are Project gameplay difficulty ratings with provenance stored on each concrete sublevel record. "
            "These names are possible local subregions, not automatic encounters or guaranteed routes. "
            f"Entering one keeps authoritative level.number={parent}; Project WORLD_CANON/live state wins conflicts. "
            "All canonical runtime Entities remain roaming-eligible here; there is no Level 0/4/6 exception."
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
    '      "DIFFICULTY + ROAMING LOCK: Gameplay difficulty 1-5 trong KNOWLEDGE_PACKET phải được tôn trọng. WIKI_DIRECT là class Wiki đã xác minh; WIKI_NONSTANDARD_MAPPED giữ raw class Wiki rồi map sang 1-5; PROJECT_DESIGNED là provisional và tuyệt đối không được kể như canon Wiki. Entity roaming độc lập với Wiki entity-count/resident wording: toàn bộ canonical roaming pool có thể xuất hiện ở mọi Level 0-6 và mọi sublevel, không ngoại lệ Level 0, 4 hay 6. " +\n'
)
if "SUBLEVEL STATE LOCK:" not in main:
    if main.count(prompt_anchor) != 1:
        raise RuntimeError(f"Sublevel writer prompt anchor expected once, found {main.count(prompt_anchor)}")
    main = main.replace(prompt_anchor, prompt_rule + prompt_anchor, 1)
elif "DIFFICULTY + ROAMING LOCK:" not in main:
    raise RuntimeError("Sublevel prompt exists without required difficulty/roaming lock")
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
    if "Gameplay difficulty is " not in parent_record.get("text", ""):
        raise RuntimeError("Parent Level difficulty missing from runtime knowledge: " + parent_id)

sublevel_records = [
    record for record in final_records
    if record.get("domain") == "SUBLEVEL" and record.get("kind") == "wiki-reference"
]
if any("Gameplay difficulty is " not in record.get("text", "") for record in sublevel_records):
    raise RuntimeError("At least one sublevel runtime record is missing gameplay difficulty")
if any("there is no Level 0/4/6 exception" not in record.get("text", "") for record in sublevel_records):
    raise RuntimeError("Global Entity roaming override missing from sublevel runtime records")

level6_records = [
    record for record in sublevel_records
    if record.get("id", "").startswith("SUBLEVEL.06.")
]
if len(level6_records) != 6 or any("outdoor dark-tundra baseline" not in record.get("text", "") for record in level6_records):
    raise RuntimeError("Level 6 project-baseline lock missing from sublevel references")

print(
    "WEB_SUBLEVEL_CANON_V2 merged 33 current Level 0-6 Fandom sublevels + 7 parent catalogs; "
    "40 difficulty profiles validated, all sublevel snapshots empty, integer parent Level authority preserved, "
    "all-Level Entity roaming override and runtime sublevel lookup installed."
)

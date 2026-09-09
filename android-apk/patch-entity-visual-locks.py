from pathlib import Path
import hashlib
import json
import re
import struct

ROOT = Path(__file__).resolve().parent
DATA = ROOT / "app/src/main/assets/knowledge/entity_visual_locks.json"
DB = ROOT / "app/src/main/assets/knowledge/knowledge_db.json"
ENGINE = ROOT / "app/src/main/java/com/rabpit/backroom/core/knowledge/KnowledgeContextEngine.kt"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
ENTITY_ASSETS = ROOT / "app/src/main/assets/entity"

EXPECTED_KEYS = (
    "hound", "clump", "duller", "deathmoth", "hostile_faceling", "false_puddle", "paintings",
    "smiler", "skin-stealer", "predatory_window", "biological_pipeline", "wretch", "cable_mimic",
    "the_beast_of_level_5", "hotel_corpse_lure", "jeff_the_killer", "jane_the_killer", "slenderman",
    "diep_minh",
)


def replace_once(source: str, old: str, new: str, label: str) -> str:
    if new in source:
        return source
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return source.replace(old, new, 1)


def visual_id(key: str) -> str:
    return "ENTITY.VISUAL." + key.replace("-", "_").upper()


def png_size(raw: bytes) -> tuple[int, int]:
    if len(raw) < 24 or raw[:8] != b"\x89PNG\r\n\x1a\n" or raw[12:16] != b"IHDR":
        raise RuntimeError("Entity visual asset is not a valid PNG")
    return struct.unpack(">II", raw[16:24])


if not DATA.is_file() or not DB.is_file() or not ENGINE.is_file() or not MAIN.is_file():
    raise RuntimeError("Entity visual-lock source/runtime file missing")

payload = json.loads(DATA.read_text(encoding="utf-8"))
items = payload.get("records")
if not isinstance(items, list):
    raise RuntimeError("entity_visual_locks.json records must be an array")

keys = [str(item.get("key", "")).strip() for item in items]
if tuple(keys) != EXPECTED_KEYS:
    raise RuntimeError("Entity visual-lock keys/order do not match canonical runtime Entity keys")
if len(set(keys)) != len(keys):
    raise RuntimeError("Duplicate Entity visual-lock key")

asset_records = []
for item in items:
    key = str(item["key"]).strip()
    display = str(item.get("displayName", key)).strip()
    visual = str(item.get("visual", "")).strip()
    expected_hash = str(item.get("sha256", "")).lower()
    expected_width = int(item.get("width", 0))
    expected_height = int(item.get("height", 0))
    asset = ENTITY_ASSETS / f"{key}.png"
    if not asset.is_file():
        raise RuntimeError("Entity visual-lock asset missing: " + asset.name)
    raw = asset.read_bytes()
    actual_hash = hashlib.sha256(raw).hexdigest()
    actual_width, actual_height = png_size(raw)
    if actual_hash != expected_hash:
        raise RuntimeError(
            f"Entity visual-lock sprite drift for {key}: expected {expected_hash}, got {actual_hash}. "
            "Review the new PNG and update its Visual Lock deliberately."
        )
    if (actual_width, actual_height) != (expected_width, expected_height):
        raise RuntimeError(
            f"Entity visual-lock dimensions drift for {key}: "
            f"expected {expected_width}x{expected_height}, got {actual_width}x{actual_height}"
        )
    if len(visual) < 90 or len(visual) > 700:
        raise RuntimeError(f"Entity visual-lock description length out of range for {key}: {len(visual)}")
    if re.search(r"\b(?:sprite|asset|sha256|png)\b", visual, flags=re.I):
        raise RuntimeError("Entity Visual Lock must be narration-only and cannot leak implementation metadata: " + key)
    asset_records.append({
        "id": visual_id(key),
        "domain": "ENTITY",
        "kind": "asset-visual-lock",
        "text": visual,
        "source": {
            "document": f"android-apk/app/src/main/assets/entity/{key}.png",
            "anchor": f"sha256:{actual_hash}; {actual_width}x{actual_height}",
        },
        "authority": "ASSET_VISUAL_LOCK",
        "mutability": "IMMUTABLE",
        "priority": 18,
        "tags": [key, display.lower(), "entity visual", "visual lock"],
        "references": ["ENTITY.GLOBAL_HARD_LOCK"],
        "affordances": ["visual_reference"],
    })

knowledge = json.loads(DB.read_text(encoding="utf-8"))
records = [
    record for record in knowledge.get("records", [])
    if not str(record.get("id", "")).startswith("ENTITY.VISUAL.")
]
records.extend(asset_records)
knowledge["records"] = records
DB.write_text(json.dumps(knowledge, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

final_ids = [str(record.get("id", "")) for record in knowledge["records"]]
for item in asset_records:
    if final_ids.count(item["id"]) != 1:
        raise RuntimeError("Entity visual-lock DB merge failed: " + item["id"])

engine = ENGINE.read_text(encoding="utf-8")

builder_anchor = "  private class Builder(\n"
engine_helpers = r'''  private fun visualRecordId(rawKey: String): String {
    val key = rawKey.trim().lowercase(Locale.ROOT)
    if (key.isBlank()) return ""
    return "ENTITY.VISUAL." + key.replace("-", "_").uppercase(Locale.ROOT)
  }

  private fun firstAppearanceEntityKey(rolls: JSONObject): String {
    val encounter = rolls.optJSONObject("entityEncounter")
    if (encounter == null || !encounter.optBoolean("success", false)) return ""
    val direct = rolls.optString("roamingEntityKey", "").trim()
    if (direct.isNotEmpty()) return direct
    return rolls.optJSONArray("entityEncounterKeys")?.optString(0, "")?.trim().orEmpty()
  }

  @JvmStatic
  fun visualForEntity(context: Context, rawKey: String): String {
    val id = visualRecordId(rawKey)
    if (id.isBlank()) return ""
    return database(context.applicationContext).records[id]?.text.orEmpty()
  }

  @JvmStatic
  fun firstAppearanceVisual(context: Context, rollsJson: String): String {
    val rolls = runCatching { JSONObject(rollsJson) }.getOrElse { return "" }
    val key = firstAppearanceEntityKey(rolls)
    if (key.isBlank()) return ""
    return visualForEntity(context, key)
  }

'''
if "fun firstAppearanceVisual(context: Context, rollsJson: String): String" not in engine:
    engine = replace_once(engine, builder_anchor, engine_helpers + builder_anchor, "Entity visual engine helpers")
elif "fun visualForEntity(context: Context, rawKey: String): String" not in engine:
    first_visual_anchor = "  @JvmStatic\n  fun firstAppearanceVisual(context: Context, rollsJson: String): String {\n"
    visual_lookup = r'''  @JvmStatic
  fun visualForEntity(context: Context, rawKey: String): String {
    val id = visualRecordId(rawKey)
    if (id.isBlank()) return ""
    return database(context.applicationContext).records[id]?.text.orEmpty()
  }

'''
    engine = replace_once(engine, first_visual_anchor, visual_lookup + first_visual_anchor, "Entity visual direct lookup")
    engine = engine.replace(
        '    return database(context.applicationContext).records[visualRecordId(key)]?.text.orEmpty()\n',
        '    return visualForEntity(context, key)\n',
        1,
    )

build_old = "      addSceneAffordances()\n      addStateDrivenRecords()\n      expandReferences()\n"
build_new = "      addSceneAffordances()\n      addStateDrivenRecords()\n      addCurrentEntityVisual()\n      expandReferences()\n"
engine = replace_once(engine, build_old, build_new, "Entity visual context routing call")

state_anchor = "    private fun addStateDrivenRecords() {\n"
state_helper = r'''    private fun addCurrentEntityVisual() {
      val keys = linkedSetOf<String>()
      val combat = state.optJSONObject("combat")
      if (combat != null && combat.optBoolean("active", false)) {
        combat.optString("entityKey", "").trim().takeIf { it.isNotEmpty() }?.let(keys::add)
      }
      state.optJSONObject("flags")
        ?.optString("entityEncounterKey", "")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let(keys::add)
      firstAppearanceEntityKey(rolls).takeIf { it.isNotEmpty() }?.let(keys::add)

      keys.forEach { key ->
        val id = visualRecordId(key)
        if (id.isNotEmpty()) add(id, "active/new Entity PNG visual lock: $key")
      }
    }

'''
if "private fun addCurrentEntityVisual()" not in engine:
    engine = replace_once(engine, state_anchor, state_helper + state_anchor, "Entity visual state routing helper")

for marker in (
    "fun visualForEntity(context: Context, rawKey: String): String",
    "fun firstAppearanceVisual(context: Context, rollsJson: String): String",
    "private fun addCurrentEntityVisual()",
    'add(id, "active/new Entity PNG visual lock: $key")',
    "addCurrentEntityVisual()\n      expandReferences()",
):
    if marker not in engine:
        raise RuntimeError("Entity visual KnowledgeContextEngine contract missing: " + marker)
ENGINE.write_text(engine, encoding="utf-8")

main = MAIN.read_text(encoding="utf-8")

writer_match = re.search(r"(?m)^(\s*)private\s+String\s+writerPrompt\s*\(", main)
if not writer_match:
    raise RuntimeError("writerPrompt not found for Entity visual narration lock")


def method_bounds(source: str, start: int) -> tuple[int, int]:
    open_brace = source.find("{", start)
    if open_brace < 0:
        raise RuntimeError("writerPrompt opening brace missing")
    depth = 0
    i = open_brace
    state = "code"
    escaped = False
    while i < len(source):
        ch = source[i]
        nxt = source[i + 1] if i + 1 < len(source) else ""
        if state == "string":
            if escaped:
                escaped = False
            elif ch == "\\":
                escaped = True
            elif ch == '"':
                state = "code"
        elif state == "char":
            if escaped:
                escaped = False
            elif ch == "\\":
                escaped = True
            elif ch == "'":
                state = "code"
        elif state == "line_comment":
            if ch == "\n":
                state = "code"
        elif state == "block_comment":
            if ch == "*" and nxt == "/":
                state = "code"
                i += 1
        else:
            if ch == '"':
                state = "string"
            elif ch == "'":
                state = "char"
            elif ch == "/" and nxt == "/":
                state = "line_comment"
                i += 1
            elif ch == "/" and nxt == "*":
                state = "block_comment"
                i += 1
            elif ch == "{":
                depth += 1
            elif ch == "}":
                depth -= 1
                if depth == 0:
                    return start, i + 1
        i += 1
    raise RuntimeError("writerPrompt closing brace missing")


if "ENTITY VISUAL NARRATION HARD LOCK:" not in main:
    start, end = method_bounds(main, writer_match.start())
    method = main[start:end]
    return_match = re.search(r"(?m)^([ \t]*)return\s+", method)
    if not return_match:
        raise RuntimeError("writerPrompt return expression missing for Entity visual narration")
    indent = return_match.group(1)
    continuation = indent + "  "
    prefix = (
        '"ENTITY VISUAL NARRATION HARD LOCK: mọi record <ENTITY.VISUAL.*> trong KNOWLEDGE PACKET là ngoại hình bất biến lấy trực tiếp từ PNG local đang đóng gói. Không thay bằng web-reference, không tự thêm đặc điểm không nhìn thấy trong lock. " +\n'
        + continuation
        + '"Khi một Entity vừa được roll để xuất hiện, runtime sẽ ghép nguyên đoạn Visual Lock của đúng canonical key vào đầu reply Game Master; phần văn xuôi còn lại phải tiếp nối tự nhiên và không mô tả mâu thuẫn hoặc lặp lại nguyên đoạn đó. Khi Entity tiếp tục hiện diện ở các lượt sau, giữ đúng các đặc điểm đã khóa và chỉ nhắc lại chi tiết thị giác khi góc nhìn, ánh sáng hoặc hành động làm chúng hữu ích. " +\n'
        + continuation
    )
    method = method[:return_match.end()] + prefix + method[return_match.end():]
    main = main[:start] + method + main[end:]

helper_anchor = "  private String writerPrompt(JSONObject before, String action, JSONObject rolls, JSONArray auditFeedback) throws Exception {\n"
java_helper = r'''  private String enforceEntityFirstAppearanceVisual(JSONObject rolls, String reply) throws Exception {
    String visual = com.rabpit.backroom.core.knowledge.KnowledgeContextEngine.firstAppearanceVisual(
      MainActivity.this, rolls.toString()).trim();
    if (visual.isEmpty() || reply.contains(visual)) return reply;
    return visual + "\n\n" + reply;
  }

  private JSONObject enforceCombatEntityVisualTransition(String beforeStateJson, JSONObject combatResult) throws Exception {
    if (combatResult == null || !combatResult.optBoolean("handled", false)) return combatResult;
    JSONObject before = new JSONObject(beforeStateJson);
    JSONObject beforeCombat = before.optJSONObject("combat");
    String beforeKey = beforeCombat != null && beforeCombat.optBoolean("active", false)
      ? beforeCombat.optString("entityKey", "").trim() : "";
    JSONObject resultState = combatResult.optJSONObject("state");
    JSONObject afterCombat = resultState != null ? resultState.optJSONObject("combat") : null;
    if (afterCombat == null || !afterCombat.optBoolean("active", false)) return combatResult;
    String afterKey = afterCombat.optString("entityKey", "").trim();
    if (afterKey.isEmpty() || afterKey.equals(beforeKey)) return combatResult;

    String visual = com.rabpit.backroom.core.knowledge.KnowledgeContextEngine.visualForEntity(
      MainActivity.this, afterKey).trim();
    if (visual.isEmpty()) return combatResult;
    String reply = combatResult.optString("reply", "");
    if (reply.contains(visual)) return combatResult;
    String enriched = reply.isEmpty() ? visual : reply + "\n\n" + visual;
    combatResult.put("reply", enriched);

    JSONArray log = resultState.optJSONArray("log");
    if (log != null && log.length() > 0) {
      JSONObject last = log.optJSONObject(log.length() - 1);
      if (last != null && "gm".equals(last.optString("role", ""))) last.put("text", enriched);
    }
    return combatResult;
  }

'''
if "private String enforceEntityFirstAppearanceVisual(JSONObject rolls, String reply)" not in main:
    main = replace_once(main, helper_anchor, java_helper + helper_anchor, "Entity visual reply helper")
elif "private JSONObject enforceCombatEntityVisualTransition(String beforeStateJson, JSONObject combatResult)" not in main:
    combat_helper = r'''  private JSONObject enforceCombatEntityVisualTransition(String beforeStateJson, JSONObject combatResult) throws Exception {
    if (combatResult == null || !combatResult.optBoolean("handled", false)) return combatResult;
    JSONObject before = new JSONObject(beforeStateJson);
    JSONObject beforeCombat = before.optJSONObject("combat");
    String beforeKey = beforeCombat != null && beforeCombat.optBoolean("active", false)
      ? beforeCombat.optString("entityKey", "").trim() : "";
    JSONObject resultState = combatResult.optJSONObject("state");
    JSONObject afterCombat = resultState != null ? resultState.optJSONObject("combat") : null;
    if (afterCombat == null || !afterCombat.optBoolean("active", false)) return combatResult;
    String afterKey = afterCombat.optString("entityKey", "").trim();
    if (afterKey.isEmpty() || afterKey.equals(beforeKey)) return combatResult;

    String visual = com.rabpit.backroom.core.knowledge.KnowledgeContextEngine.visualForEntity(
      MainActivity.this, afterKey).trim();
    if (visual.isEmpty()) return combatResult;
    String reply = combatResult.optString("reply", "");
    if (reply.contains(visual)) return combatResult;
    String enriched = reply.isEmpty() ? visual : reply + "\n\n" + visual;
    combatResult.put("reply", enriched);

    JSONArray log = resultState.optJSONArray("log");
    if (log != null && log.length() > 0) {
      JSONObject last = log.optJSONObject(log.length() - 1);
      if (last != null && "gm".equals(last.optString("role", ""))) last.put("text", enriched);
    }
    return combatResult;
  }

'''
    main = replace_once(main, helper_anchor, combat_helper + helper_anchor, "Queued Entity visual transition helper")

first_old = '          if (reply.isEmpty()) throw new Exception("AI trả về phản hồi rỗng, lượt này không được ghi.");\n'
first_new = first_old + (
    '          reply = enforceEntityFirstAppearanceVisual(rolls, reply);\n'
    '          generated.put("reply", reply);\n'
)
main = replace_once(main, first_old, first_new, "Entity visual first-pass enforcement")

repair_old = '            if (reply.isEmpty()) throw new Exception("AI repair trả phản hồi rỗng; state không được thay đổi.");\n'
repair_new = repair_old + (
    '            reply = enforceEntityFirstAppearanceVisual(rolls, reply);\n'
    '            generated.put("reply", reply);\n'
)
main = replace_once(main, repair_old, repair_new, "Entity visual repair enforcement")

combat_old = '          JSONObject combatResult = new JSONObject(requireGameCore().processCombat(stateJson, actionKind, action));\n'
combat_new = combat_old + '          combatResult = enforceCombatEntityVisualTransition(stateJson, combatResult);\n'
main = replace_once(main, combat_old, combat_new, "Queued Entity combat visual enforcement")

for marker in (
    "ENTITY VISUAL NARRATION HARD LOCK:",
    "enforceEntityFirstAppearanceVisual(rolls, reply)",
    "KnowledgeContextEngine.firstAppearanceVisual",
    "enforceCombatEntityVisualTransition(stateJson, combatResult)",
    "KnowledgeContextEngine.visualForEntity",
):
    if marker not in main:
        raise RuntimeError("Entity visual GM narration contract missing: " + marker)
MAIN.write_text(main, encoding="utf-8")

if len(asset_records) != 19:
    raise RuntimeError("Expected exactly 19 Entity PNG Visual Locks")
hash_by_key = {item["key"]: item["sha256"] for item in items}
if hash_by_key["the_beast_of_level_5"] != hash_by_key["hotel_corpse_lure"]:
    raise RuntimeError("The Beast / Hotel Corpse Lure sprite relationship changed; review both Visual Locks")

print(
    "Entity PNG Visual Locks installed: 19/19 canonical Entity sprites hash-locked, "
    "state/roll routed into KNOWLEDGE_PACKET, first appearance narrated, queued Entity transitions narrated."
)

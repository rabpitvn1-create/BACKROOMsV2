from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
FACADE = ROOT / "app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
FINALIZER = ROOT / "patch-inventory-v4-final.py"

facade = FACADE.read_text(encoding="utf-8")

# Player prose must never mutate Inventory. The typed ActionRuntime starts before processRule(), so
# reject manual pickup without leaving a stale action session behind.
pickup_block = '''    if (isDirectPlayerPickupAction(action) || interpreted.candidates.any { it.intent == GameIntent.PICKUP_ITEM }) {
      abortAction("player_pickup_unavailable")
      val current = repository.load()
      val result = syncLegacy(legacy, current, incrementTurn = false)
      val reply = validationReply("player_pickup_unavailable")
      appendLog(result, action, reply)
      logger.log(PipelineLogEvent("REJECT", turnId = turnId, details = mapOf("reason" to "player_pickup_unavailable")))
      return response(true, result, "player_pickup_unavailable", "validation_rejected", reply)
    }
'''

if pickup_block not in facade:
    # Remove an older V4 compatibility guard if a previous generated layer installed it.
    old_start = facade.find('    if (isDirectPlayerPickupAction(action) || interpreted.candidates.any { it.intent == GameIntent.PICKUP_ITEM }) {')
    if old_start >= 0:
        old_end = facade.find('\n    }', old_start)
        if old_end < 0:
            raise RuntimeError("Inventory V4 compat: malformed pickup guard")
        facade = facade[:old_start] + facade[old_end + len('\n    }'):]

    method_start = facade.find("  fun processRule(")
    method_end = facade.find("\n  fun ", method_start + 4)
    if method_start < 0:
        raise RuntimeError("Inventory V4 compat: processRule missing")
    if method_end < 0:
        method_end = len(facade)

    insert_candidates = (
        "    // Restore is lore/narrative-only.",
        "    if (interpreted.candidates.any { it.intent == GameIntent.OMNIVAULT_RESTORE }) {",
        "    if (interpreted.candidates.any { it.intent == GameIntent.NO_ACTION || it.confidence != IntentConfidence.HIGH }) {",
        "    val resolvedCommands = interpreted.candidates.mapIndexedNotNull",
    )
    insert_at = -1
    for marker in insert_candidates:
        absolute = facade.find(marker, method_start, method_end)
        if absolute >= 0:
            insert_at = absolute
            break
    if insert_at < 0:
        raise RuntimeError("Inventory V4 compat: processRule insertion anchor missing")
    facade = facade[:insert_at] + pickup_block + "\n" + facade[insert_at:]

# Keep every save/follower/backfill rule installed by the historical patch stack. Wrap the final
# generated loadOrMigrate implementation instead of replacing it with a stale baseline body.
if "private fun loadOrMigratePreV4(" not in facade:
    load_start = facade.find("  private fun loadOrMigrate(")
    if load_start < 0:
        raise RuntimeError("Inventory V4 compat: loadOrMigrate missing")
    following = list(re.finditer(r"\n  private fun [A-Za-z0-9_]+\(", facade[load_start + 4:]))
    if not following:
        raise RuntimeError("Inventory V4 compat: loadOrMigrate end boundary missing")
    load_end = load_start + 4 + following[0].start()
    legacy_method = facade[load_start:load_end]
    renamed = legacy_method.replace(
        "  private fun loadOrMigrate(",
        "  private fun loadOrMigratePreV4(",
        1,
    )
    wrapper = '''  private fun loadOrMigrate(legacy: JSONObject): GameState {
    val loaded = loadOrMigratePreV4(legacy)
    val normalized = InventoryV4State.normalize(loaded)
    if (normalized != loaded) repository.save(normalized)
    return normalized
  }

'''
    facade = facade[:load_start] + wrapper + renamed + facade[load_end:]

FACADE.write_text(facade, encoding="utf-8")

main = MAIN.read_text(encoding="utf-8")

# Knowledge Context Builder owns the final writerPrompt(). Add the catalog/reward contract there,
# preserving ActionRuntime, context routing, audits and repair semantics.
if "String itemCatalogDirective =" not in main:
    writer_signature = "  private String writerPrompt(JSONObject before, String action, JSONObject rolls, JSONArray auditFeedback) throws Exception {"
    writer_start = main.find(writer_signature)
    if writer_start < 0:
        raise RuntimeError("Inventory V4 compat: final writerPrompt method missing")
    writer_end = main.find("\n  private ", writer_start + len(writer_signature))
    if writer_end < 0:
        raise RuntimeError("Inventory V4 compat: writerPrompt end boundary missing")
    return_pos = main.find("    return ", writer_start, writer_end)
    if return_pos < 0:
        raise RuntimeError("Inventory V4 compat: writerPrompt return anchor missing")
    directive = '''    String itemCatalogDirective = "DANH MỤC VẬT PHẨM HARD LOCK: chỉ các vật phẩm sau mới được tạo trong kho đồ: " + ItemCatalog.promptCatalog() + ". Vật ngoài danh mục chỉ là bối cảnh, không được tự tạo ID hay thêm vào kho đồ. Khi Kai phát hiện hoặc nhận một vật phẩm hợp lệ từ loot hoặc phần thưởng đã được xác nhận, thêm trực tiếp vật phẩm đó vào kho đồ trong cùng lượt; không viết bước nhặt, lượm, cúi xuống lấy hoặc chờ người chơi loot. ";\n'''
    main = main[:return_pos] + directive + main[return_pos:]
    return_pos += len(directive)
    main = main[:return_pos] + main[return_pos:].replace("    return ", "    return itemCatalogDirective + ", 1)

# Discovery/reward is acquisition. The historical authority gate required a pickup verb in player
# prose before accepting a new item, which conflicts with Inventory V4. A successful authoritative
# loot/reward roll now permits the GM to propose the item directly; the Kotlin ItemCatalog still
# performs the final whitelist check before authoritative commit.
legacy_reward_gate = '''        else if (acquisitionIntent(action)) {
          if (madGod) allowedNew = madGodAlreadySpawned && establishedStructured;
          else if (almond) allowedNew = establishedStructured || rollSuccess(rolls, "almondWater");
          else if (containsAny(action, "copy", "sao chép")) allowedNew = establishedStructured;
          else allowedNew = establishedStructured || rollSuccess(rolls, "loot");
        }
'''
reward_gate = '''        else if (madGod) {
          allowedNew = madGodAlreadySpawned && establishedStructured && acquisitionIntent(action);
        } else if (almond) {
          allowedNew = rollSuccess(rolls, "almondWater") || (acquisitionIntent(action) && establishedStructured);
        } else if (containsAny(action, "copy", "sao chép")) {
          allowedNew = acquisitionIntent(action) && establishedStructured;
        } else {
          allowedNew = rollSuccess(rolls, "loot") || (acquisitionIntent(action) && establishedStructured);
        }
'''
if reward_gate not in main:
    if main.count(legacy_reward_gate) != 1:
        raise RuntimeError(f"Inventory V4 compat: final reward authority gate expected once, found {main.count(legacy_reward_gate)}")
    main = main.replace(legacy_reward_gate, reward_gate, 1)

MAIN.write_text(main, encoding="utf-8")

# The finalizer was initially written against checked-in baseline methods. Rewrite only its brittle
# assumptions at runtime, leaving the final generated release architecture intact.
finalizer = FINALIZER.read_text(encoding="utf-8")
brittle_load_line = 'facade = replace_once(facade, load_old, load_new, "Inventory V4 load normalization")'
robust_load_check = '''if "InventoryV4State.normalize(loaded)" not in facade or "loadOrMigratePreV4(legacy)" not in facade:
    raise RuntimeError("Inventory V4 load normalization wrapper missing")'''
if robust_load_check not in finalizer:
    if finalizer.count(brittle_load_line) != 1:
        raise RuntimeError("Inventory V4 compat: finalizer load hook changed unexpectedly")
    finalizer = finalizer.replace(brittle_load_line, robust_load_check, 1)

# UI item actions are inserted after startup hardening, therefore they must use the lazy Core accessor.
finalizer = finalizer.replace(
    "          String result = gameCore.processInventoryUiAction(\n",
    "          String result = requireGameCore().processInventoryUiAction(\n",
)

# JSONObject.put throws checked JSONException in this Android API. The fallback path must not create
# a new JSONObject inside the catch block, otherwise javac rejects the bridge itself.
checked_fallback = '''          emit("backroomInventoryAction", new JSONObject()
            .put("handled", true)
            .put("applied", false)
            .put("message", "Không thể thực hiện thao tác vật phẩm.")
            .toString());
'''
safe_fallback = '''          emit("backroomInventoryAction", "{\\\"handled\\\":true,\\\"applied\\\":false,\\\"message\\\":\\\"Không thể thực hiện thao tác vật phẩm.\\\"}");
'''
if safe_fallback not in finalizer:
    if finalizer.count(checked_fallback) != 1:
        raise RuntimeError("Inventory V4 compat: Java fallback JSON anchor missing")
    finalizer = finalizer.replace(checked_fallback, safe_fallback, 1)

# UI-only prose rejection must also terminate the ActionRuntime session started by submitAction().
old_ui_gate = '''    if (uiOnlyItemIntent != null) {
      val result = syncLegacy(legacy, state, incrementTurn = false)
      val reply = validationReply("inventory_ui_required")
'''
new_ui_gate = '''    if (uiOnlyItemIntent != null) {
      abortAction("inventory_ui_required")
      val current = repository.load()
      val result = syncLegacy(legacy, current, incrementTurn = false)
      val reply = validationReply("inventory_ui_required")
'''
if new_ui_gate not in finalizer:
    if finalizer.count(old_ui_gate) != 1:
        raise RuntimeError("Inventory V4 compat: UI-only action gate anchor missing")
    finalizer = finalizer.replace(old_ui_gate, new_ui_gate, 1)

# MainActivity already contains this marker after writerPrompt injection, so the finalizer skips its
# obsolete GameBridge-local prompt rewrite.
if "String itemCatalogDirective =" not in MAIN.read_text(encoding="utf-8"):
    raise RuntimeError("Inventory V4 compat: writer catalog directive missing")
if "rollSuccess(rolls, \"loot\") || (acquisitionIntent(action) && establishedStructured)" not in MAIN.read_text(encoding="utf-8"):
    raise RuntimeError("Inventory V4 compat: direct reward gate missing")
FINALIZER.write_text(finalizer, encoding="utf-8")

print("Inventory V4 compatibility prepared: UI-only authority, generated saves, writer catalog, direct rewards and Java bridge preserved.")

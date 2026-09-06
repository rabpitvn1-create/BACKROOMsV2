from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
FACADE = ROOT / "app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
FINALIZER = ROOT / "patch-inventory-v4-final.py"

facade = FACADE.read_text(encoding="utf-8")

# The release patch stack currently removes the early processRule pickup guard while the reducer
# still rejects unauthorized PICKUP commands. Restore an explicit deterministic guard here so the
# final V4 layer can also attach the UI-only management gate without invoking Gemini.
pickup_block = '''    if (isDirectPlayerPickupAction(action) || interpreted.candidates.any { it.intent == GameIntent.PICKUP_ITEM }) {
      val result = syncLegacy(legacy, state, incrementTurn = false)
      val reply = validationReply("player_pickup_unavailable")
      appendLog(result, action, reply)
      logger.log(PipelineLogEvent("REJECT", turnId = turnId, details = mapOf("reason" to "player_pickup_unavailable")))
      return response(true, result, "player_pickup_unavailable", "validation_rejected", reply)
    }
'''

if pickup_block not in facade:
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

# Keep every load/backfill rule installed by the historical patch stack. Wrap the final generated
# loadOrMigrate implementation instead of replacing it with an older baseline implementation.
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

# The final writer no longer builds its prompt inside GameBridge. Knowledge Context Builder owns a
# dedicated writerPrompt() method. Inject the Inventory V4 directive there and preserve the entire
# existing context/audit pipeline.
main = MAIN.read_text(encoding="utf-8")
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
    MAIN.write_text(main, encoding="utf-8")

# The finalizer was initially written against the checked-in baseline loadOrMigrate body and the
# old inline writer prompt. Tell it to verify the compatibility wrappers above rather than replacing
# generated release semantics.
finalizer = FINALIZER.read_text(encoding="utf-8")
brittle_load_line = 'facade = replace_once(facade, load_old, load_new, "Inventory V4 load normalization")'
robust_load_check = '''if "InventoryV4State.normalize(loaded)" not in facade or "loadOrMigratePreV4(legacy)" not in facade:
    raise RuntimeError("Inventory V4 load normalization wrapper missing")'''
if robust_load_check not in finalizer:
    if finalizer.count(brittle_load_line) != 1:
        raise RuntimeError("Inventory V4 compat: finalizer load hook changed unexpectedly")
    finalizer = finalizer.replace(brittle_load_line, robust_load_check, 1)

# Because MainActivity already contains this marker after the writerPrompt injection, the finalizer
# deliberately skips its obsolete GameBridge-local prompt rewrite. Keep a fail-closed assertion here.
if "String itemCatalogDirective =" not in MAIN.read_text(encoding="utf-8"):
    raise RuntimeError("Inventory V4 compat: writer catalog directive missing")
FINALIZER.write_text(finalizer, encoding="utf-8")

print("Inventory V4 compatibility prepared: pickup guard, generated load semantics and final writerPrompt preserved.")

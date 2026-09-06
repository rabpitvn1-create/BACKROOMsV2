from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
FACADE = ROOT / "app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
FINALIZER = ROOT / "patch-inventory-v4-final.py"


def method_scope(text: str, signature: str) -> tuple[int, int]:
    start = text.find(signature)
    if start < 0:
        raise RuntimeError(f"Inventory V4 compat: method missing: {signature.strip()}")
    end = text.find("\n  fun ", start + len(signature))
    private_end = text.find("\n  private fun ", start + len(signature))
    candidates = [pos for pos in (end, private_end) if pos >= 0]
    return start, min(candidates) if candidates else len(text)


def replace_once_checked(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return text.replace(old, new, 1)


facade = FACADE.read_text(encoding="utf-8")

# ---------------------------------------------------------------------------
# Generated GameCoreFacade compatibility.
# Player prose may not mutate Inventory. Because typed ActionRuntime starts before
# processRule(), rejected prose must terminate the active session deterministically.
# ---------------------------------------------------------------------------
pickup_start_marker = '    if (isDirectPlayerPickupAction(action) || interpreted.candidates.any { it.intent == GameIntent.PICKUP_ITEM }) {'
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

process_start, process_end = method_scope(facade, "  fun processRule(")
process = facade[process_start:process_end]
if 'abortAction("player_pickup_unavailable")' not in process:
    old_guard = process.find(pickup_start_marker)
    insert_candidates = (
        "    // Restore is lore/narrative-only.",
        "    if (interpreted.candidates.any { it.intent == GameIntent.OMNIVAULT_RESTORE }) {",
        "    if (interpreted.candidates.any { it.intent == GameIntent.NO_ACTION || it.confidence != IntentConfidence.HIGH }) {",
        "    val resolvedCommands = interpreted.candidates.mapIndexedNotNull",
    )
    insert_at = -1
    for marker in insert_candidates:
        pos = process.find(marker)
        if pos >= 0:
            insert_at = pos
            break
    if insert_at < 0:
        raise RuntimeError("Inventory V4 compat: processRule insertion anchor missing")
    if old_guard >= 0:
        # Historical pickup guard lives immediately before the next authority stage. Replace the
        # complete region up to that stable stage instead of matching its generated body byte-for-byte.
        process = process[:old_guard] + pickup_block + "\n" + process[insert_at:]
    else:
        process = process[:insert_at] + pickup_block + "\n" + process[insert_at:]
    facade = facade[:process_start] + process + facade[process_end:]

# Preserve every follower/save/backfill installed by the historical patch chain. Normalize V4 by
# wrapping the final generated load method rather than replacing it with an older baseline body.
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

# ---------------------------------------------------------------------------
# Final generated MainActivity compatibility.
# ---------------------------------------------------------------------------
main = MAIN.read_text(encoding="utf-8")

# Knowledge Context Builder owns writerPrompt(). Inject the catalog contract inside that method only,
# preserving ActionRuntime, Context Builder, critic and repair behavior.
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

# Discovery/reward is acquisition. Do not match the old authority block byte-for-byte because
# upstream hardening intentionally rewrites it. Scope the rewrite to inventory_upsert and replace the
# generated allowedNew region between two semantic anchors.
upsert_start = main.find('      if (type.equals("inventory_upsert")) {')
if upsert_start < 0:
    raise RuntimeError("Inventory V4 compat: inventory_upsert authority block missing")
upsert_end = main.find('      if (type.equals("inventory_remove")) {', upsert_start)
if upsert_end < 0:
    raise RuntimeError("Inventory V4 compat: inventory_upsert end boundary missing")
upsert = main[upsert_start:upsert_end]
reward_marker = 'rollSuccess(rolls, "loot") || (acquisitionIntent(action) && establishedStructured)'
if reward_marker not in upsert:
    allowed_start = upsert.find("        boolean allowedNew =")
    apply_start = upsert.find("        if (existing >= 0) inventory.put", allowed_start)
    if allowed_start < 0 or apply_start < 0:
        raise RuntimeError("Inventory V4 compat: structural reward authority anchors missing")
    authority = '''        boolean allowedNew = false;
        JSONObject beforeFlagsForItem = before.optJSONObject("flags");
        JSONObject beforeMadGodForItem = beforeFlagsForItem != null ? beforeFlagsForItem.optJSONObject("madGod") : null;
        JSONObject explorationForItem = beforeFlagsForItem != null ? beforeFlagsForItem.optJSONObject("exploration") : null;
        JSONObject omnivaultForItem = beforeFlagsForItem != null ? beforeFlagsForItem.optJSONObject("omnivault") : null;
        boolean establishedStructured = false;
        if (explorationForItem != null) establishedStructured = lower(explorationForItem.toString()).contains(lower(name));
        if (!establishedStructured && omnivaultForItem != null) establishedStructured = lower(omnivaultForItem.toString()).contains(lower(name));
        if (!establishedStructured && beforeMadGodForItem != null) establishedStructured = lower(beforeMadGodForItem.toString()).contains(lower(name));
        boolean madGodAlreadySpawned = beforeMadGodForItem != null && beforeMadGodForItem.optBoolean("spawned", false);
        if (existing >= 0) {
          allowedNew = true;
        } else if (madGod) {
          allowedNew = madGodAlreadySpawned && establishedStructured && acquisitionIntent(action);
        } else if (almond) {
          allowedNew = rollSuccess(rolls, "almondWater") || (acquisitionIntent(action) && establishedStructured);
        } else if (containsAny(action, "copy", "sao chép")) {
          allowedNew = acquisitionIntent(action) && establishedStructured;
        } else {
          allowedNew = rollSuccess(rolls, "loot") || (acquisitionIntent(action) && establishedStructured);
        }
'''
    upsert = upsert[:allowed_start] + authority + upsert[apply_start:]
    main = main[:upsert_start] + upsert + main[upsert_end:]

MAIN.write_text(main, encoding="utf-8")

# ---------------------------------------------------------------------------
# Prepare the checked-in V4 finalizer for the generated release sources. These rewrites target text
# owned by the V4 finalizer itself, not historical generated Java/Kotlin formatting.
# ---------------------------------------------------------------------------
finalizer = FINALIZER.read_text(encoding="utf-8")

# The finalizer's original load replacement was written for the checked-in baseline. Verify the
# wrapper installed above instead of replacing final generated migration/backfill behavior.
brittle_load_line = 'facade = replace_once(facade, load_old, load_new, "Inventory V4 load normalization")'
robust_load_check = '''if "InventoryV4State.normalize(loaded)" not in facade or "loadOrMigratePreV4(legacy)" not in facade:
    raise RuntimeError("Inventory V4 load normalization wrapper missing")'''
if robust_load_check not in finalizer:
    finalizer = replace_once_checked(finalizer, brittle_load_line, robust_load_check, "Inventory V4 compat: finalizer load hook")

# Make the finalizer expect the ActionRuntime-safe pickup guard that is actually installed in the
# generated facade. This also makes the subsequent UI-lock insertion operate on the same authority.
finalizer_pickup_old = '''pickup_block = ''' + "'''" + '''    if (isDirectPlayerPickupAction(action) || interpreted.candidates.any { it.intent == GameIntent.PICKUP_ITEM }) {
      val result = syncLegacy(legacy, state, incrementTurn = false)
      val reply = validationReply("player_pickup_unavailable")
      appendLog(result, action, reply)
      logger.log(PipelineLogEvent("REJECT", turnId = turnId, details = mapOf("reason" to "player_pickup_unavailable")))
      return response(true, result, "player_pickup_unavailable", "validation_rejected", reply)
    }
''' + "'''"
finalizer_pickup_new = "pickup_block = " + repr(pickup_block)
if 'abortAction("player_pickup_unavailable")' not in finalizer[finalizer.find("pickup_block ="):finalizer.find("ui_lock =", finalizer.find("pickup_block ="))]:
    finalizer = replace_once_checked(finalizer, finalizer_pickup_old, finalizer_pickup_new, "Inventory V4 compat: finalizer pickup guard")

# UI-only prose rejection also terminates the typed ActionRuntime session. Patch the string that the
# finalizer will append, not the already-generated facade after finalizer execution.
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
    finalizer = replace_once_checked(finalizer, old_ui_gate, new_ui_gate, "Inventory V4 compat: UI-only action gate")

# Startup hardening makes GameCore lazy, so the JS bridge must use the accessor.
finalizer = finalizer.replace(
    "          String result = gameCore.processInventoryUiAction(\n",
    "          String result = requireGameCore().processInventoryUiAction(\n",
)

# JSONObject.put throws checked JSONException on this Android API. A catch fallback must be a literal
# payload so javac cannot fail while constructing the error response.
checked_fallback_pattern = re.compile(
    r'''          emit\("backroomInventoryAction", new JSONObject\(\)\s*\n'''
    r'''\s*\.put\("handled", true\)\s*\n'''
    r'''\s*\.put\("applied", false\)\s*\n'''
    r'''\s*\.put\("message", "Không thể thực hiện thao tác vật phẩm\."\)\s*\n'''
    r'''\s*\.toString\(\)\);\s*\n'''
)
safe_fallback = '          emit("backroomInventoryAction", "{\\\"handled\\\":true,\\\"applied\\\":false,\\\"message\\\":\\\"Không thể thực hiện thao tác vật phẩm.\\\"}");\n'
if safe_fallback not in finalizer:
    finalizer, count = checked_fallback_pattern.subn(safe_fallback, finalizer, count=1)
    if count != 1:
        raise RuntimeError(f"Inventory V4 compat: Java fallback JSON structural anchor expected once, found {count}")

# writerPrompt already contains the catalog marker, so the finalizer deliberately skips its obsolete
# inline-GameBridge prompt path.
current_main = MAIN.read_text(encoding="utf-8")
if "String itemCatalogDirective =" not in current_main:
    raise RuntimeError("Inventory V4 compat: writer catalog directive missing")
if reward_marker not in current_main:
    raise RuntimeError("Inventory V4 compat: direct reward gate missing")
if 'abortAction("player_pickup_unavailable")' not in FACADE.read_text(encoding="utf-8"):
    raise RuntimeError("Inventory V4 compat: ActionRuntime-safe pickup guard missing")

FINALIZER.write_text(finalizer, encoding="utf-8")
print("Inventory V4 compatibility stabilized: structural reward anchors, ActionRuntime-safe UI authority, generated saves and Java bridge preserved.")

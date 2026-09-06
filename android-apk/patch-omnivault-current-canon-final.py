from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
OMNIVAULT = CORE / "OmnivaultEngine.kt"
FACADE = CORE / "GameCoreFacade.kt"
INVENTORY_UI = CORE / "InventoryUiActions.kt"
ITEM_CATALOG = CORE / "ItemCatalog.kt"
V4_TEST = TESTS / "InventoryV4ArchitectureTest.kt"

for required in (OMNIVAULT, FACADE, INVENTORY_UI, ITEM_CATALOG, MAIN, V4_TEST):
    if not required.is_file():
        raise RuntimeError("Omnivault current-canon source missing: " + required.name)


def method_scope(text: str, signature: str) -> tuple[int, int]:
    start = text.find(signature)
    if start < 0:
        raise RuntimeError("Omnivault current-canon method missing: " + signature.strip())
    candidates = []
    for marker in ("\n  fun ", "\n  private fun ", "\n  companion object"):
        pos = text.find(marker, start + len(signature))
        if pos >= 0:
            candidates.append(pos)
    return start, min(candidates) if candidates else len(text)


# ---------------------------------------------------------------------------
# Runtime authority. Scan/Copy remain in legacy enums only for save/model compatibility,
# but they are retired capabilities and can never mutate authoritative state.
# ---------------------------------------------------------------------------
omnivault = OMNIVAULT.read_text(encoding="utf-8")
lock = '''    if (command.operation == OmnivaultCommand.Operation.SCAN || command.operation == OmnivaultCommand.Operation.COPY) {
      return invalid(state, "omnivault_operation_retired")
    }
'''
if '"omnivault_operation_retired"' not in omnivault:
    owner_anchor = '    if (command.actorId != KAI_ID) return invalid(state, "omnivault_owner_only")\n'
    if owner_anchor not in omnivault:
        raise RuntimeError("Omnivault owner authority anchor missing")
    omnivault = omnivault.replace(owner_anchor, owner_anchor + lock, 1)
OMNIVAULT.write_text(omnivault, encoding="utf-8")

# ---------------------------------------------------------------------------
# GameCore authority. Direct prose asking for Scan/Copy is rejected before turn advance,
# and Gemini candidate inventory deltas are locked for the same retired intents.
# ---------------------------------------------------------------------------
facade = FACADE.read_text(encoding="utf-8")
process_start, process_end = method_scope(facade, "  fun processRule(")
process = facade[process_start:process_end]
if 'retiredOmnivaultIntent' not in process:
    anchor = process.find("    // Player text never has authority")
    if anchor < 0:
        anchor = process.find("    if (isDirectPlayerPickupAction(action)")
    if anchor < 0:
        raise RuntimeError("Omnivault processRule authority insertion anchor missing")
    abort_line = '      abortAction("omnivault_operation_retired")\n' if 'abortAction(' in facade else ''
    gate = '''    val retiredOmnivaultIntent = interpreted.candidates.any {
      it.intent == GameIntent.OMNIVAULT_SCAN || it.intent == GameIntent.OMNIVAULT_COPY
    }
    if (retiredOmnivaultIntent) {
''' + abort_line + '''      val current = repository.load()
      val result = syncLegacy(legacy, current, incrementTurn = false)
      val reply = validationReply("omnivault_operation_retired")
      appendLog(result, action, reply)
      logger.log(PipelineLogEvent("REJECT", turnId = turnId, details = mapOf("reason" to "omnivault_operation_retired")))
      return response(true, result, "omnivault_operation_retired", "validation_rejected", reply)
    }

'''
    process = process[:anchor] + gate + process[anchor:]
    facade = facade[:process_start] + process + facade[process_end:]

# Lock candidate inventory mutation for retired Omnivault intents. This is the second authority
# barrier in case a remote writer still proposes an inventory_upsert for old Copy prose.
if "GameIntent.OMNIVAULT_COPY in actionIntents" not in facade:
    pattern = re.compile(r"(\s+val inventoryLocked = [^\n]+)")
    match = pattern.search(facade)
    if not match:
        raise RuntimeError("Omnivault Gemini inventory lock anchor missing")
    replacement = match.group(1) + " || GameIntent.OMNIVAULT_SCAN in actionIntents || GameIntent.OMNIVAULT_COPY in actionIntents"
    facade = facade[:match.start()] + replacement + facade[match.end():]

# Player-facing message is Vietnamese only.
if '"omnivault_operation_retired" ->' not in facade:
    validation_start = facade.find("  private fun validationReply(reason: String): String {")
    validation_end = facade.find("\n\n  companion object", validation_start)
    if validation_start < 0 or validation_end < 0:
        raise RuntimeError("Omnivault validationReply anchors missing")
    block = facade[validation_start:validation_end]
    case_anchor = '      "restore_narrative_only" ->'
    pos = block.find(case_anchor)
    if pos < 0:
        case_anchor = '      "player_pickup_unavailable" ->'
        pos = block.find(case_anchor)
    if pos < 0:
        raise RuntimeError("Omnivault validation message insertion anchor missing")
    block = block[:pos] + '      "omnivault_operation_retired" -> "Nhẫn Vạn Tàng không còn chức năng Quét, Sao chép hoặc tạo vật phẩm."\n' + block[pos:]
    facade = facade[:validation_start] + block + facade[validation_end:]

# Remove obsolete success prose. These events are unreachable after the hard lock and should not
# remain as a player-facing promise that the old feature still exists.
facade = re.sub(r'^\s*"omnivault_scanned"\s*->\s*"[^\n]*"\s*\n', '', facade, flags=re.MULTILINE)
facade = re.sub(r'^\s*"omnivault_copied"\s*->\s*"[^\n]*"\s*\n', '', facade, flags=re.MULTILINE)
FACADE.write_text(facade, encoding="utf-8")

# ---------------------------------------------------------------------------
# Game Master contract. Omnivault cannot be used as a back door to manufacture ItemCatalog items.
# ---------------------------------------------------------------------------
main = MAIN.read_text(encoding="utf-8")
writer_signature = "  private String writerPrompt(JSONObject before, String action, JSONObject rolls, JSONArray auditFeedback) throws Exception {"
writer_start = main.find(writer_signature)
if writer_start < 0:
    raise RuntimeError("Omnivault writerPrompt method missing")
writer_end = main.find("\n  private ", writer_start + len(writer_signature))
if writer_end < 0:
    raise RuntimeError("Omnivault writerPrompt boundary missing")
writer = main[writer_start:writer_end]
if "OMNIVAULT CURRENT CANON" not in writer:
    return_pos = writer.find("    return ")
    if return_pos < 0:
        raise RuntimeError("Omnivault writerPrompt return anchor missing")
    directive = '''    String omnivaultCanonDirective = "OMNIVAULT CURRENT CANON HARD LOCK: Nhẫn Vạn Tàng chỉ lưu trữ/lấy vật đã cất và xử lý Hoàn nguyên trang bị theo canon hiện hành. Không còn Quét, Sao chép, tạo vật phẩm, tạo bản sao, Marked hoặc Nâng cấp. Tuyệt đối không dùng Omnivault để tăng số lượng Inventory, tạo đạn, tạo consumable hoặc sinh bất kỳ Item nào. ";\n'''
    writer = writer[:return_pos] + directive + writer[return_pos:]
    return_pos += len(directive)
    writer = writer[:return_pos] + writer[return_pos:].replace("    return ", "    return omnivaultCanonDirective + ", 1)
    main = main[:writer_start] + writer + main[writer_end:]
MAIN.write_text(main, encoding="utf-8")

# ---------------------------------------------------------------------------
# Regression coverage. Preserve STORE/WITHDRAW, retire SCAN/COPY, and purge legacy scan state.
# ---------------------------------------------------------------------------
test = V4_TEST.read_text(encoding="utf-8")
if "omnivaultScanAndCopyAreRetiredWithoutMutation" not in test:
    insert = r'''

  @Test fun omnivaultScanAndCopyAreRetiredWithoutMutation() {
    val state = GameState.initial()
    for (operation in listOf(OmnivaultCommand.Operation.SCAN, OmnivaultCommand.Operation.COPY)) {
      val result = StateReducer.execute(state, OmnivaultCommand(
        commandId = "retired-${operation.name}", turnId = "TURN_1", actorId = KAI_ID,
        source = CommandSource.SYSTEM, operation = operation,
        itemId = "water-bottle", itemName = "Chai nước", quantity = 1
      ))
      assertFalse(result.applied)
      assertEquals("omnivault_operation_retired", result.validation.reason)
      assertEquals(state, result.state)
      assertTrue(result.state.inventories.getValue(KAI_ID).items.isEmpty())
      assertTrue(result.state.omnivault.storedItems.isEmpty())
    }
  }

  @Test fun omnivaultStoreAndWithdrawStillMoveExistingItemsOnly() {
    var state = StateReducer.execute(GameState.initial(), ItemCommand(
      "grant-store-water", "TURN_1", KAI_ID, source = CommandSource.SYSTEM,
      operation = ItemCommand.Operation.PICKUP, itemId = "water-bottle", itemName = "Chai nước", quantity = 1
    )).state

    val stored = StateReducer.execute(state, OmnivaultCommand(
      "store-water", "TURN_1", KAI_ID, source = CommandSource.SYSTEM,
      operation = OmnivaultCommand.Operation.STORE,
      itemId = "water-bottle", itemName = "Chai nước", quantity = 1
    ))
    assertTrue(stored.applied)
    assertFalse(stored.state.inventories.getValue(KAI_ID).items.containsKey("water-bottle"))
    assertEquals(1, stored.state.omnivault.storedItems.getValue("water-bottle").quantity)

    val withdrawn = StateReducer.execute(stored.state, OmnivaultCommand(
      "withdraw-water", "TURN_1", KAI_ID, source = CommandSource.SYSTEM,
      operation = OmnivaultCommand.Operation.WITHDRAW,
      itemId = "water-bottle", itemName = "Chai nước", quantity = 1
    ))
    assertTrue(withdrawn.applied)
    assertEquals(1, withdrawn.state.inventories.getValue(KAI_ID).items.getValue("water-bottle").quantity)
    assertFalse(withdrawn.state.omnivault.storedItems.containsKey("water-bottle"))
  }

  @Test fun v4NormalizationPurgesLegacyOmnivaultScanAndMarkedState() {
    val base = GameState.initial()
    val legacy = base.copy(omnivault = base.omnivault.copy(
      scanSlots = listOf(ScanSlot(1, "water-bottle", ItemStack("water-bottle", "Chai nước"), 123L)),
      markedSourceIds = setOf("water-bottle")
    ))
    val normalized = InventoryV4State.normalize(legacy)
    assertTrue(normalized.omnivault.scanSlots.isEmpty())
    assertTrue(normalized.omnivault.markedSourceIds.isEmpty())
  }
'''
    close = test.rfind("\n}")
    if close < 0:
        raise RuntimeError("Inventory V4 test class closing brace missing")
    test = test[:close] + insert + test[close:]
V4_TEST.write_text(test, encoding="utf-8")

# Fail closed on the exact final generated runtime.
checks = {
    "OmnivaultEngine.kt": OMNIVAULT.read_text(encoding="utf-8"),
    "GameCoreFacade.kt": FACADE.read_text(encoding="utf-8"),
    "InventoryUiActions.kt": INVENTORY_UI.read_text(encoding="utf-8"),
    "ItemCatalog.kt": ITEM_CATALOG.read_text(encoding="utf-8"),
    "MainActivity.java": MAIN.read_text(encoding="utf-8"),
    "InventoryV4ArchitectureTest.kt": V4_TEST.read_text(encoding="utf-8"),
}
required_markers = (
    'return invalid(state, "omnivault_operation_retired")',
    "retiredOmnivaultIntent",
    "GameIntent.OMNIVAULT_SCAN in actionIntents",
    "GameIntent.OMNIVAULT_COPY in actionIntents",
    '"omnivault_operation_retired" -> "Nhẫn Vạn Tàng không còn chức năng Quét, Sao chép hoặc tạo vật phẩm."',
    "OMNIVAULT CURRENT CANON HARD LOCK",
    "scanSlots = emptyList()",
    "markedSourceIds = emptySet()",
    "omnivaultScanAndCopyAreRetiredWithoutMutation",
    "omnivaultStoreAndWithdrawStillMoveExistingItemsOnly",
)
combined = "\n".join(checks.values())
for marker in required_markers:
    if marker not in combined:
        raise RuntimeError("Omnivault current-canon contract missing: " + marker)

for forbidden in (
    '"omnivault_scanned" ->',
    '"omnivault_copied" ->',
):
    if forbidden in checks["GameCoreFacade.kt"]:
        raise RuntimeError("Retired Omnivault success path survived: " + forbidden)

print("Omnivault current canon applied: STORE/WITHDRAW preserved; Scan/Copy/item creation retired and legacy scan state purged.")

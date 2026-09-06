package com.rabpit.backroom.core

data class InventoryUiOutcome(
  val state: GameState,
  val applied: Boolean,
  val reason: String? = null,
  val message: String
)

object InventoryV4State {
  fun normalize(state: GameState): GameState {
    val inventories = state.inventories.mapValues { (_, inventory) -> normalizeInventory(inventory) }
    val stored = normalizeItems(state.omnivault.storedItems.values)
    return state.copy(
      inventories = inventories,
      omnivault = state.omnivault.copy(
        storedItems = stored,
        // Omnivault canon no longer has Scan/Copy/Marked. Keep legacy fields only in the
        // save schema for backward decoding, but purge their runtime contents on normalization.
        scanSlots = emptyList(),
        markedSourceIds = emptySet()
      )
    )
  }

  private fun normalizeInventory(inventory: InventoryState): InventoryState =
    inventory.copy(items = normalizeItems(inventory.items.values))

  private fun normalizeItems(items: Collection<ItemStack>): Map<String, ItemStack> {
    val result = linkedMapOf<String, ItemStack>()
    items.forEach { raw ->
      if (ItemCatalog.isLegacyResidual(raw)) return@forEach
      // ItemCatalog is the sole Inventory whitelist. Anything retired from the catalog, including
      // old ammo, legacy Kai equipment and MadGod items, is dropped during load normalization.
      val definition = ItemCatalog.resolveLegacy(raw) ?: return@forEach
      val item = ItemCatalog.canonicalize(definition, raw)
      val old = result[item.itemId]
      result[item.itemId] = if (old == null) item else old.copy(quantity = old.quantity + item.quantity)
    }
    return result
  }
}

object InventoryUiActions {
  enum class Operation { USE, TRANSFER, DISCARD }

  fun execute(
    rawState: GameState,
    actorId: String,
    operationName: String,
    requestedItemId: String,
    requestedQuantity: Int,
    targetId: String?
  ): InventoryUiOutcome {
    val state = InventoryV4State.normalize(rawState)
    val actor = state.characters[actorId]
      ?: return failure(state, "actor_unknown", "Không tìm thấy nhân vật đang thao tác kho đồ.")
    if (actorId !in state.party.memberIds || actor.presence != CharacterPresence.ACTIVE) {
      return failure(state, "actor_not_active", "Nhân vật này hiện không thể thao tác kho đồ.")
    }

    val operation = runCatching { Operation.valueOf(operationName.trim().uppercase()) }.getOrNull()
      ?: return failure(state, "inventory_operation_unknown", "Thao tác vật phẩm không hợp lệ.")
    val inventory = state.inventories[actorId] ?: InventoryState(actorId)
    val requestedDefinition = ItemCatalog.resolve(requestedItemId)
    val owned = inventory.items[requestedItemId]
      ?: requestedDefinition?.let { inventory.items[it.id] }
      ?: return failure(state, "item_not_owned", "${actor.name} không có vật phẩm này trong kho đồ.")
    val definition = ItemCatalog.resolve(owned.itemId, owned.name)
      ?: return failure(state, "item_not_in_catalog", "Vật phẩm này không có trong danh mục vật phẩm.")
    val quantity = if (operation == Operation.USE) 1 else requestedQuantity
    if (quantity <= 0 || owned.quantity < quantity) {
      return failure(state, "insufficient_item_quantity", "${actor.name} không có đủ ${definition.displayName} trong kho đồ.")
    }

    val target = if (operation == Operation.TRANSFER) {
      val id = targetId?.takeIf { it.isNotBlank() }
        ?: return failure(state, "target_required", "Hãy chọn nhân vật nhận vật phẩm.")
      if (id == actorId) return failure(state, "target_same_as_actor", "Không thể chuyển vật phẩm cho chính nhân vật đang giữ nó.")
      if (id !in state.party.memberIds) return failure(state, "target_not_in_party", "Chỉ có thể chuyển vật phẩm cho thành viên đang ở trong đội.")
      val member = state.characters[id]
        ?: return failure(state, "target_unknown", "Không tìm thấy nhân vật nhận vật phẩm.")
      if (member.presence != CharacterPresence.ACTIVE) {
        return failure(state, "target_not_active", "${member.name} hiện không ở cùng đội để nhận vật phẩm.")
      }
      member
    } else null

    val blocked = when (operation) {
      Operation.USE -> if (!definition.usable) "item_use_not_supported" else null
      Operation.TRANSFER -> if (!definition.transferable) "item_transfer_locked" else null
      Operation.DISCARD -> if (!definition.discardable) "item_discard_locked" else null
    }
    if (blocked != null) return failure(state, blocked, blockedMessage(actor.name, definition, target?.name, blocked))

    val itemOperation = when (operation) {
      Operation.USE -> ItemCommand.Operation.USE
      Operation.TRANSFER -> ItemCommand.Operation.TRANSFER
      Operation.DISCARD -> ItemCommand.Operation.DROP
    }
    val command = ItemCommand(
      commandId = "UI:${state.turn.currentTurnId}:${System.nanoTime()}",
      turnId = null,
      actorId = actorId,
      targetId = target?.id,
      source = CommandSource.UI,
      operation = itemOperation,
      itemId = owned.itemId,
      itemName = definition.displayName,
      quantity = quantity,
      metadata = owned.metadata
    )
    val result = StateReducer.execute(state, command)
    if (!result.applied) {
      val reason = result.validation.reason ?: "inventory_action_rejected"
      return failure(state, reason, validationMessage(actor.name, definition, target?.name, reason))
    }

    val message = when (operation) {
      Operation.USE -> "${actor.name} sử dụng ${definition.displayName} x1"
      Operation.TRANSFER -> "${actor.name} đưa ${definition.displayName} x$quantity cho ${target!!.name}"
      Operation.DISCARD -> "${actor.name} vứt bỏ ${definition.displayName} x$quantity"
    }
    return InventoryUiOutcome(result.state, true, message = message)
  }

  private fun blockedMessage(actorName: String, item: ItemDefinition, targetName: String?, reason: String): String = when (reason) {
    "item_use_not_supported" -> "${item.displayName} không thể sử dụng trực tiếp từ kho đồ."
    "item_transfer_locked" -> "$actorName không thể đưa ${item.displayName} cho ${targetName ?: "nhân vật khác"}."
    "item_discard_locked" -> "$actorName không thể vứt bỏ ${item.displayName}."
    else -> "Không thể thực hiện thao tác vật phẩm này."
  }

  private fun validationMessage(actorName: String, item: ItemDefinition, targetName: String?, reason: String): String = when (reason) {
    "inventory_slot_limit", "inventory_stack_limit" ->
      "$actorName không thể đưa ${item.displayName} cho ${targetName ?: "nhân vật nhận"} vì kho đồ của ${targetName ?: "nhân vật đó"} đã đầy."
    "signature_equipment_locked" -> "$actorName không thể chuyển ${item.displayName}."
    "item_not_owned", "insufficient_item_quantity" -> "$actorName không có đủ ${item.displayName} trong kho đồ."
    "target_required" -> "Hãy chọn nhân vật nhận vật phẩm."
    "target_unknown", "target_not_in_party" -> "Không thể chuyển vật phẩm cho nhân vật này."
    "quantity_must_be_positive" -> "Số lượng vật phẩm phải lớn hơn 0."
    "healing_target_defeated" -> "$actorName không thể sử dụng ${item.displayName} trong trạng thái hiện tại."
    else -> "Không thể thực hiện thao tác vật phẩm này."
  }

  private fun failure(state: GameState, reason: String, message: String) =
    InventoryUiOutcome(state, false, reason = reason, message = message)
}

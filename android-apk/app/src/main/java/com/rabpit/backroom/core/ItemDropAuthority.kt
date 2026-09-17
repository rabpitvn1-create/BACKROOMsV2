package com.rabpit.backroom.core

enum class ItemDropOrigin { ENTITY, ITEM_BOX }

object ItemDropAuthority {
  const val ORIGIN_KEY = "dropOrigin"
  const val ORIGIN_ID_KEY = "dropOriginId"

  fun validatePickup(command: ItemCommand): ValidationResult {
    if (command.operation != ItemCommand.Operation.PICKUP) return ValidationResult(true)
    if (command.source != CommandSource.SYSTEM) {
      return ValidationResult(false, "item_acquisition_requires_authoritative_drop")
    }
    val origin = command.metadata[ORIGIN_KEY]
      ?.trim()
      ?.uppercase()
      ?.let { runCatching { ItemDropOrigin.valueOf(it) }.getOrNull() }
      ?: return ValidationResult(false, "item_drop_origin_required")
    if (command.metadata[ORIGIN_ID_KEY].isNullOrBlank()) {
      return ValidationResult(false, "item_drop_origin_id_required")
    }
    if (origin !in setOf(ItemDropOrigin.ENTITY, ItemDropOrigin.ITEM_BOX)) {
      return ValidationResult(false, "item_drop_origin_invalid")
    }
    return ValidationResult(true)
  }

  fun entityDrop(
    commandId: String,
    actorId: String,
    entityKey: String,
    item: ItemStack,
    quantity: Int = item.quantity
  ): ItemCommand = dropCommand(commandId, actorId, ItemDropOrigin.ENTITY, entityKey, item, quantity)

  fun itemBoxDrop(
    commandId: String,
    actorId: String,
    itemBoxId: String,
    item: ItemStack,
    quantity: Int = item.quantity
  ): ItemCommand = dropCommand(commandId, actorId, ItemDropOrigin.ITEM_BOX, itemBoxId, item, quantity)

  private fun dropCommand(
    commandId: String,
    actorId: String,
    origin: ItemDropOrigin,
    originId: String,
    item: ItemStack,
    quantity: Int
  ): ItemCommand = ItemCommand(
    commandId = commandId,
    turnId = null,
    actorId = actorId,
    source = CommandSource.SYSTEM,
    operation = ItemCommand.Operation.PICKUP,
    itemId = item.itemId,
    itemName = item.name,
    quantity = quantity.coerceAtLeast(1),
    metadata = item.metadata + mapOf(
      ORIGIN_KEY to origin.name,
      ORIGIN_ID_KEY to originId
    )
  )
}

/**
 * Drop data intentionally starts empty. Entity and ItemBox are the only legal
 * acquisition channels, but their concrete tables are supplied separately.
 */
object ItemDropCatalog {
  fun forEntity(entityKey: String): List<ItemStack> = emptyList()
  fun forItemBox(itemBoxId: String): List<ItemStack> = emptyList()
}

object ItemDropResolver {
  fun grantEntityDrops(state: GameState, entityKey: String, actorId: String = KAI_ID): GameState =
    grant(state, ItemDropCatalog.forEntity(entityKey)) { index, item ->
      ItemDropAuthority.entityDrop("DROP:ENTITY:$entityKey:$index", actorId, entityKey, item)
    }

  fun grantItemBoxDrops(state: GameState, itemBoxId: String, actorId: String = KAI_ID): GameState =
    grant(state, ItemDropCatalog.forItemBox(itemBoxId)) { index, item ->
      ItemDropAuthority.itemBoxDrop("DROP:ITEM_BOX:$itemBoxId:$index", actorId, itemBoxId, item)
    }

  private fun grant(
    state: GameState,
    items: List<ItemStack>,
    command: (Int, ItemStack) -> ItemCommand
  ): GameState {
    var current = state
    items.forEachIndexed { index, item ->
      val result = StateReducer.execute(current, command(index, item))
      if (result.applied) current = result.state
    }
    return current
  }
}

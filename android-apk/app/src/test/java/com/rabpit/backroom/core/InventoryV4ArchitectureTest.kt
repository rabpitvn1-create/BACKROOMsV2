package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class InventoryV4ArchitectureTest {
  private fun partyState(): GameState {
    val base = GameState.initial()
    val iris = CharacterState("iris", "Iris")
    return base.copy(
      characters = base.characters + ("iris" to iris),
      party = base.party.copy(memberIds = listOf(KAI_ID, "iris")),
      inventories = base.inventories + ("iris" to InventoryState("iris"))
    )
  }

  @Test fun catalogRejectsUnknownNarrativeItems() {
    assertNull(ItemCatalog.resolve(null, "Máy quay mini không xác định"))
    assertNotNull(ItemCatalog.resolve(null, "Chai nước"))
  }

  @Test fun uiTransferMovesWholeUnitsBetweenAuthoritativeInventories() {
    var state = partyState()
    state = StateReducer.execute(state, ItemCommand(
      "grant-water", "TURN_1", KAI_ID, source = CommandSource.SYSTEM,
      operation = ItemCommand.Operation.PICKUP, itemId = "water-bottle", itemName = "Chai nước", quantity = 2
    )).state
    val outcome = InventoryUiActions.execute(state, KAI_ID, "TRANSFER", "water-bottle", 1, "iris")
    assertTrue(outcome.applied)
    assertEquals("Kai Akechi đưa Chai nước x1 cho Iris", outcome.message)
    assertEquals(1, outcome.state.inventories.getValue(KAI_ID).items.getValue("water-bottle").quantity)
    assertEquals(1, outcome.state.inventories.getValue("iris").items.getValue("water-bottle").quantity)
  }

  @Test fun uiDiscardDestroysUnitInsteadOfCreatingWorldLoot() {
    var state = partyState()
    state = StateReducer.execute(state, ItemCommand(
      "grant-food", "TURN_1", KAI_ID, source = CommandSource.SYSTEM,
      operation = ItemCommand.Operation.PICKUP, itemId = "food-container", itemName = "Hộp đồ hộp"
    )).state
    val outcome = InventoryUiActions.execute(state, KAI_ID, "DISCARD", "food-container", 1, null)
    assertTrue(outcome.applied)
    assertFalse(outcome.state.inventories.getValue(KAI_ID).items.containsKey("food-container"))
    assertTrue(outcome.state.world.keys.none { it.contains("item", true) || it.contains("loot", true) })
  }

  @Test fun omnivaultScanAndCopyAreRetiredWithoutMutation() {
    val state = GameState.initial()
    val beforeItems = state.inventories[KAI_ID]?.items.orEmpty()
    val beforeStored = state.omnivault.storedItems
    for (operation in listOf(OmnivaultCommand.Operation.SCAN, OmnivaultCommand.Operation.COPY)) {
      val result = StateReducer.execute(state, OmnivaultCommand(
        commandId = "retired-${operation.name}", turnId = state.turn.currentTurnId, actorId = KAI_ID,
        source = CommandSource.SYSTEM, operation = operation,
        itemId = "water-bottle", itemName = "Chai nước", quantity = 1
      ))
      assertFalse(result.applied)
      assertEquals("omnivault_operation_retired", result.validation.reason)
      assertEquals(beforeItems, result.state.inventories[KAI_ID]?.items.orEmpty())
      assertEquals(beforeStored, result.state.omnivault.storedItems)
      assertTrue(result.state.omnivault.scanSlots.isEmpty())
      assertTrue(result.state.omnivault.markedSourceIds.isEmpty())
    }
  }

  @Test fun omnivaultStoreAndWithdrawStillMoveExistingItemsOnly() {
    val initial = GameState.initial()
    var state = StateReducer.execute(initial, ItemCommand(
      "grant-store-water", initial.turn.currentTurnId, KAI_ID, source = CommandSource.SYSTEM,
      operation = ItemCommand.Operation.PICKUP, itemId = "water-bottle", itemName = "Chai nước", quantity = 1
    )).state

    val stored = StateReducer.execute(state, OmnivaultCommand(
      "store-water", state.turn.currentTurnId, KAI_ID, source = CommandSource.SYSTEM,
      operation = OmnivaultCommand.Operation.STORE,
      itemId = "water-bottle", itemName = "Chai nước", quantity = 1
    ))
    assertTrue(stored.applied)
    assertFalse(stored.state.inventories.getValue(KAI_ID).items.containsKey("water-bottle"))
    assertEquals(1, stored.state.omnivault.storedItems.getValue("water-bottle").quantity)

    val withdrawn = StateReducer.execute(stored.state, OmnivaultCommand(
      "withdraw-water", stored.state.turn.currentTurnId, KAI_ID, source = CommandSource.SYSTEM,
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

}

package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class OmnivaultNaturalFlowTest {
  @Test fun scanAndCopyAreRetiredInNaturalFlow() {
    val state = GameState.initial()
    for (operation in listOf(OmnivaultCommand.Operation.SCAN, OmnivaultCommand.Operation.COPY)) {
      val result = StateReducer.execute(state, OmnivaultCommand(
        "retired-natural-${operation.name}", state.turn.currentTurnId, KAI_ID,
        source = CommandSource.RULE, operation = operation,
        itemId = "water-bottle", itemName = "Chai nước", quantity = 2
      ))
      assertFalse(result.applied)
      assertEquals("omnivault_operation_retired", result.validation.reason)
      assertEquals(state.inventories, result.state.inventories)
      assertEquals(state.omnivault.storedItems, result.state.omnivault.storedItems)
    }
  }

  @Test fun copyCannotCreateInventoryWithoutExistingStoredItem() {
    val state = GameState.initial()
    val result = StateReducer.execute(state, OmnivaultCommand(
      "retired-copy-no-template", state.turn.currentTurnId, KAI_ID,
      source = CommandSource.RULE, operation = OmnivaultCommand.Operation.COPY,
      itemId = "almond-water", itemName = "Nước Hạnh Nhân", quantity = 99
    ))
    assertFalse(result.applied)
    assertEquals("omnivault_operation_retired", result.validation.reason)
    assertEquals(state.inventories, result.state.inventories)
  }
}

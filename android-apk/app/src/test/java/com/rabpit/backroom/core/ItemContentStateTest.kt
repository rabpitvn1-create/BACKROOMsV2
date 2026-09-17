package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class ItemContentStateTest {
  private fun grant(name: String, id: String, quantity: Int = 1): GameState {
    val result = StateReducer.execute(GameState.initial(), ItemCommand(
      "grant-$id", "TURN_1", KAI_ID, source = CommandSource.SYSTEM,
      operation = ItemCommand.Operation.PICKUP, itemId = id, itemName = name, quantity = quantity
    ))
    assertTrue(result.applied)
    return result.state
  }

  @Test fun waterBottleIsAtomicAndVanishesWhenUsed() {
    val state = grant("Chai nước", "water-bottle", 2)
    val first = StateReducer.execute(state, ItemCommand(
      "use-water-1", "TURN_1", KAI_ID, source = CommandSource.UI,
      operation = ItemCommand.Operation.USE, itemId = "water-bottle", itemName = "Chai nước"
    ))
    assertTrue(first.applied)
    assertEquals(1, first.state.inventories.getValue(KAI_ID).items.getValue("water-bottle").quantity)
    val second = StateReducer.execute(first.state, ItemCommand(
      "use-water-2", "TURN_1", KAI_ID, source = CommandSource.UI,
      operation = ItemCommand.Operation.USE, itemId = "water-bottle", itemName = "Chai nước"
    ))
    assertTrue(second.applied)
    assertFalse(second.state.inventories.getValue(KAI_ID).items.containsKey("water-bottle"))
    assertTrue(second.state.inventories.getValue(KAI_ID).items.keys.none { it.contains(":empty") || it.contains(":low") })
  }

  @Test fun legacyResidualObjectsAreRemovedDuringV4Normalization() {
    val legacy = GameState.initial().copy(inventories = mapOf(KAI_ID to InventoryState(KAI_ID, mapOf(
      "water-bottle:empty" to ItemStack("water-bottle:empty", "Chai rỗng", contentState = ContentState.EMPTY),
      "ammo-cartridge:empty" to ItemStack("ammo-cartridge:empty", "Vỏ đạn", contentState = ContentState.EMPTY),
      "water-bottle:low" to ItemStack("water-bottle:low", "Chai nước còn ít nước")
    ))))
    val normalized = InventoryV4State.normalize(legacy)
    val items = normalized.inventories.getValue(KAI_ID).items
    assertEquals(setOf("water-bottle"), items.keys)
    assertEquals(1, items.getValue("water-bottle").quantity)
    assertEquals(ContentState.NONE, items.getValue("water-bottle").contentState)
  }
}

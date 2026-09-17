package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class ItemContentStateTest {
  private fun grant(name: String, id: String = "raw", quantity: Int = 1) =
    ItemDropAuthority.entityDrop(
      commandId = "grant-$id-$name",
      actorId = KAI_ID,
      entityKey = "test-entity",
      item = ItemStack(id, name, quantity),
      quantity = quantity
    )

  private fun use(id: String, n: Int) = ItemCommand(
    "use-$n-$id", "TURN_1", KAI_ID, source = CommandSource.RULE,
    operation = ItemCommand.Operation.USE, itemId = id, itemName = id
  )

  @Test fun waterBottleUsesThreeDiscreteStates() {
    val full = StateReducer.execute(GameState.initial(), grant("Chai nước", "legacy-water")).state
    val fullId = "water-bottle:full"
    assertEquals(ContentState.FULL, full.inventories.getValue(KAI_ID).items.getValue(fullId).contentState)
    assertEquals("Chai nước", full.inventories.getValue(KAI_ID).items.getValue(fullId).name)

    val low = StateReducer.execute(full, use(fullId, 1))
    val lowId = "water-bottle:low"
    assertTrue(low.applied)
    assertFalse(low.state.inventories.getValue(KAI_ID).items.containsKey(fullId))
    assertEquals(ContentState.LOW, low.state.inventories.getValue(KAI_ID).items.getValue(lowId).contentState)
    assertEquals("Chai nước còn ít nước", low.state.inventories.getValue(KAI_ID).items.getValue(lowId).name)

    val empty = StateReducer.execute(low.state, use(lowId, 2))
    val emptyId = "water-bottle:empty"
    assertTrue(empty.applied)
    assertEquals(ContentState.EMPTY, empty.state.inventories.getValue(KAI_ID).items.getValue(emptyId).contentState)
    assertEquals("Chai rỗng", empty.state.inventories.getValue(KAI_ID).items.getValue(emptyId).name)

    val rejected = StateReducer.execute(empty.state, use(emptyId, 3))
    assertFalse(rejected.applied)
    assertEquals("item_content_empty", rejected.validation.reason)
    assertEquals(1, rejected.state.inventories.getValue(KAI_ID).items.getValue(emptyId).quantity)
  }

  @Test fun usingOneStackMemberSplitsStateInsteadOfInventingAmounts() {
    val full = StateReducer.execute(GameState.initial(), grant("Chai nước", "water", 3)).state
    val used = StateReducer.execute(full, use("water-bottle:full", 1))
    assertEquals(2, used.state.inventories.getValue(KAI_ID).items.getValue("water-bottle:full").quantity)
    assertEquals(1, used.state.inventories.getValue(KAI_ID).items.getValue("water-bottle:low").quantity)
  }

  @Test fun preciseAmountsAreForbidden() {
    val invalid = StateReducer.execute(GameState.initial(), grant("Chai nước 200ml", "water-200"))
    assertFalse(invalid.applied)
    assertEquals("precise_content_amount_forbidden", invalid.validation.reason)
  }

  @Test fun emptyFoodBoxGenericBoxAndSpentCartridgeStayEmpty() {
    val food = ItemContentRules.normalize(ItemStack("food", "Vỏ thức ăn rỗng"))
    assertEquals(ContentState.EMPTY, food.contentState)
    assertEquals("Hộp thức ăn rỗng", food.name)

    val box = ItemContentRules.normalize(ItemStack("box", "Vỏ hộp rỗng"))
    assertEquals(ContentState.EMPTY, box.contentState)
    assertEquals("Hộp rỗng", box.name)

    val casing = ItemContentRules.normalize(ItemStack("casing", "Vỏ đạn"))
    assertEquals(ContentState.EMPTY, casing.contentState)
    assertEquals("Vỏ đạn", casing.name)
    assertNull(ItemContentRules.nextAfterUse(casing))
  }
}

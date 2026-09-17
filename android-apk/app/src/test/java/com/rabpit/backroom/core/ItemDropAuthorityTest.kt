package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class ItemDropAuthorityTest {
  private fun pickup(source: CommandSource, metadata: Map<String, String>) = ItemCommand(
    commandId = "PICKUP:${source.name}:${metadata.hashCode()}",
    turnId = null,
    actorId = KAI_ID,
    source = source,
    operation = ItemCommand.Operation.PICKUP,
    itemId = "test:item",
    itemName = "Test Item",
    quantity = 1,
    metadata = metadata
  )

  @Test fun geminiCannotManufactureItemEvenWithDropMetadata() {
    val command = pickup(CommandSource.GEMINI, mapOf(
      ItemDropAuthority.ORIGIN_KEY to ItemDropOrigin.ENTITY.name,
      ItemDropAuthority.ORIGIN_ID_KEY to "hound"
    ))
    val result = StateReducer.execute(GameState.initial(), command)
    assertFalse(result.applied)
    assertEquals("item_acquisition_requires_authoritative_drop", result.validation.reason)
  }

  @Test fun systemPickupWithoutApprovedOriginIsRejected() {
    val result = StateReducer.execute(GameState.initial(), pickup(CommandSource.SYSTEM, emptyMap()))
    assertFalse(result.applied)
    assertEquals("item_drop_origin_required", result.validation.reason)
  }

  @Test fun entityDropIsAccepted() {
    val item = ItemStack("entity:test", "Entity Test")
    val result = StateReducer.execute(GameState.initial(), ItemDropAuthority.entityDrop("E1", KAI_ID, "hound", item))
    assertTrue(result.validation.reason ?: "entity drop rejected", result.applied)
    assertTrue(result.state.inventories.getValue(KAI_ID).items.containsKey("entity:test"))
  }

  @Test fun itemBoxDropIsAccepted() {
    val item = ItemStack("box:test", "Box Test")
    val result = StateReducer.execute(GameState.initial(), ItemDropAuthority.itemBoxDrop("B1", KAI_ID, "itembox:placeholder", item))
    assertTrue(result.validation.reason ?: "item box drop rejected", result.applied)
    assertTrue(result.state.inventories.getValue(KAI_ID).items.containsKey("box:test"))
  }

  @Test fun dropCatalogStartsEmptyUntilDataIsAdded() {
    assertTrue(ItemDropCatalog.forEntity("hound").isEmpty())
    assertTrue(ItemDropCatalog.forItemBox("itembox:placeholder").isEmpty())
  }
}

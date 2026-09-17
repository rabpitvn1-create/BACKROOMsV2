package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class PhysiologyItemEffectTest {
  private fun stateWithPhysiology(): GameState {
    val base = GameState.initial()
    val kai = base.characters.getValue(KAI_ID).copy(
      physiology = PhysiologyState(
        minutesSinceFood = 240L,
        minutesSinceWater = 90L,
        minutesAwake = 720L,
        painState = "mild",
        infectionState = "none",
        thermalState = "normal"
      )
    )
    return base.copy(characters = base.characters + (KAI_ID to kai))
  }

  private fun grant(
    state: GameState,
    itemId: String,
    itemName: String,
    quantity: Int = 1,
    metadata: Map<String, String> = emptyMap()
  ): GameState {
    val result = StateReducer.execute(
      state,
      ItemCommand(
        commandId = "grant-$itemId",
        turnId = "TURN_1",
        actorId = KAI_ID,
        source = CommandSource.SYSTEM,
        operation = ItemCommand.Operation.PICKUP,
        itemId = itemId,
        itemName = itemName,
        quantity = quantity,
        metadata = metadata
      )
    )
    assertTrue(result.applied)
    return result.state
  }

  private fun use(state: GameState, commandId: String, itemId: String): ExecutionResult = StateReducer.execute(
    state,
    ItemCommand(
      commandId = commandId,
      turnId = "TURN_1",
      actorId = KAI_ID,
      source = CommandSource.UI,
      operation = ItemCommand.Operation.USE,
      itemId = itemId,
      itemName = itemId
    )
  )

  @Test fun waterEffectResetsOnlyWaterCounterAndConsumesWholeUnit() {
    val granted = grant(stateWithPhysiology(), "water-bottle", "Chai nước")

    val result = use(granted, "use-water", "water-bottle")

    assertTrue(result.applied)
    val physiology = result.state.characters.getValue(KAI_ID).physiology
    assertEquals(240L, physiology.minutesSinceFood)
    assertEquals(0L, physiology.minutesSinceWater)
    assertEquals(720L, physiology.minutesAwake)
    assertFalse(result.state.inventories.getValue(KAI_ID).items.containsKey("water-bottle"))
    assertTrue("physiology_water_recorded" in result.events)
  }

  @Test fun foodEffectResetsOnlyFoodCounterAndConsumesWholeUnit() {
    val granted = grant(stateWithPhysiology(), "food-container", "Hộp đồ hộp")

    val result = use(granted, "use-food", "food-container")

    assertTrue(result.applied)
    val physiology = result.state.characters.getValue(KAI_ID).physiology
    assertEquals(0L, physiology.minutesSinceFood)
    assertEquals(90L, physiology.minutesSinceWater)
    assertEquals(720L, physiology.minutesAwake)
    assertFalse(result.state.inventories.getValue(KAI_ID).items.containsKey("food-container"))
    assertTrue("physiology_food_recorded" in result.events)
  }

  @Test fun catalogNonUsableItemRejectsWithoutMutation() {
    val granted = grant(stateWithPhysiology(), "electrical:charged-cell", "Pin tích điện")
    val before = granted.characters.getValue(KAI_ID).physiology

    val result = use(granted, "use-charged-cell", "electrical:charged-cell")

    assertFalse(result.applied)
    assertEquals("item_use_not_supported", result.validation.reason)
    assertEquals(granted, result.state)
    assertEquals(before, result.state.characters.getValue(KAI_ID).physiology)
  }

  @Test fun catalogDefinitionOverridesUntrustedPhysiologyMetadata() {
    val granted = grant(
      stateWithPhysiology(),
      "water-bottle",
      "Chai nước",
      metadata = mapOf("physiologyEffect" to "HEAL", "consumable" to "false")
    )

    val stored = granted.inventories.getValue(KAI_ID).items.getValue("water-bottle")
    assertEquals("WATER", stored.metadata["physiologyEffect"])
    assertEquals("true", stored.metadata["consumable"])

    val result = use(granted, "use-canonical-water", "water-bottle")
    assertTrue(result.applied)
    val physiology = result.state.characters.getValue(KAI_ID).physiology
    assertEquals(240L, physiology.minutesSinceFood)
    assertEquals(0L, physiology.minutesSinceWater)
  }

  @Test fun duplicateUseNeverConsumesOrAppliesPhysiologyTwice() {
    val granted = grant(stateWithPhysiology(), "water-bottle", "Chai nước", quantity = 2)
    val command = ItemCommand(
      commandId = "same-use",
      turnId = "TURN_1",
      actorId = KAI_ID,
      source = CommandSource.UI,
      operation = ItemCommand.Operation.USE,
      itemId = "water-bottle",
      itemName = "Chai nước"
    )

    val first = StateReducer.execute(granted, command)
    assertTrue(first.applied)
    assertEquals(1, first.state.inventories.getValue(KAI_ID).items.getValue("water-bottle").quantity)
    assertEquals(0L, first.state.characters.getValue(KAI_ID).physiology.minutesSinceWater)

    val second = StateReducer.execute(first.state, command)
    assertFalse(second.applied)
    assertTrue(second.duplicate)
    assertEquals(first.state, second.state)
  }
}

from pathlib import Path

ROOT = Path(__file__).resolve().parent
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"
PHYS = TESTS / "PhysiologyItemEffectTest.kt"
SPECIAL = TESTS / "SpecialFollowerInventoryPolicyTest.kt"

for required in (PHYS, SPECIAL):
    if not required.is_file():
        raise RuntimeError("Inventory V4 regression source missing: " + required.name)

# Historical physiology tests manufactured arbitrary ad-hoc consumables and partial-container IDs.
# V4 makes ItemCatalog authoritative, so final regression coverage must exercise canonical whole
# items rather than preserving impossible pre-V4 fixtures.
PHYS.write_text(r'''package com.rabpit.backroom.core

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
    val granted = grant(stateWithPhysiology(), "ammo-cartridge", "Viên đạn")
    val before = granted.characters.getValue(KAI_ID).physiology

    val result = use(granted, "use-ammo", "ammo-cartridge")

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
''', encoding="utf-8")

# The follower-cap regression is generated by an older patch before ItemCatalog becomes the final
# normalization authority. Use the canonical key so an existing stack is actually recognized as the
# same item when validating 19 -> 20 -> 21 units.
SPECIAL.write_text(r'''package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpecialFollowerInventoryPolicyTest {
  @Test fun irisAndSyvialUseSixTypesAndTwentyPerType() {
    val state = GameState.initial()
    for (id in listOf(IRIS_ID, SYVIAL_ID)) {
      val profile = InventoryPolicy.profileFor(state, id)
      assertEquals(6, profile.maxTypes)
      assertEquals(20, profile.maxPerType)
    }
  }

  @Test fun seventhItemTypeIsRejectedForBothSpecialFollowers() {
    val state = GameState.initial()
    val sixItems = (1..6).associate { index ->
      val id = "item-$index"
      id to ItemStack(id, "Item $index", 1)
    }
    for (ownerId in listOf(IRIS_ID, SYVIAL_ID)) {
      val inventory = InventoryState(ownerId, sixItems)
      val error = InventoryPolicy.validateAddition(
        state,
        ownerId,
        inventory,
        ItemStack("item-7", "Item 7", 1),
        1
      )
      assertEquals("inventory_slot_limit", error)
    }
  }

  @Test fun existingCanonicalTypeCanReachTwentyButNotTwentyOne() {
    val state = GameState.initial()
    for (ownerId in listOf(IRIS_ID, SYVIAL_ID)) {
      val inventory = InventoryState(
        ownerId,
        mapOf("almond-water" to ItemStack("almond-water", "Nước Hạnh Nhân", 19))
      )
      assertNull(
        InventoryPolicy.validateAddition(
          state,
          ownerId,
          inventory,
          ItemStack("almond-water", "Nước Hạnh Nhân", 1),
          1
        )
      )
      assertEquals(
        "inventory_stack_limit",
        InventoryPolicy.validateAddition(
          state,
          ownerId,
          inventory,
          ItemStack("almond-water", "Nước Hạnh Nhân", 2),
          2
        )
      )
    }
  }
}
''', encoding="utf-8")

combined = PHYS.read_text(encoding="utf-8") + "\n" + SPECIAL.read_text(encoding="utf-8")
for marker in (
    "waterEffectResetsOnlyWaterCounterAndConsumesWholeUnit",
    "catalogNonUsableItemRejectsWithoutMutation",
    "catalogDefinitionOverridesUntrustedPhysiologyMetadata",
    'mapOf("almond-water" to ItemStack("almond-water", "Nước Hạnh Nhân", 19))',
    '"inventory_stack_limit"',
):
    if marker not in combined:
        raise RuntimeError("Inventory V4 regression contract missing: " + marker)

for stale in (
    "ration-gel",
    "bad-tonic",
    "water-bottle:full",
    "food-container:full",
    "water-bottle:empty",
):
    if stale in PHYS.read_text(encoding="utf-8"):
        raise RuntimeError("Pre-V4 physiology fixture survived: " + stale)

print("Inventory V4 regression compatibility applied: canonical physiology fixtures and follower stack identity aligned.")

package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class LuciaFollowerTest {
  @Test fun luciaUsesHundredHpAndAllRequestedStatsAreAtMostTen() {
    val state = GameState.initial()
    val lucia = state.characters.getValue(LUCIA_ID)
    val profile = lucia.statProfile
    assertEquals(100, profile.baseMaxHp)
    assertEquals(100, lucia.vitalState.currentHp)
    assertTrue(profile.str in 0..10)
    assertTrue(profile.df in 0..10)
    assertTrue(profile.agi in 0..10)
    assertTrue(profile.crit in 0..10)
    assertEquals(listOf(7, 7, 8, 7), listOf(profile.str, profile.df, profile.agi, profile.crit))
  }

  @Test fun luciaHasExactlyThreeCanonicalEquipmentSlots() {
    val state = GameState.initial()
    val slots = state.equipment.getValue(LUCIA_ID).slots
    assertEquals(3, slots.size)
    assertEquals(LUCIA_M4A1_ID, slots["weapon"])
    assertEquals(LUCIA_KNIFE_ID, slots["blade"])
    assertEquals(LUCIA_WATCH_ID, slots["wrist"])
    slots.values.forEach { id -> assertTrue(state.inventories.getValue(LUCIA_ID).items.containsKey(id)) }
    assertEquals(0, InventoryCapacityPolicy.usedSlots(state, LUCIA_ID))
  }

  @Test fun luciaGiftInventoryAllowsThreeTypesAndOneHundredEach() {
    val state = GameState.initial()
    val profile = InventoryPolicy.profileFor(state, LUCIA_ID)
    assertEquals(3, profile.maxTypes)
    assertEquals(100, profile.maxPerType)

    val three = InventoryState(LUCIA_ID, mapOf(
      "a" to ItemStack("a", "A", 100),
      "b" to ItemStack("b", "B", 1),
      "c" to ItemStack("c", "C", 1)
    ))
    assertEquals("inventory_slot_limit", InventoryPolicy.validateAddition(state, LUCIA_ID, three, ItemStack("d", "D", 1), 1))

    val ninetyNine = InventoryState(LUCIA_ID, mapOf("a" to ItemStack("a", "A", 99)))
    assertNull(InventoryPolicy.validateAddition(state, LUCIA_ID, ninetyNine, ItemStack("a", "A", 1), 1))
    assertEquals("inventory_stack_limit", InventoryPolicy.validateAddition(state, LUCIA_ID, ninetyNine, ItemStack("a", "A", 2), 2))
  }

  @Test fun luciaStartsOutsidePartyAndKeepsCanonAmmoSeparateFromGiftSlots() {
    val state = GameState.initial()
    val lucia = state.characters.getValue(LUCIA_ID)
    assertFalse(LUCIA_ID in state.party.memberIds)
    assertEquals("100%", lucia.metadata["encounterChance"])
    assertTrue(lucia.statProfile.regen.enabled)
    assertEquals(3, lucia.statProfile.regen.percentOfMaxHp)
    assertEquals(3, lucia.statProfile.regen.intervalCompletedTurns)
    assertEquals("0", lucia.metadata["encounterLevels"])
    assertEquals("EXPLORE", lucia.metadata["encounterAction"])
    assertEquals("60", lucia.metadata["startingLoadedAmmo"])
    assertEquals("90", lucia.metadata["startingReserveAmmo"])
    assertEquals("150", lucia.metadata["startingTotalAmmo"])
  }

  @Test fun luciaRegeneratesThreePercentEveryThirdCompletedTurn() {
    var state = CharacterStatEngine.setCurrentHp(GameState.initial(), LUCIA_ID, 80)
    state = CharacterStatEngine.applyCompletedTurnRegen(state, "TURN_L1")
    assertEquals(80, state.characters.getValue(LUCIA_ID).vitalState.currentHp)
    state = CharacterStatEngine.applyCompletedTurnRegen(state, "TURN_L2")
    assertEquals(80, state.characters.getValue(LUCIA_ID).vitalState.currentHp)
    state = CharacterStatEngine.applyCompletedTurnRegen(state, "TURN_L3")
    assertEquals(83, state.characters.getValue(LUCIA_ID).vitalState.currentHp)
    val duplicate = CharacterStatEngine.applyCompletedTurnRegen(state, "TURN_L3")
    assertEquals(83, duplicate.characters.getValue(LUCIA_ID).vitalState.currentHp)
    assertEquals(0, duplicate.characters.getValue(LUCIA_ID).vitalState.completedTurnsTowardRegen)
  }
}

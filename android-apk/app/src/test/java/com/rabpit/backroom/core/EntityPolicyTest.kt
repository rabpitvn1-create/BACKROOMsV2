package com.rabpit.backroom.core

import java.util.Random
import org.junit.Assert.*
import org.junit.Test

class EntityPolicyTest {
  private class Dice(private vararg val values: Int) : Random() {
    var calls = 0
    override fun nextInt(bound: Int): Int = values[calls++ % values.size].also {
      require(it in 0 until bound)
    }
  }

  @Test fun independentDiceKeepEverySuccessAndRespectTwoPercentBoundary() {
    val dice = Dice(199, 200, 0, 9999)
    val rolls = EntityEncounterPolicy.roll(arrayOf("hound", "smiler", "diep_minh", "jane_the_killer"), true, dice)
    assertEquals(4, dice.calls)
    assertEquals("[\"hound\",\"diep_minh\"]", rolls.getJSONArray("entityEncounterKeys").toString())
    assertTrue(rolls.getJSONObject("entityEncounter").getBoolean("success"))
    assertFalse(rolls.getJSONObject("entityRolls").getJSONObject("smiler").getBoolean("success"))
  }

  @Test fun ineligibleActionsNeverRollOrSpawn() {
    val dice = Dice(0)
    val rolls = EntityEncounterPolicy.roll(arrayOf("hound", "diep_minh"), false, dice)
    assertEquals(0, dice.calls)
    assertEquals(0, rolls.getJSONArray("entityEncounterKeys").length())
    assertFalse(rolls.getJSONObject("entityEncounter").getBoolean("success"))
  }

  @Test fun successfulEntitiesSurviveSaveLoadAndResolveInOrderWithoutOverwrite() {
    var state = EntityEncounterPolicy.enqueue(GameState.initial(), listOf("hound", "diep_minh", "smiler"))
    assertEquals("hound", CombatRuntime.active(state)!!.entityKey)
    assertEquals(state, EntityEncounterPolicy.enqueue(state, listOf("jane_the_killer")))
    state = GameStateCodec.decode(GameStateCodec.encode(state))
    state = CombatRuntime.resolve(state.copy(metadata = state.metadata +
      ("combat.escapeProgress" to "95")), "EXECUTE", "chạy thoát").state
    state = EntityEncounterPolicy.advance(state)
    assertEquals("diep_minh", CombatRuntime.active(state)!!.entityKey)
  }

  @Test fun everyKillGetsExactlyOneCatalogItemAndDuplicateAwardDoesNothing() {
    val base = GameState.initial().copy(inventories = mapOf(KAI_ID to InventoryState(KAI_ID)))
    val pool = ItemCatalog.all().filter { it.rewardable }
    for (index in pool.indices) {
      val award = EntityDrops.award(base, "kill-$index", Dice(index))
      assertEquals(1, award.state.inventories.getValue(KAI_ID).items.values.sumOf { it.quantity })
      assertEquals(1, award.state.inventories.getValue(KAI_ID).items.getValue(pool[index].id).quantity)
      assertEquals(award.state, EntityDrops.award(award.state, "kill-$index", Dice(index)).state)
    }
  }

  @Test fun fullStackRetainsDropAcrossSaveAndClaimsItAfterSpaceIsFreed() {
    val base = GameState.initial()
    val definition = ItemCatalog.all().first { it.rewardable }
    val fullItem = ItemCatalog.canonicalize(definition, ItemStack(definition.id, definition.displayName,
      quantity = InventoryPolicy.profileFor(base, KAI_ID).maxPerType))
    val full = base.copy(inventories = mapOf(KAI_ID to InventoryState(KAI_ID, mapOf(fullItem.itemId to fullItem))))
    val award = EntityDrops.award(full, "full-kill", Dice(0))
    assertEquals(full.inventories, award.state.inventories)
    assertTrue(award.message.contains("chờ nhận"))
    val reloaded = GameStateCodec.decode(GameStateCodec.encode(award.state))
    val freed = reloaded.copy(inventories = mapOf(KAI_ID to InventoryState(KAI_ID,
      mapOf(fullItem.itemId to fullItem.copy(quantity = fullItem.quantity - 1)))))
    val claimed = EntityDrops.claimPending(freed)
    assertEquals(fullItem.quantity, claimed.inventories.getValue(KAI_ID).items.getValue(fullItem.itemId).quantity)
    assertEquals(claimed, EntityDrops.claimPending(claimed))
  }
}

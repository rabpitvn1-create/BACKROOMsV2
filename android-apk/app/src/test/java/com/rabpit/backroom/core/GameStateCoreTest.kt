package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class GameStateCoreTest {
  private fun base(vararg characters: CharacterState): GameState {
    val all = listOf(CharacterState(KAI_ID, "Kai Akechi")) + characters
    return GameState.initial().copy(
      characters = all.associateBy { it.id },
      inventories = all.associate { it.id to InventoryState(it.id) },
      equipment = all.associate { it.id to EquipmentState(it.id) }
    )
  }

  private fun item(
    id: String,
    op: ItemCommand.Operation,
    quantity: Int = 1,
    target: String? = null,
    slot: String? = null,
    source: CommandSource = CommandSource.RULE
  ) = ItemCommand("cmd-$id-$op-$quantity-${target.orEmpty()}-$source", "TURN_1", KAI_ID, target, source, op, id, id, quantity, slot)

  private fun entityGrant(id: String, quantity: Int = 1, commandId: String = "grant-$id-$quantity") =
    ItemDropAuthority.entityDrop(
      commandId = commandId,
      actorId = KAI_ID,
      entityKey = "test-entity",
      item = ItemStack(id, id, quantity),
      quantity = quantity
    )

  @Test fun authoritativeGrantDropAndDuplicateAreDeterministic() {
    val grant = entityGrant("water", commandId = "grant-water")
    val picked = StateReducer.execute(base(), grant)
    assertTrue(picked.applied)
    assertEquals(1, picked.state.inventories.getValue(KAI_ID).items.getValue("water").quantity)

    val duplicate = StateReducer.execute(picked.state, grant)
    assertTrue(duplicate.duplicate)
    assertEquals(1, duplicate.state.inventories.getValue(KAI_ID).items.getValue("water").quantity)

    val dropped = StateReducer.execute(picked.state, item("water", ItemCommand.Operation.DROP))
    assertFalse(dropped.state.inventories.getValue(KAI_ID).items.containsKey("water"))
  }

  @Test fun onlyAuthoritativeDropsCanAcquireItems() {
    val rulePickup = StateReducer.execute(base(), item("water", ItemCommand.Operation.PICKUP, source = CommandSource.RULE))
    assertFalse(rulePickup.applied)
    assertEquals("item_acquisition_requires_authoritative_drop", rulePickup.validation.reason)

    val geminiPickup = StateReducer.execute(base(), item("water", ItemCommand.Operation.PICKUP, source = CommandSource.GEMINI))
    assertFalse(geminiPickup.applied)
    assertEquals("item_acquisition_requires_authoritative_drop", geminiPickup.validation.reason)

    val bareSystemPickup = StateReducer.execute(base(), item("water", ItemCommand.Operation.PICKUP, source = CommandSource.SYSTEM))
    assertFalse(bareSystemPickup.applied)
    assertEquals("item_drop_origin_required", bareSystemPickup.validation.reason)

    val authoritative = StateReducer.execute(base(), entityGrant("water"))
    assertTrue(authoritative.applied)
    assertEquals(1, authoritative.state.inventories.getValue(KAI_ID).items.getValue("water").quantity)
  }

  @Test fun transferRequiresOwnershipAndKnownTarget() {
    val iris = CharacterState("iris", "Iris")
    val picked = StateReducer.execute(base(iris), entityGrant("water", 2)).state
    val moved = StateReducer.execute(picked, item("water", ItemCommand.Operation.TRANSFER, 1, "iris"))
    assertTrue(moved.applied)
    assertEquals(1, moved.state.inventories.getValue(KAI_ID).items.getValue("water").quantity)
    assertEquals(1, moved.state.inventories.getValue("iris").items.getValue("water").quantity)
  }

  @Test fun equipAndUnequipUseOwnedItem() {
    val picked = StateReducer.execute(base(), entityGrant("gun")).state
    val equipped = StateReducer.execute(picked, item("gun", ItemCommand.Operation.EQUIP, slot = "weapon"))
    assertEquals("gun", equipped.state.equipment.getValue(KAI_ID).slots["weapon"])
    val unequipped = StateReducer.execute(equipped.state, item("gun", ItemCommand.Operation.UNEQUIP, slot = "weapon"))
    assertNull(unequipped.state.equipment.getValue(KAI_ID).slots["weapon"])
  }

  @Test fun partyNeedsPresenceConsentAndHasFourMemberLimit() {
    val people = (1..4).map { CharacterState("p$it", "P$it") }
    var state = base(*people.toTypedArray())
    for (i in 1..3) {
      val command = PartyCommand("join-$i", "TURN_1", KAI_ID, "p$i", CommandSource.UI, PartyCommand.Operation.ADD, true, true)
      state = StateReducer.execute(state, command).state
    }
    assertEquals(4, state.party.memberIds.size)
    val full = StateReducer.execute(state, PartyCommand("join-4", "TURN_1", KAI_ID, "p4", CommandSource.UI, PartyCommand.Operation.ADD, true, true))
    assertEquals("party_full", full.validation.reason)
    val noConsent = StateReducer.execute(base(people[0]), PartyCommand("no-consent", "TURN_1", KAI_ID, "p1", CommandSource.LITERT, PartyCommand.Operation.ADD, false, true))
    assertEquals("join_not_confirmed", noConsent.validation.reason)
  }

  @Test fun statusIsStructuredAndRemovable() {
    val effect = StatusEffect("injury-leg", "INJURY", "validated_event", "TURN_1", persistent = true)
    val applied = StateReducer.execute(base(), StatusCommand("status-add", "TURN_1", KAI_ID, source = CommandSource.SYSTEM, operation = StatusCommand.Operation.APPLY, effect = effect))
    assertTrue("injury-leg" in applied.state.statuses)
    val removed = StateReducer.execute(applied.state, StatusCommand("status-remove", "TURN_1", KAI_ID, source = CommandSource.SYSTEM, operation = StatusCommand.Operation.REMOVE, statusId = "injury-leg"))
    assertFalse("injury-leg" in removed.state.statuses)
  }

  @Test fun geminiWorldDeltaNeedsGameEngineValidation() {
    val rejected = StateReducer.execute(base(), ValidatedLegacyStateCommand(
      "world-invalid", "TURN_1", source = CommandSource.GEMINI, location = "Level 1", validatedByGameEngine = false
    ))
    assertEquals("engine_validation_required", rejected.validation.reason)
    assertNull(rejected.state.world["location"])
    val accepted = StateReducer.execute(base(), ValidatedLegacyStateCommand(
      "world-valid", "TURN_1", source = CommandSource.GEMINI, location = "Level 1", validatedByGameEngine = true
    ))
    assertEquals("Level 1", accepted.state.world["location"])
  }
}

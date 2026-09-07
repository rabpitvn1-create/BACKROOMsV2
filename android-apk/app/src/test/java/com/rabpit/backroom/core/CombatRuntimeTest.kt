package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CombatRuntimeTest {
  @Test fun entityTriggerStartsOneAuthoritativeEncounterWithHealth() {
    val started = CombatRuntime.start(GameState.initial(), "hound")
    val combat = CombatRuntime.active(started)
    assertNotNull(combat)
    assertEquals("hound", combat!!.entityKey)
    assertEquals(80, combat.entityMaxHp)
    assertEquals(80, combat.entityHp)
    assertEquals(100, combat.playerMaxHp)
    assertEquals(100, combat.playerHp)

    val duplicate = CombatRuntime.start(started, "smiler")
    assertEquals("hound", CombatRuntime.active(duplicate)!!.entityKey)
  }

  @Test fun repeatedAuthoritativeAttacksEventuallyDestroyAndClearEntity() {
    var state = CombatRuntime.start(GameState.initial(), "hound")
    var destroyed = false
    repeat(24) {
      if (destroyed) return@repeat
      val result = CombatRuntime.resolve(state, "EXECUTE", "bắn Hound bằng Magnum")
      assertTrue(result.handled)
      state = result.state
      destroyed = result.entityDestroyed
    }
    assertTrue("Entity must be destroyable by authoritative combat resolution", destroyed)
    assertNull(CombatRuntime.active(state))
  }

  @Test fun combatExploreIsMovementNotAnotherEncounter() {
    val started = CombatRuntime.start(GameState.initial(), "skin-stealer")
    val before = CombatRuntime.active(started)!!
    val result = CombatRuntime.resolve(started, "EXPLORE", "lùi lại tìm vật che chắn")
    assertTrue(result.handled)
    val after = CombatRuntime.active(result.state)
    if (after != null) {
      assertEquals("skin-stealer", after.entityKey)
      assertTrue(after.escapeProgress >= before.escapeProgress)
      assertTrue(after.range.ordinal >= before.range.ordinal)
    }
  }

  @Test fun escapeResolutionClearsEncounterWithoutDestroyingRequirement() {
    var state = CombatRuntime.start(GameState.initial(), "smiler")
    var escaped = false
    repeat(12) {
      if (escaped) return@repeat
      val move = CombatRuntime.resolve(state, "EXPLORE", "lùi vào cover và di chuyển")
      state = move.state
      if (move.escaped) { escaped = true; return@repeat }
      val flee = CombatRuntime.resolve(state, "EXECUTE", "chạy thoát khỏi encounter")
      state = flee.state
      escaped = flee.escaped
    }
    assertTrue(escaped)
    assertNull(CombatRuntime.active(state))
  }

  @Test fun readActionRevealsTelegraphAndBuildsOpeningWhenEncounterSurvives() {
    val state = CombatRuntime.start(GameState.initial(), "clump")
    val result = CombatRuntime.resolve(state, "SEARCH", "quan sát kỹ chuyển động của nó")
    assertTrue(result.handled)
    val after = CombatRuntime.active(result.state)
    assertNotNull(after)
    assertTrue(after!!.opening >= 1)
    assertTrue(after.momentum >= 0)
    assertFalse(after.telegraph.isBlank())
  }

  @Test fun kaiAlwaysStartsAndRealPartyMembersAlternateWithEntity() {
    val initial = GameState.initial()
    val withLucia = initial.copy(
      characters = initial.characters + ("lucia" to CharacterState("lucia", "Lucia \"Lục\"")),
      party = PartyState(leaderId = "lucia", memberIds = listOf("lucia", KAI_ID), maxMembers = 4)
    )
    val started = CombatRuntime.start(withLucia, "clump")
    val result = CombatRuntime.resolve(started, "EXECUTE", "bắn Clump")

    assertTrue(result.handled)
    assertEquals(listOf(KAI_ID, "entity:clump", "lucia", "entity:clump"), result.events.map { it.actorId })
    assertEquals("Kai", result.events.first().actorName)
    assertEquals("Lucia", result.events[2].actorName)
  }

  @Test fun fourVisualSlotsDoNotCreatePhantomCombatants() {
    val initial = GameState.initial()
    val withLucia = initial.copy(
      characters = initial.characters + ("lucia" to CharacterState("lucia", "Lucia")),
      party = PartyState(memberIds = listOf(KAI_ID, "lucia"), maxMembers = 4)
    )
    val started = CombatRuntime.start(withLucia, "hound")
    val combatJson = CombatRuntime.toJson(started)!!
    val slots = combatJson.getJSONArray("slots")

    assertEquals(4, slots.length())
    assertTrue(slots.getJSONObject(0).getBoolean("occupied"))
    assertTrue(slots.getJSONObject(1).getBoolean("occupied"))
    assertFalse(slots.getJSONObject(2).getBoolean("occupied"))
    assertFalse(slots.getJSONObject(3).getBoolean("occupied"))
    assertEquals(2, combatJson.getInt("participantCount"))
  }

  @Test fun missingCompanionOverlayRemainsAnEmptyPreparedSlot() {
    val initial = GameState.initial()
    val withLucia = initial.copy(
      characters = initial.characters + ("lucia" to CharacterState("lucia", "Lucia")),
      party = PartyState(memberIds = listOf(KAI_ID, "lucia"), maxMembers = 4)
    )
    val combatJson = CombatRuntime.toJson(CombatRuntime.start(withLucia, "hound"))!!
    val slots = combatJson.getJSONArray("slots")

    assertEquals("kai_entity_overlay.png", slots.getJSONObject(0).getString("overlayRef"))
    assertEquals("", slots.getJSONObject(1).getString("overlayRef"))
  }

  @Test fun combatMessagesStayCompactAndSystemFriendly() {
    val result = CombatRuntime.resolve(CombatRuntime.start(GameState.initial(), "hound"), "EXECUTE", "bắn Hound")
    assertTrue(result.events.isNotEmpty())
    assertTrue(result.events.all { it.text.length <= 140 })
    assertTrue(result.events.first().text.startsWith("Kai "))
    assertFalse(result.reply.contains("phản công:"))
  }
}

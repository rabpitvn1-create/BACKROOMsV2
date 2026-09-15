package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CombatTurnAuthorityTest {
  @Test fun inactiveCombatDoesNotAdvanceTime() {
    val initial = GameState.initial()
    val result = CombatTurnAuthority.resolve(initial, "ATTACK", "Tấn công")
    assertFalse(result.handled)
    assertTrue(result.state.time.elapsedSubjectiveMinutes == initial.time.elapsedSubjectiveMinutes)
  }

  @Test fun handledCombatAdvancesExactlyOneMinute() {
    val initial = CombatRuntime.start(GameState.initial(), "hound")
    val before = initial.time.elapsedSubjectiveMinutes
    val result = CombatTurnAuthority.resolve(initial, "ATTACK", "Tấn công")
    assertTrue(result.handled)
    assertTrue(result.state.time.elapsedSubjectiveMinutes == before + 1)
  }

  @Test fun handledCombatAppliesCompletedTurnRegenerationAtTheKotlinBoundary() {
    val initial = CombatRuntime.start(GameState.initial(), "hound")
    val result = CombatTurnAuthority.resolve(initial, "ATTACK", "Tấn công")

    assertTrue(result.handled)
    assertEquals("COMBAT_TURN_1", result.state.characters.getValue(KAI_ID).vitalState.lastRegenCompletedTurnId)
  }
}

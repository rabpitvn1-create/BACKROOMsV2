package com.rabpit.backroom.core

/**
 * Kotlin-owned orchestration for one authoritative combat action.
 *
 * CombatRuntime owns deterministic combat rules. This boundary also owns the
 * combat time cost and completed-turn regeneration so Python/Java bridges only
 * adapt legacy JSON and project the already-authoritative result.
 */
object CombatTurnAuthority {
  fun resolve(state: GameState, actionKind: String, action: String): CombatRuntime.Resolution {
    val resolution = CombatRuntime.resolve(state, actionKind, action)
    if (!resolution.handled) return resolution

    var next = resolution.state
    val time = TimeEngine.execute(next, TimeAdvanceCommand(
      commandId = "COMBAT:${next.turn.currentTurnId}:${System.nanoTime()}",
      turnId = null,
      actorId = KAI_ID,
      source = CommandSource.SYSTEM,
      minutes = 1,
      reason = "combat_action"
    ))
    if (time.applied) next = time.state
    next = CharacterStatEngine.applyCompletedTurnRegen(next, completedTurnRegenToken(state))
    return resolution.copy(state = next)
  }

  private fun completedTurnRegenToken(state: GameState): String {
    val turnNumber = state.turn.currentTurnId.substringAfterLast('_').toIntOrNull()?.coerceAtLeast(1) ?: 1
    return "COMBAT_TURN_$turnNumber"
  }
}

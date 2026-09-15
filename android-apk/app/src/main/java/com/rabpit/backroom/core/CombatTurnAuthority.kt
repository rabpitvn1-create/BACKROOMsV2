package com.rabpit.backroom.core

/**
 * Kotlin-owned orchestration for one authoritative combat action.
 *
 * CombatRuntime owns deterministic combat rules. This boundary also owns the
 * round-scoped combat time cost and completed-turn regeneration so Python/Java
 * bridges only adapt legacy JSON and project the already-authoritative result.
 */
object CombatTurnAuthority {
  fun resolve(state: GameState, actionKind: String, action: String): CombatRuntime.Resolution {
    val resolution = CombatRuntime.resolve(state, actionKind, action)
    if (!resolution.handled || !completesRound(actionKind, resolution)) return resolution

    var next = resolution.state
    val time = TimeEngine.execute(next, TimeAdvanceCommand(
      commandId = "COMBAT:${next.turn.currentTurnId}:${System.nanoTime()}",
      turnId = null,
      actorId = KAI_ID,
      source = CommandSource.SYSTEM,
      minutes = 1,
      reason = "combat_round"
    ))
    if (time.applied) next = time.state
    next = CharacterStatEngine.applyCompletedTurnRegen(next, completedTurnRegenToken(state))
    return resolution.copy(state = next)
  }

  /**
   * AUTO_COMBAT_STEP persists the next actor cursor in authoritative GameState metadata.
   * Cursor zero marks a completed cycle; terminal combat always completes the round.
   * Manual combat actions remain one complete round for backward compatibility.
   */
  fun completesRound(actionKind: String, resolution: CombatRuntime.Resolution): Boolean {
    if (!actionKind.equals("AUTO_COMBAT_STEP", ignoreCase = true)) return true
    if (resolution.entityDestroyed || resolution.escaped) return true
    return (resolution.state.metadata["combat.autoCursor"]?.toIntOrNull() ?: 0) == 0
  }

  private fun completedTurnRegenToken(state: GameState): String {
    val turnNumber = state.turn.currentTurnId.substringAfterLast('_').toIntOrNull()?.coerceAtLeast(1) ?: 1
    return "COMBAT_TURN_$turnNumber"
  }
}

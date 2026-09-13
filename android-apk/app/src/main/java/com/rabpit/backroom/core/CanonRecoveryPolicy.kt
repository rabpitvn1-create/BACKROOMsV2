package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Final fail-soft gate used only after the writer has already had one repair attempt.
 * The recovery path discards the repaired reply/ops and restores the pre-turn state,
 * so rejected proposals are harmless unless a real gameplay consequence already survived.
 */
object CanonRecoveryPolicy {
  private val harmlessFlagRoots = listOf("exploration", "communication", "visualAreaKey", "visualEventKey")
  private val combatTerms = listOf(
    "tấn công", "đánh", "bắn", "chém", "đâm", "giết", "giao chiến",
    "attack", "fight", "shoot", "slash", "stab", "kill", "combat"
  )

  @JvmStatic
  fun isEligible(
    before: JSONObject,
    candidate: JSONObject,
    generated: JSONObject,
    rolls: JSONObject,
    action: String,
    meta: Boolean,
    repaired: Boolean,
  ): Boolean {
    if (meta || !repaired) return false
    val normalizedAction = action.lowercase(Locale.ROOT)
    if (combatTerms.any(normalizedAction::contains)) return false
    if (combatActive(before) || combatActive(candidate)) return false
    if (hasEncounter(before) || hasEncounter(candidate)) return false
    if (transitionReady(before) || transitionReady(candidate)) return false
    if (containsSuccess(rolls)) return false

    // The generated payload is deliberately not trusted here. If recovery is accepted the runtime
    // clears all ops/choices and replaces candidateState with `before`, so only the reducer result
    // and locked gameplay consequences can make this branch unsafe.
    generated.optJSONArray("ops")
    return scrub(before).similar(scrub(candidate))
  }

  private fun combatActive(state: JSONObject): Boolean =
    state.optJSONObject("combat")?.optBoolean("active", false) == true

  private fun hasEncounter(state: JSONObject): Boolean {
    val flags = state.optJSONObject("flags") ?: return false
    return flags.optString("entityEncounterKey", "").isNotBlank() ||
      flags.optJSONArray("entityEncounterKeys")?.let { it.length() > 0 } == true
  }

  private fun transitionReady(state: JSONObject): Boolean {
    val exploration = state.optJSONObject("flags")?.optJSONObject("exploration") ?: return false
    return exploration.optBoolean("transitionReady", false) ||
      exploration.optBoolean("exitReady", false) ||
      exploration.optBoolean("confirmedExit", false) ||
      exploration.optString("confirmedExit", "").isNotBlank()
  }

  private fun containsSuccess(value: Any?): Boolean = when (value) {
    is JSONObject -> value.optBoolean("success", false) || value.keys().asSequence().any { containsSuccess(value.opt(it)) }
    is JSONArray -> (0 until value.length()).any { containsSuccess(value.opt(it)) }
    else -> false
  }

  private fun scrub(source: JSONObject): JSONObject {
    val copy = JSONObject(source.toString())
    copy.remove("location")
    val flags = copy.optJSONObject("flags") ?: return copy
    flags.remove("lastRolls")
    for (root in harmlessFlagRoots) flags.remove(root)
    val madGod = flags.optJSONObject("madGod")
    if (madGod != null && !madGod.keys().hasNext()) flags.remove("madGod")
    return copy
  }
}

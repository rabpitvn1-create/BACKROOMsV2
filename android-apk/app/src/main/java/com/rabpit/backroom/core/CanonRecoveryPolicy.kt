package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

object CanonRecoveryPolicy {
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
    if (normalizedAction.contains("tấn công") || normalizedAction.contains("attack") || normalizedAction.contains("combat")) return false
    if (before.optJSONObject("combat")?.optBoolean("active", false) == true) return false
    if (candidate.optJSONObject("combat")?.optBoolean("active", false) == true) return false
    if (hasEncounter(before) || hasEncounter(candidate)) return false
    if (transitionReady(before) || transitionReady(candidate)) return false
    if (containsSuccess(rolls)) return false
    generated.optJSONArray("ops")
    return scrub(before).similar(scrub(candidate))
  }

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
    val madGod = flags.optJSONObject("madGod")
    if (madGod != null && !madGod.keys().hasNext()) flags.remove("madGod")
    return copy
  }
}

package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject

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
    if (CanonFallbackPolicy.isEligible(before, candidate, generated, rolls, action, meta, repaired)) return true
    if (meta || !repaired) return false
    if (before.optJSONObject("combat")?.optBoolean("active", false) == true) return false
    if (candidate.optJSONObject("combat")?.optBoolean("active", false) == true) return false
    if (containsSuccess(rolls)) return false
    return scrub(before).similar(scrub(candidate))
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

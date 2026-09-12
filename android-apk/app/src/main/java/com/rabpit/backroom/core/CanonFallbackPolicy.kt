package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Narrow fail-soft gate for a repaired turn whose final model output still fails canon audit. */
object CanonFallbackPolicy {
  private val harmlessFlagRoots = setOf("exploration", "communication", "visualAreaKey", "visualEventKey")
  private val dangerousOperationTypes = setOf(
    "set_level", "patch_player", "inventory_upsert", "inventory_remove", "party_upsert", "party_remove"
  )
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
    if (meta || !repaired || currentLevel(before) != 0) return false
    if (!StoryProgressionPolicy.isLevel0ExplorationAction(action) || hasCombatIntent(action)) return false
    if (combatActive(before) || combatActive(candidate) || entityPresent(before)) return false
    if (transitionReady(before) || transitionReady(candidate)) return false
    if (hasDangerousRollConsequence(rolls) || hasDangerousModelOperation(generated.optJSONArray("ops"))) return false
    if (dangerousStateChanged(before, candidate)) return false
    return true
  }

  private fun currentLevel(state: JSONObject): Int = state.optJSONObject("level")?.optInt("number", 0) ?: 0

  private fun combatActive(state: JSONObject): Boolean =
    state.optJSONObject("combat")?.optBoolean("active", false) == true

  private fun entityPresent(state: JSONObject): Boolean {
    val flags = state.optJSONObject("flags") ?: return false
    return flags.optString("entityEncounterKey", "").isNotBlank() ||
      flags.optJSONArray("entityEncounterKeys")?.let { it.length() > 0 } == true
  }

  private fun transitionReady(state: JSONObject): Boolean {
    val exploration = state.optJSONObject("flags")?.optJSONObject("exploration") ?: return false
    return exploration.optBoolean("transitionReady", false) || exploration.optBoolean("exitReady", false) ||
      exploration.optBoolean("confirmedExit", false)
  }

  private fun hasCombatIntent(action: String): Boolean {
    val normalized = action.lowercase(Locale.ROOT)
    return combatTerms.any(normalized::contains)
  }

  private fun hasDangerousRollConsequence(rolls: JSONObject): Boolean {
    val consequential = listOf(
      "exitProbe", "levelExit", "entityEncounter", "hazard", "survivor",
      "irisReunion", "syvialReunion", "loot", "madGodSet", "almondWater"
    )
    if (consequential.any { rollSucceeded(rolls, it) }) return true
    if (rolls.optJSONArray("entityEncounterKeys")?.let { it.length() > 0 } == true) return true
    val entityRolls = rolls.optJSONArray("entityRolls") ?: return false
    for (index in 0 until entityRolls.length()) {
      if (entityRolls.optJSONObject(index)?.optBoolean("success", false) == true) return true
    }
    return false
  }

  private fun rollSucceeded(rolls: JSONObject, key: String): Boolean =
    rolls.optJSONObject(key)?.optBoolean("success", false) == true

  private fun hasDangerousModelOperation(ops: JSONArray?): Boolean {
    if (ops == null) return false
    for (index in 0 until ops.length()) {
      val op = ops.optJSONObject(index) ?: return true
      val type = op.optString("type", "").trim().lowercase(Locale.ROOT)
      if (type in dangerousOperationTypes) return true
      if (type == "flag_patch" && op.optString("root", "") !in harmlessFlagRoots) return true
      if (type != "set_location" && type != "flag_patch") return true
    }
    return false
  }

  private fun dangerousStateChanged(before: JSONObject, candidate: JSONObject): Boolean {
    for (key in listOf("level", "player", "party", "inventory", "combat")) {
      if (!jsonEqual(before.opt(key), candidate.opt(key))) return true
    }
    val beforeFlags = before.optJSONObject("flags") ?: JSONObject()
    val candidateFlags = candidate.optJSONObject("flags") ?: JSONObject()
    val roots = mutableSetOf<String>()
    beforeFlags.keys().forEachRemaining(roots::add)
    candidateFlags.keys().forEachRemaining(roots::add)
    return roots.any { root ->
      when {
        root == "lastRolls" -> false
        root == "madGod" && emptyJsonObject(beforeFlags.opt(root)) && emptyJsonObject(candidateFlags.opt(root)) -> false
        root in harmlessFlagRoots -> false
        else -> !jsonEqual(beforeFlags.opt(root), candidateFlags.opt(root))
      }
    }
  }

  private fun emptyJsonObject(value: Any?): Boolean =
    value == null || value === JSONObject.NULL || (value is JSONObject && !value.keys().hasNext())

  private fun jsonEqual(left: Any?, right: Any?): Boolean {
    if (left === right) return true
    if (left == null || left === JSONObject.NULL) return right == null || right === JSONObject.NULL
    if (right == null || right === JSONObject.NULL) return false
    if (left is JSONObject && right is JSONObject) {
      val keys = mutableSetOf<String>()
      left.keys().forEachRemaining(keys::add)
      right.keys().forEachRemaining(keys::add)
      return keys.all { jsonEqual(left.opt(it), right.opt(it)) }
    }
    if (left is JSONArray && right is JSONArray) {
      if (left.length() != right.length()) return false
      return (0 until left.length()).all { jsonEqual(left.opt(it), right.opt(it)) }
    }
    return left == right || left.toString() == right.toString()
  }
}

package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Narrow fail-soft gate for a repaired turn whose final model output still fails canon audit. */
object CanonFallbackPolicy {
  private val harmlessFlagRoots = setOf("exploration", "communication", "visualAreaKey", "visualEventKey")
  private val engineStoryFlagRoots = setOf("storyArc", "storyContinuity", "luciaEncounter")
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
  ): Boolean = diagnostics(before, candidate, generated, rolls, action, meta, repaired).optBoolean("eligible", false)

  @JvmStatic
  fun diagnostics(
    before: JSONObject,
    candidate: JSONObject,
    generated: JSONObject,
    rolls: JSONObject,
    action: String,
    meta: Boolean,
    repaired: Boolean,
  ): JSONObject {
    val level = currentLevel(before)
    val explorationAction = StoryProgressionPolicy.isLevel0ExplorationAction(action)
    val combatIntent = hasCombatIntent(action)
    val combatBefore = combatActive(before)
    val combatCandidate = combatActive(candidate)
    val entityBefore = entityPresent(before)
    val entityCandidate = entityPresent(candidate)
    val transitionBefore = transitionReady(before)
    val transitionCandidate = transitionReady(candidate)
    val dangerousRoll = hasDangerousRollConsequence(rolls)

    val storyBaseline = StoryProgressionPolicy.normalizeCandidate(before, JSONObject(before.toString()), action)
    val baselineFlags = storyBaseline.optJSONObject("flags") ?: JSONObject()
    val candidateFlags = candidate.optJSONObject("flags") ?: JSONObject()

    val changedTopLevel = changedTopLevelKeys(before, candidate)
    val changedFlagRoots = changedFlagRoots(before, candidate)
    val engineStoryPartyDelta =
      "party" in changedTopLevel && jsonEqual(candidate.opt("party"), storyBaseline.opt("party"))
    val engineStoryFlagDeltas = changedFlagRoots.filterTo(linkedSetOf()) { root ->
      root in engineStoryFlagRoots && jsonEqual(candidateFlags.opt(root), baselineFlags.opt(root))
    }

    val dangerousChangedTopLevel = changedTopLevel.filter { key ->
      when {
        key == "location" || key == "flags" -> false
        key == "party" && engineStoryPartyDelta -> false
        else -> true
      }
    }
    val dangerousChangedFlags = changedFlagRoots.filter { root ->
      when {
        root == "lastRolls" -> false
        root in harmlessFlagRoots -> false
        root in engineStoryFlagDeltas -> false
        else -> true
      }
    }
    val dangerousState = dangerousChangedTopLevel.isNotEmpty() || dangerousChangedFlags.isNotEmpty()
    val staleEntityContext = entityBefore && entityCandidate && !combatBefore && !combatCandidate

    val reason = when {
      meta -> "meta_turn"
      !repaired -> "repair_not_attempted"
      combatIntent -> "combat_intent"
      combatBefore -> "combat_active_before"
      combatCandidate -> "combat_active_candidate"
      transitionBefore -> "transition_ready_before"
      transitionCandidate -> "transition_ready_candidate"
      dangerousRoll -> "consequential_roll"
      dangerousState -> "accepted_authoritative_state_change"
      entityCandidate && !staleEntityContext -> "entity_present_candidate"
      else -> "eligible"
    }
    val eligible = reason == "eligible"

    return JSONObject()
      .put("eligible", eligible)
      .put("reason", reason)
      .put("meta", meta)
      .put("repaired", repaired)
      .put("currentLevel", level)
      .put("levelZero", level == 0)
      .put("level0ExplorationAction", explorationAction)
      .put("combatIntent", combatIntent)
      .put("combatActiveBefore", combatBefore)
      .put("combatActiveCandidate", combatCandidate)
      .put("entityPresentBefore", entityBefore)
      .put("entityPresentCandidate", entityCandidate)
      .put("staleEntityContext", staleEntityContext)
      .put("transitionReadyBefore", transitionBefore)
      .put("transitionReadyCandidate", transitionCandidate)
      .put("dangerousRollConsequence", dangerousRoll)
      .put("dangerousStateChanged", dangerousState)
      .put("engineStoryPartyDelta", engineStoryPartyDelta)
      .put("engineStoryFlagRoots", JSONArray(engineStoryFlagDeltas.sorted()))
      .put("storyDirective", StoryProgressionPolicy.directive(before, action))
      .put("changedTopLevelKeys", JSONArray(changedTopLevel.sorted()))
      .put("changedFlagRoots", JSONArray(changedFlagRoots.sorted()))
      .put("dangerousChangedTopLevelKeys", JSONArray(dangerousChangedTopLevel.sorted()))
      .put("dangerousChangedFlagRoots", JSONArray(dangerousChangedFlags.sorted()))
      .put("proposedOpsCount", generated.optJSONArray("ops")?.length() ?: 0)
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
    return exploration.optBoolean("transitionReady", false) ||
      exploration.optBoolean("exitReady", false) ||
      exploration.optBoolean("confirmedExit", false) ||
      exploration.optString("confirmedExit", "").isNotBlank()
  }

  private fun hasCombatIntent(action: String): Boolean {
    val normalized = action.lowercase(Locale.ROOT)
    return combatTerms.any(normalized::contains)
  }

  private fun hasDangerousRollConsequence(rolls: JSONObject): Boolean {
    val consequential = listOf(
      "exitProbe", "levelExit", "entityEncounter", "hazard", "survivor",
      "irisReunion", "syvialReunion", "loot", "almondWater"
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

  private fun changedTopLevelKeys(before: JSONObject, candidate: JSONObject): Set<String> {
    val keys = linkedSetOf<String>()
    before.keys().forEachRemaining(keys::add)
    candidate.keys().forEachRemaining(keys::add)
    return keys.filterTo(linkedSetOf()) { !jsonEqual(before.opt(it), candidate.opt(it)) }
  }

  private fun changedFlagRoots(before: JSONObject, candidate: JSONObject): Set<String> {
    val beforeFlags = before.optJSONObject("flags") ?: JSONObject()
    val candidateFlags = candidate.optJSONObject("flags") ?: JSONObject()
    val roots = linkedSetOf<String>()
    beforeFlags.keys().forEachRemaining(roots::add)
    candidateFlags.keys().forEachRemaining(roots::add)
    return roots.filterTo(linkedSetOf()) { !jsonEqual(beforeFlags.opt(it), candidateFlags.opt(it)) }
  }

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

package com.rabpit.backroom.core

import java.util.Random
import org.json.JSONArray
import org.json.JSONObject

/** Independent dice, with successful encounters persisted until combat can handle them. */
object EntityEncounterPolicy {
  private const val QUEUE = "entity.pendingEncounters"
  private const val SIDES = 10_000
  private const val THRESHOLD = 300

  /**
   * Authoritative encounter pool for the current shared Level 0-6 roaming policy.
   *
   * Keep the pool beside the roll rule so Android/Java and build-time patches never have to
   * duplicate gameplay membership or probability. Ordering is stable because queued successes
   * are resolved in this order.
   */
  private val AUTHORIZED_KEYS = arrayOf(
    "hound",
    "clump",
    "duller",
    "deathmoth",
    "hostile_faceling",
    "false_puddle",
    "paintings",
    "smiler",
    "skin-stealer",
    "predatory_window",
    "biological_pipeline",
    "wretch",
    "cable_mimic",
    "the_beast_of_level_5",
    "hotel_corpse_lure",
    "jeff_the_killer",
    "jane_the_killer",
    "slenderman",
    "diep_minh",
  )

  /** Java bridge entry point. Pool membership and probability stay inside Kotlin Game Core. */
  @JvmStatic fun roll(eligible: Boolean, random: Random): JSONObject =
    rollInternal(AUTHORIZED_KEYS.asList(), eligible, random)

  /**
   * Compatibility/test seam for callers that intentionally exercise a subset.
   * Production Android code must use [roll] without supplying keys.
   */
  @JvmStatic fun roll(keys: Array<String>, eligible: Boolean, random: Random): JSONObject =
    rollInternal(keys.asList(), eligible, random)

  @JvmStatic fun authorizedKeys(): Array<String> = AUTHORIZED_KEYS.copyOf()

  private fun rollInternal(keys: List<String>, eligible: Boolean, random: Random): JSONObject {
    val checks = JSONObject()
    val selected = JSONArray()
    for (key in keys.distinct()) {
      val value = if (eligible) random.nextInt(SIDES) + 1 else 0
      val success = eligible && value <= THRESHOLD
      checks.put(key, JSONObject().put("eligible", eligible).put("sides", SIDES)
        .put("threshold", THRESHOLD).put("roll", value).put("success", success))
      if (success) selected.put(key)
    }
    return JSONObject().put("entityRolls", checks).put("entityEncounterKeys", selected)
      .put("roamingEntityKey", selected.optString(0, ""))
      .put("entityEncounter", JSONObject().put("eligible", eligible)
        .put("success", selected.length() > 0).put("label", "Independent Entity rolls: 3% each"))
  }

  fun enqueue(state: GameState, keys: List<String>): GameState {
    // A repeated bridge call must not duplicate an already active batch.
    if (CombatRuntime.active(state) != null || state.metadata[QUEUE].orEmpty().isNotEmpty()) return state
    val authorized = keys.distinct().filter { it in AUTHORIZED_KEYS }
    if (authorized.isEmpty()) return state
    return advance(state.copy(metadata = state.metadata + (QUEUE to JSONArray(authorized).toString())))
  }

  fun advance(state: GameState): GameState {
    if (CombatRuntime.active(state) != null) return state
    val queue = JSONArray(state.metadata[QUEUE] ?: "[]")
    var next = state
    for (index in 0 until queue.length()) {
      val key = queue.getString(index)
      val tail = JSONArray()
      for (i in index + 1 until queue.length()) {
        val queued = queue.getString(i)
        if (queued in AUTHORIZED_KEYS) tail.put(queued)
      }
      next = next.copy(metadata = if (tail.length() == 0) next.metadata - QUEUE
        else next.metadata + (QUEUE to tail.toString()))
      if (key !in AUTHORIZED_KEYS) continue
      next = CombatRuntime.start(next, key)
      if (CombatRuntime.active(next) != null) return next
    }
    return next.copy(metadata = next.metadata - QUEUE)
  }
}

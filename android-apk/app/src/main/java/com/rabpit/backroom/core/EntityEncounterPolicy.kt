package com.rabpit.backroom.core

import java.util.Random
import org.json.JSONArray
import org.json.JSONObject

/** Independent dice, with successful encounters persisted until combat can handle them. */
object EntityEncounterPolicy {
  private const val QUEUE = "entity.pendingEncounters"

  @JvmStatic fun roll(keys: Array<String>, eligible: Boolean, random: Random): JSONObject {
    val checks = JSONObject()
    val selected = JSONArray()
    for (key in keys.distinct()) {
      val value = if (eligible) random.nextInt(10000) + 1 else 0
      val success = eligible && value <= 200
      checks.put(key, JSONObject().put("eligible", eligible).put("sides", 10000)
        .put("threshold", 200).put("roll", value).put("success", success))
      if (success) selected.put(key)
    }
    return JSONObject().put("entityRolls", checks).put("entityEncounterKeys", selected)
      .put("roamingEntityKey", selected.optString(0, ""))
      .put("entityEncounter", JSONObject().put("eligible", eligible)
        .put("success", selected.length() > 0).put("label", "Independent Entity rolls: 2% each"))
  }

  fun enqueue(state: GameState, keys: List<String>): GameState {
    // A repeated bridge call must not duplicate an already active batch.
    if (CombatRuntime.active(state) != null || state.metadata[QUEUE].orEmpty().isNotEmpty()) return state
    return advance(state.copy(metadata = state.metadata + (QUEUE to JSONArray(keys.distinct()).toString())))
  }

  fun advance(state: GameState): GameState {
    if (CombatRuntime.active(state) != null) return state
    val queue = JSONArray(state.metadata[QUEUE] ?: "[]")
    var next = state
    for (index in 0 until queue.length()) {
      val tail = JSONArray()
      for (i in index + 1 until queue.length()) tail.put(queue.getString(i))
      next = next.copy(metadata = if (tail.length() == 0) next.metadata - QUEUE
        else next.metadata + (QUEUE to tail.toString()))
      next = CombatRuntime.start(next, queue.getString(index))
      if (CombatRuntime.active(next) != null) return next
    }
    return next.copy(metadata = next.metadata - QUEUE)
  }
}

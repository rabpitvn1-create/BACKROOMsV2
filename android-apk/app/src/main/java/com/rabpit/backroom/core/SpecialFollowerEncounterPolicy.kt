package com.rabpit.backroom.core

import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/** Deterministic projection of special-follower encounter rolls into candidate state. */
object SpecialFollowerEncounterPolicy {
  @JvmStatic
  fun apply(beforeJson: String, candidateJson: String, rollsJson: String): String {
    val before = JSONObject(beforeJson)
    val candidate = JSONObject(candidateJson)
    val rolls = JSONObject(rollsJson.ifBlank { "{}" })
    val anNhien = succeeded(rolls, "anNhienEncounter")
    val iris = succeeded(rolls, "irisReunion")
    val syvial = succeeded(rolls, "syvialReunion")
    if (!anNhien && !iris && !syvial) return candidate.toString()

    val flags = candidate.optJSONObject("flags")
      ?: before.optJSONObject("flags")?.let { JSONObject(it.toString()) }
      ?: JSONObject()
    val party = candidate.optJSONArray("party")
      ?.let { JSONArray(it.toString()) }
      ?: before.optJSONArray("party")?.let { JSONArray(it.toString()) }
      ?: JSONArray()
    val level = currentLevel(before)

    if (anNhien) {
      val joined = ensureFollower(party, AN_NHIEN_ID, AnNhienCanon.NAME, nonCombat = true)
      val record = flags.optJSONObject("anNhien") ?: JSONObject()
      record.put("encountered", true)
        .put("present", true)
        .put("follower", true)
        .put("nonCombat", true)
        .put("levelEncountered", level)
        .put("lootBonusPercent", 10)
        .put("exitBonusPercent", 2)
        .put("joinPending", !joined)
      flags.put("anNhien", record)
    }

    if (iris) {
      val joined = ensureFollower(party, IRIS_ID, "Iris", nonCombat = false)
      val record = flags.optJSONObject("iris") ?: JSONObject()
      record.put("exists", true)
        .put("encountered", true)
        .put("present", true)
        .put("spawned", true)
        .put("follower", true)
        .put("reunionEligible", false)
        .put("continuity", "REUNITED")
        .put("levelEncountered", level)
        .put("joinPending", !joined)
      flags.put("iris", record)
    }

    if (syvial) {
      val joined = ensureFollower(party, SYVIAL_ID, "Syvial", nonCombat = false)
      val record = flags.optJSONObject("syvial") ?: JSONObject()
      record.put("exists", true)
        .put("encountered", true)
        .put("present", true)
        .put("spawned", true)
        .put("follower", true)
        .put("reunionEligible", false)
        .put("continuity", "REUNITED")
        .put("levelEncountered", level)
        .put("joinPending", !joined)
      flags.put("syvial", record)
    }

    candidate.put("flags", flags)
    candidate.put("party", party)
    return candidate.toString()
  }

  private fun succeeded(rolls: JSONObject, key: String): Boolean =
    rolls.optJSONObject(key)?.optBoolean("success", false) == true

  /** Legacy candidate party excludes Kai, so three followers means the authoritative party is full. */
  private fun ensureFollower(party: JSONArray, id: String, name: String, nonCombat: Boolean): Boolean {
    for (index in 0 until party.length()) {
      val member = party.optJSONObject(index) ?: continue
      if (member.optString("id").equals(id, ignoreCase = true) ||
        member.optString("name").equals(name, ignoreCase = true)) return true
    }
    if (party.length() >= 3) return false
    party.put(JSONObject()
      .put("id", id)
      .put("name", name)
      .put("present", true)
      .put("joinConfirmed", true)
      .put("presence", "ACTIVE")
      .put("role", "follower")
      .put("nonCombat", nonCombat))
    return true
  }

  private fun currentLevel(state: JSONObject): Int {
    state.optJSONObject("level")?.let { return it.optInt("number", 0).coerceIn(0, 6) }
    val title = state.optString("title", "").lowercase(Locale.ROOT)
    for (level in 0..6) if (title.contains("level $level")) return level
    return 0
  }
}

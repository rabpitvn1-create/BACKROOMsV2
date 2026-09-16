package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject

object AnNhienEncounterPolicy {
  @JvmStatic
  fun apply(beforeJson: String, candidateJson: String, rollsJson: String): String {
    val before = JSONObject(beforeJson)
    val candidate = JSONObject(candidateJson)
    val rolls = JSONObject(rollsJson.ifBlank { "{}" })
    if (rolls.optJSONObject("anNhienEncounter")?.optBoolean("success", false) != true) return candidate.toString()

    val flags = candidate.optJSONObject("flags")
      ?: before.optJSONObject("flags")?.let { JSONObject(it.toString()) }
      ?: JSONObject()
    val record = flags.optJSONObject("anNhien") ?: JSONObject()
    record.put("encountered", true)
      .put("present", true)
      .put("follower", true)
      .put("nonCombat", true)
      .put("levelEncountered", AnNhienCanon.HOME_LEVEL)
      .put("lootBonusPercent", 10)
      .put("exitBonusPercent", 2)
    flags.put("anNhien", record)
    candidate.put("flags", flags)

    val source = candidate.optJSONArray("party")
      ?: before.optJSONArray("party")?.let { JSONArray(it.toString()) }
      ?: JSONArray()
    val party = JSONArray()
    var found = false
    for (index in 0 until source.length()) {
      val value = source.opt(index)
      val member = value as? JSONObject
      val identity = if (member == null) value?.toString().orEmpty().lowercase()
      else "${member.optString("id", "")} ${member.optString("name", "")}".lowercase()
      if (identity.contains(AN_NHIEN_ID) || identity.contains("an nhiên") || identity.contains("an nhien")) {
        if (!found) {
          party.put(member?.let { JSONObject(it.toString()) } ?: follower())
          found = true
        }
      } else party.put(value)
    }
    if (!found) {
      while (party.length() >= 3) party.remove(party.length() - 1)
      party.put(follower())
    }
    candidate.put("party", party)
    return candidate.toString()
  }

  private fun follower(): JSONObject = JSONObject()
    .put("id", AN_NHIEN_ID)
    .put("name", AnNhienCanon.NAME)
    .put("present", true)
    .put("joinConfirmed", true)
    .put("presence", "ACTIVE")
    .put("role", "follower")
    .put("nonCombat", true)
}

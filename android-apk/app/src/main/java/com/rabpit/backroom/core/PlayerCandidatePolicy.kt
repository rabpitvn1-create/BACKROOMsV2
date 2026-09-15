package com.rabpit.backroom.core

import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/**
 * Authoritative sanitizer for provider-proposed legacy player deltas.
 *
 * The provider may suggest a candidate, but only consequences backed by authoritative rolls,
 * explicit recovery intent, or explicit owned-gear intent survive this policy. Java may call the
 * JSON adapter for pre-audit filtering; GameCore rechecks the same policy before commit.
 */
object PlayerCandidatePolicy {
  @JvmStatic
  fun applyPatch(
    beforeStateJson: String,
    currentPlayerJson: String,
    patchJson: String,
    rollsJson: String,
    action: String,
    ownedInventoryJson: String,
  ): String {
    val before = JSONObject(beforeStateJson)
    val current = JSONObject(currentPlayerJson.ifBlank { "{}" })
    val patch = JSONObject(patchJson.ifBlank { "{}" })
    val rolls = JSONObject(rollsJson.ifBlank { "{}" })
    val ownedNames = inventoryNames(JSONArray(ownedInventoryJson.ifBlank { "[]" }))
    return sanitize(current, patch, rolls, action, ownedNames).toString()
  }

  internal fun sanitizeCandidate(
    before: JSONObject,
    candidatePlayer: JSONObject?,
    rolls: JSONObject,
    action: String,
    ownedGearNames: Set<String>,
  ): JSONObject? {
    if (candidatePlayer == null) return null
    val beforePlayer = before.optJSONObject("player")
    val current = if (beforePlayer == null) JSONObject().put("name", "Kai Akechi") else JSONObject(beforePlayer.toString())
    return sanitize(current, candidatePlayer, rolls, action, ownedGearNames)
  }

  private fun sanitize(
    currentRaw: JSONObject,
    patch: JSONObject,
    rolls: JSONObject,
    action: String,
    ownedGearNames: Set<String>,
  ): JSONObject {
    val current = JSONObject(currentRaw.toString())
    val worldConsequence = rollSuccess(rolls, "hazard") || rollSuccess(rolls, "entityEncounter")
    val recoveryIntent = containsAny(
      action,
      "ăn", "uống", "nghỉ", "ngủ", "băng bó", "chữa", "hồi phục",
      "eat", "drink", "rest", "sleep", "heal",
    )
    val gearIntent = containsAny(
      action,
      "rút", "cất", "trang bị", "mặc", "cởi", "tháo", "đeo",
      "draw", "equip", "unequip", "wear",
    )

    if (patch.has("hp") && current.has("hp") && !current.isNull("hp")) {
      val beforeHp = current.optDouble("hp", Double.NaN)
      val afterHp = patch.optDouble("hp", Double.NaN)
      if (!beforeHp.isNaN() && !afterHp.isNaN() && afterHp >= 0.0 &&
        ((afterHp < beforeHp && worldConsequence) || (afterHp >= beforeHp && recoveryIntent))
      ) current.put("hp", afterHp)
    }

    if (patch.has("condition") && (worldConsequence || recoveryIntent)) {
      current.put("condition", patch.optString("condition", current.optString("condition", "")))
    }

    val needsPatch = patch.optJSONObject("needs")
    if (needsPatch != null && recoveryIntent) {
      val needs = current.optJSONObject("needs")?.let { JSONObject(it.toString()) } ?: JSONObject()
      for (key in listOf("thirst", "hunger", "fatigue", "sleepDeprivation")) {
        if (needsPatch.has(key)) needs.put(key, needsPatch.get(key))
      }
      current.put("needs", needs)
    }

    if (gearIntent) {
      for (key in listOf("weapon", "armor")) {
        if (!patch.has(key)) continue
        val proposed = patch.optString(key, "").trim()
        if (proposed.isEmpty()) continue
        val normalized = proposed.lowercase(Locale.ROOT)
        if (ownedGearNames.any { owned -> owned.isNotBlank() && normalized.contains(owned.lowercase(Locale.ROOT)) }) {
          current.put(key, proposed)
        }
      }
    }

    return current
  }

  private fun inventoryNames(inventory: JSONArray): Set<String> {
    val out = linkedSetOf<String>()
    for (index in 0 until inventory.length()) {
      val raw = inventory.opt(index)
      val name = when (raw) {
        is JSONObject -> raw.optString("name", "")
        else -> raw?.toString().orEmpty()
      }.trim()
      if (name.isNotEmpty()) out += name
    }
    return out
  }

  private fun rollSuccess(rolls: JSONObject, key: String): Boolean =
    rolls.optJSONObject(key)?.optBoolean("success", false) == true

  private fun containsAny(value: String, vararg needles: String): Boolean {
    val text = value.lowercase(Locale.ROOT)
    return needles.any { text.contains(it.lowercase(Locale.ROOT)) }
  }
}

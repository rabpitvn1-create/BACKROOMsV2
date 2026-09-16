package com.rabpit.backroom.core

import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/**
 * Authoritative policy for provider `flag_patch` operations.
 *
 * Java uses the JSON adapter only for early preview/audit filtering. The Game Core independently
 * reapplies the accepted operation basis to provider-touched flag fields before commit.
 */
object FlagCandidatePolicy {
  private val alwaysAllowedRoots = setOf(
    "exploration", "communication", "omnivault", "visualAreaKey", "visualEventKey", "reunionPath",
  )

  @JvmStatic
  fun applyOperation(
    beforeStateJson: String,
    currentFlagsJson: String,
    operationJson: String,
    rollsJson: String,
  ): String {
    val before = JSONObject(beforeStateJson)
    val flags = JSONObject(currentFlagsJson.ifBlank { "{}" })
    val operation = JSONObject(operationJson)
    val rolls = JSONObject(rollsJson.ifBlank { "{}" })
    return applyOperation(before, flags, operation, rolls).toString()
  }

  internal fun sanitizeCandidate(
    before: JSONObject,
    candidateFlags: JSONObject?,
    operations: JSONArray,
    rolls: JSONObject,
  ): JSONObject? {
    if (candidateFlags == null) return null
    val beforeFlags = before.optJSONObject("flags") ?: JSONObject()
    var safeFlags = JSONObject(beforeFlags.toString())
    val providerOps = mutableListOf<JSONObject>()

    for (index in 0 until operations.length()) {
      val op = operations.optJSONObject(index) ?: continue
      if (!op.optString("type", "").equals("flag_patch", ignoreCase = true) || !op.has("value")) continue
      providerOps += op
      safeFlags = applyOperation(before, safeFlags, op, rolls)
    }

    val result = JSONObject(candidateFlags.toString())
    for (op in providerOps) {
      val root = op.optString("root", "").trim()
      if (root.isEmpty()) continue
      val value = op.opt("value")
      if (value is JSONObject) {
        val safeRoot = safeFlags.optJSONObject(root)
        val resultRoot = result.optJSONObject(root)?.let { JSONObject(it.toString()) } ?: JSONObject()
        value.keys().forEach { key ->
          if (safeRoot != null && safeRoot.has(key)) resultRoot.put(key, deepCopy(safeRoot.opt(key)))
          else resultRoot.remove(key)
        }
        if (resultRoot.length() == 0 && !safeFlags.has(root)) result.remove(root)
        else result.put(root, resultRoot)
      } else {
        if (safeFlags.has(root)) result.put(root, deepCopy(safeFlags.opt(root)))
        else result.remove(root)
      }
    }
    return result
  }

  private fun applyOperation(
    before: JSONObject,
    currentFlagsRaw: JSONObject,
    operation: JSONObject,
    rolls: JSONObject,
  ): JSONObject {
    val flags = JSONObject(currentFlagsRaw.toString())
    val root = operation.optString("root", "").trim()
    if (root.isEmpty() || !operation.has("value") || !rootAllowed(before, root, rolls)) return flags
    val value = operation.opt("value") ?: return flags
    if (!valueAllowed(before, root, value, rolls)) return flags

    val current = flags.opt(root)
    if (current is JSONObject && value is JSONObject) {
      val merged = JSONObject(current.toString())
      mergeObject(merged, value)
      flags.put(root, merged)
    } else {
      flags.put(root, deepCopy(value))
    }
    return flags
  }

  private fun rootAllowed(before: JSONObject, root: String, rolls: JSONObject): Boolean {
    if (root in alwaysAllowedRoots) return true
    if (root == "iris") return presentCharacter(before, "iris") || rollSuccess(rolls, "irisReunion")
    if (root == "syvial") return presentCharacter(before, "syvial") || rollSuccess(rolls, "syvialReunion")
    if (root in setOf("jeff", "entityRegistry", "entitiesConfirmedLocal", "entityEncounterKey")) {
      val flags = before.optJSONObject("flags")
      return rollSuccess(rolls, "entityEncounter") || (flags?.optInt("entitiesConfirmedLocal", 0) ?: 0) > 0
    }
    if (root in setOf("survivorRegistry", "survivorsConfirmed")) {
      val flags = before.optJSONObject("flags")
      return rollSuccess(rolls, "survivor") || (flags?.optInt("survivorsConfirmed", 0) ?: 0) > 0
    }
    return false
  }

  private fun valueAllowed(before: JSONObject, root: String, value: Any, rolls: JSONObject): Boolean {
    if (root == "exploration" && value is JSONObject) {
      val beforeProgress = before.optJSONObject("flags")
        ?.optJSONObject("exploration")
        ?.optString("exitProgress", "")
        .orEmpty()
      val afterProgress = value.optString("exitProgress", beforeProgress)
      val exitMutation = afterProgress != beforeProgress || value.has("exitCandidate")
      if (exitMutation && !rollSuccess(rolls, "levelExit")) return false
      val ready = containsAny(afterProgress, "READY", "GUARANTEED", "CONDITION MET", "TRANSITION AVAILABLE")
      val prepared = containsAny(beforeProgress, "NEAR", "ALMOST", "VERY STRONG")
      if (ready && !prepared) return false
    }
    if (root == "reunionPath" && value is JSONObject) {
      if (value.has("iris") && containsAny(value.optString("iris", ""), "CONFIRMED", "DIRECT", "ARRIVED", "CONTACT ESTABLISHED") &&
        !rollSuccess(rolls, "irisReunion")) return false
      if (value.has("syvial") && containsAny(value.optString("syvial", ""), "CONFIRMED", "DIRECT", "ARRIVED", "CONTACT ESTABLISHED") &&
        !rollSuccess(rolls, "syvialReunion")) return false
    }
    return true
  }

  private fun presentCharacter(before: JSONObject, key: String): Boolean {
    val party = before.optJSONArray("party")
    if (party != null) {
      for (index in 0 until party.length()) {
        val raw = party.opt(index)
        val identity = when (raw) {
          is JSONObject -> "${raw.optString("id", "")} ${raw.optString("name", "")}"
          else -> raw?.toString().orEmpty()
        }.lowercase(Locale.ROOT)
        if (identity.contains(key.lowercase(Locale.ROOT))) return true
      }
    }
    val continuity = before.optJSONObject("flags")
      ?.optJSONObject(key)
      ?.optString("continuity", "")
      ?.lowercase(Locale.ROOT)
      .orEmpty()
    return listOf("reunited", "with kai", "together", "present").any(continuity::contains)
  }

  private fun rollSuccess(rolls: JSONObject, key: String): Boolean =
    rolls.optJSONObject(key)?.optBoolean("success", false) == true

  private fun containsAny(value: String, vararg needles: String): Boolean {
    val text = value.lowercase(Locale.ROOT)
    return needles.any { text.contains(it.lowercase(Locale.ROOT)) }
  }

  private fun mergeObject(target: JSONObject, patch: JSONObject) {
    patch.keys().forEach { key -> target.put(key, deepCopy(patch.opt(key))) }
  }

  private fun deepCopy(value: Any?): Any? = when (value) {
    is JSONObject -> JSONObject(value.toString())
    is JSONArray -> JSONArray(value.toString())
    else -> value
  }
}

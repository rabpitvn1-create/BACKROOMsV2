package com.rabpit.backroom.core

import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/**
 * Authoritative gate for provider-proposed Party membership changes.
 *
 * Java may call the public helpers to filter obviously invalid provider operations before audit,
 * but the final decision is repeated from trusted Kotlin state at the Game Core commit boundary.
 */
object PartyCandidatePolicy {
  @JvmStatic
  fun allowsProviderAddition(beforeJson: String, rollsJson: String, memberName: String): Boolean =
    allowsProviderAddition(
      JSONObject(beforeJson),
      JSONObject(rollsJson.ifBlank { "{}" }),
      memberName,
    )

  @JvmStatic
  fun allowsRemoval(action: String): Boolean {
    val text = action.lowercase(Locale.ROOT)
    return listOf("rời", "tách", "ở lại", "đuổi", "chia nhóm", "mất dấu").any(text::contains)
  }

  internal fun allowsProviderAddition(before: JSONObject, rolls: JSONObject, memberName: String): Boolean =
    allowsAddition(before, rolls, memberId = "", memberName = memberName, engineAuthorizedJoin = false)

  internal fun allowsCoreAddition(
    before: JSONObject,
    rolls: JSONObject,
    memberId: String,
    memberName: String,
    engineAuthorizedJoin: Boolean,
  ): Boolean = allowsAddition(before, rolls, memberId, memberName, engineAuthorizedJoin)

  private fun allowsAddition(
    before: JSONObject,
    rolls: JSONObject,
    memberId: String,
    memberName: String,
    engineAuthorizedJoin: Boolean,
  ): Boolean {
    val identity = "$memberId $memberName".trim().lowercase(Locale.ROOT)
    if (identity.isEmpty()) return false
    if (engineAuthorizedJoin) return true

    return when {
      isAnNhien(identity) -> anNhienEncountered(before) || rollSuccess(rolls, "anNhienEncounter")
      identity.contains("iris") -> presentCharacter(before, "iris") || rollSuccess(rolls, "irisReunion")
      identity.contains("syvial") -> presentCharacter(before, "syvial") || rollSuccess(rolls, "syvialReunion")
      else -> rollSuccess(rolls, "survivor")
    }
  }

  private fun presentCharacter(before: JSONObject, key: String): Boolean {
    if (partyContains(before.optJSONArray("party"), key)) return true
    val continuity = before.optJSONObject("flags")
      ?.optJSONObject(key)
      ?.optString("continuity", "")
      ?.lowercase(Locale.ROOT)
      .orEmpty()
    return listOf("reunited", "with kai", "together", "present").any(continuity::contains)
  }

  private fun anNhienEncountered(before: JSONObject): Boolean {
    if (partyContains(before.optJSONArray("party"), AN_NHIEN_ID) ||
      partyContains(before.optJSONArray("party"), AnNhienCanon.NAME)
    ) return true
    return before.optJSONObject("flags")
      ?.optJSONObject("anNhien")
      ?.optBoolean("encountered", false) == true
  }

  private fun partyContains(party: JSONArray?, needleRaw: String): Boolean {
    if (party == null) return false
    val needle = needleRaw.lowercase(Locale.ROOT)
    for (index in 0 until party.length()) {
      val raw = party.opt(index)
      val identity = when (raw) {
        is JSONObject -> "${raw.optString("id", "")} ${raw.optString("name", "")}"
        else -> raw?.toString().orEmpty()
      }.lowercase(Locale.ROOT)
      if (identity.contains(needle)) return true
    }
    return false
  }

  private fun isAnNhien(identity: String): Boolean =
    identity.contains(AN_NHIEN_ID) || identity.contains("an nhiên") || identity.contains("an nhien")

  private fun rollSuccess(rolls: JSONObject, key: String): Boolean =
    rolls.optJSONObject(key)?.optBoolean("success", false) == true
}

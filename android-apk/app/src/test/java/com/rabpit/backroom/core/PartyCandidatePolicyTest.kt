package com.rabpit.backroom.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PartyCandidatePolicyTest {
  private fun rolls(vararg successKeys: String): JSONObject {
    val root = JSONObject()
    successKeys.forEach { key -> root.put(key, JSONObject().put("success", true)) }
    return root
  }

  @Test fun genericProviderAdditionRequiresSurvivorRoll() {
    val before = JSONObject("""{"party":[],"flags":{}}""")
    assertFalse(PartyCandidatePolicy.allowsProviderAddition(before, rolls(), "Unknown Survivor"))
    assertTrue(PartyCandidatePolicy.allowsProviderAddition(before, rolls("survivor"), "Unknown Survivor"))
  }

  @Test fun irisRequiresPresenceOrReunionRoll() {
    val empty = JSONObject("""{"party":[],"flags":{}}""")
    assertFalse(PartyCandidatePolicy.allowsProviderAddition(empty, rolls(), "Iris"))
    assertTrue(PartyCandidatePolicy.allowsProviderAddition(empty, rolls("irisReunion"), "Iris"))

    val present = JSONObject("""{"party":[],"flags":{"iris":{"continuity":"REUNITED WITH KAI"}}}""")
    assertTrue(PartyCandidatePolicy.allowsProviderAddition(present, rolls(), "Iris"))
  }

  @Test fun syvialPresenceInPartyIsEnough() {
    val before = JSONObject("""{"party":[{"id":"syvial","name":"Syvial"}],"flags":{}}""")
    assertTrue(PartyCandidatePolicy.allowsProviderAddition(before, rolls(), "Syvial"))
  }

  @Test fun anNhienRequiresEncounterOrDedicatedRoll() {
    val empty = JSONObject("""{"party":[],"flags":{}}""")
    assertFalse(PartyCandidatePolicy.allowsProviderAddition(empty, rolls(), "An Nhiên"))
    assertTrue(PartyCandidatePolicy.allowsProviderAddition(empty, rolls("anNhienEncounter"), "An Nhiên"))

    val encountered = JSONObject("""{"party":[],"flags":{"anNhien":{"encountered":true}}}""")
    assertTrue(PartyCandidatePolicy.allowsProviderAddition(encountered, rolls(), "An Nhiên"))
  }

  @Test fun onlyTrustedCoreAuthorizationBypassesEncounterRolls() {
    val before = JSONObject("""{"party":[],"flags":{}}""")
    assertFalse(PartyCandidatePolicy.allowsProviderAddition(before, rolls(), "Lucia"))
    assertTrue(
      PartyCandidatePolicy.allowsCoreAddition(
        before = before,
        rolls = rolls(),
        memberId = "lucia",
        memberName = "Lucia",
        engineAuthorizedJoin = true,
      )
    )
  }

  @Test fun removalRequiresExplicitDepartureIntent() {
    assertFalse(PartyCandidatePolicy.allowsRemoval("Tôi hỏi Lucia xem cô ấy nghĩ gì"))
    assertTrue(PartyCandidatePolicy.allowsRemoval("Lucia ở lại đây, Kai đi tiếp"))
    assertTrue(PartyCandidatePolicy.allowsRemoval("Tách nhóm và để Syvial rời party"))
  }
}

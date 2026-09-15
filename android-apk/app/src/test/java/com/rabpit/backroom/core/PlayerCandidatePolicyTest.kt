package com.rabpit.backroom.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PlayerCandidatePolicyTest {
  private fun state(player: String = """{"name":"Kai Akechi","hp":100,"condition":"OK","needs":{"thirst":0,"hunger":0},"weapon":"Pistol","armor":"Jacket","marker":"keep"}""") =
    JSONObject().put("player", JSONObject(player))

  private fun rolls(vararg success: String): JSONObject = JSONObject().also { root ->
    success.forEach { key -> root.put(key, JSONObject().put("success", true)) }
  }

  @Test fun damageRequiresAuthoritativeWorldConsequence() {
    val before = state()
    val candidate = JSONObject("""{"hp":70}""")
    val rejected = PlayerCandidatePolicy.sanitizeCandidate(before, candidate, rolls(), "đi tiếp", setOf("Pistol"))!!
    assertEquals(100.0, rejected.getDouble("hp"), 0.0)

    val accepted = PlayerCandidatePolicy.sanitizeCandidate(before, candidate, rolls("hazard"), "đi tiếp", setOf("Pistol"))!!
    assertEquals(70.0, accepted.getDouble("hp"), 0.0)
  }

  @Test fun healingRequiresRecoveryIntent() {
    val before = state("""{"name":"Kai Akechi","hp":50}""")
    val candidate = JSONObject("""{"hp":80}""")
    assertEquals(50.0, PlayerCandidatePolicy.sanitizeCandidate(before, candidate, rolls(), "quan sát hành lang", emptySet())!!.getDouble("hp"), 0.0)
    assertEquals(80.0, PlayerCandidatePolicy.sanitizeCandidate(before, candidate, rolls(), "băng bó và hồi phục", emptySet())!!.getDouble("hp"), 0.0)
  }

  @Test fun conditionAndNeedsStayFailClosedWithoutRecoveryOrConsequence() {
    val before = state()
    val candidate = JSONObject("""{"condition":"INJURED","needs":{"thirst":80,"hunger":70,"providerField":999}}""")
    val rejected = PlayerCandidatePolicy.sanitizeCandidate(before, candidate, rolls(), "đứng yên", emptySet())!!
    assertEquals("OK", rejected.getString("condition"))
    assertEquals(0, rejected.getJSONObject("needs").getInt("thirst"))

    val consequence = PlayerCandidatePolicy.sanitizeCandidate(before, candidate, rolls("entityEncounter"), "đứng yên", emptySet())!!
    assertEquals("INJURED", consequence.getString("condition"))
    assertEquals(0, consequence.getJSONObject("needs").getInt("thirst"))

    val recovery = PlayerCandidatePolicy.sanitizeCandidate(before, candidate, rolls(), "uống nước rồi nghỉ", emptySet())!!
    assertEquals("INJURED", recovery.getString("condition"))
    assertEquals(80, recovery.getJSONObject("needs").getInt("thirst"))
    assertFalse(recovery.getJSONObject("needs").has("providerField"))
  }

  @Test fun gearRequiresExplicitIntentAndTrustedOwnership() {
    val before = state()
    val candidate = JSONObject("""{"weapon":"Black Rifle","armor":"Steel Armor"}""")
    val noIntent = PlayerCandidatePolicy.sanitizeCandidate(before, candidate, rolls(), "kiểm tra súng", setOf("Black Rifle", "Steel Armor"))!!
    assertEquals("Pistol", noIntent.getString("weapon"))
    assertEquals("Jacket", noIntent.getString("armor"))

    val unowned = PlayerCandidatePolicy.sanitizeCandidate(before, candidate, rolls(), "trang bị Black Rifle và Steel Armor", setOf("Black Rifle"))!!
    assertEquals("Black Rifle", unowned.getString("weapon"))
    assertEquals("Jacket", unowned.getString("armor"))
  }

  @Test fun coreSanitizerPreservesTrustedIdentityAndUnknownExistingFields() {
    val before = state()
    val candidate = JSONObject("""{"name":"Injected Name","codename":"Injected","marker":"replace","hp":90}""")
    val sanitized = PlayerCandidatePolicy.sanitizeCandidate(before, candidate, rolls("hazard"), "đi tiếp", emptySet())!!
    assertEquals("Kai Akechi", sanitized.getString("name"))
    assertFalse(sanitized.has("codename"))
    assertEquals("keep", sanitized.getString("marker"))
    assertEquals(90.0, sanitized.getDouble("hp"), 0.0)
  }
}

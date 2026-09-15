package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class FlagCandidatePolicyTest {
  private fun rolls(vararg success: String): JSONObject = JSONObject().also { root ->
    success.forEach { key -> root.put(key, JSONObject().put("success", true)) }
  }

  private fun op(root: String, value: Any): JSONObject = JSONObject()
    .put("type", "flag_patch")
    .put("root", root)
    .put("value", value)

  @Test fun alwaysAllowedRootMergesThroughKotlinPolicy() {
    val before = JSONObject("""{"flags":{"communication":{"radio":"off"}}}""")
    val result = JSONObject(FlagCandidatePolicy.applyOperation(
      before.toString(), before.getJSONObject("flags").toString(),
      op("communication", JSONObject().put("radio", "on")).toString(), "{}"
    ))
    assertEquals("on", result.getJSONObject("communication").getString("radio"))
  }

  @Test fun characterAndEncounterRootsRemainRollGated() {
    val before = JSONObject("""{"party":[],"flags":{}}""")
    val irisRejected = JSONObject(FlagCandidatePolicy.applyOperation(
      before.toString(), "{}", op("iris", JSONObject().put("continuity", "present")).toString(), "{}"
    ))
    assertFalse(irisRejected.has("iris"))

    val irisAccepted = JSONObject(FlagCandidatePolicy.applyOperation(
      before.toString(), "{}", op("iris", JSONObject().put("continuity", "present")).toString(), rolls("irisReunion").toString()
    ))
    assertTrue(irisAccepted.has("iris"))

    val entityRejected = JSONObject(FlagCandidatePolicy.applyOperation(
      before.toString(), "{}", op("entityRegistry", JSONObject().put("hound", true)).toString(), "{}"
    ))
    assertFalse(entityRejected.has("entityRegistry"))

    val entityAccepted = JSONObject(FlagCandidatePolicy.applyOperation(
      before.toString(), "{}", op("entityRegistry", JSONObject().put("hound", true)).toString(), rolls("entityEncounter").toString()
    ))
    assertTrue(entityAccepted.has("entityRegistry"))
  }

  @Test fun explorationExitMutationRequiresRollAndPreparedProgress() {
    val base = JSONObject("""{"flags":{"exploration":{"exitProgress":"NONE","clue":1}}}""")
    val ready = op("exploration", JSONObject().put("exitProgress", "READY").put("exitCandidate", "door"))
    val noRoll = JSONObject(FlagCandidatePolicy.applyOperation(
      base.toString(), base.getJSONObject("flags").toString(), ready.toString(), "{}"
    ))
    assertEquals("NONE", noRoll.getJSONObject("exploration").getString("exitProgress"))

    val premature = JSONObject(FlagCandidatePolicy.applyOperation(
      base.toString(), base.getJSONObject("flags").toString(), ready.toString(), rolls("levelExit").toString()
    ))
    assertEquals("NONE", premature.getJSONObject("exploration").getString("exitProgress"))

    val prepared = JSONObject("""{"flags":{"exploration":{"exitProgress":"NEAR","clue":1}}}""")
    val accepted = JSONObject(FlagCandidatePolicy.applyOperation(
      prepared.toString(), prepared.getJSONObject("flags").toString(), ready.toString(), rolls("levelExit").toString()
    ))
    assertEquals("READY", accepted.getJSONObject("exploration").getString("exitProgress"))
    assertEquals("door", accepted.getJSONObject("exploration").getString("exitCandidate"))
  }

  @Test fun reunionConfirmationRequiresMatchingRoll() {
    val before = JSONObject("""{"flags":{"reunionPath":{"note":"keep"}}}""")
    val patch = op("reunionPath", JSONObject().put("iris", "CONFIRMED"))
    val rejected = JSONObject(FlagCandidatePolicy.applyOperation(
      before.toString(), before.getJSONObject("flags").toString(), patch.toString(), "{}"
    ))
    assertFalse(rejected.getJSONObject("reunionPath").has("iris"))
    assertEquals("keep", rejected.getJSONObject("reunionPath").getString("note"))

    val accepted = JSONObject(FlagCandidatePolicy.applyOperation(
      before.toString(), before.getJSONObject("flags").toString(), patch.toString(), rolls("irisReunion").toString()
    ))
    assertEquals("CONFIRMED", accepted.getJSONObject("reunionPath").getString("iris"))
  }

  @Test fun coreSanitizerRevertsOnlyProviderTouchedFields() {
    val before = JSONObject("""{"flags":{"exploration":{"exitProgress":"NONE","clue":1},"engineOnly":{"tick":1}}}""")
    val candidate = JSONObject("""{
      "exploration":{"exitProgress":"READY","clue":99,"engineMarker":"preserve"},
      "engineOnly":{"tick":2}
    }""")
    val operations = JSONArray().put(op("exploration", JSONObject().put("exitProgress", "READY").put("clue", 99)))
    val sanitized = FlagCandidatePolicy.sanitizeCandidate(before, candidate, operations, rolls())!!
    val exploration = sanitized.getJSONObject("exploration")
    assertEquals("NONE", exploration.getString("exitProgress"))
    assertEquals(1, exploration.getInt("clue"))
    assertEquals("preserve", exploration.getString("engineMarker"))
    assertEquals(2, sanitized.getJSONObject("engineOnly").getInt("tick"))
  }
}

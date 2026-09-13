package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonRecoveryPolicyTest {
  private fun state(level: Int = 0): JSONObject = JSONObject(
    """{
      "turn":1,
      "level":{"number":$level,"name":"Level $level"},
      "player":{"name":"Kai Akechi","hp":100},
      "party":[],
      "inventory":[],
      "flags":{}
    }"""
  )

  private fun generated(): JSONObject = JSONObject()
    .put("reply", "still rejected after repair")
    .put("ops", JSONArray().put(JSONObject().put("type", "unknown_rejected_op")))

  private fun eligible(
    before: JSONObject,
    candidate: JSONObject = JSONObject(before.toString()),
    rolls: JSONObject = JSONObject().put("actionKind", "EXECUTE"),
    action: String = "Tiếp tục hành động đã chọn",
  ): Boolean = CanonRecoveryPolicy.isEligible(
    before,
    candidate,
    generated(),
    rolls,
    action,
    meta = false,
    repaired = true,
  )

  @Test fun harmlessTurnOutsideLevel0MayRecover() {
    assertTrue(eligible(state(level = 2)))
  }

  @Test fun reducerBookkeepingAndRejectedLocationMayRecover() {
    val before = state(level = 3)
    val candidate = JSONObject(before.toString())
    candidate.put("location", "rejected location")
    candidate.getJSONObject("flags")
      .put("lastRolls", JSONObject().put("actionKind", "EXECUTE"))
      .put("madGod", JSONObject())
    assertTrue(eligible(before, candidate))
  }

  @Test fun successfulRollStillFailsClosed() {
    val before = state(level = 2)
    val rolls = JSONObject()
      .put("actionKind", "EXPLORE")
      .put("loot", JSONObject().put("success", true))
    assertFalse(eligible(before, rolls = rolls))
  }

  @Test fun activeEncounterStillFailsClosed() {
    val before = state(level = 2)
    before.put("combat", JSONObject().put("active", true))
    assertFalse(eligible(before))
  }

  @Test fun transitionReadyStillFailsClosed() {
    val before = state(level = 2)
    before.getJSONObject("flags").put(
      "exploration",
      JSONObject().put("confirmedExit", "route-2A"),
    )
    assertFalse(eligible(before))
  }

  @Test fun acceptedAuthoritativeMutationStillFailsClosed() {
    val before = state(level = 2)
    val candidate = JSONObject(before.toString())
    candidate.getJSONObject("player").put("hp", 90)
    assertFalse(eligible(before, candidate))
  }
}

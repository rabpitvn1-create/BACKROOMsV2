package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonFallbackPolicyTest {
  private fun state(): JSONObject = JSONObject(
    """{
      "turn":1,
      "level":{"number":0,"name":"The Lobby"},
      "player":{"name":"Kai Akechi","hp":100},
      "party":[],
      "inventory":[],
      "flags":{"storyArc":{"current":"MAIN.PROLOGUE","currentBeat":"STORY.PROLOGUE.ENTRY_COMPLETE","nextBeat":"STORY.LEVEL0.ARRIVAL","completed":["STORY.PROLOGUE.ENTRY_COMPLETE"]}}
    }"""
  )

  private fun generated(vararg ops: JSONObject): JSONObject =
    JSONObject().put("reply", "invalid after repair").put("ops", JSONArray(ops.toList()))

  private fun rolls(key: String? = null): JSONObject = JSONObject().apply {
    put("actionKind", "EXPLORE")
    if (key != null) put(key, JSONObject().put("eligible", true).put("success", true))
  }

  private fun reducerCandidate(before: JSONObject, dice: JSONObject = rolls()): JSONObject =
    JSONObject(before.toString()).also { candidate ->
      candidate.getJSONObject("flags")
        .put("lastRolls", JSONObject(dice.toString()))
        .put("madGod", JSONObject())
    }

  private fun eligible(
    before: JSONObject = state(),
    candidate: JSONObject = JSONObject(before.toString()),
    output: JSONObject = generated(),
    dice: JSONObject = rolls(),
    action: String = "Khám phá căn phòng vàng và kiểm tra hành lang",
  ): Boolean = CanonFallbackPolicy.isEligible(before, candidate, output, dice, action, meta = false, repaired = true)

  @Test fun harmlessLevel0ExplorationMayRecoverAfterRepairStillFails() {
    assertTrue(eligible(output = generated(JSONObject().put("type", "set_location").put("value", "hành lang kế tiếp"))))
  }

  @Test fun reducerBookkeepingDoesNotBlockSafeFallback() {
    val before = state()
    val dice = rolls()
    val candidate = reducerCandidate(before, dice)
    assertTrue(
      eligible(
        before = before,
        candidate = candidate,
        output = generated(JSONObject().put("type", "set_location").put("value", "hành lang kế tiếp")),
        dice = dice,
      )
    )
  }

  @Test fun realMadGodStateChangeStillFailsClosed() {
    val before = state()
    val candidate = reducerCandidate(before)
    candidate.getJSONObject("flags").getJSONObject("madGod").put("spawned", true)
    assertFalse(eligible(before = before, candidate = candidate))
  }

  @Test fun consequentialDiscoveryRollsStillFailClosed() {
    for (key in listOf("survivor", "irisReunion", "syvialReunion", "loot", "madGodSet", "almondWater")) {
      val before = state()
      val dice = rolls(key)
      assertFalse("successful roll $key must fail closed", eligible(before = before, candidate = reducerCandidate(before, dice), dice = dice))
    }
  }

  @Test fun combatRelatedIssueFailsClosed() {
    val before = state().put("combat", JSONObject().put("active", true))
    assertFalse(eligible(before = before))
    assertFalse(eligible(action = "Khám phá rồi tấn công mục tiêu trong hành lang"))
  }

  @Test fun entityEncounterAndHazardFailClosed() {
    assertFalse(eligible(dice = rolls("entityEncounter")))
    assertFalse(eligible(dice = rolls("hazard")))
    assertFalse(eligible(dice = rolls().put("entityEncounterKeys", JSONArray().put("hound"))))
  }

  @Test fun levelTransitionFailsClosed() {
    val before = state()
    before.getJSONObject("flags").put("exploration", JSONObject().put("transitionReady", true))
    assertFalse(eligible(before = before))
    assertFalse(eligible(dice = rolls("exitProbe")))
  }

  @Test fun dangerousPartyInventoryPlayerAndStateMutationsFailClosed() {
    for (type in listOf("party_upsert", "inventory_remove", "patch_player", "set_level")) {
      assertFalse("operation $type must fail closed", eligible(output = generated(JSONObject().put("type", type))))
    }
    assertFalse(eligible(output = generated(JSONObject().put("type", "flag_patch").put("root", "storyArc").put("value", JSONObject()))))

    val changed = state()
    changed.getJSONObject("player").put("hp", 1)
    assertFalse(eligible(candidate = changed))
  }

  @Test fun fallbackCandidateStillAdvancesArrivalDeterministicallyWithoutLucia() {
    val before = state()
    val safeCandidate = JSONObject(before.toString())
    assertTrue(eligible(before = before, candidate = safeCandidate))

    val normalized = StoryProgressionPolicy.normalizeCandidate(
      before,
      safeCandidate,
      "Khám phá căn phòng vàng và kiểm tra hành lang",
    )
    val flags = normalized.getJSONObject("flags")
    val arc = flags.getJSONObject("storyArc")
    assertEquals(StoryProgressionPolicy.LEVEL0_ARRIVAL, arc.getString("currentBeat"))
    assertFalse(arc.getJSONArray("completed").toString().contains(StoryProgressionPolicy.LEVEL0_FIRST_CONTACT))
    assertFalse(flags.has("luciaEncounter"))
    assertEquals(0, normalized.getJSONArray("party").length())
  }
}

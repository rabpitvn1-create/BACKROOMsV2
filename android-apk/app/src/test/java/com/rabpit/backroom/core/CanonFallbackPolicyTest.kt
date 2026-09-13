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

  @Test fun rejectedDangerousModelOperationDoesNotBlockSafeFallbackWhenReducerStateIsUnchanged() {
    val before = state()
    val dice = rolls()
    val candidate = reducerCandidate(before, dice)
    val rejectedOps = listOf(
      JSONObject().put("type", "party_upsert").put("member", JSONObject().put("name", "Unknown Survivor")),
      JSONObject().put("type", "inventory_remove").put("name", "Missing Item"),
      JSONObject().put("type", "patch_player").put("patch", JSONObject().put("hp", 1)),
      JSONObject().put("type", "set_level").put("level", JSONObject().put("number", 1)),
      JSONObject().put("type", "flag_patch").put("root", "storyArc").put("value", JSONObject()),
      JSONObject().put("type", "totally_unknown_operation"),
    )

    for (op in rejectedOps) {
      assertTrue(
        "rejected operation ${op.optString("type")} must not defeat the no-op fallback",
        eligible(before = before, candidate = candidate, output = generated(op), dice = dice),
      )
    }
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

  @Test fun confirmedExitStringFailsClosed() {
    val before = state()
    before.getJSONObject("flags").put(
      "exploration",
      JSONObject().put("confirmedExit", "Level 0 exit route A-17"),
    )
    assertFalse(eligible(before = before))
  }

  @Test fun acceptedPartyInventoryPlayerLevelAndFlagMutationsFailClosed() {
    val beforeParty = state()
    val changedParty = reducerCandidate(beforeParty)
    changedParty.getJSONArray("party").put(JSONObject().put("id", "survivor").put("name", "Survivor"))
    assertFalse(eligible(before = beforeParty, candidate = changedParty))

    val beforeInventory = state()
    val changedInventory = reducerCandidate(beforeInventory)
    changedInventory.getJSONArray("inventory").put(JSONObject().put("name", "Almond Water"))
    assertFalse(eligible(before = beforeInventory, candidate = changedInventory))

    val beforePlayer = state()
    val changedPlayer = reducerCandidate(beforePlayer)
    changedPlayer.getJSONObject("player").put("hp", 1)
    assertFalse(eligible(before = beforePlayer, candidate = changedPlayer))

    val beforeLevel = state()
    val changedLevel = reducerCandidate(beforeLevel)
    changedLevel.put("level", JSONObject().put("number", 1).put("name", "Parking Zone"))
    changedLevel.put("title", "Level 1 – Parking Zone")
    assertFalse(eligible(before = beforeLevel, candidate = changedLevel))

    val beforeFlags = state()
    val changedFlags = reducerCandidate(beforeFlags)
    changedFlags.getJSONObject("flags").getJSONObject("storyArc").put("currentBeat", "UNTRUSTED")
    assertFalse(eligible(before = beforeFlags, candidate = changedFlags))
  }

  @Test fun unknownTopLevelMutationFailsClosed() {
    val before = state()
    val candidate = reducerCandidate(before).put("futureAuthoritativeField", JSONObject().put("changed", true))
    assertFalse(eligible(before = before, candidate = candidate))
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

package com.rabpit.backroom.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryProgressionPolicyTest {
  private fun startup(): JSONObject = JSONObject(
    """{
      "turn":1,
      "level":{"number":0,"name":"The Lobby"},
      "party":[],
      "flags":{"storyArc":{"current":"MAIN.PROLOGUE","currentBeat":"STORY.PROLOGUE.ENTRY_COMPLETE","nextBeat":"STORY.LEVEL0.ARRIVAL","completed":["STORY.PROLOGUE.ENTRY_COMPLETE"]}}
    }"""
  )

  private fun arrivalState(): JSONObject = startup().also { state ->
    val arc = state.getJSONObject("flags").getJSONObject("storyArc")
    arc.put("current", "MAIN.LEVEL0")
    arc.put("currentBeat", StoryProgressionPolicy.LEVEL0_ARRIVAL)
    arc.put("nextBeat", StoryProgressionPolicy.LEVEL0_FIRST_CONTACT)
    arc.getJSONArray("completed").put(StoryProgressionPolicy.LEVEL0_ARRIVAL)
    state.put("turn", 2)
  }

  private fun firstContactState(): JSONObject = arrivalState().also { state ->
    val flags = state.getJSONObject("flags")
    val arc = flags.getJSONObject("storyArc")
    arc.put("currentBeat", StoryProgressionPolicy.LEVEL0_FIRST_CONTACT)
    arc.put("nextBeat", "STORY.LEVEL0.LUCIA_DECISION")
    arc.getJSONArray("completed").put(StoryProgressionPolicy.LEVEL0_FIRST_CONTACT)
    flags.put(
      "luciaEncounter",
      JSONObject()
        .put("status", "met")
        .put("level", 0)
        .put("partyEligible", true)
        .put("joinPending", true)
    )
    state.put("turn", 3)
  }

  @Test fun meaningfulLevel0ExplorationAdvancesArrivalDeterministically() {
    val before = startup()
    val candidate = JSONObject(before.toString()).put("turn", 2)

    val normalized = StoryProgressionPolicy.normalizeCandidate(before, candidate, "Khám phá căn phòng vàng và kiểm tra hành lang")
    val arc = normalized.getJSONObject("flags").getJSONObject("storyArc")
    val completed = arc.getJSONArray("completed").toString()

    assertEquals("MAIN.LEVEL0", arc.getString("current"))
    assertEquals(StoryProgressionPolicy.LEVEL0_ARRIVAL, arc.getString("currentBeat"))
    assertEquals(StoryProgressionPolicy.LEVEL0_FIRST_CONTACT, arc.getString("nextBeat"))
    assertTrue(completed.contains(StoryProgressionPolicy.LEVEL0_ARRIVAL))
  }

  @Test fun sameTurnCannotCollapseArrivalIntoLuciaFirstContact() {
    val before = startup()
    val candidate = JSONObject(
      """{
        "turn":2,
        "level":{"number":0,"name":"The Lobby"},
        "party":[{"id":"lucia","name":"Lucia"}],
        "flags":{
          "storyArc":{"current":"MAIN.LEVEL0","currentBeat":"STORY.LEVEL0.FIRST_CONTACT_COMPLETE","nextBeat":"STORY.LEVEL0.LUCIA_DECISION","completed":["STORY.PROLOGUE.ENTRY_COMPLETE","STORY.LEVEL0.ARRIVAL","STORY.LEVEL0.FIRST_CONTACT_COMPLETE"]},
          "luciaEncounter":{"status":"met","level":0,"partyEligible":true,"joinPending":true}
        }
      }"""
    )

    val normalized = StoryProgressionPolicy.normalizeCandidate(before, candidate, "explore level zero carefully")
    val flags = normalized.getJSONObject("flags")
    val arc = flags.getJSONObject("storyArc")

    assertEquals(StoryProgressionPolicy.LEVEL0_ARRIVAL, arc.getString("currentBeat"))
    assertFalse(arc.getJSONArray("completed").toString().contains(StoryProgressionPolicy.LEVEL0_FIRST_CONTACT))
    assertFalse(flags.has("luciaEncounter"))
    assertEquals(0, normalized.getJSONArray("party").length())
  }

  @Test fun nonExplorationActionDoesNotConsumeArrivalBeat() {
    val before = startup()
    val candidate = JSONObject(before.toString()).put("turn", 2)

    val normalized = StoryProgressionPolicy.normalizeCandidate(before, candidate, "Kiểm tra inventory của Kai")
    val arc = normalized.getJSONObject("flags").getJSONObject("storyArc")

    assertEquals(StoryProgressionPolicy.PROLOGUE_ENTRY, arc.getString("currentBeat"))
    assertFalse(arc.getJSONArray("completed").toString().contains(StoryProgressionPolicy.LEVEL0_ARRIVAL))
  }

  @Test fun arrivalCompletedCandidatePartyLuciaBeforeFirstContactIsRemoved() {
    val before = arrivalState()
    val candidate = JSONObject(before.toString()).put("turn", 3)
    candidate.put("party", org.json.JSONArray().put(JSONObject().put("id", "lucia").put("name", "Lucia")))

    val normalized = StoryProgressionPolicy.normalizeCandidate(before, candidate, "Tiếp tục khám phá Level 0")

    assertEquals(0, normalized.getJSONArray("party").length())
    assertEquals(
      StoryProgressionPolicy.LEVEL0_ARRIVAL,
      normalized.getJSONObject("flags").getJSONObject("storyArc").getString("currentBeat")
    )
  }

  @Test fun arrivalCompletedEncounterWithoutFirstContactCompletionIsRemoved() {
    val before = arrivalState()
    val candidate = JSONObject(before.toString()).put("turn", 3)
    candidate.getJSONObject("flags").put(
      "luciaEncounter",
      JSONObject().put("status", "met").put("level", 0).put("partyEligible", true).put("joinPending", true)
    )

    val normalized = StoryProgressionPolicy.normalizeCandidate(before, candidate, "Tiếp tục khám phá Level 0")
    val flags = normalized.getJSONObject("flags")

    assertFalse(flags.has("luciaEncounter"))
    assertFalse(flags.getJSONObject("storyArc").getJSONArray("completed").toString().contains(StoryProgressionPolicy.LEVEL0_FIRST_CONTACT))
  }

  @Test fun validFirstContactTurnKeepsEncounterButStillRemovesLuciaFromParty() {
    val before = arrivalState()
    val candidate = JSONObject(before.toString()).put("turn", 3)
    candidate.put("party", org.json.JSONArray().put(JSONObject().put("id", "lucia").put("name", "Lucia")))
    val flags = candidate.getJSONObject("flags")
    val arc = flags.getJSONObject("storyArc")
    arc.put("currentBeat", StoryProgressionPolicy.LEVEL0_FIRST_CONTACT)
    arc.put("nextBeat", "STORY.LEVEL0.LUCIA_DECISION")
    arc.getJSONArray("completed").put(StoryProgressionPolicy.LEVEL0_FIRST_CONTACT)
    flags.put(
      "luciaEncounter",
      JSONObject().put("status", "met").put("level", 0).put("partyEligible", true).put("joinPending", true)
    )

    val normalized = StoryProgressionPolicy.normalizeCandidate(before, candidate, "Tiếp tục đi sâu hơn qua Level 0")
    val normalizedFlags = normalized.getJSONObject("flags")
    val normalizedArc = normalizedFlags.getJSONObject("storyArc")

    assertTrue(normalizedFlags.has("luciaEncounter"))
    assertTrue(normalizedArc.getJSONArray("completed").toString().contains(StoryProgressionPolicy.LEVEL0_FIRST_CONTACT))
    assertFalse(normalizedArc.getJSONArray("completed").toString().contains(StoryProgressionPolicy.LEVEL0_LUCIA_DECISION_COMPLETE))
    assertEquals(0, normalized.getJSONArray("party").length())
  }

  @Test fun laterTurnMayKeepValidatedFirstContactAfterArrivalAlreadyExists() {
    val before = arrivalState()
    val candidate = JSONObject(before.toString()).put("turn", 3)
    val flags = candidate.getJSONObject("flags")
    val arc = flags.getJSONObject("storyArc")
    arc.put("currentBeat", StoryProgressionPolicy.LEVEL0_FIRST_CONTACT)
    arc.put("nextBeat", "STORY.LEVEL0.LUCIA_DECISION")
    arc.getJSONArray("completed").put(StoryProgressionPolicy.LEVEL0_FIRST_CONTACT)
    flags.put("luciaEncounter", JSONObject().put("status", "met").put("level", 0).put("partyEligible", true).put("joinPending", true))

    val normalized = StoryProgressionPolicy.normalizeCandidate(before, candidate, "Tiếp tục đi sâu hơn qua Level 0")
    val normalizedFlags = normalized.getJSONObject("flags")

    assertEquals(StoryProgressionPolicy.LEVEL0_FIRST_CONTACT, normalizedFlags.getJSONObject("storyArc").getString("currentBeat"))
    assertTrue(normalizedFlags.has("luciaEncounter"))
    assertTrue(normalizedFlags.getJSONObject("storyArc").getJSONArray("completed").toString().contains(StoryProgressionPolicy.LEVEL0_ARRIVAL))
  }

  @Test fun luciaMayEnterPartyOnlyAfterDecisionAndJoinConfirmation() {
    val before = firstContactState()
    val candidate = JSONObject(before.toString()).put("turn", 4)
    candidate.put("party", org.json.JSONArray().put(JSONObject().put("id", "lucia").put("name", "Lucia")))
    val flags = candidate.getJSONObject("flags")
    val arc = flags.getJSONObject("storyArc")
    arc.put("currentBeat", StoryProgressionPolicy.LEVEL0_LUCIA_DECISION_COMPLETE)
    arc.put("nextBeat", "STORY.LEVEL0.EPSILON.ENTRY")
    arc.getJSONArray("completed").put(StoryProgressionPolicy.LEVEL0_LUCIA_DECISION_COMPLETE)
    flags.put(
      "luciaEncounter",
      JSONObject().put("status", "joined").put("level", 0).put("partyEligible", true).put("joinPending", false)
    )

    val normalized = StoryProgressionPolicy.normalizeCandidate(before, candidate, "Kai và Lucia quyết định tiếp tục đi cùng nhau")
    val normalizedFlags = normalized.getJSONObject("flags")

    assertEquals(1, normalized.getJSONArray("party").length())
    assertEquals("lucia", normalized.getJSONArray("party").getJSONObject(0).getString("id"))
    assertTrue(normalizedFlags.getJSONObject("storyArc").getJSONArray("completed").toString().contains(StoryProgressionPolicy.LEVEL0_LUCIA_DECISION_COMPLETE))
    assertEquals("joined", normalizedFlags.getJSONObject("luciaEncounter").getString("status"))
  }
}

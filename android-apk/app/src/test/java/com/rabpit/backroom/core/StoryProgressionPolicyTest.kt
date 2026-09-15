package com.rabpit.backroom.core

import org.json.JSONArray
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
        .put("relationship", "initial_tactical_trust")
    )
    state.put("turn", 3)
  }

  @Test fun firstExploreCompletesArrivalOnly() {
    val before = startup()
    val raw = JSONObject(before.toString()).put("turn", 2)
    raw.put("party", JSONArray().put(JSONObject().put("id", "lucia").put("name", "Lucia")))
    raw.getJSONObject("flags").put("luciaEncounter", JSONObject().put("status", "joined"))

    val normalized = StoryProgressionPolicy.normalizeCandidate(before, raw, "Khám phá căn phòng vàng và kiểm tra hành lang")
    val flags = normalized.getJSONObject("flags")
    val arc = flags.getJSONObject("storyArc")

    assertEquals(StoryProgressionPolicy.LEVEL0_ARRIVAL, arc.getString("currentBeat"))
    assertEquals(StoryProgressionPolicy.LEVEL0_FIRST_CONTACT, arc.getString("nextBeat"))
    assertFalse(arc.getJSONArray("completed").toString().contains(StoryProgressionPolicy.LEVEL0_FIRST_CONTACT))
    assertFalse(flags.has("luciaEncounter"))
    assertEquals(0, normalized.getJSONArray("party").length())
    assertTrue(StoryProgressionPolicy.directive(before, "Khám phá hành lang").startsWith("ARRIVAL_ONLY"))
  }

  @Test fun nonExploreCannotAdvanceOrInjectStoryState() {
    val before = startup()
    val raw = JSONObject(before.toString()).put("turn", 2)
    val flags = raw.getJSONObject("flags")
    flags.put("luciaEncounter", JSONObject().put("status", "joined"))
    flags.getJSONObject("storyArc")
      .put("currentBeat", StoryProgressionPolicy.LEVEL0_LUCIA_DECISION_COMPLETE)
      .getJSONArray("completed").put(StoryProgressionPolicy.LEVEL0_LUCIA_DECISION_COMPLETE)

    val normalized = StoryProgressionPolicy.normalizeCandidate(before, raw, "Kiểm tra inventory của Kai")
    val normalizedFlags = normalized.getJSONObject("flags")
    assertEquals(StoryProgressionPolicy.PROLOGUE_ENTRY, normalizedFlags.getJSONObject("storyArc").getString("currentBeat"))
    assertFalse(normalizedFlags.has("luciaEncounter"))
  }

  @Test fun explorationAfterArrivalCreatesFirstContactWithoutModelStoryOps() {
    val before = arrivalState()
    val raw = JSONObject(before.toString()).put("turn", 3)

    val normalized = StoryProgressionPolicy.normalizeCandidate(before, raw, "Tiếp tục khám phá Level 0")
    val flags = normalized.getJSONObject("flags")
    val arc = flags.getJSONObject("storyArc")
    val encounter = flags.getJSONObject("luciaEncounter")

    assertEquals(StoryProgressionPolicy.LEVEL0_FIRST_CONTACT, arc.getString("currentBeat"))
    assertTrue(arc.getJSONArray("completed").toString().contains(StoryProgressionPolicy.LEVEL0_FIRST_CONTACT))
    assertEquals("met", encounter.getString("status"))
    assertTrue(encounter.getBoolean("joinPending"))
    assertEquals(0, normalized.getJSONArray("party").length())
    assertTrue(StoryProgressionPolicy.directive(before, "Tiếp tục khám phá Level 0").startsWith("LUCIA_FIRST_CONTACT"))
  }

  @Test fun arrivalHoldRejectsProviderInventedFirstContact() {
    val before = arrivalState()
    val raw = JSONObject(before.toString()).put("turn", 3)
    val flags = raw.getJSONObject("flags")
    flags.getJSONObject("storyArc").getJSONArray("completed").put(StoryProgressionPolicy.LEVEL0_FIRST_CONTACT)
    flags.put("luciaEncounter", JSONObject().put("status", "met").put("partyEligible", true).put("joinPending", true))

    val normalized = StoryProgressionPolicy.normalizeCandidate(before, raw, "Kiểm tra lại trang bị")
    val normalizedFlags = normalized.getJSONObject("flags")
    assertEquals(StoryProgressionPolicy.LEVEL0_ARRIVAL, normalizedFlags.getJSONObject("storyArc").getString("currentBeat"))
    assertFalse(normalizedFlags.has("luciaEncounter"))
  }

  @Test fun firstContactDoesNotJoinWithoutExplicitTogetherDecision() {
    val before = firstContactState()
    val raw = JSONObject(before.toString()).put("turn", 4)
    raw.put("party", JSONArray().put(JSONObject().put("id", "lucia").put("name", "Lucia")))
    val flags = raw.getJSONObject("flags")
    flags.getJSONObject("storyArc").getJSONArray("completed").put(StoryProgressionPolicy.LEVEL0_LUCIA_DECISION_COMPLETE)
    flags.put("luciaEncounter", JSONObject().put("status", "joined").put("partyEligible", true).put("joinPending", false))

    val normalized = StoryProgressionPolicy.normalizeCandidate(before, raw, "Quan sát hành lang phía trước")
    val normalizedFlags = normalized.getJSONObject("flags")
    assertEquals(StoryProgressionPolicy.LEVEL0_FIRST_CONTACT, normalizedFlags.getJSONObject("storyArc").getString("currentBeat"))
    assertEquals("met", normalizedFlags.getJSONObject("luciaEncounter").getString("status"))
    assertEquals(0, normalized.getJSONArray("party").length())
  }

  @Test fun explicitTogetherDecisionJoinsLuciaWithoutProviderStateMutation() {
    val before = firstContactState()
    val raw = JSONObject(before.toString()).put("turn", 4)

    val normalized = StoryProgressionPolicy.normalizeCandidate(before, raw, "Kai và Lucia quyết định tiếp tục đi cùng nhau")
    val flags = normalized.getJSONObject("flags")
    val arc = flags.getJSONObject("storyArc")
    val encounter = flags.getJSONObject("luciaEncounter")

    assertEquals(StoryProgressionPolicy.LEVEL0_LUCIA_DECISION_COMPLETE, arc.getString("currentBeat"))
    assertTrue(arc.getJSONArray("completed").toString().contains(StoryProgressionPolicy.LEVEL0_LUCIA_DECISION_COMPLETE))
    assertEquals("joined", encounter.getString("status"))
    assertFalse(encounter.getBoolean("joinPending"))
    assertEquals("mutual-party-decision", encounter.getString("playerAgency"))
    assertEquals(1, normalized.getJSONArray("party").length())
    assertEquals("lucia", normalized.getJSONArray("party").getJSONObject(0).getString("id"))
    assertTrue(StoryProgressionPolicy.directive(before, "Kai và Lucia đi cùng nhau").startsWith("LUCIA_JOIN_DECISION"))
  }

  @Test fun completedDecisionPreservesLaterStoryBeatAndLuciaParty() {
    val before = firstContactState()
    val beforeFlags = before.getJSONObject("flags")
    val arc = beforeFlags.getJSONObject("storyArc")
    arc.getJSONArray("completed").put(StoryProgressionPolicy.LEVEL0_LUCIA_DECISION_COMPLETE)
    arc.put("currentBeat", "STORY.LEVEL0.EPSILON.ENTRY")
    arc.put("nextBeat", "STORY.LEVEL0.EPSILON.COMPLETE")
    beforeFlags.put("luciaEncounter", JSONObject().put("status", "joined").put("partyEligible", true).put("joinPending", false))
    before.put("party", JSONArray().put(JSONObject().put("id", "lucia").put("name", "Lucia")))

    val raw = JSONObject(before.toString()).put("turn", 5)
    raw.getJSONObject("flags").getJSONObject("storyArc").put("currentBeat", "MODEL_TRIED_TO_REWRITE_STORY")
    raw.put("party", JSONArray())

    val normalized = StoryProgressionPolicy.normalizeCandidate(before, raw, "Tiếp tục hành trình")
    assertEquals("STORY.LEVEL0.EPSILON.ENTRY", normalized.getJSONObject("flags").getJSONObject("storyArc").getString("currentBeat"))
    assertEquals(1, normalized.getJSONArray("party").length())
  }
}

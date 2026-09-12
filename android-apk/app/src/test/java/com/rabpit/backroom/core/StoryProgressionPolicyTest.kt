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
          "luciaEncounter":{"status":"met","partyEligible":true}
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

  @Test fun laterTurnMayKeepValidatedFirstContactAfterArrivalAlreadyExists() {
    val before = startup()
    val beforeArc = before.getJSONObject("flags").getJSONObject("storyArc")
    beforeArc.put("current", "MAIN.LEVEL0")
    beforeArc.put("currentBeat", StoryProgressionPolicy.LEVEL0_ARRIVAL)
    beforeArc.put("nextBeat", StoryProgressionPolicy.LEVEL0_FIRST_CONTACT)
    beforeArc.getJSONArray("completed").put(StoryProgressionPolicy.LEVEL0_ARRIVAL)

    val candidate = JSONObject(before.toString()).put("turn", 3)
    val flags = candidate.getJSONObject("flags")
    val arc = flags.getJSONObject("storyArc")
    arc.put("currentBeat", StoryProgressionPolicy.LEVEL0_FIRST_CONTACT)
    arc.getJSONArray("completed").put(StoryProgressionPolicy.LEVEL0_FIRST_CONTACT)
    flags.put("luciaEncounter", JSONObject().put("status", "met"))

    val normalized = StoryProgressionPolicy.normalizeCandidate(before, candidate, "Tiếp tục đi sâu hơn qua Level 0")
    val normalizedFlags = normalized.getJSONObject("flags")

    assertEquals(StoryProgressionPolicy.LEVEL0_FIRST_CONTACT, normalizedFlags.getJSONObject("storyArc").getString("currentBeat"))
    assertTrue(normalizedFlags.has("luciaEncounter"))
    assertTrue(normalizedFlags.getJSONObject("storyArc").getJSONArray("completed").toString().contains(StoryProgressionPolicy.LEVEL0_ARRIVAL))
  }
}

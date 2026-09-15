package com.rabpit.backroom.core

import java.util.Random
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GameplayRollPolicyTest {
  private class RecordingRandom(private val value: Int = 0) : Random() {
    val bounds = mutableListOf<Int>()
    override fun nextInt(bound: Int): Int {
      bounds += bound
      return value.coerceIn(0, bound - 1)
    }
  }

  @Test fun metaTurnDoesNotRollOrExposeActionKind() {
    val random = RecordingRandom()
    val rolls = GameplayRollPolicy.roll(
      JSONObject("""{"turn":9,"level":{"number":3}}"""),
      "EXPLORE",
      "đi tiếp",
      true,
      random,
    )

    assertEquals(9, rolls.getInt("turn"))
    assertTrue(rolls.getBoolean("meta"))
    assertFalse(rolls.has("actionKind"))
    assertTrue(random.bounds.isEmpty())
  }

  @Test fun genericThresholdsMatchSettledLevelFourRuntime() {
    val random = RecordingRandom()
    val state = JSONObject("""
      {
        "turn":7,
        "level":{"number":4},
        "flags":{
          "iris":{"exists":true,"continuity":"SEPARATED"},
          "syvial":{"exists":true,"continuity":"LOST"}
        }
      }
    """.trimIndent())
    val rolls = GameplayRollPolicy.roll(state, "SEARCH", "tìm nước almond và kiểm tra khu vực", false, random)

    assertEquals("SEARCH", rolls.getString("actionKind"))
    assertEquals(200, rolls.getJSONObject("survivor").getInt("threshold"))
    assertEquals(25, rolls.getJSONObject("irisReunion").getInt("threshold"))
    assertEquals(1_000_000, rolls.getJSONObject("irisReunion").getInt("max"))
    assertEquals(25, rolls.getJSONObject("syvialReunion").getInt("threshold"))
    assertEquals(300, rolls.getJSONObject("hazard").getInt("threshold"))
    assertEquals(180, rolls.getJSONObject("loot").getInt("threshold"))
    assertEquals(1, rolls.getJSONObject("madGodSet").getInt("threshold"))
    assertEquals(120, rolls.getJSONObject("almondWater").getInt("threshold"))
    assertFalse(rolls.getJSONObject("entityEncounter").getBoolean("success"))
    assertFalse(rolls.getJSONObject("exitProbe").getBoolean("eligible"))
    assertEquals(listOf(10_000, 1_000_000, 1_000_000, 10_000, 10_000, 10_000, 10_000), random.bounds)
  }

  @Test fun exploreDelegatesTheCompleteEntityBatchToEntityPolicy() {
    val random = RecordingRandom()
    val state = JSONObject("""{"turn":2,"level":{"number":1},"flags":{"entityEncountersAllowed":true}}""")
    val rolls = GameplayRollPolicy.roll(state, "EXPLORE", "đi tiếp qua hành lang", false, random)

    assertEquals(EntityEncounterPolicy.authorizedKeys().size, rolls.getJSONArray("entityEncounterKeys").length())
    assertTrue(rolls.getJSONObject("entityEncounter").getBoolean("success"))
    EntityEncounterPolicy.authorizedKeys().forEach { key ->
      val check = rolls.getJSONObject("entityRolls").getJSONObject(key)
      assertEquals(10_000, check.getInt("sides"))
      assertEquals(300, check.getInt("threshold"))
      assertTrue(check.getBoolean("success"))
    }
    // survivor + hazard, then one independent draw per Entity, then the eligible exit probe.
    assertEquals(3 + EntityEncounterPolicy.authorizedKeys().size, random.bounds.size)
  }

  @Test fun progressionFixturesSuppressCombatRngAndGuaranteeReadyParentExit() {
    val random = RecordingRandom()
    val state = JSONObject("""
      {
        "turn":12,
        "level":{"number":2},
        "flags":{"exploration":{"levelTurns":6,"sublevelId":""}}
      }
    """.trimIndent())
    val rolls = GameplayRollPolicy.roll(
      state,
      "EXPLORE",
      "đi tiếp",
      false,
      random,
      emuLevel06Traversal = true,
    )

    assertEquals(0, rolls.getJSONArray("entityEncounterKeys").length())
    assertTrue(rolls.getJSONObject("exitProbe").getBoolean("success"))
    assertTrue(rolls.getJSONObject("exitProbe").getBoolean("guaranteedByState"))
    assertTrue(rolls.getJSONObject("levelExit").getBoolean("success"))
    // survivor + hazard only. Entity and guaranteed exit do not consume RNG.
    assertEquals(listOf(10_000, 10_000), random.bounds)
  }

  @Test fun level06ChildSublevelDisablesParentExitProbe() {
    val random = RecordingRandom()
    val state = JSONObject("""
      {
        "level":{"number":0},
        "flags":{"exploration":{"levelTurns":99,"sublevelId":"0.1"}}
      }
    """.trimIndent())
    val rolls = GameplayRollPolicy.roll(
      state,
      "EXPLORE",
      "đi tiếp",
      false,
      random,
      emuLevel06Traversal = true,
    )

    val exit = rolls.getJSONObject("exitProbe")
    assertEquals(0, exit.getInt("threshold"))
    assertFalse(exit.getBoolean("eligible"))
    assertFalse(exit.getBoolean("success"))
  }
}

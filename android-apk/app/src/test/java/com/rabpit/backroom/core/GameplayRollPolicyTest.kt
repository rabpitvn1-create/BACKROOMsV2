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
    val rolls = GameplayRollPolicy.roll(JSONObject("""{"turn":9,"level":{"number":3}}"""), "EXPLORE", "đi tiếp", true, random)
    assertEquals(9, rolls.getInt("turn"))
    assertTrue(rolls.getBoolean("meta"))
    assertFalse(rolls.has("actionKind"))
    assertTrue(random.bounds.isEmpty())
  }

  @Test fun anNhienEncounterUsesIndependentQuarterPercentRoll() {
    val random = RecordingRandom()
    val state = JSONObject("""{"level":{"number":0},"flags":{}}""")
    val rolls = GameplayRollPolicy.roll(state, "EXPLORE", "đi tiếp", false, random)
    val encounter = rolls.getJSONObject("anNhienEncounter")
    assertTrue(encounter.getBoolean("eligible"))
    assertEquals(10_000, encounter.getInt("max"))
    assertEquals(25, encounter.getInt("threshold"))
    assertTrue(encounter.getBoolean("success"))
    assertFalse(encounter.optBoolean("guaranteedByState", false))
    assertFalse(rolls.getJSONObject("survivor").getBoolean("eligible"))
    assertEquals(listOf(10_000), random.bounds)
  }

  @Test fun followingAnNhienAppliesLootAndExitBonusesWithoutReEncounter() {
    val random = RecordingRandom()
    val state = JSONObject("""
      {"level":{"number":0},"party":[{"id":"an-nhien","name":"An Nhiên"}],"flags":{"anNhien":{"encountered":true}}}
    """.trimIndent())
    val rolls = GameplayRollPolicy.roll(state, "EXPLORE", "tìm đường ra và lục khu vực", false, random)
    assertFalse(rolls.getJSONObject("anNhienEncounter").getBoolean("eligible"))
    assertTrue(rolls.getJSONObject("survivor").getBoolean("eligible"))
    assertEquals(35 + AnNhienCanon.LOOT_BONUS_POINTS, rolls.getJSONObject("loot").getInt("threshold"))
    assertEquals(10 + AnNhienCanon.EXIT_BONUS_POINTS, rolls.getJSONObject("exitProbe").getInt("threshold"))
    assertTrue(rolls.getJSONObject("loot").getString("chance").contains("An Nhiên"))
    assertTrue(rolls.getJSONObject("exitProbe").getString("chance").contains("An Nhiên"))
  }

  @Test fun genericThresholdsMatchSettledLevelFourRuntime() {
    val random = RecordingRandom()
    val state = JSONObject("""
      {"turn":7,"level":{"number":4},"flags":{"iris":{"exists":true,"continuity":"SEPARATED"},"syvial":{"exists":true,"continuity":"LOST"}}}
    """.trimIndent())
    val rolls = GameplayRollPolicy.roll(state, "SEARCH", "tìm nước almond và kiểm tra khu vực", false, random)
    assertEquals("SEARCH", rolls.getString("actionKind"))
    assertEquals(200, rolls.getJSONObject("survivor").getInt("threshold"))
    assertEquals(25, rolls.getJSONObject("irisReunion").getInt("threshold"))
    assertEquals(10_000, rolls.getJSONObject("irisReunion").getInt("max"))
    assertEquals(25, rolls.getJSONObject("syvialReunion").getInt("threshold"))
    assertEquals(10_000, rolls.getJSONObject("syvialReunion").getInt("max"))
    assertEquals(300, rolls.getJSONObject("hazard").getInt("threshold"))
    assertEquals(180, rolls.getJSONObject("loot").getInt("threshold"))
    assertEquals(1, rolls.getJSONObject("madGodSet").getInt("threshold"))
    assertEquals(120, rolls.getJSONObject("almondWater").getInt("threshold"))
    assertFalse(rolls.getJSONObject("entityEncounter").getBoolean("success"))
    assertFalse(rolls.getJSONObject("exitProbe").getBoolean("eligible"))
    assertEquals(List(8) { 10_000 }, random.bounds)
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
    // Three special followers + survivor + hazard + independent Entity draws + exit probe.
    assertEquals(6 + EntityEncounterPolicy.authorizedKeys().size, random.bounds.size)
  }

  @Test fun entityDisableFlagPreventsTheEntireIndependentBatchFromConsumingRng() {
    val random = RecordingRandom()
    val state = JSONObject("""{"level":{"number":1},"flags":{"entityEncountersAllowed":false}}""")
    val rolls = GameplayRollPolicy.roll(state, "EXPLORE", "đi tiếp", false, random)
    assertEquals(0, rolls.getJSONArray("entityEncounterKeys").length())
    assertFalse(rolls.getJSONObject("entityEncounter").getBoolean("success"))
    EntityEncounterPolicy.authorizedKeys().forEach { key ->
      assertFalse(rolls.getJSONObject("entityRolls").getJSONObject(key).getBoolean("eligible"))
    }
    // Three special followers + survivor + hazard + exitProbe. Disabled Entity checks consume no draws.
    assertEquals(List(6) { 10_000 }, random.bounds)
  }

  @Test fun progressionFixturesSuppressCombatRngAndGuaranteeReadyParentExit() {
    val random = RecordingRandom()
    val state = JSONObject("""{"turn":12,"level":{"number":2},"flags":{"exploration":{"levelTurns":6,"sublevelId":""}}}""")
    val rolls = GameplayRollPolicy.roll(state, "EXPLORE", "đi tiếp", false, random, emuLevel06Traversal = true)
    assertEquals(0, rolls.getJSONArray("entityEncounterKeys").length())
    assertTrue(rolls.getJSONObject("exitProbe").getBoolean("success"))
    assertTrue(rolls.getJSONObject("exitProbe").getBoolean("guaranteedByState"))
    assertTrue(rolls.getJSONObject("levelExit").getBoolean("success"))
    // Progression fixtures suppress survivor/hazard/Entity RNG, but special-follower discovery remains independent.
    assertEquals(List(3) { 10_000 }, random.bounds)
  }

  @Test fun level06ChildSublevelDisablesParentExitProbeEvenWithAnNhienBonus() {
    val random = RecordingRandom()
    val state = JSONObject("""
      {"level":{"number":0},"party":[{"id":"an-nhien","name":"An Nhiên"}],"flags":{"anNhien":{"encountered":true},"exploration":{"levelTurns":99,"sublevelId":"0.1"}}}
    """.trimIndent())
    val rolls = GameplayRollPolicy.roll(state, "EXPLORE", "đi tiếp", false, random, emuLevel06Traversal = true)
    val exit = rolls.getJSONObject("exitProbe")
    assertEquals(0, exit.getInt("threshold"))
    assertFalse(exit.getBoolean("eligible"))
    assertFalse(exit.getBoolean("success"))
  }
}

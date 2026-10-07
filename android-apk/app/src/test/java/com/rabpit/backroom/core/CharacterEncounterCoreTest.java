package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class CharacterEncounterCoreTest {
  @Test public void candidatesUseCanonRatesAndIrisRequiresLeavingLevelZero() throws Exception {
    CharacterEncounterCore core = new CharacterEncounterCore();

    JSONObject levelZero = state(0, 1).put(LevelCore.LEVEL_KEY, "0");
    JSONArray zero = core.situationCandidates(levelZero);
    assertFalse(zero.toString().contains("character:iris"));
    assertCandidateRate(zero, "character:syvial", 100.0d / CharacterEncounterCore.RARE_ENCOUNTER_BOUND);

    JSONObject levelOne = state(1, 1).put(LevelCore.LEVEL_KEY, "1");
    JSONArray one = core.situationCandidates(levelOne);
    assertCandidateRate(one, "character:iris", 0.25d);
    JSONObject luc = findCandidate(one, "character:iris");
    assertEquals("canon:iris:after_level_0", luc.getString("eligibilityRuleId"));
    assertTrue(luc.getJSONArray("tags").toString().contains("FIRST_CONTACT"));
  }

  @Test public void activatingCandidateCommitsPartyBeforeNarrationThenAcknowledgesIntro() throws Exception {
    CharacterEncounterCore core = new CharacterEncounterCore();
    JSONObject state = state(1, 7).put(LevelCore.LEVEL_KEY, "1");

    core.activateEncounterCandidate(state, "syvial");

    assertEquals(1, state.getJSONArray("party").length());
    assertEquals("syvial", state.getJSONArray("party").getJSONObject(0).getString("id"));
    assertEquals("syvial", state.getJSONObject("characterEncounter")
        .getJSONArray("pendingIntro").getString(0));

    core.acknowledgePendingIntro(state);

    assertEquals(1, state.getJSONArray("party").length());
    assertEquals(0, state.getJSONObject("characterEncounter").getJSONArray("pendingIntro").length());
    assertEquals("syvial", state.getJSONObject("characterEncounter")
        .getJSONArray("lastIntroduced").getString(0));
  }

  @Test public void irisCannotActivateOnLevelZero() throws Exception {
    CharacterEncounterCore core = new CharacterEncounterCore();
    JSONObject state = state(0, 2).put(LevelCore.LEVEL_KEY, "0");
    try {
      core.activateEncounterCandidate(state, "iris");
      fail("Level 0 must not allow Iris reunion.");
    } catch (IllegalStateException expected) {
      assertEquals(0, state.getJSONArray("party").length());
    }
  }

  @Test public void eligibleIrisReunionSurvivesNormalizationAfterLevelZero() throws Exception {
    JSONObject state = state(1, 3)
        .put("characterEncounter", new JSONObject()
            .put("pendingIntro", new JSONArray().put("iris"))
            .put("justEncountered", new JSONArray().put("iris")));

    new CharacterEncounterCore().normalizeState(state);

    assertEquals("iris", state.getJSONObject("characterEncounter")
        .getJSONArray("pendingIntro").getString(0));
    assertEquals("iris", state.getJSONObject("characterEncounter")
        .getJSONArray("justEncountered").getString(0));
  }

  @Test public void normalizationDeduplicatesCurrentCompanionsAndStripsShadowProgression() throws Exception {
    JSONObject iris = new JSONObject()
        .put("id", "iris")
        .put("name", "Iris")
        .put("hp", 61)
        .put("stats", new JSONObject().put("STR", 11))
        .put("level", 7)
        .put("customMetadata", "keep-me");
    JSONObject state = state(0, 12);
    state.put("party", new JSONArray()
        .put(new JSONObject().put("id", "cao_minh").put("name", "Cao Minh"))
        .put(iris)
        .put(new JSONObject().put("id", "iris").put("name", "Iris"))
        .put("Syvial"));

    new CharacterEncounterCore().normalizeState(state);
    JSONArray party = state.getJSONArray("party");
    assertEquals(2, party.length());
    assertEquals("iris", party.getJSONObject(0).getString("id"));
    assertFalse(party.getJSONObject(0).has("hp"));
    assertFalse(party.getJSONObject(0).has("stats"));
    assertFalse(party.getJSONObject(0).has("level"));
    assertEquals("keep-me", party.getJSONObject(0).getString("customMetadata"));
    assertEquals("syvial", party.getJSONObject(1).getString("id"));
  }

  @Test public void allAvailableCharactersJoinedProducesNoCharacterCandidates() throws Exception {
    JSONObject state = state(2, 8).put("party", new JSONArray()
        .put(new JSONObject().put("id", "iris").put("name", "Iris"))
        .put(new JSONObject().put("id", "syvial").put("name", "Syvial")));
    CharacterEncounterCore core = new CharacterEncounterCore();
    core.normalizeState(state);
    assertEquals(0, core.situationCandidates(state).length());
  }

  @Test public void emptyLegacyPartyMigratesWithoutCrash() throws Exception {
    JSONObject state = state(0, 1).put("party", new JSONArray());
    new CharacterEncounterCore().normalizeState(state);
    assertEquals(0, state.getJSONArray("party").length());
  }

  private static void assertCandidateRate(JSONArray candidates, String key, double expected) throws Exception {
    JSONObject candidate = findCandidate(candidates, key);
    assertTrue("Missing candidate " + key, candidate != null);
    assertEquals(expected, candidate.getDouble("chancePercent"), 0.0000001d);
  }

  private static JSONObject findCandidate(JSONArray candidates, String key) throws Exception {
    for (int i = 0; i < candidates.length(); i++) {
      JSONObject candidate = candidates.getJSONObject(i);
      if (key.equals(candidate.optString("situationKey"))) return candidate;
    }
    return null;
  }

  private static JSONObject state(int level, int turn) throws Exception {
    return new JSONObject()
        .put("currentLevel", level)
        .put("turn", turn)
        .put("player", new JSONObject().put("name", "Cao Minh"))
        .put("party", new JSONArray())
        .put("flags", new JSONObject())
        .put("log", new JSONArray().put(new JSONObject().put("role", "gm").put("text", "Test")));
  }

  @Test public void irisPendingPromptIsFirstContactWithoutInheritedRelationship() throws Exception {
    JSONObject state = state(1, 5)
        .put("characterEncounter", new JSONObject()
            .put("pendingIntro", new JSONArray().put("iris"))
            .put("justEncountered", new JSONArray().put("iris")));
    CharacterEncounterCore core = new CharacterEncounterCore();
    String prompt = core.promptContext(state);

    assertTrue(prompt.contains("Iris first contact is already committed"));
    assertFalse(prompt.contains("tense reunion"));
    assertTrue(prompt.contains("Never import Kai romance"));
  }

  @Test public void retiredSaveMigratesLiveStateWithoutSwordLoreOrProgressLoss() throws Exception {
    JSONObject oldProfile = new JSONObject().put("schema", "core_stats_v1")
        .put("stats", new JSONObject().put("STR", 7).put("DEF", 5).put("SKL", 6).put("VIT", 8))
        .put("baseMaxHp", 50).put("currentHp", 31);
    JSONObject state = state(1, 5).put("gameTime", new JSONObject().put("elapsedSubjectiveMinutes", 30))
        .put(CharacterProgressionCore.ROOT_KEY, new JSONObject().put("characters", new JSONObject().put("luc_tram", oldProfile)))
        .put("party", new JSONArray().put(new JSONObject().put("id", "luc_tram").put("name", "Lục Trầm")
            .put("avatar", "file:///android_asset/avatars/luctram_avatar.png").put("role", "Chân truyền Thiên Kiếm Môn")
            .put("inventory", new JSONArray().put(new JSONObject().put("name", "Tịch Quang Kiếm"))
                .put(new JSONObject().put("name", "Thiên Cơ Bạch Kim Kiếm Khải"))
                .put(new JSONObject().put("name", "Kỷ vật Tịch Quang")))))
        .put("survival", new JSONObject().put("characters", new JSONObject().put("luc_tram", new JSONObject().put("lastFoodMinute", 17))))
        .put("combat", new JSONObject().put("currentActor", "Lục Trầm")
            .put("participants", new JSONArray().put(new JSONObject().put("id", "luc_tram").put("name", "Lục Trầm").put("hp", 31)))
            .put("currentUltimate", new JSONObject().put("name", "Thiên Kiếm Định Giới"))
            .put("diceState", new JSONObject().put("rolls", 2)))
        .put("characterEncounter", new JSONObject().put("pendingIntro", new JSONArray().put("luc_tram")));
    CharacterProgressionCore progression = new CharacterProgressionCore();
    progression.normalizeState(state);
    new CharacterEncounterCore().normalizeState(state);
    assertEquals(7, progression.profile(state, "iris").getJSONObject("stats").getInt("STR"));
    assertEquals(31, progression.profile(state, "iris").getInt("currentHp"));
    assertFalse(state.getJSONObject(CharacterProgressionCore.ROOT_KEY).getJSONObject("characters").has("luc_tram"));
    JSONObject iris = state.getJSONArray("party").getJSONObject(0);
    assertEquals("iris", iris.getString("id"));
    assertFalse(iris.has("avatar"));
    assertFalse(iris.has("role"));
    String inventory = iris.getJSONArray("inventory").toString();
    assertTrue(inventory.contains("Ivory"));
    assertTrue(inventory.contains("Kỷ vật Tịch Quang"));
    assertFalse(inventory.contains("Tịch Quang Kiếm"));
    assertTrue(state.getJSONArray("retiredLucTramParty").toString().contains("Tịch Quang Kiếm"));
    assertEquals(17, state.getJSONObject("survival").getJSONObject("characters").getJSONObject("iris").getInt("lastFoodMinute"));
    assertEquals("iris", state.getJSONObject("combat").getJSONArray("participants").getJSONObject(0).getString("id"));
    assertEquals("Iris", state.getJSONObject("combat").getString("currentActor"));
    assertEquals(2, state.getJSONObject("combat").getJSONObject("diceState").getInt("rolls"));
    assertEquals("iris", state.getJSONObject("characterEncounter").getJSONArray("pendingIntro").getString(0));
    String stable = state.toString();
    progression.normalizeState(state);
    new CharacterEncounterCore().normalizeState(state);
    assertEquals(stable, state.toString());
    new SurvivalCore().restoreFood(state, "iris", 100);
    assertEquals(17, state.getJSONObject("retiredLucTramSurvival").getInt("lastFoodMinute"));
  }

  @Test public void existingIrisWinsIdentityAndStatsWhileLegacyInventoryIsPreserved() throws Exception {
    JSONObject state = state(1, 5).put("party", new JSONArray()
        .put(new JSONObject().put("id", "luc_tram").put("name", "Lục Trầm")
            .put("inventory", new JSONArray().put(new JSONObject().put("name", "Bandage"))))
        .put(new JSONObject().put("id", "iris").put("name", "Iris").put("customMetadata", "existing")
            .put("inventory", new JSONArray().put(new JSONObject().put("name", "Ivory")))));
    new CharacterEncounterCore().normalizeState(state);
    assertEquals(1, state.getJSONArray("party").length());
    assertEquals("existing", state.getJSONArray("party").getJSONObject(0).getString("customMetadata"));
    assertTrue(state.getJSONArray("party").getJSONObject(0).getJSONArray("inventory").toString().contains("Bandage"));
    assertEquals(1, state.getJSONArray("retiredLucTramParty").length());
  }

}

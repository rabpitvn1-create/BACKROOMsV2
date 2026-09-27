package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class CharacterEncounterCoreTest {
  @Test public void candidatesUseCanonRatesAndLucTramRequiresLeavingLevelZero() throws Exception {
    CharacterEncounterCore core = new CharacterEncounterCore();

    JSONObject levelZero = state(0, 1).put(LevelCore.LEVEL_KEY, "0");
    JSONArray zero = core.situationCandidates(levelZero);
    assertFalse(zero.toString().contains("character:luc_tram"));
    assertCandidateRate(zero, "character:iris", 100.0d / CharacterEncounterCore.RARE_ENCOUNTER_BOUND);
    assertCandidateRate(zero, "character:syvial", 100.0d / CharacterEncounterCore.RARE_ENCOUNTER_BOUND);

    JSONObject levelOne = state(1, 1).put(LevelCore.LEVEL_KEY, "1");
    JSONArray one = core.situationCandidates(levelOne);
    assertCandidateRate(one, "character:luc_tram", 10.0d);
    JSONObject luc = findCandidate(one, "character:luc_tram");
    assertEquals("canon:luc_tram:after_level_0", luc.getString("eligibilityRuleId"));
    assertTrue(luc.getJSONArray("tags").toString().contains("REUNION"));
  }

  @Test public void activatingCandidateCommitsPartyBeforeNarrationThenAcknowledgesIntro() throws Exception {
    CharacterEncounterCore core = new CharacterEncounterCore();
    JSONObject state = state(1, 7).put(LevelCore.LEVEL_KEY, "1");

    core.activateEncounterCandidate(state, "iris");

    assertEquals(1, state.getJSONArray("party").length());
    assertEquals("iris", state.getJSONArray("party").getJSONObject(0).getString("id"));
    assertEquals("iris", state.getJSONObject("characterEncounter")
        .getJSONArray("pendingIntro").getString(0));

    core.acknowledgePendingIntro(state);

    assertEquals(1, state.getJSONArray("party").length());
    assertEquals(0, state.getJSONObject("characterEncounter").getJSONArray("pendingIntro").length());
    assertEquals("iris", state.getJSONObject("characterEncounter")
        .getJSONArray("lastIntroduced").getString(0));
  }

  @Test public void lucTramCannotActivateOnLevelZero() throws Exception {
    CharacterEncounterCore core = new CharacterEncounterCore();
    JSONObject state = state(0, 2).put(LevelCore.LEVEL_KEY, "0");
    try {
      core.activateEncounterCandidate(state, "luc_tram");
      fail("Level 0 must not allow Lục Trầm reunion.");
    } catch (IllegalStateException expected) {
      assertEquals(0, state.getJSONArray("party").length());
    }
  }

  @Test public void eligibleLucTramReunionSurvivesNormalizationAfterLevelZero() throws Exception {
    JSONObject state = state(1, 3)
        .put("characterEncounter", new JSONObject()
            .put("pendingIntro", new JSONArray().put("luc_tram"))
            .put("justEncountered", new JSONArray().put("luc_tram")));

    new CharacterEncounterCore().normalizeState(state);

    assertEquals("luc_tram", state.getJSONObject("characterEncounter")
        .getJSONArray("pendingIntro").getString(0));
    assertEquals("luc_tram", state.getJSONObject("characterEncounter")
        .getJSONArray("justEncountered").getString(0));
  }

  @Test public void normalizationDeduplicatesCurrentCompanionsAndStripsShadowProgression() throws Exception {
    JSONObject lucTram = new JSONObject()
        .put("id", "luc_tram")
        .put("name", "Lục Trầm")
        .put("hp", 61)
        .put("stats", new JSONObject().put("STR", 11))
        .put("level", 7)
        .put("customMetadata", "keep-me");
    JSONObject state = state(0, 12);
    state.put("party", new JSONArray()
        .put(new JSONObject().put("id", "cao_minh").put("name", "Cao Minh"))
        .put(lucTram)
        .put(new JSONObject().put("id", "luc_tram").put("name", "Lục Trầm"))
        .put("Iris")
        .put("Syvial"));

    new CharacterEncounterCore().normalizeState(state);
    JSONArray party = state.getJSONArray("party");
    assertEquals(CharacterEncounterCore.MAX_COMPANIONS, party.length());
    assertEquals("luc_tram", party.getJSONObject(0).getString("id"));
    assertFalse(party.getJSONObject(0).has("hp"));
    assertFalse(party.getJSONObject(0).has("stats"));
    assertFalse(party.getJSONObject(0).has("level"));
    assertEquals("keep-me", party.getJSONObject(0).getString("customMetadata"));
    assertEquals("iris", party.getJSONObject(1).getString("id"));
    assertEquals("syvial", party.getJSONObject(2).getString("id"));
  }

  @Test public void fullPartyProducesNoCharacterCandidates() throws Exception {
    JSONObject state = state(2, 8).put("party", new JSONArray()
        .put(new JSONObject().put("id", "luc_tram").put("name", "Lục Trầm"))
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
}

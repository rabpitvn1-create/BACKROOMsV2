package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class GameCoreFacadeNarrativeBoundaryTest {
  private static LevelCore levelCore() {
    return new LevelCore(null, bound -> 0);
  }

  private static JSONObject before() throws Exception {
    return new JSONObject()
        .put("currentLevel", 0)
        .put("currentLevelKey", "0")
        .put("location", "Hành lang gốc")
        .put("turn", 7)
        .put("flags", new JSONObject())
        .put("party", new JSONArray());
  }

  @Test public void descriptiveSceneLabelCannotForgeLevelTransition() throws Exception {
    JSONObject before = before();
    JSONObject candidate = new JSONObject(before.toString())
        .put("location", "Ngã rẽ có đèn chớp")
        .put("currentLevel", 1)
        .put("currentLevelKey", "1")
        .put("turn", 8);

    GameCoreFacade.applyNarrativeBoundary(levelCore(), before, candidate, "");

    assertEquals("0", candidate.getString("currentLevelKey"));
    assertEquals(0, candidate.getInt("currentLevel"));
    assertEquals("Ngã rẽ có đèn chớp", candidate.getString("location"));
    assertEquals(8, candidate.getInt("turn"));
  }

  @Test public void lockedNarrativeTransitionIsRejected() throws Exception {
    JSONObject before = before();
    JSONObject candidate = new JSONObject(before.toString()).put("location", "Level 0.1");

    try {
      GameCoreFacade.applyNarrativeBoundary(levelCore(), before, candidate, "0.1");
      fail("Expected locked transition rejection");
    } catch (IllegalArgumentException expected) {
      assertEquals("0", before.getString("currentLevelKey"));
    }
  }

  @Test public void unlockedValidatedNarrativeTransitionIsApplied() throws Exception {
    JSONObject before = before()
        .put("levelRoute", new JSONObject()
            .put("levelKey", "0")
            .put("streak", 999)
            .put("exitAvailable", true));
    JSONObject candidate = new JSONObject(before.toString()).put("location", "Level 0.1");

    GameCoreFacade.applyNarrativeBoundary(levelCore(), before, candidate, "0.1");

    assertEquals("0.1", candidate.getString("currentLevelKey"));
    assertEquals(0, candidate.getInt("currentLevel"));
  }

  @Test public void corePreparedEntityEncounterSurvivesNarrativeBoundary() throws Exception {
    JSONObject before = before();
    before.getJSONObject("flags").put("entityEncounterKey", "hound")
        .put("entityEncounterSource", "fixed_rate")
        .put("entityEncounterStartedTurn", 7);
    JSONObject candidate = new JSONObject(before.toString()).put("location", "Ngã rẽ")
        .put("flags", new JSONObject());

    GameCoreFacade.applyNarrativeBoundary(levelCore(), before, candidate, "");
    new EntityCore(null).validateAndApply(before, candidate);

    assertEquals("hound", candidate.getJSONObject("flags").getString("entityEncounterKey"));
    assertEquals("fixed_rate", candidate.getJSONObject("flags").getString("entityEncounterSource"));
  }
}

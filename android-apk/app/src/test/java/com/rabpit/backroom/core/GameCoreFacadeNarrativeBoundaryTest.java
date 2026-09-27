package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class GameCoreFacadeNarrativeBoundaryTest {
  private static LevelCore levelCore() {
    return new LevelCore(null, bound -> 5);
  }

  private static JSONObject before() throws Exception {
    JSONObject state = new JSONObject()
        .put("currentLevel", 0)
        .put("currentLevelKey", "0")
        .put("location", "Hành lang gốc")
        .put("turn", 7)
        .put("flags", new JSONObject())
        .put("party", new JSONArray());
    levelCore().normalizeState(state);
    return state;
  }

  @Test public void sceneLabelCannotForgeLevelTransitionWithoutTransitionTarget() throws Exception {
    JSONObject before = before();
    JSONObject candidate = new JSONObject(before.toString())
        .put("location", "Level 1 — lối ra")
        .put("currentLevel", 1)
        .put("currentLevelKey", "1")
        .put("turn", 8);

    GameCoreFacade.applyNarrativeBoundary(levelCore(), before, candidate, "");

    assertEquals("Hành lang gốc", candidate.getString("location"));
    assertEquals("0", candidate.getString("currentLevelKey"));
    assertEquals(0, candidate.getInt("currentLevel"));
    assertEquals(8, candidate.getInt("turn"));
  }

  @Test public void transitionTargetCannotBypassLockedRoute() throws Exception {
    JSONObject before = before();
    JSONObject candidate = new JSONObject(before.toString())
        .put("location", "Level 0.1 / Zenith Station");

    try {
      GameCoreFacade.applyNarrativeBoundary(levelCore(), before, candidate, "0.1");
      fail("Expected locked narrative transition to be rejected");
    } catch (IllegalArgumentException expected) {
      assertTrue(expected.getMessage().contains("locked"));
    }
  }

  @Test public void unlockedValidatedTransitionTargetCanAdvanceLevel() throws Exception {
    JSONObject before = before();
    before.getJSONObject(LevelCore.ROUTE_STATE)
        .put("streak", LevelCore.ROUTE_REQUIRED_STREAK)
        .put("exitAvailable", true);
    JSONObject candidate = new JSONObject(before.toString())
        .put("location", "Level 0.1 / Zenith Station");

    GameCoreFacade.applyNarrativeBoundary(levelCore(), before, candidate, "0.1");

    assertEquals("0.1", candidate.getString(LevelCore.LEVEL_KEY));
    assertEquals(0, candidate.getInt("currentLevel"));
    assertEquals("Level 0.1 / Zenith Station", candidate.getString("location"));
  }

  @Test public void corePreparedEntityEncounterSurvivesNarrativeBoundary() throws Exception {
    JSONObject before = before();
    before.getJSONObject("flags").put("entityEncounterKey", "hound")
        .put("entityEncounterSource", "core_independent_roll")
        .put("entityEncounterStartedTurn", 7);
    JSONObject candidate = new JSONObject(before.toString())
        .put("location", "Hành lang khác")
        .put("flags", new JSONObject());

    GameCoreFacade.applyNarrativeBoundary(levelCore(), before, candidate, "");
    new EntityCore(null).validateAndApply(before, candidate);

    assertEquals("hound", candidate.getJSONObject("flags").getString("entityEncounterKey"));
    assertEquals("core_independent_roll",
        candidate.getJSONObject("flags").getString("entityEncounterSource"));
  }
}

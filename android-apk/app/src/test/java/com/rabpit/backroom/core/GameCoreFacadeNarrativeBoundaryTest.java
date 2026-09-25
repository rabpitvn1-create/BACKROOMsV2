package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GameCoreFacadeNarrativeBoundaryTest {
  private static LevelCore levelCore() {
    return new LevelCore(null, bound -> 0);
  }

  private static JSONObject before(boolean active, boolean complete) throws Exception {
    return new JSONObject()
        .put("currentLevel", 0).put("currentLevelKey", "0")
        .put("location", "Hành lang gốc").put("turn", 7)
        .put("story", new JSONObject().put("active", active).put("arcComplete", complete)
            .put("levelKey", "0").put("currentChapter", "L0_C01")
            .put("currentSegmentId", "L0_C01_S01"))
        .put("flags", new JSONObject()).put("party", new JSONArray());
  }

  @Test public void activeStoryKeepsCanonLocationAndLevelDespiteModelSceneLabel() throws Exception {
    JSONObject before = before(true, false);
    JSONObject candidate = new JSONObject(before.toString())
        .put("location", "Level 1 — lối ra")
        .put("currentLevel", 1).put("currentLevelKey", "1").put("turn", 8);

    GameCoreFacade.applyNarrativeBoundary(levelCore(), before, candidate, "");

    assertEquals("Hành lang gốc", candidate.getString("location"));
    assertEquals("0", candidate.getString("currentLevelKey"));
    assertEquals(0, candidate.getInt("currentLevel"));
    assertEquals(8, candidate.getInt("turn"));
  }

  @Test public void activeStoryIgnoresEvenAvailableOrInvalidModelTransitionWithoutRejectingTurn()
      throws Exception {
    LevelCore level = levelCore();
    JSONObject before = before(true, false);
    level.markStoryBoundaryReady(before);
    for (String target : new String[]{"0.1", "unrecognized-destination"}) {
      JSONObject candidate = new JSONObject(before.toString()).put("location", "Level 0.1")
          .put("turn", 8);

      GameCoreFacade.applyNarrativeBoundary(level, before, candidate, target);

      assertEquals("0", candidate.getString("currentLevelKey"));
      assertEquals("Hành lang gốc", candidate.getString("location"));
      assertEquals(8, candidate.getInt("turn"));
    }
  }

  @Test public void candidateCannotOverwriteDecisionOrReturnJourneyStoryState() throws Exception {
    JSONObject before = before(true, false);
    JSONObject story = before.getJSONObject("story")
        .put("awaitingDecision", true)
        .put("decisionPackage", new JSONObject().put("contextHash", "core-choice"))
        .put("returnJourney", new JSONObject().put("active", true)
            .put("progress", 2).put("cause", StoryCore.RETURN_CAUSE_STORY));
    JSONObject candidate = new JSONObject(before.toString())
        .put("story", new JSONObject().put("active", false).put("arcComplete", true)
            .put("currentChapter", "L9").put("returnJourney", new JSONObject()));

    GameCoreFacade.applyNarrativeBoundary(levelCore(), before, candidate, "0.1");

    assertEquals(story.toString(), candidate.getJSONObject("story").toString());
    assertTrue(candidate.getJSONObject("story").getBoolean("awaitingDecision"));
    assertEquals(2, candidate.getJSONObject("story").getJSONObject("returnJourney")
        .getInt("progress"));
  }

  @Test public void inactiveOrCompletedArcStillAllowsExistingLevelTransition() throws Exception {
    for (JSONObject before : new JSONObject[]{before(false, false), before(true, true)}) {
      LevelCore level = levelCore();
      level.markStoryBoundaryReady(before);
      JSONObject candidate = new JSONObject(before.toString()).put("location", "Level 0.1");

      GameCoreFacade.applyNarrativeBoundary(level, before, candidate, "0.1");

      assertEquals("0.1", candidate.getString("currentLevelKey"));
      assertFalse(candidate.getJSONObject("story").optBoolean("active", false)
          && !candidate.getJSONObject("story").optBoolean("arcComplete", false));
    }
  }

  @Test public void corePreparedEntityEncounterSurvivesNarrativeBoundary() throws Exception {
    JSONObject before = before(true, false);
    before.getJSONObject("flags").put("entityEncounterKey", "hound")
        .put("entityEncounterSource", "story_authored")
        .put("entityEncounterStartedTurn", 7);
    JSONObject candidate = new JSONObject(before.toString()).put("location", "another level");
    candidate.put("flags", new JSONObject());

    GameCoreFacade.applyNarrativeBoundary(levelCore(), before, candidate, "0.1");
    new EntityCore(null).validateAndApply(before, candidate);

    assertEquals("hound", candidate.getJSONObject("flags").getString("entityEncounterKey"));
    assertEquals("story_authored", candidate.getJSONObject("flags")
        .getString("entityEncounterSource"));
  }
}

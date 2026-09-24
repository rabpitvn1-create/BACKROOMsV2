package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CoreRewardEconomyTest {
  @Test public void rewardCurveGrowsByFiftyPercentPerStageWithRounding() {
    assertEquals(2, CharacterProgressionCore.scaledCoreReward(
        CharacterProgressionCore.ENTITY_VICTORY_BASE_CORE, 0));
    assertEquals(3, CharacterProgressionCore.scaledCoreReward(
        CharacterProgressionCore.ENTITY_VICTORY_BASE_CORE, 1));
    assertEquals(5, CharacterProgressionCore.scaledCoreReward(
        CharacterProgressionCore.ENTITY_VICTORY_BASE_CORE, 2));
    assertEquals(7, CharacterProgressionCore.scaledCoreReward(
        CharacterProgressionCore.ENTITY_VICTORY_BASE_CORE, 3));

    assertEquals(5, CharacterProgressionCore.scaledCoreReward(
        CharacterProgressionCore.STORY_PROGRESS_BASE_CORE, 0));
    assertEquals(8, CharacterProgressionCore.scaledCoreReward(
        CharacterProgressionCore.STORY_PROGRESS_BASE_CORE, 1));
    assertEquals(11, CharacterProgressionCore.scaledCoreReward(
        CharacterProgressionCore.STORY_PROGRESS_BASE_CORE, 2));
    assertEquals(17, CharacterProgressionCore.scaledCoreReward(
        CharacterProgressionCore.STORY_PROGRESS_BASE_CORE, 3));

    assertEquals(Integer.MAX_VALUE, CharacterProgressionCore.scaledCoreReward(
        CharacterProgressionCore.STORY_PROGRESS_BASE_CORE, 10_000));
  }

  @Test public void coreBalanceSaturatesInsteadOfOverflowing() throws Exception {
    JSONObject state = baseState("0");
    CharacterProgressionCore progression = new CharacterProgressionCore();
    progression.normalizeState(state);

    assertEquals(Integer.MAX_VALUE, progression.grantCore(state, Integer.MAX_VALUE));
    assertEquals(Integer.MAX_VALUE, progression.coreCount(state));
    assertEquals(0, progression.grantCore(state, 10));
    assertEquals(0, progression.rewardStageCompletion(state, 1));
    assertEquals(Integer.MAX_VALUE, progression.coreCount(state));
  }

  @Test public void entityVictoryAlwaysGrantsCoreExactlyOnce() throws Exception {
    JSONObject state = combatState("0");
    CharacterProgressionCore progression = new CharacterProgressionCore();
    progression.normalizeState(state);

    CombatChoiceEngine.start(state, "hound", 0);
    JSONObject combat = state.getJSONObject("combat");
    combat.getJSONObject("entity").put("hp", 1);
    finalizeAs(state, 2, 2, 1, 4, 6);

    CombatChoiceEngine.resolveFinalized(state);

    assertFalse(combat.getBoolean("active"));
    assertEquals("victory", combat.getString("outcome"));
    assertTrue(combat.getBoolean("coreDropResolved"));
    assertEquals(100, combat.getInt("coreDropRatePercent"));
    assertFalse(combat.has("coreDropRoll"));
    assertEquals(2, combat.getInt("coreDropReward"));
    assertEquals(2, progression.coreCount(state));

    CombatChoiceEngine.resolveFinalized(state);
    assertEquals(2, progression.coreCount(state));
  }

  @Test public void tamMaPaysStageScaledJackpotOncePerStage() throws Exception {
    JSONObject state = combatState("0");
    CharacterProgressionCore progression = new CharacterProgressionCore();
    progression.normalizeState(state);

    CombatChoiceEngine.start(state, "tam_ma_cao_minh", 0);
    JSONObject firstCombat = state.getJSONObject("combat");
    firstCombat.getJSONObject("entity").put("hp", 1);
    finalizeAs(state, 2, 2, 1, 4, 6);
    CombatChoiceEngine.resolveFinalized(state);

    assertEquals(100, firstCombat.getInt("coreDropReward"));
    assertEquals("treasure", firstCombat.getString("coreDropRewardType"));
    assertEquals(100, progression.coreCount(state));

    CombatChoiceEngine.start(state, "tam_ma_cao_minh", 0);
    JSONObject repeatCombat = state.getJSONObject("combat");
    repeatCombat.getJSONObject("entity").put("hp", 1);
    finalizeAs(state, 2, 2, 1, 4, 6);
    CombatChoiceEngine.resolveFinalized(state);

    assertEquals(10, repeatCombat.getInt("coreDropReward"));
    assertEquals("treasure", repeatCombat.getString("coreDropRewardType"));
    assertEquals(110, progression.coreCount(state));

    state.put(LevelCore.LEVEL_KEY, "0.1");
    CombatChoiceEngine.start(state, "tam_ma_cao_minh", 0);
    JSONObject nextStageFirstCombat = state.getJSONObject("combat");
    nextStageFirstCombat.getJSONObject("entity").put("hp", 1);
    finalizeAs(state, 2, 2, 1, 4, 6);
    CombatChoiceEngine.resolveFinalized(state);

    assertEquals(1, nextStageFirstCombat.getInt("stageIndex"));
    assertEquals(110, nextStageFirstCombat.getInt("coreDropReward"));
    assertEquals("treasure", nextStageFirstCombat.getString("coreDropRewardType"));
    assertEquals(220, progression.coreCount(state));

    CombatChoiceEngine.start(state, "tam_ma_cao_minh", 0);
    JSONObject nextStageRepeatCombat = state.getJSONObject("combat");
    nextStageRepeatCombat.getJSONObject("entity").put("hp", 1);
    finalizeAs(state, 2, 2, 1, 4, 6);
    CombatChoiceEngine.resolveFinalized(state);

    assertEquals(11, nextStageRepeatCombat.getInt("coreDropReward"));
    assertEquals(231, progression.coreCount(state));
  }

  @Test public void storyProgressRewardsCanonAndConvergeButNeverTrapLoop() throws Exception {
    JSONObject state = baseState("0");
    CharacterProgressionCore progression = new CharacterProgressionCore();
    progression.normalizeState(state);

    StoryCore.DecisionResolution canon = new StoryCore.DecisionResolution(
        "Đi tiếp", "", StoryCore.OUTCOME_CANON, null, false);
    assertEquals(5, GameCoreFacade.grantStoryProgressCore(state, canon));
    assertEquals(5, progression.coreCount(state));
    assertEquals(5, state.getJSONObject("flags").getInt("lastStoryCoreReward"));

    StoryCore.DecisionResolution trap = new StoryCore.DecisionResolution(
        "Đi vòng", "", StoryCore.OUTCOME_TRAP, null, true);
    assertEquals(0, GameCoreFacade.grantStoryProgressCore(state, trap));
    assertEquals(5, progression.coreCount(state));
    assertEquals(0, state.getJSONObject("flags").getInt("lastStoryCoreReward"));

    state.put(LevelCore.LEVEL_KEY, "0.1");
    StoryCore.DecisionResolution converge = new StoryCore.DecisionResolution(
        "Quan sát rồi tiến", "", StoryCore.OUTCOME_CONVERGE, null, false);
    assertEquals(8, GameCoreFacade.grantStoryProgressCore(state, converge));
    assertEquals(13, progression.coreCount(state));
    assertEquals(1, state.getJSONObject("flags").getInt("lastStoryCoreStageIndex"));
  }

  private static JSONObject baseState(String levelKey) throws Exception {
    return new JSONObject()
        .put("turn", 1)
        .put("currentLevel", 0)
        .put(LevelCore.LEVEL_KEY, levelKey)
        .put("player", new JSONObject().put("name", "Cao Minh"))
        .put("party", new JSONArray())
        .put("flags", new JSONObject());
  }

  private static JSONObject combatState(String levelKey) throws Exception {
    return baseState(levelKey)
        .put("location", LevelCore.LEVEL_ZERO_START_LOCATION)
        .put("log", new JSONArray().put(
            new JSONObject().put("role", "gm").put("text", "Hound xuất hiện")));
  }

  private static void finalizeAs(JSONObject state, int... values) throws Exception {
    JSONObject dice = state.getJSONObject("combat").getJSONObject("diceState");
    JSONArray array = new JSONArray();
    for (int value : values) array.put(value);
    dice.put("values", array)
        .put("hasRolled", true)
        .put("finalized", true)
        .put("resolved", false)
        .put("hand", CombatChoiceEngine.classify(values));
  }
}

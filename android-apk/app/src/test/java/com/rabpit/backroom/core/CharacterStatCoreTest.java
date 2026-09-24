package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CharacterStatCoreTest {
  @Test public void baseProjectionMatchesAuthoritativeCombatDefaults() throws Exception {
    JSONObject state = baseState();
    CharacterProgressionCore progression = new CharacterProgressionCore();
    progression.normalizeState(state);

    JSONObject projected = new CharacterStatCore().project(
        state, state.getJSONObject("player"), "cao_minh", progression);
    JSONObject status = projected.getJSONObject("combatStatus");

    assertEquals(30, status.getInt("damage"));
    assertEquals(0.0d, status.getDouble("defendPercent"), 0.001d);
    assertEquals(5, status.getInt("criticalChancePercent"));
    assertEquals(0, status.getInt("evasionPercent"));
    assertEquals(0, status.getInt("resCriticalPercent"));
    assertEquals(0, status.getInt("resEvasionPercent"));
  }

  @Test public void derivedStatusTracksCoreStatsAndRuntimeBaseAttack() throws Exception {
    JSONObject state = baseState();
    state.getJSONObject("player").put("attack", 40);
    CharacterProgressionCore progression = new CharacterProgressionCore();
    progression.normalizeState(state);

    JSONObject stats = progression.profile(state, "cao_minh").getJSONObject("stats");
    stats.put("STR", 10).put("DEF", 10).put("SKL", 10).put("VIT", 10);

    JSONObject projected = new CharacterStatCore().project(
        state, state.getJSONObject("player"), "cao_minh", progression);
    JSONObject status = projected.getJSONObject("combatStatus");

    assertEquals(60, status.getInt("damage"));
    assertEquals(33.3d, status.getDouble("defendPercent"), 0.001d);
    assertEquals(15, status.getInt("criticalChancePercent"));
    assertEquals(10, status.getInt("evasionPercent"));
    assertEquals(10, status.getInt("resCriticalPercent"));
    assertEquals(10, status.getInt("resEvasionPercent"));
  }

  @Test public void derivedChanceCapsRemainBoundedAtExtremeStats() {
    assertEquals(50, CharacterStatCore.criticalChancePercent(999));
    assertEquals(35, CharacterStatCore.evasionPercent(999));
    assertEquals(50, CharacterStatCore.criticalResistancePercent(999));
    assertEquals(50, CharacterStatCore.evasionResistancePercent(999));
  }

  @Test public void survivalPenaltyChangesBothProjectionAndMaxHpWithoutHealing() throws Exception {
    JSONObject state = baseState().put("gameTime", new JSONObject()
        .put("elapsedSubjectiveMinutes", 12L * 60L));
    CharacterProgressionCore progression = new CharacterProgressionCore();
    progression.normalizeState(state);
    progression.setCurrentHp(state, "cao_minh", 30);
    JSONObject projected = new CharacterStatCore().project(
        state, state.getJSONObject("player"), "cao_minh", progression);
    assertEquals(4, projected.getJSONObject("stats").getJSONObject("VIT").getInt("effective"));
    assertEquals(45, projected.getInt("maxHp"));
    assertEquals(30, projected.getInt("currentHp"));
    new SurvivalCore().restoreWater(state, "cao_minh", 100);
    assertEquals(50, new CharacterStatCore().project(
        state, state.getJSONObject("player"), "cao_minh", progression).getInt("maxHp"));
    assertEquals(30, progression.profile(state, "cao_minh").getInt("currentHp"));
  }

  @Test public void activeStatusRefreshesOnceStacksBySourceAndExpiresByClock() throws Exception {
    JSONObject state = baseState();
    CharacterProgressionCore progression = new CharacterProgressionCore();
    progression.applyStatusEffect(state, "cao_minh", "focus", "item:a", "explorer_turn",
        2, "STR", 2);
    progression.applyStatusEffect(state, "cao_minh", "focus", "item:a", "explorer_turn",
        2, "STR", 2);
    progression.applyStatusEffect(state, "cao_minh", "focus", "item:b", "explorer_turn",
        1, "STR", -1);
    CharacterStatCore stats = new CharacterStatCore();
    assertEquals(6, stats.project(state, "cao_minh", progression)
        .getJSONObject("stats").getJSONObject("STR").getInt("effective"));
    String saved = state.toString();
    state = new JSONObject(saved);
    assertEquals(6, stats.project(state, "cao_minh", progression)
        .getJSONObject("stats").getJSONObject("STR").getInt("effective"));
    progression.advanceStatusEffects(state, "cao_minh", "explorer_turn");
    assertEquals(7, stats.project(state, "cao_minh", progression)
        .getJSONObject("stats").getJSONObject("STR").getInt("effective"));
    progression.advanceStatusEffects(state, "cao_minh", "explorer_turn");
    assertEquals(5, stats.project(state, "cao_minh", progression)
        .getJSONObject("stats").getJSONObject("STR").getInt("effective"));
  }

  private static JSONObject baseState() throws Exception {
    return new JSONObject()
        .put("turn", 1)
        .put("currentLevel", 0)
        .put(LevelCore.LEVEL_KEY, "0")
        .put("player", new JSONObject().put("name", "Cao Minh"))
        .put("party", new JSONArray())
        .put("flags", new JSONObject());
  }
}

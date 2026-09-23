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

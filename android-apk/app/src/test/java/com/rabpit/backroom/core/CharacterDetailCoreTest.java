package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public class CharacterDetailCoreTest {
  @Test public void projectionShowsOnlyNewCombatStatsAndSharedCore() throws Exception {
    JSONObject state = new JSONObject()
        .put("turn", 1)
        .put("currentLevel", 0)
        .put(LevelCore.LEVEL_KEY, "0")
        .put("player", new JSONObject().put("name", "Cao Minh"))
        .put("party", new JSONArray())
        .put("inventory", new JSONArray())
        .put("gameTime", new JSONObject().put("elapsedSubjectiveMinutes", 0));
    CharacterProgressionCore progression = new CharacterProgressionCore();
    progression.normalizeState(state);
    progression.grantCore(state, 3);

    new CharacterDetailCore().projectState(state);

    JSONObject details = state.getJSONObject("partyDetails");
    assertEquals("cao_minh", details.getString("leaderId"));
    assertEquals(4, details.getInt("maxMembers"));
    assertEquals(3, details.getInt("coreCount"));
    JSONObject cao = details.getJSONArray("members").getJSONObject(0);
    assertEquals(50, cao.getInt("currentHp"));
    assertEquals(50, cao.getInt("maxHp"));
    JSONObject stats = cao.getJSONObject("stats");
    assertEquals(5, stats.getJSONObject("STR").getInt("effective"));
    assertEquals(5, stats.getJSONObject("DEF").getInt("effective"));
    assertEquals(5, stats.getJSONObject("SKL").getInt("effective"));
    assertEquals(5, stats.getJSONObject("VIT").getInt("effective"));
    assertFalse(cao.has("explorer"));
    assertFalse(cao.has("exp"));
    assertEquals("NORMAL", cao.getJSONObject("physiology").getString("hunger"));
  }

  @Test public void joinedLuciaUsesCurrentRuntimeBaseHpAndOwnPhysiology() throws Exception {
    JSONObject state = new JSONObject()
        .put("turn", 1)
        .put("currentLevel", 0)
        .put(LevelCore.LEVEL_KEY, "0")
        .put("player", new JSONObject().put("name", "Cao Minh"))
        .put("party", new JSONArray().put(new JSONObject()
            .put("id", "lucia").put("name", "Lucia Lục").put("joined", true)))
        .put("gameTime", new JSONObject().put("elapsedSubjectiveMinutes", 5));

    new CharacterDetailCore().projectState(state);

    JSONArray members = state.getJSONObject("partyDetails").getJSONArray("members");
    assertEquals(2, members.length());
    JSONObject lucia = members.getJSONObject(1);
    assertEquals("lucia", lucia.getString("id"));
    assertEquals(50, lucia.getInt("maxHp"));
    assertEquals(50, lucia.getInt("baseMaxHp"));
    assertEquals(5, lucia.getJSONObject("stats").getJSONObject("VIT").getInt("effective"));
    assertNotNull(lucia.getJSONArray("inventory"));
  }

  @Test public void legacyThresholdsAndPercentagesRemainSurvivalOwned() throws Exception {
    JSONObject physiology = CharacterDetailCore.derivePhysiology(
        12L * 60L, 12L * 60L, 12L * 60L);
    assertEquals("MILD", physiology.getString("hunger"));
    assertEquals("MODERATE", physiology.getString("thirst"));
    assertEquals("NORMAL", physiology.getString("sleepDeprivation"));
    assertEquals(83, physiology.getInt("foodPercent"));
    assertEquals(75, physiology.getInt("waterPercent"));
    assertEquals(66, physiology.getInt("restPercent"));
  }
}

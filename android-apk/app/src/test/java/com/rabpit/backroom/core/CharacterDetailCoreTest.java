package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public class CharacterDetailCoreTest {
  @Test public void freshKaiProjectsHpExplorerExpAndStatsWithoutEquipment() throws Exception {
    JSONObject state = new JSONObject()
        .put("player", new JSONObject().put("name", "Kai Akechi"))
        .put("party", new JSONArray())
        .put("inventory", new JSONArray())
        .put("gameTime", new JSONObject().put("elapsedSubjectiveMinutes", 0));

    new CharacterDetailCore().projectState(state);

    JSONObject details = state.getJSONObject("partyDetails");
    assertEquals("kai", details.getString("leaderId"));
    assertEquals(4, details.getInt("maxMembers"));
    JSONObject kai = details.getJSONArray("members").getJSONObject(0);
    assertEquals(50, kai.getInt("currentHp"));
    assertEquals(50, kai.getInt("maxHp"));
    assertEquals(0, kai.getInt("explorer"));
    assertEquals(0, kai.getInt("exp"));
    assertEquals(50, kai.getInt("requiredExp"));
    assertEquals(40, baseTotal(kai.getJSONObject("stats")));
    assertEquals(0, kai.getJSONObject("stats").getJSONObject("STR").getInt("explorer"));
    assertFalse(kai.getJSONObject("stats").getJSONObject("STR").has("equipment"));
    assertFalse(kai.has("equipment"));
    assertEquals("NORMAL", kai.getJSONObject("physiology").getString("hunger"));
    assertEquals(100, kai.getJSONObject("physiology").getInt("foodPercent"));
  }

  @Test public void legacyThresholdsAndPercentagesMatchPreviousRuntime() throws Exception {
    JSONObject physiology = CharacterDetailCore.derivePhysiology(
        12L * 60L, 12L * 60L, 12L * 60L);
    assertEquals("MILD", physiology.getString("hunger"));
    assertEquals("MODERATE", physiology.getString("thirst"));
    assertEquals("NORMAL", physiology.getString("sleepDeprivation"));
    assertEquals(83, physiology.getInt("foodPercent"));
    assertEquals(75, physiology.getInt("waterPercent"));
    assertEquals(66, physiology.getInt("restPercent"));
  }

  @Test public void projectionIncludesOnlyJoinedCompanionsAndUsesProgressionStats() throws Exception {
    JSONObject lucia = new JSONObject()
        .put("id", "lucia").put("name", "Lucia Lục").put("joined", true)
        .put("stats", new JSONObject().put("STR", 999))
        .put("hp", 999).put("maxHp", 999);
    JSONObject notJoined = new JSONObject()
        .put("id", "iris").put("name", "Iris").put("joined", false);

    JSONObject state = new JSONObject()
        .put("player", new JSONObject().put("name", "Kai Akechi"))
        .put("party", new JSONArray().put(lucia).put(notJoined))
        .put("gameTime", new JSONObject().put("elapsedSubjectiveMinutes", 5));

    new CharacterDetailCore().projectState(state);

    JSONArray members = state.getJSONObject("partyDetails").getJSONArray("members");
    assertEquals(2, members.length());
    JSONObject projectedLucia = members.getJSONObject(1);
    assertEquals("lucia", projectedLucia.getString("id"));
    assertEquals(50, projectedLucia.getInt("currentHp"));
    assertEquals(50, projectedLucia.getInt("maxHp"));
    assertEquals(30, baseTotal(projectedLucia.getJSONObject("stats")));
    assertEquals("NORMAL", projectedLucia.getJSONObject("physiology").getString("hunger"));
    assertEquals(100, projectedLucia.getJSONObject("physiology").getInt("foodPercent"));
  }

  @Test public void existingPerCharacterPhysiologySurvivesProjection() throws Exception {
    JSONObject oldLucia = new JSONObject()
        .put("id", "lucia")
        .put("physiology", new JSONObject()
            .put("hunger", "MILD")
            .put("thirst", "NORMAL")
            .put("sleepDeprivation", "MODERATE")
            .put("foodPercent", 72)
            .put("waterPercent", 88)
            .put("restPercent", 41));
    JSONObject state = new JSONObject()
        .put("player", new JSONObject().put("name", "Kai Akechi"))
        .put("party", new JSONArray().put(new JSONObject()
            .put("id", "lucia").put("name", "Lucia Lục").put("joined", true)))
        .put("partyDetails", new JSONObject().put("members", new JSONArray().put(oldLucia)))
        .put("gameTime", new JSONObject().put("elapsedSubjectiveMinutes", 90));

    new CharacterDetailCore().projectState(state);
    JSONObject lucia = state.getJSONObject("partyDetails").getJSONArray("members").getJSONObject(1);
    JSONObject physiology = lucia.getJSONObject("physiology");
    assertEquals("MILD", physiology.getString("hunger"));
    assertEquals("MODERATE", physiology.getString("sleepDeprivation"));
    assertEquals(72, physiology.getInt("foodPercent"));
    assertEquals(41, physiology.getInt("restPercent"));
    assertNotNull(lucia.getJSONArray("inventory"));
  }

  private static int baseTotal(JSONObject stats) throws Exception {
    return stats.getJSONObject("STR").optInt("base", 0)
        + stats.getJSONObject("DF").optInt("base", 0)
        + stats.getJSONObject("AGI").optInt("base", 0)
        + stats.getJSONObject("CRIT").optInt("base", 0);
  }
}

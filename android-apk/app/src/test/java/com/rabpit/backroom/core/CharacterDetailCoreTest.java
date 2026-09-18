package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class CharacterDetailCoreTest {
  @Test public void freshKaiGetsRestoredSurvivalProjection() throws Exception {
    JSONObject state = new JSONObject()
        .put("player", new JSONObject().put("name", "Kai Akechi"))
        .put("party", new JSONArray())
        .put("inventory", new JSONArray())
        .put("gameTime", new JSONObject().put("elapsedSubjectiveMinutes", 0));

    new CharacterDetailCore().projectState(state);

    JSONObject details = state.getJSONObject("partyDetails");
    assertEquals("kai", details.getString("leaderId"));
    assertEquals(4, details.getInt("maxMembers"));
    assertEquals(1, details.getJSONArray("members").length());

    JSONObject kai = details.getJSONArray("members").getJSONObject(0);
    assertEquals("kai", kai.getString("id"));
    assertEquals(100, kai.getInt("currentHp"));
    assertEquals(100, kai.getInt("maxHp"));
    assertEquals("∞", kai.getString("energy"));
    assertEquals(4, kai.getInt("hpRegen"));
    assertEquals(82, kai.getJSONObject("stats").getInt("STR"));
    assertEquals(78, kai.getJSONObject("stats").getInt("DF"));
    assertEquals(92, kai.getJSONObject("stats").getInt("AGI"));
    assertEquals(95, kai.getJSONObject("stats").getInt("CRIT"));

    JSONObject physiology = kai.getJSONObject("physiology");
    assertEquals("NORMAL", physiology.getString("hunger"));
    assertEquals("NORMAL", physiology.getString("thirst"));
    assertEquals("NORMAL", physiology.getString("sleepDeprivation"));
    assertEquals(100, physiology.getInt("foodPercent"));
    assertEquals(100, physiology.getInt("waterPercent"));
    assertEquals(100, physiology.getInt("restPercent"));
  }

  @Test public void legacyThresholdsAndPercentagesMatchPreviousRuntime() throws Exception {
    JSONObject physiology = CharacterDetailCore.derivePhysiology(12L * 60L, 12L * 60L, 12L * 60L);

    assertEquals("MILD", physiology.getString("hunger"));
    assertEquals("MODERATE", physiology.getString("thirst"));
    assertEquals("NORMAL", physiology.getString("sleepDeprivation"));
    assertEquals(83, physiology.getInt("foodPercent"));
    assertEquals(75, physiology.getInt("waterPercent"));
    assertEquals(66, physiology.getInt("restPercent"));
  }

  @Test public void projectionIncludesOnlyJoinedCompanionsAndPreservesLuciaStats() throws Exception {
    JSONObject lucia = new JSONObject()
        .put("id", "lucia")
        .put("name", "Lucia Lục")
        .put("joined", true)
        .put("present", true)
        .put("hp", 76)
        .put("maxHp", 100)
        .put("stats", new JSONObject().put("STR", 7).put("DF", 7).put("AGI", 8).put("CRIT", 7));
    JSONObject notJoined = new JSONObject()
        .put("id", "iris")
        .put("name", "Iris")
        .put("joined", false);

    JSONObject state = new JSONObject()
        .put("player", new JSONObject().put("name", "Kai Akechi"))
        .put("party", new JSONArray().put(lucia).put(notJoined))
        .put("gameTime", new JSONObject().put("elapsedSubjectiveMinutes", 5));

    new CharacterDetailCore().projectState(state);

    JSONArray members = state.getJSONObject("partyDetails").getJSONArray("members");
    assertEquals(2, members.length());
    JSONObject projectedLucia = members.getJSONObject(1);
    assertEquals("lucia", projectedLucia.getString("id"));
    assertEquals(76, projectedLucia.getInt("currentHp"));
    assertEquals(100, projectedLucia.getInt("maxHp"));
    assertEquals(7, projectedLucia.getJSONObject("stats").getInt("STR"));
    assertEquals("UNKNOWN", projectedLucia.getJSONObject("physiology").getString("hunger"));
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
}

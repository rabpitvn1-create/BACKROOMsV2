package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SurvivalCoreTest {
  @Test public void kaiPreservesLegacyElapsedSurvivalBaseline() throws Exception {
    SurvivalCore core = new SurvivalCore();
    JSONObject state = new JSONObject()
        .put("gameTime", new JSONObject().put("elapsedSubjectiveMinutes", 12L * 60L))
        .put("party", new JSONArray());

    JSONObject physiology = core.projectPhysiology(state, "kai");

    assertEquals("MILD", physiology.getString("hunger"));
    assertEquals("MODERATE", physiology.getString("thirst"));
    assertEquals("NORMAL", physiology.getString("sleepDeprivation"));
  }

  @Test public void foodAndWaterRestoreAuthoritativePercentages() throws Exception {
    SurvivalCore core = new SurvivalCore();
    JSONObject state = new JSONObject()
        .put("gameTime", new JSONObject().put("elapsedSubjectiveMinutes", 24L * 60L))
        .put("party", new JSONArray());

    int foodGain = core.restoreFood(state, "kai", 45);
    int waterGain = core.restoreWater(state, "kai", 50);
    JSONObject physiology = core.projectPhysiology(state, "kai");

    assertEquals(34, foodGain);
    assertEquals(50, waterGain);
    assertEquals(100, physiology.getInt("foodPercent"));
    assertEquals(100, physiology.getInt("waterPercent"));
  }

  @Test public void joinedCompanionStartsSurvivalClockAtJoinTime() throws Exception {
    SurvivalCore core = new SurvivalCore();
    JSONObject state = new JSONObject()
        .put("gameTime", new JSONObject().put("elapsedSubjectiveMinutes", 500))
        .put("party", new JSONArray().put(
            new JSONObject().put("id", "lucia").put("name", "Lucia Lục").put("joined", true)));

    JSONObject physiology = core.projectPhysiology(state, "lucia");

    assertEquals(100, physiology.getInt("foodPercent"));
    assertEquals(100, physiology.getInt("waterPercent"));
    assertEquals(100, physiology.getInt("restPercent"));
  }
}

package com.rabpit.backroom.core;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class EntityStatCoreTest {
  @Test public void entityBaselineUsesOnlyLuciaBaseStatsAtNinetyThreePercent() throws Exception {
    CharacterProgressionCore progression = new CharacterProgressionCore(bound -> 0);
    JSONObject state = new JSONObject();
    progression.normalizeState(state);
    JSONObject lucia = progression.profile(state, "lucia");
    lucia.put("explorer", 9).put("exp", 777).put("currentHp", 1).put("maxHp", 999);
    lucia.getJSONObject("baseStats")
        .put("STR", 7).put("DF", 8).put("AGI", 10).put("CRIT", 12);

    JSONObject baseline = new EntityStatCore().baseline(state, progression);
    assertEquals(7, baseline.getInt("STR"));
    assertEquals(7, baseline.getInt("DF"));
    assertEquals(9, baseline.getInt("AGI"));
    assertEquals(11, baseline.getInt("CRIT"));
    assertEquals(172, baseline.getInt("maxHp"));
  }

  @Test public void roundingUsesHalfUpRule() {
    assertEquals(7, EntityStatCore.scaleFromLuciaBase(7));
    assertEquals(7, EntityStatCore.scaleFromLuciaBase(8));
    assertEquals(47, EntityStatCore.scaleFromLuciaBase(50));
  }

  @Test public void speciesHpPreservesBaselineRatioAndScalesWithLuciaProgression() throws Exception {
    CharacterProgressionCore progression = new CharacterProgressionCore(bound -> 0);
    JSONObject state = new JSONObject();
    progression.normalizeState(state);
    JSONObject lucia = progression.profile(state, "lucia");

    EntityStatCore core = new EntityStatCore();
    JSONObject baseProfile = core.profile(state, "hound", progression, 240);
    assertEquals(240, baseProfile.getJSONObject("effective").getInt("maxHp"));

    lucia.put("explorer", 3);
    progression.normalizeState(state);
    JSONObject scaledProfile = core.profile(state, "hound", progression, 240);
    assertEquals(449, scaledProfile.getJSONObject("effective").getInt("maxHp"));
  }

  @Test public void speciesModifierHookDefaultsToZero() throws Exception {
    CharacterProgressionCore progression = new CharacterProgressionCore(bound -> 0);
    JSONObject state = new JSONObject();
    progression.normalizeState(state);
    JSONObject modifier = new EntityStatCore().profile(state, "hound", progression)
        .getJSONObject("modifier");
    assertEquals(0, modifier.getInt("STR"));
    assertEquals(0, modifier.getInt("DF"));
    assertEquals(0, modifier.getInt("AGI"));
    assertEquals(0, modifier.getInt("CRIT"));
    assertEquals(0, modifier.getInt("maxHp"));
  }
}

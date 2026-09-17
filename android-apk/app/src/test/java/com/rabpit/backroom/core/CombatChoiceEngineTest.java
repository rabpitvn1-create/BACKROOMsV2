package com.rabpit.backroom.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CombatChoiceEngineTest {
  @Test public void attackUsesMaximumNeutralLegacyDamage() {
    assertEquals(28, CombatChoiceEngine.maxNormalDamage(30, 2));
    assertEquals(22, CombatChoiceEngine.maxNormalDamage(30, 8));
    assertEquals(1, CombatChoiceEngine.maxNormalDamage(5, 99));
  }

  @Test public void defendDoublesDefenseForIncomingDamage() {
    assertEquals(10, CombatChoiceEngine.normalIncomingDamage(20, 10));
    assertEquals(1, CombatChoiceEngine.defendedIncomingDamage(20, 10));
    assertEquals(8, CombatChoiceEngine.defendedIncomingDamage(22, 7));
  }

  @Test public void counterUsesHalfNormalAttackDamage() {
    assertEquals(10, CombatChoiceEngine.counterDamage(20));
    assertEquals(11, CombatChoiceEngine.counterDamage(21));
    assertEquals(1, CombatChoiceEngine.counterDamage(1));
  }

  @Test public void battleAcceptsOnlyFixedAbcTokens() {
    assertTrue(CombatChoiceEngine.isCombatAction(CombatChoiceEngine.ACTION_A));
    assertTrue(CombatChoiceEngine.isCombatAction(CombatChoiceEngine.ACTION_B));
    assertTrue(CombatChoiceEngine.isCombatAction(CombatChoiceEngine.ACTION_C));
    assertFalse(CombatChoiceEngine.isCombatAction("Kai chạy sang trái"));
  }

  @Test public void guiltyCrownOverrideAppearsAsFortyPercentKaiProc() throws Exception {
    boolean found = false;
    for (int turn = 1; turn <= 80 && !found; turn++) {
      org.json.JSONObject state = new org.json.JSONObject()
        .put("turn", turn)
        .put("player", new org.json.JSONObject().put("name", "Kai Akechi"))
        .put("party", new org.json.JSONArray())
        .put("log", new org.json.JSONArray().put(new org.json.JSONObject().put("role", "gm").put("text", "Encounter")));
      CombatChoiceEngine.start(state, "diep_minh", 0);
      org.json.JSONObject skill = state.getJSONObject("combat").optJSONObject("currentSkill");
      if (skill != null && "Guilty Crown Override".equals(skill.optString("name"))) {
        assertEquals(40, skill.optInt("procPercent"));
        assertTrue(skill.optBoolean("offensive"));
        found = true;
      }
    }
    assertTrue("Guilty Crown Override must be selectable for Kai", found);
  }

  @Test public void guiltyCrownOverrideDealsExactTwoHundredFortyHpWhenProcSucceeds() throws Exception {
    boolean procObserved = false;
    for (int turn = 1; turn <= 160 && !procObserved; turn++) {
      org.json.JSONObject state = new org.json.JSONObject()
        .put("turn", turn)
        .put("player", new org.json.JSONObject().put("name", "Kai Akechi").put("hp", 100).put("maxHp", 100))
        .put("party", new org.json.JSONArray())
        .put("log", new org.json.JSONArray().put(new org.json.JSONObject().put("role", "gm").put("text", "Encounter")));
      CombatChoiceEngine.start(state, "diep_minh", 0);
      org.json.JSONObject combat = state.getJSONObject("combat");
      combat.put("currentSkill", new org.json.JSONObject()
        .put("name", "Guilty Crown Override")
        .put("procPercent", 40)
        .put("damagePercent", 0)
        .put("effect", "")
        .put("effectTurns", 0)
        .put("effectValue", 0)
        .put("offensive", true));
      CombatChoiceEngine.resolve(state, CombatChoiceEngine.ACTION_C);
      org.json.JSONObject entity = state.getJSONObject("combat").getJSONObject("entity");
      if (entity.optInt("hp") == 1760) procObserved = true;
    }
    assertTrue("A successful Guilty Crown proc must deal exactly 240 HP", procObserved);
  }

  @Test public void knownEntityCatalogPreservesLegacyProfiles() {
    assertTrue(CombatChoiceEngine.isKnownEntity("hound"));
    assertTrue(CombatChoiceEngine.isKnownEntity("jeff_the_killer"));
    assertTrue(CombatChoiceEngine.isKnownEntity("slenderman"));
    assertFalse(CombatChoiceEngine.isKnownEntity("not_a_real_entity"));
  }
}

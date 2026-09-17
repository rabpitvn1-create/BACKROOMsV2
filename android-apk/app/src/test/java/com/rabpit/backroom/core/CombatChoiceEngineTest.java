package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;
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

  @Test public void combatPartyCapacityIsFourIncludingKai() {
    assertEquals(4, CombatChoiceEngine.maxCombatParticipants());
  }

  @Test public void battleAcceptsOnlyFixedAbcTokens() {
    assertTrue(CombatChoiceEngine.isCombatAction(CombatChoiceEngine.ACTION_A));
    assertTrue(CombatChoiceEngine.isCombatAction(CombatChoiceEngine.ACTION_B));
    assertTrue(CombatChoiceEngine.isCombatAction(CombatChoiceEngine.ACTION_C));
    assertFalse(CombatChoiceEngine.isCombatAction("Kai chạy sang trái"));
  }

  @Test public void guiltyCrownOverrideUsesFortyPercentProcAndExactDamage() {
    assertEquals(40, CombatChoiceEngine.configuredProcPercent("kai", "Guilty Crown Override"));
    assertEquals(240, CombatChoiceEngine.exactDamageForSkill("Guilty Crown Override"));
    assertEquals(0, CombatChoiceEngine.exactDamageForSkill("The Last Requiem"));
  }

  @Test public void knownEntityCatalogPreservesLegacyProfiles() {
    assertTrue(CombatChoiceEngine.isKnownEntity("hound"));
    assertTrue(CombatChoiceEngine.isKnownEntity("jeff_the_killer"));
    assertTrue(CombatChoiceEngine.isKnownEntity("slenderman"));
    assertFalse(CombatChoiceEngine.isKnownEntity("not_a_real_entity"));
  }

  @Test public void soloCombatRosterContainsOnlyKai() throws Exception {
    JSONObject state = combatState(new JSONArray());
    CombatChoiceEngine.start(state, "hound", 0);
    JSONArray participants = state.getJSONObject("combat").getJSONArray("participants");
    assertEquals(1, participants.length());
    assertEquals("kai", participants.getJSONObject(0).getString("id"));
  }

  @Test public void onlyJoinedPartyCharactersEnterCombatInCanonicalOrder() throws Exception {
    JSONArray party = new JSONArray()
        .put(new JSONObject().put("id", "lucia").put("name", "Lucia Lục").put("joined", true))
        .put(new JSONObject().put("id", "iris").put("name", "Iris").put("joined", false))
        .put(new JSONObject().put("id", "syvial").put("name", "Syvial").put("joined", true));
    JSONObject state = combatState(party);
    CombatChoiceEngine.start(state, "hound", 0);
    JSONArray participants = state.getJSONObject("combat").getJSONArray("participants");
    assertEquals(3, participants.length());
    assertEquals("kai", participants.getJSONObject(0).getString("id"));
    assertEquals("lucia", participants.getJSONObject(1).getString("id"));
    assertEquals("syvial", participants.getJSONObject(2).getString("id"));
  }

  private static JSONObject combatState(JSONArray party) throws Exception {
    return new JSONObject()
        .put("turn", 4)
        .put("player", new JSONObject().put("name", "Kai Akechi"))
        .put("party", party)
        .put("flags", new JSONObject().put("entityEncounterKey", "hound"))
        .put("log", new JSONArray().put(new JSONObject().put("role", "gm").put("text", "Hound xuất hiện")));
  }
}

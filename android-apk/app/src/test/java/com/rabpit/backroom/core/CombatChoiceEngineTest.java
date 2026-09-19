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

  @Test public void criticalDamageContractIsThreePointFiveTimesBase() {
    assertEquals(3.5d, CombatChoiceEngine.CRITICAL_DAMAGE_MULTIPLIER, 0.0d);
  }

  @Test public void guiltyCrownOverrideUsesFortyPercentProcAndExactDamage() {
    assertEquals(40, CombatChoiceEngine.configuredProcPercent("kai", "Guilty Crown Override"));
    assertEquals(240, CombatChoiceEngine.exactDamageForSkill("Guilty Crown Override"));
    assertEquals(0, CombatChoiceEngine.exactDamageForSkill("The Last Requiem"));
  }

  @Test public void knownEntityCatalogPreservesLegacyProfiles() throws Exception {
    assertTrue(CombatChoiceEngine.isKnownEntity("hound"));
    assertTrue(CombatChoiceEngine.isKnownEntity("jeff_the_killer"));
    assertTrue(CombatChoiceEngine.isKnownEntity("slenderman"));
    assertFalse(CombatChoiceEngine.isKnownEntity("not_a_real_entity"));

    JSONObject state = combatState(new JSONArray());
    CombatChoiceEngine.start(state, "hound", 0);
    JSONObject entity = state.getJSONObject("combat").getJSONObject("entity");
    assertEquals(240, entity.getInt("maxHp"));
    assertEquals(15, entity.getInt("attack"));
    assertEquals(2, entity.getInt("defense"));
    assertTrue(entity.has("statBaseline"));
    assertTrue(entity.has("statModifier"));
  }

  @Test public void sub500EntityHpIsIncreasedByTwoHundredPercent() throws Exception {
    String[] keys = {
        "hound", "clump", "duller", "deathmoth", "hostile_faceling", "false_puddle",
        "paintings", "smiler", "skin-stealer", "predatory_window", "biological_pipeline",
        "wretch", "cable_mimic", "the_beast_of_level_5", "hotel_corpse_lure",
        "jeff_the_killer", "jane_the_killer", "slenderman", "diep_minh"
    };
    int[] expectedHp = {
        240, 315, 270, 195, 225, 285,
        210, 255, 300, 345, 360,
        255, 300, 435, 330,
        360, 360, 480, 2000
    };

    for (int i = 0; i < keys.length; i++) {
      JSONObject state = combatState(new JSONArray());
      CombatChoiceEngine.start(state, keys[i], 0);
      JSONObject entity = state.getJSONObject("combat").getJSONObject("entity");
      assertEquals(keys[i] + " maxHp", expectedHp[i], entity.getInt("maxHp"));
      assertEquals(keys[i] + " hp", expectedHp[i], entity.getInt("hp"));
    }
  }

  @Test public void soloCombatRosterContainsOnlyKaiAndUsesProgressionHp() throws Exception {
    JSONObject state = combatState(new JSONArray());
    CombatChoiceEngine.start(state, "hound", 0);
    JSONArray participants = state.getJSONObject("combat").getJSONArray("participants");
    assertEquals(1, participants.length());
    assertEquals("kai", participants.getJSONObject(0).getString("id"));
    assertEquals(50, participants.getJSONObject(0).getInt("hp"));
    assertEquals(50, participants.getJSONObject(0).getInt("maxHp"));
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

  @Test public void defeatConsumesEncounterAndDoesNotLeaveRestartFlag() throws Exception {
    JSONObject state = combatState(new JSONArray());
    CombatChoiceEngine.start(state, "clump", 0);

    JSONObject combat = state.getJSONObject("combat");
    combat.getJSONArray("participants").getJSONObject(0).put("hp", 1);
    CombatChoiceEngine.resolve(state, CombatChoiceEngine.ACTION_A);

    assertFalse(state.getJSONObject("combat").getBoolean("active"));
    assertEquals("defeat", state.getJSONObject("combat").getString("outcome"));
    assertEquals("", state.getJSONObject("flags").getString("entityEncounterKey"));
    assertEquals(0, state.getJSONObject("characterProgression")
        .getJSONObject("characters").getJSONObject("kai").getInt("currentHp"));
  }

  @Test public void terminalDefeatNormalizationClearsLegacyRestartFlag() throws Exception {
    JSONObject state = combatState(new JSONArray());
    state.put("combat", new JSONObject()
        .put("active", false)
        .put("outcome", "defeat"));
    state.getJSONObject("flags").put("entityEncounterKey", "clump");

    CombatChoiceEngine.normalizeTerminalEncounter(state);

    assertEquals("", state.getJSONObject("flags").getString("entityEncounterKey"));
  }

  @Test public void houndKillRewardsExpExactlyOnce() throws Exception {
    JSONObject state = combatState(new JSONArray());
    CombatChoiceEngine.start(state, "hound", 0);
    state.getJSONObject("combat").getJSONObject("entity").put("hp", 1);
    CombatChoiceEngine.resolve(state, CombatChoiceEngine.ACTION_A);

    JSONObject kai = state.getJSONObject("characterProgression")
        .getJSONObject("characters").getJSONObject("kai");
    assertEquals(10, kai.getInt("exp"));
    assertTrue(state.getJSONObject("combat").getBoolean("expResolved"));

    CombatChoiceEngine.resolve(state, CombatChoiceEngine.ACTION_A);
    assertEquals(10, kai.getInt("exp"));
  }

  private static JSONObject combatState(JSONArray party) throws Exception {
    return new JSONObject()
        .put("turn", 4)
        .put("player", new JSONObject().put("name", "Kai Akechi"))
        .put("party", party)
        .put("flags", new JSONObject().put("entityEncounterKey", "hound"))
        .put("log", new JSONArray().put(
            new JSONObject().put("role", "gm").put("text", "Hound xuất hiện")));
  }
}

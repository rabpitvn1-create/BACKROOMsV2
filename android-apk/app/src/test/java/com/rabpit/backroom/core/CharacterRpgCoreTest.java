package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class CharacterRpgCoreTest {
  @Test public void freshKaiGetsLevelOneAndCurrentLoadout() throws Exception {
    JSONObject state = new JSONObject();
    CharacterLevelCore levels = new CharacterLevelCore();
    EquipmentStatCore equipment = new EquipmentStatCore();

    levels.normalizeState(state);
    equipment.normalizeState(state);

    assertEquals(1, levels.levelFor(state, "kai"));
    JSONArray loadout = state.getJSONObject("characterRpg")
        .getJSONObject("characters")
        .getJSONObject("kai")
        .getJSONArray("equipment");
    assertEquals(3, loadout.length());
    assertEquals(EquipmentStatCore.MK19_ID, loadout.getString(0));
    assertEquals(EquipmentStatCore.MK20_ID, loadout.getString(1));
    assertEquals(EquipmentStatCore.OMNIVAULT_ID, loadout.getString(2));
  }

  @Test public void equipmentBonusesPreserveLegacyNormalizedEffectiveStats() throws Exception {
    JSONObject state = new JSONObject();
    CharacterLevelCore levels = new CharacterLevelCore();
    EquipmentStatCore equipment = new EquipmentStatCore();
    CharacterStatCore stats = new CharacterStatCore();

    levels.normalizeState(state);
    equipment.normalizeState(state);
    JSONObject projection = stats.project(state, "kai", levels, equipment);
    JSONObject values = projection.getJSONObject("stats");

    assertEquals(107, values.getJSONObject("STR").getInt("effective"));
    assertEquals(109, values.getJSONObject("DF").getInt("effective"));
    assertEquals(112, values.getJSONObject("AGI").getInt("effective"));
    assertEquals(109, values.getJSONObject("CRIT").getInt("effective"));
    assertEquals(25, values.getJSONObject("STR").getInt("equipment"));
    assertEquals(31, values.getJSONObject("DF").getInt("equipment"));
    assertEquals(20, values.getJSONObject("AGI").getInt("equipment"));
    assertEquals(14, values.getJSONObject("CRIT").getInt("equipment"));
  }

  @Test public void levelIsIndependentUntilGrowthRuleIsConfigured() throws Exception {
    JSONObject state = new JSONObject()
        .put("characterRpg", new JSONObject().put("characters", new JSONObject().put("kai",
            new JSONObject().put("level", 7))));
    CharacterLevelCore levels = new CharacterLevelCore();
    EquipmentStatCore equipment = new EquipmentStatCore();
    CharacterStatCore stats = new CharacterStatCore();

    levels.normalizeState(state);
    equipment.normalizeState(state);
    JSONObject projection = stats.project(state, "kai", levels, equipment);

    assertEquals(7, projection.getInt("level"));
    assertFalse(projection.getBoolean("levelGrowthConfigured"));
    assertEquals(107, projection.getJSONObject("stats").getJSONObject("STR").getInt("effective"));
  }

  @Test public void unregisteredEquipmentCannotInjectStats() throws Exception {
    JSONObject state = new JSONObject()
        .put("characterRpg", new JSONObject().put("characters", new JSONObject().put("kai",
            new JSONObject().put("level", 1).put("equipment",
                new JSONArray().put("forged-gemini-item").put(EquipmentStatCore.MK19_ID)))));
    CharacterLevelCore levels = new CharacterLevelCore();
    EquipmentStatCore equipment = new EquipmentStatCore();

    levels.normalizeState(state);
    equipment.normalizeState(state);

    JSONArray normalized = state.getJSONObject("characterRpg")
        .getJSONObject("characters")
        .getJSONObject("kai")
        .getJSONArray("equipment");
    assertEquals(1, normalized.length());
    assertEquals(EquipmentStatCore.MK19_ID, normalized.getString(0));
    assertEquals(8, equipment.aggregate(state, "kai").crit);
  }
}

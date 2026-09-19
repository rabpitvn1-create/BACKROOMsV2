package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class EquipmentStatCoreTest {
  @Test public void legacyEquipmentBonusesAreAllZero() throws Exception {
    JSONObject state = new JSONObject();
    new CharacterProgressionCore(bound -> 0).normalizeState(state);
    EquipmentStatCore equipment = new EquipmentStatCore();
    EquipmentStatCore.Bonus bonus = equipment.aggregate(state, "kai");
    assertEquals(0, bonus.str);
    assertEquals(0, bonus.df);
    assertEquals(0, bonus.agi);
    assertEquals(0, bonus.crit);

    JSONArray projected = equipment.projection(state, "kai");
    assertEquals(3, projected.length());
    for (int i = 0; i < projected.length(); i++) {
      JSONObject stats = projected.getJSONObject(i).getJSONObject("stats");
      assertEquals(0, stats.getInt("STR"));
      assertEquals(0, stats.getInt("DF"));
      assertEquals(0, stats.getInt("AGI"));
      assertEquals(0, stats.getInt("CRIT"));
    }
  }
}

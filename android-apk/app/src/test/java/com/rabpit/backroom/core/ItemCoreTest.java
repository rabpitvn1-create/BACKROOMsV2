package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ItemCoreTest {
  @Test public void chestSpawnRateIsExactlyThreePercentBoundary() {
    assertTrue(ItemCore.shouldSpawnChest(0));
    assertTrue(ItemCore.shouldSpawnChest(2));
    assertFalse(ItemCore.shouldSpawnChest(3));
    assertFalse(ItemCore.shouldSpawnChest(99));
  }

  @Test public void everyEntityDropRateStaysBetweenTenAndTwentyPercent() {
    String[] keys = {
      "hound","clump","duller","deathmoth","hostile_faceling","false_puddle","paintings","smiler",
      "skin-stealer","predatory_window","biological_pipeline","wretch","cable_mimic",
      "the_beast_of_level_5","hotel_corpse_lure","jeff_the_killer","jane_the_killer","slenderman","diep_minh"
    };
    for (String key : keys) {
      int rate = ItemCore.entityDropRatePercent(key);
      assertTrue(rate >= 10);
      assertTrue(rate <= 20);
      assertTrue(ItemCore.shouldDropEntityLoot(rate - 1, rate));
      assertFalse(ItemCore.shouldDropEntityLoot(rate, rate));
    }
  }

  @Test public void allEightConsumableEffectsMatchContract() {
    assertEquals(50, ItemCore.itemEffectValue(ItemCore.ALMOND_WATER_ID, "hunger"));
    assertEquals(100, ItemCore.itemEffectValue(ItemCore.ALMOND_WATER_ID, "thirst"));
    assertEquals(15, ItemCore.itemEffectValue(ItemCore.BANDAGE_ID, "hp"));
    assertEquals(35, ItemCore.itemEffectValue(ItemCore.FIRST_AID_KIT_ID, "hp"));
    assertEquals(50, ItemCore.itemEffectValue(ItemCore.LAVIE_WATER_ID, "thirst"));
    assertEquals(10, ItemCore.itemEffectValue(ItemCore.COCONUT_WATER_ID, "hunger"));
    assertEquals(70, ItemCore.itemEffectValue(ItemCore.COCONUT_WATER_ID, "thirst"));
    assertEquals(45, ItemCore.itemEffectValue(ItemCore.BANH_MI_THIT_ID, "hunger"));
    assertEquals(25, ItemCore.itemEffectValue(ItemCore.HOT_SOY_MILK_ID, "hunger"));
    assertEquals(30, ItemCore.itemEffectValue(ItemCore.HOT_SOY_MILK_ID, "thirst"));
    assertEquals(80, ItemCore.itemEffectValue(ItemCore.COM_TAM_SUON_BI_CHA_ID, "hunger"));
    assertEquals(0, ItemCore.itemEffectValue(ItemCore.COM_TAM_SUON_BI_CHA_ID, "hp"));
  }

  @Test public void chestPoolContainsAllEightNamedConsumables() throws Exception {
    JSONObject state = new JSONObject();
    String[] expected = {
      "Almond Water",
      "Băng Gạc Y Tế",
      "Túi Sơ Cứu",
      "Nước Suối Lavie",
      "Nước Dừa",
      "Bánh Mì Thịt",
      "Sữa Đậu Nành Nóng",
      "Cơm Tấm Sườn Bì Chả"
    };
    for (int i = 0; i < expected.length; i++) {
      assertEquals(expected[i], ItemCore.grantChestLootItem(state, i));
    }
    assertEquals(8, state.getJSONArray("inventory").length());
  }

  @Test public void entityPoolNeverDropsOrdinarySaigonFood() throws Exception {
    JSONObject state = new JSONObject();
    for (int i = 0; i < 20; i++) {
      String name = ItemCore.grantEntityLootItem(state, i);
      assertTrue("Almond Water".equals(name) || "Băng Gạc Y Tế".equals(name));
      assertFalse(name.contains("Cơm Tấm"));
      assertFalse(name.contains("Bánh Mì"));
      assertFalse(name.contains("Sữa Đậu Nành"));
      assertFalse(name.contains("Lavie"));
    }
  }

  @Test public void firstAidKitHealsAuthoritativeProgressionHp() throws Exception {
    JSONObject state = new JSONObject()
        .put("player", new JSONObject().put("name", "Kai Akechi"))
        .put("party", new JSONArray());
    CharacterProgressionCore progression = new CharacterProgressionCore(bound -> 0);
    progression.normalizeState(state);
    progression.setCurrentHp(state, "kai", 10);
    ItemCore.grantChestLootItem(state, 2);

    String reply = new ItemCore().applyItemAction(state, ItemCore.FIRST_AID_KIT_ID, "use", "", 1);

    assertEquals(45, progression.profile(state, "kai").getInt("currentHp"));
    assertTrue(reply.contains("HP +35"));
    assertEquals(0, state.getJSONArray("inventory").length());
  }

  @Test public void banhMiAndLavieUpdateAuthoritativeSurvivalState() throws Exception {
    JSONObject foodState = new JSONObject()
        .put("player", new JSONObject().put("name", "Kai Akechi"))
        .put("party", new JSONArray())
        .put("gameTime", new JSONObject().put("elapsedSubjectiveMinutes", 48L * 60L));
    ItemCore.grantChestLootItem(foodState, 5);
    SurvivalCore survival = new SurvivalCore();
    assertEquals(33, survival.projectPhysiology(foodState, "kai").getInt("foodPercent"));

    String foodReply = new ItemCore().applyItemAction(
        foodState, ItemCore.BANH_MI_THIT_ID, "use", "", 1);

    assertEquals(78, survival.projectPhysiology(foodState, "kai").getInt("foodPercent"));
    assertTrue(foodReply.contains("Đói +45"));

    JSONObject waterState = new JSONObject()
        .put("player", new JSONObject().put("name", "Kai Akechi"))
        .put("party", new JSONArray())
        .put("gameTime", new JSONObject().put("elapsedSubjectiveMinutes", 24L * 60L));
    ItemCore.grantChestLootItem(waterState, 3);
    assertEquals(50, survival.projectPhysiology(waterState, "kai").getInt("waterPercent"));

    String waterReply = new ItemCore().applyItemAction(
        waterState, ItemCore.LAVIE_WATER_ID, "use", "", 1);

    assertEquals(100, survival.projectPhysiology(waterState, "kai").getInt("waterPercent"));
    assertTrue(waterReply.contains("Khát +50"));
  }

  @Test public void downedCompanionCannotBypassTenTurnReviveWithHealingItem() throws Exception {
    JSONObject state = new JSONObject()
        .put("player", new JSONObject().put("name", "Kai Akechi"))
        .put("party", new JSONArray().put(new JSONObject()
            .put("id", "lucia").put("name", "Lucia Lục").put("joined", true)));
    CharacterProgressionCore progression = new CharacterProgressionCore(bound -> 0);
    progression.normalizeState(state);
    progression.setCurrentHp(state, "lucia", 0);
    ItemCore.grantChestLootItem(state, 2);

    try {
      new ItemCore().applyItemAction(state, ItemCore.FIRST_AID_KIT_ID, "share", "lucia", 1);
      fail("Expected downed companion healing to be rejected");
    } catch (IllegalStateException expected) {
      assertTrue(expected.getMessage().contains("10 Explorer Turn"));
    }

    assertEquals(0, progression.profile(state, "lucia").getInt("currentHp"));
    assertEquals(1, state.getJSONArray("inventory").length());
  }
}

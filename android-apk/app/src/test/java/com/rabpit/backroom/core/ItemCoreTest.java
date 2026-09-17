package com.rabpit.backroom.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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

  @Test public void consumableEffectsMatchGameplayContract() {
    assertEquals(50, ItemCore.itemEffectValue(ItemCore.ALMOND_WATER_ID, "hunger"));
    assertEquals(100, ItemCore.itemEffectValue(ItemCore.ALMOND_WATER_ID, "thirst"));
    assertEquals(15, ItemCore.itemEffectValue(ItemCore.BANDAGE_ID, "hp"));
    assertEquals(0, ItemCore.itemEffectValue(ItemCore.BANDAGE_ID, "thirst"));
  }
}

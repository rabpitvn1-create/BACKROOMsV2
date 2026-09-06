package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class ItemCatalogPr4Test {
  private val expectedIds = listOf(
    "water-bottle",
    "food-container",
    "almond-water",
    BANDAGE_ID,
    ANTISEPTIC_ID,
    "fuel-container",
    "object:greek-fire",
    "object:liquid-pain",
    "electrical:charged-cell",
    "salvage:ceiling-wire"
  )

  @Test fun catalogContainsExactlyTheTenPr4Items() {
    assertEquals(expectedIds, ItemCatalog.all().map { it.id })
    assertEquals(10, ItemCatalog.all().size)
    assertEquals(10, ItemCatalog.all().count { it.rewardable })
  }

  @Test fun retiredItemsAreNotCatalogItems() {
    listOf(
      "ammo-cartridge",
      "salvage:tripse-alloy",
      "salvage:industrial",
      "salvage:electrical",
      "salvage:office",
      "salvage:boiler",
      "resource:dry-wallpaper-fiber",
      "chemical:dupont-bayer-solution",
      "item:brass-key",
      "resource:reliable-paper",
      "resource:fire-material",
      "madgod:set",
      "madgod:armor",
      "madgod:magnum",
      KAI_WHITE_WRAITH_ID,
      KAI_BLACKBLOOD_ARMOR_ID,
      KAI_OMNIVAULT_RING_ID
    ).forEach { id -> assertNull("retired Item must not resolve: $id", ItemCatalog.resolve(id)) }
  }

  @Test fun catalogCarriesThePr4GameplayStats() {
    fun metadata(id: String) = ItemCatalog.resolve(id)!!.metadata

    assertEquals("WATER", metadata("water-bottle")["physiologyEffect"])
    assertEquals("FOOD", metadata("food-container")["physiologyEffect"])
    assertEquals("WATER", metadata("almond-water")["physiologyEffect"])
    assertEquals("true", metadata("almond-water")["anomalous"])

    assertEquals("10", metadata(BANDAGE_ID)["healHp"])
    assertEquals("20", metadata(ANTISEPTIC_ID)["healHp"])
    assertEquals("loot", metadata(BANDAGE_ID)["dropRoll"])
    assertEquals("loot", metadata(ANTISEPTIC_ID)["dropRoll"])

    assertEquals("generator,fire,cooking,utility", metadata("fuel-container")["roles"])
    assertEquals("HIGH", metadata("object:greek-fire")["hazardLevel"])
    assertEquals("EXTREME", metadata("object:liquid-pain")["hazardLevel"])
    assertEquals("true", metadata("object:liquid-pain")["toxic"])
    assertEquals("true", metadata("object:liquid-pain")["corrosive"])
    assertEquals("device,power", metadata("electrical:charged-cell")["roles"])
    assertEquals("true", metadata("salvage:ceiling-wire")["craftMaterial"])
  }

  @Test fun normalizationPurgesAnythingOutsideTheTenItemWhitelist() {
    val inventory = InventoryState(
      KAI_ID,
      linkedMapOf(
        "water-bottle" to ItemStack("water-bottle", "Chai nước", 2),
        "ammo-cartridge" to ItemStack("ammo-cartridge", "Viên đạn", 12),
        "madgod:armor" to ItemStack("madgod:armor", "MadGod Armor"),
        KAI_BLACKBLOOD_ARMOR_ID to ItemStack(KAI_BLACKBLOOD_ARMOR_ID, "Blackblood Armor"),
        "salvage:tripse-alloy" to ItemStack("salvage:tripse-alloy", "Hợp kim Tripse")
      )
    )
    val state = GameState.initial().copy(inventories = mapOf(KAI_ID to inventory))

    val normalized = InventoryV4State.normalize(state)
    val items = normalized.inventories.getValue(KAI_ID).items

    assertEquals(setOf("water-bottle"), items.keys)
    assertEquals(2, items.getValue("water-bottle").quantity)
  }

  @Test fun healingDefinitionsUseTheEstablishedExactValues() {
    assertEquals(10, HealingItems.BANDAGE_HEAL_HP)
    assertEquals(20, HealingItems.ANTISEPTIC_HEAL_HP)
    assertEquals("loot", HealingItems.DROP_ROLL_KEY)
    assertEquals(10, HealingItems.healAmount(ItemStack("bandage", "Bandage")))
    assertEquals(20, HealingItems.healAmount(ItemStack("antiseptic", "Antiseptic")))
  }
}

package com.rabpit.backroom.core

import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InventoryAcquisitionPolicyTest {
  private fun before(flags: String = "{}") = JSONObject("""{"turn":1,"flags":$flags}""")
  private fun rolls(loot: Boolean = false, almond: Boolean = false) = JSONObject().apply {
    put("loot", JSONObject().put("success", loot))
    put("almondWater", JSONObject().put("success", almond))
  }

  @Test fun existingStackIncreaseKeepsLegacyAuthorization() {
    assertTrue(InventoryAcquisitionPolicy.allows(before(), rolls(), "đứng yên", "Bandage", alreadyOwned = true))
  }

  @Test fun newGenericRewardNeedsAcquisitionIntentAndLootOrEstablishedState() {
    assertFalse(InventoryAcquisitionPolicy.allows(before(), rolls(loot = true), "quan sát hộp", "Bandage", alreadyOwned = false))
    assertFalse(InventoryAcquisitionPolicy.allows(before(), rolls(), "nhận Bandage", "Bandage", alreadyOwned = false))
    assertTrue(InventoryAcquisitionPolicy.allows(before(), rolls(loot = true), "nhận Bandage", "Bandage", alreadyOwned = false))

    val established = before("""{"exploration":{"reward":"Bandage"}}""")
    assertTrue(InventoryAcquisitionPolicy.allows(established, rolls(), "nhận Bandage", "Bandage", alreadyOwned = false))
  }

  @Test fun almondWaterUsesItsDedicatedRollOrEstablishedState() {
    assertFalse(InventoryAcquisitionPolicy.allows(before(), rolls(), "lấy Almond Water", "Almond Water", alreadyOwned = false))
    assertTrue(InventoryAcquisitionPolicy.allows(before(), rolls(almond = true), "lấy Almond Water", "Almond Water", alreadyOwned = false))

    val established = before("""{"exploration":{"found":"Almond Water"}}""")
    assertTrue(InventoryAcquisitionPolicy.allows(established, rolls(), "lấy Almond Water", "Almond Water", alreadyOwned = false))
  }

  @Test fun copyCannotTurnLootRollIntoAnUnestablishedItem() {
    assertFalse(InventoryAcquisitionPolicy.allows(before(), rolls(loot = true), "copy Bandage", "Bandage", alreadyOwned = false))
    val established = before("""{"omnivault":{"observed":"Bandage"}}""")
    assertTrue(InventoryAcquisitionPolicy.allows(established, rolls(), "copy Bandage", "Bandage", alreadyOwned = false))
  }

  @Test fun madGodRequiresSpawnedStructuredStateEvenWhenLootSucceeds() {
    val notSpawned = before("""{"madGod":{"spawned":false,"reward":"MadGod Armor"}}""")
    assertFalse(InventoryAcquisitionPolicy.allows(notSpawned, rolls(loot = true), "nhận MadGod Armor", "MadGod Armor", alreadyOwned = false))

    val spawnedButUnestablished = before("""{"madGod":{"spawned":true}}""")
    assertFalse(InventoryAcquisitionPolicy.allows(spawnedButUnestablished, rolls(loot = true), "nhận MadGod Armor", "MadGod Armor", alreadyOwned = false))

    val authorized = before("""{"madGod":{"spawned":true,"reward":"MadGod Armor"}}""")
    assertTrue(InventoryAcquisitionPolicy.allows(authorized, rolls(), "nhận MadGod Armor", "MadGod Armor", alreadyOwned = false))
  }
}

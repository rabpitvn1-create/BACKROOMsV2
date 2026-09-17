from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
TESTS = ROOT / "android-apk/app/src/test/java/com/rabpit/backroom/core"


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    if new in text:
        return
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def replace_exact_count(path: Path, old: str, new: str, expected: int, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    if old not in text and new in text:
        return
    count = text.count(old)
    if count != expected:
        raise RuntimeError(f"{label}: expected {expected} anchors, found {count}")
    path.write_text(text.replace(old, new), encoding="utf-8")


def regex_once(path: Path, pattern: str, replacement: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    updated, count = re.subn(pattern, replacement, text, count=1, flags=re.S)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 semantic anchor, found {count}")
    path.write_text(updated, encoding="utf-8")


# The final ItemDropAuthority only accepts Android-authoritative Entity/ItemBox
# acquisition. Older unit-test fixtures used raw SYSTEM PICKUP commands, which
# now correctly fail before the behavior under test is reached. Keep production
# authority strict and make the fixtures model an ItemBox-originated grant.
physiology = TESTS / "PhysiologyItemEffectTest.kt"
replace_once(
    physiology,
    "        metadata = metadata\n",
    '''        metadata = metadata + mapOf(
          ItemDropAuthority.ORIGIN_KEY to ItemDropOrigin.ITEM_BOX.name,
          ItemDropAuthority.ORIGIN_ID_KEY to "test-item-box:$id"
        )
''',
    "Physiology authoritative pickup fixture",
)

turn = TESTS / "TurnCoordinatorTest.kt"
replace_once(
    turn,
    "class TurnCoordinatorTest {\n",
    '''class TurnCoordinatorTest {
  private fun dropMetadata(id: String): Map<String, String> = mapOf(
    ItemDropAuthority.ORIGIN_KEY to ItemDropOrigin.ITEM_BOX.name,
    ItemDropAuthority.ORIGIN_ID_KEY to "test-item-box:$id"
  )

''',
    "TurnCoordinator authoritative drop helper",
)
replace_exact_count(
    turn,
    'operation = ItemCommand.Operation.PICKUP, itemId = "water", itemName = "Water")',
    'operation = ItemCommand.Operation.PICKUP, itemId = "water", itemName = "Water", metadata = dropMetadata("water"))',
    2,
    "TurnCoordinator water grants",
)
replace_once(
    turn,
    'operation = ItemCommand.Operation.PICKUP, itemId = "rope", itemName = "Rope")',
    'operation = ItemCommand.Operation.PICKUP, itemId = "rope", itemName = "Rope", metadata = dropMetadata("rope"))',
    "TurnCoordinator rope grant",
)

# CharacterEquipmentSystem.seedFresh makes canonical equipped gear Inventory-owned.
# The UI/capacity layer hides equipped items and charges zero slots; ownership is
# intentionally retained. Omnivault, however, is retired and must not survive
# legacy migration.
codec = TESTS / "GameStateCodecTest.kt"
replace_once(
    codec,
    '''  @Test fun freshStateKeepsSignatureGearOnlyInEquipment() {
    val state = GameState.initial()
    assertTrue(state.inventories.getValue(KAI_ID).items.isEmpty())
    assertEquals(KAI_WHITE_WRAITH_ID, state.equipment.getValue(KAI_ID).slots["weapon"])
    assertEquals(KAI_BLACKBLOOD_ARMOR_ID, state.equipment.getValue(KAI_ID).slots["armor"])
  }
''',
    '''  @Test fun freshStateKeepsSignatureGearOwnedAndEquipped() {
    val state = GameState.initial()
    val inventory = state.inventories.getValue(KAI_ID).items
    assertTrue(inventory.containsKey(KAI_WHITE_WRAITH_ID))
    assertTrue(inventory.containsKey(KAI_BLACKBLOOD_ARMOR_ID))
    assertEquals(KAI_WHITE_WRAITH_ID, state.equipment.getValue(KAI_ID).slots["weapon"])
    assertEquals(KAI_BLACKBLOOD_ARMOR_ID, state.equipment.getValue(KAI_ID).slots["armor"])
  }
''',
    "Fresh canonical equipment ownership contract",
)

legacy_method = '''  @Test fun legacyWebViewSaveMigratesWithoutLosingNormalInventoryOrParty() {
    val legacy = """{
      "turn":184,
      "title":"BACKROOMS",
      "location":"Level 0",
      "inventory":[
        {"name":"White Wraith Magnum","quantity":1},
        {"name":"Blackblood Armor & linked modules","quantity":1},
        {"name":"Omnivault Ring / Nhẫn Vạn Tàng","quantity":1},
        {"name":"Almond Water","quantity":2,"state":"sealed"}
      ],
      "party":[{"id":"iris","name":"Iris","avatar":"iris.png"}]
    }"""
    val migrated = GameStateCodec.decode(legacy)
    assertEquals(CURRENT_SAVE_VERSION, migrated.saveVersion)
    assertEquals("TURN_184", migrated.turn.currentTurnId)
    assertEquals(GameTimeState(), migrated.time)
    assertEquals(0L, migrated.characters.getValue(KAI_ID).physiology.minutesSinceFood)
    assertEquals(0L, migrated.characters.getValue(KAI_ID).physiology.minutesSinceWater)
    assertEquals(0L, migrated.characters.getValue(KAI_ID).physiology.minutesAwake)
    assertEquals(PhysiologyState(), migrated.characters.getValue("iris").physiology)
    val migratedItems = migrated.inventories.getValue(KAI_ID).items.values
    assertTrue(migratedItems.any { it.quantity == 2 && it.name.contains("Almond Water", ignoreCase = true) })
    assertFalse(migratedItems.any {
      it.name.contains("Omnivault", ignoreCase = true) || it.name.contains("Vạn Tàng", ignoreCase = true)
    })
    assertEquals(KAI_WHITE_WRAITH_ID, migrated.equipment.getValue(KAI_ID).slots["weapon"])
    assertEquals(KAI_BLACKBLOOD_ARMOR_ID, migrated.equipment.getValue(KAI_ID).slots["armor"])
    assertEquals(listOf(KAI_ID, "iris"), migrated.party.memberIds)
    assertEquals("Level 0", migrated.world["location"])
  }
'''
regex_once(
    codec,
    r'''  @Test fun legacyWebViewSaveMigratesWithoutLosingNormalInventoryOrParty\(\) \{.*?\n  \}\n(?=\n  @Test fun v2CoreSaveMovesSignatureItemsOutOfInventory\(\))''',
    legacy_method,
    "Legacy migration inventory contract",
)

# patch-newgame-inventory-capacity.py is generated before Omnivault retirement.
# Kai has five canonical unique equipped items after the retired ring is removed.
new_game = TESTS / "InventoryCapacityNewGameTest.kt"
replace_once(
    new_game,
    "    assertEquals(6, kai.equipment.values.toSet().size)\n",
    "    assertEquals(5, kai.equipment.values.toSet().size)\n",
    "Kai post-Omnivault equipment count",
)
replace_once(
    new_game,
    "    assertTrue(kai.inventoryDetails.count { it.equipped } >= 6)\n",
    "    assertTrue(kai.inventoryDetails.count { it.equipped } >= 5)\n",
    "Kai post-Omnivault equipped detail count",
)

print("Runtime regression contracts aligned with final item authority and Omnivault retirement.")

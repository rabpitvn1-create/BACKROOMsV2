from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def write(path: Path, text: str) -> None:
    path.write_text(text, encoding="utf-8")


def sub(text: str, pattern: str, replacement: str, label: str, required: bool = False) -> str:
    updated, count = re.subn(pattern, replacement, text, flags=re.S | re.M)
    if required and count == 0:
        raise RuntimeError(f"{label}: anchor not found")
    return updated


def strip_lines(text: str, needles: tuple[str, ...]) -> str:
    return "".join(line for line in text.splitlines(True) if not any(n.lower() in line.lower() for n in needles))


# ---------------------------------------------------------------------------
# 1) Retire Omnivault as a gameplay system.
# ---------------------------------------------------------------------------
omnivault_engine = CORE / "OmnivaultEngine.kt"
if omnivault_engine.exists():
    omnivault_engine.unlink()

state_path = CORE / "GameState.kt"
state = read(state_path)
state = strip_lines(state, (
    "KAI_OMNIVAULT_RING_ID",
    "RING_NAME = \"Omnivault Ring\"",
    "omnivault ring",
    "nhẫn vạn tàng",
))
start = state.find("\ndata class ScanSlot(")
if start >= 0:
    end = state.find("\ndata class PendingTurn(", start)
    if end < 0:
        raise RuntimeError("PendingTurn anchor missing after retired Omnivault state")
    state = state[:start] + "\n" + state[end:]
state = sub(state, r"\n\s*val omnivault: OmnivaultState = OmnivaultState\(\),", "", "GameState omnivault field")
write(state_path, state)

command_path = CORE / "GameCommand.kt"
command = read(command_path)
command = command.replace("PICKUP, DROP, USE, TRANSFER, STORE, WITHDRAW, EQUIP, UNEQUIP", "PICKUP, DROP, USE, TRANSFER, EQUIP, UNEQUIP")
start = command.find("\ndata class OmnivaultCommand(")
if start >= 0:
    end = command.find("\ndata class PartyCommand(", start)
    if end < 0:
        raise RuntimeError("PartyCommand anchor missing after OmnivaultCommand")
    command = command[:start] + "\n" + command[end:]
command = command.replace("CHARACTER, INVENTORY, PARTY, STATUS, OMNIVAULT", "CHARACTER, INVENTORY, PARTY, STATUS")
write(command_path, command)

intent_path = CORE / "IntentPipeline.kt"
intent = read(intent_path)
intent = intent.replace("PICKUP_ITEM, DROP_ITEM, USE_ITEM, TRANSFER_ITEM, STORE_ITEM, WITHDRAW_ITEM,", "PICKUP_ITEM, DROP_ITEM, USE_ITEM, TRANSFER_ITEM,")
intent = sub(intent, r"\n\s*OMNIVAULT_STORE, OMNIVAULT_WITHDRAW, OMNIVAULT_SCAN, OMNIVAULT_COPY,\n\s*OMNIVAULT_RESTORE, OMNIVAULT_QUERY,", "", "Omnivault intents")
intent = "\n".join(line for line in intent.splitlines() if "GameIntent.OMNIVAULT_" not in line) + "\n"
intent = intent.replace(" +\n      context.state.omnivault.storedItems.values +\n      context.state.omnivault.scanSlots.map { it.templateItem }", "")
intent = sub(
    intent,
    r"\n\s*\?: context\.state\.omnivault\.storedItems\[id\]\n\s*\?: context\.state\.omnivault\.scanSlots\.firstOrNull \{ it\.templateItem\.itemId == id \}\?\.templateItem",
    "",
    "Omnivault item lookup",
)
intent = "\n".join(line for line in intent.splitlines() if "-> \"omnivault\"" not in line) + "\n"
intent = intent.replace("|omnivault", "")
write(intent_path, intent)

pipeline_path = CORE / "CommandPipeline.kt"
pipeline = read(pipeline_path)
pipeline = "\n".join(line for line in pipeline.splitlines() if "GameIntent.OMNIVAULT_" not in line and "QueryCommand.Type.OMNIVAULT" not in line) + "\n"
pipeline = sub(pipeline, r"\n\s*private fun vaultCommand\(.*?\n\s*OmnivaultCommand\(.*?\n", "\n", "Omnivault resolver helper")
write(pipeline_path, pipeline)

# ---------------------------------------------------------------------------
# 2) Central item acquisition authority. Only Entity and ItemBox may grant.
# ---------------------------------------------------------------------------
authority_path = CORE / "ItemDropAuthority.kt"
write(authority_path, r'''package com.rabpit.backroom.core

enum class ItemDropOrigin { ENTITY, ITEM_BOX }

object ItemDropAuthority {
  const val ORIGIN_KEY = "dropOrigin"
  const val ORIGIN_ID_KEY = "dropOriginId"

  fun validatePickup(command: ItemCommand): ValidationResult {
    if (command.operation != ItemCommand.Operation.PICKUP) return ValidationResult(true)
    if (command.source != CommandSource.SYSTEM) {
      return ValidationResult(false, "item_acquisition_requires_authoritative_drop")
    }
    val origin = command.metadata[ORIGIN_KEY]
      ?.trim()
      ?.uppercase()
      ?.let { runCatching { ItemDropOrigin.valueOf(it) }.getOrNull() }
      ?: return ValidationResult(false, "item_drop_origin_required")
    if (command.metadata[ORIGIN_ID_KEY].isNullOrBlank()) {
      return ValidationResult(false, "item_drop_origin_id_required")
    }
    if (origin !in setOf(ItemDropOrigin.ENTITY, ItemDropOrigin.ITEM_BOX)) {
      return ValidationResult(false, "item_drop_origin_invalid")
    }
    return ValidationResult(true)
  }

  fun entityDrop(
    commandId: String,
    actorId: String,
    entityKey: String,
    item: ItemStack,
    quantity: Int = item.quantity
  ): ItemCommand = dropCommand(commandId, actorId, ItemDropOrigin.ENTITY, entityKey, item, quantity)

  fun itemBoxDrop(
    commandId: String,
    actorId: String,
    itemBoxId: String,
    item: ItemStack,
    quantity: Int = item.quantity
  ): ItemCommand = dropCommand(commandId, actorId, ItemDropOrigin.ITEM_BOX, itemBoxId, item, quantity)

  private fun dropCommand(
    commandId: String,
    actorId: String,
    origin: ItemDropOrigin,
    originId: String,
    item: ItemStack,
    quantity: Int
  ): ItemCommand = ItemCommand(
    commandId = commandId,
    turnId = null,
    actorId = actorId,
    source = CommandSource.SYSTEM,
    operation = ItemCommand.Operation.PICKUP,
    itemId = item.itemId,
    itemName = item.name,
    quantity = quantity.coerceAtLeast(1),
    metadata = item.metadata + mapOf(
      ORIGIN_KEY to origin.name,
      ORIGIN_ID_KEY to originId
    )
  )
}

/**
 * Drop data intentionally starts empty. Entity and ItemBox are the only legal
 * acquisition channels, but their concrete tables are supplied separately.
 */
object ItemDropCatalog {
  fun forEntity(entityKey: String): List<ItemStack> = emptyList()
  fun forItemBox(itemBoxId: String): List<ItemStack> = emptyList()
}

object ItemDropResolver {
  fun grantEntityDrops(state: GameState, entityKey: String, actorId: String = KAI_ID): GameState =
    grant(state, ItemDropCatalog.forEntity(entityKey)) { index, item ->
      ItemDropAuthority.entityDrop("DROP:ENTITY:$entityKey:$index", actorId, entityKey, item)
    }

  fun grantItemBoxDrops(state: GameState, itemBoxId: String, actorId: String = KAI_ID): GameState =
    grant(state, ItemDropCatalog.forItemBox(itemBoxId)) { index, item ->
      ItemDropAuthority.itemBoxDrop("DROP:ITEM_BOX:$itemBoxId:$index", actorId, itemBoxId, item)
    }

  private fun grant(
    state: GameState,
    items: List<ItemStack>,
    command: (Int, ItemStack) -> ItemCommand
  ): GameState {
    var current = state
    items.forEachIndexed { index, item ->
      val result = StateReducer.execute(current, command(index, item))
      if (result.applied) current = result.state
    }
    return current
  }
}
''')

reducer_path = CORE / "StateReducer.kt"
reducer = read(reducer_path)
reducer = sub(
    reducer,
    r"\n\s*// Player-facing pickup commands.*?return ValidationResult\(false, \"player_pickup_unavailable\"\)\n\s*\}\n",
    '''
    if (command is ItemCommand && command.operation == ItemCommand.Operation.PICKUP) {
      val acquisition = ItemDropAuthority.validatePickup(command)
      if (!acquisition.valid) return acquisition
    }
''',
    "Pickup authority",
)
reducer = sub(reducer, r"\n\s*// Restore remains.*?\n\s*\}\n", "\n", "Omnivault restore validation")
reducer = reducer.replace("      is OmnivaultCommand -> command.itemName\n", "")
reducer = reducer.replace("      is OmnivaultCommand -> OmnivaultEngine.execute(state, command)\n", "")
reducer = reducer.replace("      is OmnivaultCommand -> command.itemId\n", "")
write(reducer_path, reducer)

engines_path = CORE / "Engines.kt"
engines = read(engines_path)
engines = sub(engines, r"\n\s*ItemCommand\.Operation\.STORE,\s*ItemCommand\.Operation\.WITHDRAW -> invalid\(state, \"use_omnivault_command\"\)", "", "Inventory Omnivault bridge")
write(engines_path, engines)

# Entity defeat is wired to the only future Entity drop table. Today it is empty.
combat_path = CORE / "CombatRuntime.kt"
combat = read(combat_path)
old_entity_death = '''      val persisted = encode(state, c.copy(phase = Phase.RESOLVED, entityCondition = EntityCondition.DESTROYED))
      val cleared = clearCombatOnly(persisted)
      return Resolution(cleared, true, log.joinToString(" ") + " ${c.entityName} đã bị tiêu diệt.", entityDestroyed = true)'''
new_entity_death = '''      val persisted = encode(state, c.copy(phase = Phase.RESOLVED, entityCondition = EntityCondition.DESTROYED))
      val cleared = clearCombatOnly(persisted)
      val withDrops = ItemDropResolver.grantEntityDrops(cleared, c.entityKey)
      return Resolution(withDrops, true, log.joinToString(" ") + " ${c.entityName} đã bị tiêu diệt.", entityDestroyed = true)'''
if old_entity_death in combat:
    combat = combat.replace(old_entity_death, new_entity_death, 1)
write(combat_path, combat)

# ---------------------------------------------------------------------------
# 3) Saves ignore retired Omnivault payloads; unknown old JSON remains harmless.
# ---------------------------------------------------------------------------
codec_path = CORE / "GameStateCodec.kt"
codec = read(codec_path)
codec = strip_lines(codec, ('put("omnivault", omnivault(', 'omnivault = decodeOmnivault('))
codec = sub(codec, r"\n\s*private fun omnivault\(value: OmnivaultState\).*?\n\s*private fun turn\(value: TurnState\)", "\n\n  private fun turn(value: TurnState)", "Omnivault codec")
write(codec_path, codec)

# ---------------------------------------------------------------------------
# 4) Gemini/story state can remove/use items, but can never manufacture them.
# ---------------------------------------------------------------------------
facade_path = CORE / "GameCoreFacade.kt"
facade = read(facade_path)
facade = sub(
    facade,
    r"\n\s*// Restore is lore/narrative-only.*?return response\(false, legacy, null, \"fallback_required\"\)\n\s*\}\n",
    "\n",
    "Omnivault rule fallback",
)
facade = facade.replace(" || GameIntent.OMNIVAULT_RESTORE in actionIntents", "")
facade = sub(
    facade,
    r"\s*if \(desired == old\) return@forEachIndexed\n\s*val stack = desiredById\[id\] \?: current\.getValue\(id\)\n\s*commands \+= ItemCommand\(\n\s*\"\$turnId:GEMINI:INV:\$index\", turnId, KAI_ID, source = CommandSource\.GEMINI,\n\s*operation = if \(desired > old\) ItemCommand\.Operation\.PICKUP else ItemCommand\.Operation\.DROP,\n\s*itemId = id, itemName = stack\.name, quantity = kotlin\.math\.abs\(desired - old\), metadata = stack\.metadata\n\s*\)",
    '''
      if (desired == old || desired > old) return@forEachIndexed
      val stack = current.getValue(id)
      commands += ItemCommand(
        "$turnId:GEMINI:INV:$index", turnId, KAI_ID, source = CommandSource.GEMINI,
        operation = ItemCommand.Operation.DROP,
        itemId = id, itemName = stack.name, quantity = old - desired, metadata = stack.metadata
      )''',
    "Gemini inventory increase suppression",
)
facade = facade.replace(" + state.omnivault.storedItems.values", "")
facade = sub(facade, r"\n\s*val omnivaultWithdrawal = Regex\(.*?\n\s*if \(omnivaultWithdrawal\) return false", "", "Omnivault pickup exception")
facade = "\n".join(line for line in facade.splitlines() if '"omnivault_' not in line.lower()) + "\n"
write(facade_path, facade)

# ---------------------------------------------------------------------------
# 5) Generated equipment/runtime cleanup after legacy patch stack runs.
# ---------------------------------------------------------------------------
system_path = CORE / "CharacterEquipmentSystem.kt"
if system_path.exists():
    system = read(system_path)
    system = sub(
        system,
        r"\n\s*EquipmentDefinition\(\n\s*id = KAI_OMNIVAULT_RING_ID,.*?(?=\n\s*EquipmentDefinition\(\n\s*id = IRIS_RECON_FRAME_ID)",
        "\n",
        "Generated Omnivault equipment definition",
    )
    system = strip_lines(system, ("KAI_OMNIVAULT_RING_ID", "Omnivault Integration", "Omnivault"))
    write(system_path, system)

# Make the upstream generator compatible with a source tree that no longer has the ring.
generator_path = ROOT / "patch-character-status-equipment-system.py"
if generator_path.exists():
    generator = read(generator_path)
    generator = sub(
        generator,
        r"\n\s*EquipmentDefinition\(\n\s*id = KAI_OMNIVAULT_RING_ID,.*?(?=\n\s*EquipmentDefinition\(\n\s*id = IRIS_RECON_FRAME_ID)",
        "\n",
        "Generator Omnivault equipment definition",
    )
    generator = sub(generator, r"\n\s*@Test fun omnivaultMayHaveZeroCombatStatsWithAbilities\(\) \{.*?\n\s*\}", "", "Generator Omnivault test")
    generator = strip_lines(generator, ("KAI_OMNIVAULT_RING_ID", "Omnivault Integration", "Omnivault"))
    write(generator_path, generator)

# ---------------------------------------------------------------------------
# 6) Disable generic narrative loot. Healing items remain definitions only.
# ---------------------------------------------------------------------------
healing_path = CORE / "HealingItems.kt"
if healing_path.exists():
    healing = read(healing_path)
    healing = strip_lines(healing, ('DROP_ROLL_KEY = "loot"', '"dropRoll" to DROP_ROLL_KEY'))
    write(healing_path, healing)

lucia_path = CORE / "LuciaCanon.kt"
if lucia_path.exists():
    lucia = read(lucia_path)
    lucia = strip_lines(lucia, ("BATTLEFIELD_RECON_LOOT_BONUS_PERCENT", '"lootChanceBonusPercent"'))
    write(lucia_path, lucia)

if MAIN.exists():
    main = read(MAIN)
    main = sub(
        main,
        r"\n\s*private JSONArray sanitizedInventory\(JSONArray current, JSONArray proposed, JSONObject rolls\) throws Exception \{.*?\n\s*\}\n\n\s*private JSONArray sanitizedParty",
        r'''
  private JSONArray sanitizedInventory(JSONArray current, JSONArray proposed, JSONObject rolls) throws Exception {
    JSONArray safe = new JSONArray();
    if (current == null) return safe;
    for (int i = 0; i < current.length(); i++) safe.put(current.opt(i));
    return safe;
  }

  private JSONArray sanitizedParty''',
        "Narrative inventory sanitizer",
    )
    main = sub(main, r"\n\s*rolls\.put\(\"loot\".*?;", "", "Generic loot roll")
    main = sub(main, r"\n\s*rolls\.put\(\"almondWater\".*?;", "", "Almond-water item roll")
    main = sub(main, r"\n\s*int luciaScoutBonus = .*?\n\s*rolls\.put\(\"loot\".*?;", "", "Lucia generic loot bonus")
    main = main.replace("encounter/item/reunion/level transition", "encounter/reunion/level transition")
    main = main.replace("Inventory chỉ được thêm vật đã tồn tại trong state/cảnh và thực sự được Kai nhặt/lấy/nhận/cất, hoặc kết quả loot hợp lệ. Nhìn thấy không đồng nghĩa sở hữu.", "Inventory không được tăng từ lời kể/model output. Item mới chỉ được cấp bởi Android từ Entity drop hoặc ItemBox drop; nhìn thấy hay tìm thấy không đồng nghĩa sở hữu.")
    main = sub(main, r"\n\s*String luciaScoutDirective = .*?;", "", "Lucia loot prompt")
    main = main.replace(' + luciaScoutDirective + "\\n"', '')
    main = main.replace(' + healingItemDirective + "\\n"', ' + healingItemDirective + "\\n"')
    write(MAIN, main)

# Update source canon so the GM no longer treats generic search loot as acquisition.
drive_path = ROOT / "drive-canon.txt"
if drive_path.exists():
    drive = read(drive_path)
    drive = drive.replace("encounter, loot, exit hoặc snapshot sự kiện", "encounter, exit hoặc snapshot sự kiện")
    drive = drive.replace("- Hazard / Entity / Loot / Almond Water theo profile Level. Level 0/4/6 chỉ cho Entity dạng roaming/incursion theo roll. Chuyển Level chỉ khi exitProbe success hoặc state đã khóa transitionReady/exitReady.", "- Hazard / Entity theo profile Level. Level 0/4/6 chỉ cho Entity dạng roaming/incursion theo roll. Chuyển Level chỉ khi exitProbe success hoặc state đã khóa transitionReady/exitReady.\n- ITEM DROP HARD LOCK: Item mới chỉ có thể vào Inventory từ Entity drop hoặc ItemBox drop do Android xác nhận. Search/Explore, AI narration, generic loot roll và lời kể không được tự tạo Item.")
    write(drive_path, drive)

codex_path = ROOT / "kai-codex.txt"
if codex_path.exists():
    codex = read(codex_path)
    codex = codex.replace("; nối Omnivault Ring", "")
    codex = sub(codex, r"\n11\. OMNIVAULT RING / NHẪN VẠN TÀNG\n.*?(?=\n12\.)", "\n", "Kai Omnivault codex section")
    write(codex_path, codex)

# The old resource-policy patch must not recreate the retired system.
resource_patch = ROOT / "patch-kai-resource-policy-final.py"
if resource_patch.exists():
    write(resource_patch, '''from pathlib import Path\n\nROOT = Path(__file__).resolve().parent\nMAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"\ntext = MAIN.read_text(encoding="utf-8")\nold = "Inventory chỉ được tăng từ acquisition/drop do game xác nhận"\nnew = "Inventory chỉ được tăng bởi Android từ Entity drop hoặc ItemBox drop; AI/player text không có quyền tạo Item"\nif old in text:\n    text = text.replace(old, new)\nMAIN.write_text(text, encoding="utf-8")\nprint("Kai item authority aligned to Entity/ItemBox-only drops.")\n''')

# ---------------------------------------------------------------------------
# 7) Regression tests and hard gates.
# ---------------------------------------------------------------------------
test_path = TESTS / "ItemDropAuthorityTest.kt"
write(test_path, r'''package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class ItemDropAuthorityTest {
  private fun pickup(source: CommandSource, metadata: Map<String, String>) = ItemCommand(
    commandId = "PICKUP:${source.name}:${metadata.hashCode()}",
    turnId = null,
    actorId = KAI_ID,
    source = source,
    operation = ItemCommand.Operation.PICKUP,
    itemId = "test:item",
    itemName = "Test Item",
    quantity = 1,
    metadata = metadata
  )

  @Test fun geminiCannotManufactureItemEvenWithDropMetadata() {
    val command = pickup(CommandSource.GEMINI, mapOf(
      ItemDropAuthority.ORIGIN_KEY to ItemDropOrigin.ENTITY.name,
      ItemDropAuthority.ORIGIN_ID_KEY to "hound"
    ))
    val result = StateReducer.execute(GameState.initial(), command)
    assertFalse(result.applied)
    assertEquals("item_acquisition_requires_authoritative_drop", result.validation.reason)
  }

  @Test fun systemPickupWithoutApprovedOriginIsRejected() {
    val result = StateReducer.execute(GameState.initial(), pickup(CommandSource.SYSTEM, emptyMap()))
    assertFalse(result.applied)
    assertEquals("item_drop_origin_required", result.validation.reason)
  }

  @Test fun entityDropIsAccepted() {
    val item = ItemStack("entity:test", "Entity Test")
    val result = StateReducer.execute(GameState.initial(), ItemDropAuthority.entityDrop("E1", KAI_ID, "hound", item))
    assertTrue(result.validation.reason ?: "entity drop rejected", result.applied)
    assertTrue(result.state.inventories.getValue(KAI_ID).items.containsKey("entity:test"))
  }

  @Test fun itemBoxDropIsAccepted() {
    val item = ItemStack("box:test", "Box Test")
    val result = StateReducer.execute(GameState.initial(), ItemDropAuthority.itemBoxDrop("B1", KAI_ID, "itembox:placeholder", item))
    assertTrue(result.validation.reason ?: "item box drop rejected", result.applied)
    assertTrue(result.state.inventories.getValue(KAI_ID).items.containsKey("box:test"))
  }

  @Test fun dropCatalogStartsEmptyUntilDataIsAdded() {
    assertTrue(ItemDropCatalog.forEntity("hound").isEmpty())
    assertTrue(ItemDropCatalog.forItemBox("itembox:placeholder").isEmpty())
  }
}
''')

# Remove generated Omnivault test fragments if a previous generator created them.
for path in TESTS.glob("*.kt"):
    text = read(path)
    cleaned = sub(text, r"\n\s*@Test fun omnivault.*?\n\s*\}", "", f"{path.name} Omnivault test")
    if cleaned != text:
        write(path, cleaned)

# Generated healing tests may still carry the pre-update generic-loot assertion.
healing_test = TESTS / "HealingItemTest.kt"
if healing_test.exists():
    healing_text = read(healing_test)
    healing_text = sub(
        healing_text,
        r"\n\s*@Test fun healingItemsShareTheOrdinaryLootGate\(\) \{.*?\n\s*\}",
        "",
        "Healing generic-loot regression",
    )
    write(healing_test, healing_text)

# Rewrite the generated healing prompt so it describes effects only;
# acquisition remains exclusively controlled by Entity/ItemBox tables.
if MAIN.exists():
    main = read(MAIN)
    main = sub(
        main,
        r'\n\s*String healingItemDirective = "HEALING ITEM HARD LOCK:.*?;\n',
        '\n    String healingItemDirective = "HEALING ITEM DATA: Băng gạc hồi đúng 10 HP; Thuốc sát trùng hồi đúng 20 HP. Hai Item này không có generic loot roll. Việc cấp Item chỉ do Android Entity drop hoặc ItemBox drop quyết định.";\n',
        "Healing prompt authority",
    )
    write(MAIN, main)

# Core/runtime must not retain an executable Omnivault system.
for path in list(CORE.glob("*.kt")):
    lower = read(path).lower()
    if "omnivaultcommand" in lower or "omnivaultengine" in lower or "kai_omnivault_ring_id" in lower:
        raise RuntimeError(f"Retired Omnivault runtime survived in {path.name}")

if omnivault_engine.exists():
    raise RuntimeError("OmnivaultEngine.kt survived retirement")

print("Massive item update applied: Omnivault retired; Item acquisition locked to Entity/ItemBox drops.")

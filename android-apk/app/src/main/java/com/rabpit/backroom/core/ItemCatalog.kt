package com.rabpit.backroom.core

import java.text.Normalizer
import java.util.Locale

data class ItemDefinition(
  val id: String,
  val displayName: String,
  val aliases: Set<String> = emptySet(),
  val usable: Boolean = false,
  val transferable: Boolean = true,
  val discardable: Boolean = true,
  val rewardable: Boolean = true,
  val metadata: Map<String, String> = emptyMap()
)

/**
 * Authoritative whitelist for gameplay Items.
 *
 * Narrative nouns never become Items by themselves. A story/system reward must resolve here
 * before it can enter authoritative Inventory state.
 */
object ItemCatalog {
  private val definitions = listOf(
    ItemDefinition(
      id = "water-bottle",
      displayName = "Chai nước",
      aliases = setOf("chai nước", "water bottle", "nước đóng chai"),
      usable = true,
      metadata = mapOf("consumable" to "true", "consumedOnUse" to "true", "physiologyEffect" to "WATER")
    ),
    ItemDefinition(
      id = "food-container",
      displayName = "Hộp đồ hộp",
      aliases = setOf("hộp đồ hộp", "đồ hộp", "hộp thức ăn", "hộp đồ ăn", "canned food"),
      usable = true,
      metadata = mapOf("consumable" to "true", "consumedOnUse" to "true", "physiologyEffect" to "FOOD")
    ),
    ItemDefinition(
      id = "almond-water",
      displayName = "Nước Hạnh Nhân",
      aliases = setOf("nước hạnh nhân", "almond water"),
      usable = true,
      metadata = mapOf("consumable" to "true", "consumedOnUse" to "true", "physiologyEffect" to "WATER")
    ),
    ItemDefinition(
      id = "medical:bandage",
      displayName = "Băng gạc",
      aliases = setOf("băng gạc", "băng", "bandage"),
      usable = true,
      metadata = mapOf("consumable" to "true", "consumedOnUse" to "true", "itemCategory" to "medical")
    ),
    ItemDefinition(
      id = "medical:antiseptic",
      displayName = "Thuốc sát trùng",
      aliases = setOf("thuốc sát trùng", "thuốc sát khuẩn", "antiseptic"),
      usable = true,
      metadata = mapOf("consumable" to "true", "consumedOnUse" to "true", "itemCategory" to "medical")
    ),
    ItemDefinition(
      id = "ammo-cartridge",
      displayName = "Viên đạn",
      aliases = setOf("viên đạn", "đạn", "ammo", "cartridge"),
      usable = false
    ),
    ItemDefinition(
      id = "fuel-container",
      displayName = "Bình nhiên liệu",
      aliases = setOf("bình nhiên liệu", "can nhiên liệu", "fuel container"),
      usable = false
    ),
    ItemDefinition(
      id = "object:greek-fire",
      displayName = "Greek Fire",
      aliases = setOf("greek fire"),
      usable = false
    ),
    ItemDefinition(
      id = "object:liquid-pain",
      displayName = "Liquid Pain",
      aliases = setOf("liquid pain"),
      usable = false
    ),
    ItemDefinition(
      id = "electrical:charged-cell",
      displayName = "Pin tích điện",
      aliases = setOf("pin tích điện", "charged cell", "battery pack", "pin"),
      usable = false
    ),
    ItemDefinition(
      id = "salvage:ceiling-wire",
      displayName = "Dây điện",
      aliases = setOf("dây điện", "ceiling wire"),
      usable = false
    ),
    ItemDefinition(
      id = "salvage:tripse-alloy",
      displayName = "Hợp kim Tripse",
      aliases = setOf("hợp kim tripse", "tripse alloy"),
      usable = false
    ),
    ItemDefinition(
      id = "salvage:industrial",
      displayName = "Phế liệu công nghiệp",
      aliases = setOf("phế liệu công nghiệp", "industrial salvage"),
      usable = false
    ),
    ItemDefinition(
      id = "salvage:electrical",
      displayName = "Phế liệu điện",
      aliases = setOf("phế liệu điện", "electrical salvage"),
      usable = false
    ),
    ItemDefinition(
      id = "salvage:office",
      displayName = "Phế liệu văn phòng",
      aliases = setOf("phế liệu văn phòng", "office salvage"),
      usable = false
    ),
    ItemDefinition(
      id = "salvage:boiler",
      displayName = "Phế liệu lò hơi",
      aliases = setOf("phế liệu lò hơi", "boiler salvage"),
      usable = false
    ),
    ItemDefinition(
      id = "resource:dry-wallpaper-fiber",
      displayName = "Sợi giấy dán tường khô",
      aliases = setOf("sợi giấy dán tường khô", "dry wallpaper fiber"),
      usable = false
    ),
    ItemDefinition(
      id = "chemical:dupont-bayer-solution",
      displayName = "Dung dịch DuPont–Bayer",
      aliases = setOf("dung dịch dupont bayer", "dupont bayer solution"),
      usable = false
    ),
    ItemDefinition(
      id = "item:brass-key",
      displayName = "Chìa khóa đồng",
      aliases = setOf("chìa khóa đồng", "brass key"),
      usable = false
    ),
    ItemDefinition(
      id = "resource:reliable-paper",
      displayName = "Giấy sạch",
      aliases = setOf("giấy sạch", "reliable paper"),
      usable = false
    ),
    ItemDefinition(
      id = "resource:fire-material",
      displayName = "Vật liệu nhóm lửa",
      aliases = setOf("vật liệu nhóm lửa", "fire material", "gỗ khô"),
      usable = false
    ),
    ItemDefinition(
      id = "madgod:set",
      displayName = "MadGod Set",
      aliases = setOf("madgod set", "madgod"),
      usable = false,
      transferable = false,
      discardable = false
    ),
    ItemDefinition(
      id = KAI_WHITE_WRAITH_ID,
      displayName = KaiStartingEquipment.WEAPON_NAME,
      aliases = setOf("white wraith magnum", "w.w magnum", "white wraith"),
      usable = false,
      transferable = false,
      discardable = false,
      rewardable = false
    ),
    ItemDefinition(
      id = KAI_BLACKBLOOD_ARMOR_ID,
      displayName = KaiStartingEquipment.ARMOR_NAME,
      aliases = setOf("blackblood armor", "blackblood armor & linked modules"),
      usable = false,
      transferable = false,
      discardable = false,
      rewardable = false
    ),
    ItemDefinition(
      id = KAI_OMNIVAULT_RING_ID,
      displayName = KaiStartingEquipment.RING_NAME,
      aliases = setOf("omnivault ring", "nhẫn omnivault", "nhẫn vạn tàng"),
      usable = false,
      transferable = false,
      discardable = false,
      rewardable = false
    )
  )

  private val byId = definitions.associateBy { it.id }
  private val byAlias = buildMap {
    definitions.forEach { definition ->
      (definition.aliases + definition.displayName).forEach { alias ->
        put(normalize(alias), definition)
      }
    }
  }

  fun all(): List<ItemDefinition> = definitions

  fun resolve(itemId: String?, name: String? = null): ItemDefinition? {
    val id = itemId?.trim().orEmpty()
    byId[id]?.let { return it }
    val normalizedName = normalize(name.orEmpty())
    if (normalizedName.isNotEmpty()) byAlias[normalizedName]?.let { return it }
    return null
  }

  fun findMention(text: String): ItemDefinition? {
    val haystack = " ${normalize(text)} "
    return definitions.asSequence()
      .flatMap { definition -> (definition.aliases + definition.displayName).asSequence().map { alias -> definition to normalize(alias) } }
      .filter { (_, alias) -> alias.isNotEmpty() && haystack.contains(" $alias ") }
      .maxByOrNull { (_, alias) -> alias.length }
      ?.first
  }

  fun resolveLegacy(item: ItemStack): ItemDefinition? {
    if (isLegacyResidual(item)) return null
    resolve(item.itemId, item.name)?.let { return it }
    val baseId = item.itemId.replace(Regex(":(?:full|low)$", RegexOption.IGNORE_CASE), "")
    byId[baseId]?.let { return it }
    val legacyName = normalize(item.name)
    return when {
      legacyName.contains("chai nuoc con it") -> byId["water-bottle"]
      legacyName.contains("hop thuc an con it") || legacyName.contains("hop do an con it") -> byId["food-container"]
      legacyName.contains("binh nhien lieu con it") -> byId["fuel-container"]
      else -> null
    }
  }

  fun isLegacyResidual(item: ItemStack): Boolean {
    if (item.contentState == ContentState.EMPTY) return true
    if (item.itemId.endsWith(":empty", true)) return true
    val n = normalize(item.name)
    return n == "vo dan" || n.contains("chai rong") || n.contains("hop rong") ||
      n.contains("hop thuc an rong") || n.contains("binh rong") || n.startsWith("vo chai") || n.startsWith("vo hop")
  }

  fun canonicalize(definition: ItemDefinition, item: ItemStack): ItemStack {
    val cleanMetadata = item.metadata - setOf(
      "remainingContent", "contentAmount", "contentPercent", "contentState", "containerPersistent",
      "omnivaultCopyCount", "scanSlot", "markedSource"
    )
    return item.copy(
      itemId = definition.id,
      name = definition.displayName,
      condition = null,
      archetypeId = definition.id,
      contentState = ContentState.NONE,
      metadata = cleanMetadata + definition.metadata
    )
  }

  @JvmStatic fun promptCatalog(): String = definitions
    .filter { it.rewardable }
    .joinToString(", ") { it.displayName }

  private fun normalize(value: String): String {
    val decomposed = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
      .replace(Regex("\\p{Mn}+"), "")
    return decomposed.replace(Regex("[^\\p{L}\\p{N}]+"), " ").replace(Regex("\\s+"), " ").trim()
  }
}

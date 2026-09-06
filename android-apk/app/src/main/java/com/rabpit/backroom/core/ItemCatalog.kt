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
 *
 * PR #04 intentionally keeps this catalog small. Equipment is a separate gameplay concern and
 * retired/legacy equipment IDs are not Inventory Items.
 */
object ItemCatalog {
  private val definitions = listOf(
    ItemDefinition(
      id = "water-bottle",
      displayName = "Chai nước",
      aliases = setOf("chai nước", "water bottle", "nước đóng chai"),
      usable = true,
      metadata = mapOf(
        "itemCategory" to "drink",
        "directUse" to "true",
        "consumable" to "true",
        "consumedOnUse" to "true",
        "physiologyEffect" to "WATER",
        "healHp" to "0",
        "tags" to "water,hydration,survival"
      )
    ),
    ItemDefinition(
      id = "food-container",
      displayName = "Hộp đồ hộp",
      aliases = setOf("hộp đồ hộp", "đồ hộp", "hộp thức ăn", "hộp đồ ăn", "canned food"),
      usable = true,
      metadata = mapOf(
        "itemCategory" to "food",
        "directUse" to "true",
        "consumable" to "true",
        "consumedOnUse" to "true",
        "physiologyEffect" to "FOOD",
        "healHp" to "0",
        "tags" to "food,ration,hunger,survival"
      )
    ),
    ItemDefinition(
      id = "almond-water",
      displayName = "Nước Hạnh Nhân",
      aliases = setOf("nước hạnh nhân", "almond water"),
      usable = true,
      metadata = mapOf(
        "itemCategory" to "drink",
        "directUse" to "true",
        "consumable" to "true",
        "consumedOnUse" to "true",
        "physiologyEffect" to "WATER",
        "healHp" to "0",
        "anomalous" to "true",
        "tags" to "almond-water,hydration,anomalous,survival,alertness-support"
      )
    ),
    ItemDefinition(
      id = BANDAGE_ID,
      displayName = HealingItems.BANDAGE_NAME,
      aliases = setOf("băng gạc", "băng", "bandage"),
      usable = true,
      metadata = mapOf(
        "itemCategory" to "medical",
        "directUse" to "true",
        "consumable" to "true",
        "consumedOnUse" to "true",
        "healHp" to HealingItems.BANDAGE_HEAL_HP.toString(),
        "dropRoll" to HealingItems.DROP_ROLL_KEY,
        "tags" to "medical,bandage,wound,healing"
      )
    ),
    ItemDefinition(
      id = ANTISEPTIC_ID,
      displayName = HealingItems.ANTISEPTIC_NAME,
      aliases = setOf("thuốc sát trùng", "thuốc sát khuẩn", "antiseptic"),
      usable = true,
      metadata = mapOf(
        "itemCategory" to "medical",
        "directUse" to "true",
        "consumable" to "true",
        "consumedOnUse" to "true",
        "healHp" to HealingItems.ANTISEPTIC_HEAL_HP.toString(),
        "dropRoll" to HealingItems.DROP_ROLL_KEY,
        "tags" to "medical,antiseptic,infection-control,healing"
      )
    ),
    ItemDefinition(
      id = "fuel-container",
      displayName = "Bình nhiên liệu",
      aliases = setOf("bình nhiên liệu", "can nhiên liệu", "fuel container"),
      metadata = mapOf(
        "itemCategory" to "fuel",
        "directUse" to "false",
        "roles" to "generator,fire,cooking,utility",
        "tags" to "fuel,generator,fire,cooking,utility"
      )
    ),
    ItemDefinition(
      id = "object:greek-fire",
      displayName = "Greek Fire",
      aliases = setOf("greek fire"),
      metadata = mapOf(
        "itemCategory" to "anomalous_incendiary",
        "directUse" to "false",
        "anomalous" to "true",
        "hazardLevel" to "HIGH",
        "roles" to "fire,heat,cooking,combat",
        "tags" to "greek-fire,incendiary,fuel,heat,weapon,anomalous"
      )
    ),
    ItemDefinition(
      id = "object:liquid-pain",
      displayName = "Liquid Pain",
      aliases = setOf("liquid pain"),
      metadata = mapOf(
        "itemCategory" to "hazardous_chemical",
        "directUse" to "false",
        "hazardLevel" to "EXTREME",
        "toxic" to "true",
        "corrosive" to "true",
        "roles" to "trap,anti-entity",
        "tags" to "liquid-pain,corrosive,toxic,chemical,trap,anti-entity"
      )
    ),
    ItemDefinition(
      id = "electrical:charged-cell",
      displayName = "Pin tích điện",
      aliases = setOf("pin tích điện", "charged cell", "battery pack", "pin"),
      metadata = mapOf(
        "itemCategory" to "power_resource",
        "directUse" to "false",
        "roles" to "device,power",
        "tags" to "battery,charged-cell,electricity,power,device"
      )
    ),
    ItemDefinition(
      id = "salvage:ceiling-wire",
      displayName = "Dây điện",
      aliases = setOf("dây điện", "ceiling wire"),
      metadata = mapOf(
        "itemCategory" to "salvage",
        "directUse" to "false",
        "craftMaterial" to "true",
        "roles" to "electrical-repair,power-connection,binding,simple-trap",
        "tags" to "wire,electrical,salvage,repair,crafting,trap"
      )
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
    return n.contains("chai rong") || n.contains("hop rong") || n.contains("hop thuc an rong") ||
      n.contains("binh rong") || n.startsWith("vo chai") || n.startsWith("vo hop")
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

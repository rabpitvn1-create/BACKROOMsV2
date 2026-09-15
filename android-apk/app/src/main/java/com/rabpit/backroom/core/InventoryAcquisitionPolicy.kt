package com.rabpit.backroom.core

import java.util.Locale
import org.json.JSONObject

/**
 * Authoritative gate for provider-proposed Inventory acquisition.
 *
 * The Android bridge may ask this policy whether a candidate item is allowed so rejected model
 * operations are filtered before audit, but the same policy is enforced again at the Game Core
 * commit boundary. Java/Python must not recreate these gameplay rules.
 */
object InventoryAcquisitionPolicy {
  @JvmStatic
  fun allows(
    beforeJson: String,
    rollsJson: String,
    action: String,
    itemName: String,
    alreadyOwned: Boolean,
  ): Boolean = allows(
    JSONObject(beforeJson),
    JSONObject(rollsJson.ifBlank { "{}" }),
    action,
    itemName,
    alreadyOwned,
  )

  internal fun allows(
    before: JSONObject,
    rolls: JSONObject,
    action: String,
    itemName: String,
    alreadyOwned: Boolean,
  ): Boolean {
    if (alreadyOwned) return true
    val name = itemName.trim()
    if (name.isEmpty() || !acquisitionIntent(action)) return false

    val established = establishedStructured(before, name)
    val normalizedName = name.lowercase(Locale.ROOT)
    val isMadGod = normalizedName.contains("madgod")
    val isAlmondWater = normalizedName.contains("almond water")

    return when {
      isMadGod -> madGodAlreadySpawned(before) && established
      isAlmondWater -> established || rollSuccess(rolls, "almondWater")
      copyIntent(action) -> established
      else -> established || rollSuccess(rolls, "loot")
    }
  }

  private fun acquisitionIntent(action: String): Boolean {
    val text = action.lowercase(Locale.ROOT)
    return listOf(
      "nhặt", "lấy", "cầm", "thu hồi", "tịch thu", "nhận", "cất", "bỏ vào",
      "đưa vào omnivault", "store", "sao chép", "copy",
    ).any(text::contains)
  }

  private fun copyIntent(action: String): Boolean {
    val text = action.lowercase(Locale.ROOT)
    return text.contains("copy") || text.contains("sao chép")
  }

  private fun establishedStructured(before: JSONObject, itemName: String): Boolean {
    val flags = before.optJSONObject("flags") ?: return false
    val needle = itemName.lowercase(Locale.ROOT)
    return listOf("exploration", "omnivault", "madGod").any { key ->
      flags.optJSONObject(key)?.toString()?.lowercase(Locale.ROOT)?.contains(needle) == true
    }
  }

  private fun madGodAlreadySpawned(before: JSONObject): Boolean =
    before.optJSONObject("flags")?.optJSONObject("madGod")?.optBoolean("spawned", false) == true

  private fun rollSuccess(rolls: JSONObject, key: String): Boolean =
    rolls.optJSONObject(key)?.optBoolean("success", false) == true
}

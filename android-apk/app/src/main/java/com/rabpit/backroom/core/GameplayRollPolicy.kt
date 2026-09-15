package com.rabpit.backroom.core

import java.util.Locale
import java.util.Random
import org.json.JSONObject

/**
 * Authoritative gameplay dice for the legacy Android turn bridge.
 *
 * The provider receives these outcomes as read-only context. It never chooses probabilities,
 * eligibility or rerolls. This policy intentionally mirrors the settled Level 0-6 runtime while
 * Java remains only an Android/WebView adapter.
 */
object GameplayRollPolicy {
  private val HAZARD_THRESHOLDS = intArrayOf(400, 700, 1000, 1200, 300, 1000, 1200)
  private val LOOT_THRESHOLDS = intArrayOf(35, 120, 100, 150, 180, 100, 45)
  private val WATER_THRESHOLDS = intArrayOf(20, 70, 35, 20, 120, 60, 35)

  @JvmStatic
  fun roll(
    stateJson: String,
    actionKind: String?,
    action: String,
    meta: Boolean,
    random: Random,
    emuLevel1Progression: Boolean = false,
    emuLevel06Traversal: Boolean = false,
  ): JSONObject = roll(
    JSONObject(stateJson), actionKind, action, meta, random,
    emuLevel1Progression, emuLevel06Traversal,
  )

  internal fun roll(
    state: JSONObject,
    actionKind: String?,
    action: String,
    meta: Boolean,
    random: Random,
    emuLevel1Progression: Boolean = false,
    emuLevel06Traversal: Boolean = false,
  ): JSONObject {
    val rolls = JSONObject().put("turn", state.optInt("turn", 1)).put("meta", meta)
    if (meta) return rolls

    val normalizedKind = actionKind.orEmpty().trim().uppercase(Locale.ROOT)
    val exploreAction = normalizedKind == "EXPLORE"
    rolls.put("actionKind", normalizedKind)

    val level = currentLevel(state).coerceIn(0, 6)
    val text = action.lowercase(Locale.ROOT)
    val physical = containsAny(text,
      "đi", "bước", "chạy", "leo", "mở", "đóng", "chạm", "lục", "tìm", "kiểm tra",
      "khảo sát", "quét", "scan", "bắn", "phá", "đẩy", "kéo", "tiến", "lùi", "cúi",
      "nhìn vào", "bò", "nhảy", "đào", "tháo", "đập", "vượt", "đi qua")
    val search = containsAny(text,
      "tìm", "lục", "khám phá", "khảo sát", "kiểm tra", "quét", "scan", "mở", "tháo",
      "quan sát kỹ", "rà")
    val water = containsAny(text,
      "nước", "water", "almond", "uống", "khát", "chai", "vòi", "hồ", "fountain")
    val exitIntent = containsAny(text,
      "exit", "lối thoát", "thoát", "cửa trắng", "cánh cửa", "ngưỡng", "chuyển level",
      "sang level", "hành lang phía sau", "đường ra")

    val flags = state.optJSONObject("flags")
    val survivorAllowed = flags == null || flags.optBoolean("survivorEncountersAllowed", true)
    val entityAllowed = flags == null || flags.optBoolean("entityEncountersAllowed", true)
    val madGod = flags?.optJSONObject("madGod")
    val madGodEligible = search &&
      (madGod == null || !madGod.optBoolean("spawned", false)) &&
      (flags == null || flags.optBoolean("madGodDiscoveryAllowed", true))

    // Keep the settled draw order stable: generic checks before/after the Entity batch match the
    // generated runtime that this class replaces.
    rolls.put("survivor", thresholdRoll("survivor", 10_000, 200, survivorAllowed, "", random))
    rolls.put("irisReunion", thresholdRoll(
      "irisReunion", 1_000_000, 25, reunionEligible(state, "iris"), "", random))
    rolls.put("syvialReunion", thresholdRoll(
      "syvialReunion", 1_000_000, 25, reunionEligible(state, "syvial"), "", random))
    rolls.put("hazard", thresholdRoll(
      "hazard", 10_000, HAZARD_THRESHOLDS[level], physical, "", random))

    val progressionFixture = emuLevel1Progression || emuLevel06Traversal
    val entityRolls = EntityEncounterPolicy.roll(
      exploreAction && entityAllowed && !progressionFixture,
      random,
    )
    val entityKeys = entityRolls.keys()
    while (entityKeys.hasNext()) {
      val key = entityKeys.next()
      rolls.put(key, entityRolls.get(key))
    }

    rolls.put("loot", thresholdRoll(
      "loot", 10_000, LOOT_THRESHOLDS[level], search, "", random))
    rolls.put("madGodSet", thresholdRoll(
      "madGodSet", 10_000, 1, madGodEligible, " UR+ UNIQUE discovery", random))
    rolls.put("almondWater", thresholdRoll(
      "almondWater", 10_000, WATER_THRESHOLDS[level], search && water, "", random))

    var exitThreshold = exitThreshold(state)
    if (emuLevel06Traversal && currentSublevelId(state).isNotEmpty()) {
      exitThreshold = 0
    } else if ((emuLevel1Progression || emuLevel06Traversal) &&
      exploreAction && levelTurns(state) >= 6) {
      exitThreshold = 10_000
    }
    val exitEligible = exploreAction || (exitIntent && (physical || search))
    val exitProbe = thresholdRoll(
      "exitProbe", 10_000, exitThreshold, exitEligible, " discovery clue", random)
    rolls.put("exitProbe", exitProbe)
    rolls.put("levelExit", JSONObject(exitProbe.toString()).put("label", "levelExit"))
    return rolls
  }

  private fun thresholdRoll(
    label: String,
    max: Int,
    threshold: Int,
    eligible: Boolean,
    suffix: String,
    random: Random,
  ): JSONObject {
    val result = JSONObject()
      .put("label", label)
      .put("dice", if (threshold >= max) "none" else "d$max")
      .put("max", max)
      .put("threshold", threshold)
      .put("eligible", eligible && threshold > 0)
    val percent = if (max > 0) threshold * 100.0 / max else 0.0
    result.put("chancePercent", percent)
      .put("chance", String.format(Locale.ROOT, "%.4f%%%s", percent, suffix))
    if (!eligible || threshold <= 0) {
      return result.put("roll", JSONObject.NULL).put("success", false)
    }
    if (threshold >= max) {
      return result.put("roll", JSONObject.NULL).put("success", true).put("guaranteedByState", true)
    }
    val value = random.nextInt(max) + 1
    return result.put("roll", value).put("success", value <= threshold)
  }

  private fun exitThreshold(state: JSONObject): Int {
    val flags = state.optJSONObject("flags") ?: return 10
    val explicit = flags.optInt("exitChanceThreshold", -1)
    if (explicit in 0..10_000) return explicit
    var progress = flags.optString("exitProgress", "")
    val exploration = flags.optJSONObject("exploration")
    if (progress.isEmpty() && exploration != null) progress = exploration.optString("exitProgress", "")
    val upper = progress.uppercase(Locale.ROOT)
    return when {
      containsAny(upper, "READY", "GUARANTEED", "CONDITION MET", "TRANSITION AVAILABLE") -> 10_000
      containsAny(upper, "NEAR", "ALMOST", "VERY STRONG") -> 150
      containsAny(upper, "STRONG", "CORRECT ROUTE") -> 100
      containsAny(upper, "CLUE", "CANDIDATE", "OPENED", "OBSERVED", "TRACKED") -> 50
      else -> 10
    }
  }

  private fun reunionEligible(state: JSONObject, key: String): Boolean {
    val flags = state.optJSONObject("flags")
    val record = flags?.optJSONObject(key) ?: return false
    if (!record.optBoolean("exists", true)) return false
    if (partyHas(state, key) || flagSpawned(state, key)) return false
    if (record.has("reunionEligible") && !record.optBoolean("reunionEligible", true)) return false
    val continuity = record.optString("continuity", "").uppercase(Locale.ROOT)
    return continuity.isEmpty() || containsAny(continuity, "SEPARATED", "LOST", "UNKNOWN")
  }

  private fun partyHas(state: JSONObject, needle: String): Boolean {
    val party = state.optJSONArray("party") ?: return false
    for (index in 0 until party.length()) {
      val item = party.opt(index)
      val name = if (item is JSONObject) item.optString("name", "") else item?.toString().orEmpty()
      if (name.contains(needle, ignoreCase = true)) return true
    }
    return false
  }

  private fun flagSpawned(state: JSONObject, key: String): Boolean {
    val value = state.optJSONObject("flags")?.optJSONObject(key) ?: return false
    return value.optBoolean("spawned", false) || value.optBoolean("present", false)
  }

  private fun currentLevel(state: JSONObject): Int {
    state.optJSONObject("level")?.let { return it.optInt("number", 0).coerceIn(0, 6) }
    val title = state.optString("title", "").lowercase(Locale.ROOT)
    for (level in 0..6) if (title.contains("level $level")) return level
    val location = state.optString("location", "").lowercase(Locale.ROOT).replace('–', '-')
    val names = arrayOf(
      "the lobby", "parking zone", "pipe dreams", "the electrical station",
      "the abandoned office", "terror hotel", "lights out")
    for (level in names.indices) if (location.contains(names[level])) return level
    return when {
      containsAny(location, "bãi đỗ xe", "bãi đậu xe", "gara bê tông", "garage bê tông") -> 1
      containsAny(location, "hầm đường ống", "đường ống", "pipe dreams") -> 2
      containsAny(location, "trạm điện", "electrical station") -> 3
      containsAny(location, "văn phòng bỏ hoang", "abandoned office") -> 4
      containsAny(location, "khách sạn kinh hoàng", "terror hotel") -> 5
      containsAny(location, "lights out", "vùng bóng tối") -> 6
      else -> 0
    }
  }

  private fun levelTurns(state: JSONObject): Int =
    state.optJSONObject("flags")?.optJSONObject("exploration")?.optInt("levelTurns", 0) ?: 0

  private fun currentSublevelId(state: JSONObject): String =
    state.optJSONObject("flags")?.optJSONObject("exploration")?.optString("sublevelId", "")?.trim().orEmpty()

  private fun containsAny(text: String, vararg terms: String): Boolean =
    terms.any { text.contains(it, ignoreCase = true) }
}

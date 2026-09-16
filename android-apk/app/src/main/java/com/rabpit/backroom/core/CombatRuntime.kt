package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Authoritative, save-persistent combat state stored in GameState.metadata. */
object CombatRuntime {
  private const val PREFIX = "combat."
  private const val ENTITY_HP_BONUS = 30
  private const val ENTITY_EVASION_PERCENT = 25
  private const val ENTITY_REGEN_PER_TURN = 1
  private const val KAI_GUILTY_CROWN_INTERVAL_TURNS = 3
  private const val KAI_GUILTY_CROWN_SHOTS = 24
  private const val KAI_GUILTY_CROWN_ACCURACY_PERCENT = 200
  private const val KAI_GUILTY_CROWN_DAMAGE_PER_SHOT = 10
  private const val DIEP_MINH_KEY = "diep_minh"
  private const val DIEP_MINH_MAX_HP = 2999
  private const val DIEP_MINH_ATTACK_PERCENT = 10
  private const val DIEP_MINH_REGEN_PER_TURN = 30
  private const val DIEP_MINH_ULTIMATE_INTERVAL_TURNS = 5
  private const val DIEP_MINH_ULTIMATE_PERCENT = 5
  private const val LUCIA_M4A1_COMBAT_DAMAGE = 26
  private const val KAI_LAST_REQUIEM_CHANCE_PERCENT = 30
  private const val KAI_LAST_REQUIEM_DAMAGE_PERCENT = 170
  private const val KAI_LAST_REQUIEM_BLEED_TURNS = 3
  private const val KAI_LAST_REQUIEM_BLEED_MAX_HP_PERCENT = 5
  private const val KAI_SILENT_LULLABY_CHANCE_PERCENT = 20
  private const val KAI_SILENT_LULLABY_DAMAGE_PERCENT = 130
  private const val KAI_SALVATION_CHANCE_PERCENT = 20
  private const val KAI_SALVATION_DAMAGE_PERCENT = 147
  private const val KAI_QUICK_STEP_CHANCE_PERCENT = 30
  private const val KAI_QUICK_STEP_EVASION_BONUS_PERCENT = 50
  private const val KAI_QUICK_STEP_DURATION_TURNS = 3
  private const val KAI_BLEED_TURNS_KEY = "combat.kaiBleedTurns"
  private const val KAI_QUICK_STEP_TURNS_KEY = "combat.kaiQuickStepTurns"
  // TRUE_TURN_COMBAT_V2
  private const val AUTO_CURSOR_KEY = "combat.autoCursor"
  private const val AUTO_STUN_KEY = "combat.autoStunActions"
  private const val AUTO_ACCURACY_PENALTY_KEY = "combat.autoAccuracyPenalty"
  private const val AUTO_ROUND_COUNTED_KEY = "combat.autoRoundCounted"
  private const val IRIS_ANALYZED_TURNS_KEY = "combat.irisAnalyzedTurns"
  private const val IRIS_ARMOR_BREAK_TURNS_KEY = "combat.irisArmorBreakTurns"
  private const val IRIS_EXPOSED_TURNS_KEY = "combat.irisExposedTurns"
  private const val SYVIAL_BLEED_TURNS_KEY = "combat.syvialBleedTurns"
  private const val SYVIAL_DEVIL_TRIGGER_KEY = "combat.syvialDevilTrigger"
  private const val SYVIAL_DISORIENT_TURNS_KEY = "combat.syvialDisorientTurns"
  private const val IRIS_ULTIMATE_INTERVAL_TURNS = 4
  private const val SYVIAL_ULTIMATE_INTERVAL_TURNS = 3
  private const val AN_NHIEN_ULTIMATE_INTERVAL_TURNS = 5

  enum class Phase { ACTIVE, RESOLVED }
  enum class RangeBand { CLOSE, NEAR, FAR }
  enum class Cover { EXPOSED, PARTIAL, HARD }
  enum class EntityCondition { HEALTHY, HURT, WOUNDED, CRITICAL, DESTROYED }
  enum class Intent { READ, ATTACK, EVADE, MOVE, GUARD, ESCAPE, OTHER }

  data class Profile(
    val key: String,
    val displayName: String,
    val maxHp: Int,
    val attack: Int,
    val armor: Int,
    val aggression: Int
  )

  data class Snapshot(
    val encounterId: String,
    val entityKey: String,
    val entityName: String,
    val phase: Phase,
    val playerHp: Int,
    val playerMaxHp: Int,
    val entityHp: Int,
    val entityMaxHp: Int,
    val entityCondition: EntityCondition,
    val range: RangeBand,
    val cover: Cover,
    val momentum: Int,
    val opening: Int,
    val escapeProgress: Int,
    val noise: Int,
    val telegraph: String,
    val telegraphRevealed: Boolean,
    val eventCounter: Int,
    val seed: Long
  )

  data class Resolution(
    val state: GameState,
    val handled: Boolean,
    val reply: String = "",
    val entityDestroyed: Boolean = false,
    val escaped: Boolean = false,
    val roundCompleted: Boolean = true
  )

  private val profiles = listOf(
    Profile("hound", "Hound", 80, 15, 2, 8),
    Profile("clump", "Clump", 105, 17, 5, 7),
    Profile("duller", "Duller", 90, 14, 3, 6),
    Profile("deathmoth", "Deathmoth", 65, 13, 1, 7),
    Profile("hostile_faceling", "Hostile Faceling", 75, 14, 2, 7),
    Profile("false_puddle", "False Puddle", 95, 16, 4, 5),
    Profile("paintings", "Paintings", 70, 12, 1, 5),
    Profile("smiler", "Smiler", 85, 18, 2, 9),
    Profile("skin-stealer", "Skin-Stealer", 100, 18, 4, 8),
    Profile("predatory_window", "Predatory Window", 115, 17, 6, 6),
    Profile("biological_pipeline", "Biological Pipeline", 120, 18, 7, 7),
    Profile("wretch", "Wretch", 85, 16, 2, 8),
    Profile("cable_mimic", "Cable Mimic", 100, 17, 5, 8),
    Profile("the_beast_of_level_5", "The Beast of Level 5", 145, 22, 8, 9),
    Profile("hotel_corpse_lure", "Hotel Corpse Lure", 110, 18, 5, 7),
    Profile("jeff_the_killer", "Jeff the Killer", 120, 20, 4, 9),
    Profile("jane_the_killer", "Jane the Killer", 120, 20, 4, 9),
    Profile("slenderman", "Slenderman", 160, 23, 8, 10),
    Profile(DIEP_MINH_KEY, "Diệp Minh", DIEP_MINH_MAX_HP, 0, 8, 9)
  ).associateBy { it.key }

  fun active(state: GameState): Snapshot? = decode(state)?.takeIf { it.phase == Phase.ACTIVE }

  fun start(state: GameState, entityKey: String): GameState {
    if (active(state) != null) return state
    val profile = profiles[entityKey] ?: return state
    val effective = CharacterStatEngine.effective(state, KAI_ID)
    val playerMax = effective.maxHp
    val playerHp = state.characters[KAI_ID]?.vitalState?.currentHp?.coerceIn(0, playerMax) ?: playerMax
    val seed = stableSeed(entityKey, state.turn.currentTurnId, state.time.elapsedSubjectiveMinutes)
    val enhancedEntityMaxHp = if (profile.key == DIEP_MINH_KEY) DIEP_MINH_MAX_HP else profile.maxHp + ENTITY_HP_BONUS
    val snapshot = Snapshot(
      encounterId = "${state.turn.currentTurnId}:${entityKey}:${abs(seed)}",
      entityKey = entityKey,
      entityName = profile.displayName,
      phase = Phase.ACTIVE,
      playerHp = playerHp,
      playerMaxHp = playerMax,
      entityHp = enhancedEntityMaxHp,
      entityMaxHp = enhancedEntityMaxHp,
      entityCondition = EntityCondition.HEALTHY,
      range = RangeBand.NEAR,
      cover = Cover.EXPOSED,
      momentum = 0,
      opening = 0,
      escapeProgress = 0,
      noise = 0,
      telegraph = telegraphFor(profile, seed, 0),
      telegraphRevealed = false,
      eventCounter = 0,
      seed = seed
    )
    return encode(state, snapshot)
  }

  private fun autoCombatantIds(state: GameState): List<String> = state.party.memberIds
    .distinct()
    .mapNotNull { memberId -> activePartyCharacter(state, memberId) }
    .filter { member ->
      !member.metadata["nonCombat"].equals("true", true) &&
        !member.metadata["canUseWeapons"].equals("false", true)
    }
    .map { member -> member.id }

  private fun autoTurnOrder(state: GameState): List<String> = autoCombatantIds(state).flatMap { memberId ->
    listOf(memberId, "entity:$memberId")
  }

  private fun currentAutoCursor(state: GameState, order: List<String>): Int {
    if (order.isEmpty()) return 0
    val raw = state.metadata[AUTO_CURSOR_KEY]?.toIntOrNull() ?: 0
    return ((raw % order.size) + order.size) % order.size
  }

  private fun withAutoCursor(state: GameState, cursor: Int): GameState {
    val metadata = state.metadata.toMutableMap()
    metadata[AUTO_CURSOR_KEY] = cursor.coerceAtLeast(0).toString()
    return state.copy(metadata = metadata)
  }

  private fun withoutAutoRoundState(state: GameState): GameState = state.copy(
    metadata = state.metadata - AUTO_ACCURACY_PENALTY_KEY - AUTO_ROUND_COUNTED_KEY
  )

  private fun clearDestroyedTrueTurnEntity(state: GameState, c: Snapshot): GameState {
    val persisted = encode(state, c.copy(phase = Phase.RESOLVED, entityCondition = EntityCondition.DESTROYED))
    return clearCombatOnly(persisted)
  }

  private fun nextAutoCursor(state: GameState, previousOrder: List<String>, previousCursor: Int): Int {
    val nextOrder = autoTurnOrder(state)
    if (nextOrder.isEmpty() || previousOrder.isEmpty()) return 0
    for (offset in 1..previousOrder.size) {
      val candidate = previousOrder[(previousCursor + offset) % previousOrder.size]
      val nextIndex = nextOrder.indexOf(candidate)
      if (nextIndex >= 0) return nextIndex
    }
    return 0
  }

  private fun autoActorMemberId(actorId: String): String = actorId.removePrefix("entity:")

  private fun autoActorSlot(state: GameState, actorId: String): Int =
    state.party.memberIds.indexOf(autoActorMemberId(actorId)).coerceAtLeast(0)

  private fun autoActorName(state: GameState, c: Snapshot, actorId: String): String {
    val memberId = autoActorMemberId(actorId)
    val memberName = state.characters[memberId]?.name ?: memberId
    return if (actorId.startsWith("entity:")) "${c.entityName} → $memberName" else memberName
  }

  private fun autoActorOverlay(actorId: String): String = when (actorId) {
    "kai", "entity:kai" -> "file:///android_asset/kai_entity_overlay.png"
    "lucia", "entity:lucia" -> "file:///android_asset/lucia_entity_overlay.png"
    "syvial", "entity:syvial" -> "file:///android_asset/syvial_entity_overlay.png"
    else -> ""
  }

  fun resolve(state: GameState, actionKind: String, action: String): Resolution {
    val current = active(state) ?: return Resolution(state, handled = false)
    val profile = profiles[current.entityKey] ?: return Resolution(clear(state), handled = false)
    val splitAuto = actionKind.equals("AUTO_COMBAT_STEP", true)
    val autoOrder = if (splitAuto) autoTurnOrder(state) else emptyList()
    val autoCursor = if (splitAuto) currentAutoCursor(state, autoOrder) else 0
    val autoActor = if (splitAuto && autoOrder.isNotEmpty()) autoOrder[autoCursor] else ""
    val intent = if (splitAuto) Intent.ATTACK else classify(actionKind, action)
    val autoRoundAlreadyCounted = splitAuto && (state.metadata[AUTO_ROUND_COUNTED_KEY]?.toIntOrNull() ?: 0) > 0
    val autoRoundStartsNow = splitAuto && !autoActor.startsWith("entity:") && !autoRoundAlreadyCounted
    var c = current.copy(eventCounter = current.eventCounter + if (!splitAuto || autoRoundStartsNow) 1 else 0)
    val log = mutableListOf<String>()
    var resolvedState = state
    var bleedTurns = state.metadata[KAI_BLEED_TURNS_KEY]?.toIntOrNull()?.coerceIn(0, KAI_LAST_REQUIEM_BLEED_TURNS) ?: 0
    var quickStepTurns = state.metadata[KAI_QUICK_STEP_TURNS_KEY]?.toIntOrNull()?.coerceIn(0, KAI_QUICK_STEP_DURATION_TURNS) ?: 0
    var entityStunnedThisTurn = splitAuto && (state.metadata[AUTO_STUN_KEY]?.toIntOrNull() ?: 0) > 0
    var companionEnemyAccuracyPenalty = 0
    var irisAnalyzedTurns = state.metadata[IRIS_ANALYZED_TURNS_KEY]?.toIntOrNull()?.coerceIn(0, 3) ?: 0
    var irisArmorBreakTurns = state.metadata[IRIS_ARMOR_BREAK_TURNS_KEY]?.toIntOrNull()?.coerceIn(0, 2) ?: 0
    var irisExposedTurns = state.metadata[IRIS_EXPOSED_TURNS_KEY]?.toIntOrNull()?.coerceIn(0, 2) ?: 0
    var syvialBleedTurns = state.metadata[SYVIAL_BLEED_TURNS_KEY]?.toIntOrNull()?.coerceIn(0, 3) ?: 0
    var syvialDisorientTurns = state.metadata[SYVIAL_DISORIENT_TURNS_KEY]?.toIntOrNull()?.coerceIn(0, 2) ?: 0
    var syvialDevilTrigger = state.metadata[SYVIAL_DEVIL_TRIGGER_KEY]?.equals("true", ignoreCase = true) == true

    when (intent) {
      Intent.READ -> {
        c = c.copy(
          telegraphRevealed = true,
          opening = min(3, c.opening + 1),
          momentum = min(3, c.momentum + 1)
        )
        log += "Kai đọc được nhịp tấn công của ${c.entityName}; sơ hở tăng lên."
      }
      Intent.EVADE -> {
        val goodCounter = c.telegraph in setOf("LUNGE", "GRAB", "RUSH")
        c = c.copy(
          range = if (c.range == RangeBand.CLOSE) RangeBand.NEAR else c.range,
          momentum = (c.momentum + if (goodCounter) 2 else 1).coerceIn(-3, 3),
          opening = min(3, c.opening + if (goodCounter) 2 else 1),
          escapeProgress = min(100, c.escapeProgress + if (goodCounter) 18 else 10),
          cover = if (c.cover == Cover.EXPOSED) Cover.PARTIAL else c.cover
        )
        log += if (goodCounter) "Kai né đúng telegraph, cướp thế chủ động." else "Kai đổi góc và giảm áp lực trực diện."
      }
      Intent.MOVE -> {
        val nextRange = when (c.range) {
          RangeBand.CLOSE -> RangeBand.NEAR
          RangeBand.NEAR -> RangeBand.FAR
          RangeBand.FAR -> RangeBand.FAR
        }
        c = c.copy(
          range = nextRange,
          cover = if (c.cover == Cover.EXPOSED) Cover.PARTIAL else Cover.HARD,
          escapeProgress = min(100, c.escapeProgress + 15),
          momentum = min(3, c.momentum + 1)
        )
        log += "Kai tái định vị, kéo giãn khoảng cách và tìm vật che chắn."
      }
      Intent.GUARD -> {
        c = c.copy(cover = Cover.HARD, momentum = min(3, c.momentum + 1), opening = min(3, c.opening + 1))
        log += "Kai khóa tư thế phòng thủ và ép ${c.entityName} phải lộ hướng tấn công."
      }
      Intent.ESCAPE -> {
        val gain = 20 + c.momentum.coerceAtLeast(0) * 5 + when (c.cover) { Cover.HARD -> 15; Cover.PARTIAL -> 8; Cover.EXPOSED -> 0 }
        c = c.copy(escapeProgress = min(100, c.escapeProgress + gain), momentum = min(3, c.momentum + 1))
        log += "Kai dồn ưu thế vào đường thoát (${c.escapeProgress}%)."
      }
      Intent.ATTACK -> {
        if (!splitAuto || autoActor == "kai") {
        val roll = roll(c, 100)
        val rangeBonus = when (c.range) { RangeBand.CLOSE -> 18; RangeBand.NEAR -> 10; RangeBand.FAR -> -5 }
        val hitChance = (58 + rangeBonus + c.opening * 11 + c.momentum * 6).coerceIn(20, 96)
        val evasionRoll = roll(c.copy(eventCounter = c.eventCounter + 13), 100)
        val entityEvaded = evasionRoll < ENTITY_EVASION_PERCENT
        if (roll < hitChance && !entityEvaded) {
          val variance = 2 + roll(c.copy(eventCounter = c.eventCounter + 17), 7)
          val effective = CharacterStatEngine.effective(state, KAI_ID)
          val weaponDamage = CharacterStatEngine.weaponDamage(state, KAI_ID)
          val critChance = CombatStatMath.critChancePercent(effective.crit)
          val critical = roll(c.copy(eventCounter = c.eventCounter + 23), 100) < critChance
          val base = weaponDamage + variance + c.opening * 5 + max(0, c.momentum) * 2
          val normalized = if (critical) base * 3 / 2 else base
          val damage = min(max(1, normalized - profile.armor), max(1, profile.maxHp * 70 / 100))
          val hp = max(0, c.entityHp - damage)
          c = c.copy(
            entityHp = hp,
            entityCondition = condition(hp, c.entityMaxHp),
            momentum = min(3, c.momentum + 1),
            opening = max(0, c.opening - 1),
            noise = min(100, c.noise + 35)
          )
          log += "Đòn đánh trúng ${c.entityName}: -$damage HP (${c.entityHp}/${c.entityMaxHp})."
        } else {
          c = c.copy(momentum = max(-3, c.momentum - 1), opening = max(0, c.opening - 1), noise = min(100, c.noise + 28))
          log += if (entityEvaded) "${c.entityName} né đòn (25% evasion) và giành lại áp lực." else "Đòn đánh trượt; ${c.entityName} giành lại áp lực."
        }
        }
        // LUCIA_AUTO_ATTACK_V1: an ACTIVE living Lucia attacks once in every authoritative ATTACK round.
        val lucia = resolvedState.characters[LUCIA_ID]
        val luciaActive = LUCIA_ID in resolvedState.party.memberIds &&
          lucia?.presence == CharacterPresence.ACTIVE && (lucia.vitalState.currentHp > 0)
        if ((!splitAuto || autoActor == "lucia") && luciaActive && c.entityHp > 0) {
          val luciaRangeBonus = when (c.range) { RangeBand.CLOSE -> 18; RangeBand.NEAR -> 10; RangeBand.FAR -> -5 }
          val luciaHitChance = (58 + luciaRangeBonus + c.opening * 11 + c.momentum * 6).coerceIn(20, 96)
          val luciaRoll = roll(c.copy(eventCounter = c.eventCounter + 83), 100)
          val luciaEvasionRoll = roll(c.copy(eventCounter = c.eventCounter + 97), 100)
          val luciaEntityEvaded = luciaEvasionRoll < ENTITY_EVASION_PERCENT
          if (luciaRoll < luciaHitChance && !luciaEntityEvaded) {
            val luciaDamage = max(1, LUCIA_M4A1_COMBAT_DAMAGE - profile.armor)
            val luciaHp = max(0, c.entityHp - luciaDamage)
            c = c.copy(
              entityHp = luciaHp,
              entityCondition = condition(luciaHp, c.entityMaxHp),
              noise = min(100, c.noise + 22)
            )
            log += "Lucia \"Lục\" bắn hỗ trợ bằng M4A1: -$luciaDamage HP (${c.entityHp}/${c.entityMaxHp})."
          } else {
            log += "Lucia \"Lục\" cũng khai hỏa nhưng phát bắn không trúng mục tiêu."
          }
        }
      }
      Intent.OTHER -> {
        c = c.copy(momentum = max(-3, c.momentum - 1))
        log += "Hành động không tạo được lợi thế chiến đấu rõ ràng."
      }
    }

    // Lucia commits only her own attack and hands ownership to Entity->Lucia.
    if (splitAuto && autoActor == LUCIA_ID) {
      if (c.entityHp <= 0) {
        val persisted = encode(resolvedState, c.copy(phase = Phase.RESOLVED, entityCondition = EntityCondition.DESTROYED))
        val cleared = clearCombatOnly(persisted)
        return Resolution(
          cleared,
          true,
          log.joinToString(" ") + " ${c.entityName} đã bị tiêu diệt.",
          entityDestroyed = true,
          roundCompleted = true
        )
      }
      if (autoRoundStartsNow) resolvedState = withCombatCounter(resolvedState, AUTO_ROUND_COUNTED_KEY, 1)
      val stagedState = encode(resolvedState, c)
      val staged = withAutoCursor(stagedState, nextAutoCursor(stagedState, autoOrder, autoCursor))
      return Resolution(staged, true, log.joinToString(" "), roundCompleted = false)
    }

    // Companion mechanics are resolved only by their own authoritative actor.
    val irisActive = activePartyCharacter(resolvedState, IRIS_ID) != null
    val syvialCharacter = activePartyCharacter(resolvedState, SYVIAL_ID)
    val syvialActive = syvialCharacter != null
    val anNhienActive = activePartyCharacter(resolvedState, AN_NHIEN_ID) != null
    val autoMemberEntityHpBefore = if (splitAuto && !autoActor.startsWith("entity:") && autoActor != KAI_ID && autoActor != LUCIA_ID) c.entityHp else -1

    if (!splitAuto || autoActor == KAI_ID) {
    if (c.entityHp > 0 && bleedTurns > 0) {
      val bleedDamage = percentDamage(c.entityMaxHp, KAI_LAST_REQUIEM_BLEED_MAX_HP_PERCENT)
      val hp = max(0, c.entityHp - bleedDamage)
      bleedTurns = max(0, bleedTurns - 1)
      resolvedState = withCombatCounter(resolvedState, KAI_BLEED_TURNS_KEY, bleedTurns)
      c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
      log += "Bleeding từ The Last Requiem gây -$bleedDamage HP (${KAI_LAST_REQUIEM_BLEED_MAX_HP_PERCENT}% Max HP; ${c.entityHp}/${c.entityMaxHp}); còn $bleedTurns turn."
    }

    if (c.entityHp > 0 && syvialBleedTurns > 0) {
      val bleedDamage = percentDamage(c.entityMaxHp, 4)
      val hp = max(0, c.entityHp - bleedDamage)
      syvialBleedTurns = max(0, syvialBleedTurns - 1)
      resolvedState = withCombatCounter(resolvedState, SYVIAL_BLEED_TURNS_KEY, syvialBleedTurns)
      c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
      log += "Bleeding từ Crimson Guillotine gây -$bleedDamage HP (4% Max HP; ${c.entityHp}/${c.entityMaxHp}); còn $syvialBleedTurns turn."
    }

    if (c.entityHp <= 0) {
      val cleared = clearDestroyedTrueTurnEntity(resolvedState, c)
      return Resolution(cleared, true, log.joinToString(" ") + " ${c.entityName} đã bị tiêu diệt.", entityDestroyed = true)
    }
    if (c.escapeProgress >= 100) {
      val persisted = encode(resolvedState, c.copy(phase = Phase.RESOLVED))
      val cleared = clearCombatOnly(persisted)
      return Resolution(cleared, true, log.joinToString(" ") + " Kai cắt được truy đuổi và thoát khỏi encounter.", escaped = true)
    }

    if (c.eventCounter % KAI_GUILTY_CROWN_INTERVAL_TURNS == 0) {
      val totalDamage = KAI_GUILTY_CROWN_SHOTS * KAI_GUILTY_CROWN_DAMAGE_PER_SHOT
      val hp = max(0, c.entityHp - totalDamage)
      c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
      log += "Guilty Crown Override tự động kích hoạt ở combat turn ${c.eventCounter}: $KAI_GUILTY_CROWN_SHOTS/" +
        "$KAI_GUILTY_CROWN_SHOTS phát trúng liên tiếp, Accuracy $KAI_GUILTY_CROWN_ACCURACY_PERCENT%, bỏ qua toàn bộ hiệu ứng né; " +
        "mỗi phát -$KAI_GUILTY_CROWN_DAMAGE_PER_SHOT HP, tổng -$totalDamage HP (${c.entityHp}/${c.entityMaxHp})."
      if (c.entityHp <= 0) {
        val persisted = encode(resolvedState, c.copy(phase = Phase.RESOLVED, entityCondition = EntityCondition.DESTROYED))
        val cleared = clearCombatOnly(persisted)
        return Resolution(cleared, true, log.joinToString(" ") + " ${c.entityName} đã bị tiêu diệt.", entityDestroyed = true)
      }
    }

    val isGuiltyCrownTurn = c.eventCounter % KAI_GUILTY_CROWN_INTERVAL_TURNS == 0
    if (!isGuiltyCrownTurn && c.entityHp > 0) {
      val weaponDamage = CharacterStatEngine.weaponDamage(resolvedState, KAI_ID)

      if (roll(c.copy(eventCounter = c.eventCounter + 101), 100) < KAI_LAST_REQUIEM_CHANCE_PERCENT) {
        val damage = weaponSkillDamage(weaponDamage, KAI_LAST_REQUIEM_DAMAGE_PERCENT, profile.armor)
        val hp = max(0, c.entityHp - damage)
        c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp), noise = min(100, c.noise + 22))
        bleedTurns = KAI_LAST_REQUIEM_BLEED_TURNS
        resolvedState = withCombatCounter(resolvedState, KAI_BLEED_TURNS_KEY, bleedTurns)
        log += "The Last Requiem tự động kích hoạt: 4 phát vào khớp vai, ${KAI_LAST_REQUIEM_DAMAGE_PERCENT}% DMG = -$damage HP; Bleeding ${KAI_LAST_REQUIEM_BLEED_TURNS} turn, ${KAI_LAST_REQUIEM_BLEED_MAX_HP_PERCENT}% Max HP/turn."
      }

      if (c.entityHp > 0 && roll(c.copy(eventCounter = c.eventCounter + 113), 100) < KAI_SILENT_LULLABY_CHANCE_PERCENT) {
        val damage = weaponSkillDamage(weaponDamage, KAI_SILENT_LULLABY_DAMAGE_PERCENT, profile.armor)
        val hp = max(0, c.entityHp - damage)
        c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp), noise = min(100, c.noise + 18))
        entityStunnedThisTurn = true
        log += "Silent Lullaby tự động kích hoạt: Kai bật lên cao, 4 viên ghim cùng điểm trên ngực, ${KAI_SILENT_LULLABY_DAMAGE_PERCENT}% DMG = -$damage HP; Stun 1 turn."
      }

      if (c.entityHp > 0 && roll(c.copy(eventCounter = c.eventCounter + 127), 100) < KAI_SALVATION_CHANCE_PERCENT) {
        val damage = weaponSkillDamage(weaponDamage, KAI_SALVATION_DAMAGE_PERCENT, profile.armor)
        val hp = max(0, c.entityHp - damage)
        c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp), noise = min(100, c.noise + 16))
        log += "Salvation tự động kích hoạt: Kai ném súng ra sau mục tiêu, dịch chuyển tức thời tới vị trí súng và bắn nhanh 2 phát, ${KAI_SALVATION_DAMAGE_PERCENT}% DMG = -$damage HP."
      }

      if (c.entityHp > 0 && roll(c.copy(eventCounter = c.eventCounter + 139), 100) < KAI_QUICK_STEP_CHANCE_PERCENT) {
        quickStepTurns = KAI_QUICK_STEP_DURATION_TURNS
        resolvedState = withCombatCounter(resolvedState, KAI_QUICK_STEP_TURNS_KEY, quickStepTurns)
        log += "Quick Step tự động kích hoạt: dịch chuyển ngắn liên tục, +${KAI_QUICK_STEP_EVASION_BONUS_PERCENT}% Evasion trong ${KAI_QUICK_STEP_DURATION_TURNS} turn."
      }
    }

    if (c.entityHp <= 0) {
      val cleared = clearDestroyedTrueTurnEntity(resolvedState, c)
      return Resolution(cleared, true, log.joinToString(" ") + " ${c.entityName} đã bị tiêu diệt.", entityDestroyed = true)
    }

    }

    // COMPANION_SKILLS_R01: Iris, Syvial and An Nhien wrap the finalized combat response.

    if ((!splitAuto || autoActor == IRIS_ID) && irisActive && c.entityHp > 0) {
      if (irisAnalyzedTurns <= 0) {
        irisAnalyzedTurns = 3
        resolvedState = withCombatCounter(resolvedState, IRIS_ANALYZED_TURNS_KEY, irisAnalyzedTurns)
        log += "ARGUS Terrain Read: Iris đánh dấu mục tiêu Analyzed trong 3 turn."
      }
      val irisWeapon = CharacterStatEngine.weaponDamage(resolvedState, IRIS_ID)
      val irisArmor = when {
        irisExposedTurns > 0 -> armorAfterIgnore(profile.armor, 20)
        irisArmorBreakTurns > 0 -> armorAfterIgnore(profile.armor, 20)
        else -> profile.armor
      }
      val irisUltimate = c.eventCounter % IRIS_ULTIMATE_INTERVAL_TURNS == 0
      if (irisUltimate) {
        val damage = companionSkillDamage(irisWeapon, 300, irisArmor)
        val hp = max(0, c.entityHp - damage)
        c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp), noise = min(100, c.noise + 30))
        irisExposedTurns = 2
        resolvedState = withCombatCounter(resolvedState, IRIS_EXPOSED_TURNS_KEY, irisExposedTurns)
        log += "ARGUS // Thousandfold Execution: 12 phát luân phiên, 300% DMG = -$damage HP; Fully Exposed 2 turn."
      } else {
        if (roll(c.copy(eventCounter = c.eventCounter + 151), 100) < 30 && c.entityHp > 0) {
          val percent = if (irisAnalyzedTurns > 0) 170 else 155
          val damage = companionSkillDamage(irisWeapon, percent, irisArmor)
          val hp = max(0, c.entityHp - damage)
          c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp), noise = min(100, c.noise + 14))
          log += "Twosome Time tự động kích hoạt: 2 phát chéo góc, $percent% DMG = -$damage HP."
        }
        if (roll(c.copy(eventCounter = c.eventCounter + 163), 100) < 20 && c.entityHp > 0) {
          val damage = companionSkillDamage(irisWeapon, 145, irisArmor)
          val hp = max(0, c.entityHp - damage)
          c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp), noise = min(100, c.noise + 18))
          log += "Rain Storm tự động kích hoạt: 6 phát khi đổi góc trên không, 145% DMG = -$damage HP."
        }
        if (roll(c.copy(eventCounter = c.eventCounter + 179), 100) < 20 && c.entityHp > 0) {
          val damage = companionSkillDamage(irisWeapon, 185, irisArmor)
          val hp = max(0, c.entityHp - damage)
          c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp), noise = min(100, c.noise + 24))
          irisArmorBreakTurns = 2
          resolvedState = withCombatCounter(resolvedState, IRIS_ARMOR_BREAK_TURNS_KEY, irisArmorBreakTurns)
          log += "Honeycomb Fire tự động kích hoạt: 8 phát tập trung, 185% DMG = -$damage HP; Armor Break 20% trong 2 turn."
        }
        if (roll(c.copy(eventCounter = c.eventCounter + 191), 100) < 25 && c.entityHp > 0) {
          val chargedArmor = armorAfterIgnore(profile.armor, 35)
          val damage = companionSkillDamage(irisWeapon, 175, chargedArmor)
          val hp = max(0, c.entityHp - damage)
          c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp), noise = min(100, c.noise + 20))
          log += "Charged Shot tự động kích hoạt: 175% DMG = -$damage HP, bỏ qua 35% Armor."
        }
      }
    }

    if ((!splitAuto || autoActor == SYVIAL_ID) && syvialActive && c.entityHp > 0) {
      val syvialMaxHp = CharacterStatEngine.effective(resolvedState, SYVIAL_ID).maxHp
      val syvialHp = syvialCharacter!!.vitalState.currentHp
      if (!syvialDevilTrigger && (syvialHp * 2 <= syvialMaxHp || c.entityKey == DIEP_MINH_KEY)) {
        syvialDevilTrigger = true
        val metadata = resolvedState.metadata.toMutableMap()
        metadata[SYVIAL_DEVIL_TRIGGER_KEY] = "true"
        resolvedState = resolvedState.copy(metadata = metadata)
        log += "Syvial kích hoạt Devil Trigger."
      }
      val regenPercent = if (syvialDevilTrigger) 4 else 2
      if (syvialHp > 0 && syvialHp < syvialMaxHp) {
        val heal = percentDamage(syvialMaxHp, regenPercent)
        resolvedState = CharacterStatEngine.setCurrentHp(resolvedState, SYVIAL_ID, syvialHp + heal)
        val after = resolvedState.characters[SYVIAL_ID]?.vitalState?.currentHp ?: syvialHp
        log += "Lucifer Core hồi Syvial +${after - syvialHp} HP ($after/$syvialMaxHp)."
      }
      val syvialWeapon = CharacterStatEngine.weaponDamage(resolvedState, SYVIAL_ID)
      val dtMultiplier = if (syvialDevilTrigger) 125 else 100
      fun syvialDamage(percent: Int, armor: Int): Int = companionSkillDamage(syvialWeapon, (percent * dtMultiplier + 99) / 100, armor)
      val syvialUltimate = syvialDevilTrigger && c.eventCounter % SYVIAL_ULTIMATE_INTERVAL_TURNS == 0
      if (syvialUltimate) {
        val damage = min(c.entityHp, 24 * 10)
        val hp = max(0, c.entityHp - damage)
        c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp), noise = min(100, c.noise + 28))
        log += "GodKiller Override // Twenty-Four Severance: thời gian ngoại giới dừng, đúng 24 nhát x 10 HP = -$damage HP; bỏ qua Evasion."
      } else {
        if (roll(c.copy(eventCounter = c.eventCounter + 211), 100) < 30 && c.entityHp > 0) {
          val damage = syvialDamage(175, armorAfterIgnore(profile.armor, 20))
          val hp = max(0, c.entityHp - damage)
          c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
          log += "Rift Sever tự động kích hoạt: Spatial Shift + GodKiller, 175% DMG = -$damage HP."
        }
        if (roll(c.copy(eventCounter = c.eventCounter + 223), 100) < 20 && c.entityHp > 0) {
          val damage = syvialDamage(190, profile.armor)
          val hp = max(0, c.entityHp - damage)
          c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
          syvialBleedTurns = 3
          resolvedState = withCombatCounter(resolvedState, SYVIAL_BLEED_TURNS_KEY, syvialBleedTurns)
          log += "Crimson Guillotine tự động kích hoạt: 190% DMG = -$damage HP; Bleeding 3 turn x 4% Max HP."
        }
        if (roll(c.copy(eventCounter = c.eventCounter + 239), 100) < 20 && c.entityHp > 0) {
          val damage = syvialDamage(155, profile.armor)
          val hp = max(0, c.entityHp - damage)
          c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
          entityStunnedThisTurn = true
          log += "Lucifer Breaker tự động kích hoạt: 155% DMG = -$damage HP; Entity bị Stun trong phản ứng hiện tại."
        }
        if (syvialDevilTrigger && roll(c.copy(eventCounter = c.eventCounter + 251), 100) < 20 && c.entityHp > 0) {
          val damage = syvialDamage(210, profile.armor)
          val hp = max(0, c.entityHp - damage)
          c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
          syvialDisorientTurns = 2
          resolvedState = withCombatCounter(resolvedState, SYVIAL_DISORIENT_TURNS_KEY, syvialDisorientTurns)
          log += "Spatial Dominion tự động kích hoạt: 210% DMG = -$damage HP; Disoriented -25% Accuracy trong 2 turn."
        }
      }
    }

    val accuracyPenaltyBeforeRoundSupport = companionEnemyAccuracyPenalty
    if ((!splitAuto || autoActor == KAI_ID) && anNhienActive && c.entityHp > 0) {
      if (roll(c.copy(eventCounter = c.eventCounter + 269), 100) < 25) {
        companionEnemyAccuracyPenalty += 25
        log += "An Nhiên dùng Quăng Đại Cái Gì Đó: tiếng động lệch hướng khiến Entity -25 điểm % Accuracy trong phản ứng hiện tại."
      }
      if (c.eventCounter % AN_NHIEN_ULTIMATE_INTERVAL_TURNS == 0) {
        companionEnemyAccuracyPenalty += 20
        c = c.copy(escapeProgress = min(100, c.escapeProgress + 30))
        log += "Kế Hoạch Không Có Trong Kế Hoạch: +30 Escape Progress và Entity -20 điểm % Accuracy trong phản ứng hiện tại."
      }
    }

    val newlyResolvedRoundSupportPenalty =
      max(0, companionEnemyAccuracyPenalty - accuracyPenaltyBeforeRoundSupport)
    val persistedRoundSupportPenalty =
      state.metadata[AUTO_ACCURACY_PENALTY_KEY]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
    if (splitAuto && autoActor == KAI_ID) {
      resolvedState = withCombatCounter(resolvedState, AUTO_ACCURACY_PENALTY_KEY, newlyResolvedRoundSupportPenalty)
    } else if (splitAuto && autoActor.startsWith("entity:")) {
      companionEnemyAccuracyPenalty += persistedRoundSupportPenalty
      if (AUTO_ACCURACY_PENALTY_KEY in resolvedState.metadata) {
        resolvedState = resolvedState.copy(metadata = resolvedState.metadata - AUTO_ACCURACY_PENALTY_KEY)
      }
    }

    if (syvialDisorientTurns > 0) companionEnemyAccuracyPenalty += 25

    val trueTurnEnemyAccuracyPenalty = companionEnemyAccuracyPenalty

    if (splitAuto && autoMemberEntityHpBefore >= 0 && c.entityHp == autoMemberEntityHpBefore && c.entityHp > 0) {
      val member = activePartyCharacter(resolvedState, autoActor)
      if (member != null) {
        val rangeBonus = when (c.range) { RangeBand.CLOSE -> 18; RangeBand.NEAR -> 10; RangeBand.FAR -> -5 }
        val hitChance = (58 + rangeBonus + c.opening * 11 + c.momentum * 6).coerceIn(20, 96)
        val actorSeedOffset = (autoActor.hashCode() and 0x7fffffff) % 997
        val attackRoll = roll(c.copy(eventCounter = c.eventCounter + 307 + actorSeedOffset), 100)
        val evasionRoll = roll(c.copy(eventCounter = c.eventCounter + 311 + actorSeedOffset), 100)
        if (attackRoll < hitChance && evasionRoll >= ENTITY_EVASION_PERCENT) {
          val weaponDamage = CharacterStatEngine.weaponDamage(resolvedState, autoActor)
          val damage = max(1, weaponDamage - profile.armor)
          val hp = max(0, c.entityHp - damage)
          c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp), noise = min(100, c.noise + 24))
          log += "${member.name} tấn công: -$damage HP (${c.entityHp}/${c.entityMaxHp})."
        } else {
          log += "${member.name} tấn công nhưng ${c.entityName} tránh được đòn."
        }
      }
    }

    if (c.entityHp <= 0) {
      val cleared = clearDestroyedTrueTurnEntity(resolvedState, c)
      return Resolution(cleared, true, log.joinToString(" ") + " ${c.entityName} đã bị tiêu diệt.", entityDestroyed = true)
    }

    if (splitAuto && !autoActor.startsWith("entity:")) {
      if (autoRoundStartsNow) resolvedState = withCombatCounter(resolvedState, AUTO_ROUND_COUNTED_KEY, 1)
      if (entityStunnedThisTurn) resolvedState = withCombatCounter(resolvedState, AUTO_STUN_KEY, 1)
      val stagedState = encode(resolvedState, c)
      val staged = withAutoCursor(stagedState, nextAutoCursor(stagedState, autoOrder, autoCursor))
      return Resolution(staged, true, log.joinToString(" "), roundCompleted = false)
    }

    if (splitAuto && autoActor.startsWith("entity:") && entityStunnedThisTurn) {
      // Stun is exactly one Entity action. Persist it across the Kai -> Entity boundary,
      // then consume it before evaluating the Entity response.
      resolvedState = withCombatCounter(resolvedState, AUTO_STUN_KEY, 0)
    }

    // Enemy response. Diệp Minh uses percentage damage; all other Entity behavior remains unchanged.
    if (splitAuto && autoActor.startsWith("entity:") && autoActor != "entity:$KAI_ID") {
      val targetId = autoActorMemberId(autoActor)
      val target = resolvedState.characters[targetId]
      val targetName = target?.name ?: targetId
      if (entityStunnedThisTurn) {
        log += "Silent Lullaby: ${c.entityName} bị Stun và mất lượt phản ứng hiện tại."
      } else if (target == null || target.presence != CharacterPresence.ACTIVE || target.vitalState.currentHp <= 0) {
        log += "${c.entityName} không còn mục tiêu $targetName hợp lệ cho lượt phản ứng này."
      } else {
        val effective = CharacterStatEngine.effective(resolvedState, targetId)
        val targetMaxHp = effective.maxHp
        val targetHp = target.vitalState.currentHp.coerceIn(0, targetMaxHp)
        val incomingRoll = roll(c.copy(eventCounter = c.eventCounter + 157), 100)
        val defense = when (c.cover) { Cover.HARD -> 22; Cover.PARTIAL -> 10; Cover.EXPOSED -> 0 } +
          CombatStatMath.agilityDefense(effective.agi) * 4 + max(0, c.momentum) * 2
        val enemyChance = (profile.aggression * 8 - defense + max(0, -c.momentum) * 7 - trueTurnEnemyAccuracyPenalty).coerceIn(8, 88)
        if (incomingRoll < enemyChance) {
          val mitigation = CombatStatMath.defenseReduction(effective.df) + CombatStatMath.agilityDefense(effective.agi)
          val damage = if (c.entityKey == DIEP_MINH_KEY) {
            percentDamage(targetMaxHp, DIEP_MINH_ATTACK_PERCENT)
          } else {
            max(1, profile.attack + roll(c.copy(eventCounter = c.eventCounter + 173), 7) -
              when (c.cover) { Cover.HARD -> 8; Cover.PARTIAL -> 4; Cover.EXPOSED -> 0 } - mitigation)
          }
          resolvedState = CharacterStatEngine.setCurrentHp(resolvedState, targetId, targetHp - damage)
          val after = resolvedState.characters[targetId]?.vitalState?.currentHp ?: max(0, targetHp - damage)
          c = c.copy(momentum = max(-3, c.momentum - 1))
          log += if (c.entityKey == DIEP_MINH_KEY) {
            "Diệp Minh phản công $targetName: -$damage HP (${DIEP_MINH_ATTACK_PERCENT}% Max HP; $after/$targetMaxHp)."
          } else {
            "${c.entityName} phản công $targetName: -$damage HP ($after/$targetMaxHp)."
          }
        } else {
        if (targetId == IRIS_ID && (irisActive && c.entityHp > 0 && roll(c.copy(eventCounter = c.eventCounter + 281), 100) < 15)) {
          val damage = companionSkillDamage(CharacterStatEngine.weaponDamage(resolvedState, IRIS_ID), 120, profile.armor)
          val hp = max(0, c.entityHp - damage)
          c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
          log += "Dead Angle: Iris phản kích tức thời 120% DMG = -$damage HP."
        }
        if (targetId == SYVIAL_ID && (syvialActive && c.entityHp > 0 && roll(c.copy(eventCounter = c.eventCounter + 293), 100) < 30)) {
          val damage = companionSkillDamage(CharacterStatEngine.weaponDamage(resolvedState, SYVIAL_ID), if (syvialDevilTrigger) 157 else 125, profile.armor)
          val hp = max(0, c.entityHp - damage)
          c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
          log += "Counterphase: Syvial Spatial Shift vào góc chết và phản chém -$damage HP."
        }
          log += "$targetName tránh được lượt phản công của ${c.entityName}."
        }
      }
    } else {
    if (entityStunnedThisTurn) {
      log += "Silent Lullaby: ${c.entityName} bị Stun và mất lượt phản ứng hiện tại."
    } else if (c.entityKey == DIEP_MINH_KEY && c.eventCounter % DIEP_MINH_ULTIMATE_INTERVAL_TURNS == 0) {
      val pulse = damageActivePartyByPercent(resolvedState, DIEP_MINH_ULTIMATE_PERCENT)
      resolvedState = pulse.state
      c = c.copy(playerHp = pulse.kaiHp, momentum = max(-3, c.momentum - 1))
      log += "Devils And Gold kích hoạt ở combat turn ${c.eventCounter}: toàn bộ nhân vật ACTIVE đang ra trận nhận ${DIEP_MINH_ULTIMATE_PERCENT}% Max HP. ${pulse.summary}."
    } else {
      val incomingRoll = roll(c.copy(eventCounter = c.eventCounter + 31), 100)
      val defense = when (intent) { Intent.EVADE -> 34; Intent.GUARD -> 30; Intent.MOVE -> 18; Intent.READ -> 12; else -> 0 } +
        when (c.cover) { Cover.HARD -> 22; Cover.PARTIAL -> 10; Cover.EXPOSED -> 0 } + max(0, c.momentum) * 4
      val quickStepEvasion = if (quickStepTurns > 0) KAI_QUICK_STEP_EVASION_BONUS_PERCENT else 0
      val enemyChance = (profile.aggression * 8 - defense + max(0, -c.momentum) * 7 - quickStepEvasion - trueTurnEnemyAccuracyPenalty).coerceIn(0, 88)
      if (incomingRoll < enemyChance) {
        val damage = if (c.entityKey == DIEP_MINH_KEY) {
          percentDamage(c.playerMaxHp, DIEP_MINH_ATTACK_PERCENT)
        } else {
          max(1, profile.attack + roll(c.copy(eventCounter = c.eventCounter + 47), 7) - when (c.cover) { Cover.HARD -> 8; Cover.PARTIAL -> 4; Cover.EXPOSED -> 0 })
        }
        val hp = max(0, c.playerHp - damage)
        c = c.copy(playerHp = hp, momentum = max(-3, c.momentum - 1))
        log += if (c.entityKey == DIEP_MINH_KEY) {
          "Diệp Minh phản công: Kai -$damage HP (${DIEP_MINH_ATTACK_PERCENT}% Max HP; ${c.playerHp}/${c.playerMaxHp})."
        } else {
          "${c.entityName} phản công: Kai -$damage HP (${c.playerHp}/${c.playerMaxHp})."
        }
      } else {
        log += if (quickStepTurns > 0) {
          "Quick Step khiến ${c.entityName} hụt đòn; +${KAI_QUICK_STEP_EVASION_BONUS_PERCENT}% Evasion đang hoạt động."
        } else {
          "${c.entityName} không xuyên được thế phòng thủ/di chuyển của Kai."
        }
        if (!splitAuto && (irisActive && c.entityHp > 0 && roll(c.copy(eventCounter = c.eventCounter + 281), 100) < 15)) {
          val damage = companionSkillDamage(CharacterStatEngine.weaponDamage(resolvedState, IRIS_ID), 120, profile.armor)
          val hp = max(0, c.entityHp - damage)
          c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
          log += "Dead Angle: Iris phản kích tức thời 120% DMG = -$damage HP."
        }
        if (!splitAuto && (syvialActive && c.entityHp > 0 && roll(c.copy(eventCounter = c.eventCounter + 293), 100) < 30)) {
          val damage = companionSkillDamage(CharacterStatEngine.weaponDamage(resolvedState, SYVIAL_ID), if (syvialDevilTrigger) 157 else 125, profile.armor)
          val hp = max(0, c.entityHp - damage)
          c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
          log += "Counterphase: Syvial Spatial Shift vào góc chết và phản chém -$damage HP."
        }
      }
    }

    }

    if (splitAuto && autoActor.startsWith("entity:") && c.entityHp <= 0) {
      val cleared = clearDestroyedTrueTurnEntity(resolvedState, c)
      return Resolution(
        cleared,
        true,
        log.joinToString(" ") + " ${c.entityName} đã bị tiêu diệt.",
        entityDestroyed = true,
        roundCompleted = true
      )
    }

    if (splitAuto && autoActor.startsWith("entity:") && autoCursor < autoOrder.lastIndex) {
      val stagedState = encode(resolvedState, c)
      val staged = withAutoCursor(stagedState, nextAutoCursor(stagedState, autoOrder, autoCursor))
      return Resolution(staged, true, log.joinToString(" "), roundCompleted = false)
    }

    if (quickStepTurns > 0) {
      quickStepTurns = max(0, quickStepTurns - 1)
      resolvedState = withCombatCounter(resolvedState, KAI_QUICK_STEP_TURNS_KEY, quickStepTurns)
    }

    if (irisAnalyzedTurns > 0) {
      irisAnalyzedTurns = max(0, irisAnalyzedTurns - 1)
      resolvedState = withCombatCounter(resolvedState, IRIS_ANALYZED_TURNS_KEY, irisAnalyzedTurns)
    }
    if (irisArmorBreakTurns > 0) {
      irisArmorBreakTurns = max(0, irisArmorBreakTurns - 1)
      resolvedState = withCombatCounter(resolvedState, IRIS_ARMOR_BREAK_TURNS_KEY, irisArmorBreakTurns)
    }
    if (irisExposedTurns > 0) {
      irisExposedTurns = max(0, irisExposedTurns - 1)
      resolvedState = withCombatCounter(resolvedState, IRIS_EXPOSED_TURNS_KEY, irisExposedTurns)
    }
    if (syvialDisorientTurns > 0) {
      syvialDisorientTurns = max(0, syvialDisorientTurns - 1)
      resolvedState = withCombatCounter(resolvedState, SYVIAL_DISORIENT_TURNS_KEY, syvialDisorientTurns)
    }

    val entityHpBeforeRegen = c.entityHp
    val entityRegen = if (c.entityKey == DIEP_MINH_KEY) DIEP_MINH_REGEN_PER_TURN else ENTITY_REGEN_PER_TURN
    val entityHpAfterRegen = min(c.entityMaxHp, c.entityHp + entityRegen)
    if (entityHpAfterRegen > entityHpBeforeRegen) {
      c = c.copy(entityHp = entityHpAfterRegen, entityCondition = condition(entityHpAfterRegen, c.entityMaxHp))
      log += "${c.entityName} hồi +$entityRegen HP (${c.entityHp}/${c.entityMaxHp})."
    }

    c = c.copy(
      telegraph = telegraphFor(profile, c.seed, c.eventCounter),
      telegraphRevealed = false,
      opening = max(0, c.opening - if (intent == Intent.READ) 0 else 1)
    )
    if (splitAuto) {
      resolvedState = withAutoCursor(resolvedState, 0)
      resolvedState = withoutAutoRoundState(resolvedState)
    }
    val next = encode(resolvedState, c)
    return Resolution(next, true, log.joinToString(" "), roundCompleted = true)
  }

  fun toJson(state: GameState): JSONObject? = decode(state)?.let { c -> JSONObject().apply {
    put("active", c.phase == Phase.ACTIVE)
    put("auto", true)
    put("round", c.eventCounter)
    val order = autoTurnOrder(state)
    val cursor = currentAutoCursor(state, order)
    val actorId = order.getOrElse(cursor) { "kai" }
    put("autoCursor", cursor)
    put("autoOrder", JSONArray(order))
    put("activeActorId", actorId)
    put("activeActorName", autoActorName(state, c, actorId))
    put("activeActorSlot", autoActorSlot(state, actorId))
    put("activeActorTargetId", if (actorId.startsWith("entity:")) autoActorMemberId(actorId) else c.entityKey)
    put("activeActorOverlayUri", autoActorOverlay(actorId))
    put("autoMode", "TRUE_TURN_V2")
    put("encounterId", c.encounterId)
    put("entityKey", c.entityKey)
    put("entityName", c.entityName)
    put("playerHp", c.playerHp); put("playerMaxHp", c.playerMaxHp)
    put("entityHp", c.entityHp); put("entityMaxHp", c.entityMaxHp)
    put("entityCondition", c.entityCondition.name)
    put("range", c.range.name); put("cover", c.cover.name)
    put("momentum", c.momentum); put("opening", c.opening)
    put("escapeProgress", c.escapeProgress); put("noise", c.noise)
    put("telegraph", if (c.telegraphRevealed) c.telegraph else "UNKNOWN")
    put("telegraphRevealed", c.telegraphRevealed)
  } }

  fun clear(state: GameState): GameState = clearCombatOnly(state)

  private fun withCombatCounter(state: GameState, key: String, value: Int): GameState {
    val metadata = state.metadata.toMutableMap()
    if (value > 0) metadata[key] = value.toString() else metadata.remove(key)
    return state.copy(metadata = metadata)
  }

  private fun weaponSkillDamage(weaponDamage: Int, percent: Int, armor: Int): Int =
    max(1, ((max(1, weaponDamage) * percent + 99) / 100) - armor)

  private fun activePartyCharacter(state: GameState, characterId: String): CharacterState? {
    if (characterId !in state.party.memberIds) return null
    val character = state.characters[characterId] ?: return null
    return character.takeIf { it.presence == CharacterPresence.ACTIVE && it.vitalState.currentHp > 0 }
  }

  private fun companionSkillDamage(weaponDamage: Int, percent: Int, armor: Int): Int =
    max(1, ((max(1, weaponDamage) * percent + 99) / 100) - max(0, armor))

  private fun armorAfterIgnore(armor: Int, ignorePercent: Int): Int =
    max(0, armor - ((armor * ignorePercent + 99) / 100))

  private data class PartyPercentDamage(
    val state: GameState,
    val kaiHp: Int,
    val summary: String
  )

  private fun percentDamage(maxHp: Int, percent: Int): Int =
    max(1, (maxHp * percent + 99) / 100)

  private fun damageActivePartyByPercent(state: GameState, percent: Int): PartyPercentDamage {
    var next = state
    val lines = mutableListOf<String>()
    state.party.memberIds.distinct().forEach { characterId ->
      val character = next.characters[characterId] ?: return@forEach
      if (character.presence != CharacterPresence.ACTIVE || character.vitalState.currentHp <= 0) return@forEach
      val maxHp = CharacterStatEngine.effective(next, characterId).maxHp
      val damage = percentDamage(maxHp, percent)
      val before = character.vitalState.currentHp
      next = CharacterStatEngine.setCurrentHp(next, characterId, before - damage)
      val after = next.characters[characterId]?.vitalState?.currentHp ?: max(0, before - damage)
      lines += "${character.name} -$damage HP ($after/$maxHp)"
    }
    val kaiMaxHp = CharacterStatEngine.effective(next, KAI_ID).maxHp
    val kaiHp = next.characters[KAI_ID]?.vitalState?.currentHp?.coerceIn(0, kaiMaxHp) ?: kaiMaxHp
    return PartyPercentDamage(
      state = next,
      kaiHp = kaiHp,
      summary = if (lines.isEmpty()) "không có nhân vật ACTIVE hợp lệ để nhận sát thương" else lines.joinToString("; ")
    )
  }

  private fun encode(state: GameState, c: Snapshot): GameState {
    val metadata = state.metadata.toMutableMap()
    metadata["${PREFIX}encounterId"] = c.encounterId
    metadata["${PREFIX}entityKey"] = c.entityKey
    metadata["${PREFIX}entityName"] = c.entityName
    metadata["${PREFIX}phase"] = c.phase.name
    metadata["${PREFIX}entityHp"] = c.entityHp.toString()
    metadata["${PREFIX}entityMaxHp"] = c.entityMaxHp.toString()
    metadata["${PREFIX}entityCondition"] = c.entityCondition.name
    metadata["${PREFIX}range"] = c.range.name
    metadata["${PREFIX}cover"] = c.cover.name
    metadata["${PREFIX}momentum"] = c.momentum.toString()
    metadata["${PREFIX}opening"] = c.opening.toString()
    metadata["${PREFIX}escapeProgress"] = c.escapeProgress.toString()
    metadata["${PREFIX}noise"] = c.noise.toString()
    metadata["${PREFIX}telegraph"] = c.telegraph
    metadata["${PREFIX}telegraphRevealed"] = c.telegraphRevealed.toString()
    metadata["${PREFIX}eventCounter"] = c.eventCounter.toString()
    metadata["${PREFIX}seed"] = c.seed.toString()
    return CharacterStatEngine.setCurrentHp(state.copy(metadata = metadata), KAI_ID, c.playerHp)
  }

  private fun decode(state: GameState): Snapshot? {
    val m = state.metadata
    val key = m["${PREFIX}entityKey"]?.takeIf { it.isNotBlank() } ?: return null
    val profile = profiles[key] ?: return null
    val canonicalMaxHp = if (profile.key == DIEP_MINH_KEY) DIEP_MINH_MAX_HP else profile.maxHp + ENTITY_HP_BONUS
    val storedMaxHp = m["${PREFIX}entityMaxHp"]?.toIntOrNull()?.coerceAtLeast(1) ?: canonicalMaxHp
    val maxHp = max(storedMaxHp, canonicalMaxHp)
    val storedHp = m["${PREFIX}entityHp"]?.toIntOrNull()?.coerceIn(0, storedMaxHp) ?: storedMaxHp
    val hp = if (storedMaxHp < canonicalMaxHp) min(maxHp, storedHp + (canonicalMaxHp - storedMaxHp)) else storedHp.coerceIn(0, maxHp)
    val playerMax = CharacterStatEngine.effective(state, KAI_ID).maxHp
    val playerHp = state.characters[KAI_ID]?.vitalState?.currentHp?.coerceIn(0, playerMax) ?: playerMax
    return Snapshot(
      encounterId = m["${PREFIX}encounterId"].orEmpty(),
      entityKey = key,
      entityName = m["${PREFIX}entityName"] ?: profile.displayName,
      phase = enumOr(Phase.ACTIVE, m["${PREFIX}phase"]),
      playerHp = playerHp,
      playerMaxHp = playerMax,
      entityHp = hp,
      entityMaxHp = maxHp,
      entityCondition = condition(hp, maxHp),
      range = enumOr(RangeBand.NEAR, m["${PREFIX}range"]),
      cover = enumOr(Cover.EXPOSED, m["${PREFIX}cover"]),
      momentum = m["${PREFIX}momentum"]?.toIntOrNull()?.coerceIn(-3, 3) ?: 0,
      opening = m["${PREFIX}opening"]?.toIntOrNull()?.coerceIn(0, 3) ?: 0,
      escapeProgress = m["${PREFIX}escapeProgress"]?.toIntOrNull()?.coerceIn(0, 100) ?: 0,
      noise = m["${PREFIX}noise"]?.toIntOrNull()?.coerceIn(0, 100) ?: 0,
      telegraph = m["${PREFIX}telegraph"] ?: telegraphFor(profile, stableSeed(key, state.turn.currentTurnId, state.time.elapsedSubjectiveMinutes), 0),
      telegraphRevealed = m["${PREFIX}telegraphRevealed"].toBoolean(),
      eventCounter = m["${PREFIX}eventCounter"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
      seed = m["${PREFIX}seed"]?.toLongOrNull() ?: stableSeed(key, state.turn.currentTurnId, state.time.elapsedSubjectiveMinutes)
    )
  }

  private fun clearCombatOnly(state: GameState): GameState {
    val metadata = state.metadata.filterKeys { !it.startsWith(PREFIX) }
    return state.copy(metadata = metadata)
  }

  private fun classify(actionKind: String, raw: String): Intent {
    val text = raw.lowercase()
    if (containsAny(text, "bắn", "đánh", "chém", "đâm", "tấn công", "shoot", "attack", "fire")) return Intent.ATTACK
    if (containsAny(text, "né", "lách", "dodge", "evade", "tránh")) return Intent.EVADE
    if (containsAny(text, "chạy thoát", "bỏ chạy", "thoát", "escape", "flee")) return Intent.ESCAPE
    if (containsAny(text, "thủ", "đỡ", "chặn", "guard", "block", "cover")) return Intent.GUARD
    if (actionKind.equals("SEARCH", true) || containsAny(text, "quan sát", "đọc", "nhìn kỹ", "theo dõi", "observe", "read")) return Intent.READ
    if (actionKind.equals("EXPLORE", true) || containsAny(text, "lùi", "tiến", "di chuyển", "núp", "vòng", "move", "reposition")) return Intent.MOVE
    return Intent.OTHER
  }

  private fun containsAny(text: String, vararg needles: String) = needles.any(text::contains)

  private fun condition(hp: Int, maxHp: Int): EntityCondition {
    if (hp <= 0) return EntityCondition.DESTROYED
    val ratio = hp.toDouble() / maxHp.toDouble()
    return when {
      ratio > .75 -> EntityCondition.HEALTHY
      ratio > .50 -> EntityCondition.HURT
      ratio > .25 -> EntityCondition.WOUNDED
      else -> EntityCondition.CRITICAL
    }
  }

  private fun telegraphFor(profile: Profile, seed: Long, counter: Int): String {
    val options = when {
      profile.key == "smiler" -> listOf("STALK", "RUSH", "VANISH")
      profile.key == "cable_mimic" -> listOf("GRAB", "LUNGE", "FLANK")
      profile.key == "slenderman" -> listOf("STALK", "GRAB", "RUSH")
      else -> listOf("LUNGE", "GRAB", "RUSH", "FLANK")
    }
    return options[positiveMod(mix(seed, counter), options.size)]
  }

  private fun roll(c: Snapshot, bound: Int): Int = positiveMod(mix(c.seed, c.eventCounter), bound)
  private fun stableSeed(entityKey: String, turnId: String, time: Long): Long = mix(entityKey.hashCode().toLong() * 31L + turnId.hashCode(), time.toInt())
  private fun mix(seed: Long, counter: Int): Long {
    var x = seed xor (counter.toLong() * -7046029254386353131L)
    x = (x xor (x ushr 30)) * -4658895280553007687L
    x = (x xor (x ushr 27)) * -7723592293110705685L
    return x xor (x ushr 31)
  }
  private fun positiveMod(value: Long, bound: Int): Int = ((value and Long.MAX_VALUE) % bound.toLong()).toInt()
  private inline fun <reified T : Enum<T>> enumOr(fallback: T, raw: String?): T = enumValues<T>().firstOrNull { it.name == raw } ?: fallback
}

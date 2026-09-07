package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Authoritative, save-persistent combat state stored in GameState.metadata. */
object CombatRuntime {
  private const val PREFIX = "combat."
  private const val PLAYER_HP = "combat.playerHp"
  private const val PLAYER_MAX_HP = "combat.playerMaxHp"
  private const val MAX_PARTY_SLOTS = 4

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

  data class CombatEvent(
    val actorId: String,
    val actorName: String,
    val side: String,
    val text: String,
    val overlayRef: String = "",
    val entityHp: Int,
    val entityMaxHp: Int,
    val playerHp: Int,
    val playerMaxHp: Int
  ) {
    fun toJson(): JSONObject = JSONObject().apply {
      put("actorId", actorId)
      put("actorName", actorName)
      put("side", side)
      put("text", text)
      put("overlayRef", overlayRef)
      put("entityHp", entityHp)
      put("entityMaxHp", entityMaxHp)
      put("playerHp", playerHp)
      put("playerMaxHp", playerMaxHp)
    }
  }

  data class Resolution(
    val state: GameState,
    val handled: Boolean,
    val reply: String = "",
    val events: List<CombatEvent> = emptyList(),
    val entityDestroyed: Boolean = false,
    val escaped: Boolean = false
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
    Profile("slenderman", "Slenderman", 160, 23, 8, 10)
  ).associateBy { it.key }

  fun active(state: GameState): Snapshot? = decode(state)?.takeIf { it.phase == Phase.ACTIVE }

  fun start(state: GameState, entityKey: String): GameState {
    if (active(state) != null) return state
    val normalizedKey = normalizeEntityKey(entityKey)
    val profile = profiles[normalizedKey] ?: return state
    val playerMax = state.metadata[PLAYER_MAX_HP]?.toIntOrNull()?.coerceIn(1, 999) ?: 100
    val playerHp = state.metadata[PLAYER_HP]?.toIntOrNull()?.coerceIn(0, playerMax) ?: playerMax
    val seed = stableSeed(normalizedKey, state.turn.currentTurnId, state.time.elapsedSubjectiveMinutes)
    val snapshot = Snapshot(
      encounterId = "${state.turn.currentTurnId}:$normalizedKey:${abs(seed)}",
      entityKey = normalizedKey,
      entityName = profile.displayName,
      phase = Phase.ACTIVE,
      playerHp = playerHp,
      playerMaxHp = playerMax,
      entityHp = profile.maxHp,
      entityMaxHp = profile.maxHp,
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

  /**
   * Resolves one player-submitted combat round.
   * Turn order is always Kai -> Entity -> each real party member -> Entity.
   * Empty UI slots never become actors.
   */
  fun resolve(state: GameState, actionKind: String, action: String): Resolution {
    val current = active(state) ?: return Resolution(state, handled = false)
    val profile = profiles[current.entityKey] ?: return Resolution(clear(state), handled = false)
    val intent = classify(actionKind, action)
    val participants = activePartyMembers(state)
    var c = current.copy(eventCounter = current.eventCounter + 1)
    val events = mutableListOf<CombatEvent>()

    val kaiText: String
    when (intent) {
      Intent.READ -> {
        c = c.copy(
          telegraphRevealed = true,
          opening = min(3, c.opening + 1),
          momentum = min(3, c.momentum + 1)
        )
        kaiText = "Kai quan sát ${c.entityName} • Opening +1."
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
        kaiText = "Kai né đòn • Escape ${c.escapeProgress}%."
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
        kaiText = "Kai đổi vị trí • ${c.range.name} • Cover ${c.cover.name}."
      }
      Intent.GUARD -> {
        c = c.copy(cover = Cover.HARD, momentum = min(3, c.momentum + 1), opening = min(3, c.opening + 1))
        kaiText = "Kai phòng thủ • Cover HARD."
      }
      Intent.ESCAPE -> {
        val gain = 20 + c.momentum.coerceAtLeast(0) * 5 + when (c.cover) { Cover.HARD -> 15; Cover.PARTIAL -> 8; Cover.EXPOSED -> 0 }
        c = c.copy(escapeProgress = min(100, c.escapeProgress + gain), momentum = min(3, c.momentum + 1))
        kaiText = "Kai tìm đường thoát • ${c.escapeProgress}%."
      }
      Intent.ATTACK -> {
        val attack = resolvePartyAttack(c, profile, KAI_ID, isKai = true)
        c = attack.first
        kaiText = attack.second
      }
      Intent.OTHER -> {
        c = c.copy(momentum = max(-3, c.momentum - 1))
        kaiText = "Kai chưa tạo được lợi thế rõ ràng."
      }
    }
    events += combatEvent(state, c, KAI_ID, "party", kaiText)

    if (c.entityHp <= 0) return destroyedResult(state, c, events)
    if (c.escapeProgress >= 100) return escapedResult(state, c, events)

    c = c.copy(eventCounter = c.eventCounter + 1)
    val firstEntityTurn = resolveEntityTurn(c, profile, intent)
    c = firstEntityTurn.first
    events += combatEvent(state, c, "entity:${c.entityKey}", "entity", firstEntityTurn.second)

    if (c.playerHp <= 0) {
      val next = encode(state, c)
      return Resolution(next, true, events.joinToString(" ") { it.text }, events)
    }

    participants.drop(1).forEach { actorId ->
      if (c.entityHp <= 0 || c.playerHp <= 0) return@forEach
      c = c.copy(eventCounter = c.eventCounter + 1)
      val attack = resolvePartyAttack(c, profile, actorId, isKai = false)
      c = attack.first
      events += combatEvent(state, c, actorId, "party", attack.second)
      if (c.entityHp <= 0) return@forEach

      c = c.copy(eventCounter = c.eventCounter + 1)
      val entityTurn = resolveEntityTurn(c, profile, Intent.OTHER)
      c = entityTurn.first
      events += combatEvent(state, c, "entity:${c.entityKey}", "entity", entityTurn.second)
    }

    if (c.entityHp <= 0) return destroyedResult(state, c, events)

    val next = encode(state, c)
    return Resolution(next, true, events.joinToString(" ") { it.text }, events)
  }

  fun toJson(state: GameState): JSONObject? = decode(state)?.let { c -> JSONObject().apply {
    val participants = activePartyMembers(state)
    put("active", c.phase == Phase.ACTIVE)
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
    put("activeActorId", KAI_ID)
    put("participantCount", participants.size)
    put("turnOrder", JSONArray().apply {
      participants.forEach { id ->
        put(id)
        put("entity:${c.entityKey}")
      }
    })
    put("slots", JSONArray().apply {
      repeat(MAX_PARTY_SLOTS) { index ->
        val id = participants.getOrNull(index)
        put(JSONObject().apply {
          put("slot", index)
          put("occupied", id != null)
          if (id != null) {
            put("id", id)
            put("name", combatActorName(state, id))
            put("overlayRef", combatOverlayRef(state, id))
          } else {
            put("id", "")
            put("name", "")
            put("overlayRef", "")
          }
        })
      }
    })
  } }

  fun clear(state: GameState): GameState = clearCombatOnly(state)

  private fun resolvePartyAttack(c: Snapshot, profile: Profile, actorId: String, isKai: Boolean): Pair<Snapshot, String> {
    val rangeBonus = when (c.range) { RangeBand.CLOSE -> 18; RangeBand.NEAR -> 10; RangeBand.FAR -> -5 }
    val hitChance = if (isKai) {
      (58 + rangeBonus + c.opening * 11 + c.momentum * 6).coerceIn(20, 96)
    } else {
      (62 + rangeBonus / 2 + c.opening * 8 + c.momentum * 4).coerceIn(24, 94)
    }
    val hitRoll = roll(c, 100)
    val actorName = if (actorId == KAI_ID) "Kai" else actorId
    if (hitRoll >= hitChance) {
      val next = c.copy(
        momentum = max(-3, c.momentum - if (isKai) 1 else 0),
        opening = max(0, c.opening - 1),
        noise = min(100, c.noise + if (isKai) 28 else 20)
      )
      return next to "$actorName đánh trượt ${c.entityName}."
    }

    val variance = if (isKai) 4 + roll(c.copy(eventCounter = c.eventCounter + 17), 9) else 3 + roll(c.copy(eventCounter = c.eventCounter + 19), 7)
    val base = if (isKai) 18 + variance + c.opening * 7 + max(0, c.momentum) * 3 else 15 + variance + c.opening * 4 + max(0, c.momentum) * 2
    val damage = max(1, base - profile.armor)
    val hp = max(0, c.entityHp - damage)
    val next = c.copy(
      entityHp = hp,
      entityCondition = condition(hp, c.entityMaxHp),
      momentum = min(3, c.momentum + 1),
      opening = max(0, c.opening - 1),
      noise = min(100, c.noise + if (isKai) 35 else 24)
    )
    return next to "$actorName đánh trúng ${c.entityName} • -$damage HP (${next.entityHp}/${next.entityMaxHp})."
  }

  private fun resolveEntityTurn(c: Snapshot, profile: Profile, defenseIntent: Intent): Pair<Snapshot, String> {
    val incomingRoll = roll(c.copy(eventCounter = c.eventCounter + 31), 100)
    val defense = when (defenseIntent) { Intent.EVADE -> 34; Intent.GUARD -> 30; Intent.MOVE -> 18; Intent.READ -> 12; else -> 0 } +
      when (c.cover) { Cover.HARD -> 22; Cover.PARTIAL -> 10; Cover.EXPOSED -> 0 } + max(0, c.momentum) * 4
    val enemyChance = (profile.aggression * 8 - defense + max(0, -c.momentum) * 7).coerceIn(8, 88)
    var next = c
    val text = if (incomingRoll < enemyChance) {
      val damage = max(1, profile.attack + roll(c.copy(eventCounter = c.eventCounter + 47), 7) - when (c.cover) { Cover.HARD -> 8; Cover.PARTIAL -> 4; Cover.EXPOSED -> 0 })
      val hp = max(0, c.playerHp - damage)
      next = c.copy(playerHp = hp, momentum = max(-3, c.momentum - 1))
      "${c.entityName} đánh trúng Kai • -$damage HP (${next.playerHp}/${next.playerMaxHp})."
    } else {
      "${c.entityName} đánh trượt."
    }
    next = next.copy(
      telegraph = telegraphFor(profile, next.seed, next.eventCounter),
      telegraphRevealed = false,
      opening = max(0, next.opening - if (defenseIntent == Intent.READ) 0 else 1)
    )
    return next to text
  }

  private fun destroyedResult(state: GameState, c: Snapshot, events: MutableList<CombatEvent>): Resolution {
    if (events.isNotEmpty()) {
      val last = events.last()
      events[events.lastIndex] = last.copy(text = last.text + " • ${c.entityName} bị tiêu diệt.")
    }
    val persisted = encode(state, c.copy(phase = Phase.RESOLVED, entityCondition = EntityCondition.DESTROYED))
    val cleared = clearCombatOnly(persisted)
    return Resolution(cleared, true, events.joinToString(" ") { it.text }, events, entityDestroyed = true)
  }

  private fun escapedResult(state: GameState, c: Snapshot, events: MutableList<CombatEvent>): Resolution {
    if (events.isNotEmpty()) {
      val last = events.last()
      events[events.lastIndex] = last.copy(text = last.text + " • Thoát khỏi encounter.")
    }
    val persisted = encode(state, c.copy(phase = Phase.RESOLVED))
    val cleared = clearCombatOnly(persisted)
    return Resolution(cleared, true, events.joinToString(" ") { it.text }, events, escaped = true)
  }

  private fun combatEvent(state: GameState, c: Snapshot, actorId: String, side: String, text: String): CombatEvent {
    val entityActor = side == "entity"
    val actorName = if (entityActor) c.entityName else combatActorName(state, actorId)
    val overlay = if (entityActor) "entity/${c.entityKey.replace('-', '_')}.png" else combatOverlayRef(state, actorId)
    return CombatEvent(
      actorId = actorId,
      actorName = actorName,
      side = side,
      text = if (side == "party" && actorId != KAI_ID) replaceActorIdPrefix(text, actorId, actorName) else text,
      overlayRef = overlay,
      entityHp = c.entityHp,
      entityMaxHp = c.entityMaxHp,
      playerHp = c.playerHp,
      playerMaxHp = c.playerMaxHp
    )
  }

  private fun replaceActorIdPrefix(text: String, actorId: String, actorName: String): String =
    if (text.startsWith(actorId)) actorName + text.removePrefix(actorId) else text

  private fun activePartyMembers(state: GameState): List<String> {
    val limit = state.party.maxMembers.coerceIn(1, MAX_PARTY_SLOTS)
    return (listOf(KAI_ID) + state.party.memberIds)
      .distinct()
      .filter { id -> id == KAI_ID || state.characters[id]?.presence == CharacterPresence.ACTIVE }
      .take(limit)
  }

  private fun combatActorName(state: GameState, actorId: String): String {
    if (actorId == KAI_ID) return "Kai"
    val raw = state.characters[actorId]?.name?.trim().orEmpty()
    if (raw.isBlank()) return actorId
    return raw.substringBefore(" \"").ifBlank { raw }
  }

  private fun combatOverlayRef(state: GameState, actorId: String): String {
    if (actorId == KAI_ID) return "kai_entity_overlay.png"
    return state.characters[actorId]?.metadata?.get("combatOverlay")?.trim().orEmpty()
  }

  private fun encode(state: GameState, c: Snapshot): GameState {
    val metadata = state.metadata.toMutableMap()
    metadata["${PREFIX}encounterId"] = c.encounterId
    metadata["${PREFIX}entityKey"] = c.entityKey
    metadata["${PREFIX}entityName"] = c.entityName
    metadata["${PREFIX}phase"] = c.phase.name
    metadata[PLAYER_HP] = c.playerHp.toString()
    metadata[PLAYER_MAX_HP] = c.playerMaxHp.toString()
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
    return state.copy(metadata = metadata)
  }

  private fun decode(state: GameState): Snapshot? {
    val m = state.metadata
    val key = m["${PREFIX}entityKey"]?.takeIf { it.isNotBlank() } ?: return null
    val profile = profiles[key] ?: return null
    val maxHp = m["${PREFIX}entityMaxHp"]?.toIntOrNull()?.coerceAtLeast(1) ?: profile.maxHp
    val hp = m["${PREFIX}entityHp"]?.toIntOrNull()?.coerceIn(0, maxHp) ?: maxHp
    val playerMax = m[PLAYER_MAX_HP]?.toIntOrNull()?.coerceAtLeast(1) ?: 100
    return Snapshot(
      encounterId = m["${PREFIX}encounterId"].orEmpty(),
      entityKey = key,
      entityName = m["${PREFIX}entityName"] ?: profile.displayName,
      phase = enumOr(Phase.ACTIVE, m["${PREFIX}phase"]),
      playerHp = m[PLAYER_HP]?.toIntOrNull()?.coerceIn(0, playerMax) ?: playerMax,
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
    val preservedHp = state.metadata[PLAYER_HP]
    val preservedMax = state.metadata[PLAYER_MAX_HP]
    val metadata = state.metadata.filterKeys { !it.startsWith(PREFIX) }.toMutableMap()
    if (preservedHp != null) metadata[PLAYER_HP] = preservedHp
    if (preservedMax != null) metadata[PLAYER_MAX_HP] = preservedMax
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

  private fun normalizeEntityKey(raw: String): String {
    val direct = raw.trim().lowercase()
    if (profiles.containsKey(direct)) return direct
    val key = direct.replace('-', '_').replace(' ', '_')
    return when (key) {
      "skin_stealer", "skinstealer" -> "skin-stealer"
      "faceling", "hostilefaceling" -> "hostile_faceling"
      "death_moth" -> "deathmoth"
      "falsepuddle" -> "false_puddle"
      "cablemimic" -> "cable_mimic"
      "beast_of_level_5", "thebeastoflevel5" -> "the_beast_of_level_5"
      "jeff", "jeffthekiller" -> "jeff_the_killer"
      "jane", "janethekiller" -> "jane_the_killer"
      else -> key
    }
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

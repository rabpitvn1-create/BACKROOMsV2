package com.rabpit.backroom.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal fun synchronizeValidatedLuciaCharacter(state: GameState, candidate: JSONObject): GameState {
  val flags = candidate.optJSONObject("flags") ?: return state
  val storyArc = flags.optJSONObject("storyArc") ?: return state
  val completed = storyArc.optJSONArray("completed") ?: return state
  fun hasBeat(beat: String): Boolean = (0 until completed.length()).any { completed.optString(it) == beat }

  val encounter = flags.optJSONObject("luciaEncounter") ?: return state
  val status = encounter.optString("status", "").trim().lowercase()
  val firstContact = hasBeat(StoryProgressionPolicy.LEVEL0_FIRST_CONTACT) &&
    encounter.optInt("level", -1) == 0 && status in setOf("met", "contact", "contacted", "first_contact", "first-contact", "joined")
  if (!firstContact) return state

  val joined = hasBeat(StoryProgressionPolicy.LEVEL0_LUCIA_DECISION_COMPLETE) &&
    status == "joined" && encounter.optBoolean("partyEligible", false) && !encounter.optBoolean("joinPending", true)
  val existing = state.characters["lucia"]
  val lucia = (existing ?: CharacterState("lucia", "Lucia")).copy(
    presence = CharacterPresence.ACTIVE,
    metadata = existing?.metadata.orEmpty() + mapOf(
      "storyManaged" to "lucia",
      "joinEligible" to joined.toString(),
    )
  )
  return state.copy(
    characters = state.characters + ("lucia" to lucia),
    inventories = if ("lucia" in state.inventories) state.inventories else state.inventories + ("lucia" to InventoryState("lucia")),
    equipment = if ("lucia" in state.equipment) state.equipment else state.equipment + ("lucia" to EquipmentState("lucia")),
  )
}

class GameCoreFacade private constructor(
  private val repository: SaveRepository,
  private val logger: GamePipelineLogger,
  private val localModel: LiteRTIntentInterpreter
) : AutoCloseable {
  private val rules = RuleIntentInterpreter()
  private val resolver = CommandResolver()

  /** Fast deterministic pass. Gemini is never called from this method. */
  fun processRule(legacyStateJson: String, action: String): String {
    val legacy = JSONObject(legacyStateJson)
    val state = loadOrMigrate(legacy)
    if (MadGodCanon.cheat(action)) return applyMadGodCheat(legacy,state)
    if (AnNhienCanon.matchesPartyCheatCode(action)) return applyAnNhienPartyCheat(legacy, state)
    SpecialFollowersCanon.matchesPartyCheatCode(action)?.let { targetId ->
      return applySpecialFollowerPartyCheat(legacy, state, targetId)
    }
    val turnId = nextTurnId(legacy, state)
    logger.log(PipelineLogEvent("INPUT", turnId = turnId, details = mapOf("length" to action.length.toString())))
    val pending = TurnCoordinator.createPending(state, turnId, action)
    if (pending.error != null) return response(false, legacy, pending.error, "pending_rejected")
    if (isMadGodEquipRequest(action)) {
      val owned = pending.state.inventories[KAI_ID]?.items?.containsKey(MADGOD_SET_ID) == true
      if (!owned) {
        val result = syncLegacy(legacy, pending.state, incrementTurn = false)
        val reply = validationReply("item_not_owned")
        appendLog(result, action, reply)
        return response(true, result, "item_not_owned", "validation_rejected", reply)
      }
      val command = ItemCommand(
        commandId = "$turnId:MADGOD:EQUIP",
        turnId = turnId,
        actorId = KAI_ID,
        source = CommandSource.RULE,
        operation = ItemCommand.Operation.EQUIP,
        itemId = MADGOD_SET_ID,
        itemName = MadGodCanon.SET_NAME,
        quantity = 1,
        slot = "weapon"
      )
      val committed = commitActionRuntime(pending.state, mutableListOf(command), action, turnId)
      if (committed.error != null) {
        val rejected = TurnCoordinator.reject(pending.state, committed.error)
        repository.save(rejected.state)
        val result = syncLegacy(legacy, rejected.state, incrementTurn = true)
        val reply = validationReply(committed.error)
        appendLog(result, action, reply)
        return response(true, result, committed.error, "validation_rejected", reply)
      }
      repository.save(committed.state)
      val result = syncLegacy(legacy, committed.state, incrementTurn = true)
      val reply = "MadGod Set đã ghi đè White Wraith Magnum và Blackblood Armor của Kai. Omnivault Ring được giữ nguyên."
      appendLog(result, action, reply)
      return response(true, result, null, "madgod_equipped", reply)
    }
    val context = contextFor(pending.state)
    val ruleResult = rules.interpretSync(action, context)
    val candidates = ruleResult.candidates.map { candidate ->
      if (candidate.confidence == IntentConfidence.HIGH || candidate.intent == GameIntent.NO_ACTION) candidate
      else localModel.interpretSync(candidate.clause, context).candidates.singleOrNull() ?: candidate
    }
    val interpreted = IntentResult(candidates, candidates.any { it.confidence != IntentConfidence.HIGH && it.intent != GameIntent.NO_ACTION })
    interpreted.candidates.forEach { logger.log(PipelineLogEvent("INTENT", turnId = turnId, source = it.source, intent = it.intent, confidence = it.score)) }

    val retiredOmnivaultIntent = interpreted.candidates.any {
      it.intent == GameIntent.OMNIVAULT_SCAN || it.intent == GameIntent.OMNIVAULT_COPY
    }
    if (retiredOmnivaultIntent) {
      abortAction("omnivault_operation_retired")
      val current = repository.load()
      val result = syncLegacy(legacy, current, incrementTurn = false)
      val reply = validationReply("omnivault_operation_retired")
      appendLog(result, action, reply)
      logger.log(PipelineLogEvent("REJECT", turnId = turnId, details = mapOf("reason" to "omnivault_operation_retired")))
      return response(true, result, "omnivault_operation_retired", "validation_rejected", reply)
    }

    // Player text never has authority to manufacture an acquisition event. Reject immediately,
    // do not call Gemini, do not advance the turn, and do not mutate Inventory.
    if (isDirectPlayerPickupAction(action)) {
      val result = syncLegacy(legacy, state, incrementTurn = false)
      val reply = validationReply("player_pickup_unavailable")
      appendLog(result, action, reply)
      logger.log(PipelineLogEvent("REJECT", turnId = turnId, details = mapOf("reason" to "player_pickup_unavailable")))
      return response(true, result, "player_pickup_unavailable", "validation_rejected", reply)
    }

    if (isDirectPlayerPickupAction(action) || interpreted.candidates.any { it.intent == GameIntent.PICKUP_ITEM }) {
      abortAction("player_pickup_unavailable")
      val current = repository.load()
      val result = syncLegacy(legacy, current, incrementTurn = false)
      val reply = validationReply("player_pickup_unavailable")
      appendLog(result, action, reply)
      logger.log(PipelineLogEvent("REJECT", turnId = turnId, details = mapOf("reason" to "player_pickup_unavailable")))
      return response(true, result, "player_pickup_unavailable", "validation_rejected", reply)
    }

    val uiOnlyItemIntent = interpreted.candidates.firstOrNull {
      it.intent in setOf(GameIntent.USE_ITEM, GameIntent.TRANSFER_ITEM, GameIntent.DROP_ITEM)
    }
    if (uiOnlyItemIntent != null) {
      abortAction("inventory_ui_required")
      val current = repository.load()
      val result = syncLegacy(legacy, current, incrementTurn = false)
      val reply = validationReply("inventory_ui_required")
      appendLog(result, action, reply)
      logger.log(PipelineLogEvent("REJECT", turnId = turnId, details = mapOf("reason" to "inventory_ui_required")))
      return response(true, result, "inventory_ui_required", "validation_rejected", reply)
    }

    // Restore is lore/narrative-only. Route prose to the GM, but authoritative state mutation is
    // explicitly suppressed again in processValidatedCandidate().
    if (interpreted.candidates.any { it.intent == GameIntent.OMNIVAULT_RESTORE }) {
      return response(false, legacy, null, "fallback_required")
    }

    if (interpreted.candidates.any { it.intent == GameIntent.NO_ACTION || it.confidence != IntentConfidence.HIGH }) {
      return response(false, legacy, null, "fallback_required")
    }
    val resolvedCommands = resolver.resolveSequence(interpreted.candidates, turnId, context).filterNotNull()
    if (resolvedCommands.size != interpreted.candidates.size || resolvedCommands.isEmpty()) return response(false, legacy, null, "resolution_incomplete")
    val commands = resolvedCommands.toMutableList()
    commands.forEach { logger.log(PipelineLogEvent("COMMAND", turnId, it.commandId, it.source)) }
    val committed = commitActionRuntime(pending.state, commands, action, turnId)
    if (committed.error != null) {
      val rejected = TurnCoordinator.reject(pending.state, committed.error)
      repository.save(rejected.state)
      val result = syncLegacy(legacy, rejected.state, incrementTurn = true)
      appendLog(result, action, validationReply(committed.error))
      return response(true, result, committed.error, "validation_rejected", validationReply(committed.error))
    }
    repository.save(committed.state)
    val result = syncLegacy(legacy, committed.state, incrementTurn = true)
    val reply = eventReply(committed.execution?.events.orEmpty())
    appendLog(result, action, reply)
    logger.log(PipelineLogEvent("COMMIT", turnId = turnId, details = mapOf("commands" to commands.size.toString())))
    return response(true, result, null, "committed", reply)
  }

  private fun applyAnNhienPartyCheat(legacy: JSONObject, state: GameState): String {
    val alreadyFollowing = AnNhienCanon.isFollowing(state)
    val (updated, error) = AnNhienCanon.forceIntoParty(state)
    val result = syncLegacy(legacy, updated, incrementTurn = false)
    val reply = when {
      error == "party_full" -> "Party đã đủ tối đa bốn thành viên; không thể thêm An Nhiên nếu chưa có chỗ trống."
      alreadyFollowing -> "An Nhiên đã ở trong Party."
      else -> "An Nhiên đã được thêm vào Party."
    }

    if (error == null) repository.save(updated)
    val log = result.optJSONArray("log") ?: JSONArray().also { result.put("log", it) }
    log.put(JSONObject().put("role", "gm").put("text", reply))
    logger.log(PipelineLogEvent(
      if (error == null) "CHEAT_COMMIT" else "CHEAT_REJECT",
      details = mapOf("command" to "an_nhien_party", "reason" to (error ?: "committed"))
    ))
    return response(
      handled = true,
      state = result,
      error = error,
      reason = if (error == null) "cheat_committed" else "cheat_rejected",
      reply = reply
    )
  }

  private fun applySpecialFollowerPartyCheat(legacy: JSONObject, state: GameState, targetId: String): String {
    val ensured = SpecialFollowersCanon.ensure(state)
    val displayName = ensured.characters[targetId]?.name ?: targetId
    val alreadyFollowing = targetId in ensured.party.memberIds
    val (updated, error) = SpecialFollowersCanon.forceIntoParty(ensured, targetId)
    val result = syncLegacy(legacy, updated, incrementTurn = false)
    val reply = when {
      error == "party_full" -> "Party đã đủ tối đa bốn thành viên; không thể thêm $displayName nếu chưa có chỗ trống."
      alreadyFollowing -> "$displayName đã ở trong Party."
      else -> "$displayName đã được thêm vào Party."
    }

    if (error == null) repository.save(updated)
    val log = result.optJSONArray("log") ?: JSONArray().also { result.put("log", it) }
    log.put(JSONObject().put("role", "gm").put("text", reply))
    logger.log(PipelineLogEvent(
      if (error == null) "CHEAT_COMMIT" else "CHEAT_REJECT",
      details = mapOf(
        "command" to if (targetId == IRIS_ID) "iris_party" else "syvial_party",
        "reason" to (error ?: "committed")
      )
    ))
    return response(
      handled = true,
      state = result,
      error = error,
      reason = if (error == null) "cheat_committed" else "cheat_rejected",
      reply = reply
    )
  }

  private fun applyMadGodCheat(legacy:JSONObject,state:GameState):String {
    val x=MadGodCanon.spawn(state); repository.save(x.state); val out=syncLegacy(legacy,x.state,incrementTurn=false);
    val flags=out.optJSONObject("flags")?:JSONObject().also{out.put("flags",it)}; flags.put("madGod",JSONObject().put("spawned",true).put("spawnSource","cheat").put("scalingMode",MadGodCanon.SCALING_MODE));
    val msg=if(x.added) "MadGod Set đã xuất hiện trong Inventory. Đây là một set duy nhất: trang bị một lần sẽ kích hoạt đồng thời MadGod Armor và MadGod Magnum, rồi khóa vĩnh viễn." else "MadGod Set đã tồn tại; /madgod không tạo bản sao thứ hai."; appendLog(out,MadGodCanon.CHEAT_CODE,msg); return response(true,out,null,"cheat_committed",msg)
  }

  fun beginAction(legacyStateJson: String, kindRaw: String, action: String): String {
    val legacy = JSONObject(legacyStateJson)
    val state = loadOrMigrate(legacy)
    if (MadGodCanon.cheat(action)) return actionStartResponse(true,null,null)
    val kind = enumValues<ActionKind>().firstOrNull { it.name == kindRaw.trim().uppercase() }
      ?: return actionStartResponse(false, null, "action_kind_invalid")
    val existing = ActionRuntime.activeSession(state)
    if (existing != null) {
      return if (existing.kind == kind && existing.input == action) actionStartResponse(true, existing, null)
      else actionStartResponse(false, existing, "action_session_already_active")
    }
    val turnId = nextTurnId(legacy, state)
    val sessionId = "$turnId:${kind.name}:${action.hashCode().toUInt()}"
    val started = ActionRuntime.start(
      state = state,
      sessionId = sessionId,
      turnId = turnId,
      actorId = KAI_ID,
      kind = kind,
      input = action,
      locationKey = state.world["location"] ?: legacy.optString("location").takeIf(String::isNotBlank),
      plannedMinutes = TimeCostPolicy.estimateMinutes(action),
      searchDepth = if (kind == ActionKind.SEARCH) SearchDepth.NORMAL else null
    )
    if (!started.applied) return actionStartResponse(false, started.session, started.error ?: "action_start_failed")
    repository.save(started.state)
    return actionStartResponse(true, started.session, null)
  }

  fun currentActionContext(): String {
    val state = repository.load()
    val active = ActionRuntime.activeSession(state)
    return JSONObject().apply {
      put("active", active != null)
      if (active != null) {
        put("sessionId", active.sessionId)
        put("turnId", active.turnId)
        put("kind", active.kind.name)
        put("phase", active.phase.name)
        put("location", active.locationKey ?: JSONObject.NULL)
        put("elapsedMinutes", active.elapsedMinutes)
        put("plannedMinutes", active.plannedMinutes ?: JSONObject.NULL)
        put("searchDepth", active.searchDepth?.name ?: JSONObject.NULL)
        if (active.kind == ActionKind.SEARCH && !active.locationKey.isNullOrBlank()) {
          put("searchCoverage", JSONArray(ActionRuntime.searchCoverage(state, active.locationKey).sorted()))
        }
      }
    }.toString()
  }

  fun abortAction(reason: String): Boolean {
    if (!repository.exists()) return false
    val state = repository.load()
    val active = ActionRuntime.activeSession(state) ?: return false
    val interrupted = ActionRuntime.interrupt(state, active.sessionId, reason.ifBlank { "pipeline_error" })
    if (!interrupted.applied) return false
    repository.save(interrupted.state)
    return true
  }

  private fun actionStartResponse(handled: Boolean, session: ActionSessionSnapshot?, error: String?): String = JSONObject().apply {
    put("handled", handled)
    if (session != null) {
      put("sessionId", session.sessionId)
      put("turnId", session.turnId)
      put("kind", session.kind.name)
    }
    if (error != null) put("error", error)
  }.toString()

  private fun commitActionRuntime(
    state: GameState,
    commands: MutableList<GameCommand>,
    action: String,
    turnId: String
  ): TurnResult {
    val active = ActionRuntime.activeSession(state)
    if (active == null) {
        return TurnCoordinator.commit(state, commands)
    }
    if (active.turnId != turnId) return TurnResult(state, error = "action_turn_mismatch")

    val minutes = active.plannedMinutes ?: TimeCostPolicy.estimateMinutes(action)
    val progressed = ActionRuntime.advance(state, active.sessionId, "resolve", minutes)
    if (!progressed.applied && !progressed.duplicate) {
      return TurnResult(state, error = progressed.error ?: "action_time_rejected")
    }
    val progressedState = if (progressed.duplicate) state else progressed.state
    val committed = TurnCoordinator.commit(progressedState, commands)
    if (committed.error != null) return committed

    var finalState = committed.state
    if (active.kind == ActionKind.SEARCH && !active.locationKey.isNullOrBlank()) {
      val depth = active.searchDepth ?: SearchDepth.NORMAL
      val coverage = ActionRuntime.markSearchCoverage(
        finalState,
        active.sessionId,
        setOf("depth:${depth.name.lowercase()}")
      )
      if (coverage.applied) finalState = coverage.state
    }

    val completed = ActionRuntime.complete(finalState, active.sessionId)
    if (!completed.applied) return TurnResult(finalState, committed.execution, completed.error ?: "action_complete_failed")
    return TurnResult(completed.state, committed.execution?.copy(state = completed.state))
  }

  fun currentPartyDetails(legacyStateJson: String? = null): String {
    val source = if (repository.exists()) {
      repository.load()
    } else if (!legacyStateJson.isNullOrBlank()) {
      runCatching { GameStateCodec.decode(legacyStateJson) }.getOrElse { GameState.initial() }
    } else {
      GameState.initial()
    }
    val state = CharacterEquipmentSystem.normalize(source)
    if (!repository.exists()) repository.save(state)
    return CharacterDetailJson.encodeParty(CharacterDetailProjector.projectParty(state)).toString()
  }

  fun resetNewGame(): String {
    repository.clear()
    val fresh = CharacterEquipmentSystem.normalize(GameState.initial())
    repository.save(fresh)
    return CharacterDetailJson.encodeParty(CharacterDetailProjector.projectParty(fresh)).toString()
  }

  fun currentCoreState(): String = GameStateCodec.encode(repository.load())
  fun clear() = repository.clear()
  fun processInventoryUiAction(
    legacyStateJson: String,
    actorId: String,
    operation: String,
    itemId: String,
    quantity: Int,
    targetId: String?
  ): String {
    val legacy = JSONObject(legacyStateJson)
    val core = loadOrMigrate(legacy)
    val outcome = InventoryUiActions.execute(core, actorId, operation, itemId, quantity, targetId)
    val synchronized = syncLegacy(legacy, outcome.state, incrementTurn = false)
    if (outcome.applied) {
      repository.save(outcome.state)
      appendSystemLog(synchronized, outcome.message)
    }
    return JSONObject().apply {
      put("handled", true)
      put("applied", outcome.applied)
      put("state", synchronized)
      put("message", outcome.message)
      outcome.reason?.let { put("reason", it) }
    }.toString()
  }

  override fun close() = localModel.close()

  /**
   * Commits only the gameplay delta accepted by Kotlin Game Core policy.
   * Candidate prose/JSON never becomes storage directly: Inventory, Party, Player and provider
   * flags are all rechecked against authoritative roll/operation context before command commit.
   */
  fun processValidatedCandidate(
    beforeJson: String,
    candidateJson: String,
    rollsJson: String,
    operationsJson: String,
    action: String,
  ): String {
    val before = JSONObject(beforeJson)
    val candidate = JSONObject(candidateJson)
    RuntimeDebugLog.recordTurnStage(before.optInt("turn", 0), "candidateAfterStoryNormalization", candidate)
    RuntimeDebugLog.appendTurnStage(before.optInt("turn", 0), "storyProgression", JSONObject()
      .put("storyArcBefore", before.optJSONObject("flags")?.opt("storyArc") ?: JSONObject.NULL)
      .put("storyArcNormalized", candidate.optJSONObject("flags")?.opt("storyArc") ?: JSONObject.NULL)
      .put("luciaEncounter", candidate.optJSONObject("flags")?.opt("luciaEncounter") ?: JSONObject.NULL))
    val core = loadOrMigrate(before)
    val rolls = JSONObject(rollsJson.ifBlank { "{}" })
    val operations = JSONArray(operationsJson.ifBlank { "[]" })
    val preparedCore = synchronizeValidatedLuciaCharacter(core, candidate)
    val turnId = nextTurnId(before, preparedCore)
    RuntimeDebugLog.recordEvent("game_core", "validated_candidate_prepared", before.optInt("turn", 0), JSONObject()
      .put("turnId", turnId)
      .put("action", action)
      .put("preparedCore", JSONObject(GameStateCodec.encode(preparedCore))))
    val pending = TurnCoordinator.createPending(preparedCore, turnId, action)
    RuntimeDebugLog.appendTurnStage(before.optInt("turn", 0), "gameCorePending", JSONObject()
      .put("turnId", turnId)
      .put("error", pending.error ?: "")
      .put("state", JSONObject(GameStateCodec.encode(pending.state))))
    if (pending.error != null) return response(false, before, pending.error, "pending_rejected")
    val commands = mutableListOf<GameCommand>()
    val current = pending.state.inventories[KAI_ID]?.items.orEmpty()
    val actionIntents = rules.interpretSync(action, contextFor(pending.state)).candidates.map { it.intent }.toSet()
    val inventoryLocked = isDirectPlayerPickupAction(action) || GameIntent.OMNIVAULT_RESTORE in actionIntents || GameIntent.OMNIVAULT_SCAN in actionIntents || GameIntent.OMNIVAULT_COPY in actionIntents

    val desiredById = current.toMutableMap()
    if (!inventoryLocked) {
      val desiredInventory = candidate.optJSONArray("inventory") ?: JSONArray()
      for (index in 0 until desiredInventory.length()) {
        val json = desiredInventory.optJSONObject(index) ?: continue
        val name = json.optString("name").trim(); if (name.isEmpty()) continue
        val requestedId = json.optString("id").trim().takeIf(String::isNotEmpty)
        val definition = ItemCatalog.resolve(requestedId, name) ?: continue
        if (!definition.rewardable) continue
        val old = desiredById[definition.id]
        val oldQuantity = old?.quantity ?: 0
        val proposedQuantity = json.optInt("quantity", 1).coerceAtLeast(1)
        if (proposedQuantity <= oldQuantity) continue
        val acquisitionBasis = inventoryAcquisitionBasis(operations, name)
        if (!InventoryAcquisitionPolicy.allows(before, rolls, action, name, old != null, acquisitionBasis)) continue
        val metadata = old?.metadata.orEmpty() + jsonObjectStrings(json.optJSONObject("metadata")) + definition.metadata
        desiredById[definition.id] = ItemCatalog.canonicalize(
          definition,
          ItemStack(definition.id, definition.displayName, proposedQuantity, metadata = metadata)
        )
      }
    }

    (current.keys + desiredById.keys).sorted().forEachIndexed { index, id ->
      val old = current[id]?.quantity ?: 0
      val desired = desiredById[id]?.quantity ?: 0
      if (desired <= old) return@forEachIndexed
      val stack = desiredById.getValue(id)
      commands += ItemCommand(
        "$turnId:GEMINI:INV:$index", turnId, KAI_ID, source = CommandSource.GEMINI,
        operation = ItemCommand.Operation.PICKUP,
        itemId = id, itemName = stack.name, quantity = desired - old, metadata = stack.metadata
      )
    }

    val desiredParty = mutableMapOf<String, JSONObject>()
    val partyJson = candidate.optJSONArray("party") ?: JSONArray()
    for (index in 0 until partyJson.length()) {
      val member = partyJson.optJSONObject(index) ?: continue
      val id = member.optString("id").ifBlank { member.optString("name").trim().lowercase() }
      if (id.isNotBlank()) desiredParty[id] = member
    }
    val currentFollowers = pending.state.party.memberIds.filter { it != KAI_ID }.toSet()
    if (PartyCandidatePolicy.allowsRemoval(action)) {
      (currentFollowers - desiredParty.keys).sorted().forEachIndexed { index, id ->
        commands += PartyCommand("$turnId:GEMINI:PARTY_REMOVE:$index", turnId, KAI_ID, id, CommandSource.GEMINI, PartyCommand.Operation.REMOVE)
      }
    }
    (desiredParty.keys - currentFollowers).sorted().forEachIndexed { index, id ->
      val member = desiredParty.getValue(id)
      val known = pending.state.characters[id]
      val memberName = member.optString("name").ifBlank { known?.name ?: id }
      val engineAuthorizedJoin = known?.metadata?.get("storyManaged") == "lucia" &&
        known.metadata["joinEligible"] == "true"
      if (!PartyCandidatePolicy.allowsCoreAddition(
          before, rolls, id, memberName, engineAuthorizedJoin
        )) return@forEachIndexed
      commands += PartyCommand(
        "$turnId:GEMINI:PARTY_ADD:$index", turnId, KAI_ID, id, CommandSource.GEMINI, PartyCommand.Operation.ADD,
        consentConfirmed = member.optBoolean("joinConfirmed", false) && known?.metadata?.get("joinEligible") == "true",
        targetPresent = member.optBoolean("present", false) && known?.presence == CharacterPresence.ACTIVE
      )
    }

    val sanitizedPlayer = PlayerCandidatePolicy.sanitizeCandidate(
      before = before,
      candidatePlayer = candidate.optJSONObject("player"),
      rolls = rolls,
      action = action,
      ownedGearNames = desiredById.values.map { it.name }.toSet(),
    )
    val sanitizedFlags = FlagCandidatePolicy.sanitizeCandidate(
      before = before,
      candidateFlags = candidate.optJSONObject("flags"),
      operations = operations,
      rolls = rolls,
    )
    commands += ValidatedLegacyStateCommand(
      commandId = "$turnId:GEMINI:VALIDATED_STATE", turnId = turnId, source = CommandSource.GEMINI,
      location = candidate.optString("location").takeIf(String::isNotBlank),
      title = candidate.optString("title").takeIf(String::isNotBlank),
      levelJson = candidate.optJSONObject("level")?.toString(),
      playerJson = sanitizedPlayer?.toString(),
      flagsJson = sanitizedFlags?.toString(),
      validatedByGameEngine = true
    )
    commands += timeAdvanceCommand(turnId, action)

    RuntimeDebugLog.appendTurnStage(before.optInt("turn", 0), "gameCoreCommands", JSONArray().apply {
      commands.forEach { command -> put(JSONObject()
        .put("commandId", command.commandId)
        .put("turnId", command.turnId)
        .put("actorId", command.actorId)
        .put("source", command.source.name)
        .put("type", command::class.java.simpleName)) }
    })
    val committed = commitActionRuntime(pending.state, commands, action, turnId)
    RuntimeDebugLog.appendTurnStage(before.optInt("turn", 0), "gameCoreCommitResult", JSONObject()
      .put("error", committed.error ?: "")
      .put("state", JSONObject(GameStateCodec.encode(committed.state))))
    if (committed.error != null) {
      logger.log(PipelineLogEvent("GEMINI_REJECTED", turnId = turnId, source = CommandSource.GEMINI, details = mapOf("reason" to committed.error)))
      return response(false, before, committed.error, "gemini_delta_rejected")
    }
    repository.save(committed.state)
    val synchronized = syncLegacy(candidate, committed.state, incrementTurn = false)
    RuntimeDebugLog.recordTurnStage(before.optInt("turn", 0), "coreCommittedState", synchronized)
    RuntimeDebugLog.recordEvent("game_core", "validated_candidate_committed", before.optInt("turn", 0), JSONObject()
      .put("turnId", turnId)
      .put("commands", commands.size)
      .put("inventoryLocked", inventoryLocked)
      .put("state", synchronized))
    logger.log(PipelineLogEvent("GEMINI_COMMIT", turnId = turnId, source = CommandSource.GEMINI, details = mapOf("commands" to commands.size.toString(), "inventoryLocked" to inventoryLocked.toString())))
    return response(true, synchronized, null, "gemini_delta_committed")
  }


  fun startEntityEncounters(legacyStateJson: String, keysJson: String): String {
    val legacy = JSONObject(legacyStateJson)
    val keys = JSONArray(keysJson)
    val next = EntityEncounterPolicy.enqueue(loadOrMigrate(legacy),
      (0 until keys.length()).map { keys.getString(it) })
    repository.save(next)
    return syncLegacy(legacy, next, incrementTurn = false).toString()
  }

  fun startCombatState(legacyStateJson: String, entityKey: String): String {
    val legacy = JSONObject(legacyStateJson)
    val current = loadOrMigrate(legacy)
    val next = CombatRuntime.start(current, entityKey)
    repository.save(next)
    return syncLegacy(legacy, next, incrementTurn = false).toString()
  }

  fun processCombat(legacyStateJson: String, actionKind: String, action: String): String {
    val legacy = JSONObject(legacyStateJson)
    val current = loadOrMigrate(legacy)
    if (CombatRuntime.active(current) == null) return response(false, legacy, null, "combat_inactive")

    val resolvedEntityKey = CombatRuntime.active(current)?.entityKey.orEmpty()
    var resolution = CombatTurnAuthority.resolve(current, actionKind, action)
    if (!resolution.handled) return response(false, legacy, null, "combat_inactive")
    // TRUE_TURN_COMBAT_FACADE_V2: this bridge projects Core state only. Kotlin owns round timing/regen.
    var next = resolution.state
    if (resolution.entityDestroyed) {
      val reward = EntityDrops.award(next, CombatRuntime.active(current)!!.encounterId)
      next = reward.state
      resolution = resolution.copy(state = next, reply = resolution.reply + " " + reward.message)
    }
    if (resolution.entityDestroyed || resolution.escaped) {
      val flags = next.world["flagsJson"]?.let { JSONObject(it) }
        ?: legacy.optJSONObject("flags")?.let { JSONObject(it.toString()) }
        ?: JSONObject()
      flags.put("entityEncounterKey", "")
      when (resolvedEntityKey) {
        "jeff_the_killer" -> flags.optJSONObject("jeff")?.put("present", false)
        "jane_the_killer" -> flags.optJSONObject("jane")?.put("present", false)
      }
      next = next.copy(world = next.world + ("flagsJson" to flags.toString()))
    }
    if (resolution.entityDestroyed || resolution.escaped) {
      next = EntityEncounterPolicy.advance(next)
      CombatRuntime.active(next)?.let {
        resolution = resolution.copy(reply = resolution.reply + " Entity tiếp theo: ${it.entityName}.")
      }
    }
    repository.save(next)

    val roundCompleted = CombatTurnAuthority.completesRound(actionKind, resolution)
    val output = syncLegacy(legacy, next, incrementTurn = roundCompleted)
    if (resolution.entityDestroyed || resolution.escaped) {
      val flags = output.optJSONObject("flags") ?: JSONObject().also { output.put("flags", it) }
      flags.put("entityEncounterKey", "")
    }
    if (actionKind.equals("AUTO_COMBAT", true) || actionKind.equals("AUTO_COMBAT_STEP", true)) {
      val combatLog = output.optJSONArray("log") ?: JSONArray().also { output.put("log", it) }
      combatLog.put(JSONObject().put("role", "combat").put("text", resolution.reply))
    } else {
      appendLog(output, action, resolution.reply)
    }
    return response(true, output, null, if (resolution.entityDestroyed) "combat_entity_destroyed" else if (resolution.escaped) "combat_escaped" else "combat_resolved", resolution.reply)
  }

  private fun normalizeVisualPresence(state: GameState): GameState {
    if (CombatRuntime.active(state) != null) return state
    val rawFlags = state.world["flagsJson"] ?: return state
    val flags = runCatching { JSONObject(rawFlags) }.getOrNull() ?: return state
    if (flags.optString("entityEncounterKey", "").isBlank()) return state
    flags.put("entityEncounterKey", "")
    return state.copy(world = state.world + ("flagsJson" to flags.toString()))
  }

  private fun loadOrMigrate(legacy: JSONObject): GameState {
    val loaded = loadOrMigratePreV4(legacy)
    val normalized = InventoryV4State.normalize(loaded)
    if (normalized != loaded) repository.save(normalized)
    return normalized
  }

  private fun loadOrMigratePreV4(legacy: JSONObject): GameState {
    val existed = repository.exists()
    val loaded = if (existed) repository.load() else GameStateCodec.decode(legacy)
    val normalized = EntityDrops.claimPending(normalizeVisualPresence(loaded))
    if (!existed || normalized != loaded) repository.save(normalized)
    return normalized
  }

  private fun contextFor(state: GameState): GameContext {
    val actors = state.characters.values.associate { it.name.lowercase() to it.id } + mapOf("kai" to KAI_ID, "iris" to "iris", "syvial" to "syvial", "an nhiên" to AN_NHIEN_ID, "an nhien" to AN_NHIEN_ID)
    val items = (state.inventories.values.flatMap { it.items.values } + state.omnivault.storedItems.values).associate { it.name.lowercase() to it.itemId }
    return GameContext(state, actors, items)
  }

  private fun isMadGodEquipRequest(action: String): Boolean {
    val text = action.trim()
    val equip = Regex("(?:^|\\s)(?:trang\\s+bị|equip|đeo|mặc|cầm\\s+làm\\s+vũ\\s+khí)(?:\\s|$)", RegexOption.IGNORE_CASE)
    val madGod = Regex("(?:mad\\s*god|madgod)(?:\\s+set)?", RegexOption.IGNORE_CASE)
    return equip.containsMatchIn(text) && madGod.containsMatchIn(text)
  }

  private fun isDirectPlayerPickupAction(action: String): Boolean {
    val text = action.trim()
    val omnivaultWithdrawal = Regex("(?:lấy|rút|triệu hồi).*(?:ra khỏi|khỏi|từ).*(?:omnivault|nhẫn|kho)", RegexOption.IGNORE_CASE).containsMatchIn(text)
    if (omnivaultWithdrawal) return false
    val directVerb = Regex("(?:^|\\s)(?:nhặt|lượm|cầm\\s+lên|lấy(?:\\s+lên)?|thu\\s+hồi|tịch\\s+thu|nhận(?:\\s+lấy)?|pick\\s+up|take|receive)(?:\\s|$)", RegexOption.IGNORE_CASE)
    val inventoryAssertion = Regex("(?:thêm|đưa).{0,80}(?:vào|trong)\\s+(?:inventory|kho đồ|túi đồ)", RegexOption.IGNORE_CASE)
    return directVerb.containsMatchIn(text) || inventoryAssertion.containsMatchIn(text)
  }

  private fun nextTurnId(legacy: JSONObject, state: GameState): String {
    val number = legacy.optInt("turn", state.turn.currentTurnId.substringAfterLast('_').toIntOrNull() ?: 1)
    return "TURN_${number.coerceAtLeast(1)}"
  }

  private fun timeAdvanceCommand(turnId: String, action: String): TimeAdvanceCommand = TimeAdvanceCommand(
    commandId = "$turnId:SYSTEM:TIME",
    turnId = turnId,
    actorId = KAI_ID,
    source = CommandSource.SYSTEM,
    minutes = TimeCostPolicy.estimateMinutes(action),
    reason = "player_action"
  )

  private fun stableItemId(name: String): String = name.lowercase()
    .replace(Regex("[^\\p{L}\\p{N}]+"), "-").trim('-').ifBlank { "item-${name.hashCode().toUInt()}" }

  private fun inventoryAcquisitionBasis(operations: JSONArray, itemName: String): String {
    val needle = itemName.trim()
    if (needle.isEmpty()) return ""
    for (index in 0 until operations.length()) {
      val operation = operations.optJSONObject(index) ?: continue
      if (!operation.optString("type").equals("inventory_upsert", ignoreCase = true)) continue
      val item = operation.optJSONObject("item") ?: continue
      if (item.optString("name").trim().equals(needle, ignoreCase = true)) {
        return operation.optString("basis", "").trim()
      }
    }
    return ""
  }

  private fun jsonObjectStrings(json: JSONObject?): Map<String, String> {
    if (json == null) return emptyMap()
    val result = mutableMapOf<String, String>()
    json.keys().forEach { key -> result[key] = json.optString(key) }
    return result
  }

  private fun syncLegacy(legacy: JSONObject, state: GameState, incrementTurn: Boolean): JSONObject {
    val output = JSONObject(legacy.toString())
    if (incrementTurn) output.put("turn", output.optInt("turn", 1) + 1)
    output.put("saveVersion", CURRENT_SAVE_VERSION)
    output.put("gameTime", JSONObject().apply {
      put("elapsedSubjectiveMinutes", state.time.elapsedSubjectiveMinutes)
      put("lastAdvanceMinutes", state.time.lastAdvanceMinutes)
      state.time.lastAdvanceReason?.let { put("lastAdvanceReason", it) }
    })
    output.put("partyDetails", CharacterDetailJson.encodeParty(CharacterDetailProjector.projectParty(state)))
    output.put("equipment",MadGodCanon.legacy(state))
    val kaiInventory = state.inventories[KAI_ID]?.items?.values.orEmpty()
    output.put("inventory", JSONArray().apply { kaiInventory.forEach { stack -> put(JSONObject().apply {
      put("id", stack.itemId); put("name", stack.name); put("quantity", stack.quantity)
      stack.condition?.let { put("state", it) }; put("metadata", JSONObject(stack.metadata))
    }) } })
    output.put("party", JSONArray().apply { state.party.memberIds.filter { it != KAI_ID }.forEach { id ->
      state.characters[id]?.let { character -> put(JSONObject().apply {
        put("id", character.id); put("name", character.name); character.avatarRef?.let { put("avatar", it) }
        put("presence", character.presence.name)
      }) }
    } })
    state.world["location"]?.let { output.put("location", it) }
    state.world["title"]?.let { output.put("title", it) }
    state.world["levelJson"]?.let { output.put("level", JSONObject(it)) }
    state.world["flagsJson"]?.let { output.put("flags", JSONObject(it)) }
    state.metadata["legacyPlayerJson"]?.let { output.put("player", JSONObject(it)) }
    CombatRuntime.toJson(state)?.let { output.put("combat", it) } ?: output.remove("combat")
    return output
  }

  private fun appendLog(state: JSONObject, action: String, reply: String) {
    val log = state.optJSONArray("log") ?: JSONArray().also { state.put("log", it) }
    log.put(JSONObject().put("role", "player").put("text", action))
    log.put(JSONObject().put("role", "gm").put("text", reply))
  }

  private fun appendSystemLog(state: JSONObject, message: String) {
    val log = state.optJSONArray("log") ?: JSONArray().also { state.put("log", it) }
    log.put(JSONObject().put("role", "system").put("text", message))
  }

  private fun response(handled: Boolean, state: JSONObject, error: String?, reason: String, reply: String? = null): String = JSONObject().apply {
    put("handled", handled); put("state", state); put("reason", reason)
    if (error != null) put("error", error); if (reply != null) put("reply", reply)
  }.toString()

  private fun eventReply(events: List<String>): String = when (events.lastOrNull { it != "time_advanced" }) {
    "inventory_pickup" -> "Inventory đã được cập nhật bởi một sự kiện vật phẩm hợp lệ."
    "inventory_remove" -> "Vật phẩm đã được loại khỏi Inventory theo hành động của Kai."
    "inventory_transfer" -> "Vật phẩm đã được chuyển giao."
    "item_equipped" -> "Vật phẩm đã được trang bị."
    "item_unequipped" -> "Vật phẩm đã được tháo khỏi trang bị."
    "omnivault_stored" -> "Vật phẩm đã được cất vào Omnivault."
    "omnivault_withdrawn" -> "Vật phẩm đã được lấy ra khỏi Omnivault."
    else -> "Hành động đã được Game State Core xác nhận."
  }

  private fun validationReply(reason: String): String {
    val message = when (reason) {
      "player_pickup_unavailable" -> "Vật phẩm được phát trực tiếp qua sự kiện trong câu chuyện; không cần nhặt thủ công."
      "inventory_ui_required" -> "Hãy mở kho đồ của nhân vật để sử dụng, chuyển hoặc vứt bỏ vật phẩm."
      "omnivault_operation_retired" -> "Nhẫn Vạn Tàng không còn chức năng Quét, Sao chép hoặc tạo vật phẩm."
      "restore_narrative_only" -> "Thao tác này không thể thay đổi vật phẩm trong kho đồ."
      "precise_content_amount_forbidden" -> "Kho đồ chỉ quản lý vật phẩm nguyên vẹn theo số lượng nguyên."
      "item_not_in_catalog" -> "Vật phẩm này không có trong danh mục vật phẩm."
      "item_use_not_supported" -> "Vật phẩm này không thể sử dụng trực tiếp từ kho đồ."
      "scan_source_missing", "scan_template_missing" -> "Không có vật phẩm hợp lệ để quét hoặc sao chép."
      "insufficient_item_quantity", "item_not_owned" -> "Nhân vật không có đủ vật phẩm này trong kho đồ."
      "party_full" -> "Đội đã đủ tối đa bốn thành viên."
      "join_not_confirmed" -> "Yêu cầu gia nhập chưa đủ điều kiện hoặc chưa được nhân vật xác nhận."
      "living_target_forbidden" -> "Nhẫn Vạn Tàng không thể tác động lên sinh vật sống."
      "restore_cooldown_active" -> "Vật phẩm này vẫn đang trong thời gian chờ Hoàn Nguyên 24 giờ."
      else -> "Không thể thực hiện hành động này."
    }
    return "[Cảnh báo] $message"
  }


  companion object {
    @JvmStatic fun create(context: Context, debugLogging: Boolean = false): GameCoreFacade = GameCoreFacade(
      SharedPreferencesSaveRepository(context.applicationContext), AndroidGamePipelineLogger(debugLogging), LiteRTIntentInterpreter(context.applicationContext)
    )
  }
}

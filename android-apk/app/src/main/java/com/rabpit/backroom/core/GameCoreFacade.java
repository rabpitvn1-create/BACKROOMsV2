package com.rabpit.backroom.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class GameCoreFacade implements AutoCloseable {
  private static final String TAG = "BackroomGameCore";
  private static final String PREFS = "backroom_game_core";
  private static final String STATE_KEY = "state_json";
  private static final int CURRENT_SAVE_VERSION = 14;

  private final SharedPreferences preferences;
  private final boolean debugLogging;
  private final LevelCore levelCore;
  private final EntityCore entityCore;
  private final ItemCore itemCore;
  private final CharacterEncounterCore characterEncounterCore;
  private final CharacterProgressionCore characterProgressionCore;
  private final SurvivalCore survivalCore;
  private final CharacterDetailCore characterDetailCore;
  private final EmergentTurnEngine emergentTurnEngine;
  private final Map<String, PreparedTurn> preparedTurns = new LinkedHashMap<>();

  private GameCoreFacade(Context context, boolean debugLogging) {
    Context appContext = context.getApplicationContext();
    this.preferences = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    this.debugLogging = debugLogging;
    this.levelCore = new LevelCore(appContext);
    this.entityCore = new EntityCore(appContext);
    this.itemCore = new ItemCore();
    this.characterEncounterCore = new CharacterEncounterCore();
    this.characterProgressionCore = new CharacterProgressionCore();
    this.survivalCore = new SurvivalCore();
    this.characterDetailCore = new CharacterDetailCore();
    this.emergentTurnEngine = new EmergentTurnEngine();
  }

  public static GameCoreFacade create(Context context, boolean debugLogging) {
    return new GameCoreFacade(context, debugLogging);
  }

  public synchronized String processRule(String legacyStateJson, String action) {
    JSONObject legacy = parseState(legacyStateJson);
    JSONObject stored = parseState(preferences.getString(STATE_KEY, "{}"));
    if (stored.length() > 0) legacy = stored;
    try {
      normalizeCoreState(legacy);
      emergentTurnEngine.normalizeState(legacy);
      if (emergentTurnEngine.catchUpProjections(legacy)) persist(legacy);
      if (!emergentTurnEngine.selectionProjectionFresh(legacy)) {
        throw new IllegalStateException("SelectionCooldown projection is stale");
      }

      String text = action == null ? "" : action.trim();
      if (text.isEmpty()) return response(false, legacy, "Hành động trống.", "validation_rejected", null);

      if (GameCoreRules.isDirectPlayerPickupAction(text)) {
        JSONObject result = deepCopy(legacy);
        String reply = "Không thể nhặt vật phẩm tự do. Loot chỉ nhận từ Entity hoặc Rương.";
        appendLog(result, text, reply);
        persist(result);
        return response(true, result, "player_pickup_unavailable", "validation_rejected", reply);
      }

      if (GameCoreRules.isInventoryQuery(text)) {
        JSONObject result = deepCopy(legacy);
        String reply = inventoryReply(result.optJSONArray("inventory"));
        appendLog(result, text, reply);
        persist(result);
        return response(true, result, null, "query_handled", reply);
      }

      if (GameCoreRules.isPartyQuery(text)) {
        JSONObject result = deepCopy(legacy);
        String reply = partyReply(result.optJSONArray("party"));
        appendLog(result, text, reply);
        persist(result);
        return response(true, result, null, "query_handled", reply);
      }

      int preTurnStateVersion = emergentTurnEngine.stateVersion(legacy);
      String turnId = emergentTurnEngine.nextTurnId(legacy, text);
      PreparedTurn existing = preparedTurns.get(turnId);
      if (existing != null) return preparedResponse(legacy, existing);

      TurnRng turnRng = new TurnRng(
          turnId, preTurnStateVersion,
          EmergentTurnEngine.CANON_VERSION, EmergentTurnEngine.RNG_SCHEMA_VERSION);
      JSONObject working = deepCopy(legacy);
      JSONArray events = new JSONArray();
      String replyHint = "";
      boolean openedChest = false;
      String beforeLevelKey = working.optString("currentLevelKey", String.valueOf(working.optInt("currentLevel", 0)));

      if (itemCore.isOpenChestAction(text)) {
        String itemName = itemCore.openChest(
            working, bound -> turnRng.nextInt(TurnRng.Scope.PLAYER_ACTION, bound));
        openedChest = true;
        JSONObject flags = working.optJSONObject("flags");
        int coreReward = flags == null ? 0 : Math.max(0, flags.optInt("lastChestCoreReward", 0));
        replyHint = "Rương chứa " + itemName + " x1. Đã thêm vào Inventory."
            + (coreReward > 0 ? " Nhận +" + coreReward + " Core." : "");
        JSONArray effects = new JSONArray().put(emergentTurnEngine.threadEffect(
            "CHEST_AVAILABLE", new JSONArray().put(beforeLevelKey), "TERMINATE", "RESOLVED"));
        events.put(emergentTurnEngine.event(turnId, events, "CHEST_OPENED", "LOCAL", beforeLevelKey,
            new JSONObject()
                .put("factPredicate", "chest_opened")
                .put("factValue", itemName)
                .put("causedBy", "player")
                .put("observedByPlayer", true),
            effects));
      } else {
        boolean transitioned = levelCore.applyPlayerTransitionIfRequested(working, text);
        if (transitioned) {
          String nextLevel = working.optString("currentLevelKey", "");
          JSONArray effects = new JSONArray().put(emergentTurnEngine.threadEffect(
              "LEVEL_ROUTE_SEARCH", new JSONArray().put(beforeLevelKey), "TERMINATE", "RESOLVED"));
          events.put(emergentTurnEngine.event(turnId, events, "LEVEL_TRANSITIONED", "REGIONAL", nextLevel,
              new JSONObject()
                  .put("factPredicate", "entered_level")
                  .put("factValue", nextLevel)
                  .put("causedBy", "player")
                  .put("observedByPlayer", true),
              effects));
        } else {
          levelCore.rollRouteForExplorerAction(
              working, text, bound -> turnRng.nextInt(TurnRng.Scope.PLAYER_ACTION, bound));
          appendRouteEventIfAny(working, turnId, events);
        }
      }

      incrementTurn(working);
      advanceGameTime(working, text);
      characterProgressionCore.applyExplorerTurnRecovery(working);
      survivalCore.normalizeState(working);
      itemCore.normalizeInventory(working);
      characterEncounterCore.normalizeState(working);
      working.put("mode", "ai");
      working.put("saveVersion", CURRENT_SAVE_VERSION);

      // Turn/time are authoritative too, so every world-advancing player turn has an event source.
      events.put(emergentTurnEngine.event(turnId, events, "PLAYER_ACTION_RESOLVED", "LOCAL", "cao_minh",
          new JSONObject()
              .put("factPredicate", "player_action")
              .put("factValue", text)
              .put("causedBy", "player")
              .put("impactEligible", false)
              .put("observedByPlayer", true),
          null));

      JSONArray candidates = new JSONArray();
      appendAll(candidates, entityCore.situationCandidates(working));
      if (!openedChest) {
        JSONObject chestCandidate = itemCore.explorationChestCandidate(working);
        if (chestCandidate != null) candidates.put(chestCandidate);
      }
      appendAll(candidates, characterEncounterCore.situationCandidates(working));

      JSONObject selected = emergentTurnEngine.selectCandidate(
          working, candidates, turnRng, Math.max(1, working.optInt("turn", 1)));
      PreparedTurn prepared = new PreparedTurn(
          turnId, preTurnStateVersion, text, working, events, selected, turnRng, replyHint);
      preparedTurns.clear();
      preparedTurns.put(turnId, prepared);
      return preparedResponse(legacy, prepared);
    } catch (Exception e) {
      debug("processRule failed: " + e.getMessage());
      return response(false, legacy, safeMessage(e), "core_error", null);
    }
  }

  public synchronized String completePreparedTurn(String turnId, String proposalJson) {
    JSONObject persisted = parseState(preferences.getString(STATE_KEY, "{}"));
    try {
      normalizeCoreState(persisted);
      emergentTurnEngine.normalizeState(persisted);
      if (emergentTurnEngine.hasCommitted(persisted, turnId)) {
        return response(true, persisted, null, "duplicate_commit", null);
      }

      PreparedTurn prepared = preparedTurns.get(turnId);
      if (prepared == null) {
        return response(false, persisted, "Không có turn attempt phù hợp.", "turn_attempt_missing", null);
      }
      if (emergentTurnEngine.stateVersion(persisted) != prepared.preTurnStateVersion) {
        preparedTurns.remove(turnId);
        return response(false, persisted, "State đã thay đổi trước COMMIT.", "stale_turn_attempt", null);
      }

      JSONObject working = prepared.working;
      JSONObject selected = prepared.selected;
      JSONObject proposal = emergentTurnEngine.sanitizeWorldProposal(selected, parseState(proposalJson));
      selected.put("worldProposal", proposal);
      working.getJSONObject(EmergentTurnEngine.ROOT_KEY)
          .put("lastSelection", new JSONObject(selected.toString()));

      if (!selected.optBoolean("selectedNone", false)) {
        applySelectedSituation(working, prepared.events, prepared.turnId, selected, proposal);
      }

      emergentTurnEngine.appendDormancyEvents(
          working, prepared.events, prepared.turnId, Math.max(1, working.optInt("turn", 1)));
      if (prepared.events.length() > 64) {
        throw new IllegalStateException("DomainEvent cascade exceeded max_batch_size=64");
      }
      emergentTurnEngine.validateBatch(prepared.turnId, prepared.events);
      emergentTurnEngine.commitAuthoritative(
          working, prepared.turnId, prepared.events, selected);

      // First durable write is authoritative. Projections are intentionally a second replayable write.
      persist(working);
      emergentTurnEngine.catchUpProjections(working);
      persist(working);
      preparedTurns.remove(turnId);
      return preparedCommitResponse(working, prepared);
    } catch (Exception e) {
      preparedTurns.remove(turnId);
      debug("completePreparedTurn failed: " + e.getMessage());
      return response(false, persisted, safeMessage(e), "system_fault_precommit", null);
    }
  }

  public synchronized String commitNarration(String stateJson) {
    JSONObject submitted = parseState(stateJson);
    JSONObject state = parseState(preferences.getString(STATE_KEY, "{}"));
    try {
      normalizeCoreState(state);
      JSONArray log = submitted.optJSONArray("log");
      if (log != null) state.put("log", new JSONArray(log.toString()));
      characterEncounterCore.acknowledgePendingIntro(state);
      persist(state);
      return clientSafeState(state).toString();
    } catch (Exception e) {
      throw new IllegalStateException("Không thể lưu narration.", e);
    }
  }

  private void applySelectedSituation(JSONObject working, JSONArray events, String turnId,
                                      JSONObject selected, JSONObject proposal) throws Exception {
    String kind = selected.optString("kind", "");
    String payload = selected.optString("payloadKey", "");
    JSONObject params = new JSONObject()
        .put("observedByPlayer", true)
        .put("causedBy", "world")
        .put("situationKey", selected.optString("situationKey", ""))
        .put("worldProposal", new JSONObject(proposal.toString()));

    if ("ENTITY".equals(kind)) {
      entityCore.activateEncounterCandidate(working, payload);
      params.put("factPredicate", "entity_encounter_started").put("factValue", payload);
      JSONArray effects = new JSONArray().put(emergentTurnEngine.threadEffect(
          "ENTITY_ENCOUNTER", new JSONArray().put(payload), "SEED_OR_ADVANCE", null));
      events.put(emergentTurnEngine.event(
          turnId, events, "ENTITY_ENCOUNTER_STARTED", "LOCAL", payload, params, effects));
      return;
    }

    if ("CHEST".equals(kind)) {
      itemCore.activateExplorationChest(working);
      String levelKey = working.optString("currentLevelKey", payload);
      params.put("factPredicate", "chest_discovered").put("factValue", true);
      JSONArray effects = new JSONArray().put(emergentTurnEngine.threadEffect(
          "CHEST_AVAILABLE", new JSONArray().put(levelKey), "SEED_OR_ADVANCE", null));
      events.put(emergentTurnEngine.event(
          turnId, events, "CHEST_SPAWNED", "LOCAL", levelKey, params, effects));
      return;
    }

    if ("CHARACTER".equals(kind)) {
      characterEncounterCore.activateEncounterCandidate(working, payload);
      params.put("factPredicate", "character_encountered").put("factValue", payload);
      events.put(emergentTurnEngine.event(
          turnId, events, "CHARACTER_ENCOUNTERED", "SOCIAL", payload, params, null));
      return;
    }

    throw new IllegalStateException("Unknown selected Situation kind: " + kind);
  }

  private void appendRouteEventIfAny(JSONObject state, String turnId, JSONArray events) throws Exception {
    JSONObject route = state.optJSONObject(LevelCore.ROUTE_STATE);
    if (route == null) return;
    int currentTurn = Math.max(1, state.optInt("turn", 1));
    if (route.optInt("lastRollTurn", -1) != currentTurn) return;
    String result = route.optString("lastResult", "");
    if (result.isEmpty()) return;

    String levelKey = state.optString("currentLevelKey", String.valueOf(state.optInt("currentLevel", 0)));
    String eventType = "RESET".equals(result) ? "ROUTE_SEARCH_RESET"
        : "EXIT_AVAILABLE".equals(result) ? "ROUTE_EXIT_AVAILABLE" : "ROUTE_SEARCH_PROGRESS";
    String effect = "EXIT_AVAILABLE".equals(result) ? "TERMINATE" : "SEED_OR_ADVANCE";
    JSONArray effects = new JSONArray().put(emergentTurnEngine.threadEffect(
        "LEVEL_ROUTE_SEARCH", new JSONArray().put(levelKey), effect,
        "TERMINATE".equals(effect) ? "RESOLVED" : null));
    events.put(emergentTurnEngine.event(turnId, events, eventType, "LOCAL", levelKey,
        new JSONObject()
            .put("factPredicate", "route_search_result")
            .put("factValue", result)
            .put("causedBy", "player")
            .put("observedByPlayer", true),
        effects));
  }

  private static void appendAll(JSONArray target, JSONArray source) {
    if (target == null || source == null) return;
    for (int i = 0; i < source.length(); i++) target.put(source.opt(i));
  }

  private String preparedResponse(JSONObject committedState, PreparedTurn prepared) {
    JSONObject output = new JSONObject();
    try {
      output.put("handled", false)
          .put("reason", "turn_prepared")
          .put("state", clientSafeState(committedState))
          .put("turnId", prepared.turnId)
          .put("selectedCandidate", new JSONObject(prepared.selected.toString()))
          .put("proposalRequired", prepared.selected.optBoolean("proposalRequired", false));
      if (!prepared.replyHint.isEmpty()) output.put("replyHint", prepared.replyHint);
    } catch (Exception ignored) {}
    return output.toString();
  }

  private String preparedCommitResponse(JSONObject state, PreparedTurn prepared) {
    JSONObject output = new JSONObject();
    try {
      output.put("handled", true)
          .put("reason", "turn_committed")
          .put("state", clientSafeState(state))
          .put("turnId", prepared.turnId)
          .put("selectedCandidate", new JSONObject(prepared.selected.toString()));
      if (!prepared.replyHint.isEmpty()) output.put("replyHint", prepared.replyHint);
    } catch (Exception ignored) {}
    return output.toString();
  }

  private void normalizeCoreState(JSONObject state) throws Exception {
    levelCore.normalizeState(state);
    characterProgressionCore.normalizeState(state);
    survivalCore.normalizeState(state);
    itemCore.normalizeInventory(state);
    characterEncounterCore.normalizeState(state);
    CombatChoiceEngine.normalizeTerminalEncounter(state);
  }

  public synchronized String processValidatedCandidate(String beforeJson, String candidateJson, String action,
                                                       String encounterDialogueJson) {
    return processValidatedCandidate(beforeJson, candidateJson, action, encounterDialogueJson, null);
  }

  public synchronized String processValidatedCandidate(String beforeJson, String candidateJson, String action,
                                                       String encounterDialogueJson, String transitionTarget) {
    JSONObject before = parseState(beforeJson);
    try {
      levelCore.normalizeState(before);
      characterProgressionCore.normalizeState(before);
      survivalCore.normalizeState(before);
      itemCore.normalizeInventory(before);
      characterEncounterCore.normalizeState(before);
      JSONObject candidate = parseState(candidateJson);
      JSONObject sanitized = deepCopy(candidate);
      if (CombatChoiceEngine.isKnownEntity(encounterKey(before))) {
        sanitized.put("turn", Math.max(1, before.optInt("turn", 1)));
      }

      copyField(before, sanitized, "inventory");
      copyField(before, sanitized, SurvivalCore.ROOT_KEY);
      characterProgressionCore.protectFromCandidate(before, sanitized);

      int beforeStageIndex = levelCore.stageIndexForState(before);
      applyNarrativeBoundary(levelCore, before, sanitized, transitionTarget);
      int afterStageIndex = levelCore.stageIndexForState(sanitized);
      if (afterStageIndex != beforeStageIndex) {
        int stageCoreReward = characterProgressionCore.rewardStageCompletion(sanitized, afterStageIndex);
        JSONObject flags = sanitized.optJSONObject("flags");
        if (flags == null) flags = new JSONObject();
        flags.put("lastStageCoreReward", stageCoreReward);
        flags.put("lastStageRewardIndex", afterStageIndex);
        sanitized.put("flags", flags);
      }
      entityCore.validateAndApply(before, sanitized);
      itemCore.validateAndApply(before, sanitized);
      characterEncounterCore.validateAndApply(before, sanitized, parseArray(encounterDialogueJson));
      sanitized.put("saveVersion", CURRENT_SAVE_VERSION);
      advanceGameTimeFromBefore(before, sanitized, action);
      characterProgressionCore.applyExplorerTurnRecovery(sanitized);
      survivalCore.normalizeState(sanitized);
      itemCore.normalizeInventory(sanitized);

      persist(sanitized);
      return response(true, sanitized, null, "ai_delta_committed", null);
    } catch (Exception e) {
      debug("processValidatedCandidate failed: " + e.getMessage());
      return response(false, before, safeMessage(e), "ai_delta_rejected", null);
    }
  }

  public synchronized String processCombatResolution(String stateJson) {
    JSONObject state = parseState(preferences.getString(STATE_KEY, "{}"));
    try {
      levelCore.normalizeState(state);
      characterProgressionCore.normalizeState(state);
      survivalCore.normalizeState(state);
      itemCore.normalizeInventory(state);
      characterEncounterCore.normalizeState(state);

      boolean wasActive = CombatChoiceEngine.isActive(state);
      CombatChoiceEngine.resolveFinalized(state);
      CombatChoiceEngine.normalizeTerminalEncounter(state);
      boolean active = CombatChoiceEngine.isActive(state);
      JSONObject combat = state.optJSONObject("combat");
      String outcome = combat == null ? "" : combat.optString("outcome", "");

      if (wasActive && !active && ("victory".equals(outcome) || "defeat".equals(outcome))) {
        incrementTurn(state);
      }

      state.put("saveVersion", CURRENT_SAVE_VERSION);
      persist(state);
      return response(true, state, null,
          active ? "combat_turn_resolved" : "combat_finished", null);
    } catch (Exception e) {
      return response(false, state, safeMessage(e), "combat_resolve_rejected", null);
    }
  }


  public synchronized String restartAfterDeath() {
    JSONObject state = parseState(preferences.getString(STATE_KEY, "{}"));
    try {
      JSONObject combat = state.optJSONObject("combat");
      if (combat == null || !"defeat".equals(combat.optString("outcome", ""))
          || !combat.optBoolean("deathRestartPending", false)) {
        return response(false, state, "Không có lượt hồi sinh đang chờ.",
            "death_restart_unavailable", null);
      }

      LevelCore.returnToCurrentLevelStart(state);
      combat.put("deathRestartPending", false)
          .put("outcome", "");
      state.put("combat", combat);
      state.put("saveVersion", CURRENT_SAVE_VERSION);
      persist(state);
      return response(true, state, null, "death_restart_completed", null);
    } catch (Exception e) {
      return response(false, state, safeMessage(e), "death_restart_rejected", null);
    }
  }


  public synchronized String levelPromptContext(String stateJson) {
    return levelPromptContext(stateJson, "");
  }

  public synchronized String levelPromptContext(String stateJson, String action) {
    JSONObject state = parseState(stateJson);
    try {
      levelCore.normalizeState(state);
      return levelCore.promptContext(state, action);
    } catch (Exception e) {
      return "CURRENT LEVEL: 0\nLEVEL CANON: unavailable";
    }
  }

  public synchronized String characterPromptContext(String stateJson) {
    JSONObject state = parseState(stateJson);
    try {
      levelCore.normalizeState(state);
      characterEncounterCore.normalizeState(state);
      return characterEncounterCore.promptContext(state);
    } catch (Exception e) {
      return "CHARACTER ENCOUNTER CORE: unavailable. Do not spawn characters or mutate Party.";
    }
  }

  public synchronized String entityPromptContext(String stateJson) {
    JSONObject state = parseState(stateJson);
    try {
      levelCore.normalizeState(state);
      return entityCore.promptContext(state);
    } catch (Exception e) {
      return "ENTITY CORE: unavailable. Do not invent an Entity.";
    }
  }

  public synchronized String itemPromptContext(String stateJson) {
    JSONObject state = parseState(stateJson);
    try {
      levelCore.normalizeState(state);
      return itemCore.promptContext(state);
    } catch (Exception e) {
      return "ITEM CORE: unavailable. Do not invent or grant loot.";
    }
  }

  public synchronized String processItemAction(String stateJson, String itemId, String operation,
                                               String targetId, int quantity) {
    return processItemAction(stateJson, "cao_minh", itemId, operation, targetId, quantity);
  }

  public synchronized String processItemAction(String stateJson, String ownerId, String itemId,
                                               String operation, String targetId, int quantity) {
    JSONObject state = parseState(stateJson);
    JSONObject stored = parseState(preferences.getString(STATE_KEY, "{}"));
    if (stored.length() > 0) state = stored;
    try {
      levelCore.normalizeState(state);
      characterProgressionCore.normalizeState(state);
      survivalCore.normalizeState(state);
      itemCore.normalizeInventory(state);
      characterEncounterCore.normalizeState(state);
      String reply = itemCore.applyItemAction(state, ownerId, itemId, operation, targetId, quantity);
      state.put("saveVersion", CURRENT_SAVE_VERSION);
      persist(state);
      return response(true, state, null, "item_action_committed", reply);
    } catch (Exception e) {
      return response(false, state, safeMessage(e), "item_action_rejected", null);
    }
  }

  public synchronized String processCoreUpgrade(String stateJson, String characterId, String stat) {
    JSONObject submitted = parseState(stateJson);
    JSONObject state = parseState(preferences.getString(STATE_KEY, "{}"));
    if (state.length() == 0) state = submitted;
    try {
      levelCore.normalizeState(state);
      characterProgressionCore.normalizeState(state);
      characterEncounterCore.normalizeState(state);
      if (CombatChoiceEngine.isActive(state)) {
        return response(false, state,
            "Battle đang hoạt động. Hãy hoàn tất Poker Dice trước khi nâng chỉ số.",
            "combat_locked", null);
      }
      JSONObject result = characterProgressionCore.upgradeStat(state, characterId, stat);
      state.put("saveVersion", CURRENT_SAVE_VERSION);
      characterDetailCore.projectState(state);
      persist(state);
      String reply = result.getString("stat") + " của " + result.getString("characterId")
          + " tăng lên " + result.getInt("value") + ". -" + result.getInt("cost")
          + " Core.";
      return response(true, state, null, "core_upgrade_committed", reply);
    } catch (Exception e) {
      return response(false, state, safeMessage(e), "core_upgrade_rejected", null);
    }
  }

  public synchronized String levelSnapshotDescriptor(String stateJson) {
    JSONObject state = parseState(stateJson);
    try {
      levelCore.normalizeState(state);
      return levelCore.snapshotDescriptor(state);
    } catch (Exception e) {
      return "{\"level\":0}";
    }
  }

  public synchronized String normalizeState(String stateJson) {
    JSONObject state = parseState(stateJson);
    try {
      JSONObject persisted = parseState(preferences.getString(STATE_KEY, "{}"));
      if (persisted.length() > 0) {
        state = persisted;
      } else {
        state = newGameState(state);
      }
      normalizeCoreState(state);
      characterProgressionCore.applyExplorerTurnRecovery(state);
      emergentTurnEngine.normalizeState(state);
      emergentTurnEngine.catchUpProjections(state);
      state.put("saveVersion", CURRENT_SAVE_VERSION);
      persist(state);
    } catch (Exception e) {
      debug("normalizeState failed: " + e.getMessage());
    }
    return clientSafeState(state).toString();
  }

  public synchronized String currentCoreState() {
    return clientSafeState(parseState(preferences.getString(STATE_KEY, "{}"))).toString();
  }

  public synchronized String commitRuntimeState(String stateJson) {
    JSONObject state = parseState(stateJson);
    try {
      // Called only from native orchestration after Core validates the narrative delta.
      persist(state);
      return clientSafeState(state).toString();
    } catch (Exception e) {
      throw new IllegalStateException("Không thể lưu combat state.", e);
    }
  }

  public synchronized String startNewGame(String initialJson) {
    preferences.edit().remove(STATE_KEY).commit();
    return normalizeState(initialJson);
  }

  static JSONObject newGameState(JSONObject initial) throws Exception {
    if (initial == null) initial = new JSONObject();
    JSONObject fresh = new JSONObject()
        .put("title", initial.optString("title", "Level 0 : The Lobby"))
        .put("turn", 1).put("mode", "local APK")
        .put("currentLevel", 0).put("currentLevelKey", "0")
        .put("location", initial.optString("location", "Hành lang vàng nhạt — khu vực chưa xác định"))
        .put("player", new JSONObject().put("name", "Cao Minh").put("condition", "Ổn định"))
        .put("party", new JSONArray())
        .put("inventory", new JSONArray()
            .put(new JSONObject().put("name", "Huyết Ma Kiếm"))
            .put(new JSONObject().put("name", "Huyết Ma Chiến Khải"))
            .put(new JSONObject().put("name", "Vạn Tàng Giới")))
        .put("flags", new JSONObject());
    if (initial.has("characterCanon")) fresh.put("characterCanon", initial.get("characterCanon"));
    JSONArray log = initial.optJSONArray("log");
    if (log != null) fresh.put("log", new JSONArray(log.toString()));
    return fresh;
  }

  public synchronized void clear() {
    preferences.edit().remove(STATE_KEY).apply();
  }

  private static final class PreparedTurn {
    final String turnId;
    final int preTurnStateVersion;
    final String action;
    final JSONObject working;
    final JSONArray events;
    final JSONObject selected;
    final TurnRng rng;
    final String replyHint;

    PreparedTurn(String turnId, int preTurnStateVersion, String action, JSONObject working,
                 JSONArray events, JSONObject selected, TurnRng rng, String replyHint) {
      this.turnId = turnId;
      this.preTurnStateVersion = preTurnStateVersion;
      this.action = action;
      this.working = working;
      this.events = events;
      this.selected = selected;
      this.rng = rng;
      this.replyHint = replyHint == null ? "" : replyHint;
    }
  }

  @Override public void close() {
    // Pure Java core has no native model/runtime resources to close.
  }

  private JSONObject parseState(String json) {
    try {
      if (json == null || json.trim().isEmpty()) return new JSONObject();
      return new JSONObject(json);
    } catch (Exception e) {
      return new JSONObject();
    }
  }

  private JSONArray parseArray(String json) {
    try {
      if (json == null || json.trim().isEmpty()) return new JSONArray();
      return new JSONArray(json);
    } catch (Exception e) {
      return new JSONArray();
    }
  }

  private JSONObject deepCopy(JSONObject source) {
    try {
      return new JSONObject(source == null ? "{}" : source.toString());
    } catch (Exception e) {
      return new JSONObject();
    }
  }

  static void applyNarrativeBoundary(LevelCore levelCore, JSONObject before, JSONObject sanitized,
                                     String transitionTarget) throws Exception {
    if (transitionTarget == null) {
      levelCore.validateAndApplyTransition(before, sanitized);
    } else {
      levelCore.applyNarrativeTransition(before, sanitized, transitionTarget);
    }
  }

  private static void copyField(JSONObject source, JSONObject target, String key) throws Exception {
    if (source != null && source.has(key)) target.put(key, source.get(key));
    else target.remove(key);
  }

  private JSONArray normalizeInventory(JSONArray input) {
    JSONArray output = new JSONArray();
    if (input == null) return output;
    Map<String, JSONObject> deduplicated = new LinkedHashMap<>();
    for (int i = 0; i < input.length(); i++) {
      JSONObject item = input.optJSONObject(i);
      if (item == null) continue;
      String name = item.optString("name", "").trim();
      if (name.isEmpty()) continue;
      String id = item.optString("id", "").trim();
      if (id.isEmpty()) id = GameCoreRules.stableItemId(name);
      int quantity = Math.max(1, item.optInt("quantity", 1));
      JSONObject normalized;
      try {
        normalized = new JSONObject(item.toString());
      } catch (Exception e) {
        normalized = new JSONObject();
      }
      try {
        normalized.put("id", id);
        normalized.put("name", name);
        normalized.put("quantity", quantity);
      } catch (Exception ignored) {}
      deduplicated.put(id, normalized);
    }
    for (JSONObject value : deduplicated.values()) output.put(value);
    return output;
  }

  private void incrementTurn(JSONObject state) throws Exception {
    state.put("turn", Math.max(1, state.optInt("turn", 1)) + 1);
  }

  private void advanceGameTime(JSONObject state, String action) throws Exception {
    JSONObject gameTime = state.optJSONObject("gameTime");
    if (gameTime == null) gameTime = new JSONObject();
    int minutes = GameCoreRules.estimateMinutes(action);
    long elapsed = Math.max(0L, gameTime.optLong("elapsedSubjectiveMinutes", 0L));
    gameTime.put("elapsedSubjectiveMinutes", elapsed + minutes);
    gameTime.put("lastAdvanceMinutes", minutes);
    gameTime.put("lastAdvanceReason", "player_action");
    state.put("gameTime", gameTime);
    state.put("saveVersion", CURRENT_SAVE_VERSION);
    advanceExplorerStatusEffects(state);
  }

  private void advanceGameTimeFromBefore(JSONObject before, JSONObject candidate, String action) throws Exception {
    JSONObject prior = before.optJSONObject("gameTime");
    long elapsed = prior == null ? 0L : Math.max(0L, prior.optLong("elapsedSubjectiveMinutes", 0L));
    int minutes = GameCoreRules.estimateMinutes(action);
    JSONObject gameTime = new JSONObject();
    gameTime.put("elapsedSubjectiveMinutes", elapsed + minutes);
    gameTime.put("lastAdvanceMinutes", minutes);
    gameTime.put("lastAdvanceReason", "player_action");
    candidate.put("gameTime", gameTime);
    advanceExplorerStatusEffects(candidate);
  }

  private void advanceExplorerStatusEffects(JSONObject state) throws Exception {
    characterProgressionCore.advanceStatusEffects(state, "cao_minh", "explorer_turn");
    JSONArray party = state.optJSONArray("party");
    if (party == null) return;
    for (int i = 0; i < party.length(); i++) {
      JSONObject member = party.optJSONObject(i);
      if (member == null || !CharacterEncounterCore.isJoinedMember(member)) continue;
      characterProgressionCore.advanceStatusEffects(
          state, member.optString("id", ""), "explorer_turn");
    }
  }

  private String inventoryReply(JSONArray inventory) {
    if (inventory == null || inventory.length() == 0) return "Inventory hiện đang trống.";
    StringBuilder reply = new StringBuilder("Inventory hiện có: ");
    boolean first = true;
    for (int i = 0; i < inventory.length(); i++) {
      JSONObject item = inventory.optJSONObject(i);
      if (item == null) continue;
      String name = item.optString("name", "").trim();
      if (name.isEmpty()) continue;
      if (!first) reply.append(", ");
      first = false;
      reply.append(name);
      int quantity = Math.max(1, item.optInt("quantity", 1));
      if (quantity > 1) reply.append(" x").append(quantity);
    }
    if (first) return "Inventory hiện đang trống.";
    reply.append('.');
    return reply.toString();
  }

  private String partyReply(JSONArray party) {
    if (party == null || party.length() == 0) return "Cao Minh hiện không có đồng đội trong party.";
    StringBuilder reply = new StringBuilder("Party hiện có Cao Minh");
    for (int i = 0; i < party.length(); i++) {
      JSONObject member = party.optJSONObject(i);
      if (member == null) continue;
      String name = member.optString("name", member.optString("id", "")).trim();
      if (!name.isEmpty()) reply.append(", ").append(name);
    }
    reply.append('.');
    return reply.toString();
  }

  private void appendLog(JSONObject state, String action, String reply) throws Exception {
    JSONArray log = state.optJSONArray("log");
    if (log == null) log = new JSONArray();
    log.put(new JSONObject().put("role", "player").put("text", action));
    log.put(new JSONObject().put("role", "gm").put("text", reply));
    state.put("log", log);
  }

  private String encounterKey(JSONObject state) {
    JSONObject flags = state == null ? null : state.optJSONObject("flags");
    return flags == null ? "" : flags.optString("entityEncounterKey", "").trim().toLowerCase(Locale.ROOT);
  }

  static boolean startRandomEntityEncounter(EntityCore entityCore, JSONObject state) throws Exception {
    if (CombatChoiceEngine.isActive(state)) return true;
    entityCore.prepareEncounter(state);
    String encounter = state.optJSONObject("flags").optString("entityEncounterKey", "")
        .trim().toLowerCase(Locale.ROOT);
    if (!CombatChoiceEngine.isKnownEntity(encounter)) return false;
    CombatChoiceEngine.start(state, encounter, lastGmLogIndex(state));
    return CombatChoiceEngine.isActive(state);
  }

  private static int lastGmLogIndex(JSONObject state) {
    JSONArray log = state == null ? null : state.optJSONArray("log");
    if (log == null || log.length() == 0) return 0;
    for (int i = log.length() - 1; i >= 0; i--) {
      JSONObject entry = log.optJSONObject(i);
      if (entry != null && !"player".equals(entry.optString("role"))) return i;
    }
    return Math.max(0, log.length() - 1);
  }

  private void persist(JSONObject state) {
    if (state != null) {
      try {
        characterProgressionCore.normalizeState(state);
        survivalCore.normalizeState(state);
        itemCore.normalizeInventory(state);
        characterDetailCore.projectState(state);
        } catch (Exception e) {
        debug("Character detail projection failed: " + e.getMessage());
      }
    }
    boolean committed = preferences.edit()
        .putString(STATE_KEY, state == null ? "{}" : state.toString())
        .commit();
    if (!committed) throw new IllegalStateException("SharedPreferences commit failed");
  }

  private String response(boolean handled, JSONObject state, String error, String reason, String reply) {
    JSONObject output = new JSONObject();
    try {
      output.put("handled", handled);
      output.put("state", clientSafeState(state == null ? new JSONObject() : state));
      output.put("reason", reason == null ? "" : reason);
      if (error != null && !error.isEmpty()) output.put("error", error);
      if (reply != null && !reply.isEmpty()) output.put("reply", reply);
    } catch (Exception ignored) {}
    return output.toString();
  }

  private JSONObject clientSafeState(JSONObject source) {
    return deepCopy(source);
  }

  private String safeMessage(Exception e) {
    if (e == null || e.getMessage() == null || e.getMessage().trim().isEmpty()) return "Game State Core error";
    return e.getMessage();
  }

  private void debug(String message) {
    if (debugLogging) Log.d(TAG, message);
  }
}

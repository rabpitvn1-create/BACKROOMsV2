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
  private static final int CURRENT_SAVE_VERSION = 11;

  private final SharedPreferences preferences;
  private final boolean debugLogging;
  private final LevelCore levelCore;
  private final EntityCore entityCore;
  private final ItemCore itemCore;
  private final CharacterEncounterCore characterEncounterCore;
  private final StoryCore storyCore;
  private final CharacterProgressionCore characterProgressionCore;
  private final SurvivalCore survivalCore;
  private final CharacterDetailCore characterDetailCore;

  private GameCoreFacade(Context context, boolean debugLogging) {
    Context appContext = context.getApplicationContext();
    this.preferences = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    this.debugLogging = debugLogging;
    this.levelCore = new LevelCore(appContext);
    this.entityCore = new EntityCore(appContext);
    this.itemCore = new ItemCore();
    this.characterEncounterCore = new CharacterEncounterCore();
    this.storyCore = new StoryCore(appContext);
    this.characterProgressionCore = new CharacterProgressionCore();
    this.survivalCore = new SurvivalCore();
    this.characterDetailCore = new CharacterDetailCore();
  }

  public static GameCoreFacade create(Context context, boolean debugLogging) {
    return new GameCoreFacade(context, debugLogging);
  }

  public synchronized String processRule(String legacyStateJson, String action) {
    JSONObject legacy = parseState(legacyStateJson);
    try {
      restoreHiddenDecisionPackage(
          legacy, parseState(preferences.getString(STATE_KEY, "{}")));
      levelCore.normalizeState(legacy);
      characterProgressionCore.normalizeState(legacy);
      survivalCore.normalizeState(legacy);
      itemCore.normalizeInventory(legacy);
      characterEncounterCore.normalizeState(legacy);
      storyCore.normalizeState(legacy);
      syncStoryBoundaryReadiness(legacy);
      CombatChoiceEngine.normalizeTerminalEncounter(legacy);
      String text = action == null ? "" : action.trim();
      if (text.isEmpty()) return response(false, legacy, null, "fallback_required", null);

      if (storyArcComplete(legacy) && StoryCore.isAdvanceAction(text)) {
        if (!levelCore.storyHandoffAvailable(legacy)) {
          persist(legacy);
          return response(true, legacy, "story_handoff_locked", "story_handoff_locked",
              "Ranh giới cốt truyện đã tới nhưng LevelCore chưa xác nhận được cạnh chuyển hợp lệ.");
        }
        int beforeStageIndex = levelCore.stageIndexForState(legacy);
        levelCore.applyStoryArcTransition(legacy);
        int afterStageIndex = levelCore.stageIndexForState(legacy);
        if (afterStageIndex != beforeStageIndex) {
          int stageCoreReward = characterProgressionCore.rewardStageCompletion(legacy, afterStageIndex);
          JSONObject flags = legacy.optJSONObject("flags");
          if (flags == null) flags = new JSONObject();
          flags.put("lastStageCoreReward", stageCoreReward);
          flags.put("lastStageRewardIndex", afterStageIndex);
          legacy.put("flags", flags);
        }
        storyCore.normalizeState(legacy);
        incrementTurn(legacy);
        advanceGameTime(legacy, text);
        characterProgressionCore.applyExplorerTurnRecovery(legacy);
        String reply = "Cao Minh và Lục Trầm tiếp tục qua ranh giới đã được xác nhận.";
        appendLog(legacy, text, reply);
        legacy.put("saveVersion", CURRENT_SAVE_VERSION);
        persist(legacy);
        return response(true, legacy, null, "story_handoff_committed", reply);
      }

      if (storyCore.awaitingEntityAttack(legacy)) {
        JSONObject result = deepCopy(legacy);
        String reply = "Encounter cốt truyện đang chờ. Chỉ có thể chọn Tấn công.";
        persist(result);
        return response(true, result, "story_entity_attack_required",
            "story_entity_attack_required", reply);
      }

      if (storyCore.awaitingDecision(legacy)) {
        JSONObject result = deepCopy(legacy);
        String reply = storyCore.decisionReady(legacy)
            ? "Hãy chọn một hành động đang hiển thị trong khung GAME MASTER."
            : "Các lựa chọn đang được chuẩn bị trong lúc bạn đọc đoạn hiện tại.";
        persist(result);
        return response(true, result, "story_decision_required", "story_decision_required", reply);
      }

      if (storyCore.blocksFreePlayerAction(legacy) && !StoryCore.isAdvanceAction(text)) {
        JSONObject result = deepCopy(legacy);
        String reply = "Đang ở đoạn cắt cảnh của cốt truyện. Hãy tiếp tục cốt truyện để quay lại lượt của Cao Minh.";
        persist(result);
        return response(true, result, "story_cutaway_locked", "story_cutaway_locked", reply);
      }

      if (itemCore.isOpenChestAction(text)) {
        JSONObject result = deepCopy(legacy);
        String itemName = itemCore.openChest(result);
        incrementTurn(result);
        advanceGameTime(result, text);
        characterProgressionCore.applyExplorerTurnRecovery(result);
        JSONObject flags = result.optJSONObject("flags");
        int coreReward = flags == null ? 0 : Math.max(0, flags.optInt("lastChestCoreReward", 0));
        String reply = "Rương chứa " + itemName + " x1. Đã thêm vào Inventory."
            + (coreReward > 0 ? " Nhận +" + coreReward + " Core." : "");
        appendLog(result, "Mở Rương", reply);
        persist(result);
        return response(true, result, null, "chest_opened", reply);
      }

      if (GameCoreRules.isDirectPlayerPickupAction(text)) {
        JSONObject result = deepCopy(legacy);
        String reply = "Không thể nhặt vật phẩm tự do. Loot chỉ nhận từ Entity hoặc Rương.";
        appendLog(result, text, reply);
        persist(result);
        debug("Rejected direct player pickup");
        return response(true, result, "player_pickup_unavailable", "validation_rejected", reply);
      }

      if (GameCoreRules.isInventoryQuery(text)) {
        JSONObject result = deepCopy(legacy);
        incrementTurn(result);
        advanceGameTime(result, text);
        characterProgressionCore.applyExplorerTurnRecovery(result);
        String reply = inventoryReply(result.optJSONArray("inventory"));
        appendLog(result, text, reply);
        persist(result);
        return response(true, result, null, "committed", reply);
      }

      if (GameCoreRules.isPartyQuery(text)) {
        JSONObject result = deepCopy(legacy);
        incrementTurn(result);
        advanceGameTime(result, text);
        characterProgressionCore.applyExplorerTurnRecovery(result);
        String reply = partyReply(result.optJSONArray("party"));
        appendLog(result, text, reply);
        persist(result);
        return response(true, result, null, "committed", reply);
      }

      StoryCore.AuthoredTurn authored =
          storyCore.advanceAndRender(legacy, text, characterEncounterCore);
      if (authored != null) {
        syncStoryBoundaryReadiness(legacy);
        incrementTurn(legacy);
        advanceGameTime(legacy, text);
        characterProgressionCore.applyExplorerTurnRecovery(legacy);
        appendStoryLog(legacy, authored);
        legacy.put("saveVersion", CURRENT_SAVE_VERSION);
        persist(legacy);
        return response(true, legacy, null, "authored_story_committed", authored.reply);
      }

      if (!storyCore.ownsLevelProgression(legacy)) {
        levelCore.rollRouteForExplorerAction(legacy, text);
      }
      itemCore.prepareExplorationLoot(legacy);
      entityCore.prepareEncounter(legacy);
      characterEncounterCore.rollForExplorerAction(legacy, text);
      legacy.put("saveVersion", CURRENT_SAVE_VERSION);
      persist(legacy);
      return response(false, legacy, null, "fallback_required", null);
    } catch (Exception e) {
      debug("processRule failed: " + e.getMessage());
      return response(false, legacy, safeMessage(e), "core_error", null);
    }
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
      storyCore.normalizeState(before);
      JSONObject candidate = parseState(candidateJson);
      JSONObject sanitized = deepCopy(candidate);
      if (CombatChoiceEngine.isKnownEntity(encounterKey(before))) {
        sanitized.put("turn", Math.max(1, before.optInt("turn", 1)));
      }

      copyField(before, sanitized, "inventory");
      copyField(before, sanitized, SurvivalCore.ROOT_KEY);
      copyField(before, sanitized, StoryCore.ROOT_KEY);
      characterProgressionCore.protectFromCandidate(before, sanitized);

      int beforeStageIndex = levelCore.stageIndexForState(before);
      if (transitionTarget == null) {
        levelCore.validateAndApplyTransition(before, sanitized);
      } else {
        levelCore.applyNarrativeTransition(before, sanitized, transitionTarget);
      }
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
      storyCore.normalizeState(sanitized);
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

  public synchronized String processStoryDecision(String stateJson, String choiceId) {
    JSONObject submitted = parseState(stateJson);
    JSONObject state = parseState(preferences.getString(STATE_KEY, "{}"));
    try {
      restoreHiddenDecisionPackage(submitted, state);
      levelCore.normalizeState(state);
      characterProgressionCore.normalizeState(state);
      survivalCore.normalizeState(state);
      itemCore.normalizeInventory(state);
      characterEncounterCore.normalizeState(state);
      storyCore.normalizeState(state);

      JSONObject submittedStory = submitted.optJSONObject(StoryCore.ROOT_KEY);
      JSONObject storedStory = state.optJSONObject(StoryCore.ROOT_KEY);
      String submittedDecisionId = submittedStory == null ? "" : submittedStory.optString("decisionId", "").trim();
      String storedDecisionId = storedStory == null ? "" : storedStory.optString("decisionId", "").trim();
      if (submittedDecisionId.isEmpty() || !submittedDecisionId.equals(storedDecisionId)) {
        throw new IllegalStateException("Story decision trên UI đã cũ.");
      }

      StoryCore.DecisionResolution resolution =
          storyCore.resolveDecision(state, choiceId, characterEncounterCore);
      grantStoryProgressCore(state, resolution);
      advanceGameTime(state, resolution.visibleChoice);
      characterProgressionCore.applyExplorerTurnRecovery(state);
      survivalCore.normalizeState(state);
      itemCore.normalizeInventory(state);
      appendDecisionLog(state, resolution);

      // The encounter is the final gate of the current Explorer Turn.
      entityCore.prepareEncounter(state);
      String encounter = encounterKey(state);
      if (CombatChoiceEngine.isKnownEntity(encounter)) {
        CombatChoiceEngine.start(state, encounter, lastGmLogIndex(state));
      } else {
        incrementTurn(state);
        if (storyCore.hasPendingStoryAdvance(state)) {
          advancePendingStorySequence(state);
        } else if (resolution.looped) {
          storyCore.refreshLoopDecisionContext(state);
        }
      }

      state.put("saveVersion", CURRENT_SAVE_VERSION);
      persist(state);
      return response(true, state, null,
          CombatChoiceEngine.isActive(state) ? "story_random_entity_combat"
              : (resolution.looped ? "story_decision_looped" : "story_turn_committed"),
          resolution.reply);
    } catch (Exception e) {
      return response(false, state, safeMessage(e), "story_decision_rejected", null);
    }
  }

  public synchronized String processStoryEntityAttack(String stateJson) {
    JSONObject state = parseState(preferences.getString(STATE_KEY, "{}"));
    try {
      levelCore.normalizeState(state);
      characterProgressionCore.normalizeState(state);
      survivalCore.normalizeState(state);
      itemCore.normalizeInventory(state);
      characterEncounterCore.normalizeState(state);
      storyCore.normalizeState(state);

      String entityKey = storyCore.consumeEntityAttack(state);
      entityCore.prepareAuthoredEncounter(state, entityKey);

      JSONArray log = state.optJSONArray("log");
      if (log == null) log = new JSONArray();
      log.put(new JSONObject().put("role", "player").put("text", "Tấn công"));
      state.put("log", log);

      CombatChoiceEngine.start(state, entityKey, lastGmLogIndex(state));
      if (!CombatChoiceEngine.isActive(state)) {
        throw new IllegalStateException("Không thể bắt đầu authored Entity combat: " + entityKey);
      }

      state.put("saveVersion", CURRENT_SAVE_VERSION);
      persist(state);
      return response(true, state, null, "story_entity_combat_started", null);
    } catch (Exception e) {
      return response(false, state, safeMessage(e), "story_entity_attack_rejected", null);
    }
  }

  public synchronized String processCombatResolution(String stateJson) {
    JSONObject submitted = parseState(stateJson);
    JSONObject state = parseState(preferences.getString(STATE_KEY, "{}"));
    try {
      restoreHiddenDecisionPackage(submitted, state);
      // Combat dice/participants live on the submitted state. Keep Core-owned story
      // state from persisted storage, but apply the submitted combat snapshot.
      if (submitted.has("combat")) state.put("combat", submitted.get("combat"));
      if (submitted.has("party")) state.put("party", submitted.get("party"));
      if (submitted.has("player")) state.put("player", submitted.get("player"));

      levelCore.normalizeState(state);
      characterProgressionCore.normalizeState(state);
      survivalCore.normalizeState(state);
      itemCore.normalizeInventory(state);
      characterEncounterCore.normalizeState(state);
      storyCore.normalizeState(state);

      boolean wasActive = CombatChoiceEngine.isActive(state);
      CombatChoiceEngine.resolveFinalized(state);
      CombatChoiceEngine.normalizeTerminalEncounter(state);
      boolean active = CombatChoiceEngine.isActive(state);
      JSONObject combat = state.optJSONObject("combat");
      String outcome = combat == null ? "" : combat.optString("outcome", "");

      if (wasActive && !active && ("victory".equals(outcome) || "defeat".equals(outcome))) {
        if ("defeat".equals(outcome)) storyCore.rearmAuthoredEncounterAfterDeath(state);
        incrementTurn(state);
        if (storyCore.hasPendingStoryAdvance(state)) {
          advancePendingStorySequence(state);
        } else if (storyCore.awaitingDecision(state)) {
          storyCore.refreshLoopDecisionContext(state);
        }
      }

      state.put("saveVersion", CURRENT_SAVE_VERSION);
      persist(state);
      return response(true, state, null,
          active ? "combat_turn_resolved" : "combat_finished", null);
    } catch (Exception e) {
      return response(false, state, safeMessage(e), "combat_resolve_rejected", null);
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

  public synchronized String storyPromptContext(String stateJson) {
    JSONObject state = parseState(stateJson);
    try {
      characterEncounterCore.normalizeState(state);
      storyCore.normalizeState(state);
      return storyCore.promptContext(state);
    } catch (Exception e) {
      return "STORY CORE: unavailable. Do not invent authored story progression or Party changes.";
    }
  }

  public synchronized String storyLogMetadata(String stateJson) {
    JSONObject state = parseState(stateJson);
    try {
      characterEncounterCore.normalizeState(state);
      storyCore.normalizeState(state);
      return storyCore.logMetadata(state).toString();
    } catch (Exception e) {
      return "{}";
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

  public synchronized String processStoryCharacterEvent(
      String stateJson, String characterId, String eventType) {
    JSONObject state = parseState(stateJson);
    try {
      levelCore.normalizeState(state);
      characterProgressionCore.normalizeState(state);
      survivalCore.normalizeState(state);
      itemCore.normalizeInventory(state);
      characterEncounterCore.normalizeState(state);
      storyCore.normalizeState(state);
      storyCore.applyCharacterEvent(state, characterId, eventType, characterEncounterCore);
      state.put("saveVersion", CURRENT_SAVE_VERSION);
      persist(state);
      return response(true, state, null, "story_event_committed", null);
    } catch (Exception e) {
      return response(false, state, safeMessage(e), "story_event_rejected", null);
    }
  }

  public synchronized String processItemAction(String stateJson, String itemId, String operation,
                                               String targetId, int quantity) {
    return processItemAction(stateJson, "cao_minh", itemId, operation, targetId, quantity);
  }

  public synchronized String processItemAction(String stateJson, String ownerId, String itemId,
                                               String operation, String targetId, int quantity) {
    JSONObject state = parseState(stateJson);
    try {
      levelCore.normalizeState(state);
      characterProgressionCore.normalizeState(state);
      survivalCore.normalizeState(state);
      itemCore.normalizeInventory(state);
      characterEncounterCore.normalizeState(state);
      storyCore.normalizeState(state);
      if (storyCore.awaitingDecision(state) || storyCore.awaitingEntityAttack(state)
          || storyCore.hasPendingStoryAdvance(state)) {
        return response(false, state,
            "Hãy xử lý lượt cốt truyện hiện tại trước khi thay đổi Inventory.",
            "story_decision_locked", null);
      }
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
      storyCore.normalizeState(state);
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
      restoreHiddenDecisionPackage(state, persisted);
      levelCore.normalizeState(state);
      characterProgressionCore.normalizeState(state);
      survivalCore.normalizeState(state);
      itemCore.normalizeInventory(state);
      characterEncounterCore.normalizeState(state);
      storyCore.normalizeState(state);
      CombatChoiceEngine.normalizeTerminalEncounter(state);
      characterProgressionCore.applyExplorerTurnRecovery(state);
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

  public synchronized void clear() {
    preferences.edit().remove(STATE_KEY).apply();
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

  private void copyField(JSONObject source, JSONObject target, String key) throws Exception {
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

  private boolean storyArcComplete(JSONObject state) {
    JSONObject story = state == null ? null : state.optJSONObject(StoryCore.ROOT_KEY);
    return story != null && story.optBoolean("active", false) && story.optBoolean("arcComplete", false);
  }

  private void syncStoryBoundaryReadiness(JSONObject state) throws Exception {
    if (!storyArcComplete(state)) return;
    JSONObject route = state.optJSONObject(LevelCore.ROUTE_STATE);
    if (route != null && route.optBoolean("storyExitReady", false)) return;
    levelCore.markStoryBoundaryReady(state);
  }

  private void appendLog(JSONObject state, String action, String reply) throws Exception {
    JSONArray log = state.optJSONArray("log");
    if (log == null) log = new JSONArray();
    log.put(new JSONObject().put("role", "player").put("text", action));
    log.put(new JSONObject().put("role", "gm").put("text", reply));
    state.put("log", log);
  }

  static int grantStoryProgressCore(
      JSONObject state, StoryCore.DecisionResolution resolution) throws Exception {
    if (state == null || resolution == null) return 0;

    int stageIndex = LevelCore.stageIndex(state);
    JSONObject flags = state.optJSONObject("flags");
    if (flags == null) flags = new JSONObject();

    boolean rewardable = !resolution.looped
        && (StoryCore.OUTCOME_CANON.equals(resolution.outcome)
            || StoryCore.OUTCOME_CONVERGE.equals(resolution.outcome));
    if (!rewardable) {
      flags.put("lastStoryCoreReward", 0);
      flags.put("lastStoryCoreStageIndex", stageIndex);
      state.put("flags", flags);
      return 0;
    }

    int requested = CharacterProgressionCore.scaledCoreReward(
        CharacterProgressionCore.STORY_PROGRESS_BASE_CORE, stageIndex);
    CharacterProgressionCore progression = new CharacterProgressionCore();
    int granted = progression.grantCore(state, requested);

    flags.put("lastStoryCoreReward", granted);
    flags.put("lastStoryCoreStageIndex", stageIndex);
    state.put("flags", flags);
    return granted;
  }

  private String encounterKey(JSONObject state) {
    JSONObject flags = state == null ? null : state.optJSONObject("flags");
    return flags == null ? "" : flags.optString("entityEncounterKey", "").trim().toLowerCase(Locale.ROOT);
  }

  private int lastGmLogIndex(JSONObject state) {
    JSONArray log = state == null ? null : state.optJSONArray("log");
    if (log == null || log.length() == 0) return 0;
    for (int i = log.length() - 1; i >= 0; i--) {
      JSONObject entry = log.optJSONObject(i);
      if (entry != null && !"player".equals(entry.optString("role"))) return i;
    }
    return Math.max(0, log.length() - 1);
  }

  private void advancePendingStorySequence(JSONObject state) throws Exception {
    StoryCore.AuthoredTurn authored = storyCore.advancePendingTurn(state, characterEncounterCore);
    int safety = 0;
    while (authored != null && safety++ < 64) {
      appendStoryContinuationLog(state, authored);
      if (!"cutaway".equals(authored.visibility)) return;
      authored = storyCore.advanceAndRender(
          state, StoryCore.ADVANCE_ACTION_VI, characterEncounterCore);
    }
    if (safety >= 64) throw new IllegalStateException("Cutaway auto-advance exceeded safety bound.");
  }

  private void appendStoryContinuationLog(
      JSONObject state, StoryCore.AuthoredTurn authored) throws Exception {
    JSONArray log = state.optJSONArray("log");
    if (log == null) log = new JSONArray();
    JSONObject gm = new JSONObject()
        .put("role", "gm")
        .put("text", authored.reply)
        .put("storyThread", authored.thread)
        .put("storyVisibility", authored.visibility)
        .put("storyChapter", authored.chapterId)
        .put("storySegmentId", authored.segmentId)
        .put("storyMode", authored.mode)
        .put("authored", true);
    log.put(gm);
    state.put("log", log);
  }

  private void appendDecisionLog(
      JSONObject state, StoryCore.DecisionResolution resolution) throws Exception {
    JSONArray log = state.optJSONArray("log");
    if (log == null) log = new JSONArray();
    log.put(new JSONObject().put("role", "player").put("text", resolution.visibleChoice));

    if (resolution.reply != null && !resolution.reply.trim().isEmpty()) {
      JSONObject gm = new JSONObject().put("role", "gm").put("text", resolution.reply);
      JSONObject metadata = storyCore.logMetadata(state);
      java.util.Iterator<String> keys = metadata.keys();
      while (keys.hasNext()) {
        String key = keys.next();
        gm.put(key, metadata.get(key));
      }
      gm.put("authored", false);
      log.put(gm);
    }
    state.put("log", log);
  }

  private void appendStoryLog(
      JSONObject state, StoryCore.AuthoredTurn authored) throws Exception {
    JSONArray log = state.optJSONArray("log");
    if (log == null) log = new JSONArray();
    JSONObject gm = new JSONObject()
        .put("role", "gm")
        .put("text", authored.reply)
        .put("storyThread", authored.thread)
        .put("storyVisibility", authored.visibility)
        .put("storyChapter", authored.chapterId)
        .put("storySegmentId", authored.segmentId)
        .put("storyMode", authored.mode)
        .put("authored", true);
    log.put(gm);
    state.put("log", log);
  }

  private void persist(JSONObject state) {
    if (state != null) {
      try {
        characterProgressionCore.normalizeState(state);
        survivalCore.normalizeState(state);
        itemCore.normalizeInventory(state);
        characterDetailCore.projectState(state);
        storyCore.normalizeState(state);
      } catch (Exception e) {
        debug("Character detail projection failed: " + e.getMessage());
      }
    }
    preferences.edit().putString(STATE_KEY, state == null ? "{}" : state.toString()).apply();
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
    JSONObject safe = deepCopy(source);
    try {
      JSONObject story = safe.optJSONObject(StoryCore.ROOT_KEY);
      if (story == null) return safe;
      JSONObject pack = story.optJSONObject("decisionPackage");
      if (pack != null) {
        pack.remove("outcomes");
        story.put("decisionPackage", pack);
      }
      safe.put(StoryCore.ROOT_KEY, story);
    } catch (Exception ignored) {}
    return safe;
  }

  private void restoreHiddenDecisionPackage(JSONObject submitted, JSONObject persisted) {
    try {
      JSONObject submittedStory = submitted == null ? null : submitted.optJSONObject(StoryCore.ROOT_KEY);
      JSONObject persistedStory = persisted == null ? null : persisted.optJSONObject(StoryCore.ROOT_KEY);
      if (submittedStory == null || persistedStory == null) return;
      if (!submittedStory.optBoolean("awaitingDecision", false)
          || !persistedStory.optBoolean("awaitingDecision", false)) return;
      String submittedId = submittedStory.optString("decisionId", "").trim();
      String persistedId = persistedStory.optString("decisionId", "").trim();
      if (submittedId.isEmpty() || !submittedId.equals(persistedId)) return;
      JSONObject persistedPack = persistedStory.optJSONObject("decisionPackage");
      if (persistedPack == null || persistedPack.optJSONObject("outcomes") == null) return;
      submittedStory.put("decisionPackage", new JSONObject(persistedPack.toString()));
      submitted.put(StoryCore.ROOT_KEY, submittedStory);
    } catch (Exception ignored) {}
  }

  private String safeMessage(Exception e) {
    if (e == null || e.getMessage() == null || e.getMessage().trim().isEmpty()) return "Game State Core error";
    return e.getMessage();
  }

  private void debug(String message) {
    if (debugLogging) Log.d(TAG, message);
  }
}

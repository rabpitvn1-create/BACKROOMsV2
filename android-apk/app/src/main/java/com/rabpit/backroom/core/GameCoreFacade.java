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
  private static final int CURRENT_SAVE_VERSION = 12;

  private final SharedPreferences preferences;
  private final boolean debugLogging;
  private final LevelCore levelCore;
  private final EntityCore entityCore;
  private final ItemCore itemCore;
  private final CharacterEncounterCore characterEncounterCore;
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
    this.characterProgressionCore = new CharacterProgressionCore();
    this.survivalCore = new SurvivalCore();
    this.characterDetailCore = new CharacterDetailCore();
  }

  public static GameCoreFacade create(Context context, boolean debugLogging) {
    return new GameCoreFacade(context, debugLogging);
  }


  public synchronized String processRule(String legacyStateJson, String action) {
    JSONObject state = parseState(legacyStateJson);
    JSONObject stored = parseState(preferences.getString(STATE_KEY, "{}"));
    if (stored.length() > 0) state = stored;
    try {
      state.remove("story");
      levelCore.normalizeState(state);
      characterProgressionCore.normalizeState(state);
      survivalCore.normalizeState(state);
      itemCore.normalizeInventory(state);
      characterEncounterCore.normalizeState(state);
      CombatChoiceEngine.normalizeTerminalEncounter(state);

      String text = action == null ? "" : action.trim();
      if (text.isEmpty()) return response(false, state, null, "fallback_required", null);

      if (itemCore.isOpenChestAction(text)) {
        JSONObject result = deepCopy(state);
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
        JSONObject result = deepCopy(state);
        String reply = "Không thể nhặt vật phẩm tự do. Loot chỉ nhận từ Entity hoặc Rương.";
        appendLog(result, text, reply);
        persist(result);
        return response(true, result, "player_pickup_unavailable", "validation_rejected", reply);
      }

      if (GameCoreRules.isInventoryQuery(text)) {
        JSONObject result = deepCopy(state);
        incrementTurn(result);
        advanceGameTime(result, text);
        characterProgressionCore.applyExplorerTurnRecovery(result);
        String reply = inventoryReply(result.optJSONArray("inventory"));
        appendLog(result, text, reply);
        persist(result);
        return response(true, result, null, "committed", reply);
      }

      if (GameCoreRules.isPartyQuery(text)) {
        JSONObject result = deepCopy(state);
        incrementTurn(result);
        advanceGameTime(result, text);
        characterProgressionCore.applyExplorerTurnRecovery(result);
        String reply = partyReply(result.optJSONArray("party"));
        appendLog(result, text, reply);
        persist(result);
        return response(true, result, null, "committed", reply);
      }

      levelCore.rollRouteForExplorerAction(state, text);
      itemCore.prepareExplorationLoot(state);
      entityCore.prepareEncounter(state);
      characterEncounterCore.rollForExplorerAction(state, text);
      state.put("saveVersion", CURRENT_SAVE_VERSION);
      persist(state);
      return response(false, state, null, "fallback_required", null);
    } catch (Exception e) {
      debug("processRule failed: " + e.getMessage());
      return response(false, state, safeMessage(e), "core_error", null);
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
      before.remove("story");
      levelCore.normalizeState(before);
      characterProgressionCore.normalizeState(before);
      survivalCore.normalizeState(before);
      itemCore.normalizeInventory(before);
      characterEncounterCore.normalizeState(before);

      JSONObject candidate = parseState(candidateJson);
      candidate.remove("story");
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
      state.remove("story");
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
      state.remove("story");
      JSONObject combat = state.optJSONObject("combat");
      if (combat == null || !"defeat".equals(combat.optString("outcome", ""))
          || !combat.optBoolean("deathRestartPending", false)) {
        return response(false, state, "Không có lượt hồi sinh đang chờ.",
            "death_restart_unavailable", null);
      }

      LevelCore.returnToCurrentLevelStart(state);
      combat.put("deathRestartPending", false)
          .put("deathReturnJourneyPending", false)
          .put("deathReturnJourneyStarted", false)
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
      state.remove("story");
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
      state.remove("story");
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
      state = persisted.length() > 0 ? persisted : newGameState(state);
      state.remove("story");
      levelCore.normalizeState(state);
      characterProgressionCore.normalizeState(state);
      survivalCore.normalizeState(state);
      itemCore.normalizeInventory(state);
      characterEncounterCore.normalizeState(state);
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



  public synchronized String commitRuntimeState(String stateJson) {
    JSONObject state = parseState(stateJson);
    try {
      state.remove("story");
      persist(state);
      return clientSafeState(state).toString();
    } catch (Exception e) {
      throw new IllegalStateException("Không thể lưu runtime state.", e);
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
        state.remove("story");
        characterProgressionCore.normalizeState(state);
        survivalCore.normalizeState(state);
        itemCore.normalizeInventory(state);
        characterDetailCore.projectState(state);
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
    safe.remove("story");
    return safe;
  }








  private String safeMessage(Exception e) {
    if (e == null || e.getMessage() == null || e.getMessage().trim().isEmpty()) return "Game State Core error";
    return e.getMessage();
  }

  private void debug(String message) {
    if (debugLogging) Log.d(TAG, message);
  }
}

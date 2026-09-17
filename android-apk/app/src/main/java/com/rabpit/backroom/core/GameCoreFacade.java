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
  private static final int CURRENT_SAVE_VERSION = 3;

  private final SharedPreferences preferences;
  private final boolean debugLogging;

  private GameCoreFacade(Context context, boolean debugLogging) {
    this.preferences = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    this.debugLogging = debugLogging;
  }

  public static GameCoreFacade create(Context context, boolean debugLogging) {
    return new GameCoreFacade(context, debugLogging);
  }

  public synchronized String processRule(String legacyStateJson, String action) {
    JSONObject legacy = parseState(legacyStateJson);
    try {
      String text = action == null ? "" : action.trim();
      if (text.isEmpty()) return response(false, legacy, null, "fallback_required", null);

      if (GameCoreRules.isDirectPlayerPickupAction(text)) {
        JSONObject result = deepCopy(legacy);
        String reply = "[Warning] This action is not available.";
        appendLog(result, text, reply);
        persist(result);
        debug("Rejected direct player pickup");
        return response(true, result, "player_pickup_unavailable", "validation_rejected", reply);
      }

      if (GameCoreRules.isInventoryQuery(text)) {
        JSONObject result = deepCopy(legacy);
        incrementTurn(result);
        advanceGameTime(result, text);
        String reply = inventoryReply(result.optJSONArray("inventory"));
        appendLog(result, text, reply);
        persist(result);
        return response(true, result, null, "committed", reply);
      }

      if (GameCoreRules.isPartyQuery(text)) {
        JSONObject result = deepCopy(legacy);
        incrementTurn(result);
        advanceGameTime(result, text);
        String reply = partyReply(result.optJSONArray("party"));
        appendLog(result, text, reply);
        persist(result);
        return response(true, result, null, "committed", reply);
      }

      return response(false, legacy, null, "fallback_required", null);
    } catch (Exception e) {
      debug("processRule failed: " + e.getMessage());
      return response(false, legacy, safeMessage(e), "core_error", null);
    }
  }

  public synchronized String processValidatedCandidate(String beforeJson, String candidateJson, String action) {
    JSONObject before = parseState(beforeJson);
    try {
      JSONObject candidate = parseState(candidateJson);
      JSONObject sanitized = deepCopy(candidate);

      if (GameCoreRules.inventoryMutationLocked(action)) {
        copyField(before, sanitized, "inventory");
      } else if (candidate.has("inventory")) {
        sanitized.put("inventory", normalizeInventory(candidate.optJSONArray("inventory")));
      } else {
        copyField(before, sanitized, "inventory");
      }

      sanitized.put("party", sanitizeParty(before.optJSONArray("party"), candidate.optJSONArray("party")));
      sanitized.put("saveVersion", CURRENT_SAVE_VERSION);
      advanceGameTimeFromBefore(before, sanitized, action);

      persist(sanitized);
      return response(true, sanitized, null, "gemini_delta_committed", null);
    } catch (Exception e) {
      debug("processValidatedCandidate failed: " + e.getMessage());
      return response(false, before, safeMessage(e), "gemini_delta_rejected", null);
    }
  }

  public synchronized String currentCoreState() {
    return preferences.getString(STATE_KEY, "{}");
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

  private JSONArray sanitizeParty(JSONArray before, JSONArray candidate) {
    JSONArray safeBefore = before == null ? new JSONArray() : before;
    JSONArray safeCandidate = candidate == null ? new JSONArray() : candidate;
    Map<String, JSONObject> existing = new LinkedHashMap<>();
    for (int i = 0; i < safeBefore.length(); i++) {
      JSONObject member = safeBefore.optJSONObject(i);
      if (member == null) continue;
      String id = memberId(member);
      if (!id.isEmpty()) existing.put(id, member);
    }

    JSONArray output = new JSONArray();
    for (int i = 0; i < safeCandidate.length(); i++) {
      JSONObject member = safeCandidate.optJSONObject(i);
      if (member == null) continue;
      String id = memberId(member);
      if (id.isEmpty()) continue;
      boolean alreadyMember = existing.containsKey(id);
      boolean confirmedJoin = member.optBoolean("joinConfirmed", false) && member.optBoolean("present", false);
      if (alreadyMember || confirmedJoin) {
        try {
          output.put(new JSONObject(member.toString()));
        } catch (Exception ignored) {
          output.put(member);
        }
      }
    }
    return output;
  }

  private String memberId(JSONObject member) {
    String id = member.optString("id", "").trim();
    if (!id.isEmpty()) return id;
    return member.optString("name", "").trim().toLowerCase(Locale.ROOT);
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
    if (party == null || party.length() == 0) return "Kai hiện không có đồng đội trong party.";
    StringBuilder reply = new StringBuilder("Party hiện có Kai");
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

  private void persist(JSONObject state) {
    preferences.edit().putString(STATE_KEY, state == null ? "{}" : state.toString()).apply();
  }

  private String response(boolean handled, JSONObject state, String error, String reason, String reply) {
    JSONObject output = new JSONObject();
    try {
      output.put("handled", handled);
      output.put("state", state == null ? new JSONObject() : state);
      output.put("reason", reason == null ? "" : reason);
      if (error != null && !error.isEmpty()) output.put("error", error);
      if (reply != null && !reply.isEmpty()) output.put("reply", reply);
    } catch (Exception ignored) {}
    return output.toString();
  }

  private String safeMessage(Exception e) {
    if (e == null || e.getMessage() == null || e.getMessage().trim().isEmpty()) return "Game State Core error";
    return e.getMessage();
  }

  private void debug(String message) {
    if (debugLogging) Log.d(TAG, message);
  }
}

package com.rabpit.backroom.core;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

final class LevelCore {
  interface IntRng {
    int nextInt(int bound);
  }

  static final String ROUTE_STATE = "levelRoute";
  static final int ROUTE_SUCCESS_PERCENT = 60;
  static final int ROUTE_REQUIRED_STREAK = 10;

  private static final String KNOWLEDGE_ASSET = "knowledge/knowledge_db.json";
  private static final String SNAPSHOT_MANIFEST_ASSET = "level_snapshots/drive/manifest.json";
  private static final int LEVEL_MISMATCH = -2;
  private static final int ROUTE_ROLL_BOUND = 100;

  private final Map<Integer, String> canonByLevel = new LinkedHashMap<>();
  private final Map<Integer, JSONArray> snapshotsByLevel = new LinkedHashMap<>();
  private final Map<Integer, String> visualTypeByLevel = new LinkedHashMap<>();
  private final IntRng rng;
  private String snapshotRoot = "level_snapshots/wiki";

  LevelCore(Context context) {
    this(context, bound -> ThreadLocalRandom.current().nextInt(bound));
  }

  LevelCore(Context context, IntRng rng) {
    if (rng == null) throw new IllegalArgumentException("rng is required");
    this.rng = rng;
    if (context != null) {
      loadKnowledge(context);
      loadSnapshotManifest(context);
    }
  }

  void normalizeState(JSONObject state) throws Exception {
    int level = resolveLevel(state);
    state.put("currentLevel", level);
    normalizeRouteState(state, level);
  }

  void rollRouteForExplorerAction(JSONObject state, String action) throws Exception {
    normalizeState(state);
    if (!GameCoreRules.isRouteExplorationAction(action)) return;

    int level = resolveLevel(state);
    JSONObject route = normalizeRouteState(state, level);
    if (route.optBoolean("exitAvailable", false)) return;

    int turn = Math.max(1, state.optInt("turn", 1));
    if (route.optInt("lastRollTurn", -1) == turn) return;

    int streak = route.optInt("streak", 0);
    if (streak == 0 && route.optString("originLocation", "").trim().isEmpty()) {
      route.put("originLocation", state.optString("location", ""));
    }

    int roll = nextRoll(ROUTE_ROLL_BOUND);
    route.put("lastRollTurn", turn);

    if (roll < ROUTE_SUCCESS_PERCENT) {
      streak = Math.min(ROUTE_REQUIRED_STREAK, streak + 1);
      route.put("streak", streak);
      route.remove("returnLocation");
      if (streak >= ROUTE_REQUIRED_STREAK) {
        route.put("exitAvailable", true);
        route.put("lastResult", "EXIT_AVAILABLE");
      } else {
        route.put("exitAvailable", false);
        route.put("lastResult", "SUCCESS");
      }
    } else {
      String origin = route.optString("originLocation", state.optString("location", ""));
      route.put("streak", 0);
      route.put("exitAvailable", false);
      route.put("lastResult", "RESET");
      route.put("returnLocation", origin);
      route.remove("originLocation");
    }

    state.put(ROUTE_STATE, route);
  }

  void validateAndApplyTransition(JSONObject before, JSONObject candidate) throws Exception {
    normalizeState(before);
    int from = resolveLevel(before);
    JSONObject beforeRoute = normalizeRouteState(before, from);
    copyRouteState(beforeRoute, candidate);

    boolean resetThisTurn =
        beforeRoute.optInt("lastRollTurn", -1) == Math.max(1, before.optInt("turn", 1))
            && "RESET".equals(beforeRoute.optString("lastResult", ""));

    if (resetThisTurn) {
      candidate.put("currentLevel", from);
      String returnLocation = beforeRoute.optString("returnLocation", "").trim();
      if (!returnLocation.isEmpty()) candidate.put("location", returnLocation);
      return;
    }

    int explicit = candidate != null && candidate.has("currentLevel")
        ? candidate.optInt("currentLevel", -1)
        : -1;
    int requested = requestedLevel(
        explicit,
        candidate == null ? "" : candidate.optString("location", ""),
        from);

    if (requested == LEVEL_MISMATCH) {
      throw new IllegalArgumentException("currentLevel does not match location");
    }
    if (!GameCoreRules.levelTransitionAllowed(from, requested)) {
      throw new IllegalArgumentException("Invalid Level transition: " + from + " -> " + requested);
    }

    if (requested != from) {
      if (!beforeRoute.optBoolean("exitAvailable", false)) {
        throw new IllegalArgumentException("Level transition is locked until the hidden route chain completes");
      }
      candidate.put("currentLevel", requested);
      candidate.put(ROUTE_STATE, newRouteState(requested));
      return;
    }

    candidate.put("currentLevel", from);
  }

  String promptContext(JSONObject state) {
    int level = resolveLevel(state);
    String canon = canonByLevel.get(level);
    if (canon == null || canon.trim().isEmpty()) canon = "Level " + level + " canon is unavailable.";

    StringBuilder allowed = new StringBuilder();
    for (int target = 0; target <= 6; target++) {
      if (target == level || !GameCoreRules.levelTransitionAllowed(level, target)) continue;
      if (allowed.length() > 0) allowed.append(", ");
      allowed.append(target);
    }

    JSONObject route;
    try {
      route = normalizeRouteState(state, level);
    } catch (Exception e) {
      route = new JSONObject();
    }

    int turn = Math.max(1, state.optInt("turn", 1));
    boolean rolledThisTurn = route.optInt("lastRollTurn", -1) == turn;
    String result = rolledThisTurn ? route.optString("lastResult", "") : "";
    boolean exitAvailable = route.optBoolean("exitAvailable", false);

    String routeInstruction;
    if ("RESET".equals(result)) {
      String returnLocation = route.optString("returnLocation", state.optString("location", ""));
      routeInstruction =
          "HIDDEN ROUTE OUTCOME THIS TURN: RESET. Narrate naturally that the attempted route folds, loops, or returns Kai toward familiar ground"
              + (returnLocation.trim().isEmpty() ? "." : " near: " + returnLocation + ".")
              + " Never mention a roll, streak, probability, reset counter, or hidden system.";
    } else if ("EXIT_AVAILABLE".equals(result) || exitAvailable) {
      routeInstruction =
          "HIDDEN ROUTE OUTCOME: EXIT AVAILABLE. You may now establish a real boundary/route to a valid adjacent Level if the player's action naturally reaches or crosses it. Never reveal the hidden route system.";
    } else if ("SUCCESS".equals(result)) {
      routeInstruction =
          "HIDDEN ROUTE OUTCOME THIS TURN: SUCCESS. Narrate meaningful continued exploration inside the current Level, but do not reveal an exit yet. Never mention a roll, streak, probability, or hidden system.";
    } else {
      routeInstruction =
          "HIDDEN ROUTE OUTCOME THIS TURN: NO ROUTE ROLL. Keep route progression implicit and never reveal the hidden system.";
    }

    String transitionInstruction = exitAvailable
        ? "LEVEL TRANSITION: AVAILABLE. Only a valid adjacent Level may be reached, and only when the narration/action actually crosses its boundary."
        : "LEVEL TRANSITION: LOCKED. Keep currentLevel unchanged and keep the environment inside the current Level. Do not pre-narrate architecture from another Level.";

    return "CURRENT LEVEL: " + level + "\n" +
      "LEVEL CANON: " + canon + "\n" +
      "VALID ADJACENT LEVEL TRANSITIONS: " + (allowed.length() == 0 ? "none" : allowed) + "\n" +
      transitionInstruction + "\n" +
      routeInstruction;
  }

  String snapshotDescriptor(JSONObject state) {
    int level = resolveLevel(state);
    JSONArray assets = snapshotsByLevel.get(level);
    JSONObject output = new JSONObject();
    try {
      output.put("level", level);
      output.put("visualType", visualTypeByLevel.containsKey(level) ? visualTypeByLevel.get(level) : "scene");
      if (assets != null && assets.length() > 0) {
        int turn = Math.max(1, state.optInt("turn", 1));
        int index = Math.floorMod(turn - 1, assets.length());
        String relative = assets.optString(index, "");
        if (!relative.isEmpty()) output.put("path", "file:///android_asset/" + snapshotRoot + "/" + relative);
      }
    } catch (Exception ignored) {}
    return output.toString();
  }

  static int requestedLevel(int explicit, String location, int fallback) {
    int inferred = GameCoreRules.levelFromLocation(location);
    if (explicit >= 0 && explicit <= 6) {
      if (inferred >= 0 && inferred != explicit) return LEVEL_MISMATCH;
      return explicit;
    }
    return inferred >= 0 ? inferred : fallback;
  }

  private JSONObject normalizeRouteState(JSONObject state, int level) throws Exception {
    JSONObject route = state.optJSONObject(ROUTE_STATE);
    if (route == null || route.optInt("level", -1) != level) {
      route = newRouteState(level);
      state.put(ROUTE_STATE, route);
      return route;
    }

    int streak = Math.max(0, Math.min(ROUTE_REQUIRED_STREAK, route.optInt("streak", 0)));
    boolean exitAvailable = streak >= ROUTE_REQUIRED_STREAK;
    route.put("level", level);
    route.put("streak", exitAvailable ? ROUTE_REQUIRED_STREAK : streak);
    route.put("exitAvailable", exitAvailable);
    state.put(ROUTE_STATE, route);
    return route;
  }

  private static JSONObject newRouteState(int level) throws Exception {
    return new JSONObject()
        .put("level", level)
        .put("streak", 0)
        .put("exitAvailable", false)
        .put("lastRollTurn", -1)
        .put("lastResult", "");
  }

  private static void copyRouteState(JSONObject route, JSONObject candidate) throws Exception {
    if (candidate == null) throw new IllegalArgumentException("candidate state is required");
    candidate.put(ROUTE_STATE, new JSONObject(route.toString()));
  }

  private int resolveLevel(JSONObject state) {
    if (state != null && state.has("currentLevel")) {
      int explicit = state.optInt("currentLevel", -1);
      if (explicit >= 0 && explicit <= 6) return explicit;
    }
    int inferred = GameCoreRules.levelFromLocation(state == null ? "" : state.optString("location", ""));
    return inferred >= 0 ? inferred : 0;
  }

  private int nextRoll(int bound) {
    int value = rng.nextInt(bound);
    if (value < 0 || value >= bound) {
      throw new IllegalStateException("RNG returned an out-of-range value");
    }
    return value;
  }

  private void loadKnowledge(Context context) {
    try {
      JSONObject root = new JSONObject(readAsset(context, KNOWLEDGE_ASSET));
      JSONArray records = root.optJSONArray("records");
      if (records == null) return;
      for (int i = 0; i < records.length(); i++) {
        JSONObject record = records.optJSONObject(i);
        if (record == null || !"LEVEL".equals(record.optString("domain"))) continue;
        String id = record.optString("id", "");
        if (!id.startsWith("LEVEL.")) continue;
        try {
          int level = Integer.parseInt(id.substring("LEVEL.".length()));
          if (level >= 0 && level <= 6) canonByLevel.put(level, record.optString("text", ""));
        } catch (NumberFormatException ignored) {}
      }
    } catch (Exception ignored) {}
  }

  private void loadSnapshotManifest(Context context) {
    try {
      JSONObject manifest = new JSONObject(readAsset(context, SNAPSHOT_MANIFEST_ASSET));
      snapshotRoot = manifest.optString("root", snapshotRoot);
      JSONObject levels = manifest.optJSONObject("levels");
      if (levels == null) return;
      for (int level = 0; level <= 6; level++) {
        JSONObject entry = levels.optJSONObject(String.valueOf(level));
        if (entry == null) continue;
        JSONArray assets = entry.optJSONArray("assets");
        if (assets != null && assets.length() > 0) snapshotsByLevel.put(level, assets);
        visualTypeByLevel.put(level, entry.optString("visualType", "scene"));
      }
    } catch (Exception ignored) {}
  }

  private String readAsset(Context context, String path) throws Exception {
    StringBuilder text = new StringBuilder();
    try (InputStream input = context.getAssets().open(path);
         BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"))) {
      String line;
      while ((line = reader.readLine()) != null) text.append(line).append('\n');
    }
    return text.toString();
  }
}

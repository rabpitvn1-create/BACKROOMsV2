package com.rabpit.backroom.core;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class LevelCore {
  interface IntRng {
    int nextInt(int bound);
  }

  static final String ROUTE_STATE = "levelRoute";
  static final String LEVEL_KEY = "currentLevelKey";
  static final int ROUTE_SUCCESS_PERCENT = 60;
  static final int ROUTE_TRIPLE_SUCCESS_PERCENT = 5;
  static final int ROUTE_TRIPLE_SUCCESS_INCREMENT = 3;
  static final int ROUTE_REQUIRED_STREAK = 10;
  static final String LEVEL_ZERO_START_LOCATION =
      "Level 0 / The Lobby — khu phòng vàng ban đầu sau khi đi qua cổng không gian";

  private static final String[] LEVEL_ZERO_PROGRESSION = {
      "0", "0.1", "0.2", "0.5", "0.7", "manila_room", "the_torment", "red_rooms", "1"
  };
  private static final Pattern LEVEL_TOKEN =
      Pattern.compile("(?i)(?:^|\\b)level\\s*([0-6](?:\\.[0-9]+)?)");
  private static final String LEVEL_KNOWLEDGE_ASSET = "knowledge/level_knowledge.json";
  private static final String LEGACY_KNOWLEDGE_ASSET = "knowledge/knowledge_db.json";
  private static final String SNAPSHOT_MANIFEST_ASSET = "level_snapshots/drive/manifest.json";
  private static final String INVALID_LEVEL_KEY = "__invalid__";
  private static final int LEVEL_MISMATCH = -2;
  private static final int ROUTE_ROLL_BOUND = 100;

  private final Map<String, JSONObject> knowledgeByLevelKey = new LinkedHashMap<>();
  private final Map<String, String> legacyCanonByLevelKey = new LinkedHashMap<>();
  private final JSONArray knowledgeSectionOrder = new JSONArray();
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
      loadLevelKnowledge(context);
      if (knowledgeByLevelKey.isEmpty()) loadLegacyKnowledge(context);
      loadSnapshotManifest(context);
    }
  }

  LevelCore(String levelKnowledgeJson, IntRng rng) {
    if (rng == null) throw new IllegalArgumentException("rng is required");
    this.rng = rng;
    loadLevelKnowledgeText(levelKnowledgeJson);
  }

  void normalizeState(JSONObject state) throws Exception {
    String levelKey = resolveLevelKey(state);
    int level = parentLevel(levelKey);
    state.put("currentLevel", level);
    state.put(LEVEL_KEY, levelKey);
    normalizeRouteState(state, levelKey);
  }

  void rollRouteForExplorerAction(JSONObject state, String action) throws Exception {
    normalizeState(state);
    if (!GameCoreRules.isRouteExplorationAction(action)) return;

    String levelKey = resolveLevelKey(state);
    JSONObject route = normalizeRouteState(state, levelKey);
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
      int increment = roll < ROUTE_TRIPLE_SUCCESS_PERCENT
          ? ROUTE_TRIPLE_SUCCESS_INCREMENT
          : 1;
      streak = Math.min(ROUTE_REQUIRED_STREAK, streak + increment);
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

  static void resetToLevelZeroStart(JSONObject state) throws Exception {
    if (state == null) return;
    state.put("currentLevel", 0);
    state.put(LEVEL_KEY, "0");
    state.put("location", LEVEL_ZERO_START_LOCATION);
    state.put(ROUTE_STATE, newRouteState("0"));
  }

  void validateAndApplyTransition(JSONObject before, JSONObject candidate) throws Exception {
    normalizeState(before);
    String fromKey = resolveLevelKey(before);
    JSONObject beforeRoute = normalizeRouteState(before, fromKey);
    copyRouteState(beforeRoute, candidate);

    boolean resetThisTurn =
        beforeRoute.optInt("lastRollTurn", -1) == Math.max(1, before.optInt("turn", 1))
            && "RESET".equals(beforeRoute.optString("lastResult", ""));

    if (resetThisTurn) {
      candidate.put("currentLevel", parentLevel(fromKey));
      candidate.put(LEVEL_KEY, fromKey);
      String returnLocation = beforeRoute.optString("returnLocation", "").trim();
      if (!returnLocation.isEmpty()) candidate.put("location", returnLocation);
      return;
    }

    String requestedKey = requestedLevelKey(candidate, fromKey);
    if (INVALID_LEVEL_KEY.equals(requestedKey)) {
      throw new IllegalArgumentException("Unsupported or inconsistent Level destination");
    }

    if (!nodeTransitionAllowed(fromKey, requestedKey)) {
      throw new IllegalArgumentException(
          "Invalid Level transition: " + displayName(fromKey) + " -> " + displayName(requestedKey));
    }

    if (!requestedKey.equals(fromKey)) {
      if (!beforeRoute.optBoolean("exitAvailable", false)) {
        throw new IllegalArgumentException("Level transition is locked until the hidden route chain completes");
      }
      candidate.put("currentLevel", parentLevel(requestedKey));
      candidate.put(LEVEL_KEY, requestedKey);
      candidate.put(ROUTE_STATE, newRouteState(requestedKey));
      if (!locationIdentifiesKey(candidate.optString("location", ""), requestedKey)) {
        candidate.put("location", defaultLocation(requestedKey));
      }
      return;
    }

    candidate.put("currentLevel", parentLevel(fromKey));
    candidate.put(LEVEL_KEY, fromKey);
  }

  String promptContext(JSONObject state) {
    String levelKey = resolveLevelKey(state);
    int level = parentLevel(levelKey);
    String canon = knowledgeContext(levelKey);
    if (canon.trim().isEmpty()) {
      canon = legacyCanonByLevelKey.get(levelKey);
    }
    if (canon == null || canon.trim().isEmpty()) {
      canon = "Canon for " + displayName(levelKey) + " is unavailable.";
    }

    String next = nextRequiredLevelKey(levelKey);
    String allowed;
    if (next != null) {
      allowed = displayName(next);
    } else {
      StringBuilder values = new StringBuilder();
      for (int target = 0; target <= 6; target++) {
        if (target == level || !GameCoreRules.levelTransitionAllowed(level, target)) continue;
        if (values.length() > 0) values.append(", ");
        values.append(displayName(String.valueOf(target)));
      }
      allowed = values.length() == 0 ? "none" : values.toString();
    }

    JSONObject route;
    try {
      route = normalizeRouteState(state, levelKey);
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
          "HIDDEN ROUTE OUTCOME: EXIT AVAILABLE. You may now establish a real boundary/route to "
              + allowed
              + " if the player's action naturally reaches or crosses it. Never reveal the hidden route system.";
    } else if ("SUCCESS".equals(result)) {
      routeInstruction =
          "HIDDEN ROUTE OUTCOME THIS TURN: SUCCESS. Narrate meaningful continued exploration inside "
              + displayName(levelKey)
              + ", but do not reveal an exit yet. Never mention a roll, streak, probability, or hidden system.";
    } else {
      routeInstruction =
          "HIDDEN ROUTE OUTCOME THIS TURN: NO ROUTE ROLL. Keep route progression implicit and never reveal the hidden system.";
    }

    String transitionInstruction = exitAvailable
        ? "LEVEL TRANSITION: AVAILABLE. The only allowed next destination is " + allowed
            + ". Do not skip any Level 0 sub-level node."
        : "LEVEL TRANSITION: LOCKED. Keep currentLevelKey unchanged and keep the environment inside "
            + displayName(levelKey) + ".";

    String mutationInstruction = next == null
        ? "STATE RULE: currentLevelKey must match the current Level. currentLevel remains the integer parent Level number."
        : "STATE RULE: when the narration actually crosses into the next destination, set currentLevelKey to '"
            + next + "' and currentLevel to " + parentLevel(next)
            + ". Before that moment keep currentLevelKey='" + levelKey + "'.";

    return "CURRENT LEVEL NODE: " + displayName(levelKey) + "\n"
        + "CURRENT LEVEL KEY: " + levelKey + "\n"
        + "PARENT LEVEL NUMBER: " + level + "\n"
        + "LEVEL KNOWLEDGE BUNDLE:\n" + canon + "\n"
        + "KNOWLEDGE RULE: environment canon is backstage truth, not automatic character knowledge. "
        + "variationPool contains optional scene motifs, never guaranteed persistent objects. "
        + "Entity spawning, item spawning, combat, stats and hidden route progress remain Core-owned.\n"
        + "VALID NEXT LEVEL TRANSITION: " + allowed + "\n"
        + transitionInstruction + "\n"
        + mutationInstruction + "\n"
        + routeInstruction;
  }

  String snapshotDescriptor(JSONObject state) {
    String levelKey = resolveLevelKey(state);
    int level = parentLevel(levelKey);
    JSONObject output = new JSONObject();
    try {
      output.put("level", level);
      output.put("levelKey", levelKey);
      output.put("label", displayName(levelKey));
      output.put("visualType", visualTypeByLevel.containsKey(level) ? visualTypeByLevel.get(level) : "scene");
      // Numbered main Levels keep their curated local snapshots. Sub-level nodes intentionally do not
      // reuse Level 0 art because that would visually misrepresent places such as Red Rooms or The Torment.
      if (isMainLevelKey(levelKey)) {
        JSONArray assets = snapshotsByLevel.get(level);
        if (assets != null && assets.length() > 0) {
          int turn = Math.max(1, state.optInt("turn", 1));
          int index = Math.floorMod(turn - 1, assets.length());
          String relative = assets.optString(index, "");
          if (!relative.isEmpty()) output.put("path", "file:///android_asset/" + snapshotRoot + "/" + relative);
        }
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

  static String[] levelZeroProgressionKeys() {
    return LEVEL_ZERO_PROGRESSION.clone();
  }

  static String displayName(String levelKey) {
    String key = normalizeKey(levelKey);
    switch (key) {
      case "0": return "Level 0 — The Lobby";
      case "0.1": return "Level 0.1 — Zenith Station";
      case "0.2": return "Level 0.2 — Remodeled Mess";
      case "0.5": return "Level 0.5 — Aquaclaustrophobic Infirmary";
      case "0.7": return "Level 0.7 — The Reminiscence District";
      case "manila_room": return "Manila Room";
      case "the_torment": return "The Torment";
      case "red_rooms": return "Red Rooms";
      case "1": return "Level 1 — Parking Zone";
      case "2": return "Level 2 — Pipe Dreams";
      case "3": return "Level 3 — The Electrical Station";
      case "4": return "Level 4 — The Abandoned Office";
      case "5": return "Level 5 — Terror Hotel";
      case "6": return "Level 6 — Lights Out";
      default: return levelKey == null || levelKey.trim().isEmpty() ? "Unknown Level" : levelKey;
    }
  }

  static String defaultLocation(String levelKey) {
    String key = normalizeKey(levelKey);
    switch (key) {
      case "0": return LEVEL_ZERO_START_LOCATION;
      case "0.1": return "Level 0.1 / Zenith Station";
      case "0.2": return "Level 0.2 / Remodeled Mess";
      case "0.5": return "Level 0.5 / Aquaclaustrophobic Infirmary";
      case "0.7": return "Level 0.7 / The Reminiscence District";
      case "manila_room": return "Manila Room / Level 0";
      case "the_torment": return "The Torment / Level 0";
      case "red_rooms": return "Red Rooms / Level 0";
      case "1": return "Level 1 / Parking Zone";
      case "2": return "Level 2 / Pipe Dreams";
      case "3": return "Level 3 / The Electrical Station";
      case "4": return "Level 4 / The Abandoned Office";
      case "5": return "Level 5 / Terror Hotel";
      case "6": return "Level 6 / Lights Out";
      default: return displayName(key);
    }
  }

  private JSONObject normalizeRouteState(JSONObject state, String levelKey) throws Exception {
    JSONObject route = state.optJSONObject(ROUTE_STATE);
    if (route == null || !levelKey.equals(normalizeKey(route.optString("levelKey", "")))) {
      route = newRouteState(levelKey);
      state.put(ROUTE_STATE, route);
      return route;
    }

    int streak = Math.max(0, Math.min(ROUTE_REQUIRED_STREAK, route.optInt("streak", 0)));
    boolean exitAvailable = streak >= ROUTE_REQUIRED_STREAK;
    route.put("level", parentLevel(levelKey));
    route.put("levelKey", levelKey);
    route.put("streak", exitAvailable ? ROUTE_REQUIRED_STREAK : streak);
    route.put("exitAvailable", exitAvailable);
    state.put(ROUTE_STATE, route);
    return route;
  }

  private static JSONObject newRouteState(String levelKey) throws Exception {
    return new JSONObject()
        .put("level", parentLevel(levelKey))
        .put("levelKey", normalizeKey(levelKey))
        .put("streak", 0)
        .put("exitAvailable", false)
        .put("lastRollTurn", -1)
        .put("lastResult", "");
  }

  private static void copyRouteState(JSONObject route, JSONObject candidate) throws Exception {
    if (candidate == null) throw new IllegalArgumentException("candidate state is required");
    candidate.put(ROUTE_STATE, new JSONObject(route.toString()));
  }

  private String resolveLevelKey(JSONObject state) {
    if (state == null) return "0";

    String explicit = normalizeKey(state.optString(LEVEL_KEY, ""));
    if (isKnownLevelKey(explicit)) return explicit;

    String inferred = levelKeyFromLocation(state.optString("location", ""));
    if (isKnownLevelKey(inferred)) return inferred;

    int level = state.optInt("currentLevel", -1);
    if (level >= 0 && level <= 6) return String.valueOf(level);
    return "0";
  }

  private static String requestedLevelKey(JSONObject candidate, String fallback) {
    if (candidate == null) return INVALID_LEVEL_KEY;

    String explicit = normalizeKey(candidate.optString(LEVEL_KEY, ""));
    if (!explicit.isEmpty() && !isKnownLevelKey(explicit)) return INVALID_LEVEL_KEY;

    String location = candidate.optString("location", "");
    String rawLocationKey = rawLevelKeyFromLocation(location);
    if (!rawLocationKey.isEmpty() && !isKnownLevelKey(rawLocationKey)) {
      return INVALID_LEVEL_KEY;
    }
    String locationKey = levelKeyFromLocation(location);

    String requested;
    if (!locationKey.isEmpty() && (!locationKey.equals(fallback) || explicit.isEmpty() || explicit.equals(fallback))) {
      requested = locationKey;
    } else if (!explicit.isEmpty()) {
      requested = explicit;
    } else if (candidate.has("currentLevel")) {
      int level = candidate.optInt("currentLevel", -1);
      if (level < 0 || level > 6) return INVALID_LEVEL_KEY;
      requested = level == parentLevel(fallback) ? fallback : String.valueOf(level);
    } else {
      requested = fallback;
    }

    if (candidate.has("currentLevel")) {
      int numeric = candidate.optInt("currentLevel", -1);
      int requestedParent = parentLevel(requested);
      int fallbackParent = parentLevel(fallback);
      if (numeric < 0 || numeric > 6 || (numeric != requestedParent && numeric != fallbackParent)) {
        return INVALID_LEVEL_KEY;
      }
    }

    return isKnownLevelKey(requested) ? requested : INVALID_LEVEL_KEY;
  }

  private static boolean nodeTransitionAllowed(String fromKey, String toKey) {
    String from = normalizeKey(fromKey);
    String to = normalizeKey(toKey);
    if (!isKnownLevelKey(from) || !isKnownLevelKey(to)) return false;
    if (from.equals(to)) return true;

    String required = nextRequiredLevelKey(from);
    if (required != null) return required.equals(to);

    int fromLevel = parentLevel(from);
    int toLevel = parentLevel(to);
    return isMainLevelKey(from) && isMainLevelKey(to)
        && GameCoreRules.levelTransitionAllowed(fromLevel, toLevel);
  }

  private static String nextRequiredLevelKey(String levelKey) {
    String key = normalizeKey(levelKey);
    for (int i = 0; i < LEVEL_ZERO_PROGRESSION.length - 1; i++) {
      if (LEVEL_ZERO_PROGRESSION[i].equals(key)) return LEVEL_ZERO_PROGRESSION[i + 1];
    }
    return null;
  }

  private static boolean isKnownLevelKey(String key) {
    String normalized = normalizeKey(key);
    if (normalized.matches("[0-6]")) return true;
    for (String progressionKey : LEVEL_ZERO_PROGRESSION) {
      if (progressionKey.equals(normalized)) return true;
    }
    return false;
  }

  private static boolean isMainLevelKey(String key) {
    return normalizeKey(key).matches("[0-6]");
  }

  private static int parentLevel(String levelKey) {
    String key = normalizeKey(levelKey);
    if (key.matches("[1-6]")) return Integer.parseInt(key);
    return 0;
  }

  private static String rawLevelKeyFromLocation(String location) {
    String text = location == null ? "" : location.trim();
    if (text.isEmpty()) return "";

    String lower = text.toLowerCase(Locale.ROOT);
    if (lower.contains("manila room")) return "manila_room";
    if (lower.contains("the torment") || lower.matches(".*\\btorment\\b.*")) return "the_torment";
    if (lower.contains("red rooms") || lower.contains("red room")) return "red_rooms";

    Matcher matcher = LEVEL_TOKEN.matcher(text);
    if (!matcher.find()) return "";
    return normalizeNumericKey(matcher.group(1));
  }

  private static String levelKeyFromLocation(String location) {
    String key = rawLevelKeyFromLocation(location);
    return isKnownLevelKey(key) ? key : "";
  }

  private static boolean locationIdentifiesKey(String location, String levelKey) {
    return normalizeKey(levelKey).equals(levelKeyFromLocation(location));
  }

  private static String normalizeNumericKey(String raw) {
    String value = raw == null ? "" : raw.trim();
    if (value.matches("[0-6]")) return String.valueOf(Integer.parseInt(value));
    if (value.matches("0\\.[0-9]+")) return value;
    return value;
  }

  private static String normalizeKey(String raw) {
    String key = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    key = key.replace('-', '_').replace(' ', '_');
    if ("manila".equals(key) || "manila_room".equals(key)) return "manila_room";
    if ("torment".equals(key) || "the_torment".equals(key)) return "the_torment";
    if ("red_room".equals(key) || "red_rooms".equals(key)) return "red_rooms";
    if (key.matches("0+[1-6]")) return String.valueOf(Integer.parseInt(key));
    if ("00".equals(key)) return "0";
    return normalizeNumericKey(key);
  }

  private int nextRoll(int bound) {
    int value = rng.nextInt(bound);
    if (value < 0 || value >= bound) {
      throw new IllegalStateException("RNG returned an out-of-range value");
    }
    return value;
  }

  private void loadLevelKnowledge(Context context) {
    try {
      loadLevelKnowledgeText(readAsset(context, LEVEL_KNOWLEDGE_ASSET));
    } catch (Exception ignored) {}
  }

  private void loadLevelKnowledgeText(String raw) {
    if (raw == null || raw.trim().isEmpty()) return;
    try {
      JSONObject root = new JSONObject(raw);
      if (root.optInt("schemaVersion", 0) < 2) return;

      JSONArray order = root.optJSONArray("sectionOrder");
      if (order != null) {
        for (int i = 0; i < order.length(); i++) {
          String section = order.optString(i, "").trim();
          if (!section.isEmpty()) knowledgeSectionOrder.put(section);
        }
      }

      JSONObject levels = root.optJSONObject("levels");
      if (levels == null) return;
      for (String key : new String[]{"0", "0.1", "0.2", "0.5", "0.7", "manila_room", "the_torment", "red_rooms", "1", "2", "3", "4", "5", "6"}) {
        JSONObject bundle = levels.optJSONObject(key);
        if (bundle != null) knowledgeByLevelKey.put(key, new JSONObject(bundle.toString()));
      }
    } catch (Exception ignored) {}
  }

  private String knowledgeContext(String levelKey) {
    JSONObject bundle = knowledgeByLevelKey.get(normalizeKey(levelKey));
    if (bundle == null) return "";

    StringBuilder out = new StringBuilder();
    String name = bundle.optString("name", displayName(levelKey)).trim();
    if (!name.isEmpty()) out.append("NAME: ").append(name).append('\n');

    JSONArray order = knowledgeSectionOrder;
    if (order.length() == 0) {
      order = new JSONArray()
          .put("identity").put("architecture").put("zones").put("sensory")
          .put("anomalies").put("hazards").put("resources").put("entities")
          .put("navigation").put("entrancesExits").put("gameplayOverride")
          .put("gmConstraints").put("variationPool");
    }

    for (int i = 0; i < order.length(); i++) {
      String section = order.optString(i, "").trim();
      JSONArray values = bundle.optJSONArray(section);
      if (values == null || values.length() == 0) continue;
      if (out.length() > 0) out.append('\n');
      out.append(sectionLabel(section)).append(":\n");
      for (int j = 0; j < values.length(); j++) {
        String value = values.optString(j, "").trim();
        if (!value.isEmpty()) out.append("- ").append(value).append('\n');
      }
    }
    return out.toString().trim();
  }

  private static String sectionLabel(String section) {
    if ("entrancesExits".equals(section)) return "ENTRANCES / EXITS";
    if ("gameplayOverride".equals(section)) return "BACKROOMSV2 OVERRIDES";
    if ("gmConstraints".equals(section)) return "GM CONSTRAINTS";
    if ("variationPool".equals(section)) return "VARIATION POOL";
    return section == null ? "" : section.replace('_', ' ').toUpperCase(Locale.ROOT);
  }

  private void loadLegacyKnowledge(Context context) {
    try {
      JSONObject root = new JSONObject(readAsset(context, LEGACY_KNOWLEDGE_ASSET));
      JSONArray records = root.optJSONArray("records");
      if (records == null) return;
      for (int i = 0; i < records.length(); i++) {
        JSONObject record = records.optJSONObject(i);
        if (record == null || !"LEVEL".equals(record.optString("domain"))) continue;
        String id = record.optString("id", "");
        if (!id.startsWith("LEVEL.")) continue;
        String levelKey = normalizeKnowledgeKey(id.substring("LEVEL.".length()));
        if (isKnownLevelKey(levelKey)) legacyCanonByLevelKey.put(levelKey, record.optString("text", ""));
      }
    } catch (Exception ignored) {}
  }

  private static String normalizeKnowledgeKey(String raw) {
    String key = normalizeKey(raw);
    if ("manila_room".equals(key) || "the_torment".equals(key) || "red_rooms".equals(key)) return key;
    if (raw != null && raw.matches("0[0-6]")) return String.valueOf(Integer.parseInt(raw));
    return key;
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

package com.rabpit.backroom.core;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.LinkedHashMap;
import java.util.Map;

final class LevelCore {
  private static final String KNOWLEDGE_ASSET = "knowledge/knowledge_db.json";
  private static final String SNAPSHOT_MANIFEST_ASSET = "level_snapshots/wiki/manifest.json";
  private static final int LEVEL_MISMATCH = -2;

  private final Map<Integer, String> canonByLevel = new LinkedHashMap<>();
  private final Map<Integer, JSONArray> snapshotsByLevel = new LinkedHashMap<>();
  private final Map<Integer, String> visualTypeByLevel = new LinkedHashMap<>();
  private String snapshotRoot = "level_snapshots/wiki";

  LevelCore(Context context) {
    loadKnowledge(context);
    loadSnapshotManifest(context);
  }

  void normalizeState(JSONObject state) throws Exception {
    state.put("currentLevel", resolveLevel(state));
  }

  void validateAndApplyTransition(JSONObject before, JSONObject candidate) throws Exception {
    int from = resolveLevel(before);
    int explicit = candidate != null && candidate.has("currentLevel") ? candidate.optInt("currentLevel", -1) : -1;
    int requested = requestedLevel(explicit, candidate == null ? "" : candidate.optString("location", ""), from);
    if (requested == LEVEL_MISMATCH) {
      throw new IllegalArgumentException("currentLevel does not match location");
    }
    if (!GameCoreRules.levelTransitionAllowed(from, requested)) {
      throw new IllegalArgumentException("Invalid Level transition: " + from + " -> " + requested);
    }
    candidate.put("currentLevel", requested);
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
    return "CURRENT LEVEL: " + level + "\n" +
      "LEVEL CANON: " + canon + "\n" +
      "VALID ADJACENT LEVEL TRANSITIONS: " + (allowed.length() == 0 ? "none" : allowed) + "\n" +
      "A Level transition is allowed only when the narrated environment establishes a real route/boundary transition. Never teleport between unrelated Levels.";
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

  private int resolveLevel(JSONObject state) {
    if (state != null && state.has("currentLevel")) {
      int explicit = state.optInt("currentLevel", -1);
      if (explicit >= 0 && explicit <= 6) return explicit;
    }
    int inferred = GameCoreRules.levelFromLocation(state == null ? "" : state.optString("location", ""));
    return inferred >= 0 ? inferred : 0;
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

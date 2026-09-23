package com.rabpit.backroom.core;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

final class EntityCore {
  private static final String REGISTRY_ASSET = "knowledge/entity_encounters.json";
  private static final String ENCOUNTER_KEY = "entityEncounterKey";
  private static final String RESOLVED_KEY = "entityEncounterResolved";
  private static final String SOURCE = "core_independent_roll";

  private final Map<String, EntityDefinition> entities = new LinkedHashMap<>();
  private final Map<String, LegacyEntityDefinition> legacyEntities = new LinkedHashMap<>();

  EntityCore(Context context) {
    loadRegistry(context);
  }

  void prepareAuthoredEncounter(JSONObject state, String rawEntityKey) throws Exception {
    String entityKey = rawEntityKey == null ? "" : rawEntityKey.trim().toLowerCase();
    EntityDefinition entity = entities.get(entityKey);
    if (entity == null) {
      throw new IllegalArgumentException("Unknown authored Entity key: " + entityKey);
    }
    JSONObject flags = flags(state);
    flags.put(ENCOUNTER_KEY, entity.key);
    flags.remove(RESOLVED_KEY);
    flags.put("entityEncounterSource", "story_authored");
    flags.put("entityEncounterRatePercent", 100);
    flags.put("entityEncounterLevel", state.optInt("currentLevel", 0));
    flags.put("entityEncounterStartedTurn", Math.max(1, state.optInt("turn", 1)));
    state.put("flags", flags);
  }

  void prepareEncounter(JSONObject state) throws Exception {
    JSONObject flags = flags(state);
    String activeKey = flags.optString(ENCOUNTER_KEY, "").trim();
    if (!activeKey.isEmpty()) {
      flags.remove(RESOLVED_KEY);
      state.put("flags", flags);
      return;
    }

    int level = state.optInt("currentLevel", 0);
    List<EntityDefinition> hits = new ArrayList<>();
    for (EntityDefinition entity : entities.values()) {
      if (!roamingAllowedOn(level)) continue;
      double roll = ThreadLocalRandom.current().nextDouble(100.0);
      if (roll < entity.ratePercent) hits.add(entity);
    }

    flags.remove(RESOLVED_KEY);
    if (hits.isEmpty()) {
      flags.put(ENCOUNTER_KEY, "");
      clearActiveMetadata(flags);
      state.put("flags", flags);
      return;
    }

    EntityDefinition selected = hits.get(ThreadLocalRandom.current().nextInt(hits.size()));
    flags.put(ENCOUNTER_KEY, selected.key);
    flags.put("entityEncounterSource", SOURCE);
    flags.put("entityEncounterRatePercent", selected.ratePercent);
    flags.put("entityEncounterLevel", level);
    flags.put("entityEncounterStartedTurn", Math.max(1, state.optInt("turn", 1)));
    state.put("flags", flags);
  }

  void validateAndApply(JSONObject before, JSONObject candidate) throws Exception {
    JSONObject beforeFlags = flags(before);
    JSONObject candidateFlags = flags(candidate);
    String activeKey = beforeFlags.optString(ENCOUNTER_KEY, "").trim();
    boolean resolved = candidateFlags.optBoolean(RESOLVED_KEY, false);

    candidateFlags.remove(RESOLVED_KEY);
    if (activeKey.isEmpty()) {
      candidateFlags.put(ENCOUNTER_KEY, "");
      clearActiveMetadata(candidateFlags);
      candidate.put("flags", candidateFlags);
      return;
    }

    if (resolved) {
      candidateFlags.put("lastEntityEncounterKey", activeKey);
      candidateFlags.put("lastEntityEncounterResolvedTurn", Math.max(1, candidate.optInt("turn", before.optInt("turn", 1))));
      candidateFlags.put(ENCOUNTER_KEY, "");
      clearActiveMetadata(candidateFlags);
      candidate.put("flags", candidateFlags);
      return;
    }

    candidateFlags.put(ENCOUNTER_KEY, activeKey);
    copyMetadata(beforeFlags, candidateFlags, "entityEncounterSource");
    copyMetadata(beforeFlags, candidateFlags, "entityEncounterRatePercent");
    copyMetadata(beforeFlags, candidateFlags, "entityEncounterLevel");
    copyMetadata(beforeFlags, candidateFlags, "entityEncounterStartedTurn");
    candidate.put("flags", candidateFlags);
  }

  String promptContext(JSONObject state) {
    JSONObject flags = state == null ? null : state.optJSONObject("flags");
    String activeKey = flags == null ? "" : flags.optString(ENCOUNTER_KEY, "").trim();
    if (activeKey.isEmpty()) {
      return "ENTITY CORE: no active Entity encounter this turn. Do not invent, summon or select an Entity. " +
        "Encounter spawning is owned exclusively by Main Game Core independent fixed-rate rolls. " +
        "All registered auto-spawn Entities are roaming and may roll on every valid Backrooms Level regardless of their original canon habitat.";
    }

    EntityDefinition entity = entities.get(activeKey);
    if (entity == null) {
      LegacyEntityDefinition legacy = legacyEntities.get(activeKey);
      if (legacy != null) return legacyPromptContext(activeKey, legacy.name, legacy.canon);
      return "ENTITY CORE: active legacy encounter key=" + activeKey + ". Preserve this existing encounter until it is actually resolved. " +
        "Do not replace it with another Entity. Set flags.entityEncounterResolved=true only when the narrated encounter genuinely ends.";
    }

    return "ENTITY CORE ACTIVE ENCOUNTER: " + entity.name + " (key=" + entity.key + ").\n" +
      "FIXED INDEPENDENT SPAWN RATE: " + entity.ratePercent + "% per eligible world-advancing turn.\n" +
      "ROAMING POLICY: this registered Entity is valid on every Backrooms Level. Original canon habitat/location restrictions do not block its presence.\n" +
      "ENTITY CANON (behavior/capabilities only): " + entity.canon + "\n" +
      "Do not replace this Entity with another one. Continue the encounter according to state and behavioral canon. " +
      "Set flags.entityEncounterResolved=true only when the Entity is no longer directly present/engaged and the encounter has genuinely ended.";
  }

  static String legacyPromptContext(String activeKey, String name, String canon) {
    String safeKey = activeKey == null ? "" : activeKey.trim();
    String safeName = name == null || name.trim().isEmpty() ? safeKey : name.trim();
    String safeCanon = canon == null ? "" : canon.trim();
    return "ENTITY CORE ACTIVE LEGACY/BOSS ENCOUNTER: " + safeName + " (key=" + safeKey + ").\n" +
      "LEGACY ENTITY CANON: " + safeCanon + "\n" +
      "RUNTIME LOCK: this encounter is legacy/boss-only and must not be treated as an auto-spawn Entity. " +
      "Preserve this encounter until it is genuinely resolved; do not replace it with another Entity. " +
      "Set flags.entityEncounterResolved=true only when the narrated encounter genuinely ends.";
  }

  private JSONObject flags(JSONObject state) throws Exception {
    JSONObject flags = state.optJSONObject("flags");
    if (flags == null) flags = new JSONObject();
    state.put("flags", flags);
    return flags;
  }

  private void clearActiveMetadata(JSONObject flags) {
    flags.remove("entityEncounterSource");
    flags.remove("entityEncounterRatePercent");
    flags.remove("entityEncounterLevel");
    flags.remove("entityEncounterStartedTurn");
  }

  private void copyMetadata(JSONObject source, JSONObject target, String key) throws Exception {
    if (source.has(key)) target.put(key, source.get(key));
    else target.remove(key);
  }

  private void loadRegistry(Context context) {
    try {
      JSONObject root = new JSONObject(readAsset(context, REGISTRY_ASSET));
      if (!"independent_per_entity".equals(root.optString("rollMode"))) return;
      JSONArray records = root.optJSONArray("entities");
      if (records == null) return;
      for (int i = 0; i < records.length(); i++) {
        JSONObject record = records.optJSONObject(i);
        if (record == null) continue;
        String key = record.optString("key", "").trim();
        String name = record.optString("name", key).trim();
        double rate = record.optDouble("ratePercent", 0.0);
        String canon = record.optString("canon", "").trim();
        if (key.isEmpty() || rate < 1.0 || rate > 1.5) continue;
        entities.put(key, new EntityDefinition(key, name, rate, canon));
      }

      JSONArray legacyRecords = root.optJSONArray("legacyEntities");
      if (legacyRecords != null) {
        for (int i = 0; i < legacyRecords.length(); i++) {
          JSONObject record = legacyRecords.optJSONObject(i);
          if (record == null) continue;
          String key = record.optString("key", "").trim();
          String name = record.optString("name", key).trim();
          String canon = record.optString("canon", "").trim();
          if (key.isEmpty() || canon.isEmpty()) continue;
          legacyEntities.put(key, new LegacyEntityDefinition(key, name, canon));
        }
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

  static boolean roamingAllowedOn(int level) {
    return level >= 0;
  }

  private static final class LegacyEntityDefinition {
    final String key;
    final String name;
    final String canon;

    LegacyEntityDefinition(String key, String name, String canon) {
      this.key = key;
      this.name = name;
      this.canon = canon;
    }
  }

  private static final class EntityDefinition {
    final String key;
    final String name;
    final double ratePercent;
    final String canon;

    EntityDefinition(String key, String name, double ratePercent, String canon) {
      this.key = key;
      this.name = name;
      this.ratePercent = ratePercent;
      this.canon = canon;
    }
  }
}

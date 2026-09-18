package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Read-only character detail projection for the WebView Party UI.
 *
 * Restores the legacy partyDetails contract without giving Gemini ownership of character state.
 * Existing legacy detail data is preserved when present. Kai's survival counters are derived from
 * authoritative subjective game time when no explicit physiology data exists.
 */
final class CharacterDetailCore {
  static final int MAX_MEMBERS = 4;
  private static final long FOOD_CRITICAL_MINUTES = 72L * 60L;
  private static final long WATER_CRITICAL_MINUTES = 48L * 60L;
  private static final long REST_CRITICAL_MINUTES = 36L * 60L;

  void projectState(JSONObject state) throws Exception {
    if (state == null) return;

    JSONObject previousDetails = state.optJSONObject("partyDetails");
    Map<String, JSONObject> previousById = detailMembersById(previousDetails);
    long elapsed = elapsedMinutes(state);

    JSONObject details = new JSONObject()
        .put("leaderId", "kai")
        .put("maxMembers", MAX_MEMBERS)
        .put("elapsedSubjectiveMinutes", elapsed);

    JSONArray members = new JSONArray();
    JSONObject player = state.optJSONObject("player");
    if (player == null) player = new JSONObject().put("name", "Kai Akechi");
    members.put(projectMember(state, player, previousById.get("kai"), "kai", "Kai Akechi", true, elapsed));

    JSONArray party = state.optJSONArray("party");
    if (party != null) {
      for (int i = 0; i < party.length() && members.length() < MAX_MEMBERS; i++) {
        JSONObject source = party.optJSONObject(i);
        if (source == null || !CharacterEncounterCore.isJoinedMember(source)) continue;
        String id = characterId(source);
        if (id.isEmpty() || "kai".equals(id)) continue;
        members.put(projectMember(
            state, source, previousById.get(id), id,
            source.optString("name", displayName(id)), false, elapsed));
      }
    }

    details.put("members", members);
    state.put("partyDetails", details);
  }

  static JSONObject derivePhysiology(long minutesSinceFood, long minutesSinceWater, long minutesAwake)
      throws Exception {
    JSONObject result = new JSONObject();
    putPhysiology(result, "hunger", minutesSinceFood, 12L * 60L, 24L * 60L, 48L * 60L,
        FOOD_CRITICAL_MINUTES, "foodPercent");
    putPhysiology(result, "thirst", minutesSinceWater, 6L * 60L, 12L * 60L, 24L * 60L,
        WATER_CRITICAL_MINUTES, "waterPercent");
    putPhysiology(result, "sleepDeprivation", minutesAwake, 16L * 60L, 20L * 60L, 24L * 60L,
        REST_CRITICAL_MINUTES, "restPercent");
    result.put("minutesSinceFood", Math.max(0L, minutesSinceFood));
    result.put("minutesSinceWater", Math.max(0L, minutesSinceWater));
    result.put("minutesAwake", Math.max(0L, minutesAwake));
    return result;
  }

  private JSONObject projectMember(JSONObject state, JSONObject source, JSONObject previous,
                                   String id, String fallbackName, boolean leader, long elapsed)
      throws Exception {
    JSONObject member = new JSONObject()
        .put("id", id)
        .put("name", nonEmpty(source.optString("name", ""), fallbackName))
        .put("presence", nonEmpty(source.optString("presence", ""), leader ? "ACTIVE" : "ACTIVE"))
        .put("isLeader", leader);

    String avatar = firstString(source, previous, "avatar", "avatarRef");
    if (!avatar.isEmpty()) member.put("avatar", avatar);

    String healthState = firstString(source, previous, "healthState", "condition");
    if (!healthState.isEmpty()) member.put("healthState", healthState);
    String condition = firstString(source, previous, "condition", "healthState");
    if (!condition.isEmpty()) member.put("condition", condition);

    int previousMax = previous == null ? 0 : firstPositive(previous, 0, "maxHp", "maxHP", "hpMax");
    int maxHp = firstPositive(source, previousMax > 0 ? previousMax : 100, "maxHp", "maxHP", "hpMax");
    int previousHp = previous == null ? 0 : firstNonNegative(previous, -1, "currentHp", "hp", "currentHP");
    int hp = firstNonNegative(source, previousHp >= 0 ? previousHp : maxHp, "hp", "currentHp", "currentHP");
    hp = Math.max(0, Math.min(hp, maxHp));
    member.put("currentHp", hp).put("hp", hp).put("maxHp", maxHp);

    copyStringIfPresent(source, previous, member, "role");
    copyStringIfPresent(source, previous, member, "energy");
    copyNumberIfPresent(source, previous, member, "hpRegen");
    copyObjectIfPresent(source, previous, member, "stats");
    copyArrayIfPresent(source, previous, member, "injuries");
    copyArrayIfPresent(source, previous, member, "statuses");
    copyArrayIfPresent(source, previous, member, "effects");

    JSONObject physiology = source.optJSONObject("physiology");
    if (physiology == null && previous != null) physiology = previous.optJSONObject("physiology");
    if (physiology != null) {
      member.put("physiology", new JSONObject(physiology.toString()));
    } else if (leader) {
      member.put("physiology", derivePhysiology(elapsed, elapsed, elapsed));
    } else {
      JSONObject unknown = new JSONObject()
          .put("hunger", "UNKNOWN")
          .put("thirst", "UNKNOWN")
          .put("sleepDeprivation", "UNKNOWN");
      member.put("physiology", unknown);
    }

    JSONArray inventory = source.optJSONArray("inventory");
    if (inventory == null && leader) inventory = state.optJSONArray("inventory");
    if (inventory == null && previous != null) inventory = previous.optJSONArray("inventory");
    member.put("inventory", inventory == null ? new JSONArray() : new JSONArray(inventory.toString()));

    Object equipment = source.opt("equipment");
    if (equipment == null && previous != null) equipment = previous.opt("equipment");
    if (equipment instanceof JSONObject) {
      member.put("equipment", new JSONObject(equipment.toString()));
    } else if (equipment instanceof JSONArray) {
      member.put("equipment", new JSONArray(equipment.toString()));
    }

    return member;
  }

  private static Map<String, JSONObject> detailMembersById(JSONObject details) {
    Map<String, JSONObject> output = new LinkedHashMap<>();
    if (details == null) return output;
    JSONArray members = details.optJSONArray("members");
    if (members == null) return output;
    for (int i = 0; i < members.length(); i++) {
      JSONObject member = members.optJSONObject(i);
      String id = characterId(member);
      if (!id.isEmpty() && !output.containsKey(id)) output.put(id, member);
    }
    return output;
  }

  private static long elapsedMinutes(JSONObject state) {
    JSONObject time = state == null ? null : state.optJSONObject("gameTime");
    return time == null ? 0L : Math.max(0L, time.optLong("elapsedSubjectiveMinutes", 0L));
  }

  private static void putPhysiology(JSONObject result, String bandKey, long minutes,
                                    long mildAt, long moderateAt, long severeAt, long criticalAt,
                                    String percentKey) throws Exception {
    result.put(bandKey, band(minutes, mildAt, moderateAt, severeAt, criticalAt));
    result.put(percentKey, remainingPercent(minutes, criticalAt));
  }

  private static String band(long minutes, long mildAt, long moderateAt, long severeAt, long criticalAt) {
    if (minutes < 0L) return "UNKNOWN";
    if (minutes >= criticalAt) return "CRITICAL";
    if (minutes >= severeAt) return "SEVERE";
    if (minutes >= moderateAt) return "MODERATE";
    if (minutes >= mildAt) return "MILD";
    return "NORMAL";
  }

  private static int remainingPercent(long minutes, long emptyAt) {
    if (minutes < 0L || emptyAt <= 0L) return 0;
    long remaining = Math.max(0L, Math.min(emptyAt, emptyAt - minutes));
    return (int)Math.max(0L, Math.min(100L, remaining * 100L / emptyAt));
  }

  private static String characterId(JSONObject member) {
    if (member == null) return "";
    String raw = (member.optString("id", "") + " " + member.optString("name", ""))
        .trim().toLowerCase(Locale.ROOT);
    if (raw.contains("kai") || raw.contains("twilight")) return "kai";
    if (raw.contains("lucia") || raw.contains("hứa thuý mai") || raw.contains("hứa thúy mai")
        || raw.contains("hua thuy mai")) return "lucia";
    if (raw.contains("iris") || raw.contains("argus")) return "iris";
    if (raw.contains("syvial")) return "syvial";
    return member.optString("id", "").trim().toLowerCase(Locale.ROOT);
  }

  private static String displayName(String id) {
    if ("lucia".equals(id)) return "Lucia Lục";
    if ("iris".equals(id)) return "Iris";
    if ("syvial".equals(id)) return "Syvial";
    return "Kai Akechi";
  }

  private static String nonEmpty(String value, String fallback) {
    return value == null || value.trim().isEmpty() ? fallback : value.trim();
  }

  private static String firstString(JSONObject source, JSONObject previous, String... keys) {
    for (String key : keys) {
      String value = source == null ? "" : source.optString(key, "").trim();
      if (!value.isEmpty()) return value;
    }
    for (String key : keys) {
      String value = previous == null ? "" : previous.optString(key, "").trim();
      if (!value.isEmpty()) return value;
    }
    return "";
  }

  private static int firstPositive(JSONObject source, int fallback, String... keys) {
    if (source != null) {
      for (String key : keys) {
        int value = source.optInt(key, 0);
        if (value > 0) return value;
      }
    }
    return fallback;
  }

  private static int firstNonNegative(JSONObject source, int fallback, String... keys) {
    if (source != null) {
      for (String key : keys) {
        if (!source.has(key)) continue;
        int value = source.optInt(key, -1);
        if (value >= 0) return value;
      }
    }
    return fallback;
  }

  private static void copyStringIfPresent(JSONObject source, JSONObject previous, JSONObject target,
                                          String key) throws Exception {
    String value = firstString(source, previous, key);
    if (!value.isEmpty()) target.put(key, value);
  }

  private static void copyNumberIfPresent(JSONObject source, JSONObject previous, JSONObject target,
                                          String key) throws Exception {
    if (source != null && source.has(key)) target.put(key, source.optInt(key, 0));
    else if (previous != null && previous.has(key)) target.put(key, previous.optInt(key, 0));
  }

  private static void copyObjectIfPresent(JSONObject source, JSONObject previous, JSONObject target,
                                          String key) throws Exception {
    JSONObject value = source == null ? null : source.optJSONObject(key);
    if (value == null && previous != null) value = previous.optJSONObject(key);
    if (value != null) target.put(key, new JSONObject(value.toString()));
  }

  private static void copyArrayIfPresent(JSONObject source, JSONObject previous, JSONObject target,
                                         String key) throws Exception {
    JSONArray value = source == null ? null : source.optJSONArray(key);
    if (value == null && previous != null) value = previous.optJSONArray(key);
    target.put(key, value == null ? new JSONArray() : new JSONArray(value.toString()));
  }
}

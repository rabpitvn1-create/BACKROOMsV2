package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** Owns companion encounter rolls, joins, migration and pending GM introductions. */
final class CharacterEncounterCore {
  static final int MAX_COMPANIONS = 3;
  static final int LUC_TRAM_RATE_PERCENT = 10;
  static final int RARE_ENCOUNTER_BOUND = 4000;

  interface IntRng {
    int nextInt(int bound);
  }

  static final class EncounterResult {
    final List<String> joinedIds;
    final boolean capacityRejected;

    EncounterResult(List<String> joinedIds, boolean capacityRejected) {
      this.joinedIds = joinedIds;
      this.capacityRejected = capacityRejected;
    }

    boolean joinedAny() {
      return !joinedIds.isEmpty();
    }
  }

  private static final String ENCOUNTER_STATE = "characterEncounter";
  private static final String PENDING_INTRO = "pendingIntro";
  private static final String JUST_ENCOUNTERED = "justEncountered";
  private static final String LAST_ROLL_TURN = "lastExplorerRollTurn";
  private static final String LAST_ROLL_ACTION = "lastExplorerRollAction";
  private static final String[] CANONICAL_ORDER = {"lucia", "iris", "syvial"};

  private final IntRng rng;

  CharacterEncounterCore() {
    this(bound -> ThreadLocalRandom.current().nextInt(bound));
  }

  CharacterEncounterCore(IntRng rng) {
    if (rng == null) throw new IllegalArgumentException("rng is required");
    this.rng = rng;
  }

  void normalizeState(JSONObject state) throws Exception {
    if (state == null) return;
    JSONArray source = state.optJSONArray("party");
    Map<String, JSONObject> byId = new LinkedHashMap<>();
    if (source != null) {
      for (int i = 0; i < source.length(); i++) {
        Object raw = source.opt(i);
        JSONObject member = raw instanceof JSONObject ? (JSONObject) raw : legacyStringMember(raw);
        String id = characterId(member);
        if (id.isEmpty() || "cao_minh".equals(id) || byId.containsKey(id) || !isEncounterCharacter(id)) continue;
        byId.put(id, normalizedMember(id, member));
      }
    }

    JSONArray party = new JSONArray();
    for (String id : CANONICAL_ORDER) {
      JSONObject member = byId.get(id);
      if (member != null && party.length() < MAX_COMPANIONS) party.put(member);
    }
    state.put("party", party);

    JSONObject encounter = encounterState(state);
    JSONArray joined = new JSONArray();
    for (String id : CANONICAL_ORDER) if (containsPartyId(party, id)) joined.put(id);
    encounter.put("joined", joined);
    encounter.put(PENDING_INTRO, filterEncounterIds(encounter.optJSONArray(PENDING_INTRO)));
    encounter.put(JUST_ENCOUNTERED, filterEncounterIds(encounter.optJSONArray(JUST_ENCOUNTERED)));
    state.put(ENCOUNTER_STATE, encounter);
  }

  EncounterResult rollForExplorerAction(JSONObject state, String action) throws Exception {
    normalizeState(state);
    JSONObject encounter = encounterState(state);
    JSONArray pending = encounter.optJSONArray(PENDING_INTRO);
    int turn = Math.max(1, state.optInt("turn", 1));
    if ((pending != null && pending.length() > 0) || encounter.optInt(LAST_ROLL_TURN, -1) == turn) {
      return new EncounterResult(new ArrayList<>(), false);
    }

    encounter.put(LAST_ROLL_TURN, turn);
    encounter.put(LAST_ROLL_ACTION, String.valueOf((action == null ? "" : action.trim()).hashCode()));
    JSONArray party = state.getJSONArray("party");
    List<String> hits = new ArrayList<>();

    String currentLevelKey = state.optString(LevelCore.LEVEL_KEY, String.valueOf(state.optInt("currentLevel", 0)));
    // "lucia" remains the stable save/runtime id for backward compatibility.
    // Lục Trầm's current canon requires the reunion to happen only after Level 0.
    if (!containsPartyId(party, "lucia")
        && isLucTramEncounterLevel(currentLevelKey)
        && shouldEncounterLucTram(currentLevelKey, nextRoll(100))) {
      hits.add("lucia");
    }
    if (!containsPartyId(party, "iris") && shouldEncounterRare(nextRoll(RARE_ENCOUNTER_BOUND))) {
      hits.add("iris");
    }
    if (!containsPartyId(party, "syvial") && shouldEncounterRare(nextRoll(RARE_ENCOUNTER_BOUND))) {
      hits.add("syvial");
    }

    int available = MAX_COMPANIONS - party.length();
    if (hits.size() > available) {
      encounter.put("lastCapacityRejected", new JSONArray(hits));
      encounter.put(JUST_ENCOUNTERED, new JSONArray());
      state.put(ENCOUNTER_STATE, encounter);
      return new EncounterResult(new ArrayList<>(), true);
    }

    encounter.remove("lastCapacityRejected");
    JSONArray encountered = new JSONArray(hits);
    encounter.put(JUST_ENCOUNTERED, encountered);
    encounter.put(PENDING_INTRO, new JSONArray(hits));
    state.put(ENCOUNTER_STATE, encounter);
    normalizeState(state);
    return new EncounterResult(hits, false);
  }

  void validateAndApply(JSONObject before, JSONObject candidate, JSONArray introDialogue) throws Exception {
    normalizeState(before);
    candidate.put("party", new JSONArray(before.getJSONArray("party").toString()));
    JSONObject beforeEncounter = before.optJSONObject(ENCOUNTER_STATE);
    candidate.put(ENCOUNTER_STATE, beforeEncounter == null
        ? new JSONObject()
        : new JSONObject(beforeEncounter.toString()));
    normalizeState(candidate);

    JSONArray pending = candidate.getJSONObject(ENCOUNTER_STATE).optJSONArray(PENDING_INTRO);
    if (pending == null || pending.length() == 0) return;
    validateIntroDialogue(introDialogue);

    JSONArray party = candidate.getJSONArray("party");
    for (int i = 0; i < pending.length(); i++) {
      String id = pending.optString(i, "").trim().toLowerCase(Locale.ROOT);
      if (id.isEmpty() || containsPartyId(party, id)) continue;
      if (party.length() >= MAX_COMPANIONS) {
        throw new IllegalStateException("Party đã đầy trước khi hoàn tất character encounter.");
      }
      party.put(normalizedMember(id, null));
    }
    candidate.put("party", party);
    normalizeState(candidate);

    JSONObject encounter = candidate.getJSONObject(ENCOUNTER_STATE);
    encounter.put("lastIntroduced", new JSONArray(pending.toString()));
    encounter.put("lastIntroducedTurn", Math.max(1, candidate.optInt("turn", 1)));
    encounter.put(PENDING_INTRO, new JSONArray());
    encounter.put(JUST_ENCOUNTERED, new JSONArray());
  }

  String promptContext(JSONObject state) {
    try {
      normalizeState(state);
      JSONArray party = state.getJSONArray("party");
      JSONObject encounter = state.getJSONObject(ENCOUNTER_STATE);
      JSONArray pending = encounter.optJSONArray(PENDING_INTRO);
      List<String> joined = new ArrayList<>();
      List<String> notMet = new ArrayList<>();
      for (String id : CANONICAL_ORDER) {
        if (containsPartyId(party, id)) joined.add(displayName(id));
        else notMet.add(displayName(id));
      }
      String recent = displayNames(encounter.optJSONArray(JUST_ENCOUNTERED));
      String pendingNames = displayNames(pending);
      boolean lucTramPending = containsString(pending, "lucia");
      String encounterRule = lucTramPending
          ? "Lục Trầm is a REUNION, not first contact: she and Cao Minh knew and fought each other before Backrooms. " +
              "Begin with wary/hostile recognition consistent with their old rivalry. Do not jump directly to trust, romance, forgiveness or the hidden truth of Táng Kiếm Cốc. "
          : "For Iris/Syvial, depict first contact in the current location. ";
      return "CHARACTER ENCOUNTER CORE:\n" +
          "Joined: " + listText(joined) + ".\n" +
          "Not met: " + listText(notMet) + ".\n" +
          "Just encountered: " + (recent.isEmpty() ? "none" : recent) + ".\n" +
          "Pending intro: " + (pendingNames.isEmpty() ? "none" : pendingNames) + ".\n" +
          "Core exclusively owns encounter rolls and Party membership. Never spawn a character, add/remove/reorder Party, or change joined state. " +
          "Joined characters may be treated as already accompanying Cao Minh. Pending-intro characters are NOT yet accompanying Cao Minh at the start of this turn. " +
          (pendingNames.isEmpty()
              ? "Return encounterDialogue as []."
              : "A pending character encounter has triggered. Depict the encounter in the current location before any spoken line. " +
                  encounterRule +
                  "Do not imply the character was already walking with Cao Minh, already in his Party, or present in earlier Backrooms turns. " +
                  "Return encounterDialogue with 2-5 short Vietnamese spoken lines total, canon-accurate and natural. " +
                  "After this validated encounter scene the Core will auto-join the character in the same turn; do not ask the player to accept them and do not advance an extra Explorer Turn.");
    } catch (Exception e) {
      return "CHARACTER ENCOUNTER CORE: unavailable. Do not spawn characters or mutate Party.";
    }
  }

  static boolean isLucTramEncounterLevel(String levelKey) {
    String normalized = levelKey == null ? "" : levelKey.trim().toLowerCase(Locale.ROOT);
    return !normalized.isEmpty()
        && !"0".equals(normalized)
        && !normalized.startsWith("0.")
        && !normalized.startsWith("0-");
  }

  static boolean shouldEncounterLucTram(String levelKey, int roll) {
    return isLucTramEncounterLevel(levelKey)
        && roll >= 0 && roll < LUC_TRAM_RATE_PERCENT;
  }

  static boolean shouldEncounterRare(int roll) {
    return roll == 0;
  }

  static boolean isJoinedMember(JSONObject member) {
    return member != null && member.optBoolean("joined", false) && isEncounterCharacter(characterId(member));
  }

  private int nextRoll(int bound) {
    int value = rng.nextInt(bound);
    if (value < 0 || value >= bound) throw new IllegalStateException("RNG returned an out-of-range value");
    return value;
  }

  private void validateIntroDialogue(JSONArray dialogue) {
    if (dialogue == null || dialogue.length() < 2 || dialogue.length() > 5) {
      throw new IllegalArgumentException("Gemini phải trả 2-5 lượt thoại cho character vừa encounter.");
    }
    for (int i = 0; i < dialogue.length(); i++) {
      if (dialogue.optString(i, "").trim().isEmpty()) {
        throw new IllegalArgumentException("Hội thoại encounter không được có lượt thoại rỗng.");
      }
    }
  }

  private static JSONObject legacyStringMember(Object raw) {
    if (!(raw instanceof String) || ((String) raw).trim().isEmpty()) return null;
    try {
      return new JSONObject().put("name", ((String) raw).trim());
    } catch (Exception ignored) {
      return null;
    }
  }

  private static JSONObject normalizedMember(String id, JSONObject source) throws Exception {
    JSONObject member = source == null ? new JSONObject() : new JSONObject(source.toString());
    member.put("id", id);
    member.put("name", displayName(id));
    member.put("joined", true);
    member.put("present", true);
    member.put("joinConfirmed", true);
    if (!member.has("inventory")) member.put("inventory", defaultInventory(id));
    member.remove("level");
    member.remove("explorer");
    member.remove("exp");
    member.remove("baseStats");
    member.remove("stats");
    member.remove("hp");
    member.remove("currentHp");
    member.remove("currentHP");
    member.remove("maxHp");
    member.remove("maxHP");
    member.remove("equipment");
    return member;
  }

  private static JSONArray defaultInventory(String id) throws Exception {
    JSONArray inventory = new JSONArray();
    if ("lucia".equals(id)) {
      inventory.put(new JSONObject().put("name", "Tịch Quang Kiếm"));
      inventory.put(new JSONObject().put("name", "Thiên Cơ Bạch Kim Kiếm Khải"));
    } else if ("iris".equals(id)) {
      inventory.put(new JSONObject().put("name", "SRU Recon Frame R03"));
      inventory.put(new JSONObject().put("name", "Ivory & Ebony"));
    } else if ("syvial".equals(id)) {
      inventory.put(new JSONObject().put("name", "GodKiller"));
      inventory.put(new JSONObject().put("name", "Lucifer Armor"));
    }
    return inventory;
  }

  private static JSONArray filterEncounterIds(JSONArray ids) {
    JSONArray result = new JSONArray();
    if (ids == null) return result;
    for (String id : CANONICAL_ORDER) {
      if (containsString(ids, id)) result.put(id);
    }
    return result;
  }

  private static JSONObject encounterState(JSONObject state) throws Exception {
    JSONObject encounter = state.optJSONObject(ENCOUNTER_STATE);
    if (encounter == null) encounter = new JSONObject();
    state.put(ENCOUNTER_STATE, encounter);
    return encounter;
  }

  private static JSONArray filterIds(JSONArray ids, JSONArray party) {
    JSONArray result = new JSONArray();
    if (ids == null) return result;
    for (String id : CANONICAL_ORDER) {
      if (containsString(ids, id) && containsPartyId(party, id)) result.put(id);
    }
    return result;
  }

  private static boolean containsString(JSONArray values, String expected) {
    if (values == null) return false;
    for (int i = 0; i < values.length(); i++) {
      if (expected.equals(values.optString(i, "").trim().toLowerCase(Locale.ROOT))) return true;
    }
    return false;
  }

  private static boolean containsPartyId(JSONArray party, String id) {
    if (party == null) return false;
    for (int i = 0; i < party.length(); i++) {
      if (id.equals(characterId(party.optJSONObject(i)))) return true;
    }
    return false;
  }

  private static String characterId(JSONObject member) {
    if (member == null) return "";
    String raw = (member.optString("id", "") + " " + member.optString("name", "")).trim().toLowerCase(Locale.ROOT);
    if (raw.contains("cao_minh") ) return "cao_minh";
    if (raw.contains("lục trầm") || raw.contains("luc tram") || raw.contains("lucia")
        || raw.contains("hứa thuý mai") || raw.contains("hứa thúy mai") || raw.contains("hua thuy mai")) return "lucia";
    if (raw.contains("iris") || raw.contains("argus")) return "iris";
    if (raw.contains("syvial")) return "syvial";
    return "";
  }

  private static boolean isEncounterCharacter(String id) {
    return "lucia".equals(id) || "iris".equals(id) || "syvial".equals(id);
  }

  private static String displayName(String id) {
    if ("lucia".equals(id)) return "Lục Trầm";
    if ("iris".equals(id)) return "Iris";
    if ("syvial".equals(id)) return "Syvial";
    return id;
  }

  private static String displayNames(JSONArray ids) {
    if (ids == null) return "";
    List<String> names = new ArrayList<>();
    for (int i = 0; i < ids.length(); i++) {
      String id = ids.optString(i, "").trim().toLowerCase(Locale.ROOT);
      if (isEncounterCharacter(id)) names.add(displayName(id));
    }
    return join(names);
  }

  private static String listText(List<String> values) {
    return values.isEmpty() ? "none" : join(values);
  }

  private static String join(List<String> values) {
    StringBuilder output = new StringBuilder();
    for (String value : values) {
      if (output.length() > 0) output.append(", ");
      output.append(value);
    }
    return output.toString();
  }
}

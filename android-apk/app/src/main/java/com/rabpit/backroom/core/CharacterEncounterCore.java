package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Owns deterministic companion candidate eligibility, Party joins, migration and pending introductions. */
final class CharacterEncounterCore {
  static final int MAX_COMPANIONS = 3;
  static final int RARE_ENCOUNTER_BOUND = 4000;
  static final double LUC_TRAM_REUNION_PERCENT = 0.25d;

  private static final String ENCOUNTER_STATE = "characterEncounter";
  private static final String PENDING_INTRO = "pendingIntro";
  private static final String JUST_ENCOUNTERED = "justEncountered";
  private static final String[] CANONICAL_ORDER = {"luc_tram", "syvial"};

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
    boolean allowLucTram = state.optInt("currentLevel", 0) > 0;
    encounter.put(PENDING_INTRO, filterEncounterIds(encounter.optJSONArray(PENDING_INTRO), allowLucTram));
    encounter.put(JUST_ENCOUNTERED, filterEncounterIds(encounter.optJSONArray(JUST_ENCOUNTERED), allowLucTram));
    state.put(ENCOUNTER_STATE, encounter);
  }

  JSONArray situationCandidates(JSONObject state) throws Exception {
    normalizeState(state);
    JSONArray output = new JSONArray();
    JSONArray party = state.getJSONArray("party");
    JSONObject encounter = state.getJSONObject(ENCOUNTER_STATE);
    JSONArray pending = encounter.optJSONArray(PENDING_INTRO);
    if ((pending != null && pending.length() > 0) || party.length() >= MAX_COMPANIONS) return output;

    if (state.optInt("currentLevel", 0) > 0 && !containsPartyId(party, "luc_tram")) {
      output.put(new JSONObject()
          .put("candidateId", "character:luc_tram")
          .put("situationKey", "character:luc_tram")
          .put("kind", "CHARACTER")
          .put("category", "SOCIAL")
          .put("chancePercent", LUC_TRAM_REUNION_PERCENT)
          .put("payloadKey", "luc_tram")
          .put("source", "CANON")
          .put("publicSummary", "Lục Trầm xuất hiện; đây là cuộc tái ngộ với Cao Minh, không phải lần đầu gặp.")
          .put("proposalRequired", false)
          .put("eligibilityRuleId", "canon:luc_tram:after_level_0")
          .put("cooldownTurns", 24)
          .put("tags", new JSONArray().put("SOCIAL").put("CHARACTER").put("REUNION").put("luc_tram"))
          .put("keyRefs", new JSONArray().put("luc_tram")));
    }

    for (String id : new String[] {"syvial"}) {
      if (containsPartyId(party, id)) continue;
      output.put(new JSONObject()
          .put("candidateId", "character:" + id)
          .put("situationKey", "character:" + id)
          .put("kind", "CHARACTER")
          .put("category", "SOCIAL")
          .put("chancePercent", 100.0d / RARE_ENCOUNTER_BOUND)
          .put("payloadKey", id)
          .put("source", "CANON")
          .put("publicSummary", displayName(id) + " vừa xuất hiện và chạm mặt Cao Minh.")
          .put("proposalRequired", false)
          .put("eligibilityRuleId", "canon:character:" + id)
          .put("cooldownTurns", 24)
          .put("tags", new JSONArray().put("SOCIAL").put("CHARACTER").put(id))
          .put("keyRefs", new JSONArray().put(id)));
    }
    return output;
  }

  void activateEncounterCandidate(JSONObject state, String rawId) throws Exception {
    normalizeState(state);
    String id = rawId == null ? "" : rawId.trim().toLowerCase(Locale.ROOT);
    if ("luc_tram".equals(id) && state.optInt("currentLevel", 0) <= 0) {
      throw new IllegalStateException("Lục Trầm reunion is not eligible on Level 0.");
    }
    if (!isEncounterCharacter(id)) throw new IllegalArgumentException("Unknown character candidate: " + id);

    JSONArray party = state.getJSONArray("party");
    if (containsPartyId(party, id)) return;
    if (party.length() >= MAX_COMPANIONS) throw new IllegalStateException("Party đã đầy.");

    party.put(normalizedMember(id, null));
    state.put("party", party);
    JSONObject encounter = encounterState(state);
    encounter.put(JUST_ENCOUNTERED, new JSONArray().put(id));
    encounter.put(PENDING_INTRO, new JSONArray().put(id));
    encounter.put("joined", appendUnique(encounter.optJSONArray("joined"), id));
    state.put(ENCOUNTER_STATE, encounter);
    normalizeState(state);
  }

  void acknowledgePendingIntro(JSONObject state) throws Exception {
    normalizeState(state);
    JSONObject encounter = state.getJSONObject(ENCOUNTER_STATE);
    JSONArray pending = encounter.optJSONArray(PENDING_INTRO);
    if (pending == null || pending.length() == 0) return;
    encounter.put("lastIntroduced", new JSONArray(pending.toString()));
    encounter.put("lastIntroducedTurn", Math.max(1, state.optInt("turn", 1)));
    encounter.put(PENDING_INTRO, new JSONArray());
    encounter.put(JUST_ENCOUNTERED, new JSONArray());
    state.put(ENCOUNTER_STATE, encounter);
  }

  private static JSONArray appendUnique(JSONArray values, String id) throws Exception {
    JSONArray output = values == null ? new JSONArray() : new JSONArray(values.toString());
    if (!containsString(output, id)) output.put(id);
    return output;
  }

  String promptContext(JSONObject state) {
    try {
      normalizeState(state);
      JSONArray party = state.getJSONArray("party");
      JSONObject encounter = state.getJSONObject(ENCOUNTER_STATE);
      JSONArray pending = encounter.optJSONArray(PENDING_INTRO);
      List<String> joined = new ArrayList<>();
      List<String> randomNotMet = new ArrayList<>();
      for (String id : CANONICAL_ORDER) {
        if (containsPartyId(party, id)) {
          joined.add(displayName(id));
        } else if (!"luc_tram".equals(id) || state.optInt("currentLevel", 0) > 0) {
          randomNotMet.add(displayName(id));
        }
      }
      String recent = displayNames(encounter.optJSONArray(JUST_ENCOUNTERED));
      String pendingNames = displayNames(pending);
      boolean pendingLucTram = containsString(pending, "luc_tram");
      return "CHARACTER ENCOUNTER CORE:\n" +
          "Joined: " + listText(joined) + ".\n" +
          "Lục Trầm eligibility: 0.25% reunion candidate only after Level 0; Core owns the roll.\n" +
          "Encounter pool not met: " + listText(randomNotMet) + ".\n" +
          "Just encountered: " + (recent.isEmpty() ? "none" : recent) + ".\n" +
          "Pending intro/reunion: " + (pendingNames.isEmpty() ? "none" : pendingNames) + ".\n" +
          "Core exclusively owns encounter selection and Party membership. " +
          "Never spawn a character from narration, add/remove/reorder Party, or change joined state from narration. " +
          "Joined characters are authoritative. If pending includes Lục Trầm, depict a hostile/tense REUNION because she and Cao Minh knew and fought each other before Backrooms; never depict first contact or instant trust/romance. " +
          "For Syvial, pending means first contact. Narration must not decide whether anyone joined. " +
          (pendingNames.isEmpty()
              ? "Return encounterDialogue as []."
              : pendingLucTram
                  ? "Lục Trầm reunion is already committed. Depict a tense reunion in the current location; never frame it as first contact. " +
                      "Return encounterDialogue with 2-5 short Vietnamese spoken lines total, canon-accurate and natural. " +
                      "Do not invent Cao Minh's dialogue/decision and do not ask the player to approve Party membership."
                  : "A character first-contact event is already committed. Depict that first contact in the current location before any spoken line. " +
                      "Do not imply the character was present in earlier Backrooms turns. " +
                      "Return encounterDialogue with 2-5 short Vietnamese spoken lines total, canon-accurate and natural. " +
                      "Do not invent Cao Minh's dialogue/decision, ask the player to approve Party membership, or advance an extra Explorer Turn.");
    } catch (Exception e) {
      return "CHARACTER ENCOUNTER CORE: unavailable. Do not spawn characters or mutate Party.";
    }
  }

  static boolean isJoinedMember(JSONObject member) {
    return member != null && member.optBoolean("joined", false) && isEncounterCharacter(characterId(member));
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
    if ("luc_tram".equals(id)) {
      inventory.put(new JSONObject().put("name", "Tịch Quang Kiếm"));
      inventory.put(new JSONObject().put("name", "Thiên Cơ Bạch Kim Kiếm Khải"));
    } else if ("syvial".equals(id)) {
      inventory.put(new JSONObject().put("name", "GodKiller"));
      inventory.put(new JSONObject().put("name", "Lucifer Armor"));
    }
    return inventory;
  }

  private static JSONArray filterEncounterIds(JSONArray ids, boolean allowLucTram) {
    JSONArray result = new JSONArray();
    if (ids == null) return result;
    for (String id : CANONICAL_ORDER) {
      if ("luc_tram".equals(id) && !allowLucTram) continue;
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
    if (raw.contains("lục trầm") || raw.contains("luc tram") || raw.contains("luc_tram")) return "luc_tram";
    if (raw.contains("syvial")) return "syvial";
    return "";
  }

  private static boolean isEncounterCharacter(String id) {
    return "luc_tram".equals(id) || "syvial".equals(id);
  }

  private static String displayName(String id) {
    if ("luc_tram".equals(id)) return "Lục Trầm";
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

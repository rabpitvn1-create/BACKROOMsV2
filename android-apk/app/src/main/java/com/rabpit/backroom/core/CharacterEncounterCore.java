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
  static final double LUCIA_LEVEL_ZERO_PERCENT = 10.0d;
  static final double IRIS_ENCOUNTER_PERCENT = 0.25d;

  private static final String ENCOUNTER_STATE = "characterEncounter";
  private static final String PENDING_INTRO = "pendingIntro";
  private static final String JUST_ENCOUNTERED = "justEncountered";
  private static final String[] CANONICAL_ORDER = {"lucia", "iris", "syvial"};

  void normalizeState(JSONObject state) throws Exception {
    if (state == null) return;
    migrateRetiredCompanion(state);
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
    boolean allowIris = state.optInt("currentLevel", 0) > 0;
    encounter.put(PENDING_INTRO, filterEncounterIds(encounter.optJSONArray(PENDING_INTRO), allowIris));
    encounter.put(JUST_ENCOUNTERED, filterEncounterIds(encounter.optJSONArray(JUST_ENCOUNTERED), allowIris));
    state.put(ENCOUNTER_STATE, encounter);
  }

  static void migrateRetiredCompanion(JSONObject state) throws Exception {
    JSONArray party = state.optJSONArray("party");
    if (party != null) for (int i = 0; i < party.length(); i++) {
      Object raw = party.opt(i);
      JSONObject member = raw instanceof JSONObject ? (JSONObject) raw : legacyStringMember(raw);
      if (member == null) continue;
      String identity = member.optString("id", "").trim().toLowerCase(Locale.ROOT);
      if (identity.isEmpty()) identity = member.optString("name").toLowerCase(Locale.ROOT);
      if (!identity.contains("luc_tram") && !identity.contains("lục trầm") && !identity.contains("luc tram")) continue;
      JSONArray retired = state.optJSONArray("retiredLucTramParty");
      if (retired == null) retired = new JSONArray();
      retired.put(new JSONObject(member.toString()));
      state.put("retiredLucTramParty", retired);
      JSONArray inventory = defaultInventory("iris");
      JSONArray old = member.optJSONArray("inventory");
      if (old != null) for (int j = 0; j < old.length(); j++) {
        Object item = old.opt(j);
        String name = item instanceof JSONObject ? ((JSONObject) item).optString("name") : String.valueOf(item);
        if (!"Tịch Quang Kiếm".equals(name) && !"Tịch Quang".equals(name)
            && !"Thiên Cơ Bạch Kim Kiếm Khải".equals(name)) inventory.put(item);
      }
      member.put("id", "iris").put("name", "Iris").put("inventory", inventory);
      member.remove("relationship");
      member.remove("address");
      member.remove("canon");
      for (String key : new String[] {"avatar", "avatarRef", "overlay", "overlayRef"}) {
        if (member.optString(key).toLowerCase(Locale.ROOT).contains("luctram")) member.remove(key);
      }
      if (member.optString("role").contains("Thiên Kiếm")) member.remove("role");
      JSONObject existing = null;
      for (int j = 0; j < party.length(); j++) {
        JSONObject other = party.optJSONObject(j);
        if (j != i && other != null && "iris".equals(other.optString("id"))) { existing = other; break; }
      }
      if (existing != null) {
        JSONArray current = existing.optJSONArray("inventory");
        if (current == null) current = defaultInventory("iris");
        for (int j = 0; j < inventory.length(); j++) {
          Object item = inventory.opt(j);
          boolean present = false;
          for (int k = 0; k < current.length(); k++) if (String.valueOf(item).equals(String.valueOf(current.opt(k)))) present = true;
          if (!present) current.put(item);
        }
        existing.put("inventory", current);
        party.remove(i--);
      } else party.put(i, member);
    }
    JSONObject survival = state.optJSONObject("survival");
    JSONObject profiles = survival == null ? null : survival.optJSONObject("characters");
    if (profiles != null && profiles.has("luc_tram")) {
      if (!state.has("retiredLucTramSurvival")) state.put("retiredLucTramSurvival", new JSONObject(profiles.getJSONObject("luc_tram").toString()));
      if (!profiles.has("iris")) profiles.put("iris", profiles.get("luc_tram"));
      profiles.remove("luc_tram");
    }
    JSONObject combat = state.optJSONObject("combat");
    JSONArray participants = combat == null ? null : combat.optJSONArray("participants");
    if (participants != null) for (int i = 0; i < participants.length(); i++) {
      JSONObject actor = participants.optJSONObject(i);
      if (actor != null && "luc_tram".equals(actor.optString("id"))) actor.put("id", "iris").put("name", "Iris");
    }
    if (combat != null && ("Lục Trầm".equals(combat.optString("currentActor"))
        || "luc_tram".equals(combat.optString("currentActor")))) {
      combat.put("currentActor", "Iris");
      JSONObject skill = combat.optJSONObject("currentSkill");
      if (skill != null) skill.put("name", "Ivory & Ebony Joint Attack").put("description", "Hỏa lực song súng; gameplay projection 150% damage.");
      JSONObject ultimate = combat.optJSONObject("currentUltimate");
      if (ultimate != null) ultimate.put("name", "Ivory & Ebony Barrage");
    }
    JSONObject encounter = state.optJSONObject(ENCOUNTER_STATE);
    if (encounter != null) for (String key : new String[] {PENDING_INTRO, JUST_ENCOUNTERED, "lastIntroduced"}) {
      JSONArray ids = encounter.optJSONArray(key);
      if (ids != null) for (int i = 0; i < ids.length(); i++) if ("luc_tram".equals(ids.optString(i))) ids.put(i, "iris");
    }
  }

  JSONArray situationCandidates(JSONObject state) throws Exception {
    normalizeState(state);
    JSONArray output = new JSONArray();
    JSONArray party = state.getJSONArray("party");
    JSONObject encounter = state.getJSONObject(ENCOUNTER_STATE);
    JSONArray pending = encounter.optJSONArray(PENDING_INTRO);
    if ((pending != null && pending.length() > 0) || party.length() >= MAX_COMPANIONS) return output;

    String levelKey = state.optString(LevelCore.LEVEL_KEY,
        String.valueOf(state.optInt("currentLevel", 0))).trim();
    if ("0".equals(levelKey) && !containsPartyId(party, "lucia")) {
      output.put(new JSONObject()
          .put("candidateId", "character:lucia")
          .put("situationKey", "character:lucia")
          .put("kind", "CHARACTER")
          .put("category", "SOCIAL")
          .put("chancePercent", LUCIA_LEVEL_ZERO_PERCENT)
          .put("payloadKey", "lucia")
          .put("source", "CANON")
          .put("publicSummary", "Lucia Lục xuất hiện; đây là nhân vật riêng, không phải Iris.")
          .put("proposalRequired", false)
          .put("eligibilityRuleId", "canon:lucia:level_0")
          .put("cooldownTurns", 24)
          .put("tags", new JSONArray().put("SOCIAL").put("CHARACTER").put("FIRST_CONTACT").put("lucia"))
          .put("keyRefs", new JSONArray().put("lucia")));
    }

    if (state.optInt("currentLevel", 0) > 0 && !containsPartyId(party, "iris")) {
      output.put(new JSONObject()
          .put("candidateId", "character:iris")
          .put("situationKey", "character:iris")
          .put("kind", "CHARACTER")
          .put("category", "SOCIAL")
          .put("chancePercent", IRIS_ENCOUNTER_PERCENT)
          .put("payloadKey", "iris")
          .put("source", "CANON")
          .put("publicSummary", "Iris xuất hiện; đây là lần đầu gặp Cao Minh trừ khi continuity đã xác lập khác.")
          .put("proposalRequired", false)
          .put("eligibilityRuleId", "canon:iris:after_level_0")
          .put("cooldownTurns", 24)
          .put("tags", new JSONArray().put("SOCIAL").put("CHARACTER").put("FIRST_CONTACT").put("iris"))
          .put("keyRefs", new JSONArray().put("iris")));
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
    String levelKey = state.optString(LevelCore.LEVEL_KEY,
        String.valueOf(state.optInt("currentLevel", 0))).trim();
    if ("lucia".equals(id) && !"0".equals(levelKey)) {
      throw new IllegalStateException("Lucia Lục encounter is only eligible on Level 0.");
    }
    if ("iris".equals(id) && state.optInt("currentLevel", 0) <= 0) {
      throw new IllegalStateException("Iris encounter is not eligible on Level 0.");
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
        } else if (("lucia".equals(id) && "0".equals(state.optString(LevelCore.LEVEL_KEY,
            String.valueOf(state.optInt("currentLevel", 0))).trim()))
            || (!"lucia".equals(id) && (!"iris".equals(id)
                || state.optInt("currentLevel", 0) > 0))) {
          randomNotMet.add(displayName(id));
        }
      }
      String recent = displayNames(encounter.optJSONArray(JUST_ENCOUNTERED));
      String pendingNames = displayNames(pending);
      boolean pendingLucia = containsString(pending, "lucia");
      boolean pendingIris = containsString(pending, "iris");
      return "CHARACTER ENCOUNTER CORE:\n" +
          "Joined: " + listText(joined) + ".\n" +
          "Lucia Lục eligibility: 10% first-contact candidate on Level 0 only; Lucia Lục is NOT Iris.\n" +
          "Iris eligibility: 0.25% first-contact candidate only after Level 0; Core owns the roll.\n" +
          "Encounter pool not met: " + listText(randomNotMet) + ".\n" +
          "Just encountered: " + (recent.isEmpty() ? "none" : recent) + ".\n" +
          "Pending intro/reunion: " + (pendingNames.isEmpty() ? "none" : pendingNames) + ".\n" +
          "Core exclusively owns encounter selection and Party membership. " +
          "Never spawn a character from narration, add/remove/reorder Party, or change joined state from narration. " +
          "Never alias, rename, merge or migrate Lucia Lục/Hứa Thuý Mai into Iris; runtime ids lucia and iris are distinct. " +
          "Joined characters are authoritative. If pending includes Iris, depict FIRST CONTACT unless live continuity established contact; her relationship and address with Cao Minh are OPEN. Never import Kai romance or retired Lục Trầm rivalry. " +
          "If pending includes Lucia Lục, depict FIRST CONTACT; her relationship and address with Cao Minh are OPEN until continuity establishes them. " +
          "For Syvial, pending means first contact. Narration must not decide whether anyone joined. " +
          (pendingNames.isEmpty()
              ? "Return encounterDialogue as []."
              : pendingIris
                  ? "Iris first contact is already committed. Depict first contact in the current location; never assume prior intimacy or hostility. " +
                      "Return encounterDialogue with 2-5 short Vietnamese spoken lines total, canon-accurate and natural. " +
                      "Do not invent Cao Minh's dialogue/decision and do not ask the player to approve Party membership."
                  : pendingLucia
                      ? "Lucia Lục first contact is already committed. Keep her identity separate from Iris and do not import Iris canon, equipment, relationship or xưng hô. " +
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
    if ("lucia".equals(id)) {
      inventory.put(new JSONObject().put("name", "M4A1 cá nhân hóa"));
      inventory.put(new JSONObject().put("name", "Dao găm chiến đấu"));
      inventory.put(new JSONObject().put("name", "Đồng hồ định vị quân sự"));
    } else if ("iris".equals(id)) {
      inventory.put(new JSONObject().put("name", "Ivory"));
      inventory.put(new JSONObject().put("name", "Ebony"));
      inventory.put(new JSONObject().put("name", "Recon Frame"));
      inventory.put(new JSONObject().put("name", "Belial Core"));
    } else if ("syvial".equals(id)) {
      inventory.put(new JSONObject().put("name", "GodKiller"));
      inventory.put(new JSONObject().put("name", "Lucifer Armor"));
    }
    return inventory;
  }

  private static JSONArray filterEncounterIds(JSONArray ids, boolean allowIris) {
    JSONArray result = new JSONArray();
    if (ids == null) return result;
    for (String id : CANONICAL_ORDER) {
      if ("iris".equals(id) && !allowIris) continue;
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
    if (raw.contains("lucia") || raw.contains("hứa thuý mai") || raw.contains("hứa thúy mai")
        || raw.contains("hua thuy mai")) return "lucia";
    if (raw.contains("iris")) return "iris";
    if (raw.contains("syvial")) return "syvial";
    return "";
  }

  private static boolean isEncounterCharacter(String id) {
    return "lucia".equals(id) || "iris".equals(id) || "syvial".equals(id);
  }

  private static String displayName(String id) {
    if ("lucia".equals(id)) return "Lucia Lục";
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

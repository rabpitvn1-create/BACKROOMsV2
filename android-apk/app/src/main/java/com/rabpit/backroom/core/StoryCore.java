package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Owns authored-story runtime state.
 *
 * <p>This is intentionally content-agnostic: the novelist manuscript is not compiled into runtime
 * data yet. The Core only defines stable save-facing state and deterministic character lifecycle
 * events that a future Story Compiler/loader can drive.</p>
 */
final class StoryCore {
  static final String ROOT_KEY = "story";
  static final int SCHEMA_VERSION = 1;

  static final String STATUS_UNSEEN = "UNSEEN_IN_STORY";
  static final String STATUS_PARALLEL = "PARALLEL_STORY";
  static final String STATUS_REUNITED = "REUNITED";
  static final String STATUS_ACCOMPANYING = "ACCOMPANYING";
  static final String STATUS_PARTY_MEMBER = "PARTY_MEMBER";

  static final String EVENT_CHARACTER_PARALLEL = "CHARACTER_PARALLEL_STORY";
  static final String EVENT_CHARACTER_REUNION = "CHARACTER_REUNION";
  static final String EVENT_CHARACTER_ACCOMPANY = "CHARACTER_ACCOMPANY";
  static final String EVENT_CHARACTER_JOIN_PARTY = "CHARACTER_JOIN_PARTY";

  private static final String CHARACTERS_KEY = "characters";
  private static final String FLAGS_KEY = "flags";
  private static final String MANAGED_CHARACTERS_KEY = "managedCharacters";
  private static final Pattern CHARACTER_ID = Pattern.compile("[a-z0-9_]{1,80}");

  void normalizeState(JSONObject state) throws Exception {
    if (state == null) return;

    JSONObject story = state.optJSONObject(ROOT_KEY);
    if (story == null) story = new JSONObject();

    story.put("schemaVersion", SCHEMA_VERSION);
    if (!story.has("active")) story.put("active", false);
    if (!story.has("sourceRevision")) story.put("sourceRevision", "");
    if (!story.has("currentChapter")) story.put("currentChapter", "");
    if (!story.has("currentScene")) story.put("currentScene", "");
    if (!story.has("eventSequence")) story.put("eventSequence", 0);
    if (story.optJSONObject(FLAGS_KEY) == null) story.put(FLAGS_KEY, new JSONObject());

    JSONArray managed = story.optJSONArray(MANAGED_CHARACTERS_KEY);
    if (managed == null) managed = new JSONArray();
    if (!contains(managed, "luc_tram")) managed.put("luc_tram");
    story.put(MANAGED_CHARACTERS_KEY, managed);

    JSONObject characters = story.optJSONObject(CHARACTERS_KEY);
    if (characters == null) characters = new JSONObject();
    ensureCharacterState(characters, "luc_tram");

    // Backward-compatible migration: if an older save already has Lục Trầm joined,
    // preserve that fact and project the authored-story status forward.
    if (containsPartyId(state.optJSONArray("party"), "luc_tram")) {
      characterState(characters, "luc_tram").put("status", STATUS_PARTY_MEMBER);
    }

    story.put(CHARACTERS_KEY, characters);
    state.put(ROOT_KEY, story);
  }

  void applyCharacterEvent(
      JSONObject state,
      String rawCharacterId,
      String rawEventType,
      CharacterEncounterCore characterCore) throws Exception {
    if (state == null) throw new IllegalArgumentException("state is required");
    if (characterCore == null) throw new IllegalArgumentException("characterCore is required");

    normalizeState(state);
    String characterId = normalizeCharacterId(rawCharacterId);
    String eventType = rawEventType == null ? "" : rawEventType.trim().toUpperCase(Locale.ROOT);
    if (characterId.isEmpty()) throw new IllegalArgumentException("Invalid story character id.");

    JSONObject story = state.getJSONObject(ROOT_KEY);
    JSONObject characters = story.getJSONObject(CHARACTERS_KEY);
    JSONObject character = ensureCharacterState(characters, characterId);
    String before = character.optString("status", STATUS_UNSEEN);

    if (EVENT_CHARACTER_PARALLEL.equals(eventType)) {
      advanceStatus(character, STATUS_PARALLEL);
    } else if (EVENT_CHARACTER_REUNION.equals(eventType)) {
      advanceStatus(character, STATUS_REUNITED);
    } else if (EVENT_CHARACTER_ACCOMPANY.equals(eventType)) {
      if (statusRank(before) < statusRank(STATUS_REUNITED)) {
        throw new IllegalStateException("Character cannot accompany before authored reunion.");
      }
      advanceStatus(character, STATUS_ACCOMPANYING);
    } else if (EVENT_CHARACTER_JOIN_PARTY.equals(eventType)) {
      if (statusRank(before) < statusRank(STATUS_REUNITED)) {
        throw new IllegalStateException("Character cannot join Party before authored reunion.");
      }
      characterCore.joinFromStory(state, characterId);
      character.put("status", STATUS_PARTY_MEMBER);
    } else {
      throw new IllegalArgumentException("Unsupported story character event: " + eventType);
    }

    int sequence = Math.max(0, story.optInt("eventSequence", 0)) + 1;
    story.put("eventSequence", sequence);
    story.put("lastEventType", eventType);
    story.put("lastEventCharacter", characterId);
    story.put("lastEventTurn", Math.max(1, state.optInt("turn", 1)));
    story.put(CHARACTERS_KEY, characters);
    state.put(ROOT_KEY, story);
    normalizeState(state);
  }

  String promptContext(JSONObject state) {
    try {
      normalizeState(state);
      JSONObject story = state.getJSONObject(ROOT_KEY);
      String lucTramStatus = characterStatus(state, "luc_tram");
      boolean active = story.optBoolean("active", false);
      String chapter = story.optString("currentChapter", "").trim();
      String scene = story.optString("currentScene", "").trim();

      StringBuilder output = new StringBuilder("STORY CORE:\n");
      output.append("Authored pipeline active: ").append(active).append(".\n");
      if (active) {
        output.append("Current authored chapter: ")
            .append(chapter.isEmpty() ? "unbound" : chapter).append(".\n");
        output.append("Current authored scene: ")
            .append(scene.isEmpty() ? "unbound" : scene).append(".\n");
      } else {
        output.append("No compiled manuscript scene is bound yet; ordinary runtime behavior continues.\n");
      }
      output.append("Story-managed character Lục Trầm status: ").append(lucTramStatus).append(".\n");
      output.append("RULE: story-managed characters never spawn from random character rolls. ")
          .append("AI must not advance authored character status, invent a reunion, or add/remove Party members. ")
          .append("Story events request transitions; Java Core validates and applies them.");
      return output.toString();
    } catch (Exception e) {
      return "STORY CORE: unavailable. Do not invent authored story progression or Party changes.";
    }
  }

  static boolean isStoryManagedCharacter(String rawId) {
    return "luc_tram".equals(normalizeCharacterId(rawId));
  }

  static String characterStatus(JSONObject state, String rawCharacterId) {
    try {
      JSONObject story = state == null ? null : state.optJSONObject(ROOT_KEY);
      JSONObject characters = story == null ? null : story.optJSONObject(CHARACTERS_KEY);
      JSONObject character = characters == null ? null : characters.optJSONObject(normalizeCharacterId(rawCharacterId));
      return character == null ? STATUS_UNSEEN : normalizeStatus(character.optString("status", STATUS_UNSEEN));
    } catch (Exception ignored) {
      return STATUS_UNSEEN;
    }
  }

  private static JSONObject ensureCharacterState(JSONObject characters, String rawId) throws Exception {
    String id = normalizeCharacterId(rawId);
    JSONObject character = characters.optJSONObject(id);
    if (character == null) character = new JSONObject();
    character.put("id", id);
    character.put("status", normalizeStatus(character.optString("status", STATUS_UNSEEN)));
    characters.put(id, character);
    return character;
  }

  private static JSONObject characterState(JSONObject characters, String rawId) throws Exception {
    return ensureCharacterState(characters, rawId);
  }

  private static void advanceStatus(JSONObject character, String target) throws Exception {
    String current = normalizeStatus(character.optString("status", STATUS_UNSEEN));
    if (statusRank(target) > statusRank(current)) character.put("status", target);
  }

  private static int statusRank(String status) {
    String normalized = normalizeStatus(status);
    if (STATUS_PARALLEL.equals(normalized)) return 1;
    if (STATUS_REUNITED.equals(normalized)) return 2;
    if (STATUS_ACCOMPANYING.equals(normalized)) return 3;
    if (STATUS_PARTY_MEMBER.equals(normalized)) return 4;
    return 0;
  }

  private static String normalizeStatus(String raw) {
    String status = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
    if (STATUS_PARALLEL.equals(status)
        || STATUS_REUNITED.equals(status)
        || STATUS_ACCOMPANYING.equals(status)
        || STATUS_PARTY_MEMBER.equals(status)) {
      return status;
    }
    return STATUS_UNSEEN;
  }

  private static String normalizeCharacterId(String raw) {
    String id = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT)
        .replace(' ', '_')
        .replace('-', '_');
    if ("lục_trầm".equals(id) || "luc_tram".equals(id)) id = "luc_tram";
    return CHARACTER_ID.matcher(id).matches() ? id : "";
  }

  private static boolean contains(JSONArray values, String expected) {
    if (values == null) return false;
    for (int i = 0; i < values.length(); i++) {
      if (expected.equals(values.optString(i, "").trim().toLowerCase(Locale.ROOT))) return true;
    }
    return false;
  }

  private static boolean containsPartyId(JSONArray party, String expected) {
    if (party == null) return false;
    for (int i = 0; i < party.length(); i++) {
      JSONObject member = party.optJSONObject(i);
      if (member == null) continue;
      String id = normalizeCharacterId(member.optString("id", ""));
      String name = member.optString("name", "").trim().toLowerCase(Locale.ROOT);
      if (expected.equals(id)
          || ("luc_tram".equals(expected) && ("lục trầm".equals(name) || "luc tram".equals(name)))) {
        return true;
      }
    }
    return false;
  }
}

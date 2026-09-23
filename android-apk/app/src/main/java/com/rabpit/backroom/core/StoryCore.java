package com.rabpit.backroom.core;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;
import java.util.regex.Pattern;

/** Owns authored-story state, deterministic manuscript progression, and story character lifecycle. */
final class StoryCore {
  static final String ROOT_KEY = "story";
  static final int SCHEMA_VERSION = 2;

  static final String STATUS_UNSEEN = "UNSEEN_IN_STORY";
  static final String STATUS_PARALLEL = "PARALLEL_STORY";
  static final String STATUS_REUNITED = "REUNITED";
  static final String STATUS_ACCOMPANYING = "ACCOMPANYING";
  static final String STATUS_PARTY_MEMBER = "PARTY_MEMBER";

  static final String PRESENCE_UNKNOWN = "UNKNOWN";
  static final String PRESENCE_PRESENT = "PRESENT";
  static final String PRESENCE_MISSING = "MISSING";

  static final String EVENT_CHARACTER_PARALLEL = "CHARACTER_PARALLEL_STORY";
  static final String EVENT_CHARACTER_REUNION = "CHARACTER_REUNION";
  static final String EVENT_CHARACTER_ACCOMPANY = "CHARACTER_ACCOMPANY";
  static final String EVENT_CHARACTER_JOIN_PARTY = "CHARACTER_JOIN_PARTY";
  static final String EVENT_CHARACTER_PRESENT = "CHARACTER_PRESENT";
  static final String EVENT_CHARACTER_MISSING = "CHARACTER_MISSING";
  static final String EVENT_STORY_FLAG_SET = "STORY_FLAG_SET";
  static final String EVENT_LEVEL0_ARC_BOUNDARY_REACHED = "LEVEL0_ARC_BOUNDARY_REACHED";

  static final String ADVANCE_ACTION_VI = "tiếp tục cốt truyện";

  static final class AuthoredTurn {
    final String reply;
    final String chapterId;
    final String segmentId;
    final String thread;
    final String visibility;

    AuthoredTurn(String reply, String chapterId, String segmentId, String thread, String visibility) {
      this.reply = reply;
      this.chapterId = chapterId;
      this.segmentId = segmentId;
      this.thread = thread;
      this.visibility = visibility;
    }
  }

  private static final String CHARACTERS_KEY = "characters";
  private static final String FLAGS_KEY = "flags";
  private static final String MANAGED_CHARACTERS_KEY = "managedCharacters";
  private static final Pattern CHARACTER_ID = Pattern.compile("[a-z0-9_]{1,80}");

  private final StoryRepository repository;

  StoryCore() {
    this((StoryRepository)null);
  }

  StoryCore(Context context) {
    this(context == null ? null : new StoryRepository(context));
  }

  StoryCore(StoryRepository repository) {
    this.repository = repository;
  }

  static StoryCore withRepository(StoryRepository repository) {
    return new StoryCore(repository);
  }

  void normalizeState(JSONObject state) throws Exception {
    if (state == null) return;

    JSONObject story = state.optJSONObject(ROOT_KEY);
    boolean levelZeroRoot = "0".equals(state.optString(LevelCore.LEVEL_KEY,
        String.valueOf(state.optInt("currentLevel", 0))).trim());

    if (repository != null && repository.available() && levelZeroRoot) {
      String revision = story == null ? "" : story.optString("sourceRevision", "").trim();
      if (!repository.sourceRevision().equals(revision)) {
        story = new JSONObject();
      }
    }
    if (story == null) story = new JSONObject();

    story.put("schemaVersion", SCHEMA_VERSION);
    if (!story.has("active")) story.put("active", false);
    if (!story.has("sourceRevision")) story.put("sourceRevision", "");
    if (!story.has("currentChapter")) story.put("currentChapter", "");
    if (!story.has("currentScene")) story.put("currentScene", "");
    if (!story.has("currentSegmentIndex")) story.put("currentSegmentIndex", 0);
    if (!story.has("currentSegmentCount")) story.put("currentSegmentCount", 0);
    if (!story.has("segmentDelivered")) story.put("segmentDelivered", false);
    if (!story.has("chapterEntered")) story.put("chapterEntered", false);
    if (!story.has("arcComplete")) story.put("arcComplete", false);
    if (!story.has("thread")) story.put("thread", "cao_minh");
    if (!story.has("visibility")) story.put("visibility", "player");
    if (!story.has("eventSequence")) story.put("eventSequence", 0);
    if (story.optJSONObject(FLAGS_KEY) == null) story.put(FLAGS_KEY, new JSONObject());

    JSONArray managed = story.optJSONArray(MANAGED_CHARACTERS_KEY);
    if (managed == null) managed = new JSONArray();
    if (!contains(managed, "luc_tram")) managed.put("luc_tram");
    story.put(MANAGED_CHARACTERS_KEY, managed);

    JSONObject characters = story.optJSONObject(CHARACTERS_KEY);
    if (characters == null) characters = new JSONObject();
    ensureCharacterState(characters, "luc_tram");
    story.put(CHARACTERS_KEY, characters);

    if (repository != null && repository.available() && levelZeroRoot && !story.optBoolean("arcComplete", false)) {
      bindRepositoryStory(story);
    }

    state.put(ROOT_KEY, story);
  }

  boolean ownsLevelProgression(JSONObject state) {
    try {
      normalizeState(state);
      JSONObject story = state.optJSONObject(ROOT_KEY);
      return story != null
          && story.optBoolean("active", false)
          && "0".equals(state.optString(LevelCore.LEVEL_KEY, "").trim());
    } catch (Exception ignored) {
      return false;
    }
  }

  boolean hasPendingAuthoredStory(JSONObject state) {
    try {
      normalizeState(state);
      JSONObject story = state.getJSONObject(ROOT_KEY);
      return story.optBoolean("active", false) && !story.optBoolean("arcComplete", false);
    } catch (Exception ignored) {
      return false;
    }
  }

  boolean blocksFreePlayerAction(JSONObject state) {
    try {
      normalizeState(state);
      JSONObject story = state.getJSONObject(ROOT_KEY);
      return story.optBoolean("active", false)
          && !story.optBoolean("arcComplete", false)
          && "cutaway".equals(story.optString("visibility", ""));
    } catch (Exception ignored) {
      return false;
    }
  }

  static boolean isAdvanceAction(String action) {
    String text = action == null ? "" : action.trim().toLowerCase(Locale.ROOT);
    return ADVANCE_ACTION_VI.equals(text) || "continue story".equals(text);
  }

  AuthoredTurn advanceAndRender(
      JSONObject state, String action, CharacterEncounterCore characterCore) throws Exception {
    normalizeState(state);
    if (!isAdvanceAction(action)) return null;

    JSONObject story = state.getJSONObject(ROOT_KEY);
    if (!story.optBoolean("active", false) || story.optBoolean("arcComplete", false)
        || repository == null || !repository.available()) {
      return null;
    }

    String chapterId = story.optString("currentChapter", "").trim();
    StoryRepository.Chapter chapter = repository.chapter(chapterId);
    if (chapter == null) return null;

    int index = Math.max(0, story.optInt("currentSegmentIndex", 0));
    boolean delivered = story.optBoolean("segmentDelivered", false);

    if (delivered) {
      int count = repository.segmentCount(chapterId);
      if (index + 1 < count) {
        index++;
      } else {
        String next = chapter.nextChapter;
        if (next.isEmpty()) {
          story.put("arcComplete", true);
          state.put(ROOT_KEY, story);
          return null;
        }
        chapterId = next;
        chapter = repository.chapter(chapterId);
        if (chapter == null) throw new IllegalStateException("Missing next authored chapter: " + chapterId);
        index = 0;
        story.put("currentChapter", chapterId);
        story.put("chapterEntered", false);
        story.put("segmentDelivered", false);
      }
    }

    syncChapterProjection(story, chapter, index);
    if (!story.optBoolean("chapterEntered", false)) {
      applyEvents(state, chapter.eventsOnEnter, characterCore);
      story = state.getJSONObject(ROOT_KEY);
      syncChapterProjection(story, chapter, index);
      story.put("chapterEntered", true);
    }

    StoryRepository.Segment segment = repository.segment(chapterId, index);
    if (segment == null || segment.text.trim().isEmpty()) {
      throw new IllegalStateException("Missing authored story segment: " + chapterId + "#" + index);
    }

    story.put("currentSegmentIndex", index);
    story.put("currentSegmentCount", segment.count);
    story.put("currentScene", segment.id);
    story.put("segmentDelivered", true);
    state.put(ROOT_KEY, story);

    if (index == segment.count - 1) {
      applyEvents(state, chapter.eventsOnExit, characterCore);
      story = state.getJSONObject(ROOT_KEY);
      syncChapterProjection(story, chapter, index);
      story.put("segmentDelivered", true);
      if (story.optBoolean("arcComplete", false)) story.put("active", true);
      state.put(ROOT_KEY, story);
    }

    return new AuthoredTurn(segment.text, chapter.id, segment.id, chapter.thread, chapter.visibility);
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
    story.put("active", true);
    JSONObject characters = story.getJSONObject(CHARACTERS_KEY);
    JSONObject character = ensureCharacterState(characters, characterId);
    String before = character.optString("status", STATUS_UNSEEN);

    if (EVENT_CHARACTER_PARALLEL.equals(eventType)) {
      advanceStatus(character, STATUS_PARALLEL);
      character.put("presence", PRESENCE_PRESENT);
    } else if (EVENT_CHARACTER_REUNION.equals(eventType)) {
      advanceStatus(character, STATUS_REUNITED);
      character.put("presence", PRESENCE_PRESENT);
    } else if (EVENT_CHARACTER_ACCOMPANY.equals(eventType)) {
      if (statusRank(before) < statusRank(STATUS_REUNITED)) {
        throw new IllegalStateException("Character cannot accompany before authored reunion.");
      }
      advanceStatus(character, STATUS_ACCOMPANYING);
      character.put("presence", PRESENCE_PRESENT);
    } else if (EVENT_CHARACTER_JOIN_PARTY.equals(eventType)) {
      if (statusRank(before) < statusRank(STATUS_REUNITED)) {
        throw new IllegalStateException("Character cannot join Party before authored reunion.");
      }
      characterCore.joinFromStory(state, characterId);
      character.put("status", STATUS_PARTY_MEMBER);
      character.put("presence", PRESENCE_PRESENT);
    } else {
      throw new IllegalArgumentException("Unsupported story character event: " + eventType);
    }

    story.put(CHARACTERS_KEY, characters);
    state.put(ROOT_KEY, story);
    recordEvent(state, eventType, characterId);
  }

  String promptContext(JSONObject state) {
    try {
      normalizeState(state);
      JSONObject story = state.getJSONObject(ROOT_KEY);
      boolean active = story.optBoolean("active", false);
      String chapterId = story.optString("currentChapter", "").trim();
      StoryRepository.Chapter chapter = repository == null ? null : repository.chapter(chapterId);

      StringBuilder output = new StringBuilder("STORY CORE:\n");
      output.append("Authored pipeline active: ").append(active).append(".\n");
      if (!active || chapter == null) {
        output.append("No authored manuscript chapter is currently bound.\n");
      } else {
        output.append("Current chapter: ").append(chapter.id).append(" — ").append(chapter.title).append(".\n");
        output.append("Current authored segment: ")
            .append(story.optString("currentScene", "unstarted")).append(".\n");
        output.append("Story thread: ").append(chapter.thread)
            .append("; visibility=").append(chapter.visibility).append(".\n");
        if ("cutaway".equals(chapter.visibility)) {
          output.append("CUTAWAY RULE: this material is reader-visible but is NOT knowledge Cao Minh possesses. ")
              .append("Do not make Cao Minh react to, remember, or act on cutaway-only facts until the authored story discloses them to him.\n");
        }
        appendArray(output, "REQUIRED AUTHORED FACTS", chapter.requiredFacts);
        appendArray(output, "FORBIDDEN CLAIMS", chapter.forbiddenClaims);
      }

      output.append("Story-managed character Lục Trầm status: ")
          .append(characterStatus(state, "luc_tram")).append(".\n");
      JSONObject characters = story.optJSONObject(CHARACTERS_KEY);
      JSONObject nam = characters == null ? null : characters.optJSONObject("nam");
      if (nam != null) {
        output.append("Story-local Nam presence: ")
            .append(nam.optString("presence", PRESENCE_UNKNOWN)).append(".\n");
      }
      if (story.optBoolean("arcComplete", false)) {
        output.append("Level 0 authored arc boundary has been reached. This is NOT a validated Level 1 transition.\n");
      }
      output.append("RULE: authored prose is emitted verbatim by Java Core. AI handles only free interaction around it. ")
          .append("AI must not advance manuscript position, invent authored events, mutate Story state, or add/remove Party members.");
      return output.toString();
    } catch (Exception e) {
      return "STORY CORE: unavailable. Do not invent authored story progression or Party changes.";
    }
  }

  JSONObject logMetadata(JSONObject state) {
    JSONObject result = new JSONObject();
    try {
      normalizeState(state);
      JSONObject story = state.getJSONObject(ROOT_KEY);
      result.put("storyThread", story.optString("thread", "cao_minh"));
      result.put("storyVisibility", story.optString("visibility", "player"));
      result.put("storyChapter", story.optString("currentChapter", ""));
      result.put("storySegmentId", story.optString("currentScene", ""));
    } catch (Exception ignored) {}
    return result;
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

  private void bindRepositoryStory(JSONObject story) throws Exception {
    if (!repository.available()) return;
    if (!story.optBoolean("active", false) || story.optString("currentChapter", "").trim().isEmpty()) {
      story.put("active", true);
      story.put("sourceRevision", repository.sourceRevision());
      story.put("currentChapter", repository.startChapter());
      story.put("currentSegmentIndex", 0);
      story.put("segmentDelivered", false);
      story.put("chapterEntered", false);
      story.put("arcComplete", false);
    }
    StoryRepository.Chapter chapter = repository.chapter(story.optString("currentChapter", ""));
    if (chapter != null) {
      syncChapterProjection(story, chapter, Math.max(0, story.optInt("currentSegmentIndex", 0)));
    }
  }

  private void syncChapterProjection(JSONObject story, StoryRepository.Chapter chapter, int index) throws Exception {
    story.put("currentChapter", chapter.id);
    story.put("currentSegmentIndex", index);
    story.put("currentSegmentCount", repository == null ? 0 : repository.segmentCount(chapter.id));
    story.put("thread", chapter.thread);
    story.put("visibility", chapter.visibility);
  }

  private void applyEvents(
      JSONObject state, JSONArray events, CharacterEncounterCore characterCore) throws Exception {
    if (events == null) return;
    for (int i = 0; i < events.length(); i++) {
      JSONObject event = events.optJSONObject(i);
      if (event == null) continue;
      String type = event.optString("type", "").trim().toUpperCase(Locale.ROOT);
      if (EVENT_CHARACTER_PARALLEL.equals(type)
          || EVENT_CHARACTER_REUNION.equals(type)
          || EVENT_CHARACTER_ACCOMPANY.equals(type)
          || EVENT_CHARACTER_JOIN_PARTY.equals(type)) {
        applyCharacterEvent(state, event.optString("characterId", ""), type, characterCore);
        continue;
      }

      JSONObject story = state.getJSONObject(ROOT_KEY);
      JSONObject characters = story.getJSONObject(CHARACTERS_KEY);
      if (EVENT_CHARACTER_PRESENT.equals(type) || EVENT_CHARACTER_MISSING.equals(type)) {
        String id = normalizeCharacterId(event.optString("characterId", ""));
        if (id.isEmpty()) throw new IllegalArgumentException("Invalid story-local character id.");
        JSONObject character = ensureCharacterState(characters, id);
        character.put("scope", event.optString("scope", character.optString("scope", "story_local")));
        character.put("presence", EVENT_CHARACTER_MISSING.equals(type) ? PRESENCE_MISSING : PRESENCE_PRESENT);
        story.put(CHARACTERS_KEY, characters);
        state.put(ROOT_KEY, story);
        recordEvent(state, type, id);
      } else if (EVENT_STORY_FLAG_SET.equals(type)) {
        String key = event.optString("key", "").trim();
        if (key.isEmpty()) throw new IllegalArgumentException("Story flag key is required.");
        JSONObject flags = story.getJSONObject(FLAGS_KEY);
        Object value = event.has("value") ? event.get("value") : Boolean.TRUE;
        flags.put(key, value);
        story.put(FLAGS_KEY, flags);
        state.put(ROOT_KEY, story);
        recordEvent(state, type, key);
      } else if (EVENT_LEVEL0_ARC_BOUNDARY_REACHED.equals(type)) {
        JSONObject flags = story.getJSONObject(FLAGS_KEY);
        flags.put("level0_arc_boundary_reached", true);
        story.put(FLAGS_KEY, flags);
        story.put("arcComplete", true);
        state.put(ROOT_KEY, story);
        recordEvent(state, type, "");
      } else if (!type.isEmpty()) {
        throw new IllegalArgumentException("Unsupported story event: " + type);
      }
    }
  }

  private void recordEvent(JSONObject state, String eventType, String subject) throws Exception {
    JSONObject story = state.getJSONObject(ROOT_KEY);
    int sequence = Math.max(0, story.optInt("eventSequence", 0)) + 1;
    story.put("eventSequence", sequence);
    story.put("lastEventType", eventType);
    story.put("lastEventSubject", subject == null ? "" : subject);
    story.put("lastEventTurn", Math.max(1, state.optInt("turn", 1)));
    state.put(ROOT_KEY, story);
  }

  private static JSONObject ensureCharacterState(JSONObject characters, String rawId) throws Exception {
    String id = normalizeCharacterId(rawId);
    JSONObject character = characters.optJSONObject(id);
    if (character == null) character = new JSONObject();
    character.put("id", id);
    if (!character.has("status")) character.put("status", STATUS_UNSEEN);
    else character.put("status", normalizeStatus(character.optString("status", STATUS_UNSEEN)));
    if (!character.has("presence")) character.put("presence", PRESENCE_UNKNOWN);
    characters.put(id, character);
    return character;
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

  private static void appendArray(StringBuilder output, String label, JSONArray values) {
    if (values == null || values.length() == 0) return;
    output.append(label).append(": ");
    for (int i = 0; i < values.length(); i++) {
      String value = values.optString(i, "").trim();
      if (value.isEmpty()) continue;
      if (i > 0) output.append(" | ");
      output.append(value);
    }
    output.append("\n");
  }
}

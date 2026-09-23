package com.rabpit.backroom.core;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Owns authored-story state, deterministic progression, and prefetched hidden story decisions. */
final class StoryCore {
  static final String ROOT_KEY = "story";
  static final int SCHEMA_VERSION = 3;

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
  static final String DECISION_PREFETCH_REQUIRED = "PREFETCH_REQUIRED";
  static final String DECISION_READY = "READY";
  static final String OUTCOME_CANON = "CANON_PROGRESS";
  static final String OUTCOME_TRAP = "TRAP_LOOP";
  static final String OUTCOME_CONVERGE = "CONVERGE";

  static final class AuthoredTurn {
    final String reply;
    final String chapterId;
    final String segmentId;
    final String thread;
    final String visibility;
    final String mode;
    final JSONObject decisionContract;

    AuthoredTurn(
        String reply,
        String chapterId,
        String segmentId,
        String thread,
        String visibility,
        String mode,
        JSONObject decisionContract) {
      this.reply = reply;
      this.chapterId = chapterId;
      this.segmentId = segmentId;
      this.thread = thread;
      this.visibility = visibility;
      this.mode = mode;
      this.decisionContract = copyObject(decisionContract);
    }
  }

  static final class DecisionResolution {
    final String visibleChoice;
    final String reply;
    final String outcome;
    final AuthoredTurn authoredTurn;
    final boolean looped;

    DecisionResolution(
        String visibleChoice,
        String reply,
        String outcome,
        AuthoredTurn authoredTurn,
        boolean looped) {
      this.visibleChoice = visibleChoice;
      this.reply = reply;
      this.outcome = outcome;
      this.authoredTurn = authoredTurn;
      this.looped = looped;
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
    if (!story.has("awaitingDecision")) story.put("awaitingDecision", false);
    if (!story.has("decisionStatus")) story.put("decisionStatus", "");
    if (!story.has("decisionId")) story.put("decisionId", "");
    if (story.optJSONObject("decisionContract") == null) story.put("decisionContract", new JSONObject());
    if (story.optJSONObject("decisionPackage") == null) story.put("decisionPackage", new JSONObject());
    if (story.optJSONObject("loopHistory") == null) story.put("loopHistory", new JSONObject());
    if (story.optJSONObject(FLAGS_KEY) == null) story.put(FLAGS_KEY, new JSONObject());

    // Old v1 interaction fields are intentionally neutralized. Old save compatibility is not required.
    story.put("awaitingInteraction", false);
    story.put("interactionChoices", new JSONArray());
    story.put("interactionGuard", "");
    story.put("interactionSegmentId", "");
    story.put("interactionResolutionPending", false);
    story.put("selectedInteractionAction", "");

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
          && ("cutaway".equals(story.optString("visibility", ""))
              || story.optBoolean("awaitingDecision", false));
    } catch (Exception ignored) {
      return false;
    }
  }

  boolean awaitingDecision(JSONObject state) {
    try {
      normalizeState(state);
      JSONObject story = state.getJSONObject(ROOT_KEY);
      return story.optBoolean("active", false)
          && !story.optBoolean("arcComplete", false)
          && story.optBoolean("awaitingDecision", false);
    } catch (Exception ignored) {
      return false;
    }
  }

  boolean decisionNeedsPrefetch(JSONObject state) {
    try {
      normalizeState(state);
      JSONObject story = state.getJSONObject(ROOT_KEY);
      return story.optBoolean("awaitingDecision", false)
          && DECISION_PREFETCH_REQUIRED.equals(story.optString("decisionStatus", ""));
    } catch (Exception ignored) {
      return false;
    }
  }

  boolean decisionReady(JSONObject state) {
    try {
      normalizeState(state);
      JSONObject story = state.getJSONObject(ROOT_KEY);
      JSONObject pack = story.optJSONObject("decisionPackage");
      return story.optBoolean("awaitingDecision", false)
          && DECISION_READY.equals(story.optString("decisionStatus", ""))
          && pack != null
          && pack.optJSONArray("choices") != null
          && pack.optJSONArray("choices").length() == 3;
    } catch (Exception ignored) {
      return false;
    }
  }

  JSONArray decisionChoices(JSONObject state) {
    try {
      normalizeState(state);
      if (!decisionReady(state)) return new JSONArray();
      return copyArray(state.getJSONObject(ROOT_KEY)
          .getJSONObject("decisionPackage").optJSONArray("choices"));
    } catch (Exception ignored) {
      return new JSONArray();
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
    if (story.optBoolean("awaitingDecision", false)) {
      throw new IllegalStateException("Story decision must be resolved before authored progression.");
    }
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
    armDecisionIfNeeded(story, chapter, segment);
    state.put(ROOT_KEY, story);

    if (index == segment.count - 1) {
      applyEvents(state, chapter.eventsOnExit, characterCore);
      story = state.getJSONObject(ROOT_KEY);
      syncChapterProjection(story, chapter, index);
      story.put("segmentDelivered", true);
      if (story.optBoolean("arcComplete", false)) story.put("active", true);
      state.put(ROOT_KEY, story);
    }

    return new AuthoredTurn(
        segment.text,
        chapter.id,
        segment.id,
        chapter.thread,
        chapter.visibility,
        segment.mode,
        segment.decisionContract);
  }

  JSONObject decisionPrefetchRequest(JSONObject state, String recentStory) throws Exception {
    normalizeState(state);
    JSONObject output = new JSONObject().put("needed", false);
    if (!decisionNeedsPrefetch(state) || repository == null) return output;

    JSONObject story = state.getJSONObject(ROOT_KEY);
    String chapterId = story.optString("currentChapter", "").trim();
    int index = Math.max(0, story.optInt("currentSegmentIndex", 0));
    StoryRepository.Segment current = repository.segment(chapterId, index);
    StoryRepository.Segment next = repository.nextSegment(chapterId, index);
    JSONObject contract = story.optJSONObject("decisionContract");
    if (current == null || next == null || contract == null || contract.length() == 0) return output;

    String contextHash = decisionContextHash(state);
    String decisionId = story.optString("decisionId", current.id).trim();
    String canonText = contract.optString("canonChoiceText", "").trim();
    String guard = contract.optString("decisionGuard", "").trim();

    StringBuilder prompt = new StringBuilder();
    prompt.append("BACKROOMsV2 STORY DECISION PREFETCH\n\n");
    prompt.append("The novelist owns canon. The canonical player choice is already fixed and you MUST NOT alter it.\n");
    prompt.append("You must create exactly TWO additional plausible choices and their COMPLETE prepared reactions now, before the player clicks anything.\n");
    prompt.append("At click time there will be NO model call.\n\n");
    prompt.append("HIDDEN CANON CHOICE (do not repeat or label it): ").append(canonText).append("\n");
    prompt.append("CURRENT PAUSE ANCHOR:\n").append(clipTail(current.text, 1400)).append("\n\n");
    prompt.append("NEXT AUTHORED BEAT (private; never spoil its result in choice text):\n")
        .append(clip(next.text, 1800)).append("\n\n");
    if (recentStory != null && !recentStory.trim().isEmpty()) {
      prompt.append("RECENT READER-VISIBLE CONTEXT:\n").append(clip(recentStory, 2600)).append("\n\n");
    }
    prompt.append("DECISION GUARD:\n").append(guard).append("\n\n");
    prompt.append("Create:\n");
    prompt.append("1) TRAP_LOOP: a choice that looks genuinely reasonable but is wrong because of a clue/rule already available to the player. ")
        .append("Its reaction must naturally fold space/perception/action back to the CURRENT PAUSE ANCHOR. Never say 'wrong', 'trap', 'reset', 'loop', or expose game mechanics. ")
        .append("Do not injure, kill, consume items, change Party, reveal lore, spawn Entities, or alter canon.\n");
    prompt.append("2) CONVERGE: a different reasonable local approach with a short reaction that can immediately rejoin the exact NEXT AUTHORED BEAT unchanged. ")
        .append("Do not duplicate or summarize the next authored beat.\n\n");
    prompt.append("PUBLIC CHOICE RULES: both choices must be concise natural Vietnamese, similar in attractiveness/length to the canon choice, ")
        .append("no A/B/C labels, no bullets, no meta words, no obvious stupid option, no outcome claims.\n");
    prompt.append("REACTION RULES: Vietnamese prose only, max 900 characters each. No new authoritative facts.\n\n");
    prompt.append("Return JSON only:\n");
    prompt.append("{\"trap\":{\"text\":\"...\",\"reply\":\"...\"},")
        .append("\"converge\":{\"text\":\"...\",\"reply\":\"...\"}}");

    output.put("needed", true);
    output.put("decisionId", decisionId);
    output.put("contextHash", contextHash);
    output.put("prompt", prompt.toString());
    return output;
  }

  void installDecisionPackage(JSONObject state, String expectedContextHash, JSONObject generated)
      throws Exception {
    normalizeState(state);
    if (!decisionNeedsPrefetch(state)) throw new IllegalStateException("No story decision needs prefetch.");

    JSONObject story = state.getJSONObject(ROOT_KEY);
    String actualHash = decisionContextHash(state);
    if (expectedContextHash == null || !actualHash.equals(expectedContextHash.trim())) {
      throw new IllegalStateException("Story decision context changed before prefetch completed.");
    }

    JSONObject contract = story.optJSONObject("decisionContract");
    String canonText = contract == null ? "" : contract.optString("canonChoiceText", "").trim();
    if (!validPublicChoice(canonText)) throw new IllegalStateException("Invalid canonical decision text.");

    JSONObject trap = generated == null ? null : generated.optJSONObject("trap");
    JSONObject converge = generated == null ? null : generated.optJSONObject("converge");
    String trapText = trap == null ? "" : trap.optString("text", "").trim();
    String trapReply = trap == null ? "" : trap.optString("reply", "").trim();
    String convergeText = converge == null ? "" : converge.optString("text", "").trim();
    String convergeReply = converge == null ? "" : converge.optString("reply", "").trim();

    if (!validPublicChoice(trapText) || !validPublicChoice(convergeText)
        || !validPreparedReply(trapReply) || !validPreparedReply(convergeReply)) {
      throw new IllegalArgumentException("Prefetched story decision package is incomplete or unsafe.");
    }
    if (sameChoice(canonText, trapText) || sameChoice(canonText, convergeText)
        || sameChoice(trapText, convergeText)) {
      throw new IllegalArgumentException("Story decision choices must be distinct.");
    }

    String decisionId = story.optString("decisionId", "").trim();
    if (decisionId.isEmpty()) throw new IllegalStateException("Missing story decision id.");

    JSONObject outcomes = new JSONObject();
    List<JSONObject> publicChoices = new ArrayList<>();
    addOutcome(publicChoices, outcomes, decisionId + ":canon", canonText, OUTCOME_CANON, "");
    addOutcome(publicChoices, outcomes, decisionId + ":trap", trapText, OUTCOME_TRAP, trapReply);
    addOutcome(publicChoices, outcomes, decisionId + ":converge", convergeText, OUTCOME_CONVERGE, convergeReply);

    publicChoices.sort(Comparator.comparing(choice ->
        StoryRepository.sourceDigest(actualHash + "|" + choice.optString("id", ""))));

    JSONArray choices = new JSONArray();
    for (JSONObject choice : publicChoices) choices.put(choice);

    JSONObject pack = new JSONObject()
        .put("contextHash", actualHash)
        .put("choices", choices)
        .put("outcomes", outcomes);
    story.put("decisionPackage", pack);
    story.put("decisionStatus", DECISION_READY);
    state.put(ROOT_KEY, story);
  }

  DecisionResolution resolveDecision(
      JSONObject state, String rawChoiceId, CharacterEncounterCore characterCore) throws Exception {
    normalizeState(state);
    if (!decisionReady(state)) throw new IllegalStateException("Story decision is not ready.");

    JSONObject story = state.getJSONObject(ROOT_KEY);
    JSONObject pack = story.getJSONObject("decisionPackage");
    String expectedHash = pack.optString("contextHash", "").trim();
    if (!expectedHash.equals(decisionContextHash(state))) {
      clearDecision(story);
      throw new IllegalStateException("Story decision context changed; prefetch is stale.");
    }

    String choiceId = rawChoiceId == null ? "" : rawChoiceId.trim();
    JSONObject publicChoice = findChoice(pack.optJSONArray("choices"), choiceId);
    JSONObject outcome = pack.optJSONObject("outcomes") == null
        ? null : pack.optJSONObject("outcomes").optJSONObject(choiceId);
    if (publicChoice == null || outcome == null) throw new IllegalArgumentException("Unknown story decision choice.");

    String visibleChoice = publicChoice.optString("text", "").trim();
    String type = outcome.optString("type", "").trim();
    String preparedReply = outcome.optString("reply", "").trim();

    if (OUTCOME_TRAP.equals(type)) {
      String chapterId = story.optString("currentChapter", "").trim();
      int index = Math.max(0, story.optInt("currentSegmentIndex", 0));
      StoryRepository.Segment current = repository == null ? null : repository.segment(chapterId, index);
      if (current == null) throw new IllegalStateException("Missing trap loop anchor segment.");
      JSONObject history = story.optJSONObject("loopHistory");
      if (history == null) history = new JSONObject();
      int count = Math.max(0, history.optInt(story.optString("decisionId", current.id), 0)) + 1;
      history.put(story.optString("decisionId", current.id), count);
      story.put("loopHistory", history);
      state.put(ROOT_KEY, story);
      String reply = preparedReply + "\n\n" + current.text;
      return new DecisionResolution(visibleChoice, reply, OUTCOME_TRAP, null, true);
    }

    if (!OUTCOME_CANON.equals(type) && !OUTCOME_CONVERGE.equals(type)) {
      throw new IllegalArgumentException("Unsupported story decision outcome.");
    }

    clearDecision(story);
    state.put(ROOT_KEY, story);
    AuthoredTurn authored = advanceAndRender(state, ADVANCE_ACTION_VI, characterCore);
    if (authored == null) throw new IllegalStateException("Canonical authored beat is unavailable.");
    String reply = OUTCOME_CONVERGE.equals(type)
        ? preparedReply + "\n\n" + authored.reply
        : authored.reply;
    return new DecisionResolution(visibleChoice, reply, type, authored, false);
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
      if (story.optBoolean("awaitingDecision", false)) {
        output.append("STORY DECISION: a hidden decision package is being/has been prefetched. ")
            .append("Explorer AI must not resolve it or infer which visible choice is canon.\n");
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
      result.put("storyMode", story.optBoolean("awaitingDecision", false)
          ? StoryRepository.MODE_DECISION
          : "");
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

  private void armDecisionIfNeeded(
      JSONObject story, StoryRepository.Chapter chapter, StoryRepository.Segment segment) throws Exception {
    boolean decision = StoryRepository.MODE_DECISION.equals(segment.mode)
        && segment.decisionContract != null
        && segment.decisionContract.length() > 0
        && !"cutaway".equals(chapter.visibility);
    if (!decision) {
      clearDecision(story);
      return;
    }
    story.put("awaitingDecision", true);
    story.put("decisionStatus", DECISION_PREFETCH_REQUIRED);
    story.put("decisionId", segment.id);
    story.put("decisionContract", copyObject(segment.decisionContract));
    story.put("decisionPackage", new JSONObject());
  }

  private void clearDecision(JSONObject story) throws Exception {
    story.put("awaitingDecision", false);
    story.put("decisionStatus", "");
    story.put("decisionId", "");
    story.put("decisionContract", new JSONObject());
    story.put("decisionPackage", new JSONObject());
  }

  private String decisionContextHash(JSONObject state) {
    try {
      JSONObject story = state.optJSONObject(ROOT_KEY);
      if (story == null) return "";
      StringBuilder material = new StringBuilder();
      material.append(story.optString("sourceRevision", "")).append('|')
          .append(story.optString("currentChapter", "")).append('|')
          .append(story.optString("currentScene", "")).append('|')
          .append(story.optInt("currentSegmentIndex", 0)).append('|')
          .append(story.optString("thread", "")).append('|')
          .append(story.optString("visibility", "")).append('|')
          .append(story.optJSONObject(FLAGS_KEY)).append('|')
          .append(story.optJSONObject(CHARACTERS_KEY)).append('|')
          .append(state.optJSONArray("party")).append('|')
          .append(state.optJSONArray("inventory")).append('|')
          .append(state.optString(LevelCore.LEVEL_KEY, String.valueOf(state.optInt("currentLevel", 0)))).append('|')
          .append(state.optJSONObject(SurvivalCore.ROOT_KEY)).append('|')
          .append(state.optJSONObject("flags"));
      return StoryRepository.sourceDigest(material.toString());
    } catch (Exception ignored) {
      return "";
    }
  }

  private static void addOutcome(
      List<JSONObject> publicChoices,
      JSONObject outcomes,
      String id,
      String text,
      String type,
      String reply) throws Exception {
    publicChoices.add(new JSONObject().put("id", id).put("text", text));
    outcomes.put(id, new JSONObject().put("type", type).put("reply", reply == null ? "" : reply));
  }

  private static JSONObject findChoice(JSONArray choices, String id) {
    if (choices == null || id == null) return null;
    for (int i = 0; i < choices.length(); i++) {
      JSONObject choice = choices.optJSONObject(i);
      if (choice != null && id.equals(choice.optString("id", ""))) return choice;
    }
    return null;
  }

  private static boolean validPublicChoice(String text) {
    if (text == null) return false;
    String value = text.trim();
    if (value.length() < 4 || value.length() > 180) return false;
    String lower = value.toLowerCase(Locale.ROOT);
    return !lower.equals(ADVANCE_ACTION_VI)
        && !lower.equals("continue story")
        && !lower.contains("canon")
        && !lower.contains("trap_loop")
        && !lower.contains("bẫy")
        && !lower.matches("^[abc][\\.\\):\\-].*");
  }

  private static boolean validPreparedReply(String text) {
    if (text == null) return false;
    String value = text.trim();
    if (value.isEmpty() || value.length() > 1400) return false;
    String lower = value.toLowerCase(Locale.ROOT);
    return !lower.contains("bạn đã chọn sai")
        && !lower.contains("trap_loop")
        && !lower.contains("reset checkpoint");
  }

  private static boolean sameChoice(String a, String b) {
    String aa = a == null ? "" : a.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    String bb = b == null ? "" : b.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    return aa.equals(bb);
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
      clearDecision(story);
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

  private static JSONArray copyArray(JSONArray source) {
    try {
      return source == null ? new JSONArray() : new JSONArray(source.toString());
    } catch (Exception ignored) {
      return new JSONArray();
    }
  }

  private static JSONObject copyObject(JSONObject source) {
    try {
      return source == null ? new JSONObject() : new JSONObject(source.toString());
    } catch (Exception ignored) {
      return new JSONObject();
    }
  }

  private static String clip(String text, int max) {
    String value = text == null ? "" : text.trim();
    return value.length() <= max ? value : value.substring(0, max);
  }

  private static String clipTail(String text, int max) {
    String value = text == null ? "" : text.trim();
    return value.length() <= max ? value : value.substring(value.length() - max);
  }

  private static void appendArray(StringBuilder output, String label, JSONArray values) {
    if (values == null || values.length() == 0) return;
    output.append(label).append(": ");
    boolean wrote = false;
    for (int i = 0; i < values.length(); i++) {
      String value = values.optString(i, "").trim();
      if (value.isEmpty()) continue;
      if (wrote) output.append(" | ");
      output.append(value);
      wrote = true;
    }
    if (wrote) output.append("\n");
  }
}

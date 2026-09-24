package com.rabpit.backroom.core;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.Locale;
import java.util.regex.Pattern;

/** Owns authored-story state and locally prepared hidden story decisions. */
final class StoryCore {
  static final String ROOT_KEY = "story";
  static final int SCHEMA_VERSION = 6;

  static final String STATUS_UNSEEN = "UNSEEN_IN_STORY";
  static final String STATUS_PARALLEL = "PARALLEL_STORY";
  static final String STATUS_REUNITED = "REUNITED";
  static final String STATUS_ACCOMPANYING = "ACCOMPANYING";
  static final String STATUS_PARTY_MEMBER = "PARTY_MEMBER";

  static final String PRESENCE_UNKNOWN = "UNKNOWN";
  static final String PRESENCE_PRESENT = "PRESENT";
  static final String PRESENCE_MISSING = "MISSING";
  static final String PRESENCE_DECEASED = "DECEASED";

  static final String EVENT_CHARACTER_PARALLEL = "CHARACTER_PARALLEL_STORY";
  static final String EVENT_CHARACTER_REUNION = "CHARACTER_REUNION";
  static final String EVENT_CHARACTER_ACCOMPANY = "CHARACTER_ACCOMPANY";
  static final String EVENT_CHARACTER_JOIN_PARTY = "CHARACTER_JOIN_PARTY";
  static final String EVENT_CHARACTER_PRESENT = "CHARACTER_PRESENT";
  static final String EVENT_CHARACTER_MISSING = "CHARACTER_MISSING";
  static final String EVENT_CHARACTER_PRESENCE = "CHARACTER_PRESENCE";
  static final String EVENT_STORY_FLAG_SET = "STORY_FLAG_SET";
  static final String EVENT_STORY_FACT_SET = "STORY_FACT_SET";
  static final String EVENT_ARC_BOUNDARY_REACHED = "ARC_BOUNDARY_REACHED";
  static final String EVENT_LEVEL0_ARC_BOUNDARY_REACHED = "LEVEL0_ARC_BOUNDARY_REACHED";

  static final String ADVANCE_ACTION_VI = "tiếp tục cốt truyện";
  static final String DECISION_PROVIDER_REQUIRED = "PROVIDER_REQUIRED";
  // Compatibility alias for older tests/callers. New runtime code treats generation as an on-demand turn request.
  static final String DECISION_PREFETCH_REQUIRED = DECISION_PROVIDER_REQUIRED;
  static final String DECISION_READY = "READY";
  static final String OUTCOME_CANON = "CANON_PROGRESS";
  static final String OUTCOME_RETURN = "RETURN_TO_LEVEL_START";
  static final String OUTCOME_STAY = "STAY_IN_PLACE";
  static final String OUTCOME_TRAP = OUTCOME_RETURN;
  static final String OUTCOME_CONVERGE = OUTCOME_STAY;

  static final String RETURN_CAUSE_DEATH = "DEATH";
  static final String RETURN_CAUSE_STORY = "STORY_WRONG";
  static final String RETURN_PROGRESS = "PROGRESS";
  static final String RETURN_TO_START = "RETURN_TO_START";
  static final String RETURN_STAY = "STAY";
  static final String RETURN_PROVIDER_REQUIRED = "PROVIDER_REQUIRED";
  static final String RETURN_READY = "READY";
  private static final int RETURN_REQUIRED_PROGRESS = 3;

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

  static final class ReturnJourneyResolution {
    final String visibleChoice;
    final String reply;
    final String outcome;
    final boolean arrived;
    final String cause;

    ReturnJourneyResolution(
        String visibleChoice, String reply, String outcome, boolean arrived, String cause) {
      this.visibleChoice = visibleChoice;
      this.reply = reply;
      this.outcome = outcome;
      this.arrived = arrived;
      this.cause = cause;
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
    if (story == null) story = new JSONObject();

    String currentLevelKey = state.optString(
        LevelCore.LEVEL_KEY, String.valueOf(state.optInt("currentLevel", 0))).trim();
    boolean repositoryAvailableForLevel = repository != null
        && repository.hasStoryForLevel(currentLevelKey)
        && repository.bindLevel(currentLevelKey)
        && repository.available();

    story.put("schemaVersion", SCHEMA_VERSION);
    if (!story.has("active")) story.put("active", false);
    if (!story.has("storyId")) story.put("storyId", "");
    if (!story.has("levelKey")) story.put("levelKey", "");
    if (!story.has("sourceRevision")) story.put("sourceRevision", "");
    if (!story.has("currentChapter")) story.put("currentChapter", "");
    if (!story.has("currentScene")) story.put("currentScene", "");
    if (!story.has("currentSegmentId")) story.put("currentSegmentId", "");
    if (!story.has("currentSegmentIndex")) story.put("currentSegmentIndex", 0);
    if (!story.has("currentSegmentCount")) story.put("currentSegmentCount", 0);
    if (!story.has("segmentDelivered")) story.put("segmentDelivered", false);
    if (!story.has("chapterEntered")) story.put("chapterEntered", false);
    if (!story.has("arcComplete")) story.put("arcComplete", false);
    if (!story.has("migrationBlocked")) story.put("migrationBlocked", false);
    if (!story.has("migrationNotice")) story.put("migrationNotice", "");
    if (!story.has("thread")) story.put("thread", "cao_minh");
    if (!story.has("visibility")) story.put("visibility", "player");
    if (!story.has("eventSequence")) story.put("eventSequence", 0);
    if (!story.has("awaitingDecision")) story.put("awaitingDecision", false);
    if (!story.has("awaitingEntityAttack")) story.put("awaitingEntityAttack", false);
    if (!story.has("pendingStoryAdvance")) story.put("pendingStoryAdvance", false);
    if (story.optJSONObject("returnJourney") == null) {
      story.put("returnJourney", emptyReturnJourney());
    }
    syncReturnJourneyProjection(story);
    if (!story.has("decisionStatus")) story.put("decisionStatus", "");
    if (!story.has("decisionId")) story.put("decisionId", "");
    if (story.optJSONObject("decisionContract") == null) story.put("decisionContract", new JSONObject());
    if (story.optJSONObject("decisionPackage") == null) story.put("decisionPackage", new JSONObject());
    if (story.optJSONObject("entityGate") == null) story.put("entityGate", new JSONObject());
    if (story.optJSONObject("loopHistory") == null) story.put("loopHistory", new JSONObject());
    if (story.optJSONObject(FLAGS_KEY) == null) story.put(FLAGS_KEY, new JSONObject());
    if (story.optJSONObject("crossArcFacts") == null) story.put("crossArcFacts", new JSONObject());

    // Old v1 interaction fields are intentionally neutralized.
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

    if (!repositoryAvailableForLevel) {
      // A death-return journey is gameplay state and must survive on levels that have no authored Story arc.
      story.put("active", false);
      story.put("pendingStoryAdvance", false);
      story.put("awaitingEntityAttack", false);
      story.put("entityGate", new JSONObject());
      clearDecision(story);
      syncReturnJourneyProjection(story);
      state.put(ROOT_KEY, story);
      return;
    }

    String savedStoryId = story.optString("storyId", "").trim();
    String savedLevelKey = story.optString("levelKey", "").trim();
    boolean revisionChanged = !repository.sourceRevision().equals(story.optString("sourceRevision", ""));
    boolean legacySameArc = savedStoryId.isEmpty()
        && (savedLevelKey.isEmpty() || currentLevelKey.equals(savedLevelKey))
        && repository.chapter(story.optString("currentChapter", "")) != null;
    boolean arcChanged = !legacySameArc
        && (!repository.storyId().equals(savedStoryId) || !repository.levelKey().equals(savedLevelKey));

    if (arcChanged) {
      resetArcTransient(story);
      story.put("storyId", repository.storyId());
      story.put("levelKey", repository.levelKey());
      story.put("sourceRevision", repository.sourceRevision());
      story.put("active", true);
      story.put("currentChapter", repository.startChapter());
    } else {
      story.put("storyId", repository.storyId());
      story.put("levelKey", repository.levelKey());
      migrateCompatibleRevision(story);
    }

    if (!story.optBoolean("migrationBlocked", false) && !story.optBoolean("arcComplete", false)) {
      bindRepositoryStory(story);
      if (revisionChanged && story.optBoolean("segmentDelivered", false)) {
        StoryRepository.Segment segment = repository.segment(
            story.optString("currentChapter", ""), story.optInt("currentSegmentIndex", 0));
        StoryRepository.Chapter chapter = repository.chapter(story.optString("currentChapter", ""));
        if (segment != null && chapter != null && !story.optBoolean("pendingStoryAdvance", false)) {
          armTurnGate(story, chapter, segment);
        }
      }
    }
    syncReturnJourneyProjection(story);
    state.put(ROOT_KEY, story);
  }

  private void migrateCompatibleRevision(JSONObject story) throws Exception {
    if (repository == null || !repository.available()) return;
    String previousRevision = story.optString("sourceRevision", "").trim();
    String chapterId = story.optString("currentChapter", "").trim();
    StoryRepository.Chapter chapter = repository.chapter(chapterId);
    if (chapterId.isEmpty()) {
      story.put("sourceRevision", repository.sourceRevision());
      return;
    }
    if (chapter == null) {
      clearArcCursor(story);
      story.put("active", false);
      story.put("migrationBlocked", true);
      story.put("migrationNotice", "Authored chapter no longer exists; story progression is blocked for safe migration.");
      story.put("sourceRevision", repository.sourceRevision());
      return;
    }

    int resolvedIndex = -1;
    String stableId = story.optString("currentSegmentId",
        story.optString("currentScene", "")).trim();
    if (!stableId.isEmpty()) resolvedIndex = repository.segmentIndex(chapterId, stableId);

    if (resolvedIndex < 0 && previousRevision.equals(repository.sourceRevision())) {
      int legacyIndex = Math.max(0, story.optInt("currentSegmentIndex", 0));
      if (legacyIndex < repository.segmentCount(chapterId)) resolvedIndex = legacyIndex;
    }

    if (resolvedIndex < 0 && !story.optBoolean("segmentDelivered", false)
        && Math.max(0, story.optInt("currentSegmentIndex", 0)) == 0) {
      resolvedIndex = 0;
    }

    if (resolvedIndex < 0) {
      clearDecision(story);
      story.put("awaitingEntityAttack", false);
      story.put("entityGate", new JSONObject());
      story.put("loopHistory", new JSONObject());
      story.put("pendingStoryAdvance", false);
      story.put("returnJourneyPending", false);
      story.put("returnAnchorLocation", "");
      story.put("currentSegmentIndex", 0);
      story.put("currentSegmentId", "");
      story.put("currentScene", "");
      story.put("segmentDelivered", false);
      story.put("chapterEntered", false);
      story.put("migrationNotice", "Authored source changed; rewound to the start of the current chapter.");
    } else {
      StoryRepository.Segment segment = repository.segment(chapterId, resolvedIndex);
      story.put("currentSegmentIndex", resolvedIndex);
      story.put("currentSegmentId", segment == null ? "" : segment.id);
      if (segment != null && story.optBoolean("segmentDelivered", false)) {
        story.put("currentScene", segment.id);
      }
    }
    story.put("migrationBlocked", false);
    story.put("sourceRevision", repository.sourceRevision());
  }

  private void resetArcTransient(JSONObject story) throws Exception {
    clearArcCursor(story);
    story.put(FLAGS_KEY, new JSONObject());
    story.put("loopHistory", new JSONObject());
    story.put("arcComplete", false);
    story.put("migrationBlocked", false);
    story.put("migrationNotice", "");
  }

  private void clearArcCursor(JSONObject story) throws Exception {
    story.put("currentChapter", "");
    story.put("currentScene", "");
    story.put("currentSegmentId", "");
    story.put("currentSegmentIndex", 0);
    story.put("currentSegmentCount", 0);
    story.put("segmentDelivered", false);
    story.put("chapterEntered", false);
    story.put("pendingStoryAdvance", false);
    story.put("awaitingEntityAttack", false);
    story.put("entityGate", new JSONObject());
    clearDecision(story);
  }

  boolean ownsLevelProgression(JSONObject state) {
    try {
      normalizeState(state);
      JSONObject story = state.optJSONObject(ROOT_KEY);
      String currentLevelKey = state.optString(LevelCore.LEVEL_KEY,
          String.valueOf(state.optInt("currentLevel", 0))).trim();
      return story != null
          && story.optBoolean("active", false)
          && currentLevelKey.equals(story.optString("levelKey", "").trim());
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
      if (returnJourneyActive(state)) return true;
      return story.optBoolean("active", false)
          && !story.optBoolean("arcComplete", false)
          && ("cutaway".equals(story.optString("visibility", ""))
              || story.optBoolean("awaitingDecision", false)
              || story.optBoolean("awaitingEntityAttack", false)
              || story.optBoolean("pendingStoryAdvance", false));
    } catch (Exception ignored) {
      return false;
    }
  }

  boolean awaitingDecision(JSONObject state) {
    try {
      normalizeState(state);
      JSONObject story = state.getJSONObject(ROOT_KEY);
      return !returnJourneyActive(state)
          && story.optBoolean("active", false)
          && !story.optBoolean("arcComplete", false)
          && story.optBoolean("awaitingDecision", false);
    } catch (Exception ignored) {
      return false;
    }
  }

  boolean awaitingEntityAttack(JSONObject state) {
    try {
      normalizeState(state);
      JSONObject story = state.getJSONObject(ROOT_KEY);
      return !returnJourneyActive(state)
          && story.optBoolean("active", false)
          && !story.optBoolean("arcComplete", false)
          && story.optBoolean("awaitingEntityAttack", false);
    } catch (Exception ignored) {
      return false;
    }
  }

  JSONObject entityAttackChoice(JSONObject state) {
    JSONObject choice = new JSONObject();
    try {
      normalizeState(state);
      if (!awaitingEntityAttack(state)) return choice;
      JSONObject gate = state.getJSONObject(ROOT_KEY).optJSONObject("entityGate");
      if (gate == null) return choice;
      choice.put("id", "story_attack");
      choice.put("text", gate.optString("attackText", "Tấn công"));
      choice.put("entityKey", gate.optString("entityKey", ""));
    } catch (Exception ignored) {}
    return choice;
  }

  boolean hasPendingStoryAdvance(JSONObject state) {
    try {
      normalizeState(state);
      JSONObject story = state.getJSONObject(ROOT_KEY);
      return story.optBoolean("pendingStoryAdvance", false);
    } catch (Exception ignored) {
      return false;
    }
  }

  String consumeEntityAttack(JSONObject state) throws Exception {
    normalizeState(state);
    JSONObject story = state.getJSONObject(ROOT_KEY);
    if (!story.optBoolean("awaitingEntityAttack", false)) {
      throw new IllegalStateException("No authored Entity gate is awaiting attack.");
    }
    JSONObject gate = story.optJSONObject("entityGate");
    String entityKey = gate == null ? "" : gate.optString("entityKey", "").trim();
    if (entityKey.isEmpty()) throw new IllegalStateException("Authored Entity gate is missing entityKey.");
    story.put("awaitingEntityAttack", false);
    story.put("pendingStoryAdvance", true);
    story.put("entityGate", new JSONObject());
    state.put(ROOT_KEY, story);
    return entityKey;
  }

  boolean decisionNeedsPrefetch(JSONObject state) {
    return decisionNeedsProvider(state);
  }

  boolean decisionNeedsProvider(JSONObject state) {
    try {
      normalizeState(state);
      JSONObject story = state.getJSONObject(ROOT_KEY);
      return story.optBoolean("awaitingDecision", false)
          && !returnJourneyActive(state)
          && DECISION_PROVIDER_REQUIRED.equals(story.optString("decisionStatus", ""));
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
          && !returnJourneyActive(state)
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
    if (returnJourneyActive(state)
        || story.optBoolean("awaitingDecision", false)
        || story.optBoolean("awaitingEntityAttack", false)
        || story.optBoolean("pendingStoryAdvance", false)) {
      throw new IllegalStateException("Current story turn must be resolved before authored progression.");
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
    story.put("currentSegmentId", segment.id);
    story.put("segmentDelivered", true);
    armTurnGate(story, chapter, segment);
    state.put(ROOT_KEY, story);

    applyEvents(state, chapter.eventsAfterSegment.optJSONArray(segment.id), characterCore);
    story = state.getJSONObject(ROOT_KEY);
    syncChapterProjection(story, chapter, index);
    story.put("currentScene", segment.id);
    story.put("currentSegmentId", segment.id);
    story.put("segmentDelivered", true);

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
    return decisionGenerationRequest(state, recentStory);
  }

  JSONObject decisionGenerationRequest(JSONObject state, String recentStory) throws Exception {
    normalizeState(state);
    JSONObject output = new JSONObject().put("needed", false);
    if (!decisionNeedsProvider(state) || repository == null) return output;

    JSONObject story = state.getJSONObject(ROOT_KEY);
    String chapterId = story.optString("currentChapter", "").trim();
    int index = Math.max(0, story.optInt("currentSegmentIndex", 0));
    StoryRepository.Segment current = repository.segment(chapterId, index);
    StoryRepository.Segment next = repository.nextSegment(chapterId, index);
    JSONObject contract = story.optJSONObject("decisionContract");
    if (current == null || next == null || contract == null || contract.length() == 0) return output;

    String contextHash = decisionContextHash(state);
    String decisionId = story.optString("decisionId", current.id).trim();
    String guard = contract.optString("decisionGuard", "").trim();

    StringBuilder prompt = new StringBuilder();
    prompt.append("BACKROOMsV2 STORY CHOICE TURN\n\n");
    prompt.append("Core has already fixed the three hidden outcomes. You ONLY write player-facing Vietnamese prose. ");
    prompt.append("Never decide state, routes, canon, rewards, encounters, items or progression.\n\n");
    prompt.append("CURRENT AUTHORED BEAT:\n").append(clipTail(current.text, 1700)).append("\n\n");
    prompt.append("NEXT AUTHORED BEAT (private fixed outcome for canon.text):\n")
        .append(clip(next.text, 2100)).append("\n\n");
    if (recentStory != null && !recentStory.trim().isEmpty()) {
      prompt.append("RECENT READER-VISIBLE CONTEXT:\n").append(clip(recentStory, 2800)).append("\n\n");
    }
    prompt.append("DECISION GUARD:\n").append(guard).append("\n\n");
    prompt.append("Write exactly three choices of similar plausibility, appeal and length:\n");
    prompt.append("- canon.text: immediate action naturally leading to NEXT AUTHORED BEAT, without spoiling it.\n");
    prompt.append("- return.text + return.reply: reasonable action whose immediate local consequence folds space back toward the level entrance.\n");
    prompt.append("- stay.text + stay.reply: reasonable action whose immediate local consequence leaves Cao Minh effectively at the same place.\n");
    prompt.append("The two false choices must NOT replay completed canon, grant anything, create a new Entity, route, lore fact or authored consequence. ");
    prompt.append("Do not use the player-facing words 'chọn sai', 'reset', 'checkpoint', 'canon', 'bẫy', or obvious result labels. ");
    prompt.append("Do not use A/B/C, bullets, numbering, 'Tiếp tục', joke options, or reveal which choice is correct.\n");
    prompt.append("Each reply is local prose only, max 900 characters.\n\n");
    prompt.append("Return JSON only:\n");
    prompt.append("{\"canon\":{\"text\":\"...\"},")
        .append("\"return\":{\"text\":\"...\",\"reply\":\"...\"},")
        .append("\"stay\":{\"text\":\"...\",\"reply\":\"...\"}}");

    output.put("needed", true)
        .put("decisionId", decisionId)
        .put("contextHash", contextHash)
        .put("prompt", prompt.toString());
    return output;
  }

  void installDecisionPackage(JSONObject state, String expectedContextHash, JSONObject generated)
      throws Exception {
    normalizeState(state);
    if (!decisionNeedsProvider(state)) {
      throw new IllegalStateException("No current Story choice turn requires generation.");
    }

    JSONObject story = state.getJSONObject(ROOT_KEY);
    String actualHash = decisionContextHash(state);
    if (expectedContextHash == null || !actualHash.equals(expectedContextHash.trim())) {
      throw new IllegalStateException("Story choice context changed before provider response completed.");
    }

    JSONObject canon = generated == null ? null : generated.optJSONObject("canon");
    JSONObject returned = generated == null ? null : generated.optJSONObject("return");
    JSONObject stay = generated == null ? null : generated.optJSONObject("stay");
    String canonText = canon == null ? "" : canon.optString("text", "").trim();
    String returnText = returned == null ? "" : returned.optString("text", "").trim();
    String returnReply = returned == null ? "" : returned.optString("reply", "").trim();
    String stayText = stay == null ? "" : stay.optString("text", "").trim();
    String stayReply = stay == null ? "" : stay.optString("reply", "").trim();

    if (!validPublicChoice(canonText) || !validPublicChoice(returnText) || !validPublicChoice(stayText)
        || !validPreparedReply(returnReply) || !validPreparedReply(stayReply)) {
      throw new IllegalArgumentException("Generated Story choice package is incomplete or unsafe.");
    }
    if (sameChoice(canonText, returnText) || sameChoice(canonText, stayText)
        || sameChoice(returnText, stayText)) {
      throw new IllegalArgumentException("Story choices must be distinct.");
    }

    String decisionId = story.optString("decisionId", "").trim();
    if (decisionId.isEmpty()) throw new IllegalStateException("Missing Story decision id.");

    JSONObject outcomes = new JSONObject();
    List<JSONObject> publicChoices = new ArrayList<>();
    addOutcome(publicChoices, outcomes, opaqueChoiceId(), canonText, OUTCOME_CANON, "");
    addOutcome(publicChoices, outcomes, opaqueChoiceId(), returnText, OUTCOME_RETURN, returnReply);
    addOutcome(publicChoices, outcomes, opaqueChoiceId(), stayText, OUTCOME_STAY, stayReply);
    Collections.shuffle(publicChoices);

    JSONArray choices = new JSONArray();
    for (JSONObject choice : publicChoices) choices.put(choice);

    story.put("decisionPackage", new JSONObject()
        .put("contextHash", actualHash)
        .put("choices", choices)
        .put("outcomes", outcomes));
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
      rearmCurrentDecision(story);
      throw new IllegalStateException("Story choice context changed; provider response is stale.");
    }

    String choiceId = rawChoiceId == null ? "" : rawChoiceId.trim();
    JSONObject publicChoice = findChoice(pack.optJSONArray("choices"), choiceId);
    JSONObject outcome = pack.optJSONObject("outcomes") == null
        ? null : pack.optJSONObject("outcomes").optJSONObject(choiceId);
    if (publicChoice == null || outcome == null) {
      throw new IllegalArgumentException("Unknown story decision choice.");
    }

    String visibleChoice = publicChoice.optString("text", "").trim();
    String type = outcome.optString("type", "").trim();
    String preparedReply = outcome.optString("reply", "").trim();

    if (OUTCOME_RETURN.equals(type) || OUTCOME_STAY.equals(type)) {
      JSONObject history = story.optJSONObject("loopHistory");
      if (history == null) history = new JSONObject();
      String decisionId = story.optString("decisionId", "").trim();
      history.put(decisionId, Math.max(0, history.optInt(decisionId, 0)) + 1);
      story.put("loopHistory", history);

      if (OUTCOME_RETURN.equals(type)) {
        beginReturnJourney(state, RETURN_CAUSE_STORY, state.optString("location", ""),
            state.optString(LevelCore.LEVEL_KEY, String.valueOf(state.optInt("currentLevel", 0))));
      } else {
        rearmCurrentDecision(story);
        state.put(ROOT_KEY, story);
      }
      return new DecisionResolution(visibleChoice, preparedReply, type, null, true);
    }

    if (!OUTCOME_CANON.equals(type)) {
      throw new IllegalArgumentException("Unsupported Story decision outcome.");
    }

    clearDecision(story);
    story.put("pendingStoryAdvance", true);
    state.put(ROOT_KEY, story);
    return new DecisionResolution(visibleChoice, "", type, null, false);
  }

  void beginDeathReturnJourney(
      JSONObject state, String targetLocation, String targetLevelKey) throws Exception {
    beginReturnJourney(state, RETURN_CAUSE_DEATH, targetLocation, targetLevelKey);
  }

  private void beginReturnJourney(
      JSONObject state, String cause, String targetLocation, String targetLevelKey) throws Exception {
    JSONObject story = state.optJSONObject(ROOT_KEY);
    if (story == null) {
      story = new JSONObject();
      state.put(ROOT_KEY, story);
      normalizeState(state);
      story = state.getJSONObject(ROOT_KEY);
    }

    String levelKey = targetLevelKey == null ? "" : targetLevelKey.trim();
    if (levelKey.isEmpty()) {
      levelKey = state.optString(LevelCore.LEVEL_KEY, String.valueOf(state.optInt("currentLevel", 0)));
    }
    String destination = targetLocation == null ? "" : targetLocation.trim();
    if (destination.isEmpty()) destination = state.optString("location", "").trim();
    if (destination.isEmpty()) destination = LevelCore.defaultLocation(levelKey);

    JSONObject pausedStory = new JSONObject()
        .put("storyId", story.optString("storyId", ""))
        .put("sourceRevision", story.optString("sourceRevision", ""))
        .put("currentChapter", story.optString("currentChapter", ""))
        .put("currentScene", story.optString("currentScene", ""))
        .put("currentSegmentId", story.optString("currentSegmentId", ""))
        .put("currentSegmentIndex", story.optInt("currentSegmentIndex", 0))
        .put("eventSequence", story.optInt("eventSequence", 0))
        .put("awaitingDecision", story.optBoolean("awaitingDecision", false))
        .put("awaitingEntityAttack", story.optBoolean("awaitingEntityAttack", false))
        .put("pendingStoryAdvance", story.optBoolean("pendingStoryAdvance", false));

    if (RETURN_CAUSE_STORY.equals(cause)) rearmCurrentDecision(story);

    LevelCore.returnToCurrentLevelStart(state);
    String startLocation = state.optString("location", LevelCore.defaultLocation(levelKey));
    JSONObject journey = new JSONObject()
        .put("active", true)
        .put("journeyId", "return_" + UUID.randomUUID().toString().replace("-", ""))
        .put("cause", cause)
        .put("levelKey", levelKey)
        .put("startLocation", startLocation)
        .put("currentPosition", startLocation)
        .put("targetLocation", destination)
        .put("progress", 0)
        .put("requiredProgress", RETURN_REQUIRED_PROGRESS)
        .put("turnIndex", 0)
        .put("turnStatus", RETURN_PROVIDER_REQUIRED)
        .put("turnPackage", new JSONObject())
        .put("pausedStory", pausedStory);
    story.put("returnJourney", journey);
    syncReturnJourneyProjection(story);
    state.put(ROOT_KEY, story);
  }

  boolean returnJourneyActive(JSONObject state) {
    try {
      JSONObject story = state == null ? null : state.optJSONObject(ROOT_KEY);
      JSONObject journey = story == null ? null : story.optJSONObject("returnJourney");
      return journey != null && journey.optBoolean("active", false);
    } catch (Exception ignored) {
      return false;
    }
  }

  boolean returnJourneyNeedsProvider(JSONObject state) {
    try {
      normalizeState(state);
      JSONObject journey = state.getJSONObject(ROOT_KEY).getJSONObject("returnJourney");
      return journey.optBoolean("active", false)
          && RETURN_PROVIDER_REQUIRED.equals(journey.optString("turnStatus", ""));
    } catch (Exception ignored) {
      return false;
    }
  }

  boolean returnJourneyReady(JSONObject state) {
    try {
      normalizeState(state);
      JSONObject journey = state.getJSONObject(ROOT_KEY).getJSONObject("returnJourney");
      JSONObject pack = journey.optJSONObject("turnPackage");
      return journey.optBoolean("active", false)
          && RETURN_READY.equals(journey.optString("turnStatus", ""))
          && pack != null && pack.optJSONArray("choices") != null
          && pack.optJSONArray("choices").length() == 3;
    } catch (Exception ignored) {
      return false;
    }
  }

  JSONArray returnJourneyChoices(JSONObject state) {
    try {
      if (!returnJourneyReady(state)) return new JSONArray();
      return copyArray(state.getJSONObject(ROOT_KEY).getJSONObject("returnJourney")
          .getJSONObject("turnPackage").getJSONArray("choices"));
    } catch (Exception ignored) {
      return new JSONArray();
    }
  }

  JSONObject returnJourneyTurnRequest(JSONObject state, String recentStory) throws Exception {
    normalizeState(state);
    JSONObject output = new JSONObject().put("needed", false);
    if (!returnJourneyNeedsProvider(state)) return output;

    JSONObject story = state.getJSONObject(ROOT_KEY);
    JSONObject journey = story.getJSONObject("returnJourney");
    String contextHash = returnJourneyContextHash(state);
    String currentAuthored = "";
    String nextAuthored = "";
    if (repository != null && repository.available()) {
      StoryRepository.Segment current = repository.segment(
          story.optString("currentChapter", ""), story.optInt("currentSegmentIndex", 0));
      StoryRepository.Segment next = repository.nextSegment(
          story.optString("currentChapter", ""), story.optInt("currentSegmentIndex", 0));
      if (current != null) currentAuthored = current.text;
      if (next != null) nextAuthored = next.text;
    }

    StringBuilder prompt = new StringBuilder();
    prompt.append("BACKROOMsV2 RETURN JOURNEY TURN\n\n");
    prompt.append("Core owns every hidden outcome and all state. You ONLY narrate this current turn and phrase three choices. ");
    prompt.append("Do not decide whether Cao Minh advances, returns to the entrance, or stays.\n");
    prompt.append("CAUSE (private, never label it to the player): ").append(journey.optString("cause", "")).append("\n");
    prompt.append("CURRENT LEVEL KEY: ").append(journey.optString("levelKey", "")).append("\n");
    prompt.append("LEVEL START: ").append(journey.optString("startLocation", "")).append("\n");
    prompt.append("CURRENT POSITION: ").append(journey.optString("currentPosition", "")).append("\n");
    prompt.append("SAVED TARGET: ").append(journey.optString("targetLocation", "")).append("\n");
    prompt.append("INTERNAL PROGRESS: ").append(journey.optInt("progress", 0)).append("/")
        .append(journey.optInt("requiredProgress", RETURN_REQUIRED_PROGRESS)).append("\n\n");
    if (!currentAuthored.isEmpty()) {
      prompt.append("PAUSED AUTHORED BEAT — context only, DO NOT replay or advance it:\n")
          .append(clipTail(currentAuthored, 1200)).append("\n\n");
    }
    if (!nextAuthored.isEmpty()) {
      prompt.append("NEXT AUTHORED BEAT — hard boundary, DO NOT reveal or enter it:\n")
          .append(clip(nextAuthored, 900)).append("\n\n");
    }
    if (recentStory != null && !recentStory.trim().isEmpty()) {
      prompt.append("RECENT VISIBLE LOG — avoid repeating wording or complete choice sets:\n")
          .append(clipTail(recentStory, 2800)).append("\n\n");
    }
    prompt.append("CANON RULES: use only existing location traits, characters, traces, Entities and items supplied by Core context. ");
    prompt.append("Do not invent lore, routes, rewards, items, Entity species, canon consequences or completed events. ");
    prompt.append("A previously encountered trace or already-existing Entity may be noticed again, but do not replay its completed canon event, combat result or reward.\n");
    prompt.append("PLAYER-FACING PROSE must never say 'chọn sai', 'reset', 'checkpoint', hidden outcome labels, progress counters, or that a loop mechanic exists. ");
    prompt.append("Do not quote a long passage already shown. Spatial repetition should feel like getting lost.\n\n");
    prompt.append("Return JSON only with 120-900 character narration and exactly these semantic phrasing slots:\n");
    prompt.append("{\"narration\":\"...\",")
        .append("\"progress\":{\"text\":\"...\",\"reply\":\"...\"},")
        .append("\"return\":{\"text\":\"...\",\"reply\":\"...\"},")
        .append("\"stay\":{\"text\":\"...\",\"reply\":\"...\"}}\n");
    prompt.append("All three choice texts must be natural Vietnamese, similarly plausible/appealing/length, no numbering, no result spoilers. ");
    prompt.append("Each reply is immediate local prose only, max 900 characters.");

    output.put("needed", true)
        .put("journeyId", journey.optString("journeyId", ""))
        .put("turnIndex", journey.optInt("turnIndex", 0))
        .put("contextHash", contextHash)
        .put("prompt", prompt.toString());
    return output;
  }

  String installReturnJourneyTurn(
      JSONObject state, String expectedJourneyId, int expectedTurnIndex,
      String expectedContextHash, JSONObject generated) throws Exception {
    normalizeState(state);
    if (!returnJourneyNeedsProvider(state)) {
      throw new IllegalStateException("No return journey turn requires generation.");
    }
    JSONObject story = state.getJSONObject(ROOT_KEY);
    JSONObject journey = story.getJSONObject("returnJourney");
    if (!journey.optString("journeyId", "").equals(expectedJourneyId)
        || journey.optInt("turnIndex", -1) != expectedTurnIndex) {
      throw new IllegalStateException("Return journey provider response is stale.");
    }
    String actualHash = returnJourneyContextHash(state);
    if (expectedContextHash == null || !actualHash.equals(expectedContextHash.trim())) {
      throw new IllegalStateException("Return journey context changed before provider response completed.");
    }

    String narration = generated == null ? "" : generated.optString("narration", "").trim();
    JSONObject progress = generated == null ? null : generated.optJSONObject("progress");
    JSONObject returned = generated == null ? null : generated.optJSONObject("return");
    JSONObject stay = generated == null ? null : generated.optJSONObject("stay");
    if (!validReturnNarration(narration)) {
      throw new IllegalArgumentException("Return journey narration is invalid.");
    }
    String progressText = progress == null ? "" : progress.optString("text", "").trim();
    String progressReply = progress == null ? "" : progress.optString("reply", "").trim();
    String returnText = returned == null ? "" : returned.optString("text", "").trim();
    String returnReply = returned == null ? "" : returned.optString("reply", "").trim();
    String stayText = stay == null ? "" : stay.optString("text", "").trim();
    String stayReply = stay == null ? "" : stay.optString("reply", "").trim();
    if (!validPublicChoice(progressText) || !validPublicChoice(returnText) || !validPublicChoice(stayText)
        || !validPreparedReply(progressReply) || !validPreparedReply(returnReply)
        || !validPreparedReply(stayReply)
        || sameChoice(progressText, returnText) || sameChoice(progressText, stayText)
        || sameChoice(returnText, stayText)) {
      throw new IllegalArgumentException("Return journey choice package is incomplete or unsafe.");
    }

    JSONObject outcomes = new JSONObject();
    List<JSONObject> publicChoices = new ArrayList<>();
    addOutcome(publicChoices, outcomes, opaqueChoiceId(), progressText, RETURN_PROGRESS, progressReply);
    addOutcome(publicChoices, outcomes, opaqueChoiceId(), returnText, RETURN_TO_START, returnReply);
    addOutcome(publicChoices, outcomes, opaqueChoiceId(), stayText, RETURN_STAY, stayReply);
    Collections.shuffle(publicChoices);
    JSONArray choices = new JSONArray();
    for (JSONObject choice : publicChoices) choices.put(choice);

    journey.put("turnPackage", new JSONObject()
        .put("contextHash", actualHash)
        .put("choices", choices)
        .put("outcomes", outcomes));
    journey.put("turnStatus", RETURN_READY);
    journey.put("turnNarration", narration);
    story.put("returnJourney", journey);
    syncReturnJourneyProjection(story);
    state.put(ROOT_KEY, story);
    return narration;
  }

  ReturnJourneyResolution resolveReturnJourneyChoice(JSONObject state, String rawChoiceId)
      throws Exception {
    normalizeState(state);
    if (!returnJourneyReady(state)) throw new IllegalStateException("Return journey turn is not ready.");

    JSONObject story = state.getJSONObject(ROOT_KEY);
    JSONObject journey = story.getJSONObject("returnJourney");
    JSONObject pack = journey.getJSONObject("turnPackage");
    if (!pack.optString("contextHash", "").equals(returnJourneyContextHash(state))) {
      resetReturnTurnForProvider(journey);
      throw new IllegalStateException("Return journey choice context changed; callback is stale.");
    }

    String choiceId = rawChoiceId == null ? "" : rawChoiceId.trim();
    JSONObject publicChoice = findChoice(pack.optJSONArray("choices"), choiceId);
    JSONObject outcome = pack.optJSONObject("outcomes") == null ? null
        : pack.optJSONObject("outcomes").optJSONObject(choiceId);
    if (publicChoice == null || outcome == null) {
      throw new IllegalArgumentException("Unknown return journey choice.");
    }

    String visibleChoice = publicChoice.optString("text", "").trim();
    String type = outcome.optString("type", "").trim();
    String reply = outcome.optString("reply", "").trim();
    int progress = Math.max(0, journey.optInt("progress", 0));
    int required = Math.max(2, journey.optInt("requiredProgress", RETURN_REQUIRED_PROGRESS));
    boolean arrived = false;

    if (RETURN_PROGRESS.equals(type)) {
      progress++;
      journey.put("progress", progress);
      if (progress >= required) {
        String target = journey.optString("targetLocation", "").trim();
        if (target.isEmpty()) throw new IllegalStateException("Return journey target is missing.");
        state.put("location", target);
        journey.put("currentPosition", target);
        arrived = true;
      } else {
        String levelKey = journey.optString("levelKey", state.optString(LevelCore.LEVEL_KEY, ""));
        String local = LevelCore.returnJourneyLocation(levelKey);
        state.put("location", local);
        journey.put("currentPosition", local);
      }
    } else if (RETURN_TO_START.equals(type)) {
      journey.put("progress", 0);
      LevelCore.returnToCurrentLevelStart(state);
      String start = state.optString("location", journey.optString("startLocation", ""));
      journey.put("currentPosition", start);
    } else if (RETURN_STAY.equals(type)) {
      // Position and progress intentionally remain unchanged.
    } else {
      throw new IllegalArgumentException("Unsupported return journey outcome.");
    }

    String cause = journey.optString("cause", "");
    if (arrived) {
      journey.put("active", false)
          .put("turnStatus", "")
          .put("turnPackage", new JSONObject())
          .put("turnNarration", "");
      if (RETURN_CAUSE_STORY.equals(cause)) rearmCurrentDecision(story);
    } else {
      journey.put("turnIndex", journey.optInt("turnIndex", 0) + 1);
      resetReturnTurnForProvider(journey);
    }

    story.put("returnJourney", journey);
    syncReturnJourneyProjection(story);
    state.put(ROOT_KEY, story);
    return new ReturnJourneyResolution(visibleChoice, reply, type, arrived, cause);
  }

  private static boolean validReturnNarration(String raw) {
    String value = raw == null ? "" : raw.trim();
    if (value.length() < 120 || value.length() > 900) return false;
    String lower = value.toLowerCase(Locale.ROOT);
    return !lower.contains("chọn sai") && !lower.contains("reset")
        && !lower.contains("checkpoint") && !lower.contains("return_to_start")
        && !lower.contains("progress") && !lower.contains("stay_in_place");
  }

  static boolean validLoopNarration(String raw, String currentText, String nextText) {
    String reply = raw == null ? "" : raw.trim();
    String lower = reply.toLowerCase(Locale.ROOT);
    if (reply.length() < 120 || reply.length() > 900 || lower.contains("chọn sai")
        || lower.contains("reset") || lower.contains("checkpoint")) return false;
    for (String source : new String[]{currentText, nextText}) {
      String manuscript = source == null ? "" : source.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
      String prose = lower.replaceAll("\\s+", " ");
      for (int i = 0; i + 96 <= prose.length(); i++) {
        if (manuscript.contains(prose.substring(i, i + 96))) return false;
      }
    }
    return true;
  }

  String loopNarrationPrompt(JSONObject state) throws Exception {
    JSONObject request = returnJourneyTurnRequest(state, "");
    if (!request.optBoolean("needed", false)) {
      throw new IllegalStateException("No return journey turn is awaiting narration.");
    }
    return request.getString("prompt");
  }

  boolean validLoopNarration(JSONObject state, String reply) throws Exception {
    return validReturnNarration(reply);
  }

  void completeReturnJourney(JSONObject state) throws Exception {
    throw new IllegalStateException("Return journeys now resolve only through Core-owned choices.");
  }

  void refreshLoopDecisionContext(JSONObject state) throws Exception {
    normalizeState(state);
    JSONObject story = state.getJSONObject(ROOT_KEY);
    if (!story.optBoolean("awaitingDecision", false)
        || !DECISION_READY.equals(story.optString("decisionStatus", ""))) return;
    JSONObject pack = story.optJSONObject("decisionPackage");
    if (pack == null || pack.optJSONObject("outcomes") == null) return;
    pack.put("contextHash", decisionContextHash(state));
    story.put("decisionPackage", pack);
    state.put(ROOT_KEY, story);
  }

  AuthoredTurn advancePendingTurn(
      JSONObject state, CharacterEncounterCore characterCore) throws Exception {
    normalizeState(state);
    JSONObject story = state.getJSONObject(ROOT_KEY);
    if (!story.optBoolean("pendingStoryAdvance", false)) return null;
    story.put("pendingStoryAdvance", false);
    state.put(ROOT_KEY, story);
    return advanceAndRender(state, ADVANCE_ACTION_VI, characterCore);
  }

  void rearmAuthoredEncounterAfterDeath(JSONObject state) throws Exception {
    normalizeState(state);
    JSONObject story = state.getJSONObject(ROOT_KEY);
    if (!story.optBoolean("pendingStoryAdvance", false) || repository == null) return;
    StoryRepository.Chapter chapter = repository.chapter(story.optString("currentChapter", ""));
    StoryRepository.Segment segment = repository.segment(
        story.optString("currentChapter", ""), story.optInt("currentSegmentIndex", 0));
    if (chapter != null && segment != null && StoryRepository.MODE_ENTITY_GATE.equals(segment.mode)) {
      story.put("pendingStoryAdvance", false);
      armTurnGate(story, chapter, segment);
      state.put(ROOT_KEY, story);
    }
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
      if (characters != null) {
        java.util.Iterator<String> storyCharacterIds = characters.keys();
        while (storyCharacterIds.hasNext()) {
          String characterId = storyCharacterIds.next();
          JSONObject character = characters.optJSONObject(characterId);
          if (character == null || !"story_local".equals(character.optString("scope", ""))) continue;
          output.append("Story-local ").append(characterId).append(" presence: ")
              .append(character.optString("presence", PRESENCE_UNKNOWN)).append(".\n");
        }
      }
      if (story.optBoolean("awaitingDecision", false)) {
        output.append("STORY DECISION: every player-controlled story turn owns exactly three hidden choices. ")
            .append("Explorer AI must not resolve them or infer which visible choice is canon.\n");
      }
      if (story.optBoolean("awaitingEntityAttack", false)) {
        output.append("AUTHORED ENTITY GATE: story requires combat here. Only the Attack action is legal until combat starts.\n");
      }
      if (story.optBoolean("arcComplete", false)) {
        output.append("The authored arc boundary has been reached. StoryCore does not choose or apply a Level destination.\n");
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
      result.put("storyMode", story.optBoolean("awaitingEntityAttack", false)
          ? StoryRepository.MODE_ENTITY_GATE
          : (story.optBoolean("awaitingDecision", false) ? StoryRepository.MODE_DECISION : ""));
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

  private void armTurnGate(
      JSONObject story, StoryRepository.Chapter chapter, StoryRepository.Segment segment) throws Exception {
    clearDecision(story);
    story.put("awaitingEntityAttack", false);
    story.put("entityGate", new JSONObject());

    if ("cutaway".equals(chapter.visibility)) return;

    if (StoryRepository.MODE_ENTITY_GATE.equals(segment.mode)
        && segment.decisionContract != null
        && segment.decisionContract.length() > 0) {
      story.put("awaitingEntityAttack", true);
      story.put("entityGate", copyObject(segment.decisionContract));
      return;
    }

    if (!StoryRepository.MODE_DECISION.equals(segment.mode)
        || segment.decisionContract == null
        || segment.decisionContract.length() == 0) {
      return;
    }

    story.put("awaitingDecision", true);
    story.put("decisionStatus", DECISION_PROVIDER_REQUIRED);
    story.put("decisionId", segment.id);
    story.put("decisionContract", copyObject(segment.decisionContract));
    story.put("decisionPackage", new JSONObject());
  }

  private static JSONObject emptyReturnJourney() {
    try {
      return new JSONObject()
          .put("active", false)
          .put("journeyId", "")
          .put("cause", "")
          .put("levelKey", "")
          .put("startLocation", "")
          .put("currentPosition", "")
          .put("targetLocation", "")
          .put("progress", 0)
          .put("requiredProgress", RETURN_REQUIRED_PROGRESS)
          .put("turnIndex", 0)
          .put("turnStatus", "")
          .put("turnPackage", new JSONObject())
          .put("turnNarration", "")
          .put("pausedStory", new JSONObject());
    } catch (Exception ignored) {
      return new JSONObject();
    }
  }

  private static void syncReturnJourneyProjection(JSONObject story) throws Exception {
    JSONObject journey = story.optJSONObject("returnJourney");
    if (journey == null) {
      journey = emptyReturnJourney();
      story.put("returnJourney", journey);
    }
    boolean active = journey.optBoolean("active", false);
    story.put("returnJourneyPending", active);
    story.put("returnAnchorLocation", active ? journey.optString("targetLocation", "") : "");
  }

  private static void resetReturnTurnForProvider(JSONObject journey) throws Exception {
    journey.put("turnStatus", RETURN_PROVIDER_REQUIRED);
    journey.put("turnPackage", new JSONObject());
    journey.put("turnNarration", "");
  }

  private static void rearmCurrentDecision(JSONObject story) throws Exception {
    if (!story.optBoolean("awaitingDecision", false)) {
      // Wrong-choice journeys preserve the authored gate. Death journeys do not manufacture one.
      return;
    }
    story.put("decisionStatus", DECISION_PROVIDER_REQUIRED);
    story.put("decisionPackage", new JSONObject());
  }

  private String returnJourneyContextHash(JSONObject state) {
    try {
      JSONObject story = state.optJSONObject(ROOT_KEY);
      JSONObject journey = story == null ? null : story.optJSONObject("returnJourney");
      if (journey == null) return "";
      JSONObject material = new JSONObject()
          .put("journeyId", journey.optString("journeyId", ""))
          .put("cause", journey.optString("cause", ""))
          .put("levelKey", journey.optString("levelKey", ""))
          .put("startLocation", journey.optString("startLocation", ""))
          .put("currentPosition", journey.optString("currentPosition", ""))
          .put("targetLocation", journey.optString("targetLocation", ""))
          .put("progress", journey.optInt("progress", 0))
          .put("requiredProgress", journey.optInt("requiredProgress", RETURN_REQUIRED_PROGRESS))
          .put("turnIndex", journey.optInt("turnIndex", 0))
          .put("pausedStory", journey.optJSONObject("pausedStory"))
          .put("storyFlags", story.optJSONObject(FLAGS_KEY))
          .put("characters", story.optJSONObject(CHARACTERS_KEY))
          .put("party", state.optJSONArray("party"))
          .put("inventory", state.optJSONArray("inventory"))
          .put("gameFlags", state.optJSONObject("flags"));
      return StoryRepository.sourceDigest(material.toString());
    } catch (Exception ignored) {
      return "";
    }
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
      material.append(story.optString("storyId", "")).append('|')
          .append(story.optString("levelKey", "")).append('|')
          .append(story.optString("sourceRevision", "")).append('|')
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

  private static String opaqueChoiceId() {
    return "choice_" + UUID.randomUUID().toString().replace("-", "");
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
      story.put("storyId", repository.storyId());
      story.put("levelKey", repository.levelKey());
      story.put("sourceRevision", repository.sourceRevision());
      story.put("currentChapter", repository.startChapter());
      story.put("currentSegmentIndex", 0);
      story.put("currentSegmentId", "");
      story.put("segmentDelivered", false);
      story.put("chapterEntered", false);
      story.put("arcComplete", false);
      story.put("pendingStoryAdvance", false);
      story.put("awaitingEntityAttack", false);
      story.put("entityGate", new JSONObject());
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
      if (EVENT_CHARACTER_PRESENT.equals(type)
          || EVENT_CHARACTER_MISSING.equals(type)
          || EVENT_CHARACTER_PRESENCE.equals(type)) {
        String id = normalizeCharacterId(event.optString("characterId", ""));
        if (id.isEmpty()) throw new IllegalArgumentException("Invalid story-local character id.");
        JSONObject character = ensureCharacterState(characters, id);
        character.put("scope", event.optString("scope", character.optString("scope", "story_local")));
        String presence = EVENT_CHARACTER_MISSING.equals(type) ? PRESENCE_MISSING
            : (EVENT_CHARACTER_PRESENT.equals(type) ? PRESENCE_PRESENT
                : normalizePresence(event.optString("presence", PRESENCE_UNKNOWN)));
        character.put("presence", presence);
        story.put(CHARACTERS_KEY, characters);
        state.put(ROOT_KEY, story);
        recordEvent(state, type, id);
      } else if (EVENT_STORY_FLAG_SET.equals(type) || EVENT_STORY_FACT_SET.equals(type)) {
        String key = event.optString("key", "").trim();
        if (key.isEmpty()) throw new IllegalArgumentException("Story flag key is required.");
        JSONObject flags = story.getJSONObject(FLAGS_KEY);
        Object value = event.has("value") ? event.get("value") : Boolean.TRUE;
        flags.put(key, value);
        story.put(FLAGS_KEY, flags);
        state.put(ROOT_KEY, story);
        recordEvent(state, type, key);
      } else if (EVENT_ARC_BOUNDARY_REACHED.equals(type)
          || EVENT_LEVEL0_ARC_BOUNDARY_REACHED.equals(type)) {
        JSONObject flags = story.getJSONObject(FLAGS_KEY);
        if (EVENT_LEVEL0_ARC_BOUNDARY_REACHED.equals(type)) {
          flags.put("level0_arc_boundary_reached", true);
        }
        flags.put("arc_boundary_reached", true);
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

  private static String normalizePresence(String raw) {
    String value = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
    if (PRESENCE_PRESENT.equals(value)
        || PRESENCE_MISSING.equals(value)
        || PRESENCE_DECEASED.equals(value)) {
      return value;
    }
    return PRESENCE_UNKNOWN;
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

package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.*;

public class OfflineStoryLoopTest {
  private static Path assets() {
    for (Path root = Paths.get("").toAbsolutePath(); root != null; root = root.getParent()) {
      for (String prefix : new String[]{"src/main/assets", "app/src/main/assets",
          "android-apk/app/src/main/assets"}) {
        Path path = root.resolve(prefix);
        if (Files.isRegularFile(path.resolve("story/generated/story_catalog.json"))) return path;
      }
    }
    throw new IllegalStateException("Story assets unavailable");
  }

  private static StoryRepository repository() {
    Path root = assets();
    return new StoryRepository(path ->
        new String(Files.readAllBytes(root.resolve(path)), StandardCharsets.UTF_8));
  }

  private static JSONObject storyChoices(String tag) throws Exception {
    return new JSONObject()
        .put("canon", new JSONObject().put("text", "Đi theo dấu hiệu còn đáng tin " + tag))
        .put("return", new JSONObject()
            .put("text", "Rẽ qua khoảng sáng lệch bên trái " + tag)
            .put("reply", "Khoảng sáng co lại sau lưng Cao Minh. Bố cục trước mắt đổi chỗ, nhưng không có sự kiện đã hoàn thành nào xảy ra lại."))
        .put("stay", new JSONObject()
            .put("text", "Dừng lại kiểm tra bề mặt gần nhất " + tag)
            .put("reply", "Cao Minh giữ vị trí, kiểm tra những dấu vết quanh mình. Không có diễn biến canon, phần thưởng hay kết quả chiến đấu nào được kích hoạt thêm."));
  }

  private static JSONObject returnTurnNode(String tag) throws Exception {
    String narration = "Cao Minh đi giữa những mảng kiến trúc quen mà không hoàn toàn trùng khớp. "
        + "Một dấu cũ vẫn còn trên bề mặt gần đó, nhưng khoảng cách giữa các góc rẽ đã đổi. "
        + "Hắn giữ nguyên những gì đã biết và chỉ đánh giá ba hướng có thể thử trong khu vực hiện tại. "
        + tag;
    return new JSONObject()
        .put("narration", narration)
        .put("progress", new JSONObject()
            .put("text", "Theo dấu cũ xuyên qua lối hẹp " + tag)
            .put("reply", "Cao Minh đi qua một đoạn không gian khác dạng nhưng vẫn thuộc cùng khu vực. Những dấu cũ bắt đầu có quan hệ rõ hơn với nhau."))
        .put("return", new JSONObject()
            .put("text", "Bám theo dãy đèn lệch nhịp " + tag)
            .put("reply", "Dãy đèn dẫn qua vài góc rẽ rồi mở ra một bố cục quen thuộc ở khu vực xuất phát. Không có sự kiện cũ nào được phát lại."))
        .put("stay", new JSONObject()
            .put("text", "Kiểm tra khe tường trước mặt " + tag)
            .put("reply", "Cao Minh kiểm tra kỹ nhưng không rời khu vực đang đứng. Dấu vết vẫn ở đó, không có phần thưởng hay tiến độ canon mới."));
  }

  private static JSONObject returnTurn(String tag) throws Exception {
    return new JSONObject().put("current", returnTurnNode(tag))
        .put("next", returnTurnNode(tag + " tiếp"));
  }

  private static String decisionOutcomeId(JSONObject state, String type) throws Exception {
    JSONObject outcomes = state.getJSONObject("story").getJSONObject("decisionPackage")
        .getJSONObject("outcomes");
    java.util.Iterator<String> keys = outcomes.keys();
    while (keys.hasNext()) {
      String key = keys.next();
      if (type.equals(outcomes.getJSONObject(key).getString("type"))) return key;
    }
    throw new IllegalStateException("Missing Story outcome " + type);
  }

  private static String returnOutcomeId(JSONObject state, String type) throws Exception {
    JSONObject outcomes = state.getJSONObject("story").getJSONObject("returnJourney")
        .getJSONObject("turnPackage").getJSONObject("outcomes");
    java.util.Iterator<String> keys = outcomes.keys();
    while (keys.hasNext()) {
      String key = keys.next();
      if (type.equals(outcomes.getJSONObject(key).getString("type"))) return key;
    }
    throw new IllegalStateException("Missing return outcome " + type);
  }

  private static void installStoryChoices(StoryCore core, JSONObject state, String tag)
      throws Exception {
    JSONObject request = core.decisionGenerationRequest(state, "");
    assertTrue(request.getBoolean("needed"));
    core.installDecisionPackage(state, request.getString("contextHash"), storyChoices(tag));
  }

  private static void installReturnTurn(StoryCore core, JSONObject state, String tag)
      throws Exception {
    JSONObject request = core.returnJourneyTurnRequest(state, "");
    assertTrue(request.getBoolean("needed"));
    core.installReturnJourneyTurn(
        state,
        request.getString("journeyId"),
        request.getInt("turnIndex"),
        request.getString("contextHash"),
        returnTurn(tag));
  }

  private static JSONObject storyState(String level) throws Exception {
    return new JSONObject()
        .put("currentLevelKey", level)
        .put("currentLevel", 0)
        .put("location", "đích đã lưu " + level)
        .put("turn", 6)
        .put("party", new JSONArray())
        .put("inventory", new JSONArray().put("existing"))
        .put("flags", new JSONObject().put("canonFlag", true))
        .put("levelRoute", new JSONObject().put("levelKey", level).put("streak", 4));
  }

  private static JSONObject decisionState(StoryCore core) throws Exception {
    JSONObject state = storyState("0");
    core.normalizeState(state);
    core.advanceAndRender(state, StoryCore.ADVANCE_ACTION_VI,
        new CharacterEncounterCore(bound -> bound - 1));
    return state;
  }

  private static void converge(StoryCore core, JSONObject state) throws Exception {
    for (int step = 0; step < 3; step++) {
      installReturnTurn(core, state, "hội-tụ-" + step);
      core.resolveReturnJourneyChoice(state, returnOutcomeId(state, StoryCore.RETURN_PROGRESS));
    }
  }

  @Test public void prearmedRandomEncounterBlocksReturnTurnAndVictoryKeepsJourney() throws Exception {
    StoryCore core = StoryCore.withRepository(repository());
    JSONObject state = decisionState(core);
    core.beginDeathReturnJourney(state, "đích gốc", "0");
    installReturnTurn(core, state, "trước-combat");
    core.resolveReturnJourneyChoice(state, returnOutcomeId(state, StoryCore.RETURN_PROGRESS));
    JSONObject journey = state.getJSONObject("story").getJSONObject("returnJourney");
    String target = journey.getString("targetLocation");
    int progress = journey.getInt("progress");
    String staleChoice = returnOutcomeId(state, StoryCore.RETURN_STAY);
    state.getJSONObject("flags").put("entityEncounterKey", "hound");
    assertTrue(GameCoreFacade.startRandomEntityEncounter(new EntityCore(null), state));
    assertTrue(CombatChoiceEngine.isActive(state));
    core.resumeReturnJourneyAfterTurn(state, true);
    assertFalse(core.returnJourneyCurrentReady(state));
    assertEquals(progress, journey.getInt("progress"));
    assertEquals(target, journey.getString("targetLocation"));
    try {
      core.selectReturnChoice(state, staleChoice);
      fail("Choice before combat must be stale");
    } catch (IllegalArgumentException expected) { /* stale */ }
    state.getJSONObject("combat").put("active", false).put("outcome", "victory");
    state.getJSONObject("flags").put("entityEncounterKey", "");
    installReturnTurn(core, state, "sau-combat");
    assertTrue(core.returnJourneyReady(state));
    assertEquals(progress, journey.getInt("progress"));
    assertEquals(target, journey.getString("targetLocation"));
  }

  @Test public void deathDuringReturnQueuesLocalRestartAtLevelStart() throws Exception {
    StoryCore core = StoryCore.withRepository(repository());
    JSONObject state = decisionState(core);
    installStoryChoices(core, state, "sai");
    core.resolveDecision(state, decisionOutcomeId(state, StoryCore.OUTCOME_RETURN),
        new CharacterEncounterCore(bound -> bound - 1));
    installReturnTurn(core, state, "lượt-cũ");
    core.resolveReturnJourneyChoice(state, returnOutcomeId(state, StoryCore.RETURN_PROGRESS));
    installReturnTurn(core, state, "lượt-chết");
    String staleChoice = returnOutcomeId(state, StoryCore.RETURN_STAY);

    state.put("combat", new JSONObject().put("outcome", "defeat")
        .put("deathReturnJourneyPending", true)
        .put("deathReturnAnchorLocation", state.getString("location")));
    GameCoreFacade.armDeathReturnIfNeeded(core, state);

    JSONObject combat = state.getJSONObject("combat");
    assertFalse(combat.getBoolean("deathReturnJourneyPending"));
    assertFalse(combat.getBoolean("deathReturnJourneyStarted"));
    assertTrue(combat.getBoolean("deathRestartPending"));
    assertEquals(LevelCore.defaultLocation("0"), state.getString("location"));
    assertFalse(core.returnJourneyActive(state));
    try {
      core.selectReturnChoice(state, staleChoice);
      fail("Choice from before death must be stale");
    } catch (IllegalArgumentException expected) { /* stale */ }
    assertTrue(core.awaitingDecision(state));
    assertTrue(core.decisionNeedsProvider(state));
  }

  @Test public void deathAtDecisionGateRestoresFreshGateAndRejectsOldChoice() throws Exception {
    StoryCore core = StoryCore.withRepository(repository());
    JSONObject state = decisionState(core);
    installStoryChoices(core, state, "trước-chết");
    String stale = decisionOutcomeId(state, StoryCore.OUTCOME_CANON);
    String oldToken = state.getJSONObject("story").getString("decisionGenerationToken");
    core.beginDeathReturnJourney(state, state.getString("location"), "0");
    converge(core, state);
    assertTrue(core.awaitingDecision(state));
    assertTrue(core.decisionNeedsProvider(state));
    assertNotEquals(oldToken, state.getJSONObject("story").getString("decisionGenerationToken"));
    JSONObject staleStory = new JSONObject().put("decisionPackage", new JSONObject()
        .put("choices", new JSONArray().put(new JSONObject().put("id", stale))
            .put(new JSONObject().put("id", "old-2")).put(new JSONObject().put("id", "old-3"))));
    assertFalse(GameCoreFacade.sameStoryChoiceSet(staleStory, state.getJSONObject("story")));
    installStoryChoices(core, state, "sau-chết");
    try {
      core.resolveDecision(state, stale, new CharacterEncounterCore(bound -> bound - 1));
      fail("Old decision callback must not commit");
    } catch (IllegalArgumentException expected) { /* stale */ }
  }

  @Test public void deathWithPendingAdvanceResumesExactlyOnce() throws Exception {
    StoryCore core = StoryCore.withRepository(repository());
    JSONObject state = decisionState(core);
    installStoryChoices(core, state, "canon");
    core.resolveDecision(state, decisionOutcomeId(state, StoryCore.OUTCOME_CANON),
        new CharacterEncounterCore(bound -> bound - 1));
    int index = state.getJSONObject("story").getInt("currentSegmentIndex");
    int events = state.getJSONObject("story").getInt("eventSequence");
    core.beginDeathReturnJourney(state, state.getString("location"), "0");
    converge(core, state);
    assertTrue(core.hasPendingStoryAdvance(state));
    assertEquals(index, state.getJSONObject("story").getInt("currentSegmentIndex"));
    CharacterEncounterCore characters = new CharacterEncounterCore(bound -> bound - 1);
    assertNotNull(core.advancePendingTurn(state, characters));
    assertEquals(index + 1, state.getJSONObject("story").getInt("currentSegmentIndex"));
    assertNull(core.advancePendingTurn(state, characters));
    assertEquals(events, state.getJSONObject("story").getInt("eventSequence"));
  }

  @Test public void authoredEntityGateDeathReturnsToAttackGate() throws Exception {
    StoryCore core = StoryCore.withRepository(StoryCoreTest.entityGateFixtureRepository());
    JSONObject state = storyState("0");
    core.advanceAndRender(state, StoryCore.ADVANCE_ACTION_VI,
        new CharacterEncounterCore(bound -> bound - 1));
    assertTrue(core.awaitingEntityAttack(state));
    String segment = state.getJSONObject("story").getString("currentSegmentId");
    core.consumeEntityAttack(state);
    core.rearmAuthoredEncounterAfterDeath(state);
    core.beginDeathReturnJourney(state, state.getString("location"), "0");
    converge(core, state);
    assertTrue(core.awaitingEntityAttack(state));
    assertFalse(core.hasPendingStoryAdvance(state));
    assertEquals("hound", core.entityAttackChoice(state).getString("entityKey"));
    assertEquals(segment, state.getJSONObject("story").getString("currentSegmentId"));
  }

  @Test public void wrongStoryChoiceRunsMultiTurnJourneyAndConvergesExactlyOnce() throws Exception {
    CharacterEncounterCore characters = new CharacterEncounterCore(bound -> bound - 1);

    for (String level : new String[]{"0", "0.1"}) {
      StoryCore core = StoryCore.withRepository(repository());
      JSONObject state = storyState(level);
      core.normalizeState(state);
      StoryCore.AuthoredTurn first =
          core.advanceAndRender(state, StoryCore.ADVANCE_ACTION_VI, characters);
      assertEquals(StoryRepository.MODE_DECISION, first.mode);
      installStoryChoices(core, state, "đầu-" + level);

      JSONObject story = state.getJSONObject("story");
      String chapter = story.getString("currentChapter");
      String scene = story.getString("currentScene");
      int segmentIndex = story.getInt("currentSegmentIndex");
      int eventSequence = story.getInt("eventSequence");
      String inventory = state.getJSONArray("inventory").toString();
      String flags = state.getJSONObject("flags").toString();

      // STAY is a real hidden outcome. It must not advance authored Story.
      StoryCore.DecisionResolution stayed = core.resolveDecision(
          state, decisionOutcomeId(state, StoryCore.OUTCOME_STAY), characters);
      assertTrue(stayed.looped);
      assertEquals("đích đã lưu " + level, state.getString("location"));
      assertFalse(core.hasPendingStoryAdvance(state));
      assertTrue(core.decisionNeedsProvider(state));
      installStoryChoices(core, state, "sau-dừng-" + level);

      String staleStoryChoice = decisionOutcomeId(state, StoryCore.OUTCOME_CANON);
      StoryCore.DecisionResolution wrong = core.resolveDecision(
          state, decisionOutcomeId(state, StoryCore.OUTCOME_RETURN), characters);
      assertTrue(wrong.looped);
      assertTrue(core.returnJourneyActive(state));
      assertEquals(level, state.getString("currentLevelKey"));
      assertEquals(LevelCore.defaultLocation(level), state.getString("location"));
      assertFalse(core.awaitingDecision(state));

      JSONObject journey = state.getJSONObject("story").getJSONObject("returnJourney");
      assertEquals(StoryCore.RETURN_CAUSE_STORY, journey.getString("cause"));
      assertEquals(level, journey.getString("levelKey"));
      assertEquals("đích đã lưu " + level, journey.getString("targetLocation"));
      assertEquals(chapter, story.getString("currentChapter"));
      assertEquals(scene, story.getString("currentScene"));
      assertEquals(segmentIndex, story.getInt("currentSegmentIndex"));
      assertEquals(eventSequence, story.getInt("eventSequence"));

      // A native save/load of a generated current turn must retain its exact public and hidden package.
      installReturnTurn(core, state, "lượt-một-" + level);
      String savedChoices = core.returnJourneyChoices(state).toString();
      JSONObject loaded = new JSONObject(state.toString());
      core.normalizeState(loaded);
      state = loaded;
      assertTrue(core.returnJourneyReady(state));
      assertFalse(core.returnJourneyNeedsProvider(state));
      assertTrue(state.getJSONObject("story").getJSONObject("returnJourney")
          .getBoolean("lookaheadReady"));
      assertEquals(savedChoices, core.returnJourneyChoices(state).toString());
      assertEquals(3, state.getJSONObject("story").getJSONObject("returnJourney")
          .getJSONObject("turnPackage").getJSONObject("outcomes").length());

      String staleReturnChoice = returnOutcomeId(state, StoryCore.RETURN_STAY);
      StoryCore.ReturnJourneyResolution stayJourney =
          core.resolveReturnJourneyChoice(state, staleReturnChoice);
      assertFalse(stayJourney.arrived);
      assertEquals(0, state.getJSONObject("story").getJSONObject("returnJourney").getInt("progress"));
      assertEquals(1, state.getJSONObject("story").getJSONObject("returnJourney").getInt("turnIndex"));
      assertFalse(core.returnJourneyReady(state));
      assertFalse(state.getJSONObject("story").getJSONObject("returnJourney")
          .optString("turnNarration", "").isEmpty());
      assertTrue(core.returnJourneyNeedsProvider(state));
      assertTrue(core.returnJourneyCurrentReady(state));
      assertFalse(core.returnJourneyReady(state));

      // The promoted current turn remains selectable while its next lookahead is generated.
      JSONObject selectableWithoutLookahead = new JSONObject(state.toString());
      String pendingReturnChoice = returnOutcomeId(selectableWithoutLookahead, StoryCore.RETURN_STAY);
      core.selectReturnChoice(selectableWithoutLookahead, pendingReturnChoice);
      assertEquals(pendingReturnChoice, selectableWithoutLookahead.getJSONObject("story")
          .getJSONObject("returnJourney").getString("pendingChoiceId"));

      installReturnTurn(core, state, "lượt-hai-" + level);

      try {
        core.resolveReturnJourneyChoice(state, staleReturnChoice);
        fail("Old return callback must not mutate a newer return turn.");
      } catch (IllegalArgumentException expected) {
        assertTrue(expected.getMessage().contains("Unknown"));
      }

      StoryCore.ReturnJourneyResolution returned = core.resolveReturnJourneyChoice(
          state, returnOutcomeId(state, StoryCore.RETURN_TO_START));
      assertFalse(returned.arrived);
      assertEquals(LevelCore.defaultLocation(level), state.getString("location"));
      assertEquals(0, state.getJSONObject("story").getJSONObject("returnJourney").getInt("progress"));

      // Three Core-owned progress outcomes are required. No narration can teleport to the target.
      for (int step = 1; step <= 3; step++) {
        installReturnTurn(core, state, "tiến-" + step + "-" + level);
        StoryCore.ReturnJourneyResolution moved = core.resolveReturnJourneyChoice(
            state, returnOutcomeId(state, StoryCore.RETURN_PROGRESS));
        if (step < 3) {
          assertFalse(moved.arrived);
          assertTrue(core.returnJourneyActive(state));
          assertNotEquals("đích đã lưu " + level, state.getString("location"));
        } else {
          assertTrue(moved.arrived);
          assertFalse(core.returnJourneyActive(state));
          assertEquals("đích đã lưu " + level, state.getString("location"));
        }
        assertEquals(inventory, state.getJSONArray("inventory").toString());
        assertEquals(flags, state.getJSONObject("flags").toString());
        assertEquals(eventSequence, state.getJSONObject("story").getInt("eventSequence"));
        assertEquals(4, state.getJSONObject("levelRoute").getInt("streak"));
      }

      // The exact authored gate is restored. Only a fresh correct choice may advance it once.
      assertTrue(core.decisionNeedsProvider(state));
      installStoryChoices(core, state, "hội-tụ-" + level);
      String canonId = decisionOutcomeId(state, StoryCore.OUTCOME_CANON);
      StoryCore.DecisionResolution canon = core.resolveDecision(state, canonId, characters);
      assertFalse(canon.looped);
      assertTrue(core.hasPendingStoryAdvance(state));
      assertEquals(segmentIndex, state.getJSONObject("story").getInt("currentSegmentIndex"));

      StoryCore.AuthoredTurn next = core.advancePendingTurn(state, characters);
      assertNotNull(next);
      assertEquals(segmentIndex + 1, state.getJSONObject("story").getInt("currentSegmentIndex"));
      assertFalse(core.hasPendingStoryAdvance(state));

      try {
        core.resolveDecision(state, staleStoryChoice, characters);
        fail("Old Story callback must not commit after convergence.");
      } catch (IllegalArgumentException expected) {
        assertTrue(expected.getMessage().contains("Unknown"));
      }
      try {
        core.resolveDecision(state, canonId, characters);
        fail("The correct authored outcome must not commit twice.");
      } catch (IllegalArgumentException expected) {
        assertTrue(expected.getMessage().contains("Unknown"));
      }
    }
  }

  @Test public void deathReturnAtPointZeroOneUsesAllThreeOutcomesBeforeResumingPausedStory()
      throws Exception {
    StoryCore core = StoryCore.withRepository(repository());
    CharacterEncounterCore characters = new CharacterEncounterCore(bound -> bound - 1);
    JSONObject state = storyState("0.1").put("location", "Level 0.1 / nơi Cao Minh ngã xuống");
    core.normalizeState(state);
    StoryCore.AuthoredTurn first =
        core.advanceAndRender(state, StoryCore.ADVANCE_ACTION_VI, characters);
    assertEquals(StoryRepository.MODE_DECISION, first.mode);

    JSONObject story = state.getJSONObject("story");
    String chapter = story.getString("currentChapter");
    String segment = story.getString("currentSegmentId");
    int segmentIndex = story.getInt("currentSegmentIndex");
    int eventSequence = story.getInt("eventSequence");
    String inventory = state.getJSONArray("inventory").toString();
    String flags = state.getJSONObject("flags").toString();

    core.beginDeathReturnJourney(
        state, "Level 0.1 / nơi Cao Minh ngã xuống", "0.1");
    assertTrue(core.returnJourneyActive(state));
    assertEquals(LevelCore.defaultLocation("0.1"), state.getString("location"));
    assertEquals(StoryCore.RETURN_CAUSE_DEATH,
        state.getJSONObject("story").getJSONObject("returnJourney").getString("cause"));

    // Repeated STAY outcomes cannot secretly move the player or authored cursor.
    for (int i = 0; i < 2; i++) {
      installReturnTurn(core, state, "tử-trận-dừng-" + i);
      StoryCore.ReturnJourneyResolution result = core.resolveReturnJourneyChoice(
          state, returnOutcomeId(state, StoryCore.RETURN_STAY));
      assertFalse(result.arrived);
      assertEquals(0, state.getJSONObject("story").getJSONObject("returnJourney").getInt("progress"));
      assertEquals(LevelCore.defaultLocation("0.1"), state.getString("location"));
    }

    // Move away, then repeatedly fold back to the exact 0.1 start.
    for (int i = 0; i < 2; i++) {
      installReturnTurn(core, state, "tử-trận-tiến-thử-" + i);
      core.resolveReturnJourneyChoice(state, returnOutcomeId(state, StoryCore.RETURN_PROGRESS));
      assertNotEquals(LevelCore.defaultLocation("0.1"), state.getString("location"));

      installReturnTurn(core, state, "tử-trận-quay-đầu-" + i);
      StoryCore.ReturnJourneyResolution returned = core.resolveReturnJourneyChoice(
          state, returnOutcomeId(state, StoryCore.RETURN_TO_START));
      assertFalse(returned.arrived);
      assertEquals(LevelCore.defaultLocation("0.1"), state.getString("location"));
      assertEquals(0, state.getJSONObject("story").getJSONObject("returnJourney").getInt("progress"));
    }

    // Only three uninterrupted Core-owned progress outcomes can reach the saved death location.
    for (int step = 1; step <= 3; step++) {
      installReturnTurn(core, state, "tử-trận-về-đích-" + step);
      StoryCore.ReturnJourneyResolution moved = core.resolveReturnJourneyChoice(
          state, returnOutcomeId(state, StoryCore.RETURN_PROGRESS));
      assertEquals(step == 3, moved.arrived);
    }

    assertFalse(core.returnJourneyActive(state));
    assertEquals("0.1", state.getString("currentLevelKey"));
    assertEquals("Level 0.1 / nơi Cao Minh ngã xuống", state.getString("location"));
    assertEquals(chapter, state.getJSONObject("story").getString("currentChapter"));
    assertEquals(segment, state.getJSONObject("story").getString("currentSegmentId"));
    assertEquals(segmentIndex, state.getJSONObject("story").getInt("currentSegmentIndex"));
    assertEquals(eventSequence, state.getJSONObject("story").getInt("eventSequence"));
    assertEquals(inventory, state.getJSONArray("inventory").toString());
    assertEquals(flags, state.getJSONObject("flags").toString());
    assertTrue(core.awaitingDecision(state));
    assertTrue(core.decisionNeedsProvider(state));
  }

  @Test public void returnJourneyRejectsEarlySelectionAndPreservesChoiceIdsThroughFailureAndReload()
      throws Exception {
    StoryCore core = StoryCore.withRepository(repository());
    JSONObject state = storyState("0.1");
    core.normalizeState(state);
    core.beginDeathReturnJourney(state, "đích lỗi provider", "0.1");
    JSONObject request = core.returnJourneyTurnRequest(state, "");
    assertTrue(request.getBoolean("needed"));
    assertFalse(core.returnJourneyReady(state));
    assertEquals(3, core.returnJourneyChoices(state).length());
    JSONObject legacy = new JSONObject(state.toString());
    legacy.getJSONObject("story").getJSONObject("returnJourney")
        .put("turnPackage", new JSONObject());
    core.normalizeState(legacy);
    assertEquals(3, core.returnJourneyChoices(legacy).length());
    JSONArray choices = core.returnJourneyChoices(state);
    String selected = returnOutcomeId(state, StoryCore.RETURN_STAY);
    try {
      core.selectReturnChoice(state, selected);
      fail("Unprepared return turn must reject every choice");
    } catch (IllegalArgumentException expected) {
      assertTrue(expected.getMessage().contains("stale"));
    }

    JSONObject bad = returnTurn("không-hợp-lệ");
    bad.getJSONObject("current").getJSONObject("progress").put("reply", "reset checkpoint");
    try {
      core.installReturnJourneyTurn(state, request.getString("journeyId"),
          request.getInt("turnIndex"), request.getString("contextHash"), bad);
      fail("Unsafe reply must be rejected");
    } catch (IllegalArgumentException expected) {
      assertTrue(expected.getMessage().contains("unsafe"));
    }
    assertFalse(core.returnJourneyReady(state));
    assertEquals("", state.getJSONObject("story").getJSONObject("returnJourney")
        .getJSONObject("turnPackage").getJSONObject("outcomes")
        .getJSONObject(selected).getString("reply"));
    JSONObject reloaded = new JSONObject(state.toString());
    core.normalizeState(reloaded);
    assertEquals(choices.toString(), core.returnJourneyChoices(reloaded).toString());
    assertEquals("", reloaded.getJSONObject("story").getJSONObject("returnJourney")
        .optString("pendingChoiceId", ""));
    assertTrue(core.returnJourneyNeedsProvider(reloaded));
    core.installReturnJourneyTurn(reloaded, request.getString("journeyId"),
        request.getInt("turnIndex"), request.getString("contextHash"), returnTurn("hợp-lệ"));
    for (int i = 0; i < 3; i++) {
      assertEquals(choices.getJSONObject(i).getString("id"),
          core.returnJourneyChoices(reloaded).getJSONObject(i).getString("id"));
    }
    core.selectReturnChoice(reloaded, selected);
    StoryCore.ReturnJourneyResolution resolution = core.resolveReturnJourneyChoice(reloaded, selected);
    assertEquals(StoryCore.RETURN_STAY, resolution.outcome);
    assertEquals(1, reloaded.getJSONObject("story").getJSONObject("returnJourney").getInt("turnIndex"));
  }

  @Test public void revisitingExistingEntityTraceDoesNotReplayCanonCombatOrRewards() throws Exception {
    StoryCore core = new StoryCore();
    JSONObject state = new JSONObject()
        .put("currentLevelKey", "1")
        .put("currentLevel", 1)
        .put("location", "Level 1 / cột bê tông có dấu cào Hound")
        .put("turn", 12)
        .put("party", new JSONArray())
        .put("inventory", new JSONArray()
            .put(new JSONObject().put("id", "existing_loot").put("quantity", 1)))
        .put("flags", new JSONObject()
            .put("canon_hound_event_completed", true)
            .put("canon_hound_reward_claimed", true))
        .put("combat", new JSONObject()
            .put("active", false)
            .put("outcome", "victory")
            .put("entityKey", "hound")
            .put("lootResolved", true)
            .put("droppedItem", "existing_loot")
            .put("coreDropResolved", true)
            .put("coreDropReward", 7));
    core.normalizeState(state);

    String beforeInventory = state.getJSONArray("inventory").toString();
    String beforeFlags = state.getJSONObject("flags").toString();
    String beforeCombat = state.getJSONObject("combat").toString();

    core.beginDeathReturnJourney(
        state, "Level 1 / cột bê tông có dấu cào Hound", "1");
    JSONObject request = core.returnJourneyTurnRequest(state, "");
    JSONObject generated = returnTurn("dấu-Hound-cũ");
    generated.getJSONObject("current").put("narration",
            "Cao Minh đi qua những cột bê tông đã đổi khoảng cách. Một vết cào cũ trên chân cột "
                + "gợi lại Hound từng xuất hiện ở vùng này, nhưng không có cuộc săn cũ nào diễn ra lại. "
                + "Hắn chỉ dùng dấu vết đã tồn tại để định hướng giữa ba lối đi trước mặt.");
    core.installReturnJourneyTurn(
        state, request.getString("journeyId"), request.getInt("turnIndex"),
        request.getString("contextHash"), generated);
    core.resolveReturnJourneyChoice(state, returnOutcomeId(state, StoryCore.RETURN_STAY));

    assertEquals(beforeInventory, state.getJSONArray("inventory").toString());
    assertEquals(beforeFlags, state.getJSONObject("flags").toString());
    assertEquals(beforeCombat, state.getJSONObject("combat").toString());
    assertTrue(state.getJSONObject("flags").getBoolean("canon_hound_event_completed"));
    assertTrue(state.getJSONObject("flags").getBoolean("canon_hound_reward_claimed"));
    assertTrue(state.getJSONObject("combat").getBoolean("lootResolved"));
    assertTrue(state.getJSONObject("combat").getBoolean("coreDropResolved"));
    assertEquals(7, state.getJSONObject("combat").getInt("coreDropReward"));
  }

  @Test public void deathReturnStartsAtExactCurrentLevelKeyForEveryKnownSublevel() throws Exception {
    StoryCore core = new StoryCore();
    String[] keys = {
        "0", "0.1", "0.2", "0.5", "0.7", "manila_room", "the_torment", "red_rooms",
        "1", "2", "3", "4", "5", "6"
    };

    for (String key : keys) {
      JSONObject state = new JSONObject()
          .put("currentLevelKey", key)
          .put("currentLevel", 0)
          .put("location", "vị trí tử trận " + key)
          .put("turn", 9)
          .put("party", new JSONArray())
          .put("inventory", new JSONArray().put("kept"))
          .put("flags", new JSONObject().put("completedCanon", true));
      core.normalizeState(state);
      core.beginDeathReturnJourney(state, "vị trí tử trận " + key, key);

      JSONObject journey = state.getJSONObject("story").getJSONObject("returnJourney");
      assertTrue(journey.getBoolean("active"));
      assertEquals(StoryCore.RETURN_CAUSE_DEATH, journey.getString("cause"));
      assertEquals(key, journey.getString("levelKey"));
      assertEquals(key, state.getString("currentLevelKey"));
      assertEquals(LevelCore.defaultLocation(key), state.getString("location"));
      assertEquals("vị trí tử trận " + key, journey.getString("targetLocation"));
      assertEquals("kept", state.getJSONArray("inventory").getString(0));
      assertTrue(state.getJSONObject("flags").getBoolean("completedCanon"));
    }
  }

  @Test public void choiceGenerationIsCurrentTurnOnlyAndUsesGeminiBridgeWithHaikuFallback() throws Exception {
    String ui = new String(Files.readAllBytes(assets().resolve("gm-choice-ui.js")), StandardCharsets.UTF_8);
    Path activity = assets().resolve("../java/com/rabpit/backroom/MainActivity.java").normalize();
    String java = new String(Files.readAllBytes(activity), StandardCharsets.UTF_8);

    assertTrue(ui.contains("Android.prepareStoryDecision(JSON.stringify(state))"));
    assertTrue(ui.contains("Android.prepareReturnJourneyTurn(JSON.stringify(state))"));
    assertTrue(ui.contains("Android.resolveReturnJourneyChoice(JSON.stringify(state), String(choice.id))"));
    assertFalse(ui.contains("Android.resumeStoryReturn"));
    assertFalse(ui.contains("Android.prefetchStoryDecision"));
    assertTrue(java.contains("void prepareStoryDecision(String stateJson)"));
    assertTrue(java.contains("void prepareReturnJourneyTurn(String stateJson)"));
    assertTrue(java.contains("rawOutput = geminiText(prompt)"));
    assertTrue(java.contains("rawOutput = haikuText(prompt)"));
  }

  @Test public void providerFailureAllowsOneAutomaticUiRetryBeforeManualFallback() throws Exception {
    String ui = new String(Files.readAllBytes(assets().resolve("gm-choice-ui.js")), StandardCharsets.UTF_8);

    assertTrue(ui.contains("function allowSingleAutomaticProviderRetry()"));
    assertTrue(ui.contains("window.__storyDecisionRetryKey !== decisionKey"));
    assertTrue(ui.contains("window.__storyDecisionRetryKey = decisionKey;"));
    assertTrue(ui.contains("window.__storyDecisionRequestKey = '';"));
    assertTrue(ui.contains("window.__returnJourneyRetryKey !== returnKey"));
    assertTrue(ui.contains("window.__returnJourneyRetryKey = returnKey;"));
    assertTrue(ui.contains("window.__returnJourneyRequestKey = '';"));
    int retryStory = ui.indexOf("retryStory.addEventListener('click', function(){");
    int retryStoryRequest = ui.indexOf("requestStoryDecision(true);", retryStory);
    int retryStoryRender = ui.indexOf("window.render();", retryStoryRequest);
    assertTrue(retryStory >= 0);
    assertTrue(retryStoryRequest > retryStory);
    assertTrue(retryStoryRender > retryStoryRequest);
    assertTrue(ui.contains("retryReturn.addEventListener('click', function(){ window.__returnProviderFailedKey = ''; requestReturnJourneyTurn(true); });"));
    assertFalse(ui.contains("Đang chuẩn bị ba lựa chọn…"));
    assertFalse(ui.contains("Đang chuẩn bị ba hướng đi…"));

    int errorHandler = ui.indexOf("window.backroomError = function(message)");
    int retry = ui.indexOf("allowSingleAutomaticProviderRetry();", errorHandler);
    int previousError = ui.indexOf("previousError(message)", errorHandler);
    assertTrue(errorHandler >= 0);
    assertTrue(retry > errorHandler);
    assertTrue(previousError > retry);
  }

  @Test public void providerProseCannotExposeLoopMechanicsOrReplayLongAuthoredText() {
    String anchor = "Cao Minh dừng bên ngưỡng cửa kim loại. " + "đoạn đã đọc ".repeat(12);
    String next = "Nam xuất hiện trong lối đi tiếp theo. " + "diễn biến kế ".repeat(12);
    assertFalse(StoryCore.validLoopNarration(anchor, anchor, next));
    assertFalse(StoryCore.validLoopNarration(next, anchor, next));
    assertFalse(StoryCore.validLoopNarration("Lối đi lặp lại. " + anchor.substring(28), anchor, next));
    assertFalse(StoryCore.validLoopNarration("Cao Minh chọn sai và reset. ".repeat(6), anchor, next));
    assertTrue(StoryCore.validLoopNarration(
        "Cao Minh trở về dãy phòng quen thuộc. Hắn dò theo các vệt sáng trên tường, "
            + "đi qua vài khúc quanh giống nhau rồi dừng trước nơi câu chuyện còn bỏ ngỏ.",
        anchor, next));
  }
}

package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class StoryCoreTest {
  @Test public void freshBootstrapRendersCanonicalManuscriptAndArmsDecision() throws Exception {
    Path assets = Paths.get("app/src/main/assets");
    if (!Files.isDirectory(assets)) assets = Paths.get("android-apk/app/src/main/assets");
    final Path assetRoot = assets;
    StoryRepository repository = new StoryRepository(path ->
        new String(Files.readAllBytes(assetRoot.resolve(path)), StandardCharsets.UTF_8));
    assertTrue(repository.available());
    JSONObject state = state();
    StoryCore core = StoryCore.withRepository(repository);
    core.normalizeState(state);
    JSONObject before = state.getJSONObject(StoryCore.ROOT_KEY);
    assertTrue(before.getBoolean("active"));
    assertFalse(before.getBoolean("arcComplete"));
    assertFalse(before.getBoolean("segmentDelivered"));
    assertFalse(before.getBoolean("awaitingDecision"));
    assertFalse(before.getBoolean("awaitingEntityAttack"));
    assertFalse(before.getBoolean("pendingStoryAdvance"));
    assertEquals("L0_C01", before.getString("currentChapter"));

    StoryCore.AuthoredTurn turn = core.advanceAndRender(
        state, "tiếp tục cốt truyện", new CharacterEncounterCore(bound -> bound - 1));
    String manuscript = new String(Files.readAllBytes(
        assets.resolve("story/source/level_0/LEVEL0_CH01.md")), StandardCharsets.UTF_8);
    assertEquals("L0_C01_P001", turn.segmentId);
    assertTrue(manuscript.contains(turn.reply.trim()));
    JSONObject after = state.getJSONObject(StoryCore.ROOT_KEY);
    assertTrue(after.getBoolean("segmentDelivered"));
    assertTrue(after.getBoolean("awaitingDecision"));
    assertEquals("PREFETCH_REQUIRED", after.getString("decisionStatus"));
    assertFalse(after.getBoolean("awaitingEntityAttack"));
  }

  private static JSONObject state() throws Exception {
    return new JSONObject()
        .put("currentLevel", 0)
        .put("currentLevelKey", "0")
        .put("turn", 1)
        .put("party", new JSONArray())
        .put("flags", new JSONObject());
  }

  private static StoryRepository decisionFixtureRepository() {
    String sourcePath = "story/source/level_0/LEVEL0_CH01.md";
    StringBuilder pause = new StringBuilder(
        "Cao Minh dừng lại trước một đoạn hành lang tối, nghe tiếng ù đèn kéo dài.");
    while (pause.length() < 920) {
      pause.append(" Hắn giữ nguyên vị trí, quan sát tường, thảm và ánh đèn mà không tiến thêm.");
    }
    String source = "# Level 0 — Chương 01: Quyết định\n\n"
        + pause + "\n\n"
        + "Hắn nghiêng người, áp sát mép tường rồi bước tiếp theo dấu cũ.";
    String metadata = "{"
        + "\"schemaVersion\":1,"
        + "\"sourceRevision\":\"decision-r2\","
        + "\"segmentTargetChars\":800,"
        + "\"segmentMaxChars\":1200,"
        + "\"startChapter\":\"L0_C01\","
        + "\"chapters\":[{"
        + "\"id\":\"L0_C01\","
        + "\"title\":\"Quyết định\","
        + "\"source\":\"" + sourcePath + "\","
        + "\"thread\":\"cao_minh\","
        + "\"visibility\":\"player\","
        + "\"nextChapter\":\"\","
        + "\"eventsOnEnter\":[],"
        + "\"eventsOnExit\":[],"
        + "\"requiredFacts\":[],"
        + "\"forbiddenClaims\":[]"
        + "}]}";
    String digest = StoryRepository.sourceDigest(source);
    String decisions = "{"
        + "\"schemaVersion\":3,"
        + "\"sourceRevision\":\"decision-r2\","
        + "\"chapters\":{"
        + "\"L0_C01\":{"
        + "\"sourceDigest\":\"" + digest + "\","
        + "\"segments\":{"
        + "\"L0_C01_P001\":{"
        + "\"mode\":\"DECISION\","
        + "\"decisionContract\":{"
        + "\"loopAnchor\":\"L0_C01_P001\","
        + "\"decisionGuard\":\"Không thay đổi authored plot.\""
        + "}},"
        + "\"L0_C01_P002\":{\"mode\":\"LINEAR\",\"decisionContract\":{}}"
        + "}}}}";
    Map<String, String> sources = new LinkedHashMap<>();
    sources.put(sourcePath, source);
    return StoryRepository.fromText(metadata, sources, decisions);
  }


  private static StoryRepository entityGateFixtureRepository() {
    String sourcePath = "story/source/level_0/LEVEL0_CH01.md";
    StringBuilder encounter = new StringBuilder(
        "Cao Minh khựng lại khi một Hound chắn ngang hành lang.");
    while (encounter.length() < 920) {
      encounter.append(" Tiếng móng cào thảm vang khô, con Entity vẫn giữ nguyên vị trí đối diện hắn.");
    }
    String source = "# Level 0 — Chương 01: Encounter\n\n"
        + encounter + "\n\n"
        + "Sau trận chiến, hành lang phía trước lại chìm trong tiếng ù đều đặn.";
    String metadata = "{"
        + "\"schemaVersion\":1,"
        + "\"sourceRevision\":\"entity-gate-r1\","
        + "\"segmentTargetChars\":800,"
        + "\"segmentMaxChars\":1200,"
        + "\"startChapter\":\"L0_C01\","
        + "\"chapters\":[{"
        + "\"id\":\"L0_C01\","
        + "\"title\":\"Encounter\","
        + "\"source\":\"" + sourcePath + "\","
        + "\"thread\":\"cao_minh\","
        + "\"visibility\":\"player\","
        + "\"nextChapter\":\"\","
        + "\"eventsOnEnter\":[],"
        + "\"eventsOnExit\":[],"
        + "\"requiredFacts\":[],"
        + "\"forbiddenClaims\":[]"
        + "}]}";
    String digest = StoryRepository.sourceDigest(source);
    String decisions = "{"
        + "\"schemaVersion\":3,"
        + "\"sourceRevision\":\"entity-gate-r1\","
        + "\"chapters\":{"
        + "\"L0_C01\":{"
        + "\"sourceDigest\":\"" + digest + "\","
        + "\"segments\":{"
        + "\"L0_C01_P001\":{"
        + "\"mode\":\"ENTITY_GATE\","
        + "\"decisionContract\":{"
        + "\"entityKey\":\"hound\","
        + "\"attackText\":\"Tấn công\","
        + "\"loopAnchor\":\"L0_C01_P001\""
        + "}},"
        + "\"L0_C01_P002\":{\"mode\":\"LINEAR\",\"decisionContract\":{}}"
        + "}}}}";
    Map<String, String> sources = new LinkedHashMap<>();
    sources.put(sourcePath, source);
    return StoryRepository.fromText(metadata, sources, decisions);
  }


  private static StoryRepository fixtureRepository() {
    String metadata = "{"
        + "\"schemaVersion\":1,"
        + "\"sourceRevision\":\"fixture-r1\","
        + "\"segmentTargetChars\":1900,"
        + "\"segmentMaxChars\":2400,"
        + "\"startChapter\":\"L0_C01\","
        + "\"chapters\":["
        + "{\"id\":\"L0_C01\",\"title\":\"Một\","
        + "\"source\":\"story/source/level_0/LEVEL0_CH01.md\","
        + "\"thread\":\"cao_minh\",\"visibility\":\"player\","
        + "\"nextChapter\":\"L0_C02\","
        + "\"eventsOnEnter\":[],"
        + "\"eventsOnExit\":[{\"type\":\"CHARACTER_REUNION\",\"characterId\":\"luc_tram\"}],"
        + "\"requiredFacts\":[],\"forbiddenClaims\":[]},"
        + "{\"id\":\"L0_C02\",\"title\":\"Hai\","
        + "\"source\":\"story/source/level_0/LEVEL0_CH02.md\","
        + "\"thread\":\"luc_tram\",\"visibility\":\"cutaway\","
        + "\"nextChapter\":\"\","
        + "\"eventsOnEnter\":[{\"type\":\"CHARACTER_PRESENT\",\"characterId\":\"nam\",\"scope\":\"story_local\"}],"
        + "\"eventsOnExit\":[{\"type\":\"LEVEL0_ARC_BOUNDARY_REACHED\"}],"
        + "\"requiredFacts\":[\"reader-only\"],"
        + "\"forbiddenClaims\":[\"not Cao Minh knowledge\"]}"
        + "]}";

    Map<String, String> sources = new LinkedHashMap<>();
    sources.put("story/source/level_0/LEVEL0_CH01.md",
        "# Level 0 — Chương 01: Một\n\nĐoạn authored thứ nhất.");
    sources.put("story/source/level_0/LEVEL0_CH02.md",
        "# Level 0 — Chương 02: Hai\n\nĐây là tuyến Lục Trầm.");
    return StoryRepository.fromText(metadata, sources);
  }

  @Test public void normalizeWithoutRepositoryKeepsContentAgnosticState() throws Exception {
    JSONObject state = state();
    StoryCore core = new StoryCore();

    core.normalizeState(state);

    JSONObject story = state.getJSONObject(StoryCore.ROOT_KEY);
    assertEquals(StoryCore.SCHEMA_VERSION, story.getInt("schemaVersion"));
    assertFalse(story.getBoolean("active"));
    assertEquals("", story.getString("currentChapter"));
    assertEquals("", story.getString("currentScene"));
    assertEquals(StoryCore.STATUS_UNSEEN, StoryCore.characterStatus(state, "luc_tram"));
  }

  @Test public void repositoryBindsFinalStyleStoryWithoutAdvancingOnFreeAction() throws Exception {
    JSONObject state = state();
    StoryCore core = StoryCore.withRepository(fixtureRepository());
    CharacterEncounterCore characterCore = new CharacterEncounterCore(bound -> bound - 1);

    core.normalizeState(state);
    JSONObject story = state.getJSONObject(StoryCore.ROOT_KEY);
    assertTrue(story.getBoolean("active"));
    assertEquals("L0_C01", story.getString("currentChapter"));
    assertTrue(core.ownsLevelProgression(state));

    assertNull(core.advanceAndRender(state, "Cao Minh quan sát xung quanh", characterCore));
    assertFalse(story.getBoolean("segmentDelivered"));
  }

  @Test public void authoredTurnEmitsExactSourceAndAppliesExitEvent() throws Exception {
    JSONObject state = state();
    StoryCore core = StoryCore.withRepository(fixtureRepository());
    CharacterEncounterCore characterCore = new CharacterEncounterCore(bound -> bound - 1);

    StoryCore.AuthoredTurn turn =
        core.advanceAndRender(state, StoryCore.ADVANCE_ACTION_VI, characterCore);

    assertEquals("L0_C01", turn.chapterId);
    assertEquals("cao_minh", turn.thread);
    assertEquals("player", turn.visibility);
    assertTrue(turn.reply.contains("Level 0 — Chương 01: Một"));
    assertTrue(turn.reply.contains("Đoạn authored thứ nhất."));
    assertEquals(StoryCore.STATUS_REUNITED, StoryCore.characterStatus(state, "luc_tram"));
    assertFalse(state.getJSONObject(StoryCore.ROOT_KEY).getBoolean("arcComplete"));
  }

  @Test public void cutawayIsReaderVisibleButEndsAtArcBoundaryWithoutLevelTransition() throws Exception {
    JSONObject state = state();
    StoryCore core = StoryCore.withRepository(fixtureRepository());
    CharacterEncounterCore characterCore = new CharacterEncounterCore(bound -> bound - 1);

    core.advanceAndRender(state, StoryCore.ADVANCE_ACTION_VI, characterCore);
    StoryCore.AuthoredTurn cutaway =
        core.advanceAndRender(state, StoryCore.ADVANCE_ACTION_VI, characterCore);

    assertEquals("L0_C02", cutaway.chapterId);
    assertEquals("luc_tram", cutaway.thread);
    assertEquals("cutaway", cutaway.visibility);

    JSONObject story = state.getJSONObject(StoryCore.ROOT_KEY);
    assertTrue(story.getBoolean("arcComplete"));
    assertTrue(story.getJSONObject("flags").getBoolean("level0_arc_boundary_reached"));
    assertEquals(0, state.getInt("currentLevel"));
    assertEquals("0", state.getString("currentLevelKey"));
    assertFalse(core.hasPendingAuthoredStory(state));

    JSONObject nam = story.getJSONObject("characters").getJSONObject("nam");
    assertEquals(StoryCore.PRESENCE_PRESENT, nam.getString("presence"));

    String prompt = core.promptContext(state);
    assertTrue(prompt.contains("NOT a validated Level 1 transition"));
  }

  @Test public void parallelStoryPresenceDoesNotAddLucTramToParty() throws Exception {
    JSONObject state = state();
    StoryCore core = new StoryCore();
    CharacterEncounterCore characterCore = new CharacterEncounterCore(bound -> bound - 1);

    core.applyCharacterEvent(
        state, "luc_tram", StoryCore.EVENT_CHARACTER_PARALLEL, characterCore);

    assertEquals(StoryCore.STATUS_PARALLEL, StoryCore.characterStatus(state, "luc_tram"));
    assertEquals(0, state.getJSONArray("party").length());
  }

  @Test public void reunionDoesNotAutoJoinParty() throws Exception {
    JSONObject state = state();
    StoryCore core = new StoryCore();
    CharacterEncounterCore characterCore = new CharacterEncounterCore(bound -> bound - 1);

    core.applyCharacterEvent(
        state, "luc_tram", StoryCore.EVENT_CHARACTER_REUNION, characterCore);

    assertEquals(StoryCore.STATUS_REUNITED, StoryCore.characterStatus(state, "luc_tram"));
    assertEquals(0, state.getJSONArray("party").length());
  }

  @Test public void partyJoinRequiresPriorAuthoredReunion() throws Exception {
    JSONObject state = state();
    StoryCore core = new StoryCore();
    CharacterEncounterCore characterCore = new CharacterEncounterCore(bound -> bound - 1);

    try {
      core.applyCharacterEvent(
          state, "luc_tram", StoryCore.EVENT_CHARACTER_JOIN_PARTY, characterCore);
      fail("Expected story join to require authored reunion");
    } catch (IllegalStateException expected) {
      assertTrue(expected.getMessage().contains("before authored reunion"));
    }
    assertEquals(0, state.getJSONArray("party").length());
  }

  @Test public void authoredJoinUsesCharacterCoreWithoutRandomEncounter() throws Exception {
    JSONObject state = state();
    StoryCore core = new StoryCore();
    CharacterEncounterCore characterCore = new CharacterEncounterCore(bound -> bound - 1);

    core.applyCharacterEvent(
        state, "luc_tram", StoryCore.EVENT_CHARACTER_REUNION, characterCore);
    core.applyCharacterEvent(
        state, "luc_tram", StoryCore.EVENT_CHARACTER_ACCOMPANY, characterCore);
    core.applyCharacterEvent(
        state, "luc_tram", StoryCore.EVENT_CHARACTER_JOIN_PARTY, characterCore);

    assertEquals(StoryCore.STATUS_PARTY_MEMBER, StoryCore.characterStatus(state, "luc_tram"));
    assertEquals(1, state.getJSONArray("party").length());
    assertEquals("luc_tram", state.getJSONArray("party").getJSONObject(0).getString("id"));
    assertEquals("Lục Trầm", state.getJSONArray("party").getJSONObject(0).getString("name"));
  }

  @Test public void promptMakesStoryOwnershipExplicitWithoutRepository() throws Exception {
    JSONObject state = state();
    StoryCore core = new StoryCore();

    String prompt = core.promptContext(state);

    assertTrue(prompt.contains("Story-managed character Lục Trầm"));
    assertTrue(prompt.contains("No authored manuscript chapter is currently bound"));
    assertTrue(prompt.contains("AI must not advance manuscript position"));
  }

  private static JSONObject preparedAlternates() throws Exception {
    return new JSONObject()
        .put("canon", new JSONObject()
            .put("text", "Bám theo dấu cũ rồi bước tiếp"))
        .put("trap", new JSONObject()
            .put("text", "Theo tiếng ù rẽ sang khoảng tối bên cạnh")
            .put("reply", "Tiếng ù kéo dài thêm một nhịp. Những vệt ố quen thuộc lại hiện ra trước mắt như thể khoảng hành lang vừa tự khép vòng."))
        .put("converge", new JSONObject()
            .put("text", "Đứng yên nghe thêm một nhịp trước khi di chuyển")
            .put("reply", "Cao Minh giữ nguyên vị trí thêm một nhịp. Tiếng ù vẫn đều, không cho hắn thêm dữ kiện chắc chắn."));
  }

  private static String choiceIdForOutcome(JSONObject state, String type) throws Exception {
    JSONObject pack = state.getJSONObject(StoryCore.ROOT_KEY).getJSONObject("decisionPackage");
    JSONObject outcomes = pack.getJSONObject("outcomes");
    java.util.Iterator<String> ids = outcomes.keys();
    while (ids.hasNext()) {
      String id = ids.next();
      if (type.equals(outcomes.getJSONObject(id).getString("type"))) return id;
    }
    return "";
  }

  @Test public void decisionPrefetchBuildsThreeOpaquePublicChoices() throws Exception {
    JSONObject state = state();
    StoryCore core = StoryCore.withRepository(decisionFixtureRepository());
    CharacterEncounterCore characterCore = new CharacterEncounterCore(bound -> bound - 1);

    StoryCore.AuthoredTurn turn =
        core.advanceAndRender(state, StoryCore.ADVANCE_ACTION_VI, characterCore);

    assertEquals(StoryRepository.MODE_DECISION, turn.mode);
    assertTrue(core.awaitingDecision(state));
    assertTrue(core.decisionNeedsPrefetch(state));
    assertTrue(core.blocksFreePlayerAction(state));

    JSONObject request = core.decisionPrefetchRequest(state, "(context)");
    assertTrue(request.getBoolean("needed"));
    String contextHash = request.getString("contextHash");
    assertTrue(request.getString("prompt").contains("COMPLETE three-choice package"));

    core.installDecisionPackage(state, contextHash, preparedAlternates());
    assertTrue(core.decisionReady(state));

    JSONArray publicChoices = core.decisionChoices(state);
    assertEquals(3, publicChoices.length());
    for (int i = 0; i < publicChoices.length(); i++) {
      JSONObject choice = publicChoices.getJSONObject(i);
      assertTrue(choice.has("id"));
      assertTrue(choice.has("text"));
      String publicId = choice.getString("id").toLowerCase(java.util.Locale.ROOT);
      assertTrue(publicId.startsWith("choice_"));
      assertFalse(publicId.contains("canon"));
      assertFalse(publicId.contains("trap"));
      assertFalse(publicId.contains("converge"));
      assertFalse(choice.has("type"));
      assertFalse(choice.has("reply"));
    }

    try {
      core.advanceAndRender(state, StoryCore.ADVANCE_ACTION_VI, characterCore);
      fail("Expected authored progression to remain blocked until a decision is resolved");
    } catch (IllegalStateException expected) {
      assertTrue(expected.getMessage().contains("must be resolved"));
    }
  }

  @Test public void canonDecisionQueuesNextAuthoredTurnUntilGateClears() throws Exception {
    JSONObject state = state();
    StoryCore core = StoryCore.withRepository(decisionFixtureRepository());
    CharacterEncounterCore characterCore = new CharacterEncounterCore(bound -> bound - 1);

    core.advanceAndRender(state, StoryCore.ADVANCE_ACTION_VI, characterCore);
    JSONObject request = core.decisionPrefetchRequest(state, "");
    core.installDecisionPackage(state, request.getString("contextHash"), preparedAlternates());

    String canonId = choiceIdForOutcome(state, StoryCore.OUTCOME_CANON);
    StoryCore.DecisionResolution resolution =
        core.resolveDecision(state, canonId, characterCore);

    assertEquals(StoryCore.OUTCOME_CANON, resolution.outcome);
    assertFalse(resolution.looped);
    assertEquals("", resolution.reply);
    assertFalse(core.awaitingDecision(state));
    assertTrue(core.hasPendingStoryAdvance(state));

    StoryCore.AuthoredTurn next = core.advancePendingTurn(state, characterCore);
    assertTrue(next.reply.contains("áp sát mép tường"));
    assertFalse(core.hasPendingStoryAdvance(state));
  }

  @Test public void convergeQueuesAuthoredBeatAfterPreparedReaction() throws Exception {
    JSONObject state = state();
    StoryCore core = StoryCore.withRepository(decisionFixtureRepository());
    CharacterEncounterCore characterCore = new CharacterEncounterCore(bound -> bound - 1);

    core.advanceAndRender(state, StoryCore.ADVANCE_ACTION_VI, characterCore);
    JSONObject request = core.decisionPrefetchRequest(state, "");
    core.installDecisionPackage(state, request.getString("contextHash"), preparedAlternates());

    String convergeId = choiceIdForOutcome(state, StoryCore.OUTCOME_CONVERGE);
    StoryCore.DecisionResolution resolution =
        core.resolveDecision(state, convergeId, characterCore);

    assertEquals(StoryCore.OUTCOME_CONVERGE, resolution.outcome);
    assertFalse(resolution.looped);
    assertTrue(resolution.reply.startsWith("Cao Minh giữ nguyên vị trí"));
    assertFalse(resolution.reply.contains("áp sát mép tường"));
    assertTrue(core.hasPendingStoryAdvance(state));

    StoryCore.AuthoredTurn next = core.advancePendingTurn(state, characterCore);
    assertTrue(next.reply.contains("áp sát mép tường"));
  }

  @Test public void trapUsesPreparedReactionAndReturnsToSameDecisionAnchor() throws Exception {
    JSONObject state = state();
    StoryCore core = StoryCore.withRepository(decisionFixtureRepository());
    CharacterEncounterCore characterCore = new CharacterEncounterCore(bound -> bound - 1);

    StoryCore.AuthoredTurn anchor =
        core.advanceAndRender(state, StoryCore.ADVANCE_ACTION_VI, characterCore);
    JSONObject request = core.decisionPrefetchRequest(state, "");
    core.installDecisionPackage(state, request.getString("contextHash"), preparedAlternates());

    String trapId = choiceIdForOutcome(state, StoryCore.OUTCOME_TRAP);
    StoryCore.DecisionResolution resolution =
        core.resolveDecision(state, trapId, characterCore);

    assertEquals(StoryCore.OUTCOME_TRAP, resolution.outcome);
    assertTrue(resolution.looped);
    assertTrue(resolution.reply.contains("tự khép vòng"));
    assertTrue(resolution.reply.contains(anchor.reply));
    assertTrue(core.awaitingDecision(state));
    assertTrue(core.decisionReady(state));
    assertEquals("L0_C01_P001",
        state.getJSONObject(StoryCore.ROOT_KEY).getString("currentScene"));
    assertEquals(1,
        state.getJSONObject(StoryCore.ROOT_KEY)
            .getJSONObject("loopHistory").getInt("L0_C01_P001"));
  }

  @Test public void stalePrefetchPackageIsRejectedByContextHash() throws Exception {
    JSONObject state = state();
    StoryCore core = StoryCore.withRepository(decisionFixtureRepository());
    CharacterEncounterCore characterCore = new CharacterEncounterCore(bound -> bound - 1);

    core.advanceAndRender(state, StoryCore.ADVANCE_ACTION_VI, characterCore);
    JSONObject request = core.decisionPrefetchRequest(state, "");
    state.put("inventory", new JSONArray().put(new JSONObject().put("id", "changed")));

    try {
      core.installDecisionPackage(state, request.getString("contextHash"), preparedAlternates());
      fail("Expected stale decision package to be rejected");
    } catch (IllegalStateException expected) {
      assertTrue(expected.getMessage().contains("context changed"));
    }
  }


  @Test public void authoredEntityGateExposesOnlyAttackAndBlocksNextTurn() throws Exception {
    JSONObject state = state();
    StoryCore core = StoryCore.withRepository(entityGateFixtureRepository());
    CharacterEncounterCore characterCore = new CharacterEncounterCore(bound -> bound - 1);

    StoryCore.AuthoredTurn turn =
        core.advanceAndRender(state, StoryCore.ADVANCE_ACTION_VI, characterCore);

    assertEquals(StoryRepository.MODE_ENTITY_GATE, turn.mode);
    assertTrue(core.awaitingEntityAttack(state));
    assertTrue(core.blocksFreePlayerAction(state));
    assertFalse(core.awaitingDecision(state));

    JSONObject choice = core.entityAttackChoice(state);
    assertEquals("story_attack", choice.getString("id"));
    assertEquals("Tấn công", choice.getString("text"));

    String entityKey = core.consumeEntityAttack(state);
    assertEquals("hound", entityKey);
    assertFalse(core.awaitingEntityAttack(state));
    assertTrue(core.hasPendingStoryAdvance(state));

    StoryCore.AuthoredTurn next = core.advancePendingTurn(state, characterCore);
    assertTrue(next.reply.contains("Sau trận chiến"));
    assertFalse(core.hasPendingStoryAdvance(state));
  }

}

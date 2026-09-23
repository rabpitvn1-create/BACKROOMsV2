package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class StoryCoreTest {
  private static JSONObject state() throws Exception {
    return new JSONObject()
        .put("currentLevel", 0)
        .put("currentLevelKey", "0")
        .put("turn", 1)
        .put("party", new JSONArray())
        .put("flags", new JSONObject());
  }

  private static StoryRepository interactiveFixtureRepository() {
    String sourcePath = "story/source/level_0/LEVEL0_CH01.md";
    String source = "# Level 0 — Chương 01: Tương tác\n\nCao Minh dừng trước hai lối đi.";
    String metadata = "{"
        + "\"schemaVersion\":1,"
        + "\"sourceRevision\":\"interactive-r1\","
        + "\"segmentTargetChars\":1900,"
        + "\"segmentMaxChars\":2400,"
        + "\"startChapter\":\"L0_C01\","
        + "\"chapters\":[{"
        + "\"id\":\"L0_C01\","
        + "\"title\":\"Tương tác\","
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
    String interactions = "{"
        + "\"schemaVersion\":1,"
        + "\"sourceRevision\":\"interactive-r1\","
        + "\"chapters\":{"
        + "\"L0_C01\":{"
        + "\"sourceDigest\":\"" + digest + "\","
        + "\"segments\":{"
        + "\"L0_C01_P001\":{"
        + "\"mode\":\"INTERACTIVE\","
        + "\"choices\":["
        + "{\"text\":\"Nhìn trái\",\"action\":\"Quan sát kỹ lối bên trái\"},"
        + "{\"text\":\"Nhìn phải\",\"action\":\"Quan sát kỹ lối bên phải\"},"
        + "{\"text\":\"Đứng nghe\",\"action\":\"Đứng yên lắng nghe trước khi đi tiếp\"}"
        + "],"
        + "\"interactionGuard\":\"Không rời khu vực và không quyết định lối đi thay cho đoạn authored tiếp theo.\""
        + "}}}}}";
    Map<String, String> sources = new LinkedHashMap<>();
    sources.put(sourcePath, source);
    return StoryRepository.fromText(metadata, sources, interactions);
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

  @Test public void compiledInteractionBlocksAdvanceUntilChoiceResponseFinishes() throws Exception {
    JSONObject state = state();
    StoryCore core = StoryCore.withRepository(interactiveFixtureRepository());
    CharacterEncounterCore characterCore = new CharacterEncounterCore(bound -> bound - 1);

    StoryCore.AuthoredTurn turn =
        core.advanceAndRender(state, StoryCore.ADVANCE_ACTION_VI, characterCore);

    assertEquals(StoryRepository.MODE_INTERACTIVE, turn.mode);
    assertEquals(3, turn.choices.length());
    assertTrue(core.awaitingInteraction(state));
    assertTrue(core.blocksFreePlayerAction(state));

    try {
      core.advanceAndRender(state, StoryCore.ADVANCE_ACTION_VI, characterCore);
      fail("Expected story advance to be blocked until compiled interaction is resolved");
    } catch (IllegalStateException expected) {
      assertTrue(expected.getMessage().contains("interaction choice"));
    }

    String action = turn.choices.getJSONObject(0).getString("action");
    assertTrue(core.isCompiledInteractionChoice(state, action));
    assertFalse(core.isCompiledInteractionChoice(state, "Tự ý bỏ đi"));
    assertTrue(core.consumeCompiledInteractionChoice(state, action));
    assertTrue(core.awaitingInteraction(state));

    String prompt = core.promptContext(state);
    assertTrue(prompt.contains("COMPILED STORY INTERACTION RESPONSE"));
    assertTrue(prompt.contains(action));
    assertTrue(prompt.contains("Không rời khu vực"));

    core.finishInteractionResponse(state);
    assertFalse(core.awaitingInteraction(state));
    String after = core.promptContext(state);
    assertFalse(after.contains("COMPILED STORY INTERACTION RESPONSE"));
  }

}

package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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

  @Test public void normalizeCreatesContentAgnosticSaveState() throws Exception {
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

  @Test public void oldSaveWithJoinedLucTramMigratesForwardWithoutRemoval() throws Exception {
    JSONObject state = state().put("party", new JSONArray()
        .put(new JSONObject()
            .put("id", "luc_tram")
            .put("name", "Lục Trầm")
            .put("joined", true)));
    StoryCore core = new StoryCore();

    core.normalizeState(state);

    assertEquals(StoryCore.STATUS_PARTY_MEMBER, StoryCore.characterStatus(state, "luc_tram"));
    assertEquals(1, state.getJSONArray("party").length());
  }

  @Test public void promptMakesStoryOwnershipExplicit() throws Exception {
    JSONObject state = state();
    StoryCore core = new StoryCore();

    String prompt = core.promptContext(state);

    assertTrue(prompt.contains("Story-managed character Lục Trầm"));
    assertTrue(prompt.contains("never spawn from random character rolls"));
    assertTrue(prompt.contains("No compiled manuscript scene is bound yet"));
  }
}

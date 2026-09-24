package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

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

  private static String outcomeId(JSONObject state, String type) throws Exception {
    JSONObject outcomes = state.getJSONObject("story").getJSONObject("decisionPackage")
        .getJSONObject("outcomes");
    java.util.Iterator<String> keys = outcomes.keys();
    while (keys.hasNext()) {
      String key = keys.next();
      if (type.equals(outcomes.getJSONObject(key).getString("type"))) return key;
    }
    throw new IllegalStateException("Missing " + type);
  }

  @Test public void wrongChoicesStayInCurrentArcAndConvergeOnceAfterSaveLoad() throws Exception {
    Path root = assets();
    StoryRepository repository = new StoryRepository(path ->
        Files.readString(root.resolve(path), StandardCharsets.UTF_8));
    StoryCore core = StoryCore.withRepository(repository);
    CharacterEncounterCore characters = new CharacterEncounterCore(bound -> bound - 1);

    for (String level : new String[]{"0", "0.1"}) {
      JSONObject state = new JSONObject().put("currentLevelKey", level)
          .put("currentLevel", 0).put("location", "deeper inside")
          .put("turn", 6).put("party", new JSONArray())
          .put("inventory", new JSONArray().put("existing"))
          .put("flags", new JSONObject().put("canonFlag", true))
          .put("levelRoute", new JSONObject().put("levelKey", level).put("streak", 4));
      core.normalizeState(state);
      for (int i = 0; i < ("0".equals(level) ? 3 : 2); i++) {
        core.advanceAndRender(state, StoryCore.ADVANCE_ACTION_VI, characters);
      }
      assertTrue(core.decisionReady(state));
      assertFalse(core.decisionNeedsPrefetch(state));
      JSONObject before = state.getJSONObject("story");
      String chapter = before.getString("currentChapter");
      String scene = before.getString("currentScene");
      int eventSequence = before.getInt("eventSequence");
      int index = before.getInt("currentSegmentIndex");
      Set<String> packages = new HashSet<>();
      String oldId = outcomeId(state, StoryCore.OUTCOME_TRAP);
      for (int attempt = 0; attempt < 6; attempt++) {
        JSONArray choices = core.decisionChoices(state);
        Set<String> texts = new java.util.TreeSet<>();
        for (int j = 0; j < choices.length(); j++) texts.add(choices.getJSONObject(j).getString("text"));
        assertEquals(3, texts.size());
        assertTrue("An earlier complete choice set was repeated", packages.add(texts.toString()));
        StoryCore.DecisionResolution wrong = core.resolveDecision(
            state, outcomeId(state, StoryCore.OUTCOME_TRAP), characters);
        assertTrue(wrong.looped);
        assertFalse(wrong.reply.contains("Ma Sơn. Không có đại chiến."));
        assertEquals(level, state.getString("currentLevelKey"));
        assertEquals(LevelCore.defaultLocation(level), state.getString("location"));
        assertEquals(chapter, state.getJSONObject("story").getString("currentChapter"));
        assertEquals(scene, state.getJSONObject("story").getString("currentScene"));
        assertEquals(index, state.getJSONObject("story").getInt("currentSegmentIndex"));
        assertEquals(eventSequence, state.getJSONObject("story").getInt("eventSequence"));
        assertEquals(4, state.getJSONObject("levelRoute").getInt("streak"));
        assertEquals("existing", state.getJSONArray("inventory").getString(0));
        assertTrue(state.getJSONObject("flags").getBoolean("canonFlag"));
        assertTrue(core.decisionReady(state));
        state = new JSONObject(state.toString());
        core.normalizeState(state);
      }
      try {
        core.resolveDecision(state, oldId, characters);
        fail("Stale callback must not commit after a loop");
      } catch (IllegalArgumentException expected) {
        assertTrue(expected.getMessage().contains("Unknown"));
      }
      core.resolveDecision(state, outcomeId(state, StoryCore.OUTCOME_CANON), characters);
      assertTrue(core.hasPendingStoryAdvance(state));
      StoryCore.AuthoredTurn next = core.advancePendingTurn(state, characters);
      assertNotNull(next);
      assertEquals(chapter, next.chapterId);
      assertEquals(index + 1, state.getJSONObject("story").getInt("currentSegmentIndex"));
      assertFalse(core.hasPendingStoryAdvance(state));
      assertEquals(eventSequence, state.getJSONObject("story").getInt("eventSequence"));
      try {
        core.resolveDecision(state, oldId, characters);
        fail("Committed decision must not trigger twice");
      } catch (IllegalStateException expected) {
        assertTrue(expected.getMessage().contains("not ready"));
      }
    }
  }

  @Test public void storyChoiceUiHasNoProviderPrefetchBridge() throws Exception {
    String ui = Files.readString(assets().resolve("gm-choice-ui.js"), StandardCharsets.UTF_8);
    Path activity = assets().resolve("../java/com/rabpit/backroom/MainActivity.java").normalize();
    String java = Files.readString(activity, StandardCharsets.UTF_8);
    assertFalse(ui.contains("Android.prefetchStoryDecision"));
    assertFalse(java.contains("void prefetchStoryDecision("));
    assertTrue(java.contains("gameCore.processStoryDecision(stateJson, choiceId)"));
  }
}

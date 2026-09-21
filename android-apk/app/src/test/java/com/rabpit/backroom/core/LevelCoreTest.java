package com.rabpit.backroom.core;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class LevelCoreTest {
  private static final String ROUTE_ACTION = "Cao Minh đi tiếp theo hành lang";

  private static final class SequenceRng implements LevelCore.IntRng {
    private final int[] values;
    private int index;

    SequenceRng(int... values) {
      this.values = values;
    }

    @Override public int nextInt(int bound) {
      if (values.length == 0) throw new IllegalStateException("No RNG values configured");
      int value = values[Math.min(index, values.length - 1)];
      index++;
      if (value < 0 || value >= bound) throw new IllegalStateException("Test RNG out of range");
      return value;
    }
  }

  private static JSONObject state(int turn, String location) throws Exception {
    return new JSONObject()
        .put("currentLevel", 0)
        .put("turn", turn)
        .put("location", location);
  }

  @Test public void structuredKnowledgeLoadsOnlyCurrentLevelBundle() throws Exception {
    String knowledge = new JSONObject()
        .put("schemaVersion", 2)
        .put("sectionOrder", new org.json.JSONArray()
            .put("identity").put("architecture").put("gmConstraints").put("variationPool"))
        .put("levels", new JSONObject()
            .put("0", new JSONObject()
                .put("name", "Level 0 — Test")
                .put("identity", new org.json.JSONArray().put("YELLOW_IDENTITY"))
                .put("architecture", new org.json.JSONArray().put("YELLOW_ARCH"))
                .put("gmConstraints", new org.json.JSONArray().put("YELLOW_RULE"))
                .put("variationPool", new org.json.JSONArray().put("YELLOW_VARIATION")))
            .put("0.1", new JSONObject()
                .put("name", "Level 0.1 — Test")
                .put("identity", new org.json.JSONArray().put("ZENITH_IDENTITY"))
                .put("architecture", new org.json.JSONArray().put("ZENITH_ARCH"))
                .put("gmConstraints", new org.json.JSONArray().put("ZENITH_RULE"))
                .put("variationPool", new org.json.JSONArray().put("ZENITH_VARIATION"))))
        .toString();

    LevelCore core = LevelCore.withKnowledge(knowledge, new SequenceRng(5));
    JSONObject state = state(1, "Level 0.1 / Zenith Station").put(LevelCore.LEVEL_KEY, "0.1");
    String prompt = core.promptContext(state);

    assertTrue(prompt.contains("LEVEL KNOWLEDGE BUNDLE:"));
    assertTrue(prompt.contains("ZENITH_IDENTITY"));
    assertTrue(prompt.contains("ZENITH_ARCH"));
    assertTrue(prompt.contains("GM CONSTRAINTS"));
    assertTrue(prompt.contains("VARIATION POOL"));
    assertFalse(prompt.contains("YELLOW_IDENTITY"));
    assertFalse(prompt.contains("YELLOW_VARIATION"));
  }

  @Test public void structuredKnowledgeRotatesLargeScenePoolsByTurn() throws Exception {
    org.json.JSONArray seeds = new org.json.JSONArray();
    for (int i = 0; i < 10; i++) seeds.put("SEED_" + i);

    String knowledge = new JSONObject()
        .put("schemaVersion", 2)
        .put("sectionOrder", new org.json.JSONArray().put("identity").put("sceneSeeds"))
        .put("levels", new JSONObject()
            .put("0", new JSONObject()
                .put("name", "Level 0")
                .put("identity", new org.json.JSONArray().put("STATIC_RULE"))
                .put("sceneSeeds", seeds)))
        .toString();

    LevelCore core = LevelCore.withKnowledge(knowledge, new SequenceRng(5));
    JSONObject turnOne = state(1, "Level 0 / Start");
    JSONObject turnTwo = state(2, "Level 0 / Start");

    String first = core.promptContext(turnOne);
    String second = core.promptContext(turnTwo);

    assertTrue(first.contains("STATIC_RULE"));
    assertTrue(second.contains("STATIC_RULE"));
    assertTrue(first.contains("SCENESEEDS") || first.contains("SCENE SEEDS"));
    assertFalse(first.equals(second));

    int firstSeedCount = 0;
    int secondSeedCount = 0;
    for (int i = 0; i < 10; i++) {
      if (first.contains("SEED_" + i)) firstSeedCount++;
      if (second.contains("SEED_" + i)) secondSeedCount++;
    }
    assertEquals(6, firstSeedCount);
    assertEquals(6, secondSeedCount);
  }

  @Test public void structuredKnowledgeRejectsLegacySchemaAsAuthoritativeBundle() throws Exception {
    String legacy = new JSONObject()
        .put("schemaVersion", 1)
        .put("levels", new JSONObject()
            .put("0", new JSONObject()
                .put("identity", new org.json.JSONArray().put("SHOULD_NOT_LOAD"))))
        .toString();

    LevelCore core = LevelCore.withKnowledge(legacy, new SequenceRng(5));
    String prompt = core.promptContext(state(1, "Level 0 / Start"));
    assertTrue(prompt.contains("Canon for Level 0"));
    assertFalse(prompt.contains("SHOULD_NOT_LOAD"));
  }

  @Test public void sublevelSnapshotsRotateThroughManifestAssets() throws Exception {
    JSONObject manifest = new JSONObject()
        .put("root", "level_snapshots/drive")
        .put("sublevelRoot", "level_snapshots/sublevels/level_0")
        .put("levels", new JSONObject()
            .put("0", new JSONObject()
                .put("visualType", "scene")
                .put("assets", new org.json.JSONArray().put("level_0/01.webp"))))
        .put("sublevels", new JSONObject()
            .put("0.1", new JSONObject()
                .put("visualType", "scene")
                .put("assets", new org.json.JSONArray()
                    .put("sublevel_0-1/03.webp")
                    .put("sublevel_0-1/04.webp"))));

    LevelCore core = new LevelCore(null, new SequenceRng(5));
    core.loadSnapshotManifestText(manifest.toString());

    JSONObject sublevel = state(1, "Level 0.1 / Zenith Station")
        .put(LevelCore.LEVEL_KEY, "0.1");

    JSONObject first = new JSONObject(core.snapshotDescriptor(sublevel));
    assertEquals(
        "file:///android_asset/level_snapshots/sublevels/level_0/sublevel_0-1/03.webp",
        first.getString("path"));

    sublevel.put("turn", 2);
    JSONObject second = new JSONObject(core.snapshotDescriptor(sublevel));
    assertEquals(
        "file:///android_asset/level_snapshots/sublevels/level_0/sublevel_0-1/04.webp",
        second.getString("path"));

    sublevel.put("turn", 3);
    JSONObject wrapped = new JSONObject(core.snapshotDescriptor(sublevel));
    assertEquals(first.getString("path"), wrapped.getString("path"));
  }

  @Test public void mainLevelSnapshotRotationRemainsUnchanged() throws Exception {
    JSONObject manifest = new JSONObject()
        .put("root", "level_snapshots/drive")
        .put("levels", new JSONObject()
            .put("0", new JSONObject()
                .put("visualType", "scene")
                .put("assets", new org.json.JSONArray()
                    .put("level_0/01.webp")
                    .put("level_0/02.webp"))));

    LevelCore core = new LevelCore(null, new SequenceRng(5));
    core.loadSnapshotManifestText(manifest.toString());

    JSONObject main = state(2, "Level 0 / Start");
    JSONObject descriptor = new JSONObject(core.snapshotDescriptor(main));
    assertEquals("file:///android_asset/level_snapshots/drive/level_0/02.webp", descriptor.getString("path"));
  }

  @Test public void routeRollUsesFiveFiftyFiveFortyDistributionBoundaries() throws Exception {
    LevelCore tripleCore = new LevelCore(null, new SequenceRng(4));
    JSONObject triple = state(1, "Level 0 / A");
    tripleCore.rollRouteForExplorerAction(triple, ROUTE_ACTION);
    assertEquals(3, triple.getJSONObject(LevelCore.ROUTE_STATE).getInt("streak"));
    assertEquals("SUCCESS", triple.getJSONObject(LevelCore.ROUTE_STATE).getString("lastResult"));

    LevelCore normalCore = new LevelCore(null, new SequenceRng(5));
    JSONObject normal = state(1, "Level 0 / A");
    normalCore.rollRouteForExplorerAction(normal, ROUTE_ACTION);
    assertEquals(1, normal.getJSONObject(LevelCore.ROUTE_STATE).getInt("streak"));

    LevelCore lastSuccessCore = new LevelCore(null, new SequenceRng(59));
    JSONObject lastSuccess = state(1, "Level 0 / A");
    lastSuccessCore.rollRouteForExplorerAction(lastSuccess, ROUTE_ACTION);
    assertEquals(1, lastSuccess.getJSONObject(LevelCore.ROUTE_STATE).getInt("streak"));

    LevelCore failCore = new LevelCore(null, new SequenceRng(60));
    JSONObject failed = state(1, "Level 0 / A");
    failCore.rollRouteForExplorerAction(failed, ROUTE_ACTION);
    assertEquals(0, failed.getJSONObject(LevelCore.ROUTE_STATE).getInt("streak"));
    assertEquals("RESET", failed.getJSONObject(LevelCore.ROUTE_STATE).getString("lastResult"));
  }

  @Test public void tenConsecutiveSuccessesUnlockExit() throws Exception {
    LevelCore core = new LevelCore(null, new SequenceRng(5));
    JSONObject state = state(1, "Level 0 / Start");

    for (int turn = 1; turn <= 10; turn++) {
      state.put("turn", turn);
      core.rollRouteForExplorerAction(state, ROUTE_ACTION);
    }

    JSONObject route = state.getJSONObject(LevelCore.ROUTE_STATE);
    assertEquals(LevelCore.ROUTE_REQUIRED_STREAK, route.getInt("streak"));
    assertTrue(route.getBoolean("exitAvailable"));
    assertEquals("EXIT_AVAILABLE", route.getString("lastResult"));
  }

  @Test public void tripleSuccessCountsAsThreeAndCanCompleteChain() throws Exception {
    LevelCore core = new LevelCore(null, new SequenceRng(5,5,5,5,5,5,5,4));
    JSONObject state = state(1, "Level 0 / Start");

    for (int turn = 1; turn <= 8; turn++) {
      state.put("turn", turn);
      core.rollRouteForExplorerAction(state, ROUTE_ACTION);
    }

    JSONObject route = state.getJSONObject(LevelCore.ROUTE_STATE);
    assertEquals(10, route.getInt("streak"));
    assertTrue(route.getBoolean("exitAvailable"));
    assertEquals("EXIT_AVAILABLE", route.getString("lastResult"));
  }

  @Test public void oneFailureResetsNineSuccessesToZero() throws Exception {
    LevelCore core = new LevelCore(null, new SequenceRng(5,5,5,5,5,5,5,5,5,60));
    JSONObject state = state(1, "Level 0 / Start");

    for (int turn = 1; turn <= 10; turn++) {
      state.put("turn", turn);
      core.rollRouteForExplorerAction(state, ROUTE_ACTION);
    }

    JSONObject route = state.getJSONObject(LevelCore.ROUTE_STATE);
    assertEquals(0, route.getInt("streak"));
    assertFalse(route.getBoolean("exitAvailable"));
    assertEquals("RESET", route.getString("lastResult"));
  }

  @Test public void sameTurnCannotReroll() throws Exception {
    LevelCore core = new LevelCore(null, new SequenceRng(5,60));
    JSONObject state = state(4, "Level 0 / Start");

    core.rollRouteForExplorerAction(state, ROUTE_ACTION);
    core.rollRouteForExplorerAction(state, ROUTE_ACTION);

    JSONObject route = state.getJSONObject(LevelCore.ROUTE_STATE);
    assertEquals(1, route.getInt("streak"));
    assertEquals("SUCCESS", route.getString("lastResult"));
  }

  @Test public void nonRouteActionDoesNotRoll() throws Exception {
    LevelCore core = new LevelCore(null, new SequenceRng(5));
    JSONObject state = state(1, "Level 0 / Start");

    core.rollRouteForExplorerAction(state, "Cao Minh nghỉ tại đây");

    JSONObject route = state.getJSONObject(LevelCore.ROUTE_STATE);
    assertEquals(0, route.getInt("streak"));
    assertEquals(-1, route.getInt("lastRollTurn"));
  }

  @Test public void transitionIsRejectedBeforeChainCompletes() throws Exception {
    LevelCore core = new LevelCore(null, new SequenceRng(5));
    JSONObject before = state(1, "Level 0 / Start");
    core.normalizeState(before);

    JSONObject candidate = new JSONObject(before.toString())
        .put("currentLevel", 0)
        .put(LevelCore.LEVEL_KEY, "0.1")
        .put("location", "Level 0.1 / Zenith Station");

    try {
      core.validateAndApplyTransition(before, candidate);
      fail("Expected locked transition to be rejected");
    } catch (IllegalArgumentException expected) {
      assertTrue(expected.getMessage().contains("locked"));
    }
  }

  @Test public void completedChainAllowsOnlyNextSublevelAndResetsRoute() throws Exception {
    LevelCore core = new LevelCore(null, new SequenceRng(5));
    JSONObject before = state(1, "Level 0 / Start");
    for (int turn = 1; turn <= 10; turn++) {
      before.put("turn", turn);
      core.rollRouteForExplorerAction(before, ROUTE_ACTION);
    }

    JSONObject candidate = new JSONObject(before.toString())
        .put("currentLevel", 0)
        .put(LevelCore.LEVEL_KEY, "0.1")
        .put("location", "Level 0.1 / Zenith Station");

    core.validateAndApplyTransition(before, candidate);

    assertEquals(0, candidate.getInt("currentLevel"));
    assertEquals("0.1", candidate.getString(LevelCore.LEVEL_KEY));
    JSONObject route = candidate.getJSONObject(LevelCore.ROUTE_STATE);
    assertEquals(0, route.getInt("level"));
    assertEquals("0.1", route.getString("levelKey"));
    assertEquals(0, route.getInt("streak"));
    assertFalse(route.getBoolean("exitAvailable"));
  }

  @Test public void completedLevelZeroCannotSkipDirectlyToLevelOne() throws Exception {
    LevelCore core = new LevelCore(null, new SequenceRng(5));
    JSONObject before = state(1, "Level 0 / Start");
    for (int turn = 1; turn <= 10; turn++) {
      before.put("turn", turn);
      core.rollRouteForExplorerAction(before, ROUTE_ACTION);
    }

    JSONObject candidate = new JSONObject(before.toString())
        .put("currentLevel", 1)
        .put(LevelCore.LEVEL_KEY, "1")
        .put("location", "Level 1 / Parking Zone");

    try {
      core.validateAndApplyTransition(before, candidate);
      fail("Expected Level 0 -> Level 1 skip to be rejected");
    } catch (IllegalArgumentException expected) {
      assertTrue(expected.getMessage().contains("Invalid Level transition"));
    }
  }

  @Test public void fullMandatorySublevelSequenceEndsAtLevelOne() throws Exception {
    LevelCore core = new LevelCore(null, new SequenceRng(5));
    String[] progression = LevelCore.levelZeroProgressionKeys();
    JSONObject current = state(1, LevelCore.defaultLocation(progression[0]));
    current.put(LevelCore.LEVEL_KEY, progression[0]);
    int turn = 1;

    for (int i = 0; i < progression.length - 1; i++) {
      for (int step = 0; step < LevelCore.ROUTE_REQUIRED_STREAK; step++) {
        current.put("turn", turn++);
        core.rollRouteForExplorerAction(current, ROUTE_ACTION);
      }

      String next = progression[i + 1];
      JSONObject candidate = new JSONObject(current.toString())
          .put("currentLevel", "1".equals(next) ? 1 : 0)
          .put(LevelCore.LEVEL_KEY, next)
          .put("location", LevelCore.defaultLocation(next));
      core.validateAndApplyTransition(current, candidate);
      assertEquals(next, candidate.getString(LevelCore.LEVEL_KEY));
      current = candidate;
    }

    assertEquals("1", current.getString(LevelCore.LEVEL_KEY));
    assertEquals(1, current.getInt("currentLevel"));
  }

  @Test public void levelZeroPointThreeIsNotPartOfTheGameRoute() throws Exception {
    LevelCore core = new LevelCore(null, new SequenceRng(5));
    JSONObject before = state(1, "Level 0.2 / Remodeled Mess")
        .put(LevelCore.LEVEL_KEY, "0.2");
    for (int turn = 1; turn <= 10; turn++) {
      before.put("turn", turn);
      core.rollRouteForExplorerAction(before, ROUTE_ACTION);
    }

    JSONObject candidate = new JSONObject(before.toString())
        .put("currentLevel", 0)
        .put(LevelCore.LEVEL_KEY, "0.3")
        .put("location", "Level 0.3 / The Icy Rooms");

    try {
      core.validateAndApplyTransition(before, candidate);
      fail("Level 0.3 must be rejected");
    } catch (IllegalArgumentException expected) {
      assertTrue(expected.getMessage().contains("Unsupported"));
    }
  }

  @Test public void failedRouteReturnsCandidateToChainOrigin() throws Exception {
    LevelCore core = new LevelCore(null, new SequenceRng(5,60));
    JSONObject before = state(1, "Level 0 / Origin");

    core.rollRouteForExplorerAction(before, ROUTE_ACTION);
    before.put("turn", 2);
    before.put("location", "Level 0 / Deeper corridor");
    core.rollRouteForExplorerAction(before, ROUTE_ACTION);

    JSONObject candidate = new JSONObject(before.toString())
        .put("location", "Level 0 / Invented next corridor");

    core.validateAndApplyTransition(before, candidate);

    assertEquals("Level 0 / Origin", candidate.getString("location"));
    assertEquals(0, candidate.getJSONObject(LevelCore.ROUTE_STATE).getInt("streak"));
  }

  @Test public void candidateCannotForgeHiddenRouteProgress() throws Exception {
    LevelCore core = new LevelCore(null, new SequenceRng(5));
    JSONObject before = state(1, "Level 0 / Start");
    core.normalizeState(before);

    JSONObject forgedRoute = new JSONObject()
        .put("level", 0)
        .put("streak", 10)
        .put("exitAvailable", true);
    JSONObject candidate = new JSONObject(before.toString())
        .put(LevelCore.ROUTE_STATE, forgedRoute)
        .put("currentLevel", 0);

    core.validateAndApplyTransition(before, candidate);

    JSONObject protectedRoute = candidate.getJSONObject(LevelCore.ROUTE_STATE);
    assertEquals(0, protectedRoute.getInt("streak"));
    assertFalse(protectedRoute.getBoolean("exitAvailable"));
  }
  @Test public void stageIndexFollowsProgressionGraphNotDecimalNames() throws Exception {
    String[] keys = {"0","0.1","0.2","0.5","0.7","manila_room","the_torment","red_rooms","1","2","3","4","5","6"};
    for (int i = 0; i < keys.length; i++) {
      assertEquals(keys[i], i, LevelCore.stageIndexForKey(keys[i]));
    }
    assertEquals(4, LevelCore.stageIndexForKey("0.7"));
    assertEquals(8, LevelCore.stageIndexForKey("1"));
    assertEquals(13, LevelCore.stageIndexForKey("6"));
  }

}

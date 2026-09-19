package com.rabpit.backroom.core;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class LevelCoreTest {
  private static final String ROUTE_ACTION = "Kai đi tiếp theo hành lang";

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

  @Test public void routeRollUsesExactSixtyFortyBoundary() throws Exception {
    LevelCore successCore = new LevelCore(null, new SequenceRng(59));
    JSONObject success = state(1, "Level 0 / A");
    successCore.rollRouteForExplorerAction(success, ROUTE_ACTION);
    assertEquals(1, success.getJSONObject(LevelCore.ROUTE_STATE).getInt("streak"));

    LevelCore failCore = new LevelCore(null, new SequenceRng(60));
    JSONObject failed = state(1, "Level 0 / A");
    failCore.rollRouteForExplorerAction(failed, ROUTE_ACTION);
    assertEquals(0, failed.getJSONObject(LevelCore.ROUTE_STATE).getInt("streak"));
    assertEquals("RESET", failed.getJSONObject(LevelCore.ROUTE_STATE).getString("lastResult"));
  }

  @Test public void tenConsecutiveSuccessesUnlockExit() throws Exception {
    LevelCore core = new LevelCore(null, new SequenceRng(0));
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

  @Test public void oneFailureResetsNineSuccessesToZero() throws Exception {
    LevelCore core = new LevelCore(null, new SequenceRng(0,0,0,0,0,0,0,0,0,99));
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
    LevelCore core = new LevelCore(null, new SequenceRng(0,99));
    JSONObject state = state(4, "Level 0 / Start");

    core.rollRouteForExplorerAction(state, ROUTE_ACTION);
    core.rollRouteForExplorerAction(state, ROUTE_ACTION);

    JSONObject route = state.getJSONObject(LevelCore.ROUTE_STATE);
    assertEquals(1, route.getInt("streak"));
    assertEquals("SUCCESS", route.getString("lastResult"));
  }

  @Test public void nonRouteActionDoesNotRoll() throws Exception {
    LevelCore core = new LevelCore(null, new SequenceRng(0));
    JSONObject state = state(1, "Level 0 / Start");

    core.rollRouteForExplorerAction(state, "Kai nghỉ tại đây");

    JSONObject route = state.getJSONObject(LevelCore.ROUTE_STATE);
    assertEquals(0, route.getInt("streak"));
    assertEquals(-1, route.getInt("lastRollTurn"));
  }

  @Test public void transitionIsRejectedBeforeChainCompletes() throws Exception {
    LevelCore core = new LevelCore(null, new SequenceRng(0));
    JSONObject before = state(1, "Level 0 / Start");
    core.normalizeState(before);

    JSONObject candidate = new JSONObject(before.toString())
        .put("currentLevel", 1)
        .put("location", "Level 1 / Parking Zone");

    try {
      core.validateAndApplyTransition(before, candidate);
      fail("Expected locked transition to be rejected");
    } catch (IllegalArgumentException expected) {
      assertTrue(expected.getMessage().contains("locked"));
    }
  }

  @Test public void completedChainAllowsAdjacentTransitionAndResetsRoute() throws Exception {
    LevelCore core = new LevelCore(null, new SequenceRng(0));
    JSONObject before = state(1, "Level 0 / Start");
    for (int turn = 1; turn <= 10; turn++) {
      before.put("turn", turn);
      core.rollRouteForExplorerAction(before, ROUTE_ACTION);
    }

    JSONObject candidate = new JSONObject(before.toString())
        .put("currentLevel", 1)
        .put("location", "Level 1 / Parking Zone");

    core.validateAndApplyTransition(before, candidate);

    assertEquals(1, candidate.getInt("currentLevel"));
    JSONObject route = candidate.getJSONObject(LevelCore.ROUTE_STATE);
    assertEquals(1, route.getInt("level"));
    assertEquals(0, route.getInt("streak"));
    assertFalse(route.getBoolean("exitAvailable"));
  }

  @Test public void failedRouteReturnsCandidateToChainOrigin() throws Exception {
    LevelCore core = new LevelCore(null, new SequenceRng(0,99));
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
    LevelCore core = new LevelCore(null, new SequenceRng(0));
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
}

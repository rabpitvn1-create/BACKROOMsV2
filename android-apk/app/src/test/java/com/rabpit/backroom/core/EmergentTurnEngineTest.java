package com.rabpit.backroom.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class EmergentTurnEngineTest {
  @Test public void scopedRngIsDeterministicAndIndependent() {
    TurnRng first = new TurnRng("turn-x", 7, "canon-v1", "rng-v1");
    int playerA = first.nextInt(TurnRng.Scope.PLAYER_ACTION, 1000);
    int candidateA = first.nextInt(TurnRng.Scope.CANDIDATE_SELECTION, 1000);

    TurnRng second = new TurnRng("turn-x", 7, "canon-v1", "rng-v1");
    second.nextInt(TurnRng.Scope.PLAYER_ACTION, 1000);
    second.nextInt(TurnRng.Scope.PLAYER_ACTION, 1000);
    int candidateB = second.nextInt(TurnRng.Scope.CANDIDATE_SELECTION, 1000);

    assertEquals(candidateA, candidateB);
    assertEquals(playerA, new TurnRng("turn-x", 7, "canon-v1", "rng-v1")
        .nextInt(TurnRng.Scope.PLAYER_ACTION, 1000));
  }

  @Test public void noneSelectionDoesNotCreateCooldown() throws Exception {
    EmergentTurnEngine engine = new EmergentTurnEngine();
    JSONObject state = new JSONObject().put("turn", 2);
    engine.normalizeState(state);

    TurnRng rng = new TurnRng("turn-none", 0,
        EmergentTurnEngine.CANON_VERSION, EmergentTurnEngine.RNG_SCHEMA_VERSION);
    JSONObject selected = engine.selectCandidate(state, new JSONArray(), rng, 2);
    assertTrue(selected.getBoolean("selectedNone"));

    engine.commitAuthoritative(state, "turn-none", new JSONArray(), selected);
    engine.catchUpProjections(state);

    JSONObject index = state.getJSONObject(EmergentTurnEngine.ROOT_KEY).getJSONObject("selectionIndex");
    assertFalse(index.has("NONE"));
  }

  @Test public void committedEventCreatesFactThreadAndReplayableCooldownProjection() throws Exception {
    EmergentTurnEngine engine = new EmergentTurnEngine();
    JSONObject state = new JSONObject().put("turn", 4);
    engine.normalizeState(state);
    String turnId = engine.nextTurnId(state, "đi tiếp");

    JSONArray effects = new JSONArray().put(engine.threadEffect(
        "ENTITY_ENCOUNTER", new JSONArray().put("hound"), "SEED_OR_ADVANCE", null));
    JSONArray events = new JSONArray();
    events.put(engine.event(turnId, events, "ENTITY_ENCOUNTER_STARTED", "LOCAL", "hound",
        new JSONObject().put("observedByPlayer", true).put("causedBy", "world"), effects));

    JSONObject selection = engine.candidate(
        "ENTITY", "entity:hound", "DANGER", 3.0d, "Hound xuất hiện.", "hound", true)
        .put("selectedNone", false);

    engine.commitAuthoritative(state, turnId, events, selection);
    assertFalse(engine.selectionProjectionFresh(state));
    assertTrue(engine.catchUpProjections(state));
    assertTrue(engine.selectionProjectionFresh(state));

    JSONObject root = state.getJSONObject(EmergentTurnEngine.ROOT_KEY);
    assertEquals(1, root.getJSONArray("historicalFacts").length());
    assertEquals("ACTIVE", root.getJSONArray("threads").getJSONObject(0).getString("status"));
    assertEquals(4, root.getJSONObject("selectionIndex").getJSONObject("entity:hound")
        .getInt("lastSelectedTurn"));
  }

  @Test public void differentScopesDoNotShareCounters() {
    TurnRng rng = new TurnRng("turn-y", 1, "canon", "rng");
    rng.nextInt(TurnRng.Scope.PLAYER_ACTION, 10);
    rng.nextInt(TurnRng.Scope.PLAYER_ACTION, 10);
    assertEquals(2, rng.drawsUsed(TurnRng.Scope.PLAYER_ACTION));
    assertEquals(0, rng.drawsUsed(TurnRng.Scope.CANDIDATE_SELECTION));
    assertNotEquals(rng.drawKey(TurnRng.Scope.PLAYER_ACTION, 0),
        rng.drawKey(TurnRng.Scope.CANDIDATE_SELECTION, 0));
  }
}

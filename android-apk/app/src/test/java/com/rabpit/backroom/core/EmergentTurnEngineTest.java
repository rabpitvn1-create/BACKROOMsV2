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

  @Test public void authoritativePatchReplaysCommittedRootsAndExcludesRuntimeNarration() throws Exception {
    JSONObject before = new JSONObject()
        .put("turn", 2)
        .put("location", "A")
        .put("inventory", new JSONArray())
        .put("log", new JSONArray().put("old"))
        .put(EmergentTurnEngine.ROOT_KEY, new JSONObject().put("internal", true));
    JSONObject after = new JSONObject(before.toString())
        .put("turn", 3)
        .put("location", "B")
        .put("inventory", new JSONArray().put(new JSONObject().put("id", "bandage")))
        .put("log", new JSONArray().put("new"));

    JSONObject patch = AuthoritativeStatePatch.diff(before, after);
    JSONObject replayed = AuthoritativeStatePatch.apply(before, patch);

    assertEquals(3, replayed.getInt("turn"));
    assertEquals("B", replayed.getString("location"));
    assertEquals(1, replayed.getJSONArray("inventory").length());
    assertEquals("old", replayed.getJSONArray("log").getString(0));
    assertFalse(patch.getJSONObject("set").has(EmergentTurnEngine.ROOT_KEY));
  }

  @Test public void structuredThreadPredicateResolvesOnlyWhenStateMatches() throws Exception {
    JSONArray conditions = ThreadPredicateEngine.conditionsForThreadType(
        "CHEST_AVAILABLE", new JSONArray().put("0"));
    JSONObject blocked = new JSONObject().put("flags", new JSONObject().put("chestPresent", true));
    JSONObject resolved = new JSONObject().put("flags", new JSONObject().put("chestPresent", false));

    assertFalse(ThreadPredicateEngine.allSatisfied(blocked, conditions));
    assertTrue(ThreadPredicateEngine.allSatisfied(resolved, conditions));
  }

  @Test public void dueScheduledCandidateIsMandatoryAndCannotLoseToNone() throws Exception {
    EmergentTurnEngine engine = new EmergentTurnEngine();
    JSONObject state = new JSONObject().put("turn", 8);
    engine.normalizeState(state);
    JSONObject root = state.getJSONObject(EmergentTurnEngine.ROOT_KEY);
    JSONObject scheduledCandidate = engine.candidate(
        "TEST", "scheduled:test", "ENVIRONMENTAL", 1.0d, "scheduled", "x", false);
    root.getJSONObject("scheduler").getJSONArray("pending").put(new JSONObject()
        .put("triggerId", "t1")
        .put("dueTurn", 8)
        .put("situationKey", "scheduled:test")
        .put("candidate", scheduledCandidate)
        .put("fired", false));

    JSONArray due = engine.schedulerCandidates(state, 8);
    assertEquals(1, due.length());
    assertTrue(due.getJSONObject(0).getBoolean("mandatory"));

    JSONObject selected = engine.selectCandidate(
        state, due, new TurnRng("turn-scheduled", 0,
            EmergentTurnEngine.CANON_VERSION, EmergentTurnEngine.RNG_SCHEMA_VERSION), 8);
    assertEquals("scheduled:test", selected.getString("situationKey"));
    assertFalse(selected.getBoolean("selectedNone"));
  }

  @Test public void commitRecordPinsVersionsAndStoresReplayableDelta() throws Exception {
    EmergentTurnEngine engine = new EmergentTurnEngine();
    JSONObject before = new JSONObject().put("turn", 2).put("location", "A");
    engine.normalizeState(before);
    JSONObject after = new JSONObject(before.toString()).put("location", "B");
    JSONArray events = new JSONArray();
    String turnId = engine.nextTurnId(before, "move");
    events.put(engine.event(turnId, events, "TEST_MOVED", "LOCAL", "cao_minh",
        new JSONObject().put("factPredicate", "moved").put("factValue", "B")
            .put("causedBy", "player").put("observedByPlayer", true), null));

    engine.commitAuthoritative(before, after, turnId, events, null);

    JSONObject commit = after.getJSONObject(EmergentTurnEngine.ROOT_KEY)
        .getJSONArray("commitLog").getJSONObject(0);
    assertEquals(EmergentTurnEngine.CANON_VERSION, commit.getString("canonVersion"));
    assertEquals(EmergentTurnEngine.RNG_SCHEMA_VERSION, commit.getString("rngSchemaVersion"));
    assertEquals(EmergentTurnEngine.RESOLVER_VERSION, commit.getString("resolverVersion"));
    assertEquals("B", commit.getJSONObject("stateDelta").getJSONObject("set").getString("location"));
  }

}

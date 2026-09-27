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


  @Test public void narrativeSkeletonDefaultsToReadOnlyNonPlotContract() throws Exception {
    EmergentTurnEngine engine = new EmergentTurnEngine();
    JSONObject state = new JSONObject().put("turn", 1);
    engine.normalizeState(state);

    JSONObject root = state.getJSONObject(EmergentTurnEngine.ROOT_KEY);
    JSONObject skeleton = root.getJSONObject(NarrativeSkeleton.ROOT_KEY);
    assertTrue(NarrativeSkeleton.contractValid(skeleton));
    assertTrue(root.has(CampaignSkeleton.ROOT_KEY));
    assertFalse(root.has("skeleton"));
    assertTrue(skeleton.has("campaignIdentity"));
    assertTrue(skeleton.has("longTermTensions"));
    assertTrue(skeleton.has("anchorMysteries"));
    assertTrue(skeleton.has("importantRelationships"));
    assertTrue(skeleton.has("unresolvedWorldQuestions"));
    assertTrue(skeleton.has("convergenceConditions"));
    assertTrue(skeleton.has("endingPossibilities"));
    assertTrue(skeleton.has("attentionHints"));
    assertFalse(skeleton.has("nextEvent"));
    assertFalse(skeleton.has("phaseGate"));
    assertFalse(skeleton.has("plotCursor"));
    assertFalse(skeleton.has("spawnSchedule"));
  }

  @Test public void campaignSkeletonMigratesSeparatelyFromNarrativeSkeleton() throws Exception {
    EmergentTurnEngine engine = new EmergentTurnEngine();
    JSONObject state = new JSONObject().put("turn", 7);
    engine.normalizeState(state);
    JSONObject root = state.getJSONObject(EmergentTurnEngine.ROOT_KEY);

    JSONObject legacy = new JSONObject(root.getJSONObject(CampaignSkeleton.ROOT_KEY).toString());
    legacy.getJSONObject("axes").getJSONObject("entity_attention").put("score", 0.42d);
    root.remove(CampaignSkeleton.ROOT_KEY);
    root.remove(NarrativeSkeleton.ROOT_KEY);
    root.put("skeleton", legacy);
    root.getJSONObject("projectionWatermarks")
        .remove("campaignSkeleton")
        .remove("narrativeSkeleton");

    engine.normalizeState(state);

    JSONObject migrated = root.getJSONObject(CampaignSkeleton.ROOT_KEY);
    assertEquals(0.42d,
        migrated.getJSONObject("axes").getJSONObject("entity_attention").getDouble("score"), 0.000001d);
    assertTrue(root.has(NarrativeSkeleton.ROOT_KEY));
    assertFalse(root.has("skeleton"));
  }

  @Test public void narrativeSkeletonCarriesReadableCommittedContinuity() throws Exception {
    EmergentTurnEngine engine = new EmergentTurnEngine();
    JSONObject state = new JSONObject()
        .put("turn", 12)
        .put("currentLevel", 1)
        .put("currentLevelKey", "1")
        .put("location", "Level 1")
        .put("player", new JSONObject().put("name", "Cao Minh"))
        .put("party", new JSONArray().put(
            new JSONObject().put("id", "luc_tram").put("name", "Lục Trầm")));
    engine.normalizeState(state);
    String turnId = engine.nextTurnId(state, "continuity");

    JSONArray events = new JSONArray();
    events.put(engine.event(turnId, events, "CHARACTER_REUNION", "SOCIAL", "luc_tram",
        new JSONObject().put("factPredicate", "character_reunion")
            .put("factValue", "luc_tram").put("observedByPlayer", true),
        new JSONArray().put(engine.threadEffect(
            "LUC_TRAM_RELATIONSHIP", new JSONArray().put("luc_tram"), "SEED_OR_ADVANCE", null))));
    events.put(engine.event(turnId, events, "ROUTE_SEARCH_PROGRESS", "LOCAL", "1",
        new JSONObject().put("factPredicate", "route_search_result")
            .put("factValue", "PROGRESS").put("observedByPlayer", true),
        new JSONArray().put(engine.threadEffect(
            "LEVEL_ROUTE_SEARCH", new JSONArray().put("1"), "SEED_OR_ADVANCE", null))));

    engine.commitAuthoritative(state, turnId, events, null);
    engine.catchUpProjections(state);

    JSONObject skeleton = state.getJSONObject(EmergentTurnEngine.ROOT_KEY)
        .getJSONObject(NarrativeSkeleton.ROOT_KEY);
    assertTrue(skeleton.getJSONArray("longTermTensions").toString().contains("summary"));
    assertTrue(skeleton.getJSONArray("anchorMysteries").toString().contains("Level 1"));
    assertTrue(skeleton.getJSONArray("importantRelationships").toString().contains("Lục Trầm"));
    assertTrue(skeleton.getJSONArray("unresolvedWorldQuestions").toString().contains("question"));
    assertTrue(skeleton.getJSONArray("attentionHints").toString().contains("keyRefs"));
  }

  @Test public void narrativeSkeletonWeightsOnlyEligibleCandidatesWithMatchingKeyRefs() throws Exception {
    EmergentTurnEngine engine = new EmergentTurnEngine();
    JSONObject state = new JSONObject().put("turn", 9);
    engine.normalizeState(state);
    String turnId = engine.nextTurnId(state, "hound continuity");

    JSONArray events = new JSONArray();
    events.put(engine.event(turnId, events, "ENTITY_ENCOUNTER_STARTED", "LOCAL", "hound",
        new JSONObject().put("factPredicate", "entity_encounter_started")
            .put("factValue", "hound").put("observedByPlayer", true),
        new JSONArray().put(engine.threadEffect(
            "ENTITY_ENCOUNTER", new JSONArray().put("hound"), "SEED_OR_ADVANCE", null))));
    engine.commitAuthoritative(state, turnId, events, null);
    engine.catchUpProjections(state);

    JSONArray candidates = new JSONArray()
        .put(engine.candidate("ENTITY", "entity:hound", "DANGER", 10.0d,
            "hound", "hound", false))
        .put(engine.candidate("ENTITY", "entity:smiler", "DANGER", 10.0d,
            "smiler", "smiler", false));
    engine.selectCandidate(state, candidates,
        new TurnRng("turn-keyref", 0,
            EmergentTurnEngine.CANON_VERSION, EmergentTurnEngine.RNG_SCHEMA_VERSION), 9);

    JSONObject root = state.getJSONObject(EmergentTurnEngine.ROOT_KEY);
    JSONArray traced = root.getJSONArray("selectionTrace")
        .getJSONObject(root.getJSONArray("selectionTrace").length() - 1)
        .getJSONArray("candidates");
    JSONObject hound = null;
    JSONObject smiler = null;
    for (int i = 0; i < traced.length(); i++) {
      JSONObject item = traced.optJSONObject(i);
      if (item == null) continue;
      if ("entity:hound".equals(item.optString("situationKey"))) hound = item;
      if ("entity:smiler".equals(item.optString("situationKey"))) smiler = item;
    }
    assertTrue(hound != null && smiler != null);
    assertTrue(hound.getDouble("keyRefWeightModifier") > 1.0d);
    assertTrue(hound.getDouble("keyRefWeightModifier") <= 1.35d);
    assertEquals(1.0d, smiler.getDouble("keyRefWeightModifier"), 0.000001d);
    assertTrue(hound.getDouble("finalWeight") > smiler.getDouble("finalWeight"));

    JSONObject noEligible = engine.selectCandidate(
        state, new JSONArray(), new TurnRng("turn-keyref-none", 0,
            EmergentTurnEngine.CANON_VERSION, EmergentTurnEngine.RNG_SCHEMA_VERSION), 9);
    assertTrue(noEligible.getBoolean("selectedNone"));
  }

  @Test public void narrativeSkeletonRebuildIgnoresSelectionSchedulerAndAiProposal() throws Exception {
    EmergentTurnEngine engine = new EmergentTurnEngine();
    JSONObject state = new JSONObject().put("turn", 10);
    engine.normalizeState(state);
    String turnId = engine.nextTurnId(state, "test committed projection");

    JSONArray effects = new JSONArray().put(engine.threadEffect(
        "ENTITY_ENCOUNTER", new JSONArray().put("hound"), "SEED_OR_ADVANCE", null));
    JSONArray events = new JSONArray();
    events.put(engine.event(turnId, events, "ENTITY_ENCOUNTER_STARTED", "LOCAL", "hound",
        new JSONObject().put("observedByPlayer", true).put("causedBy", "world"), effects));

    JSONObject selected = engine.candidate(
        "ENTITY", "entity:hound", "DANGER", 3.0d, "SELECTION_LEAK_SENTINEL", "hound", true)
        .put("selectedNone", false)
        .put("worldProposal", new JSONObject().put("actionType", "AI_PROPOSAL_LEAK_SENTINEL"));
    engine.commitAuthoritative(state, turnId, events, selected);

    JSONObject root = state.getJSONObject(EmergentTurnEngine.ROOT_KEY);
    root.put("lastSelection", new JSONObject(selected.toString()));
    root.getJSONObject("scheduler").getJSONArray("pending").put(new JSONObject()
        .put("triggerId", "leak-test")
        .put("dueTurn", 11)
        .put("situationKey", "scheduled:leak")
        .put("candidate", new JSONObject().put("publicSummary", "SCHEDULER_LEAK_SENTINEL"))
        .put("fired", false));

    int factCount = root.getJSONArray("historicalFacts").length();
    int threadCount = root.getJSONArray("threads").length();
    engine.catchUpProjections(state);

    String serialized = root.getJSONObject(NarrativeSkeleton.ROOT_KEY).toString();
    assertFalse(serialized.contains("SELECTION_LEAK_SENTINEL"));
    assertFalse(serialized.contains("AI_PROPOSAL_LEAK_SENTINEL"));
    assertFalse(serialized.contains("SCHEDULER_LEAK_SENTINEL"));
    assertEquals(factCount, root.getJSONArray("historicalFacts").length());
    assertEquals(threadCount, root.getJSONArray("threads").length());
    assertTrue(NarrativeSkeleton.attentionStrength(root, "DANGER") > 0.0d);
  }

  @Test public void narrativeSkeletonCannotCreateOrUnlockCandidates() throws Exception {
    EmergentTurnEngine engine = new EmergentTurnEngine();
    JSONObject state = new JSONObject().put("turn", 6);
    engine.normalizeState(state);
    String turnId = engine.nextTurnId(state, "seed tension");

    JSONArray effects = new JSONArray().put(engine.threadEffect(
        "ENTITY_ENCOUNTER", new JSONArray().put("hound"), "SEED_OR_ADVANCE", null));
    JSONArray events = new JSONArray();
    events.put(engine.event(turnId, events, "ENTITY_ENCOUNTER_STARTED", "LOCAL", "hound",
        new JSONObject().put("observedByPlayer", true), effects));
    engine.commitAuthoritative(state, turnId, events, null);
    engine.catchUpProjections(state);

    JSONObject root = state.getJSONObject(EmergentTurnEngine.ROOT_KEY);
    assertTrue(NarrativeSkeleton.attentionStrength(root, "DANGER") > 0.0d);
    JSONObject director = root.getJSONObject("director");
    assertEquals("WEIGHT_ONLY", director.getString("authority"));
    assertFalse(director.getBoolean("canCreateCandidates"));
    assertFalse(director.getBoolean("canUnlockEligibility"));
    assertEquals(0, engine.schedulerCandidates(state, 6).length());

    JSONObject selected = engine.selectCandidate(
        state, new JSONArray(), new TurnRng("turn-no-eligible", 0,
            EmergentTurnEngine.CANON_VERSION, EmergentTurnEngine.RNG_SCHEMA_VERSION), 6);
    assertTrue(selected.getBoolean("selectedNone"));
  }

  @Test public void staleNarrativeSkeletonRebuildDropsStoryCoreStylePlanningFields() throws Exception {
    EmergentTurnEngine engine = new EmergentTurnEngine();
    JSONObject state = new JSONObject().put("turn", 3);
    engine.normalizeState(state);
    String turnId = engine.nextTurnId(state, "commit one fact");
    JSONArray events = new JSONArray();
    events.put(engine.event(turnId, events, "PLAYER_ACTION_RESOLVED", "LOCAL", "cao_minh",
        new JSONObject().put("factPredicate", "player_action")
            .put("factValue", "observe").put("causedBy", "player")
            .put("impactEligible", false).put("observedByPlayer", true), null));
    engine.commitAuthoritative(state, turnId, events, null);
    engine.catchUpProjections(state);

    JSONObject root = state.getJSONObject(EmergentTurnEngine.ROOT_KEY);
    root.getJSONObject(NarrativeSkeleton.ROOT_KEY)
        .put("phaseGate", new JSONObject().put("phaseLabel", "ACT_2").put("nextEvent", "forced"));
    root.getJSONObject("projectionWatermarks").put("narrativeSkeleton", 0);

    assertTrue(engine.catchUpProjections(state));
    JSONObject rebuilt = root.getJSONObject(NarrativeSkeleton.ROOT_KEY);
    assertTrue(NarrativeSkeleton.contractValid(rebuilt));
    assertFalse(rebuilt.has("phaseGate"));
    assertEquals(root.getInt("commitSequence"), rebuilt.getInt("derivedFromCommitSeq"));
    assertEquals(root.getInt("commitSequence"),
        root.getJSONObject("projectionWatermarks").getInt("narrativeSkeleton"));
  }

  @Test public void directorAppliesDangerCooldownAfterCombatTerminalEvent() throws Exception {
    EmergentTurnEngine engine = new EmergentTurnEngine();
    JSONObject state = new JSONObject().put("turn", 20);
    engine.normalizeState(state);
    String turnId = engine.nextTurnId(state, "victory");
    JSONArray events = new JSONArray();
    events.put(engine.event(turnId, events, "COMBAT_VICTORY", "LOCAL", "hound",
        new JSONObject().put("observedByPlayer", true), null));

    engine.commitAuthoritative(state, turnId, events, null);
    engine.catchUpProjections(state);

    JSONObject root = state.getJSONObject(EmergentTurnEngine.ROOT_KEY);
    JSONObject director = root.getJSONObject("director");
    assertTrue(CampaignSkeleton.axisScore(root, "entity_attention") > 0.0d);
    assertEquals(23, director.getJSONObject("activeCooldowns").getInt("DANGER_UNTIL_TURN"));
    assertTrue(director.getJSONObject("tagWeightModifiers").getDouble("DANGER") <= 0.65d);
  }

  @Test public void eventCanonRegistryRejectsUnknownEventTypes() throws Exception {
    EmergentTurnEngine engine = new EmergentTurnEngine();
    try {
      engine.event("turn-unknown", new JSONArray(), "AI_INVENTED_EVENT", "LOCAL", "x",
          new JSONObject(), null);
      org.junit.Assert.fail("Unknown event types must fail closed.");
    } catch (IllegalStateException expected) {
      assertTrue(expected.getMessage().contains("Unknown canon event_type"));
    }
  }


  @Test public void worldProposalValidationKeepsAiInsideSelectedCandidateCapabilities() throws Exception {
    EmergentTurnEngine engine = new EmergentTurnEngine();
    JSONObject selected = engine.candidate(
        "ENTITY", "entity:test", "DANGER", 3.5d, "test", "test", true)
        .put("allowedWorldActions", new JSONArray().put("INTERCEPT").put("OBSERVE"))
        .put("fallbackAction", "INTERCEPT");

    JSONObject invalid = new JSONObject()
        .put("actionType", "AMBUSH")
        .put("intentTag", "aggressive");
    assertFalse(engine.worldProposalValidationReason(selected, invalid).isEmpty());
    JSONObject fallback = engine.sanitizeWorldProposal(selected, invalid);
    assertEquals("INTERCEPT", fallback.getString("actionType"));
    assertTrue(fallback.getBoolean("fallback"));

    JSONObject valid = new JSONObject()
        .put("actionType", "OBSERVE")
        .put("intentTag", "cautious");
    assertTrue(engine.worldProposalValidationReason(selected, valid).isEmpty());
    JSONObject accepted = engine.sanitizeWorldProposal(selected, valid);
    assertEquals("OBSERVE", accepted.getString("actionType"));
    assertFalse(accepted.getBoolean("fallback"));
  }

}

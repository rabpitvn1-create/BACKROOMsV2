package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.Queue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class CharacterEncounterCoreTest {
  @Test public void luciaRollsOnlyOnLevelZeroAndUsesExactTenPercentBoundary() throws Exception {
    assertTrue(CharacterEncounterCore.shouldEncounterLucia(0, 0));
    assertTrue(CharacterEncounterCore.shouldEncounterLucia(0, 9));
    assertFalse(CharacterEncounterCore.shouldEncounterLucia(0, 10));
    assertFalse(CharacterEncounterCore.shouldEncounterLucia(1, 0));

    SequenceRng levelZeroRng = new SequenceRng(9, 3999, 3999);
    JSONObject levelZero = state(0, 1);
    new CharacterEncounterCore(levelZeroRng).rollForExplorerAction(levelZero, "Kai đi tiếp");
    assertEquals("lucia", levelZero.getJSONArray("party").getJSONObject(0).getString("id"));
    assertEquals(3, levelZeroRng.calls);

    SequenceRng levelOneRng = new SequenceRng(3999, 3999);
    JSONObject levelOne = state(1, 1);
    new CharacterEncounterCore(levelOneRng).rollForExplorerAction(levelOne, "Kai đi tiếp");
    assertEquals(0, levelOne.getJSONArray("party").length());
    assertEquals(2, levelOneRng.calls);
  }

  @Test public void irisAndSyvialUseExactOneInFourThousandBoundary() {
    assertTrue(CharacterEncounterCore.shouldEncounterRare(0));
    assertFalse(CharacterEncounterCore.shouldEncounterRare(1));
    assertFalse(CharacterEncounterCore.shouldEncounterRare(3999));
  }

  @Test public void oneExplorerRollAutoJoinsAllHitsWithoutDuplicatesOrRerolls() throws Exception {
    SequenceRng rng = new SequenceRng(0, 0, 0);
    CharacterEncounterCore core = new CharacterEncounterCore(rng);
    JSONObject state = state(0, 7);
    CharacterEncounterCore.EncounterResult result =
        core.rollForExplorerAction(state, "Kai quan sát hành lang");
    assertTrue(result.joinedAny());
    assertEquals(3, state.getJSONArray("party").length());
    assertEquals("lucia", state.getJSONArray("party").getJSONObject(0).getString("id"));
    assertEquals("iris", state.getJSONArray("party").getJSONObject(1).getString("id"));
    assertEquals("syvial", state.getJSONArray("party").getJSONObject(2).getString("id"));
    core.rollForExplorerAction(state, "Gemini retry");
    state.put("turn", 8);
    core.rollForExplorerAction(state, "Kai đi tiếp");
    assertEquals(3, rng.calls);
    assertEquals(3, state.getJSONArray("party").length());
  }

  @Test public void geminiFailureDoesNotRollbackJoinedPartyAndLeavesIntroPending() throws Exception {
    JSONObject state = state(0, 3);
    CharacterEncounterCore core = new CharacterEncounterCore(new SequenceRng(0, 3999, 3999));
    core.rollForExplorerAction(state, "Kai mở cửa");
    JSONObject savedAfterFailure = new JSONObject(state.toString());
    core.normalizeState(savedAfterFailure);
    assertEquals("lucia", savedAfterFailure.getJSONArray("party").getJSONObject(0).getString("id"));
    assertEquals("lucia", savedAfterFailure.getJSONObject("characterEncounter")
        .getJSONArray("pendingIntro").getString(0));
    try {
      core.validateAndApply(savedAfterFailure,
          new JSONObject(savedAfterFailure.toString()), new JSONArray());
      fail("Pending encounter must require 2-5 dialogue lines");
    } catch (IllegalArgumentException expected) {
      assertEquals(1, savedAfterFailure.getJSONArray("party").length());
    }
  }

  @Test public void legacySaveMigrationRemovesKaiDuplicatesAndShadowProgression() throws Exception {
    JSONObject lucia = new JSONObject()
        .put("name", "Hứa Thuý Mai")
        .put("hp", 61)
        .put("stats", new JSONObject().put("STR", 11))
        .put("level", 7)
        .put("customMetadata", "keep-me");
    JSONObject state = state(0, 12);
    state.put("party", new JSONArray()
        .put(new JSONObject().put("id", "kai").put("name", "Kai Akechi"))
        .put(lucia)
        .put(new JSONObject().put("id", "lucia").put("name", "Lucia Lục"))
        .put("Iris")
        .put("Syvial"));

    new CharacterEncounterCore(new SequenceRng()).normalizeState(state);
    JSONArray party = state.getJSONArray("party");
    assertEquals(CharacterEncounterCore.MAX_COMPANIONS, party.length());
    assertEquals("lucia", party.getJSONObject(0).getString("id"));
    assertFalse(party.getJSONObject(0).has("hp"));
    assertFalse(party.getJSONObject(0).has("stats"));
    assertFalse(party.getJSONObject(0).has("level"));
    assertEquals("keep-me", party.getJSONObject(0).getString("customMetadata"));
    assertTrue(party.getJSONObject(0).getBoolean("joined"));
    assertEquals("iris", party.getJSONObject(1).getString("id"));
    assertEquals("syvial", party.getJSONObject(2).getString("id"));
  }

  @Test public void emptyLegacyPartyMigratesWithoutCrash() throws Exception {
    JSONObject state = state(0, 1).put("party", new JSONArray());
    new CharacterEncounterCore(new SequenceRng()).normalizeState(state);
    assertEquals(0, state.getJSONArray("party").length());
  }

  @Test public void geminiCandidateCannotAddRemoveOrReplacePartyMembers() throws Exception {
    CharacterEncounterCore core = new CharacterEncounterCore(new SequenceRng());
    JSONObject before = state(0, 2).put("party", new JSONArray()
        .put(new JSONObject().put("id", "lucia").put("name", "Lucia Lục")));
    core.normalizeState(before);
    JSONObject candidate = new JSONObject(before.toString()).put("party", new JSONArray()
        .put(new JSONObject().put("id", "syvial").put("name", "Syvial").put("joined", true)));
    core.validateAndApply(before, candidate, new JSONArray());
    assertEquals(1, candidate.getJSONArray("party").length());
    assertEquals("lucia", candidate.getJSONArray("party").getJSONObject(0).getString("id"));
  }

  private static JSONObject state(int level, int turn) throws Exception {
    return new JSONObject()
        .put("currentLevel", level)
        .put("turn", turn)
        .put("player", new JSONObject().put("name", "Kai Akechi"))
        .put("party", new JSONArray())
        .put("flags", new JSONObject())
        .put("log", new JSONArray().put(new JSONObject().put("role", "gm").put("text", "Test")));
  }

  private static final class SequenceRng implements CharacterEncounterCore.IntRng {
    final Queue<Integer> values = new ArrayDeque<>();
    int calls;
    SequenceRng(int... values) {
      for (int value : values) this.values.add(value);
    }
    @Override public int nextInt(int bound) {
      calls++;
      if (values.isEmpty()) throw new AssertionError("Unexpected RNG call for bound " + bound);
      return values.remove();
    }
  }
}

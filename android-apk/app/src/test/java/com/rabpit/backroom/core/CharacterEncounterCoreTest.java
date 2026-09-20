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
    assertTrue(CharacterEncounterCore.shouldEncounterLucia("0", 0));
    assertTrue(CharacterEncounterCore.shouldEncounterLucia("0", 9));
    assertFalse(CharacterEncounterCore.shouldEncounterLucia("0", 10));
    assertFalse(CharacterEncounterCore.shouldEncounterLucia("1", 0));
    assertFalse(CharacterEncounterCore.shouldEncounterLucia("0.1", 0));
    assertFalse(CharacterEncounterCore.shouldEncounterLucia("red_rooms", 0));

    SequenceRng levelZeroRng = new SequenceRng(9, 3999, 3999);
    JSONObject levelZero = state(0, 1);
    CharacterEncounterCore levelZeroCore = new CharacterEncounterCore(levelZeroRng);
    levelZeroCore.rollForExplorerAction(levelZero, "Kai đi tiếp");
    assertEquals(0, levelZero.getJSONArray("party").length());
    assertEquals("lucia", levelZero.getJSONObject("characterEncounter")
        .getJSONArray("pendingIntro").getString(0));
    JSONObject committed = new JSONObject(levelZero.toString());
    levelZeroCore.validateAndApply(levelZero, committed,
        new JSONArray().put("Ai đó?").put("Bình tĩnh. Tôi là Lucia."));
    assertEquals("lucia", committed.getJSONArray("party").getJSONObject(0).getString("id"));
    assertEquals(3, levelZeroRng.calls);

    SequenceRng levelOneRng = new SequenceRng(3999, 3999);
    JSONObject levelOne = state(1, 1);
    new CharacterEncounterCore(levelOneRng).rollForExplorerAction(levelOne, "Kai đi tiếp");
    assertEquals(0, levelOne.getJSONArray("party").length());
    assertEquals(2, levelOneRng.calls);
  }

  @Test public void luciaDoesNotRollInsideLevelZeroSublevels() throws Exception {
    SequenceRng rng = new SequenceRng(3999, 3999);
    JSONObject sublevel = state(0, 1).put(LevelCore.LEVEL_KEY, "0.1");
    new CharacterEncounterCore(rng).rollForExplorerAction(sublevel, "Kai đi tiếp");
    assertEquals(0, sublevel.getJSONArray("party").length());
    assertEquals(2, rng.calls);
  }

  @Test public void irisAndSyvialUseExactOneInFourThousandBoundary() {
    assertTrue(CharacterEncounterCore.shouldEncounterRare(0));
    assertFalse(CharacterEncounterCore.shouldEncounterRare(1));
    assertFalse(CharacterEncounterCore.shouldEncounterRare(3999));
  }

  @Test public void oneExplorerRollQueuesAllHitsThenJoinsAfterFirstContact() throws Exception {
    SequenceRng rng = new SequenceRng(0, 0, 0);
    CharacterEncounterCore core = new CharacterEncounterCore(rng);
    JSONObject state = state(0, 7);
    CharacterEncounterCore.EncounterResult result =
        core.rollForExplorerAction(state, "Kai quan sát hành lang");
    assertTrue(result.joinedAny());
    assertEquals(0, state.getJSONArray("party").length());
    assertEquals(3, state.getJSONObject("characterEncounter").getJSONArray("pendingIntro").length());

    JSONObject candidate = new JSONObject(state.toString());
    core.validateAndApply(state, candidate,
        new JSONArray().put("Đứng lại.").put("Tôi không có ý gây sự.").put("Nói sau, ra khỏi chỗ này trước."));
    assertEquals(3, candidate.getJSONArray("party").length());
    assertEquals("lucia", candidate.getJSONArray("party").getJSONObject(0).getString("id"));
    assertEquals("iris", candidate.getJSONArray("party").getJSONObject(1).getString("id"));
    assertEquals("syvial", candidate.getJSONArray("party").getJSONObject(2).getString("id"));

    core.rollForExplorerAction(candidate, "Gemini retry");
    candidate.put("turn", 8);
    core.rollForExplorerAction(candidate, "Kai đi tiếp");
    assertEquals(3, rng.calls);
    assertEquals(3, candidate.getJSONArray("party").length());
  }

  @Test public void geminiFailureKeepsFirstContactPendingWithoutPrematureJoin() throws Exception {
    JSONObject state = state(0, 3);
    CharacterEncounterCore core = new CharacterEncounterCore(new SequenceRng(0, 3999, 3999));
    core.rollForExplorerAction(state, "Kai mở cửa");
    JSONObject savedAfterFailure = new JSONObject(state.toString());
    core.normalizeState(savedAfterFailure);
    assertEquals(0, savedAfterFailure.getJSONArray("party").length());
    assertEquals("lucia", savedAfterFailure.getJSONObject("characterEncounter")
        .getJSONArray("pendingIntro").getString(0));
    try {
      core.validateAndApply(savedAfterFailure,
          new JSONObject(savedAfterFailure.toString()), new JSONArray());
      fail("Pending encounter must require 2-5 dialogue lines");
    } catch (IllegalArgumentException expected) {
      assertEquals(0, savedAfterFailure.getJSONArray("party").length());
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
    assertTrue(party.getJSONObject(0).getJSONArray("inventory").length() >= 3);
    assertTrue(party.getJSONObject(1).getJSONArray("inventory").length() >= 2);
    assertTrue(party.getJSONObject(2).getJSONArray("inventory").length() >= 2);
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

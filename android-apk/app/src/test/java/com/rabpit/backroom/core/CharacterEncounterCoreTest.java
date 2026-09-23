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
  @Test public void lucTramRollsOnlyAfterLevelZeroAndUsesExactTenPercentBoundary() throws Exception {
    assertFalse(CharacterEncounterCore.shouldEncounterLucTram("0", 0));
    assertFalse(CharacterEncounterCore.shouldEncounterLucTram("0.1", 0));
    assertFalse(CharacterEncounterCore.shouldEncounterLucTram("0-7", 0));
    assertTrue(CharacterEncounterCore.shouldEncounterLucTram("1", 0));
    assertTrue(CharacterEncounterCore.shouldEncounterLucTram("1", 9));
    assertFalse(CharacterEncounterCore.shouldEncounterLucTram("1", 10));

    SequenceRng levelZeroRng = new SequenceRng(3999, 3999);
    JSONObject levelZero = state(0, 1).put(LevelCore.LEVEL_KEY, "0");
    new CharacterEncounterCore(levelZeroRng).rollForExplorerAction(levelZero, "Cao Minh đi tiếp");
    assertEquals(0, levelZero.getJSONArray("party").length());
    assertEquals(0, levelZero.getJSONObject("characterEncounter")
        .getJSONArray("pendingIntro").length());
    assertEquals(2, levelZeroRng.calls);

    SequenceRng levelOneRng = new SequenceRng(9, 3999, 3999);
    JSONObject levelOne = state(1, 1).put(LevelCore.LEVEL_KEY, "1");
    CharacterEncounterCore levelOneCore = new CharacterEncounterCore(levelOneRng);
    levelOneCore.rollForExplorerAction(levelOne, "Cao Minh đi tiếp");
    assertEquals(0, levelOne.getJSONArray("party").length());
    assertEquals("luc_tram", levelOne.getJSONObject("characterEncounter")
        .getJSONArray("pendingIntro").getString(0));
    JSONObject committed = new JSONObject(levelOne.toString());
    levelOneCore.validateAndApply(levelOne, committed,
        new JSONArray().put("Ma đầu.").put("Không ngờ lại gặp ngươi ở nơi này."));
    assertEquals("luc_tram", committed.getJSONArray("party").getJSONObject(0).getString("id"));
    assertEquals("Lục Trầm", committed.getJSONArray("party").getJSONObject(0).getString("name"));
    assertEquals(3, levelOneRng.calls);
  }

  @Test public void lucTramDoesNotEncounterInsideLevelZeroSublevels() throws Exception {
    SequenceRng rng = new SequenceRng(3999, 3999);
    JSONObject sublevel = state(0, 1).put(LevelCore.LEVEL_KEY, "0.1");
    new CharacterEncounterCore(rng).rollForExplorerAction(sublevel, "Cao Minh đi tiếp");
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
    JSONObject state = state(1, 7).put(LevelCore.LEVEL_KEY, "1");
    CharacterEncounterCore.EncounterResult result =
        core.rollForExplorerAction(state, "Cao Minh quan sát hành lang");
    assertTrue(result.joinedAny());
    assertEquals(0, state.getJSONArray("party").length());
    assertEquals(3, state.getJSONObject("characterEncounter").getJSONArray("pendingIntro").length());

    JSONObject candidate = new JSONObject(state.toString());
    core.validateAndApply(state, candidate,
        new JSONArray().put("Đứng lại.").put("Tôi không có ý gây sự.").put("Nói sau, ra khỏi chỗ này trước."));
    assertEquals(3, candidate.getJSONArray("party").length());
    assertEquals("luc_tram", candidate.getJSONArray("party").getJSONObject(0).getString("id"));
    assertEquals("iris", candidate.getJSONArray("party").getJSONObject(1).getString("id"));
    assertEquals("syvial", candidate.getJSONArray("party").getJSONObject(2).getString("id"));

    core.rollForExplorerAction(candidate, "Gemini retry");
    candidate.put("turn", 8);
    core.rollForExplorerAction(candidate, "Cao Minh đi tiếp");
    assertEquals(3, rng.calls);
    assertEquals(3, candidate.getJSONArray("party").length());
  }

  @Test public void geminiFailureKeepsReunionPendingWithoutPrematureJoin() throws Exception {
    JSONObject state = state(1, 3).put(LevelCore.LEVEL_KEY, "1");
    CharacterEncounterCore core = new CharacterEncounterCore(new SequenceRng(0, 3999, 3999));
    core.rollForExplorerAction(state, "Cao Minh mở cửa");
    JSONObject savedAfterFailure = new JSONObject(state.toString());
    core.normalizeState(savedAfterFailure);
    assertEquals(0, savedAfterFailure.getJSONArray("party").length());
    assertEquals("luc_tram", savedAfterFailure.getJSONObject("characterEncounter")
        .getJSONArray("pendingIntro").getString(0));
    try {
      core.validateAndApply(savedAfterFailure,
          new JSONObject(savedAfterFailure.toString()), new JSONArray());
      fail("Pending encounter must require 2-5 dialogue lines");
    } catch (IllegalArgumentException expected) {
      assertEquals(0, savedAfterFailure.getJSONArray("party").length());
    }
  }

  @Test public void normalizationDeduplicatesCurrentCompanionsAndStripsShadowProgression() throws Exception {
    JSONObject lucTram = new JSONObject()
        .put("id", "luc_tram")
        .put("name", "Lục Trầm")
        .put("hp", 61)
        .put("stats", new JSONObject().put("STR", 11))
        .put("level", 7)
        .put("customMetadata", "keep-me");
    JSONObject state = state(0, 12);
    state.put("party", new JSONArray()
        .put(new JSONObject().put("id", "cao_minh").put("name", "Cao Minh"))
        .put(lucTram)
        .put(new JSONObject().put("id", "luc_tram").put("name", "Lục Trầm"))
        .put("Iris")
        .put("Syvial"));

    new CharacterEncounterCore(new SequenceRng()).normalizeState(state);
    JSONArray party = state.getJSONArray("party");
    assertEquals(CharacterEncounterCore.MAX_COMPANIONS, party.length());
    assertEquals("luc_tram", party.getJSONObject(0).getString("id"));
    assertEquals("Lục Trầm", party.getJSONObject(0).getString("name"));
    assertFalse(party.getJSONObject(0).has("hp"));
    assertFalse(party.getJSONObject(0).has("stats"));
    assertFalse(party.getJSONObject(0).has("level"));
    assertEquals("keep-me", party.getJSONObject(0).getString("customMetadata"));
    assertTrue(party.getJSONObject(0).getBoolean("joined"));
    assertEquals("iris", party.getJSONObject(1).getString("id"));
    assertEquals("syvial", party.getJSONObject(2).getString("id"));
    assertTrue(party.getJSONObject(0).getJSONArray("inventory").length() >= 2);
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
        .put(new JSONObject().put("id", "luc_tram").put("name", "Lục Trầm")));
    core.normalizeState(before);
    JSONObject candidate = new JSONObject(before.toString()).put("party", new JSONArray()
        .put(new JSONObject().put("id", "syvial").put("name", "Syvial").put("joined", true)));
    core.validateAndApply(before, candidate, new JSONArray());
    assertEquals(1, candidate.getJSONArray("party").length());
    assertEquals("luc_tram", candidate.getJSONArray("party").getJSONObject(0).getString("id"));
  }

  private static JSONObject state(int level, int turn) throws Exception {
    return new JSONObject()
        .put("currentLevel", level)
        .put("turn", turn)
        .put("player", new JSONObject().put("name", "Cao Minh"))
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

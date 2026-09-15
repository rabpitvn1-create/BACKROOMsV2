package com.rabpit.backroom.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RuntimeDebugLogTest {
  @Before fun reset() {
    RuntimeDebugLog.resetForTests()
    RuntimeDebugLog.initializeSession(JSONObject()
      .put("versionName", "test")
      .put("versionCode", 1)
      .put("providers", JSONObject().put("gemini", "Gemini model matrix")))
  }

  @Test fun exportIsValidAndContainsBeforeCandidateAfterAndEvents() {
    val before = JSONObject("""{"turn":4,"level":{"number":0},"flags":{"storyArc":{"currentBeat":"A"}}}""")
    val candidate = JSONObject("""{"turn":4,"level":{"number":0},"flags":{"storyArc":{"currentBeat":"B"}}}""")
    val after = JSONObject("""{"turn":5,"level":{"number":0},"flags":{"storyArc":{"currentBeat":"B"}}}""")

    RuntimeDebugLog.beginTurn(4, "Quan sát hành lang", meta = false, before = before)
    RuntimeDebugLog.recordTurnStage(4, "candidateInitial", candidate)
    RuntimeDebugLog.recordEvent("writer", "parsed", 4, JSONObject().put("reply", "Không có gì mới."))
    RuntimeDebugLog.finishTurn(4, after, "Không có gì mới.", null)

    val exported = JSONObject(RuntimeDebugLog.exportJson(after))
    assertEquals(1, exported.getInt("schemaVersion"))
    assertTrue(exported.getJSONObject("session").has("startedAt"))
    assertTrue(exported.getJSONArray("events").length() >= 2)
    val turn = exported.getJSONArray("turns").getJSONObject(0)
    assertEquals(4, turn.getInt("turn"))
    assertEquals("A", turn.getJSONObject("stateBefore").getJSONObject("flags").getJSONObject("storyArc").getString("currentBeat"))
    assertEquals("B", turn.getJSONObject("candidateInitial").getJSONObject("flags").getJSONObject("storyArc").getString("currentBeat"))
    assertEquals(5, turn.getJSONObject("stateAfter").getInt("turn"))
  }

  @Test fun exportRedactsSensitiveKeysAndCredentialShapedStrings() {
    val payload = JSONObject()
      .put("authorization", "Bearer fake-auth-value-123456")
      .put("api_key", "fake-api-key-value")
      .put("x-api-key", "fake-x-api-key-value")
      .put("token", "fake-token-value")
      .put("secret", "fake-secret-value")
      .put("credential", "fake-credential-value")
      .put("password", "fake-password-value")
      .put("nested", JSONObject().put("message", "Bearer fake-bearer-value-123456"))

    RuntimeDebugLog.recordEvent("security", "fixture", 1, payload)
    val raw = RuntimeDebugLog.exportJson(null)
    assertTrue(raw.contains("[REDACTED]"))
    for (forbidden in listOf(
      "fake-auth-value-123456",
      "fake-api-key-value",
      "fake-x-api-key-value",
      "fake-token-value",
      "fake-secret-value",
      "fake-credential-value",
      "fake-password-value",
      "fake-bearer-value-123456",
    )) assertFalse("secret-like fixture leaked: $forbidden", raw.contains(forbidden))
  }

  @Test fun canonFallbackDiagnosticsExplainTheSameDecisionAsIsEligible() {
    val before = JSONObject("""{
      "turn":1,
      "level":{"number":0,"name":"The Lobby"},
      "player":{"name":"Kai Akechi","hp":100},
      "party":[],
      "inventory":[],
      "flags":{"storyArc":{"currentBeat":"STORY.PROLOGUE.ENTRY_COMPLETE","completed":["STORY.PROLOGUE.ENTRY_COMPLETE"]}}
    }""")
    val candidate = JSONObject(before.toString())
    val generated = JSONObject().put("reply", "x").put("ops", org.json.JSONArray())
    val rolls = JSONObject().put("actionKind", "EXPLORE")
    val action = "Khám phá căn phòng vàng và kiểm tra hành lang"

    val diagnostics = CanonFallbackPolicy.diagnostics(before, candidate, generated, rolls, action, meta = false, repaired = true)
    val eligible = CanonFallbackPolicy.isEligible(before, candidate, generated, rolls, action, meta = false, repaired = true)
    assertEquals(eligible, diagnostics.getBoolean("eligible"))
    assertEquals("eligible", diagnostics.getString("reason"))

    val blocked = CanonFallbackPolicy.diagnostics(before, candidate, generated, rolls, action, meta = true, repaired = true)
    assertFalse(blocked.getBoolean("eligible"))
    assertEquals("meta_turn", blocked.getString("reason"))
  }

  @Test fun staleEncounterAndTransitionContextDoNotBlockSafeExploreFallback() {
    val before = JSONObject("""{
      "turn":7,
      "level":{"number":0,"name":"The Lobby"},
      "player":{"name":"Kai Akechi","hp":100},
      "party":[],
      "inventory":[],
      "flags":{"entityEncounterKey":"hound","exploration":{"transitionReady":true}}
    }""")
    val candidate = JSONObject(before.toString())
    candidate.getJSONObject("player").put("hp", 1)
    val generated = JSONObject().put("reply", "x").put("ops", org.json.JSONArray())
    val rolls = JSONObject().put("actionKind", "EXPLORE")

    val diagnostics = CanonFallbackPolicy.diagnostics(
      before, candidate, generated, rolls,
      "Quan sát hành lang và tiếp tục khám phá", meta = false, repaired = true,
    )
    assertTrue(diagnostics.getBoolean("eligible"))
    assertTrue(diagnostics.getBoolean("dangerousStateChanged"))
    assertTrue(diagnostics.getBoolean("lowRiskExplorationRecovery"))
    assertTrue(diagnostics.getBoolean("entityPresentBefore"))
    assertTrue(diagnostics.getBoolean("transitionReadyBefore"))
  }

  @Test fun consequentialExploreRollStillFailsClosed() {
    val before = JSONObject("""{
      "turn":8,
      "level":{"number":0,"name":"The Lobby"},
      "player":{"name":"Kai Akechi","hp":100},
      "flags":{}
    }""")
    val candidate = JSONObject(before.toString())
    val generated = JSONObject().put("reply", "x").put("ops", org.json.JSONArray())
    val rolls = JSONObject()
      .put("actionKind", "EXPLORE")
      .put("entityEncounter", JSONObject().put("success", true))
    val diagnostics = CanonFallbackPolicy.diagnostics(
      before, candidate, generated, rolls,
      "Tiếp tục khám phá", meta = false, repaired = true,
    )
    assertFalse(diagnostics.getBoolean("eligible"))
    assertEquals("consequential_roll", diagnostics.getString("reason"))
  }

}

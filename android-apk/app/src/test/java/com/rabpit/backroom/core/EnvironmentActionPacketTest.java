package com.rabpit.backroom.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class EnvironmentActionPacketTest {
  @Test public void environmentExchangeDoesNotAdvanceStoryOrTurn() throws Exception {
    JSONObject story = new JSONObject()
        .put("active", true)
        .put("awaitingDecision", true)
        .put("currentSegmentIndex", 4);
    JSONObject state = new JSONObject()
        .put("turn", 12)
        .put("story", story)
        .put("log", new JSONArray().put(
            new JSONObject().put("role", "gm").put("text", "Ba lối rẽ nằm phía trước.")));

    String storyBefore = story.toString();
    EnvironmentActionPacket.appendExchange(
        state, "Cao Minh kiểm tra vệt đen trên tường.", "Vệt đen là lớp vật liệu cháy sẫm.");

    assertEquals(12, state.getInt("turn"));
    assertEquals(storyBefore, state.getJSONObject("story").toString());
    JSONArray log = state.getJSONArray("log");
    assertEquals(3, log.length());
    assertEquals("environment", log.getJSONObject(1).getString("scope"));
    assertEquals("environment", log.getJSONObject(2).getString("scope"));
    assertFalse(log.getJSONObject(2).has("choices"));
  }

  @Test public void environmentPromptExcludesStoryStateAndForbidsProgression() throws Exception {
    JSONObject state = new JSONObject()
        .put("turn", 7)
        .put("location", "Hành lang vàng")
        .put("story", new JSONObject().put("secretFutureBeat", "KHÔNG ĐƯỢC LỘ"))
        .put("levelRoute", new JSONObject().put("streak", 2))
        .put("log", new JSONArray());

    String prompt = EnvironmentActionPacket.build(
        "LEVEL CONTEXT", "ENTITY CONTEXT", "ITEM CONTEXT", "CHARACTER CONTEXT",
        "GM: Cao Minh đứng trước một vệt đen.", state, "Kiểm tra vệt đen.");

    assertTrue(prompt.contains("ENVIRONMENT-ONLY CONTRACT"));
    assertTrue(prompt.contains("Không được tiến, resolve, thay thế"));
    assertTrue(prompt.contains("Chỉ được có trường reply"));
    assertFalse(prompt.contains("secretFutureBeat"));
    assertFalse(prompt.contains("\"levelRoute\""));
  }
}

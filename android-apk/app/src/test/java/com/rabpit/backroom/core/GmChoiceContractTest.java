package com.rabpit.backroom.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class GmChoiceContractTest {
  @Test public void gmEntryAddsCoreOwnedSemanticHighlightsWithoutModelMetadata() throws Exception {
    JSONObject state = new JSONObject()
        .put("player", new JSONObject().put("name", "Kai Akechi"))
        .put("location", "Level 0 / The Lobby — hành lang phía đông")
        .put("party", new JSONArray().put(new JSONObject().put("id", "lucia").put("name", "Lucia Lục")))
        .put("inventory", new JSONArray().put(new JSONObject().put("id", "almond-water").put("name", "Almond Water")));

    String reply = "Kai Akechi gặp Clump tại Level 0. Lucia Lục dùng Quick Step trong khi "
        + "Almond Water vẫn còn. HP 50/50, EXP 10 và trạng thái Chảy máu xuất hiện.";
    JSONObject entry = GmChoiceContract.gmEntry(reply, new JSONObject(), state);
    JSONArray highlights = entry.getJSONArray("highlights");

    assertHighlight(highlights, "Kai Akechi", "character");
    assertHighlight(highlights, "Lucia Lục", "character");
    assertHighlight(highlights, "Clump", "entity");
    assertHighlight(highlights, "Quick Step", "skill");
    assertHighlight(highlights, "Chảy máu", "effect");
    assertHighlight(highlights, "Almond Water", "item");
    assertHighlight(highlights, "Level 0", "location");
    assertHighlight(highlights, "HP 50/50", "stat");
    assertHighlight(highlights, "EXP", "stat");
  }

  @Test public void deterministicTypeReplacesUntypedModelDuplicate() throws Exception {
    JSONObject generated = new JSONObject().put("highlights",
        new JSONArray().put("Clump").put(new JSONObject().put("text", "vệt đen").put("type", "effect")));

    JSONObject entry = GmChoiceContract.gmEntry(
        "Clump đứng cạnh một vệt đen.", generated, new JSONObject());
    JSONArray highlights = entry.getJSONArray("highlights");

    assertHighlight(highlights, "Clump", "entity");
    assertHighlight(highlights, "vệt đen", "effect");
  }

  @Test public void knownTermsNotPresentInReplyAreNotInjected() throws Exception {
    JSONObject entry = GmChoiceContract.gmEntry(
        "Kai đứng yên.", new JSONObject(), new JSONObject());
    JSONArray highlights = entry.getJSONArray("highlights");

    assertHighlight(highlights, "Kai", "character");
    assertFalse(hasHighlight(highlights, "Clump"));
    assertFalse(hasHighlight(highlights, "Almond Water"));
  }

  @Test public void choicesAlsoReceiveDeterministicSemanticHighlights() throws Exception {
    JSONObject generated = new JSONObject().put("choices",
        new JSONArray().put(new JSONObject().put("text", "Đi về Level 0 và dùng Bandage")));
    JSONObject entry = GmChoiceContract.gmEntry("", generated, new JSONObject());
    JSONObject choice = entry.getJSONArray("choices").getJSONObject(0);

    assertHighlight(choice.getJSONArray("highlights"), "Level 0", "location");
    assertHighlight(choice.getJSONArray("highlights"), "Bandage", "item");
  }

  private static void assertHighlight(JSONArray values, String text, String type) throws Exception {
    for (int i = 0; i < values.length(); i++) {
      JSONObject value = values.optJSONObject(i);
      if (value == null || !text.equals(value.optString("text", ""))) continue;
      assertEquals(type, value.optString("type", ""));
      return;
    }
    throw new AssertionError("Missing highlight: " + text + " / " + type + " in " + values);
  }

  private static boolean hasHighlight(JSONArray values, String text) {
    for (int i = 0; i < values.length(); i++) {
      JSONObject value = values.optJSONObject(i);
      if (value != null && text.equals(value.optString("text", ""))) return true;
      if (text.equals(values.optString(i, ""))) return true;
    }
    return false;
  }
}

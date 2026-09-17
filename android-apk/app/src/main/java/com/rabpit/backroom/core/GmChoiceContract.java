package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;

/** Sanitizes the AI-only Explorer choice/highlight projection before it reaches the WebView. */
public final class GmChoiceContract {
  private static final int MAX_CHOICES = 3;
  private static final int MAX_CHOICE_TEXT = 180;
  private static final int MAX_HIGHLIGHTS = 24;
  private static final int MAX_HIGHLIGHT_TEXT = 120;

  private GmChoiceContract() {}

  public static JSONObject gmEntry(String reply, JSONObject generated) throws Exception {
    JSONObject entry = new JSONObject().put("role", "gm").put("text", reply == null ? "" : reply.trim());
    JSONArray highlights = sanitizeHighlights(generated == null ? null : generated.optJSONArray("highlights"));
    if (highlights.length() > 0) entry.put("highlights", highlights);
    JSONArray choices = sanitizeChoices(generated == null ? null : generated.optJSONArray("choices"));
    if (choices.length() > 0) entry.put("choices", choices);
    return entry;
  }

  public static JSONArray sanitizeChoices(JSONArray input) throws Exception {
    JSONArray output = new JSONArray();
    if (input == null) return output;
    for (int i = 0; i < input.length() && output.length() < MAX_CHOICES; i++) {
      Object raw = input.opt(i);
      String text = "";
      JSONArray rawHighlights = null;
      if (raw instanceof JSONObject) {
        JSONObject object = (JSONObject) raw;
        text = object.optString("text", object.optString("action", "")).trim();
        rawHighlights = object.optJSONArray("highlights");
      } else if (raw != null) {
        text = String.valueOf(raw).trim();
      }
      if (text.isEmpty()) continue;
      if (text.length() > MAX_CHOICE_TEXT) text = text.substring(0, MAX_CHOICE_TEXT).trim();
      JSONObject choice = new JSONObject()
        .put("id", String.valueOf((char)('A' + output.length())))
        .put("text", text)
        .put("action", text);
      JSONArray highlights = sanitizeHighlights(rawHighlights);
      if (highlights.length() > 0) choice.put("highlights", highlights);
      output.put(choice);
    }
    return output;
  }

  public static JSONArray sanitizeHighlights(JSONArray input) {
    JSONArray output = new JSONArray();
    if (input == null) return output;
    for (int i = 0; i < input.length() && output.length() < MAX_HIGHLIGHTS; i++) {
      Object raw = input.opt(i);
      String text;
      if (raw instanceof JSONObject) {
        JSONObject object = (JSONObject) raw;
        text = object.optString("text", object.optString("name", "")).trim();
      } else {
        text = raw == null ? "" : String.valueOf(raw).trim();
      }
      if (text.length() < 2) continue;
      if (text.length() > MAX_HIGHLIGHT_TEXT) text = text.substring(0, MAX_HIGHLIGHT_TEXT).trim();
      boolean duplicate = false;
      for (int j = 0; j < output.length(); j++) {
        if (text.equalsIgnoreCase(output.optString(j, ""))) { duplicate = true; break; }
      }
      if (!duplicate) output.put(text);
    }
    return output;
  }
}

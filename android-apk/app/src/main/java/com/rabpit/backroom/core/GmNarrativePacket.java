package com.rabpit.backroom.core;

import org.json.JSONObject;

/** Builds the compact read-only narrative packet sent to the text provider. */
public final class GmNarrativePacket {
  public static final int MAX_RECENT_STORY_CHARS = 2800;

  private GmNarrativePacket() {}

  public static JSONObject projectState(JSONObject state) throws Exception {
    JSONObject projected = state == null ? new JSONObject() : new JSONObject(state.toString());
    projected.remove("log");
    projected.remove("levelRoute");
    projected.remove("characterCanon");
    projected.remove(StoryCore.ROOT_KEY);
    return projected;
  }

  public static String build(
      String levelContext,
      String entityContext,
      String itemContext,
      String characterContext,
      String recentStory,
      JSONObject state,
      String action,
      String gmStyleExamples) throws Exception {
    return build(
        levelContext, entityContext, itemContext, characterContext, "",
        recentStory, state, action, gmStyleExamples);
  }

  public static String build(
      String levelContext,
      String entityContext,
      String itemContext,
      String characterContext,
      String storyContext,
      String recentStory,
      JSONObject state,
      String action,
      String gmStyleExamples) throws Exception {
    JSONObject promptState = projectState(state);
    String recent = clip(recentStory, MAX_RECENT_STORY_CHARS);
    String style = clip(gmStyleExamples, 1800);

    return "Bạn là Game Master của text game Backrooms (xianxia x Backrooms).\n"
        + GmNarratorContract.promptContext() + "\n"
        + GmNarratorContract.caoMinhNarrativeCard() + "\n"
        + "NGÔN NGỮ HIỂN THỊ: reply, sceneLabel, choices và encounterDialogue phải là tiếng Việt tự nhiên. "
        + "Chỉ giữ tiếng Anh cho tên riêng/tên chính thức cần thiết.\n"
        + style + "\n"
        + "CORE-OWNED: Java Core sở hữu Level/route, authored Story state, Entity spawn, Loot, Inventory, Party, Survival, Progression và Combat. "
        + "AI không được sửa các state này. sceneLabel chỉ là nhãn mô tả và không bao giờ tự chuyển Level.\n"
        + "EXPLORER CHOICES: trả 0-3 gợi ý ngắn; nếu Entity đang đối đầu trực tiếp thì choices=[].\n"
        + "ENCOUNTER DIALOGUE: chỉ khi Character Core có pending intro; khi đó trả đúng 2-5 câu thoại. Nếu không thì [].\n"
        + safe(levelContext) + "\n"
        + safe(entityContext) + "\n"
        + safe(itemContext) + "\n"
        + safe(characterContext) + "\n"
        + safe(storyContext) + "\n"
        + "RECENT STORY (chỉ giữ continuity, không lặp nguyên văn):\n" + recent + "\n"
        + "READ-ONLY STATE: " + promptState.toString() + "\n"
        + "PLAYER ACTION: " + safe(action) + "\n"
        + "OUTPUT: chỉ JSON hợp lệ, không markdown. transitionTarget phải rỗng trừ khi Level Core nói route đã mở "
        + "và narration thực sự đi qua boundary; khi đó dùng đúng key được Level Core cho phép.\n"
        + "{\"reply\":\"phản hồi Game Master\",\"sceneLabel\":\"mô tả vị trí ngắn\","
        + "\"transitionTarget\":\"\",\"choices\":[{\"text\":\"Gợi ý 1\"}],\"encounterDialogue\":[]}";
  }

  private static String clip(String value, int max) {
    String text = safe(value);
    if (text.length() <= max) return text;
    return text.substring(0, max);
  }

  private static String safe(String value) {
    return value == null ? "" : value.trim();
  }
}

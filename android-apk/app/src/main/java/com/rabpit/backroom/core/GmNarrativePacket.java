package com.rabpit.backroom.core;

import org.json.JSONObject;

/** Builds the compact read-only narrative packet sent to the text provider. */
public final class GmNarrativePacket {
  public static final int MAX_RECENT_CONTEXT_CHARS = 2800;

  private GmNarrativePacket() {}

  public static JSONObject projectState(JSONObject state) throws Exception {
    JSONObject projected = state == null ? new JSONObject() : new JSONObject(state.toString());
    projected.remove("log");
    projected.remove("levelRoute");
    projected.remove("characterCanon");
    return projected;
  }

  public static String build(
      String levelContext,
      String entityContext,
      String itemContext,
      String characterContext,
      String recentContext,
      JSONObject state,
      String action,
      String gmStyleExamples) throws Exception {
    JSONObject promptState = projectState(state);
    String recent = clip(recentContext, MAX_RECENT_CONTEXT_CHARS);
    String style = clip(gmStyleExamples, 1800);

    return "Bạn là Game Master của text game Backrooms (xianxia x Backrooms).\n"
        + GmNarratorContract.promptContext() + "\n"
        + GmNarratorContract.caoMinhNarrativeCard() + "\n"
        + "VAI TRÒ GM: bạn quyết định diễn biến tự do tiếp theo dựa trên hành động người chơi, continuity và canon hiện có. "
        + "Không có cốt truyện, chương hay diễn biến định sẵn cần bám theo. Không ép người chơi quay về một tuyến cố định.\n"
        + "NGÔN NGỮ HIỂN THỊ: reply, sceneLabel, choices và encounterDialogue phải là tiếng Việt tự nhiên. "
        + "Chỉ giữ tiếng Anh cho tên riêng/tên chính thức cần thiết. Mỗi choices[].text phải viết hoàn toàn bằng tiếng Việt; "
        + "không trộn động từ, chỉ hướng hoặc mô tả môi trường tiếng Anh vào câu lựa chọn.\n"
        + style + "\n"
        + "CORE-OWNED: Java Core sở hữu Level/route, Entity spawn, Loot, Inventory, Party, Survival, Progression và Combat. "
        + "GM quyết định diễn biến và mô tả, nhưng không được tự sửa các state Core-owned. sceneLabel chỉ là nhãn mô tả; "
        + "Level chỉ đổi khi transitionTarget hợp lệ được Level Core cho phép.\n"
        + "EXPLORER CHOICES: trả 0-3 gợi ý hành động ngắn, cụ thể và phù hợp với tình huống hiện tại; "
        + "đây là gợi ý của GM, không phải nhánh kịch bản cố định. Nếu Entity đang đối đầu trực tiếp thì choices=[].\n"
        + "ENCOUNTER DIALOGUE: chỉ khi Character Core có pending intro; khi đó trả đúng 2-5 câu thoại. Nếu không thì [].\n"
        + safe(levelContext) + "\n"
        + safe(entityContext) + "\n"
        + safe(itemContext) + "\n"
        + safe(characterContext) + "\n"
        + "RECENT CONTEXT (chỉ giữ continuity, không lặp nguyên văn):\n" + recent + "\n"
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

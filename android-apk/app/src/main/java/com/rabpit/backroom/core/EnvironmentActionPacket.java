package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;

/** Builds and commits PLAYER ACTION responses that are intentionally isolated from authored Story. */
public final class EnvironmentActionPacket {
  public static final String SCOPE = "environment";
  private static final int MAX_RECENT_CONTEXT_CHARS = 2600;

  private EnvironmentActionPacket() {}

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
      String recentContext,
      JSONObject state,
      String action) throws Exception {
    return "Bạn xử lý PLAYER ACTION phụ trợ trong text game Backrooms (xianxia x Backrooms).\n"
        + GmNarratorContract.promptContext() + "\n"
        + GmNarratorContract.caoMinhNarrativeCard() + "\n"
        + "ENVIRONMENT-ONLY CONTRACT: chỉ phản hồi tương tác tức thời của Cao Minh với môi trường hiện tại. "
        + "Có thể mô tả quan sát hoặc hệ quả cục bộ, hợp lý và không tạo progression mới. "
        + "Không được tiến, resolve, thay thế, hé lộ trước hoặc tạo nhánh cho authored Story. "
        + "Không tạo Story choice. Không đổi Level/route/location, turn/thời gian, Entity/Combat, "
        + "Item/Loot/Inventory, Party, Survival hoặc Progression. Không cấp phần thưởng hay mở khóa.\n"
        + "Mọi Core state bên dưới là READ-ONLY. Nếu hành động đòi thay đổi state bị cấm, chỉ mô tả phần "
        + "tương tác môi trường có thể quan sát được và dừng tại đó.\n"
        + safe(levelContext) + "\n"
        + safe(entityContext) + "\n"
        + safe(itemContext) + "\n"
        + safe(characterContext) + "\n"
        + "RECENT VISIBLE CONTEXT (chỉ để hiểu scene hiện tại):\n"
        + clip(recentContext, MAX_RECENT_CONTEXT_CHARS) + "\n"
        + "READ-ONLY STATE: " + projectState(state).toString() + "\n"
        + "PLAYER ACTION: " + safe(action) + "\n"
        + "OUTPUT: chỉ JSON hợp lệ, không markdown. Chỉ được có trường reply.\n"
        + "{\"reply\":\"phản hồi ngắn của Game Master về tương tác môi trường\"}";
  }

  public static void appendExchange(JSONObject state, String action, String reply) throws Exception {
    JSONArray log = state.optJSONArray("log");
    if (log == null) log = new JSONArray();

    log.put(new JSONObject()
        .put("role", "player")
        .put("text", safe(action))
        .put("scope", SCOPE));

    JSONObject gmEntry = GmChoiceContract.gmEntry(safe(reply), new JSONObject(), state);
    gmEntry.remove("choices");
    gmEntry.remove("battleLog");
    gmEntry.put("scope", SCOPE);
    log.put(gmEntry);
    state.put("log", log);
  }

  private static String clip(String value, int max) {
    String text = safe(value);
    return text.length() <= max ? text : text.substring(text.length() - max);
  }

  private static String safe(String value) {
    return value == null ? "" : value.trim();
  }
}

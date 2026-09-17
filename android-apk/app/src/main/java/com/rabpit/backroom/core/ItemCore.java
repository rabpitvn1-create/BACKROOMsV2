package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/** Core-owned loot and consumable item rules. AI is never allowed to create loot. */
final class ItemCore {
  static final int CHEST_SPAWN_RATE_PERCENT = 3;
  static final int ENTITY_DROP_MIN_PERCENT = 10;
  static final int ENTITY_DROP_MAX_PERCENT = 20;
  static final String OPEN_CHEST_ACTION = "__loot:open_chest";
  static final String ALMOND_WATER_ID = "almond-water";
  static final String BANDAGE_ID = "bandage";

  private static final String CHEST_PRESENT = "chestPresent";
  private static final String CHEST_SOURCE = "core_exploration_roll";

  void prepareExplorationLoot(JSONObject state) throws Exception {
    JSONObject flags = flags(state);
    if (flags.optBoolean(CHEST_PRESENT, false)) return;
    int roll = ThreadLocalRandom.current().nextInt(100);
    if (!shouldSpawnChest(roll)) return;
    flags.put(CHEST_PRESENT, true);
    flags.put("chestSource", CHEST_SOURCE);
    flags.put("chestSpawnRatePercent", CHEST_SPAWN_RATE_PERCENT);
    flags.put("chestSpawnTurn", Math.max(1, state.optInt("turn", 1)));
    state.put("flags", flags);
  }

  void validateAndApply(JSONObject before, JSONObject candidate) throws Exception {
    JSONObject beforeFlags = flags(before);
    JSONObject candidateFlags = flags(candidate);
    if (beforeFlags.optBoolean(CHEST_PRESENT, false)) {
      candidateFlags.put(CHEST_PRESENT, true);
      copy(beforeFlags, candidateFlags, "chestSource");
      copy(beforeFlags, candidateFlags, "chestSpawnRatePercent");
      copy(beforeFlags, candidateFlags, "chestSpawnTurn");
    } else {
      candidateFlags.remove(CHEST_PRESENT);
      candidateFlags.remove("chestSource");
      candidateFlags.remove("chestSpawnRatePercent");
      candidateFlags.remove("chestSpawnTurn");
    }
    candidate.put("flags", candidateFlags);
  }

  boolean isOpenChestAction(String action) {
    String value = action == null ? "" : action.trim().toLowerCase(Locale.ROOT);
    return OPEN_CHEST_ACTION.equals(action)
        || value.equals("mở rương")
        || value.equals("mo ruong")
        || value.equals("open chest");
  }

  String openChest(JSONObject state) throws Exception {
    JSONObject flags = flags(state);
    if (!flags.optBoolean(CHEST_PRESENT, false)) {
      throw new IllegalStateException("Không có rương để mở.");
    }
    String itemName = grantLootItem(state, ThreadLocalRandom.current().nextInt(100));
    flags.put(CHEST_PRESENT, false);
    flags.put("lastChestOpenedTurn", Math.max(1, state.optInt("turn", 1)));
    flags.remove("chestSource");
    flags.remove("chestSpawnRatePercent");
    flags.remove("chestSpawnTurn");
    state.put("flags", flags);
    return itemName;
  }

  String applyItemAction(JSONObject state, String itemId, String operation, String targetId, int requestedQuantity)
      throws Exception {
    String op = operation == null ? "" : operation.trim().toLowerCase(Locale.ROOT);
    JSONArray inventory = state.optJSONArray("inventory");
    if (inventory == null) throw new IllegalArgumentException("Inventory đang trống.");

    int index = findItemIndex(inventory, itemId);
    if (index < 0) throw new IllegalArgumentException("Không tìm thấy vật phẩm.");
    JSONObject stack = inventory.getJSONObject(index);
    String normalizedId = normalizedItemId(stack);
    int available = Math.max(1, stack.optInt("quantity", 1));
    int quantity = Math.max(1, Math.min(requestedQuantity, available));
    String name = stack.optString("name", itemName(normalizedId));

    if ("discard".equals(op)) {
      consumeStack(inventory, index, quantity);
      state.put("inventory", inventory);
      return "Đã vứt bỏ " + name + " x" + quantity + ".";
    }

    if (!isConsumable(normalizedId)) {
      throw new IllegalArgumentException(name + " không phải vật phẩm tiêu hao.");
    }

    JSONObject target;
    String targetName;
    if ("share".equals(op)) {
      target = findPartyMember(state.optJSONArray("party"), targetId);
      if (target == null) throw new IllegalArgumentException("Không tìm thấy nhân vật trong party.");
      targetName = target.optString("name", target.optString("id", "Đồng đội"));
    } else if ("use".equals(op)) {
      target = state.optJSONObject("player");
      if (target == null) {
        target = new JSONObject().put("name", "Kai Akechi");
        state.put("player", target);
      }
      targetName = target.optString("name", "Kai Akechi");
    } else {
      throw new IllegalArgumentException("Hành động vật phẩm không hợp lệ.");
    }

    String effectText = applyEffects(target, normalizedId, quantity);
    consumeStack(inventory, index, quantity);
    state.put("inventory", inventory);
    if ("share".equals(op)) {
      return "Đã chia sẻ " + name + " x" + quantity + " cho " + targetName + ". " + effectText;
    }
    return targetName + " sử dụng " + name + " x" + quantity + ". " + effectText;
  }

  String promptContext(JSONObject state) {
    JSONObject flags = state == null ? null : state.optJSONObject("flags");
    boolean chest = flags != null && flags.optBoolean(CHEST_PRESENT, false);
    if (chest) {
      return "ITEM CORE: một Rương loot thật đang hiện diện trong scene. Có thể mô tả Rương, nhưng tuyệt đối không tự mở, "
          + "không tự cho vật phẩm và không sửa inventory. UI/Core sẽ xử lý Mở Rương; rương luôn có vật phẩm 100%.";
    }
    return "ITEM CORE: không có loot rời trong scene. Không tự sinh, nhặt, trao hoặc thêm item vào inventory. "
        + "Loot consumable chỉ đến từ Entity drop hoặc Rương do Core spawn.";
  }

  static boolean shouldSpawnChest(int roll) {
    return roll >= 0 && roll < CHEST_SPAWN_RATE_PERCENT;
  }

  static int entityDropRatePercent(String entityKey) {
    String key = entityKey == null ? "" : entityKey.trim().toLowerCase(Locale.ROOT);
    int span = ENTITY_DROP_MAX_PERCENT - ENTITY_DROP_MIN_PERCENT + 1;
    return ENTITY_DROP_MIN_PERCENT + Math.floorMod(key.hashCode(), span);
  }

  static boolean shouldDropEntityLoot(int roll, int ratePercent) {
    int rate = Math.max(ENTITY_DROP_MIN_PERCENT, Math.min(ENTITY_DROP_MAX_PERCENT, ratePercent));
    return roll >= 0 && roll < rate;
  }

  static int itemEffectValue(String itemId, String stat) {
    String id = itemId == null ? "" : itemId.trim().toLowerCase(Locale.ROOT);
    String key = stat == null ? "" : stat.trim().toLowerCase(Locale.ROOT);
    if (ALMOND_WATER_ID.equals(id)) {
      if ("hunger".equals(key)) return 50;
      if ("thirst".equals(key)) return 100;
    }
    if (BANDAGE_ID.equals(id) && "hp".equals(key)) return 15;
    return 0;
  }

  static String grantLootItem(JSONObject state, int selector) throws Exception {
    String id = Math.floorMod(selector, 100) < 50 ? ALMOND_WATER_ID : BANDAGE_ID;
    addItem(state, id, 1);
    return itemName(id);
  }

  private static boolean isConsumable(String id) {
    return ALMOND_WATER_ID.equals(id) || BANDAGE_ID.equals(id);
  }

  private static String itemName(String id) {
    if (ALMOND_WATER_ID.equals(id)) return "Almond Water";
    if (BANDAGE_ID.equals(id)) return "Bandage";
    return id == null || id.isEmpty() ? "Vật phẩm" : id;
  }

  private static JSONObject itemDefinition(String id, int quantity) throws Exception {
    JSONObject effects = new JSONObject();
    if (ALMOND_WATER_ID.equals(id)) {
      effects.put("hunger", 50).put("thirst", 100);
    } else if (BANDAGE_ID.equals(id)) {
      effects.put("hp", 15);
    }
    return new JSONObject()
        .put("id", id)
        .put("name", itemName(id))
        .put("quantity", Math.max(1, quantity))
        .put("kind", "consumable")
        .put("stackable", true)
        .put("effects", effects);
  }

  private static void addItem(JSONObject state, String id, int quantity) throws Exception {
    JSONArray inventory = state.optJSONArray("inventory");
    if (inventory == null) inventory = new JSONArray();
    for (int i = 0; i < inventory.length(); i++) {
      JSONObject item = inventory.optJSONObject(i);
      if (item == null || !id.equals(normalizedItemId(item))) continue;
      item.put("id", id);
      item.put("name", itemName(id));
      item.put("kind", "consumable");
      item.put("stackable", true);
      item.put("effects", itemDefinition(id, 1).getJSONObject("effects"));
      item.put("quantity", Math.max(1, item.optInt("quantity", 1)) + Math.max(1, quantity));
      state.put("inventory", inventory);
      return;
    }
    inventory.put(itemDefinition(id, quantity));
    state.put("inventory", inventory);
  }

  private static int findItemIndex(JSONArray inventory, String itemId) {
    String requested = itemId == null ? "" : itemId.trim().toLowerCase(Locale.ROOT);
    for (int i = 0; i < inventory.length(); i++) {
      JSONObject item = inventory.optJSONObject(i);
      if (item == null) continue;
      String id = normalizedItemId(item);
      String name = item.optString("name", "").trim().toLowerCase(Locale.ROOT);
      if (requested.equals(id) || requested.equals(name)) return i;
    }
    return -1;
  }

  private static String normalizedItemId(JSONObject item) {
    String id = item == null ? "" : item.optString("id", "").trim();
    if (!id.isEmpty()) return id.toLowerCase(Locale.ROOT);
    return GameCoreRules.stableItemId(item == null ? "" : item.optString("name", ""));
  }

  private static void consumeStack(JSONArray inventory, int index, int quantity) throws Exception {
    JSONObject item = inventory.getJSONObject(index);
    int remaining = Math.max(1, item.optInt("quantity", 1)) - quantity;
    if (remaining <= 0) inventory.remove(index);
    else item.put("quantity", remaining);
  }

  private static JSONObject findPartyMember(JSONArray party, String targetId) {
    if (party == null) return null;
    String requested = targetId == null ? "" : targetId.trim().toLowerCase(Locale.ROOT);
    for (int i = 0; i < party.length(); i++) {
      JSONObject member = party.optJSONObject(i);
      if (member == null) continue;
      String id = member.optString("id", "").trim().toLowerCase(Locale.ROOT);
      String name = member.optString("name", "").trim().toLowerCase(Locale.ROOT);
      if (requested.equals(id) || requested.equals(name) || requested.equals(String.valueOf(i))) return member;
    }
    return null;
  }

  private static String applyEffects(JSONObject target, String itemId, int quantity) throws Exception {
    if (ALMOND_WATER_ID.equals(itemId)) {
      int hunger = itemEffectValue(itemId, "hunger") * quantity;
      int thirst = itemEffectValue(itemId, "thirst") * quantity;
      increase(target, "hunger", "maxHunger", hunger, 100, false);
      increase(target, "thirst", "maxThirst", thirst, 100, false);
      return "Hunger +" + hunger + ", Thirsty +" + thirst + ".";
    }
    int heal = itemEffectValue(itemId, "hp") * quantity;
    increase(target, "hp", "maxHp", heal, 100, true);
    return "HP +" + heal + ".";
  }

  private static void increase(JSONObject target, String valueKey, String maxKey, int amount, int fallbackMax,
                               boolean missingStartsFull) throws Exception {
    int max = Math.max(1, target.optInt(maxKey, fallbackMax));
    int current = target.has(valueKey)
        ? Math.max(0, target.optInt(valueKey, 0))
        : (missingStartsFull ? max : 0);
    target.put(maxKey, max);
    target.put(valueKey, Math.min(max, current + Math.max(0, amount)));
  }

  private JSONObject flags(JSONObject state) throws Exception {
    JSONObject flags = state.optJSONObject("flags");
    if (flags == null) flags = new JSONObject();
    state.put("flags", flags);
    return flags;
  }

  private void copy(JSONObject source, JSONObject target, String key) throws Exception {
    if (source.has(key)) target.put(key, source.get(key));
    else target.remove(key);
  }
}

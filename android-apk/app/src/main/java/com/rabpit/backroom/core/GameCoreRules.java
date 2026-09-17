package com.rabpit.backroom.core;

import java.util.Locale;
import java.util.regex.Pattern;

final class GameCoreRules {
  private static final Pattern OMNIVAULT_WITHDRAW = Pattern.compile(
      "(?:lấy|rút|triệu hồi).*(?:ra khỏi|khỏi|từ).*(?:omnivault|nhẫn|kho)",
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  private static final Pattern DIRECT_PICKUP = Pattern.compile(
      "(?:^|\\s)(?:nhặt|lượm|cầm\\s+lên|lấy(?:\\s+lên)?|thu\\s+hồi|tịch\\s+thu|nhận(?:\\s+lấy)?|pick\\s+up|take|receive)(?:\\s|$)",
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  private static final Pattern INVENTORY_ASSERTION = Pattern.compile(
      "(?:thêm|bỏ|đưa).{0,80}(?:vào|trong)\\s+(?:inventory|kho đồ|túi đồ)",
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  private static final Pattern RESTORE = Pattern.compile(
      "(?:hoàn nguyên|restore|khôi phục vật)",
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  private static final Pattern INVENTORY_QUERY = Pattern.compile(
      "(?:inventory|kho đồ|túi đồ).*(?:gì|xem|kiểm tra|hiện tại)|(?:xem|kiểm tra).*(?:inventory|kho đồ|túi đồ)",
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  private static final Pattern PARTY_QUERY = Pattern.compile(
      "(?:party|đội hình|nhóm).*(?:ai|xem|kiểm tra|hiện tại)|(?:xem|kiểm tra).*(?:party|đội hình)",
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  private static final Pattern LONG_REST = Pattern.compile("(?:ngủ|sleep)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  private static final Pattern REST = Pattern.compile("(?:nghỉ|rest)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  private static final Pattern MOVE = Pattern.compile("(?:đi|chạy|di chuyển|leo|bò|walk|run|move)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  private GameCoreRules() {}

  static boolean isDirectPlayerPickupAction(String action) {
    String text = action == null ? "" : action.trim();
    if (text.isEmpty() || OMNIVAULT_WITHDRAW.matcher(text).find()) return false;
    return DIRECT_PICKUP.matcher(text).find() || INVENTORY_ASSERTION.matcher(text).find();
  }

  static boolean isRestoreAction(String action) {
    return RESTORE.matcher(action == null ? "" : action).find();
  }

  static boolean inventoryMutationLocked(String action) {
    return isDirectPlayerPickupAction(action) || isRestoreAction(action);
  }

  static boolean isInventoryQuery(String action) {
    return INVENTORY_QUERY.matcher(action == null ? "" : action).find();
  }

  static boolean isPartyQuery(String action) {
    return PARTY_QUERY.matcher(action == null ? "" : action).find();
  }

  static int estimateMinutes(String action) {
    String text = action == null ? "" : action.trim();
    if (text.isEmpty()) return 0;
    if (LONG_REST.matcher(text).find()) return 30;
    if (REST.matcher(text).find()) return 10;
    if (MOVE.matcher(text).find()) return 5;
    return Math.min(5, Math.max(1, text.length() / 80 + 1));
  }

  static String stableItemId(String name) {
    String normalized = name == null ? "" : name.toLowerCase(Locale.ROOT)
        .replaceAll("[^\\p{L}\\p{N}]+", "-")
        .replaceAll("^-+|-+$", "");
    if (!normalized.isEmpty()) return normalized;
    return "item-" + Integer.toUnsignedString(name == null ? 0 : name.hashCode());
  }
}

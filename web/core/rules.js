// Port of com.rabpit.backroom.core.GameCoreRules (69 lines, pure regex/Locale).
// Line-faithful: same patterns, same order, same helper semantics.
//
// Java -> JS semantic bridges used here:
// - Java Pattern flags CASE_INSENSITIVE | UNICODE_CASE  ==  JS flags "iu"
//   (the "u" flag is what makes JS case folding + \p{...} Unicode-aware).
// - Java \s is ASCII-only ([ \t\n\x0B\f\r]); JS \s also matches NBSP, U+3000, ...
//   The Java pattern sources below are kept verbatim and every \s is substituted
//   with the exact ASCII class before compiling, removing that drift class.
// - Java String.trim() removes only chars <= U+0020; JS trim() removes Unicode
//   whitespace. javaTrim() reproduces the Java behaviour and is used everywhere
//   the Java code trims.
// - shortcut: Java "." also excludes U+0085 (NEL) while JS "." matches it; the
//   patterns' .* / .{0,80} therefore differ on text containing U+0085. Accepted:
//   NEL does not occur in real player text. Revisit only if a fixture ever shows it.

const JAVA_WS = "[ \\t\\n\\u000B\\f\\r]";

function javaPattern(source) {
  return new RegExp(source.replaceAll("\\s", JAVA_WS), "iu");
}

const OMNIVAULT_WITHDRAW = javaPattern(
  String.raw`(?:lấy|rút|triệu hồi).*(?:ra khỏi|khỏi|từ).*(?:omnivault|nhẫn|kho)`);
const DIRECT_PICKUP = javaPattern(
  String.raw`(?:^|\s)(?:nhặt|lượm|cầm\s+lên|lấy(?:\s+lên)?|thu\s+hồi|tịch\s+thu|nhận(?:\s+lấy)?|pick\s+up|take|receive)(?:\s|$)`);
const INVENTORY_ASSERTION = javaPattern(
  String.raw`(?:thêm|bỏ|đưa).{0,80}(?:vào|trong)\s+(?:inventory|kho đồ|túi đồ)`);
const RESTORE = javaPattern(
  String.raw`(?:hoàn nguyên|restore|khôi phục vật)`);
const INVENTORY_QUERY = javaPattern(
  String.raw`(?:inventory|kho đồ|túi đồ).*(?:gì|xem|kiểm tra|hiện tại)|(?:xem|kiểm tra).*(?:inventory|kho đồ|túi đồ)`);
const PARTY_QUERY = javaPattern(
  String.raw`(?:party|đội hình|nhóm).*(?:ai|xem|kiểm tra|hiện tại)|(?:xem|kiểm tra).*(?:party|đội hình)`);
const LONG_REST = javaPattern(String.raw`(?:ngủ|sleep)`);
const REST = javaPattern(String.raw`(?:nghỉ|rest)`);
const MOVE = javaPattern(String.raw`(?:đi|chạy|di chuyển|leo|bò|walk|run|move)`);

/** Java String.trim(): strips leading/trailing chars <= U+0020 only. */
export function javaTrim(text) {
  return text.replace(/^[\u0000-\u0020]+|[\u0000-\u0020]+$/g, "");
}

export function isDirectPlayerPickupAction(action) {
  const text = action == null ? "" : javaTrim(action);
  if (text === "" || OMNIVAULT_WITHDRAW.test(text)) return false;
  return DIRECT_PICKUP.test(text) || INVENTORY_ASSERTION.test(text);
}

export function isRestoreAction(action) {
  return RESTORE.test(action == null ? "" : action);
}

export function inventoryMutationLocked(action) {
  return isDirectPlayerPickupAction(action) || isRestoreAction(action);
}

export function isInventoryQuery(action) {
  return INVENTORY_QUERY.test(action == null ? "" : action);
}

export function isPartyQuery(action) {
  return PARTY_QUERY.test(action == null ? "" : action);
}

export function estimateMinutes(action) {
  const text = action == null ? "" : javaTrim(action);
  if (text === "") return 0;
  if (LONG_REST.test(text)) return 30;
  if (REST.test(text)) return 10;
  if (MOVE.test(text)) return 5;
  // Java: Math.min(5, Math.max(1, text.length() / 80 + 1)) with int division;
  // String.length() and JS .length both count UTF-16 code units.
  return Math.min(5, Math.max(1, Math.floor(text.length / 80) + 1));
}

// Java String.hashCode() reinterpreted via Integer.toUnsignedString().
function javaHashCodeUnsigned(text) {
  let h = 0;
  for (let i = 0; i < text.length; i++) h = (31 * h + text.charCodeAt(i)) | 0;
  return h >>> 0;
}

export function stableItemId(name) {
  // Java: name.toLowerCase(Locale.ROOT), then [^\p{L}\p{N}]+ -> "-", strip edge "-".
  const normalized = (name == null ? "" : name)
    .toLowerCase()
    .replace(/[^\p{L}\p{N}]+/gu, "-")
    .replace(/^-+|-+$/g, "");
  if (normalized !== "") return normalized;
  return "item-" + javaHashCodeUnsigned(name == null ? "" : name);
}

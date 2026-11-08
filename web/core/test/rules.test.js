// Re-expression of android-apk/app/src/test/java/.../GameCoreRulesTest.java
// (all 5 test methods, assertions verbatim) + fixture extension: every one of
// the 9 Java patterns gets Vietnamese action strings, including the
// UNICODE_CASE folding checks (all-caps Vietnamese) — synthesis §5/C1 evidence.
import { test } from "node:test";
import assert from "node:assert/strict";
import {
  estimateMinutes,
  inventoryMutationLocked,
  isDirectPlayerPickupAction,
  isInventoryQuery,
  isPartyQuery,
  isRestoreAction,
  stableItemId,
} from "../rules.js";

// ---- GameCoreRulesTest, verbatim ----

test("directPickupIsRejected (Java test)", () => {
  assert.equal(isDirectPlayerPickupAction("Cao Minh nhặt chai Almond Water lên"), true);
  assert.equal(isDirectPlayerPickupAction("take the key"), true);
});

test("storageWithdrawalIsNotTreatedAsPickup (Java test)", () => {
  assert.equal(isDirectPlayerPickupAction("lấy thanh kiếm ra khỏi kho"), false);
});

test("restoreLocksInventoryMutation (Java test)", () => {
  assert.equal(inventoryMutationLocked("hoàn nguyên vật thể trong nhẫn"), true);
});

test("normalActionDoesNotLockInventory (Java test)", () => {
  assert.equal(inventoryMutationLocked("Cao Minh kiểm tra hành lang phía trước"), false);
});

test("timeCostIsDeterministic (Java test)", () => {
  assert.equal(estimateMinutes("Cao Minh ngủ một lúc"), 30);
  assert.equal(estimateMinutes("Cao Minh nghỉ tại đây"), 10);
  assert.equal(estimateMinutes("Cao Minh chạy sang phòng kế bên"), 5);
  assert.equal(estimateMinutes("Cao Minh nhìn quanh"), 1);
});

// ---- STORAGE_WITHDRAW fixtures ----

test("STORAGE_WITHDRAW: rút/triệu hồi + từ/khỏi + kho/nhẫn are not pickups", () => {
  assert.equal(isDirectPlayerPickupAction("rút khẩu súng từ kho"), false);
  assert.equal(isDirectPlayerPickupAction("Triệu hồi thanh kiếm khỏi nhẫn"), false);
  assert.equal(isDirectPlayerPickupAction("LẤY ĐẠN RA KHỎI KHO"), false); // full caps folding
});

test("STORAGE_WITHDRAW: leaving a non-vault place is still a pickup", () => {
  assert.equal(isDirectPlayerPickupAction("lấy chìa khóa ra khỏi phòng"), true);
  assert.equal(isDirectPlayerPickupAction("lấy đồ trong kho"), true); // no ra khỏi/khỏi/từ
});

// ---- DIRECT_PICKUP fixtures ----

test("DIRECT_PICKUP: every verb in the alternation", () => {
  for (const action of [
    "lượm viên đạn lên",
    "Cao Minh cầm lên khẩu súng",
    "Cao Minh lấy hộp cứu thương",
    "thu hồi vật chứng",
    "tịch thu chìa khóa",
    "nhận lấy phần thưởng",
    "Cao Minh nhận nhiệm vụ mới",
    "pick up the key",
    "Cao Minh receive the package",
  ]) {
    assert.equal(isDirectPlayerPickupAction(action), true, action);
  }
});

test("DIRECT_PICKUP: word-boundary negatives", () => {
  assert.equal(isDirectPlayerPickupAction("takeaway food for Cao Minh"), false); // take glued to 'a'
  assert.equal(isDirectPlayerPickupAction("Cao Minh tiếpnhận thông báo"), false); // nhận glued to 'p'
  assert.equal(isDirectPlayerPickupAction("Cao Minh mở khóa cửa"), false);
  assert.equal(isDirectPlayerPickupAction(""), false);
  assert.equal(isDirectPlayerPickupAction(null), false);
  assert.equal(isDirectPlayerPickupAction("   "), false);
});

// ---- INVENTORY_ASSERTION fixtures ----

test("INVENTORY_ASSERTION: thêm/bỏ/đưa ... vào/trong + inventory|kho đồ|túi đồ", () => {
  assert.equal(isDirectPlayerPickupAction("thêm đạn vào kho đồ"), true);
  assert.equal(isDirectPlayerPickupAction("bỏ chìa khóa vào inventory"), true);
  assert.equal(isDirectPlayerPickupAction("đưa vật phẩm vào túi đồ"), true);
  assert.equal(isDirectPlayerPickupAction("Thêm Đạn Vào Kho Đồ"), true); // title-case folding
});

test("INVENTORY_ASSERTION: vào a non-inventory target does not lock", () => {
  assert.equal(isDirectPlayerPickupAction("thêm gia vị vào món ăn"), false);
  assert.equal(isDirectPlayerPickupAction("đưa Cao Minh đến kho đồ"), false); // no vào/trong
});

// ---- RESTORE fixtures ----

test("RESTORE: hoàn nguyên / restore / khôi phục vật", () => {
  assert.equal(isRestoreAction("hoàn nguyên vật thể trong nhẫn"), true);
  assert.equal(isRestoreAction("Restore vật phẩm"), true);
  assert.equal(isRestoreAction("khôi phục vật phẩm bị hỏng"), true);
  assert.equal(inventoryMutationLocked("khôi phục vật phẩm bị hỏng"), true);
});

test("RESTORE: khôi phục without vật does not lock", () => {
  assert.equal(isRestoreAction("khôi phục năng lượng cho Cao Minh"), false);
  assert.equal(inventoryMutationLocked("khôi phục năng lượng cho Cao Minh"), false);
});

// ---- INVENTORY_QUERY fixtures ----

test("INVENTORY_QUERY: both alternation directions", () => {
  assert.equal(isInventoryQuery("xem inventory"), true);
  assert.equal(isInventoryQuery("kiểm tra kho đồ"), true);
  assert.equal(isInventoryQuery("Inventory có gì?"), true);
  assert.equal(isInventoryQuery("kho đồ hiện tại có gì"), true);
  assert.equal(isInventoryQuery("túi đồ của Cao Minh có gì không"), true);
  assert.equal(isInventoryQuery("XEM TÚI ĐỒ"), true); // full caps folding
});

test("INVENTORY_QUERY: negatives", () => {
  assert.equal(isInventoryQuery("Cao Minh mở kho đồ"), false);
  assert.equal(isInventoryQuery("Cao Minh kiểm tra hành lang phía trước"), false);
  assert.equal(isInventoryQuery(null), false);
});

// ---- PARTY_QUERY fixtures ----

test("PARTY_QUERY: both alternation directions", () => {
  assert.equal(isPartyQuery("party có ai không"), true);
  assert.equal(isPartyQuery("xem đội hình"), true);
  assert.equal(isPartyQuery("đội hình hiện tại"), true);
  assert.equal(isPartyQuery("nhóm có ai"), true);
});

test("PARTY_QUERY: nhóm is absent from the second alternation (Java asymmetry)", () => {
  // Pattern B is (xem|kiểm tra).*(party|đội hình) — nhóm does not appear there,
  // and pattern A needs the noun before the query word. Java returns false.
  assert.equal(isPartyQuery("kiểm tra nhóm"), false);
  assert.equal(isPartyQuery("Cao Minh lập nhóm mới"), false);
  assert.equal(isPartyQuery(null), false);
});

// ---- estimateMinutes fixtures ----

test("estimateMinutes: rest/move precedence and defaults", () => {
  assert.equal(estimateMinutes("Cao Minh sleep now"), 30);
  assert.equal(estimateMinutes("ngủ nghỉ một chút"), 30); // LONG_REST beats REST
  assert.equal(estimateMinutes("Cao Minh rest here"), 10);
  assert.equal(estimateMinutes("Cao Minh đi bộ ra cửa"), 5);
  assert.equal(estimateMinutes("Cao Minh leo lên cầu thang"), 5);
  assert.equal(estimateMinutes(""), 0);
  assert.equal(estimateMinutes(null), 0);
  assert.equal(estimateMinutes("a".repeat(79)), 1); // 79/80 -> 0 + 1
  assert.equal(estimateMinutes("a".repeat(200)), 3); // 200/80 -> 2 + 1
  assert.equal(estimateMinutes("a".repeat(400)), 5); // capped at 5
});

// ---- stableItemId fixtures ----

test("stableItemId: slugs, Vietnamese, hashCode fallback", () => {
  assert.equal(stableItemId("Almond Water"), "almond-water");
  assert.equal(stableItemId("W.W Magnum"), "w-w-magnum");
  assert.equal(stableItemId("Đạn 9mm"), "đạn-9mm");
  assert.equal(stableItemId("Thuốc Hồi Phục"), "thuốc-hồi-phục");
  assert.equal(stableItemId("  Nước   Ép  "), "nước-ép");
  assert.equal(stableItemId("Café ☕ Đặc Biệt"), "café-đặc-biệt");
  assert.equal(stableItemId("!!!"), "item-32769"); // Java hashCode("!!!") unsigned
  assert.equal(stableItemId(""), "item-0");
  assert.equal(stableItemId(null), "item-0");
});

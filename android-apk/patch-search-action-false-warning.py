from pathlib import Path

ROOT = Path(__file__).resolve().parent
FACADE = ROOT / "app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt"
TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/OmnivaultNaturalFlowTest.kt"

text = FACADE.read_text(encoding="utf-8")

# PICKUP_ITEM classification must not by itself reject unrelated prose. Direct
# acquisition wording remains unavailable because item acquisition is owned by
# the Android Entity/ItemBox drop authority.
old_guard = "    if (isDirectPlayerPickupAction(action) || interpreted.candidates.any { it.intent == GameIntent.PICKUP_ITEM }) {\n"
new_guard = "    if (isDirectPlayerPickupAction(action)) {\n"
if old_guard in text:
    text = text.replace(old_guard, new_guard, 1)

# Candidate inventory additions are ignored by GameCoreFacade. Keep the lock
# focused on explicit player pickup language; no retired Omnivault exception.
old_lock = "    val inventoryLocked = isDirectPlayerPickupAction(action) || GameIntent.PICKUP_ITEM in actionIntents\n"
new_lock = "    val inventoryLocked = isDirectPlayerPickupAction(action)\n"
if old_lock in text:
    text = text.replace(old_lock, new_lock, 1)

old_inventory_assertion = r'''    val inventoryAssertion = Regex("(?:thêm|bỏ|đưa).{0,80}(?:vào|trong)\\s+(?:inventory|kho đồ|túi đồ)", RegexOption.IGNORE_CASE)
'''
new_inventory_assertion = r'''    val inventoryAssertion = Regex("(?:thêm|đưa).{0,80}(?:vào|trong)\\s+(?:inventory|kho đồ|túi đồ)", RegexOption.IGNORE_CASE)
'''
if old_inventory_assertion in text:
    text = text.replace(old_inventory_assertion, new_inventory_assertion, 1)

translations = {
    '"precise_content_amount_forbidden" -> "This action is not available."': '"precise_content_amount_forbidden" -> "Hành động này không khả dụng với lượng nội dung được chỉ định."',
    '"item_content_empty" -> "This action is not available."': '"item_content_empty" -> "Vật phẩm này hiện không có nội dung khả dụng."',
    '"insufficient_item_quantity", "item_not_owned" -> "This action is not available."': '"insufficient_item_quantity", "item_not_owned" -> "Kai không có đủ vật phẩm cần thiết cho hành động này."',
    'else -> "This action is not available."': 'else -> "Hành động này không khả dụng trong trạng thái hiện tại."',
}
for old, new in translations.items():
    if old in text:
        text = text.replace(old, new)

pickup_line = '      "player_pickup_unavailable" -> "Item mới chỉ có thể nhận từ Entity drop hoặc ItemBox drop."\n'
party_anchor = '      "party_full" -> "Party đã đủ tối đa bốn thành viên."\n'
if pickup_line not in text and party_anchor in text:
    text = text.replace(party_anchor, pickup_line + party_anchor, 1)

FACADE.write_text(text, encoding="utf-8")

# This regression suite belonged to the retired Omnivault system and must not
# be recreated by the nested legacy patch chain.
if TEST.exists():
    TEST.unlink()

facade = FACADE.read_text(encoding="utf-8")
for forbidden in ("GameIntent.OMNIVAULT_", "OmnivaultCommand", "world_consequence"):
    if forbidden in facade:
        raise RuntimeError(f"Retired item authority survived false-warning patch: {forbidden}")

print("Pickup false-warning compatibility applied without Omnivault or generic-loot authority.")

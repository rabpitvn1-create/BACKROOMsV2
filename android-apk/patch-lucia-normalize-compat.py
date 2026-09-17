from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
SYSTEM = CORE / "CharacterEquipmentSystem.kt"
POLICY = CORE / "InventoryPolicy.kt"

text = SYSTEM.read_text(encoding="utf-8")
marker = "    val input = LuciaCanon.ensure(source)\n"
if marker not in text:
    old = """  private fun normalizeInternal(input: GameState, seedStarting: Boolean, fillStartingHp: Boolean): GameState {
    val inventories = input.inventories.toMutableMap()
"""
    new = """  private fun normalizeInternal(source: GameState, seedStarting: Boolean, fillStartingHp: Boolean): GameState {
    val input = LuciaCanon.ensure(source)
    val inventories = input.inventories.toMutableMap()
"""
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"Lucia final normalizer anchor: expected exactly 1, found {count}")
    text = text.replace(old, new, 1)
if marker not in text:
    raise RuntimeError("Lucia final normalizer contract missing")
SYSTEM.write_text(text, encoding="utf-8")

policy = POLICY.read_text(encoding="utf-8")
if '  val LUCIA = InventoryProfile(maxTypes = 3, maxPerType = 100)\n' not in policy:
    anchor = '  val NORMAL = InventoryProfile(maxTypes = 2, maxPerType = 2)\n'
    if anchor not in policy:
        raise RuntimeError("Lucia inventory profile anchor missing")
    policy = policy.replace(anchor, '  val LUCIA = InventoryProfile(maxTypes = 3, maxPerType = 100)\n' + anchor, 1)
route = '    if (characterId == KAI_ID) return KAI\n'
if '    if (characterId == LUCIA_ID) return LUCIA\n' not in policy:
    if route not in policy:
        raise RuntimeError("Lucia inventory routing anchor missing")
    policy = policy.replace(route, route + '    if (characterId == LUCIA_ID) return LUCIA\n', 1)
POLICY.write_text(policy, encoding="utf-8")
print("Lucia compatibility applied to final equipment normalizer and inventory policy stack.")

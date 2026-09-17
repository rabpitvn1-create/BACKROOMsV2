from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
SYSTEM = CORE / "CharacterEquipmentSystem.kt"
POLICY = CORE / "InventoryPolicy.kt"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"

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

# The Iris/Syvial deterministic commit is installed earlier in the patch chain.
# Keep lastRolls directly adjacent to the final flags commit because the Lucia
# layer deliberately uses that stable three-line tail as its insertion contract.
main = MAIN.read_text(encoding="utf-8")
misordered = '''    flags.put("lastRolls", rolls);
    if (rollSuccess(rolls, "irisReunion")) {
'''
ordered = '''    if (rollSuccess(rolls, "irisReunion")) {
'''
if misordered in main:
    main = main.replace(misordered, ordered, 1)
    tail = '''    state.put("flags", flags);
    return state;
  }
'''
    stable_tail = '''    flags.put("lastRolls", rolls);
    state.put("flags", flags);
    return state;
  }
'''
    if main.count(tail) != 1:
        raise RuntimeError(f"Lucia encounter tail relocation: expected exactly 1 tail, found {main.count(tail)}")
    main = main.replace(tail, stable_tail, 1)

stable_tail = '''    flags.put("lastRolls", rolls);
    state.put("flags", flags);
    return state;
  }
'''
if stable_tail not in main:
    raise RuntimeError("Lucia deterministic encounter insertion tail missing after follower restore")
MAIN.write_text(main, encoding="utf-8")

print("Lucia compatibility applied to final equipment normalizer, inventory policy and encounter insertion tail.")

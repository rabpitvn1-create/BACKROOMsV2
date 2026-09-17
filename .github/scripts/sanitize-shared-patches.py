from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
APK = ROOT / "android-apk"
TARGET = re.compile(
    r"MadGod|Mad God|madgod|mad god|AN_NHIEN|AnNhien|an_nhien|an-nhien|"
    r"(?<![\w])An Nhiên(?![\w])|(?<![\w])An Nhien(?![\w])|annhien",
    re.I,
)

def path(name: str) -> Path:
    return APK / name

def read(name: str) -> str:
    return path(name).read_text(encoding="utf-8")

def write(name: str, text: str) -> None:
    path(name).write_text(text, encoding="utf-8")

def assert_clean(name: str, text: str) -> None:
    m = TARGET.search(text)
    if m:
        line = text[:m.start()].count("\n") + 1
        raise RuntimeError(f"{name} still contains retired reference at line {line}: {m.group(0)}")

purge = ROOT / ".github/scripts/purge-retired-systems.py"
text = purge.read_text(encoding="utf-8")
text = text.replace(
    'TARGET = re.compile(r"MadGod|Mad God|madgod|mad god|AN_NHIEN|AnNhien|an_nhien|an-nhien|An Nhiên|An Nhien|annhien", re.I)',
    'TARGET = re.compile(r"MadGod|Mad God|madgod|mad god|AN_NHIEN|AnNhien|an_nhien|an-nhien|(?<![\\w])An Nhiên(?![\\w])|(?<![\\w])An Nhien(?![\\w])|annhien", re.I)',
)

def switch_block(source: str, marker: str, end_marker: str) -> str:
    start = source.index(marker)
    end = source.index(end_marker, start)
    block = source[start:end]
    opener = "write(rel, dedent('''\\\n"
    closer = "'''))\n\n"
    if opener not in block or closer not in block:
        raise RuntimeError(f"delimiters not found for {marker}")
    block = block.replace(opener, 'write(rel, dedent("""\\\n', 1)
    pos = block.rfind(closer)
    if pos < 0:
        raise RuntimeError(f"closing delimiter not found for {marker}")
    block = block[:pos] + '"""))\n\n' + block[pos + len(closer):]
    return source[:start] + block + source[end:]

text = switch_block(text, '# State-op hardening now carries only the unrelated conservative deletion rule.', '# Audit patches: remove the retired high-risk root and prompt clause only.')
text = switch_block(text, '# Progression/Snapshot patch keeps its level/cache/combat/healthbar duties only.', '# Build packaging must not require removed assets.')

state_start = text.index('# State-op hardening now carries only the unrelated conservative deletion rule.')
state_end = text.index('# Audit patches: remove the retired high-risk root and prompt clause only.', state_start)
state_rewrite = '# State-op hardening became obsolete after the retired system was removed.\nrel = "android-apk/patch-state-op-hardening.py"\nwrite(rel, \'print("State-op hardening: no retired-system work remains.")\\n\')\n\n'
text = text[:state_start] + state_rewrite + text[state_end:]

start = text.index('# Orchestrator: remove dedicated item/root/roll/state handling.')
end = text.index('# State-op hardening became obsolete after the retired system was removed.', start)
block = text[start:end]
needle = 'write(rel, text)\n'
extra = "text = text.replace(' MadGod roll success chỉ mở discovery route, không tự đưa set vào inventory.', '')\n"
if extra not in block:
    block = block.replace(needle, extra + needle, 1)
text = text[:start] + block + text[end:]

marker = 'for path in APK.glob("*.py"):\n'
sanitizer = '''for _name in active_clean:\n    _path = APK / _name\n    if not _path.exists():\n        continue\n    _text = _path.read_text(encoding="utf-8")\n    _text = _text.replace(", madGod,", ",")\n    _text = _text.replace(" MadGod roll success chỉ mở discovery route, không tự đưa set vào inventory.", "")\n    _text = _text.replace('            "MadGod Set success chỉ mở đường/vị trí khám phá; acquired mặc định false cho tới khi Kai thực sự tiếp cận và lấy. " +\\\\n', '')\n    _path.write_text(_text, encoding="utf-8")\n\n'''
if sanitizer not in text:
    if marker not in text:
        raise RuntimeError('active patch loop marker missing')
    text = text.replace(marker, sanitizer + marker, 1)
purge.write_text(text, encoding="utf-8")

name = 'patch-character-stat-schema.py'
t = read(name)
t = re.sub(r'\n  private val anNhien = CharacterStatProfile\((?:.|\n)*?\n  \)\n\n', '\n', t, count=1)
t = t.replace('    "an-nhien", "an_nhien", "annhien" -> anNhien\n', '')
t = re.sub(r'\n    val anNhien = CharacterStatProfiles\.forId\("an-nhien"\)\n    assertEquals\(100, anNhien\.baseMaxHp\)\n    assertEquals\(EnergyMode\.NOT_APPLICABLE, anNhien\.energy\.mode\)\n    assertFalse\(anNhien\.regen\.enabled\)\n    assertEquals\(0, anNhien\.crit\)\n', '\n', t, count=1)
assert_clean(name, t)
write(name, t)

name = 'patch-character-status-equipment-system.py'
t = read(name)
t = re.sub(r'^AN_NHIEN = .*?\n', '', t, flags=re.M)
t = re.sub(r'^MADGOD = .*?\n', '', t, flags=re.M)
t, count = re.subn(r'\n    EquipmentDefinition\(\n      id = AN_NHIEN_OUTFIT_ID(?:.|\n)*?\n    \)\n  \)\n\n  private val definitions', '\n  )\n\n  private val definitions', t, count=1)
if count != 1: raise RuntimeError('target equipment definition block not found')
t = re.sub(r'^    AN_NHIEN_ID -> .*?\n', '', t, flags=re.M)
t = t.replace('"madgod_equipment_slot_mismatch"', '"special_equipment_slot_mismatch"')
t = re.sub(r'\n    val lockedByMadGod = targetSlots\.any \{ slot -> equipment\.slots\[slot\] == MADGOD_SET_ID && command\.itemId != MADGOD_SET_ID \}\n    if \(lockedByMadGod\) return invalid\(state, "madgod_equipment_permanent"\)\n    if \(command\.itemId == MADGOD_SET_ID && equipment\.slots\.values\.count \{ it == MADGOD_SET_ID \} >= 2\) return changed\(state, "item_equipped"\)\n', '\n', t, count=1)
t = re.sub(r'^    if \(command\.itemId == MADGOD_SET_ID\) return invalid\(state, "madgod_equipment_permanent"\)\n', '', t, flags=re.M)
t = re.sub(r'^    if \(def\.occupiesSlots\.any \{ equipment\.slots\[it\.key\] == MADGOD_SET_ID && itemId != MADGOD_SET_ID \}\) return null\n', '', t, flags=re.M)
t, count = re.subn(r'          val madGodOccupies = characterId == KAI_ID && slot in setOf\(EquipmentSlot\.WEAPON, EquipmentSlot\.ARMOR\) &&\n            slots\.values\.any \{ it == MADGOD_SET_ID \}\n          if \(!madGodOccupies && slot\.key !in slots\) slots\[slot\.key\] = itemId\n', '          if (slot.key !in slots) slots[slot.key] = itemId\n', t, count=1)
if count != 1: raise RuntimeError('starting-loadout retired lock block not found')
t, count = re.subn(r'\n# An Nhiên owns her equipped outfit/footwear in Inventory while retaining max two food item types\.(?:.|\n)*?AN_NHIEN\.write_text\(an, encoding="utf-8"\)\n', '\n', t, count=1)
if count != 1: raise RuntimeError('retired follower inventory patch block not found')
t = t.replace("""for candidate in (
    '    return SpecialFollowersCanon.ensure(AnNhienCanon.ensure(decoded))\\n',
    '    return AnNhienCanon.ensure(decoded)\\n',
):""", """for candidate in (
    '    return SpecialFollowersCanon.ensure(decoded)\\n',
):""")
t, count = re.subn(r'\n# --- MadGod is normalized into the same 100-HP gameplay scale ----------------(?:.|\n)*?MADGOD\.write_text\(mg, encoding="utf-8"\)\n', '\n', t, count=1)
if count != 1: raise RuntimeError('retired special equipment normalization block not found')
t = re.sub(r'^\s*assertEquals\(EnergyMode\.NOT_APPLICABLE, s\.characters\.getValue\(AN_NHIEN_ID\).*?\n', '', t, flags=re.M)
t, count = re.subn(r'\n  @Test fun madGodIsNotCanonicalStartingLoadoutAndCountsOnceAcrossTwoSlots\(\) \{(?:.|\n)*?\n  @Test fun projectionAfterReloadEqualsBasePlusEquippedItems', '\n  @Test fun projectionAfterReloadEqualsBasePlusEquippedItems', t, count=1)
if count != 1: raise RuntimeError('retired special equipment tests not found')
t = t.replace(', MadGod normalization', '')
assert_clean(name, t)
write(name, t)

name = 'patch-combat-hp-metadata-cleanup.py'
t = read(name)
t = re.sub(r'^MADGOD_TEST = .*?\n', '', t, flags=re.M)
t = t.replace("# Equipped clothing is owned by An Nhiên's Inventory but does not consume her two FOOD carry slots.", '# Equipped items stay inventory-owned while capacity accounting is handled by the shared policy.')
t, count = re.subn(r'\n# An Nhiên\'s outfit/footwear remain fixed even though all Equipment now uses the shared engine\.(?:.|\n)*?EQUIPMENT_SYSTEM\.write_text\(equipment_system, encoding="utf-8"\)\n', '\n', t, count=1)
if count != 1: raise RuntimeError('retired follower equipment lock block not found')
t = t.replace('SpecialFollowersCanon.ensure(AnNhienCanon.ensure(state))', 'SpecialFollowersCanon.ensure(state)')
t, count = re.subn(r'\n# Historical MadGod tests encoded the retired x50 implementation and removal-from-Inventory model\.(?:.|\n)*?MADGOD_TEST\.write_text\(r\'\'\'(?:.|\n)*?\'\'\', encoding="utf-8"\)\n', '\n', t, count=1)
if count != 1: raise RuntimeError('retired equipment regression rewrite block not found')
t = t.replace('Combat HP cleanup, UI compatibility, An Nhien equipment rules, and redesigned regression expectations applied.', 'Combat HP cleanup, UI compatibility, and redesigned regression expectations applied.')
assert_clean(name, t)
write(name, t)

name = 'patch-newgame-inventory-capacity.py'
t = read(name)
t = t.replace('SpecialFollowersCanon.ensure(AnNhienCanon.ensure(GameState.initial()))', 'SpecialFollowersCanon.ensure(GameState.initial())')
t = t.replace('equippedItemsConsumeZeroCapacityForAllFourCharacters', 'equippedItemsConsumeZeroCapacityForBaselineCharacters')
t = t.replace('listOf(KAI_ID, IRIS_ID, SYVIAL_ID, AN_NHIEN_ID)', 'listOf(KAI_ID, IRIS_ID, SYVIAL_ID)')
t, count = re.subn(r'\n  @Test fun madGodOccupiesTwoEquipmentSlotsButIsOneOwnedZeroCapacityItem\(\) \{(?:.|\n)*?\n  \}\n\n  @Test fun saveLoadRecalculatesCapacityFromOwnershipAndEquipmentReferences', '\n\n  @Test fun saveLoadRecalculatesCapacityFromOwnershipAndEquipmentReferences', t, count=1)
if count != 1: raise RuntimeError('retired capacity regression test not found')
t = t.replace("    'madGodOccupiesTwoEquipmentSlotsButIsOneOwnedZeroCapacityItem',\n", '')
assert_clean(name, t)
write(name, t)

name = 'patch-inventory-capacity-final-fix.py'
t = read(name)
t, count = re.subn(r'\ntest = TEST\.read_text\(encoding="utf-8"\)(?:.|\n)*?TEST\.write_text\(test, encoding="utf-8"\)\n', '\n', t, count=1)
if count != 1: raise RuntimeError('retired capacity test adjustment block not found')
t = t.replace('# intentionally occupies multiple equipment slots (for example MadGod weapon+armor).', '# intentionally occupies multiple equipment slots.')
t = t.replace("    'assertEquals(2, InventoryCapacityPolicy.usedSlots(equip.state, KAI_ID))',\n", '')
assert_clean(name, t)
write(name, t)

name = 'patch-lucia-normalize-compat.py'
t = r'''from pathlib import Path

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
'''
assert_clean(name, t)
write(name, t)

name = 'patch-lucia-follower.py'
t = read(name)
t = t.replace("""    anchor = '''  private val anNhien = CharacterStatProfile(
'''""", """    anchor = '''  private val fallback = CharacterStatProfile()
'''""")
t = t.replace("""    '''    "an-nhien", "an_nhien", "annhien" -> anNhien
    else -> fallback
''',
    '''    "an-nhien", "an_nhien", "annhien" -> anNhien
    "lucia", "luc", "lucia-luc" -> lucia
    else -> fallback
''',""", """    '''    else -> fallback
''',
    '''    "lucia", "luc", "lucia-luc" -> lucia
    else -> fallback
''',""")
t = t.replace("""    insert_anchor = '''    EquipmentDefinition(
      id = AN_NHIEN_OUTFIT_ID'''""", """    insert_anchor = '''  )

  private val definitions'''""")
t = t.replace("""    '''    AN_NHIEN_ID -> linkedMapOf(EquipmentSlot.OUTFIT to AN_NHIEN_OUTFIT_ID, EquipmentSlot.FOOTWEAR to AN_NHIEN_FOOTWEAR_ID)
    else -> emptyMap()
''',
    '''    AN_NHIEN_ID -> linkedMapOf(EquipmentSlot.OUTFIT to AN_NHIEN_OUTFIT_ID, EquipmentSlot.FOOTWEAR to AN_NHIEN_FOOTWEAR_ID)
    LUCIA_ID -> linkedMapOf(
      EquipmentSlot.WEAPON to LUCIA_M4A1_ID,
      EquipmentSlot.BLADE to LUCIA_KNIFE_ID,
      EquipmentSlot.WRIST to LUCIA_WATCH_ID
    )
    else -> emptyMap()
''',""", """    '''    else -> emptyMap()
''',
    '''    LUCIA_ID -> linkedMapOf(
      EquipmentSlot.WEAPON to LUCIA_M4A1_ID,
      EquipmentSlot.BLADE to LUCIA_KNIFE_ID,
      EquipmentSlot.WRIST to LUCIA_WATCH_ID
    )
    else -> emptyMap()
''',""")
t, count = re.subn(r"if 'val LUCIA = InventoryProfile\(maxTypes = 3, maxPerType = 100\)' not in policy:(?:.|\n)*?(?=\npolicy = replace_once\()", """if 'val LUCIA = InventoryProfile(maxTypes = 3, maxPerType = 100)' not in policy:
    profile_anchor = '  val NORMAL = InventoryProfile(maxTypes = 2, maxPerType = 2)\\n'
    policy = replace_once(policy, profile_anchor, '  val LUCIA = InventoryProfile(maxTypes = 3, maxPerType = 100)\\n' + profile_anchor, "Lucia inventory profile")
""", t, count=1)
if count != 1: raise RuntimeError('Lucia inventory profile block not found')
assert_clean(name, t)
write(name, t)

name = 'patch-lucia-combat-scout-finalize.py'
t = r'''from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
PATCH = ROOT / "patch-lucia-combat-scout.py"
source = PATCH.read_text(encoding="utf-8")
source, _ = re.subn(r"^\s*'Lucia \\\"Lục\\\" bắn hỗ trợ bằng M4A1',\n", "", source, count=1, flags=re.MULTILINE)
exec(compile(source, str(PATCH), "exec"), {"__name__": "__main__", "__file__": str(PATCH)})
print("Lucia combat/scout finalizer executed with the live Lucia-only loot bonus.")
'''
assert_clean(name, t)
write(name, t)

name = 'patch-companion-skills-ui-finalize.py'
t = read(name)
t, count = re.subn(r'\n# The existing An Nhiên follower patch already owns a \+2 percentage-point Exit bonus\.(?:.|\n)*?(?=\nsource = source\.replace\(\n    \'state\.metadata\[SYVIAL_DEVIL_TRIGGER_KEY\])', '\n', t, count=1)
if count != 1: raise RuntimeError('retired companion-finalizer composition block not found')
assert_clean(name, t)
write(name, t)

name = 'patch-companion-skills-ui.py'
t = read(name)
t, count = re.subn(r'\n  private val anNhien = listOf\((?:.|\n)*?\n  \)\n\n  private val kai', '\n\n  private val kai', t, count=1)
if count != 1: raise RuntimeError('retired companion skill catalog block not found')
t = re.sub(r'^    AN_NHIEN_ID -> anNhien\n', '', t, flags=re.M)
t = re.sub(r'^  private const val AN_NHIEN_ULTIMATE_INTERVAL_TURNS = 5\n', '', t, flags=re.M)
t = t.replace('# 3) Iris + Syvial + An Nhien runtime skills. These run after Kai, Diệp Minh,', '# 3) Iris + Syvial runtime skills. These run after Kai, Diệp Minh,')
t = t.replace('    // COMPANION_SKILLS_R01: Iris, Syvial and An Nhien wrap the finalized combat response.', '    // COMPANION_SKILLS_R01: Iris and Syvial wrap the finalized combat response.')
t = re.sub(r'^    val anNhienActive = activePartyCharacter\(resolvedState, AN_NHIEN_ID\) != null\n', '', t, flags=re.M)
t, count = re.subn(r'\n    if \(anNhienActive && c\.entityHp > 0\) \{(?:.|\n)*?\n    \}\n\n    if \(syvialDisorientTurns > 0\)', '\n\n    if (syvialDisorientTurns > 0)', t, count=1)
if count != 1: raise RuntimeError('retired companion combat utility block not found')
t = t.replace("    'Quăng Đại Cái Gì Đó',\n", '').replace("    'Kế Hoạch Không Có Trong Kế Hoạch',\n", '')
t, count = re.subn(r'\n# ---------------------------------------------------------------------------\n# 4\) An Nhien exploration utility\.(?:.|\n)*?(?=\n# ---------------------------------------------------------------------------\n# 5\) Character Detail UI:)', '\n', t, count=1)
if count != 1: raise RuntimeError('retired exploration utility section not found')
t = re.sub(r'^    assertEquals\(8, CompanionSkillCatalog\.forCharacter\(AN_NHIEN_ID\)\.size\)\n', '', t, flags=re.M)
t = re.sub(r'^    assertTrue\(CompanionSkillCatalog\.forCharacter\(AN_NHIEN_ID\).*?\n', '', t, flags=re.M)
t, count = re.subn(r'\n  @Test fun anNhienRemainsNonCombatAndWeaponLocked\(\) \{(?:.|\n)*?\n  \}\n\n  @Test fun irisAndSyvialAutomaticSkillsResolveWhenTheyAreActivePartyMembers', '\n\n  @Test fun irisAndSyvialAutomaticSkillsResolveWhenTheyAreActivePartyMembers', t, count=1)
if count != 1: raise RuntimeError('retired companion catalog test not found')
t, count = re.subn(r'\n  @Test fun anNhienCombatUtilityNeverDealsDamageDirectly\(\) \{(?:.|\n)*?\n  \}\n(?=\}\n\'\'\', encoding="utf-8"\))', '\n', t, count=1)
if count != 1: raise RuntimeError('retired companion combat test not found')
t = t.replace('Companion skills R01 applied: Iris + Syvial combat kits, An Nhien utility kit, and compact Character Skill panel.', 'Companion skills R01 applied: Iris + Syvial combat kits and compact Character Skill panel.')
assert_clean(name, t)
write(name, t)

print('Shared runtime patches sanitized.')

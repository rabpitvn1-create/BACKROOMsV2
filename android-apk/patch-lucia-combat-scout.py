from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
COMBAT = CORE / "CombatRuntime.kt"
LUCIA = CORE / "LuciaCanon.kt"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/CombatRuntimeTest.kt"
AVATAR = ROOT / "app/src/main/assets/avatars/lucia_avatar.jpg"


def replace_once(source: str, old: str, new: str, label: str) -> str:
    if new in source:
        return source
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return source.replace(old, new, 1)


# Keep generated Lucia canon metadata and Android loot projection compatible. CombatRuntime itself is
# checked-in Kotlin authority and is verified below without source rewriting.
lucia = LUCIA.read_text(encoding="utf-8")
if 'const val BATTLEFIELD_RECON_LOOT_BONUS_PERCENT = 5' not in lucia:
    lucia = replace_once(
        lucia,
        '  const val AVATAR_REF = "avatars/lucia_avatar.jpg"\n',
        '  const val AVATAR_REF = "avatars/lucia_avatar.jpg"\n  const val BATTLEFIELD_RECON_LOOT_BONUS_PERCENT = 5\n',
        "Lucia Battlefield Recon constant",
    )
lucia = replace_once(
    lucia,
    '        "goal" to "Tìm lối sang Level 1"\n',
    '        "goal" to "Tìm lối sang Level 1",\n'
    '        "passiveSkill" to "Trinh sát chiến trường",\n'
    '        "lootChanceBonusPercent" to BATTLEFIELD_RECON_LOOT_BONUS_PERCENT.toString(),\n'
    '        "avatarBuild" to "EXIF_STRIPPED_JPEG_R02"\n',
    "Lucia passive metadata",
)
LUCIA.write_text(lucia, encoding="utf-8")

combat = COMBAT.read_text(encoding="utf-8")
for marker in (
    'private const val LUCIA_M4A1_COMBAT_DAMAGE = 26',
    '// LUCIA_AUTO_ATTACK_V1:',
    'LUCIA_ID in resolvedState.party.memberIds',
    'lucia?.presence == CharacterPresence.ACTIVE',
    'if ((!splitAuto || autoActor == "lucia") && luciaActive && c.entityHp > 0)',
    'val luciaEvasionRoll = roll(c.copy(eventCounter = c.eventCounter + 97), 100)',
    'val luciaEntityEvaded = luciaEvasionRoll < ENTITY_EVASION_PERCENT',
    '// Lucia commits only her own attack and hands ownership to Entity->Lucia.',
):
    if marker not in combat:
        raise RuntimeError("Checked-in Kotlin Lucia combat authority missing: " + marker)
if 'LUCIA_JOINT_ATTACK' in combat:
    raise RuntimeError("Legacy Lucia joint-order combat authority survived in checked-in Kotlin")

main = MAIN.read_text(encoding="utf-8")
loot_anchor_start = main.find('    rolls.put("loot", thresholdRoll("loot", 10000,')
if loot_anchor_start < 0:
    raise RuntimeError("Lucia loot bonus: generic loot roll not found")
loot_anchor_end = main.find("\n", loot_anchor_start)
if loot_anchor_end < 0:
    raise RuntimeError("Lucia loot bonus: generic loot roll line is truncated")
loot_new = '''    int luciaScoutBonus = (partyHas(state, "lucia") || partyHas(state, "lục")) ? 500 : 0;
    int lootThreshold = Math.min(10000, lootThresholds[level] + (anNhienFollowing ? 1000 : 0) + luciaScoutBonus);
    String lootSuffix = (anNhienFollowing ? " +10% An Nhiên" : "") +
      (luciaScoutBonus > 0 ? " + Lucia Trinh sát chiến trường 5%" : "");
    rolls.put("loot", thresholdRoll("loot", 10000, lootThreshold, search, lootSuffix));
'''
main = main[:loot_anchor_start] + loot_new + main[loot_anchor_end + 1:]

if 'LUCIA SCOUT PASSIVE HARD LOCK:' not in main:
    prompt_pos = main.find('LUCIA FOLLOWER HARD LOCK:')
    if prompt_pos < 0:
        raise RuntimeError("Lucia prompt contract anchor missing")
    return_pos = main.rfind('    return actionDirective + ', 0, prompt_pos)
    if return_pos < 0:
        raise RuntimeError("Lucia writerPrompt return anchor missing")
    directive = (
        '    String luciaScoutDirective = "LUCIA SCOUT PASSIVE HARD LOCK: Khi Lucia \\"Lục\\" đang ở trong Party, '
        'passive Trinh sát chiến trường cộng đúng +5 điểm phần trăm vào generic loot roll hiện có. Không tạo roll vật phẩm '
        'riêng, không bỏ qua search eligibility, không tự nhặt vật phẩm và không vượt InventoryPolicy.";\n'
    )
    main = main[:return_pos] + directive + main[return_pos:]
    main = replace_once(
        main,
        '    return actionDirective + "\\n" + healingItemDirective + "\\n" + "',
        '    return actionDirective + "\\n" + healingItemDirective + "\\n" + luciaScoutDirective + "\\n" + "',
        "Lucia scout prompt injection",
    )

for marker in (
    'int luciaScoutBonus = (partyHas(state, "lucia") || partyHas(state, "lục")) ? 500 : 0;',
    'int lootThreshold = Math.min(10000, lootThresholds[level] + (anNhienFollowing ? 1000 : 0) + luciaScoutBonus);',
    'LUCIA SCOUT PASSIVE HARD LOCK:',
    '+5 điểm phần trăm',
):
    if marker not in main:
        raise RuntimeError("Lucia scout loot projection missing: " + marker)
if 'thresholdRoll("luciaLoot"' in main or 'thresholdRoll("battlefieldRecon"' in main:
    raise RuntimeError("Lucia passive must augment generic loot, not add a second roll")
MAIN.write_text(main, encoding="utf-8")


def strip_exif_app1(raw: bytes) -> bytes:
    if len(raw) < 4 or raw[:2] != b"\xff\xd8":
        raise RuntimeError("Lucia avatar is not a JPEG")
    out = bytearray(raw[:2])
    i = 2
    while i < len(raw):
        if raw[i] != 0xFF:
            out.extend(raw[i:])
            break
        start = i
        while i < len(raw) and raw[i] == 0xFF:
            i += 1
        if i >= len(raw):
            out.extend(raw[start:])
            break
        marker = raw[i]
        i += 1
        if marker == 0xDA:
            out.extend(raw[start:])
            break
        if marker in (0xD8, 0xD9, 0x01) or 0xD0 <= marker <= 0xD7:
            out.extend(raw[start:i])
            if marker == 0xD9:
                break
            continue
        if i + 2 > len(raw):
            raise RuntimeError("Lucia avatar has a truncated JPEG segment")
        seg_len = int.from_bytes(raw[i:i + 2], "big")
        if seg_len < 2 or i + seg_len > len(raw):
            raise RuntimeError("Lucia avatar has an invalid JPEG segment length")
        segment = raw[start:i + seg_len]
        payload = raw[i + 2:i + seg_len]
        if not (marker == 0xE1 and payload.startswith(b"Exif\x00\x00")):
            out.extend(segment)
        i += seg_len
    return bytes(out)


avatar_after = strip_exif_app1(AVATAR.read_bytes())
if len(avatar_after) <= 1024 or avatar_after[:2] != b"\xff\xd8" or avatar_after[-2:] != b"\xff\xd9":
    raise RuntimeError("Rebuilt Lucia avatar failed JPEG integrity checks")
if b"Exif\x00\x00" in avatar_after:
    raise RuntimeError("Rebuilt Lucia avatar still contains EXIF metadata")
AVATAR.write_bytes(avatar_after)

test = TEST.read_text(encoding="utf-8")
for marker in (
    'luciaGetsAnIndependentCombatResolutionWhenBothAttack',
    'luciaAutoAttacksOnAPlainAttackRound',
    'trueTurnAutoCombatCommitsKaiEntityLuciaEntityAsIndependentSubturns',
    'assertFalse(kai.reply.contains("Lucia \\"Lục\\""))',
    'assertEquals("lucia", CombatRuntime.toJson(entityKai.state)!!.getString("activeActorId"))',
    'assertEquals("entity:lucia", CombatRuntime.toJson(lucia.state)!!.getString("activeActorId"))',
    'trueTurnKaiSubturnDoesNotRunLegacyCompanionAssists',
):
    if marker not in test:
        raise RuntimeError("Checked-in Lucia combat regression coverage missing: " + marker)

print(
    "Lucia canon/scout projection synchronized; checked-in true-turn Kotlin combat authority verified "
    "without CombatRuntime or test source rewrite."
)

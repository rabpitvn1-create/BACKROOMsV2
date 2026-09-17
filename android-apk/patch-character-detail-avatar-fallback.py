from pathlib import Path
import runpy

ROOT = Path(__file__).resolve().parent
INDEX = ROOT / "app/src/main/assets/index.html"
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
FACADE = CORE / "GameCoreFacade.kt"

html = INDEX.read_text(encoding="utf-8")

old = """    detailAvatar.src=member.avatar||member.avatarRef||(member.id==='kai'?'avatars/kai_avatar.png':'avatars/kai_avatar.png');
    detailAvatar.alt=member.name||member.id||'Nhân vật';"""
new = """    const detailAvatarSrc=member.avatar||member.avatarRef||(member.id==='kai'?'avatars/kai_avatar.png':'');
    detailAvatar.hidden=!detailAvatarSrc;
    if(detailAvatarSrc)detailAvatar.src=detailAvatarSrc;else detailAvatar.removeAttribute('src');
    detailAvatar.alt=member.name||member.id||'Nhân vật';"""

if new not in html:
    if old not in html:
        raise RuntimeError("Character detail avatar fallback anchor not found")
    html = html.replace(old, new, 1)
if "member.id==='kai'?'avatars/kai_avatar.png':'avatars/kai_avatar.png'" in html:
    raise RuntimeError("Non-Kai character still falls back to Kai avatar")
INDEX.write_text(html, encoding="utf-8")


def replace_once(source: str, old_value: str, new_value: str, label: str) -> str:
    count = source.count(old_value)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return source.replace(old_value, new_value, 1)


# Preserve the pre-existing UX and acquisition hardening before restoring the
# independent Iris/Syvial follower layer that used to be reached indirectly.
for script in (
    "patch-survival-hud-chat-ux.py",
    "patch-search-action-false-warning.py",
):
    runpy.run_path(str(ROOT / script), run_name="__main__")

# ---------------------------------------------------------------------------
# Iris / Syvial core seed, save backfill, avatars, shortcuts and encounter rule.
# This is intentionally independent from every other follower implementation.
# ---------------------------------------------------------------------------
for filename in ("Iris_avatar.jpg", "Syvial_avatar.jpg"):
    path = ROOT / "app/src/main/assets/avatars" / filename
    if not path.is_file() or path.stat().st_size <= 0:
        raise RuntimeError(f"Missing special follower avatar: {path}")

canon_path = CORE / "SpecialFollowersCanon.kt"
canon = canon_path.read_text(encoding="utf-8")
constants_old = '  const val ENCOUNTER_CHANCE = "0.25%"\n  const val ENCOUNTER_LEVELS = "0-6"\n'
constants_new = constants_old + (
    '  const val IRIS_AVATAR_REF = "avatars/Iris_avatar.jpg"\n'
    '  const val SYVIAL_AVATAR_REF = "avatars/Syvial_avatar.jpg"\n'
    '  const val IRIS_PARTY_CHEAT_CODE = "/iris123"\n'
    '  const val SYVIAL_PARTY_CHEAT_CODE = "/Syv123"\n'
)
if 'IRIS_AVATAR_REF = "avatars/Iris_avatar.jpg"' not in canon:
    canon = replace_once(canon, constants_old, constants_new, "special follower constants")

iris_old = '''    return base.copy(
      id = IRIS_ID,
      name = "Iris",
      inventoryId = IRIS_ID,
'''
iris_new = '''    return base.copy(
      id = IRIS_ID,
      name = "Iris",
      avatarRef = IRIS_AVATAR_REF,
      inventoryId = IRIS_ID,
'''
if 'avatarRef = IRIS_AVATAR_REF' not in canon:
    canon = replace_once(canon, iris_old, iris_new, "Iris avatarRef")

syvial_old = '''    return base.copy(
      id = SYVIAL_ID,
      name = "Syvial",
      inventoryId = SYVIAL_ID,
'''
syvial_new = '''    return base.copy(
      id = SYVIAL_ID,
      name = "Syvial",
      avatarRef = SYVIAL_AVATAR_REF,
      inventoryId = SYVIAL_ID,
'''
if 'avatarRef = SYVIAL_AVATAR_REF' not in canon:
    canon = replace_once(canon, syvial_old, syvial_new, "Syvial avatarRef")

shortcut_helpers = '''  fun matchesPartyCheatCode(action: String): String? = when (action.trim()) {
    IRIS_PARTY_CHEAT_CODE -> IRIS_ID
    SYVIAL_PARTY_CHEAT_CODE -> SYVIAL_ID
    else -> null
  }

  fun forceIntoParty(state: GameState, targetId: String): Pair<GameState, String?> {
    if (targetId != IRIS_ID && targetId != SYVIAL_ID) return state to "unknown_follower"
    val ensured = ensure(state)
    if (targetId in ensured.party.memberIds) return ensured to null
    if (ensured.party.memberIds.size >= ensured.party.maxMembers) return ensured to "party_full"
    return ensured.copy(party = ensured.party.copy(memberIds = ensured.party.memberIds + targetId)) to null
  }

'''
if 'fun matchesPartyCheatCode(action: String)' not in canon:
    ensure_anchor = '  fun ensure(state: GameState): GameState {\n'
    if ensure_anchor not in canon:
        raise RuntimeError("SpecialFollowersCanon ensure anchor missing")
    canon = canon.replace(ensure_anchor, shortcut_helpers + ensure_anchor, 1)
canon_path.write_text(canon, encoding="utf-8")

# Fresh core state must know Iris/Syvial exist, while Party still starts with Kai only.
state_path = CORE / "GameState.kt"
state = state_path.read_text(encoding="utf-8")
seed_old = '''      characters = mapOf(
        KAI_ID to CharacterState(
          KAI_ID,
          "Kai Akechi",
          avatarRef = "avatars/kai_avatar.png",
          physiology = PhysiologyState.freshRunBaseline(),
          metadata = mapOf("inventoryProfile" to "kai")
        )
      ),
      inventories = mapOf(KAI_ID to InventoryState(KAI_ID)),
      equipment = mapOf(KAI_ID to EquipmentState(KAI_ID, KaiStartingEquipment.slots))
'''
seed_new = '''      characters = mapOf(
        KAI_ID to CharacterState(
          KAI_ID,
          "Kai Akechi",
          avatarRef = "avatars/kai_avatar.png",
          physiology = PhysiologyState.freshRunBaseline(),
          metadata = mapOf("inventoryProfile" to "kai")
        ),
        IRIS_ID to SpecialFollowersCanon.irisCharacter(),
        SYVIAL_ID to SpecialFollowersCanon.syvialCharacter()
      ),
      inventories = mapOf(
        KAI_ID to InventoryState(KAI_ID),
        IRIS_ID to InventoryState(IRIS_ID),
        SYVIAL_ID to InventoryState(SYVIAL_ID)
      ),
      equipment = mapOf(
        KAI_ID to EquipmentState(KAI_ID, KaiStartingEquipment.slots),
        IRIS_ID to EquipmentState(IRIS_ID, SpecialFollowersCanon.irisEquipmentSlots),
        SYVIAL_ID to EquipmentState(SYVIAL_ID, SpecialFollowersCanon.syvialEquipmentSlots)
      )
'''
if 'IRIS_ID to SpecialFollowersCanon.irisCharacter()' not in state:
    state = replace_once(state, seed_old, seed_new, "fresh Iris/Syvial seed")
state_path.write_text(state, encoding="utf-8")

# Existing saves get the same canonical records. The later equipment normalizer
# recognizes this exact return shape and wraps it without dropping the followers.
codec_path = CORE / "GameStateCodec.kt"
codec = codec_path.read_text(encoding="utf-8")
if 'return SpecialFollowersCanon.ensure(decoded)' not in codec:
    decode_old = '''  fun decode(root: JSONObject): GameState {
    val version = root.optInt("saveVersion", 0)
    return when {
      version >= CURRENT_SAVE_VERSION -> decodeCurrent(root)
      version == 2 && root.has("inventories") -> migrateV2Core(root)
      else -> LegacySaveMigration.migrate(root)
    }
  }
'''
    decode_new = '''  fun decode(root: JSONObject): GameState {
    val version = root.optInt("saveVersion", 0)
    val decoded = when {
      version >= CURRENT_SAVE_VERSION -> decodeCurrent(root)
      version == 2 && root.has("inventories") -> migrateV2Core(root)
      else -> LegacySaveMigration.migrate(root)
    }
    return SpecialFollowersCanon.ensure(decoded)
  }
'''
    codec = replace_once(codec, decode_old, decode_new, "special follower save backfill")
codec_path.write_text(codec, encoding="utf-8")

# Preserve the established 0.2500% independent physical-turn encounter policy.
main = MAIN.read_text(encoding="utf-8")
iris_roll_old = '    rolls.put("irisReunion", thresholdRoll("irisReunion", 1000000, 25, reunionEligibleAndroid(state, "iris"), ""));\n'
iris_roll_new = '    rolls.put("irisReunion", thresholdRoll("irisReunion", 10000, 25, physical && reunionEligibleAndroid(state, "iris"), " follower encounter"));\n'
if iris_roll_new not in main:
    main = replace_once(main, iris_roll_old, iris_roll_new, "Iris 0.25 percent encounter")
syvial_roll_old = '    rolls.put("syvialReunion", thresholdRoll("syvialReunion", 1000000, 25, reunionEligibleAndroid(state, "syvial"), ""));\n'
syvial_roll_new = '    rolls.put("syvialReunion", thresholdRoll("syvialReunion", 10000, 25, physical && reunionEligibleAndroid(state, "syvial"), " follower encounter"));\n'
if syvial_roll_new not in main:
    main = replace_once(main, syvial_roll_old, syvial_roll_new, "Syvial 0.25 percent encounter")

follower_helper = r'''  private boolean ensureSpecialFollowerInLegacyParty(JSONObject state, String id, String name) throws Exception {
    JSONArray party = state.optJSONArray("party");
    if (party == null) party = new JSONArray();
    String targetId = lower(id).trim();
    String targetName = lower(name).trim();
    for (int i = 0; i < party.length(); i++) {
      Object item = party.opt(i);
      if (!(item instanceof JSONObject)) continue;
      JSONObject member = (JSONObject)item;
      if (lower(member.optString("id", "")).trim().equals(targetId) ||
          lower(member.optString("name", "")).trim().equals(targetName)) {
        state.put("party", party);
        return true;
      }
    }
    // Legacy party excludes Kai, so three entries means the authoritative four-member party is full.
    if (party.length() >= 3) {
      state.put("party", party);
      return false;
    }
    party.put(new JSONObject()
      .put("id", id)
      .put("name", name)
      .put("present", true)
      .put("joinConfirmed", true)
      .put("presence", "ACTIVE")
      .put("role", "follower")
      .put("nonCombat", false));
    state.put("party", party);
    return true;
  }

'''
if 'private boolean ensureSpecialFollowerInLegacyParty(' not in main:
    helper_anchor = '  private boolean reunionEligibleAndroid(JSONObject state, String key) {\n'
    if helper_anchor not in main:
        raise RuntimeError("special follower encounter helper anchor missing")
    main = main.replace(helper_anchor, follower_helper + helper_anchor, 1)

if 'ensureSpecialFollowerInLegacyParty(state, "iris", "Iris")' not in main:
    tail_anchor = '''    state.put("flags", flags);
    return state;
  }
'''
    tail_replacement = '''    if (rollSuccess(rolls, "irisReunion")) {
      JSONObject iris = flags.optJSONObject("iris");
      if (iris == null) iris = new JSONObject();
      boolean irisJoined = ensureSpecialFollowerInLegacyParty(state, "iris", "Iris");
      iris.put("exists", true)
        .put("encountered", true)
        .put("present", true)
        .put("spawned", true)
        .put("follower", true)
        .put("reunionEligible", false)
        .put("continuity", "REUNITED")
        .put("levelEncountered", currentLevel(before))
        .put("joinPending", !irisJoined);
      flags.put("iris", iris);
    }

    if (rollSuccess(rolls, "syvialReunion")) {
      JSONObject syvial = flags.optJSONObject("syvial");
      if (syvial == null) syvial = new JSONObject();
      boolean syvialJoined = ensureSpecialFollowerInLegacyParty(state, "syvial", "Syvial");
      syvial.put("exists", true)
        .put("encountered", true)
        .put("present", true)
        .put("spawned", true)
        .put("follower", true)
        .put("reunionEligible", false)
        .put("continuity", "REUNITED")
        .put("levelEncountered", currentLevel(before))
        .put("joinPending", !syvialJoined);
      flags.put("syvial", syvial);
    }

    state.put("flags", flags);
    return state;
  }
'''
    main = replace_once(main, tail_anchor, tail_replacement, "Iris/Syvial deterministic encounter commit")

if 'IRIS / SYVIAL FOLLOWER LOCK:' not in main:
    prompt_anchor = '"Chỉ dùng flag root:'
    start = main.find(prompt_anchor)
    if start < 0:
        raise RuntimeError("special follower prompt anchor missing")
    end = main.find('\n', start)
    if end < 0:
        raise RuntimeError("special follower prompt line end missing")
    follower_prompt = (
        '            "IRIS / SYVIAL FOLLOWER LOCK: irisReunion và syvialReunion là hai roll độc lập 0.2500% trên mỗi lượt physical đủ điều kiện ở Level 0–6. '
        'Chỉ success=true mới cho nhân vật tương ứng xuất hiện hoặc reunion. Khi gặp và Party còn chỗ, Iris hoặc Syvial tự gia nhập; '
        'nếu Party đầy thì giữ present + joinPending, không tự đuổi thành viên khác. Iris giữ canon Scout / Target Eliminator, Gunslinger với Ivory & Ebony và Blackblood Recon Frame R03; '
        'Syvial giữ canon UR+, GodKiller và Lucifer Armor. Không hạ năng lực hoặc bịa thêm canon để cân bằng gameplay. " +\n'
    )
    main = main[:end + 1] + follower_prompt + main[end + 1:]

for required in (
    'thresholdRoll("irisReunion", 10000, 25, physical && reunionEligibleAndroid(state, "iris")',
    'thresholdRoll("syvialReunion", 10000, 25, physical && reunionEligibleAndroid(state, "syvial")',
    'ensureSpecialFollowerInLegacyParty(state, "iris", "Iris")',
    'ensureSpecialFollowerInLegacyParty(state, "syvial", "Syvial")',
    'IRIS / SYVIAL FOLLOWER LOCK:',
):
    if required not in main:
        raise RuntimeError("special follower runtime contract missing: " + required)
MAIN.write_text(main, encoding="utf-8")

# Restore the exact developer Party shortcuts without turn/time advancement.
facade = FACADE.read_text(encoding="utf-8")
shortcut_intercept = '''    SpecialFollowersCanon.matchesPartyCheatCode(action)?.let { targetId ->
      return applySpecialFollowerPartyCheat(legacy, state, targetId)
    }
'''
if shortcut_intercept not in facade:
    process_anchor = '''    val state = loadOrMigrate(legacy)
    val turnId = nextTurnId(legacy, state)
'''
    facade = replace_once(
        facade,
        process_anchor,
        '    val state = loadOrMigrate(legacy)\n' + shortcut_intercept + '    val turnId = nextTurnId(legacy, state)\n',
        "special follower shortcut intercept",
    )

shortcut_handler = '''  private fun applySpecialFollowerPartyCheat(legacy: JSONObject, state: GameState, targetId: String): String {
    val ensured = SpecialFollowersCanon.ensure(state)
    val displayName = ensured.characters[targetId]?.name ?: targetId
    val alreadyFollowing = targetId in ensured.party.memberIds
    val (updated, error) = SpecialFollowersCanon.forceIntoParty(ensured, targetId)
    val result = syncLegacy(legacy, updated, incrementTurn = false)
    val reply = when {
      error == "party_full" -> "Party đã đủ tối đa bốn thành viên; không thể thêm $displayName nếu chưa có chỗ trống."
      alreadyFollowing -> "$displayName đã ở trong Party."
      else -> "$displayName đã được thêm vào Party."
    }
    if (error == null) repository.save(updated)
    val log = result.optJSONArray("log") ?: JSONArray().also { result.put("log", it) }
    log.put(JSONObject().put("role", "gm").put("text", reply))
    logger.log(PipelineLogEvent(
      if (error == null) "CHEAT_COMMIT" else "CHEAT_REJECT",
      details = mapOf(
        "command" to if (targetId == IRIS_ID) "iris_party" else "syvial_party",
        "reason" to (error ?: "committed")
      )
    ))
    return response(
      handled = true,
      state = result,
      error = error,
      reason = if (error == null) "cheat_committed" else "cheat_rejected",
      reply = reply
    )
  }

'''
if 'private fun applySpecialFollowerPartyCheat(' not in facade:
    current_state_anchor = '  fun currentCoreState(): String = GameStateCodec.encode(repository.load())\n'
    if current_state_anchor not in facade:
        raise RuntimeError("GameCoreFacade currentCoreState anchor missing")
    facade = facade.replace(current_state_anchor, shortcut_handler + current_state_anchor, 1)
FACADE.write_text(facade, encoding="utf-8")

# Regression coverage for the restored independent follower path.
(TESTS / "SpecialFollowersTest.kt").write_text(r'''package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpecialFollowersTest {
  @Test fun irisAndSyvialAreSeededAsOptionalFollowersOutsideParty() {
    val state = GameState.initial()
    val iris = state.characters.getValue(IRIS_ID)
    val syvial = state.characters.getValue(SYVIAL_ID)
    assertEquals("follower", iris.metadata["npcType"])
    assertEquals("0.25%", iris.metadata["encounterChance"])
    assertEquals("Scout / Target Eliminator", iris.metadata["role"])
    assertEquals("follower", syvial.metadata["npcType"])
    assertEquals("0.25%", syvial.metadata["encounterChance"])
    assertEquals("UR+", syvial.metadata["combatTier"])
    assertFalse(IRIS_ID in state.party.memberIds)
    assertFalse(SYVIAL_ID in state.party.memberIds)
  }

  @Test fun decodeBackfillsBothFollowersWithoutForcingPartyMembership() {
    val base = GameState.initial()
    val stripped = base.copy(
      characters = base.characters - IRIS_ID - SYVIAL_ID,
      inventories = base.inventories - IRIS_ID - SYVIAL_ID,
      equipment = base.equipment - IRIS_ID - SYVIAL_ID
    )
    val decoded = GameStateCodec.decode(GameStateCodec.encode(stripped))
    assertTrue(IRIS_ID in decoded.characters)
    assertTrue(SYVIAL_ID in decoded.characters)
    assertFalse(IRIS_ID in decoded.party.memberIds)
    assertFalse(SYVIAL_ID in decoded.party.memberIds)
  }
}
''', encoding="utf-8")

(TESTS / "SpecialFollowerShortcutTest.kt").write_text(r'''package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpecialFollowerShortcutTest {
  @Test fun uploadedAvatarsAreLinkedToFollowerCharacters() {
    val state = GameState.initial()
    assertEquals("avatars/Iris_avatar.jpg", state.characters.getValue(IRIS_ID).avatarRef)
    assertEquals("avatars/Syvial_avatar.jpg", state.characters.getValue(SYVIAL_ID).avatarRef)
  }

  @Test fun exactSlashCodesResolveToRequestedFollowers() {
    assertEquals(IRIS_ID, SpecialFollowersCanon.matchesPartyCheatCode(" /iris123 "))
    assertEquals(SYVIAL_ID, SpecialFollowersCanon.matchesPartyCheatCode(" /Syv123 "))
    assertNull(SpecialFollowersCanon.matchesPartyCheatCode("/syv123"))
  }

  @Test fun shortcutsAreImmediateIdempotentAndRespectPartyCap() {
    val base = GameState.initial()
    val (withIris, irisError) = SpecialFollowersCanon.forceIntoParty(base, IRIS_ID)
    assertNull(irisError)
    assertTrue(IRIS_ID in withIris.party.memberIds)
    val (withBoth, syvialError) = SpecialFollowersCanon.forceIntoParty(withIris, SYVIAL_ID)
    assertNull(syvialError)
    assertTrue(SYVIAL_ID in withBoth.party.memberIds)
    val (again, againError) = SpecialFollowersCanon.forceIntoParty(withBoth, SYVIAL_ID)
    assertNull(againError)
    assertEquals(withBoth.party.memberIds, again.party.memberIds)

    val full = base.copy(
      characters = base.characters + mapOf("a" to CharacterState("a", "A"), "b" to CharacterState("b", "B"), "c" to CharacterState("c", "C")),
      party = PartyState(KAI_ID, listOf(KAI_ID, "a", "b", "c"), 4)
    )
    val (unchanged, fullError) = SpecialFollowersCanon.forceIntoParty(full, IRIS_ID)
    assertEquals("party_full", fullError)
    assertFalse(IRIS_ID in unchanged.party.memberIds)
  }
}
''', encoding="utf-8")

# The capacity patch is already clean and independent; it only needed a live caller.
runpy.run_path(str(ROOT / "patch-special-follower-inventory-cap.py"), run_name="__main__")
print("Iris/Syvial core, 0.25% encounter, avatars, shortcuts and 6x20 inventory policy restored.")

for script in (
    "patch-friendly-item-display.py",
    "patch-jeff-encounter-2pct.py",
    "patch-entity-encounter-plus-8pct.py",
    "patch-immersive-fullscreen.py",
    "patch-knowledge-engine-source.py",
    "patch-knowledge-context-builder.py",
    "benchmark-knowledge-context.py",
    "patch-startup-survival.py",
    "patch-local-entity-overlay.py",
    "patch-jane-killer.py",
    "patch-three-action-runtime-ui.py",
    "patch-entity-overlay-runtime-hotfix.py",
):
    runpy.run_path(str(ROOT / script), run_name="__main__")

final_html = INDEX.read_text(encoding="utf-8")
final_java = MAIN.read_text(encoding="utf-8")
final_facade = FACADE.read_text(encoding="utf-8")

for marker in (
    'id="searchActionButton"', 'id="submit"', 'id="exploreActionButton"',
    'submitMacroAction("SEARCH","Tìm kiếm")', 'submitMacroAction("EXPLORE","Khám phá")',
    'STEP2_THREE_ACTIONS',
):
    if marker not in final_html:
        raise RuntimeError(f"final UI contract missing: {marker}")
if '<button id="submit">THỰC HIỆN</button>' in final_html:
    raise RuntimeError("legacy single Execute button remains")

for marker in (
    '@JavascriptInterface public void submitAction(String stateJson, String actionKind, String action)',
    '.beginAction(stateJson, actionKind, action)', 'SEARCH HARD LOCK:', 'EXPLORE HARD LOCK:',
    'file:///android_asset/entity/', 'window.backroomEntityOverlay=function(payload)',
    'private void forceEntityEncounterFlag(JSONObject candidateState, JSONObject rolls)',
    'forceEntityEncounterFlag(candidateState, rolls);',
    'thresholdRoll("irisReunion", 10000, 25',
    'thresholdRoll("syvialReunion", 10000, 25',
):
    if marker not in final_java:
        raise RuntimeError(f"final Android runtime contract missing: {marker}")

for marker in (
    'fun beginAction(legacyStateJson: String, kindRaw: String, action: String)',
    'private fun commitActionRuntime(', 'ActionRuntime.markSearchCoverage(',
    'applySpecialFollowerPartyCheat(legacy, state, targetId)',
):
    if marker not in final_facade:
        raise RuntimeError(f"final core contract missing: {marker}")

print("Character detail/runtime patch chain verified with Iris/Syvial follower behavior preserved.")

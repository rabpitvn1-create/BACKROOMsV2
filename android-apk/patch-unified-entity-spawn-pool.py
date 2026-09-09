from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
FACADE = ROOT / "app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt"
INDEX = ROOT / "app/src/main/assets/index.html"
HEALTHBAR = ROOT / "patch-character-healthbar.py"


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return source.replace(old, new, 1)


def schedule_after_healthbar() -> bool:
    # patch-progression invokes this script before the health/status stack. Those later patches still
    # expect the pre-unification anchors, so schedule the real unification at the very end instead.
    # Diệp Minh's full combat patch already runs inside the healing finalizer. After this deferred
    # unification, only its encounter-priority helper needs to be restored.
    html = INDEX.read_text(encoding="utf-8")
    if 'id="characterHpFill"' in html:
        return False
    healthbar = HEALTHBAR.read_text(encoding="utf-8")
    marker = 'runpy.run_path(str(ROOT / "patch-unified-entity-spawn-pool.py"), run_name="__main__")'
    boss_marker = 'runpy.run_path(str(ROOT / "patch-diep-minh-boss-finalize.py"), run_name="__main__")'
    if marker not in healthbar:
        healthbar = healthbar.rstrip() + (
            '\n\n# Final Entity authority pass. Run after status/equipment/visual-state patches so their anchors remain intact.\n'
            + marker + '\n'
            + '# Restore only Diệp Minh encounter priority after the unified pool rewrites the shared helper.\n'
            + boss_marker + '\n'
        )
        HEALTHBAR.write_text(healthbar, encoding="utf-8")
    elif boss_marker not in healthbar:
        healthbar = healthbar.rstrip() + (
            '\n# Restore only Diệp Minh encounter priority after the unified pool rewrites the shared helper.\n'
            + boss_marker + '\n'
        )
        HEALTHBAR.write_text(healthbar, encoding="utf-8")
    print("Unified Entity spawn pool scheduled after the final health/status/visual patch stack; Diệp Minh encounter finalizer scheduled immediately after it.")
    return True


if not schedule_after_healthbar():
    text = MAIN.read_text(encoding="utf-8")

    # Jeff and Jane are normal choices in the one shared Entity roll. Retired
    # jeffEncounter/janeEncounter channels are no longer accepted as migration input:
    # if an earlier patch resurrects them, fail immediately and fix that producer.
    for legacy_marker in (
        'rolls.put("jeffEncounter"',
        'rolls.put("janeEncounter"',
        'rollSuccess(rolls, "jeffEncounter")',
        'rollSuccess(rolls, "janeEncounter")',
        'thresholdRoll("jeffEncounter"',
        'thresholdRoll("janeEncounter"',
    ):
        if legacy_marker in text:
            raise RuntimeError("Legacy independent killer encounter channel reintroduced before final Entity authority: " + legacy_marker)

    old_pool = '      String[] roamingPool = {"hound","clump","duller","deathmoth","hostile_faceling","false_puddle","paintings","smiler","skin-stealer","predatory_window","biological_pipeline","wretch","cable_mimic","the_beast_of_level_5","hotel_corpse_lure","slenderman"};\n'
    new_pool = '      String[] roamingPool = {"hound","clump","duller","deathmoth","hostile_faceling","false_puddle","paintings","smiler","skin-stealer","predatory_window","biological_pipeline","wretch","cable_mimic","the_beast_of_level_5","hotel_corpse_lure","jeff_the_killer","jane_the_killer","slenderman"};\n'
    if new_pool not in text:
        text = replace_once(text, old_pool, new_pool, "shared Entity roaming pool")

    # The final overlay bridge must never overwrite the selected normal Entity with a second unique roll.
    # Preserve Pressure Combat startup so the selected key immediately owns an authoritative combat session.
    helper_start = text.find('  private void forceEntityEncounterFlag(JSONObject candidateState, JSONObject rolls) throws Exception {')
    helper_end = text.find('\n  private JSONObject resolveEntityOverlay(String rawEntityKey) throws Exception {', helper_start)
    if helper_start < 0 or helper_end < 0:
        raise RuntimeError("forceEntityEncounterFlag boundary missing")
    unified_helper = r'''  private void forceEntityEncounterFlag(JSONObject candidateState, JSONObject rolls) throws Exception {
    if (candidateState == null || rolls == null) return;
    JSONObject normal = rolls.optJSONObject("entityEncounter");
    if (normal == null || !normal.optBoolean("success", false)) return;
    String entityKey = rolls.optString("roamingEntityKey", "").trim();
    if (entityKey.isEmpty()) return;
    JSONObject flags = candidateState.optJSONObject("flags");
    if (flags == null) {
      flags = new JSONObject();
      candidateState.put("flags", flags);
    }
    String canonicalKey = normalizedEntityKey(entityKey);
    flags.put("entityEncounterKey", canonicalKey);
    requireGameCore().startCombatState(candidateState.toString(), canonicalKey);
  }
'''
    text = text[:helper_start] + unified_helper + text[helper_end:]

    # Visual-state-sync V3 already made active CombatRuntime the sole source of Entity pixels. Keep
    # that stronger rule: stale entityEncounterKey / Jeff / Jane flags cannot resurrect an overlay.
    combat_visual = "function activeEntityKey(){var c=state&&state.combat;if(!c||c.active!==true)return '';return normalizeEntityKey(c.entityKey);}"
    if combat_visual not in text:
        raise RuntimeError("CombatRuntime visual authority missing after visual-state sync")

    # Rewrite the temporary local-overlay wording to the current single-pool authority.
    text = text.replace(
        'Jeff the Killer và Jane the Killer tạm giữ roll độc lập riêng ở bước hiện tại nhưng dùng key jeff_the_killer và jane_the_killer.',
        'Jeff the Killer và Jane the Killer nằm trong cùng LOCAL ROAMING POOL và chỉ xuất hiện khi roamingEntityKey chọn đúng canonical key jeff_the_killer hoặc jane_the_killer.'
    )
    text = text.replace(
        'SEARCH không được khởi tạo encounter Entity mới và entityEncounter/jeffEncounter/janeEncounter phải ineligible;',
        'SEARCH không được khởi tạo encounter Entity mới và entityEncounter phải ineligible;'
    )

    if "jeffEncounter" in text or "janeEncounter" in text:
        raise RuntimeError("Independent Jeff/Jane encounter channel remains in final MainActivity")

    for marker in (
        '"hotel_corpse_lure","jeff_the_killer","jane_the_killer","slenderman"',
        'rolls.put("roamingEntityKey"',
        'String entityKey = rolls.optString("roamingEntityKey", "").trim();',
        'requireGameCore().startCombatState(candidateState.toString(), canonicalKey);',
        combat_visual,
        'Jeff the Killer và Jane the Killer nằm trong cùng LOCAL ROAMING POOL',
    ):
        if marker not in text:
            raise RuntimeError("Unified Entity pool contract missing: " + marker)

    MAIN.write_text(text, encoding="utf-8")

    # Visual-state-sync V3 performs targeted persistent cleanup inside processCombat after all HP/regen
    # transformations. Reuse it rather than creating a second cleanup authority.
    facade = FACADE.read_text(encoding="utf-8")
    for marker in (
        'private fun normalizeVisualPresence(state: GameState): GameState',
        'val resolvedEntityKey = CombatRuntime.active(current)?.entityKey.orEmpty()',
        'flags.put("entityEncounterKey", "")',
        '"jeff_the_killer" -> flags.optJSONObject("jeff")?.put("present", false)',
        '"jane_the_killer" -> flags.optJSONObject("jane")?.put("present", false)',
        'next = next.copy(world = next.world + ("flagsJson" to flags.toString()))',
    ):
        if marker not in facade:
            raise RuntimeError("Visual-state persistent combat cleanup contract missing: " + marker)

    print("Unified Entity spawn pool installed: Jeff/Jane share entityEncounter + roamingEntityKey; no independent killer encounter channel remains.")

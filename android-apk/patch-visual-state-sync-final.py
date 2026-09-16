from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
FACADE = ROOT / "app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt"


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return source.replace(old, new, 1)


# VISUAL_STATE_SYNC_FINAL_V4
# Visual projection remains a build-time UI compatibility concern. Gameplay persistence and combat
# cleanup are checked-in Kotlin authority and MUST NOT be rewritten by this patch.
main = MAIN.read_text(encoding="utf-8")

old_scene_key = "function visualSceneKey(){var l=state&&state.level&&state.level.number;var where=String(state&&state.location||'').trim().toLowerCase();return String(l==null?'?':l)+'|'+where}"
new_scene_key = "function visualSceneKey(){var l=state&&state.level&&state.level.number;var where=String(state&&state.location||'').trim().toLowerCase();var area=String(state&&state.flags&&state.flags.visualAreaKey||'').trim().toLowerCase();return String(l==null?'?':l)+'|'+where+'|'+area}"
if new_scene_key not in main:
    main = replace_once(main, old_scene_key, new_scene_key, "area-aware Snapshot scene key")

old_level_picker = "var refs={0:'file:///android_asset/level_snapshots/level_0.webp',1:'file:///android_asset/level_snapshots/level_1.webp',2:'file:///android_asset/level_snapshots/level_2.webp',3:'file:///android_asset/level_snapshots/level_3.webp',4:'file:///android_asset/level_snapshots/level_4.webp',5:'file:///android_asset/level_snapshots/level_5.webp',6:'file:///android_asset/level_snapshots/level_6.webp'};var where=String(state&&state.location||'')+' '+String(state&&state.title||'');var lm=where.match(/Level[^0-9]*([0-6])/i);var lv=lm?Number(lm[1]):0;"
new_level_picker = "var refs={0:'file:///android_asset/level_snapshots/level_0.webp',1:'file:///android_asset/level_snapshots/level_1.webp',2:'file:///android_asset/level_snapshots/level_2.webp',3:'file:///android_asset/level_snapshots/level_3.webp',4:'file:///android_asset/level_snapshots/level_4.webp',5:'file:///android_asset/level_snapshots/level_5.webp',6:'file:///android_asset/level_snapshots/level_6.webp'};var structuredLevel=state&&state.level&&state.level.number;var where=String(state&&state.location||'')+' '+String(state&&state.title||'');var lm=where.match(/Level[^0-9]*([0-6])/i);var lv=(structuredLevel!==undefined&&structuredLevel!==null&&Number(structuredLevel)>=0&&Number(structuredLevel)<=6)?Number(structuredLevel):(lm?Number(lm[1]):0);"
if new_level_picker not in main:
    main = replace_once(main, old_level_picker, new_level_picker, "authoritative Snapshot Level picker")

old_active_entity = "function activeEntityKey(){var f=state&&state.flags||{};var direct=normalizeEntityKey(f.entityEncounterKey);if(direct)return direct;if(f.jeff&&f.jeff.present===true)return 'jeff_the_killer';if(f.jane&&f.jane.present===true)return 'jane_the_killer';return '';}"
new_active_entity = "function activeEntityKey(){var c=state&&state.combat;if(!c||c.active!==true)return '';return normalizeEntityKey(c.entityKey);}"
if new_active_entity not in main:
    main = replace_once(main, old_active_entity, new_active_entity, "authoritative current Entity visual source")

helper = r'''  private void reconcileVisualWorldState(JSONObject before, JSONObject candidateState, JSONObject rolls) throws Exception {
    int oldLevel = currentLevel(before);
    int structuredLevel = currentLevel(candidateState);
    int describedLevel = mentionedLevel(candidateState);
    int requestedLevel = structuredLevel != oldLevel ? structuredLevel : (describedLevel >= 0 ? describedLevel : oldLevel);
    boolean levelChange = requestedLevel != oldLevel;

    if (levelChange && !canTransition(before, rolls)) {
      JSONObject oldStructured = before.optJSONObject("level");
      candidateState.put("level", oldStructured != null
        ? new JSONObject(oldStructured.toString())
        : new JSONObject().put("number", oldLevel).put("name", levelName(oldLevel)));
      if (before.has("title")) candidateState.put("title", before.optString("title", ""));
      if (before.has("location")) candidateState.put("location", before.optString("location", ""));
      return;
    }

    if (levelChange) {
      candidateState.put("level", new JSONObject().put("number", requestedLevel).put("name", levelName(requestedLevel)));
      candidateState.put("title", "Level " + requestedLevel + " – " + levelName(requestedLevel));
      String candidateLocation = candidateState.optString("location", "").trim();
      int locationLevel = -1;
      if (!candidateLocation.isEmpty()) {
        JSONObject locationProbe = new JSONObject().put("location", candidateLocation);
        locationLevel = mentionedLevel(locationProbe);
      }
      if (candidateLocation.isEmpty() || (locationLevel >= 0 && locationLevel != requestedLevel)) {
        candidateState.put("location", "Level " + requestedLevel + " / " + levelName(requestedLevel));
      }
    } else {
      candidateState.put("level", new JSONObject().put("number", oldLevel).put("name", levelName(oldLevel)));
    }
  }

'''
if "private void reconcileVisualWorldState(JSONObject before, JSONObject candidateState, JSONObject rolls)" not in main:
    anchor = "  private int mentionedLevel(JSONObject state) {\n"
    if anchor not in main:
        raise RuntimeError("mentionedLevel anchor missing for visual world reconciliation")
    main = main.replace(anchor, helper + anchor, 1)

reconcile_call = "          reconcileVisualWorldState(before, candidateState, rolls);\n"
if reconcile_call not in main:
    anchor = "          forceEntityEncounterFlag(candidateState, rolls);\n"
    if anchor not in main:
        raise RuntimeError("validated candidate pre-commit anchor missing for visual world reconciliation")
    main = main.replace(anchor, reconcile_call + anchor, 1)

for marker in (
    "var area=String(state&&state.flags&&state.flags.visualAreaKey||'')",
    "var structuredLevel=state&&state.level&&state.level.number;",
    "function activeEntityKey(){var c=state&&state.combat;if(!c||c.active!==true)return '';",
    "private void reconcileVisualWorldState(JSONObject before, JSONObject candidateState, JSONObject rolls)",
    "reconcileVisualWorldState(before, candidateState, rolls);",
):
    if marker not in main:
        raise RuntimeError("Visual state synchronization contract missing: " + marker)
for retired in (
    "if(f.jeff&&f.jeff.present===true)return 'jeff_the_killer'",
    "if(f.jane&&f.jane.present===true)return 'jane_the_killer'",
    "return normalizeEntityKey(f.entityEncounterKey)",
):
    if retired in main:
        raise RuntimeError("Historical/stale Entity state still drives Snapshot overlay: " + retired)

MAIN.write_text(main, encoding="utf-8")

# Kotlin state migration and combat cleanup are already materialized and tested in GameCoreFacade.
# This patch only verifies those contracts; rewriting them here would restore split gameplay authority.
facade = FACADE.read_text(encoding="utf-8")
for marker in (
    "private fun normalizeVisualPresence(state: GameState): GameState",
    'if (CombatRuntime.active(state) != null) return state',
    'val normalized = EntityDrops.claimPending(normalizeVisualPresence(loaded))',
    'val resolvedEntityKey = CombatRuntime.active(current)?.entityKey.orEmpty()',
    'val resolution = CombatTurnAuthority.resolve(current, actionKind, action)',
    'when (resolvedEntityKey)',
    '"jeff_the_killer" -> flags.optJSONObject("jeff")?.put("present", false)',
    '"jane_the_killer" -> flags.optJSONObject("jane")?.put("present", false)',
    'next = next.copy(world = next.world + ("flagsJson" to flags.toString()))',
):
    if marker not in facade:
        raise RuntimeError("Checked-in Kotlin visual cleanup authority missing: " + marker)
for forbidden in (
    "CombatRuntime.resolve(current, actionKind, action)",
    'reason = "combat_action"',
):
    if forbidden in facade:
        raise RuntimeError("Checked-in Kotlin restored retired combat authority: " + forbidden)

print("Visual state sync V4 applied: UI projection patched; checked-in Kotlin visual/combat authority verified without source rewrite.")

from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
COMBAT = ROOT / "app/src/main/java/com/rabpit/backroom/core/CombatRuntime.kt"
FACADE = ROOT / "app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt"
TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/CombatRuntimeTest.kt"
INDEX = ROOT / "app/src/main/assets/index.html"


def replace_once(source: str, old: str, new: str, label: str) -> str:
    if new in source:
        return source
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return source.replace(old, new, 1)


# ---------------------------------------------------------------------------
# TRUE TURN COMBAT V2
#
# The previous autoplay layer animated Kai / Entity / Lucia / Entity separately
# but committed one whole combat round at Kai's visual step. This finalizer makes
# those visual turns authoritative subturns while preserving existing round-based
# mechanics exactly once per full cycle. Every valid ACTIVE member in live Party
# order owns one attack followed by one Entity response, up to PartyState.maxMembers.
#
# Combat eventCounter, Entity regeneration, Kai completed-turn regeneration,
# elapsed game time, Quick Step countdown and other round-scoped effects only
# advance once when the cycle completes. Save/load persists the next actor cursor.
# ---------------------------------------------------------------------------
combat = COMBAT.read_text(encoding="utf-8")

if "TRUE_TURN_COMBAT_V2" not in combat:
    combat = replace_once(
        combat,
        "import org.json.JSONObject\n",
        "import org.json.JSONArray\nimport org.json.JSONObject\n",
        "CombatRuntime JSONArray import",
    )

    resolution_old = '''  data class Resolution(
    val state: GameState,
    val handled: Boolean,
    val reply: String = "",
    val entityDestroyed: Boolean = false,
    val escaped: Boolean = false
  )
'''
    resolution_new = '''  data class Resolution(
    val state: GameState,
    val handled: Boolean,
    val reply: String = "",
    val entityDestroyed: Boolean = false,
    val escaped: Boolean = false,
    val roundCompleted: Boolean = true
  )
'''
    combat = replace_once(combat, resolution_old, resolution_new, "Combat Resolution round boundary")

    constants_anchor = '  private const val KAI_QUICK_STEP_TURNS_KEY = "combat.kaiQuickStepTurns"\n'
    constants_new = constants_anchor + '''  // TRUE_TURN_COMBAT_V2
  private const val AUTO_CURSOR_KEY = "combat.autoCursor"
  private const val AUTO_STUN_KEY = "combat.autoStunActions"
'''
    combat = replace_once(combat, constants_anchor, constants_new, "true-turn combat constants")

    resolve_anchor = '  fun resolve(state: GameState, actionKind: String, action: String): Resolution {\n'
    helpers = r'''  private fun autoCombatantIds(state: GameState): List<String> = state.party.memberIds
    .distinct()
    .mapNotNull { memberId -> activePartyCharacter(state, memberId)?.id }

  private fun autoTurnOrder(state: GameState): List<String> = autoCombatantIds(state).flatMap { memberId ->
    listOf(memberId, "entity:$memberId")
  }

  private fun currentAutoCursor(state: GameState, order: List<String>): Int {
    if (order.isEmpty()) return 0
    val raw = state.metadata[AUTO_CURSOR_KEY]?.toIntOrNull() ?: 0
    return ((raw % order.size) + order.size) % order.size
  }

  private fun withAutoCursor(state: GameState, cursor: Int): GameState {
    val metadata = state.metadata.toMutableMap()
    metadata[AUTO_CURSOR_KEY] = cursor.coerceAtLeast(0).toString()
    return state.copy(metadata = metadata)
  }

  private fun nextAutoCursor(state: GameState, previousOrder: List<String>, previousCursor: Int): Int {
    val nextOrder = autoTurnOrder(state)
    if (nextOrder.isEmpty() || previousOrder.isEmpty()) return 0
    for (offset in 1..previousOrder.size) {
      val candidate = previousOrder[(previousCursor + offset) % previousOrder.size]
      val nextIndex = nextOrder.indexOf(candidate)
      if (nextIndex >= 0) return nextIndex
    }
    return 0
  }

  private fun autoActorMemberId(actorId: String): String = actorId.removePrefix("entity:")

  private fun autoActorSlot(state: GameState, actorId: String): Int =
    state.party.memberIds.indexOf(autoActorMemberId(actorId)).coerceAtLeast(0)

  private fun autoActorName(state: GameState, c: Snapshot, actorId: String): String {
    val memberId = autoActorMemberId(actorId)
    val memberName = state.characters[memberId]?.name ?: memberId
    return if (actorId.startsWith("entity:")) "${c.entityName} → $memberName" else memberName
  }

  private fun autoActorOverlay(actorId: String): String = when (actorId) {
    "kai", "entity:kai" -> "file:///android_asset/kai_entity_overlay.png"
    "lucia", "entity:lucia" -> "file:///android_asset/lucia_entity_overlay.png"
    "syvial", "entity:syvial" -> "file:///android_asset/syvial_entity_overlay.png"
    else -> ""
  }

'''
    combat = replace_once(combat, resolve_anchor, helpers + resolve_anchor, "true-turn combat helpers")

    start_old = '''    val intent = classify(actionKind, action)
    var c = current.copy(eventCounter = current.eventCounter + 1)
'''
    start_new = '''    val splitAuto = actionKind.equals("AUTO_COMBAT_STEP", true)
    val autoOrder = if (splitAuto) autoTurnOrder(state) else emptyList()
    val autoCursor = if (splitAuto) currentAutoCursor(state, autoOrder) else 0
    val autoActor = if (splitAuto && autoOrder.isNotEmpty()) autoOrder[autoCursor] else ""
    val intent = if (splitAuto) Intent.ATTACK else classify(actionKind, action)
    var c = current.copy(eventCounter = current.eventCounter + if (!splitAuto || autoActor == "kai") 1 else 0)
'''
    combat = replace_once(combat, start_old, start_new, "authoritative auto subturn setup")

    combat = replace_once(
        combat,
        '    var entityStunnedThisTurn = false\n',
        '    var entityStunnedThisTurn = splitAuto && (state.metadata[AUTO_STUN_KEY]?.toIntOrNull() ?: 0) > 0\n',
        "persisted one-action stun",
    )

    # Split the final ATTACK block without rewriting any of Kai's existing attack,
    # Guilty Crown, proc, damage-normalization, bleed or equipment logic.
    resolve_start = combat.index(resolve_anchor)
    resolve_end = combat.index('\n  fun toJson(state: GameState): JSONObject?', resolve_start)
    attack_start = combat.index('      Intent.ATTACK -> {\n', resolve_start, resolve_end)
    attack_body = attack_start + len('      Intent.ATTACK -> {\n')
    lucia_comment = combat.index('        // LUCIA_AUTO_ATTACK_V1:', attack_body, resolve_end)
    if 'if (!splitAuto || autoActor == "kai") {' not in combat[attack_start:lucia_comment]:
        combat = combat[:attack_body] + '        if (!splitAuto || autoActor == "kai") {\n' + combat[attack_body:lucia_comment] + '        }\n' + combat[lucia_comment:]

    combat = replace_once(
        combat,
        '        if (luciaActive && c.entityHp > 0) {\n',
        '        if ((!splitAuto || autoActor == "lucia") && luciaActive && c.entityHp > 0) {\n',
        "Lucia independent auto actor gate",
    )
    lucia_roll_old = '''          val luciaRoll = roll(c.copy(eventCounter = c.eventCounter + 83), 100)
          if (luciaRoll < hitChance) {
'''
    lucia_roll_new = '''          val luciaRangeBonus = when (c.range) { RangeBand.CLOSE -> 18; RangeBand.NEAR -> 10; RangeBand.FAR -> -5 }
          val luciaHitChance = (58 + luciaRangeBonus + c.opening * 11 + c.momentum * 6).coerceIn(20, 96)
          val luciaRoll = roll(c.copy(eventCounter = c.eventCounter + 83), 100)
          if (luciaRoll < luciaHitChance) {
'''
    combat = replace_once(combat, lucia_roll_old, lucia_roll_new, "Lucia independent hit chance scope")

    # Lucia already has a dedicated attack block. Every other companion keeps
    # its legacy mechanics in its own authoritative subturn, with a generic
    # weapon attack only when those mechanics did not deal damage.
    bleed_marker = '    if (c.entityHp > 0 && bleedTurns > 0) {\n'
    lucia_boundary = r'''    // Lucia commits only her own attack and hands ownership to Entity->Lucia.
    if (splitAuto && autoActor == LUCIA_ID) {
      if (c.entityHp <= 0) {
        val persisted = encode(resolvedState, c.copy(phase = Phase.RESOLVED, entityCondition = EntityCondition.DESTROYED))
        val cleared = clearCombatOnly(persisted)
        return Resolution(
          cleared,
          true,
          log.joinToString(" ") + " ${c.entityName} đã bị tiêu diệt.",
          entityDestroyed = true,
          roundCompleted = true
        )
      }
      val stagedState = encode(resolvedState, c)
      val staged = withAutoCursor(stagedState, nextAutoCursor(stagedState, autoOrder, autoCursor))
      return Resolution(staged, true, log.joinToString(" "), roundCompleted = false)
    }

    // Companion mechanics are resolved only by their own authoritative actor.
    val irisActive = activePartyCharacter(resolvedState, IRIS_ID) != null
    val syvialCharacter = activePartyCharacter(resolvedState, SYVIAL_ID)
    val syvialActive = syvialCharacter != null
    val anNhienActive = activePartyCharacter(resolvedState, AN_NHIEN_ID) != null
    val autoMemberEntityHpBefore = if (splitAuto && !autoActor.startsWith("entity:") && autoActor != KAI_ID && autoActor != LUCIA_ID) c.entityHp else -1

    if (!splitAuto || autoActor == KAI_ID) {
'''
    combat = replace_once(combat, bleed_marker, lucia_boundary + bleed_marker, "Lucia subturn boundary")

    companion_scope_old = '''    val irisActive = activePartyCharacter(resolvedState, IRIS_ID) != null
    val syvialCharacter = activePartyCharacter(resolvedState, SYVIAL_ID)
    val syvialActive = syvialCharacter != null
    val anNhienActive = activePartyCharacter(resolvedState, AN_NHIEN_ID) != null
'''
    companion_scope_new = ''
    companion_scope_count = combat.count(companion_scope_old)
    if companion_scope_count != 1:
        raise RuntimeError(
            "true-turn companion presence scope: expected exactly 1 anchor, "
            f"found {companion_scope_count}"
        )
    combat = combat.replace(companion_scope_old, companion_scope_new, 1)

    companion_marker = '    // COMPANION_SKILLS_R01: Iris, Syvial and An Nhien wrap the finalized combat response.\n'
    combat = replace_once(combat, companion_marker, '    }\n\n' + companion_marker, "close Kai-only mechanics before companion actors")
    combat = replace_once(
        combat,
        companion_marker + '    if (irisActive && c.entityHp > 0) {\n',
        companion_marker + '    if ((!splitAuto || autoActor == IRIS_ID) && irisActive && c.entityHp > 0) {\n',
        "Iris authoritative actor gate",
    )
    syvial_main_anchor = '''    }

    if (syvialActive && c.entityHp > 0) {
      val syvialMaxHp = CharacterStatEngine.effective(resolvedState, SYVIAL_ID).maxHp
'''
    syvial_main_new = '''    }

    if ((!splitAuto || autoActor == SYVIAL_ID) && syvialActive && c.entityHp > 0) {
      val syvialMaxHp = CharacterStatEngine.effective(resolvedState, SYVIAL_ID).maxHp
'''
    combat = replace_once(combat, syvial_main_anchor, syvial_main_new, "Syvial authoritative actor gate")
    combat = replace_once(combat, '    if (anNhienActive && c.entityHp > 0) {\n', '    if ((!splitAuto || autoActor == AN_NHIEN_ID) && anNhienActive && c.entityHp > 0) {\n', "An Nhien authoritative actor gate")

    generic_attack_anchor = '    if (syvialDisorientTurns > 0) companionEnemyAccuracyPenalty += 25\n\n'
    generic_attack = generic_attack_anchor + r'''    if (splitAuto && autoMemberEntityHpBefore >= 0 && c.entityHp == autoMemberEntityHpBefore && c.entityHp > 0) {
      val member = activePartyCharacter(resolvedState, autoActor)
      if (member != null) {
        val rangeBonus = when (c.range) { RangeBand.CLOSE -> 18; RangeBand.NEAR -> 10; RangeBand.FAR -> -5 }
        val hitChance = (58 + rangeBonus + c.opening * 11 + c.momentum * 6).coerceIn(20, 96)
        val actorSeedOffset = (autoActor.hashCode() and 0x7fffffff) % 997
        val attackRoll = roll(c.copy(eventCounter = c.eventCounter + 307 + actorSeedOffset), 100)
        val evasionRoll = roll(c.copy(eventCounter = c.eventCounter + 311 + actorSeedOffset), 100)
        if (attackRoll < hitChance && evasionRoll >= ENTITY_EVASION_PERCENT) {
          val weaponDamage = CharacterStatEngine.weaponDamage(resolvedState, autoActor)
          val damage = max(1, weaponDamage - profile.armor)
          val hp = max(0, c.entityHp - damage)
          c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp), noise = min(100, c.noise + 24))
          log += "${member.name} tấn công: -$damage HP (${c.entityHp}/${c.entityMaxHp})."
        } else {
          log += "${member.name} tấn công nhưng ${c.entityName} tránh được đòn."
        }
      }
    }

'''
    combat = replace_once(combat, generic_attack_anchor, generic_attack, "generic Party member attack fallback")

    response_marker = '    // Enemy response. Diệp Minh uses percentage damage; all other Entity behavior remains unchanged.\n'
    kai_boundary = r'''    if (splitAuto && !autoActor.startsWith("entity:")) {
      if (entityStunnedThisTurn) resolvedState = withCombatCounter(resolvedState, AUTO_STUN_KEY, 1)
      val stagedState = encode(resolvedState, c)
      val staged = withAutoCursor(stagedState, nextAutoCursor(stagedState, autoOrder, autoCursor))
      return Resolution(staged, true, log.joinToString(" "), roundCompleted = false)
    }

    if (splitAuto && autoActor.startsWith("entity:") && entityStunnedThisTurn) {
      // Stun is exactly one Entity action. Persist it across the Kai -> Entity boundary,
      // then consume it before evaluating the Entity response.
      resolvedState = withCombatCounter(resolvedState, AUTO_STUN_KEY, 0)
    }

'''
    combat = replace_once(combat, response_marker, kai_boundary + response_marker, "Kai -> Entity subturn boundary")

    # Target the live member encoded by every Entity->member actor. The existing
    # response stays intact for manual combat and Entity->Kai, including Diệp Minh,
    # Quick Step and Silent Lullaby behavior.
    resolve_start = combat.index(resolve_anchor)
    resolve_end = combat.index('\n  fun toJson(state: GameState): JSONObject?', resolve_start)
    response_start = combat.index(response_marker, resolve_start, resolve_end)
    quick_start = combat.index('    if (quickStepTurns > 0) {\n', response_start, resolve_end)
    existing_response = combat[response_start + len(response_marker):quick_start]
    lucia_response = r'''    if (splitAuto && autoActor.startsWith("entity:") && autoActor != "entity:$KAI_ID") {
      val targetId = autoActorMemberId(autoActor)
      val target = resolvedState.characters[targetId]
      val targetName = target?.name ?: targetId
      if (entityStunnedThisTurn) {
        log += "Silent Lullaby: ${c.entityName} bị Stun và mất lượt phản ứng hiện tại."
      } else if (target == null || target.presence != CharacterPresence.ACTIVE || target.vitalState.currentHp <= 0) {
        log += "${c.entityName} không còn mục tiêu $targetName hợp lệ cho lượt phản ứng này."
      } else {
        val effective = CharacterStatEngine.effective(resolvedState, targetId)
        val targetMaxHp = effective.maxHp
        val targetHp = target.vitalState.currentHp.coerceIn(0, targetMaxHp)
        val incomingRoll = roll(c.copy(eventCounter = c.eventCounter + 157), 100)
        val defense = when (c.cover) { Cover.HARD -> 22; Cover.PARTIAL -> 10; Cover.EXPOSED -> 0 } +
          CombatStatMath.agilityDefense(effective.agi) * 4 + max(0, c.momentum) * 2
        val enemyChance = (profile.aggression * 8 - defense + max(0, -c.momentum) * 7).coerceIn(8, 88)
        if (incomingRoll < enemyChance) {
          val mitigation = CombatStatMath.defenseReduction(effective.df) + CombatStatMath.agilityDefense(effective.agi)
          val damage = if (c.entityKey == DIEP_MINH_KEY) {
            percentDamage(targetMaxHp, DIEP_MINH_ATTACK_PERCENT)
          } else {
            max(1, profile.attack + roll(c.copy(eventCounter = c.eventCounter + 173), 7) -
              when (c.cover) { Cover.HARD -> 8; Cover.PARTIAL -> 4; Cover.EXPOSED -> 0 } - mitigation)
          }
          resolvedState = CharacterStatEngine.setCurrentHp(resolvedState, targetId, targetHp - damage)
          val after = resolvedState.characters[targetId]?.vitalState?.currentHp ?: max(0, targetHp - damage)
          c = c.copy(momentum = max(-3, c.momentum - 1))
          log += if (c.entityKey == DIEP_MINH_KEY) {
            "Diệp Minh phản công $targetName: -$damage HP (${DIEP_MINH_ATTACK_PERCENT}% Max HP; $after/$targetMaxHp)."
          } else {
            "${c.entityName} phản công $targetName: -$damage HP ($after/$targetMaxHp)."
          }
        } else {
          log += "$targetName tránh được lượt phản công của ${c.entityName}."
        }
      }
    } else {
'''
    wrapped_response = response_marker + lucia_response + existing_response + '    }\n\n'
    combat = combat[:response_start] + wrapped_response + combat[quick_start:]

    # Every Entity->member response except the last pair is an intermediate
    # subturn. Party slot order, not a hard-coded companion name, selects next.
    quick_marker = '    if (quickStepTurns > 0) {\n'
    entity_kai_boundary = r'''    if (splitAuto && autoActor.startsWith("entity:") && autoCursor < autoOrder.lastIndex) {
      val stagedState = encode(resolvedState, c)
      val staged = withAutoCursor(stagedState, nextAutoCursor(stagedState, autoOrder, autoCursor))
      return Resolution(staged, true, log.joinToString(" "), roundCompleted = false)
    }

'''
    combat = replace_once(combat, quick_marker, entity_kai_boundary + quick_marker, "Entity response -> next Party member boundary")

    final_old = '''    val next = encode(resolvedState, c)
    return Resolution(next, true, log.joinToString(" "))
'''
    final_new = '''    if (splitAuto) resolvedState = withAutoCursor(resolvedState, 0)
    val next = encode(resolvedState, c)
    return Resolution(next, true, log.joinToString(" "), roundCompleted = true)
'''
    combat = replace_once(combat, final_old, final_new, "true-turn round completion")

    # Project the authoritative next actor into the legacy WebView state. This is
    # the only actor cursor used by autoplay and survives save/load via metadata.
    projection_anchor = '    put("round", c.eventCounter)\n'
    projection_new = projection_anchor + r'''    val order = autoTurnOrder(state)
    val cursor = currentAutoCursor(state, order)
    val actorId = order.getOrElse(cursor) { "kai" }
    put("autoCursor", cursor)
    put("autoOrder", JSONArray(order))
    put("activeActorId", actorId)
    put("activeActorName", autoActorName(state, c, actorId))
    put("activeActorSlot", autoActorSlot(state, actorId))
    put("activeActorTargetId", if (actorId.startsWith("entity:")) autoActorMemberId(actorId) else c.entityKey)
    put("activeActorOverlayUri", autoActorOverlay(actorId))
    put("autoMode", "TRUE_TURN_V2")
'''
    combat = replace_once(combat, projection_anchor, projection_new, "true-turn actor projection")

    for marker in (
        "TRUE_TURN_COMBAT_V2",
        'val roundCompleted: Boolean = true',
        'AUTO_CURSOR_KEY = "combat.autoCursor"',
        'autoCombatantIds(state).flatMap',
        'actionKind.equals("AUTO_COMBAT_STEP", true)',
        'autoActor == "lucia"',
        'autoActor.startsWith("entity:")',
        'autoActor == IRIS_ID',
        'autoMemberEntityHpBefore',
        'nextAutoCursor(stagedState, autoOrder, autoCursor)',
        'put("activeActorSlot", autoActorSlot(state, actorId))',
        'autoCursor < autoOrder.lastIndex',
        'file:///android_asset/syvial_entity_overlay.png',
        'CharacterStatEngine.setCurrentHp(resolvedState, targetId',
        'roundCompleted = false',
        'put("autoMode", "TRUE_TURN_V2")',
        'put("autoOrder", JSONArray(order))',
    ):
        if marker not in combat:
            raise RuntimeError("True-turn CombatRuntime marker missing: " + marker)

    COMBAT.write_text(combat, encoding="utf-8")


# ---------------------------------------------------------------------------
# GameCoreFacade: one visual/core subturn is not one game Turn. Only the final
# Entity subturn advances legacy Turn, game time and completed-turn regeneration.
# ---------------------------------------------------------------------------
facade = FACADE.read_text(encoding="utf-8")
if "TRUE_TURN_COMBAT_FACADE_V2" not in facade:
    process_start = facade.index('  fun processCombat(legacyStateJson: String, actionKind: String, action: String): String {\n')
    process_end = facade.index('\n  private fun normalizeVisualPresence(', process_start)
    process = facade[process_start:process_end]

    timing_start = process.index('    var next = resolution.state\n')
    output_start = process.index('    val output = syncLegacy(', timing_start)
    timing_old = process[timing_start:output_start]
    timing_new = '''    var next = resolution.state
    // TRUE_TURN_COMBAT_FACADE_V2: intermediate actor subturns persist combat state only.
    if (resolution.roundCompleted) {
      val time = TimeEngine.execute(next, TimeAdvanceCommand(
        commandId = "COMBAT:${next.turn.currentTurnId}:${System.nanoTime()}",
        turnId = null,
        actorId = KAI_ID,
        source = CommandSource.SYSTEM,
        minutes = 1,
        reason = "combat_round"
      ))
      if (time.applied) next = time.state
      next = CharacterStatEngine.applyCompletedTurnRegen(next, "COMBAT_TURN_${legacy.optInt("turn", 1)}")
    }
    repository.save(next)

'''
    process = process[:timing_start] + timing_new + process[output_start:]
    process = process.replace(
        '    val output = syncLegacy(legacy, next, incrementTurn = true)\n',
        '    val output = syncLegacy(legacy, next, incrementTurn = resolution.roundCompleted)\n',
        1,
    )
    facade = facade[:process_start] + process + facade[process_end:]

    facade = facade.replace(
        '    if (actionKind.equals("AUTO_COMBAT", true)) {\n',
        '    if (actionKind.equals("AUTO_COMBAT", true) || actionKind.equals("AUTO_COMBAT_STEP", true)) {\n',
        1,
    )

    for marker in (
        "TRUE_TURN_COMBAT_FACADE_V2",
        "if (resolution.roundCompleted)",
        "incrementTurn = resolution.roundCompleted",
        'actionKind.equals("AUTO_COMBAT_STEP", true)',
        'reason = "combat_round"',
    ):
        if marker not in facade:
            raise RuntimeError("True-turn facade marker missing: " + marker)
    FACADE.write_text(facade, encoding="utf-8")


# ---------------------------------------------------------------------------
# WebView autoplay V2: the UI no longer cycles actors independently of GameCore.
# It renders exactly the actor projected by CombatRuntime, waits two seconds, then
# asks the core to resolve ONE AUTO_COMBAT_STEP. Each returned state points at the
# next authoritative actor.
# ---------------------------------------------------------------------------
html = INDEX.read_text(encoding="utf-8")
old_script_marker = '<script>\n/* AUTO_PARTY_COMBAT_V1 / COMBAT_POPUP_V1 */'
if old_script_marker not in html:
    raise RuntimeError("Old automatic Party combat script marker missing")
script_start = html.index(old_script_marker)
script_end = html.index('</script>', script_start) + len('</script>')

new_script = r'''<script>
/* TRUE_TURN_AUTOPLAY_V3 / COMBAT_POPUP_V1 */
(function(){
  if(window.__trueTurnAutoplayV2)return;window.__trueTurnAutoplayV2=true;
  var STEP_MS=2000,timer=0,inFlight=false,currentActor=null,popupOpen=false;

  function combat(){return (typeof state!=='undefined'&&state&&state.combat&&state.combat.active===true)?state.combat:null;}
  function e(s){return String(s==null?'':s).replace(/[&<>"']/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]});}
  function canonicalId(raw){
    var id=String(raw||'').trim().toLocaleLowerCase('vi-VN');
    if(id==='kai'||id.indexOf('kai akechi')>=0)return 'kai';
    if(id.indexOf('lucia')>=0||id==='lục'||id==='luc')return 'lucia';
    if(id.indexOf('syvial')>=0)return 'syvial';
    if(id.indexOf('iris')>=0)return 'iris';
    return id;
  }
  function partyMembers(c){
    var raw=(state&&state.partyDetails&&Array.isArray(state.partyDetails.members))?state.partyDetails.members:[];
    var out=[],seen={};
    raw.forEach(function(m){var id=canonicalId(m&&m.id||m&&m.name);if(!id||seen[id])return;seen[id]=true;out.push({id:id,name:String(m.name||id),type:'party',currentHp:Number(m.currentHp),maxHp:Number(m.maxHp)});});
    if(!seen.kai)out.unshift({id:'kai',name:'Kai Akechi',type:'party',currentHp:Number(c&&c.playerHp),maxHp:Number(c&&c.playerMaxHp)});
    out.sort(function(a,b){if(a.id==='kai')return -1;if(b.id==='kai')return 1;if(a.id==='lucia')return -1;if(b.id==='lucia')return 1;return 0;});
    return out.slice(0,4);
  }
  function orderIds(c){return Array.isArray(c&&c.autoOrder)&&c.autoOrder.length?c.autoOrder.map(String):['kai','entity:kai'];}
  function actorForId(c,id){
    id=String(id||'kai');
    if(id.indexOf('entity:')===0){var target=id.slice(7),targetMember=partyMembers(c).find(function(x){return x.id===target;}),targetName=targetMember?targetMember.name:target;return {id:id,name:String(c.entityName||c.entityKey||'Entity')+' → '+targetName,type:'entity',overlayUri:''};}
    var member=partyMembers(c).find(function(x){return x.id===id;});
    var uri=id==='kai'?'file:///android_asset/kai_entity_overlay.png':id==='lucia'?'file:///android_asset/lucia_entity_overlay.png':id==='syvial'?'file:///android_asset/syvial_entity_overlay.png':'';
    return member||{id:id,name:id,type:'party',overlayUri:uri};
  }
  function currentFromCore(c){
    var id=String(c.activeActorId||orderIds(c)[Number(c.autoCursor)||0]||'kai');
    var actor=actorForId(c,id);actor.overlayUri=String(c.activeActorOverlayUri||actor.overlayUri||'');return actor;
  }
  function displayRound(c){var r=Math.max(0,Number(c&&c.round)||0);return Math.max(1,r+(String(c&&c.activeActorId||'')==='kai'?1:0));}
  function syncTurnHeader(c){
    if(!turnEl||!turnEl.parentElement)return;var host=turnEl.parentElement,node=host.firstChild;
    var label=c?'COMBAT • ROUND ':'TURN ';if(node&&node.nodeType===3)node.nodeValue=label;
    turnEl.textContent=c?String(displayRound(c)):String(state&&state.turn||1);
  }
  function setActor(c){
    var actor=currentFromCore(c);currentActor=actor;
    if(typeof window.backroomCombatActorSwap==='function')window.backroomCombatActorSwap({actorId:actor.id,slot:Number(c.activeActorSlot)||0,name:String(c.activeActorName||actor.name),overlayUri:actor.overlayUri,hasOverlay:!!actor.overlayUri});
    return actor;
  }
  function ensurePopup(){
    var root=document.getElementById('combatPopup');if(root)return root;
    root=document.createElement('div');root.id='combatPopup';root.hidden=true;
    root.innerHTML='<div class="combat-popup-sheet" role="dialog" aria-modal="true" aria-labelledby="combatPopupTitle"><div class="combat-popup-head"><h2 id="combatPopupTitle">COMBAT</h2><span class="combat-popup-auto">TRUE TURN AUTO</span><button type="button" class="combat-popup-close" aria-label="Đóng">×</button></div><div id="combatPopupBody"></div></div>';
    document.body.appendChild(root);return root;
  }
  function renderPopup(){
    var c=combat(),root=ensurePopup(),body=document.getElementById('combatPopupBody');if(!c||!body){if(root)root.hidden=true;return;}
    root.hidden=!popupOpen;
    var ids=orderIds(c),current=String(c.activeActorId||ids[Number(c.autoCursor)||0]||'kai'),actor=currentFromCore(c),members=partyMembers(c);
    var chips=ids.map(function(id){var a=actorForId(c,id);return '<span class="combat-turn-chip '+(id===current?'current':'')+'">'+e(a.name)+'</span>';}).join('');
    var slots=members.map(function(m){return '<div class="combat-popup-slot '+(m.id===current?'current':'')+'"><strong>'+e(m.name)+'</strong><span>'+((Number.isFinite(m.currentHp)&&Number.isFinite(m.maxHp))?(Math.max(0,m.currentHp)+'/'+Math.max(1,m.maxHp)):'PARTY')+'</span></div>';}).join('');
    body.innerHTML='<div class="combat-popup-target"><span>ROUND '+displayRound(c)+'</span><strong>'+e(c.entityName||c.entityKey||'Entity')+'</strong></div><div class="combat-popup-current">CURRENT TURN: '+e(String(c.activeActorName||actor.name))+'</div><div class="combat-popup-order">'+chips+'</div><div class="combat-popup-party">'+slots+'</div>';
  }
  function openPopup(){popupOpen=true;renderPopup();}
  function closePopup(){popupOpen=false;var root=document.getElementById('combatPopup');if(root)root.hidden=true;}
  function clearTimer(){if(timer){window.clearTimeout(timer);timer=0;}}
  function queueSubmit(ms){clearTimer();timer=window.setTimeout(submitAutoStep,ms);}
  function submitAutoStep(){
    timer=0;var c=combat();if(!c){stop();return false;}if(inFlight||busy){queueSubmit(250);return false;}if(!window.Android||typeof window.Android.submitAction!=='function'){if(statusEl)statusEl.textContent='Không tìm thấy Android bridge cho combat.';stop();return false;}
    var actor=currentFromCore(c);if(!currentActor||currentActor.id!==actor.id){step();return false;}
    inFlight=true;if(statusEl)statusEl.textContent='COMBAT AUTO • round '+displayRound(c)+' • '+String(c.activeActorName||actor.name);
    window.Android.submitAction(JSON.stringify(state),'AUTO_COMBAT_STEP','Tự động xử lý lượt '+String(c.activeActorName||actor.name));return true;
  }
  function stop(){clearTimer();currentActor=null;inFlight=false;closePopup();syncTurnHeader(null);if(typeof syncPrimaryActions==='function')syncPrimaryActions();}
  function step(){
    timer=0;var c=combat();if(!c){stop();return;}syncTurnHeader(c);var actor=setActor(c);renderPopup();if(typeof syncPrimaryActions==='function')syncPrimaryActions();
    if(statusEl)statusEl.textContent='COMBAT AUTO • round '+displayRound(c)+' • lượt '+String(c.activeActorName||actor.name);
    queueSubmit(inFlight||busy?250:STEP_MS);
  }

  document.addEventListener('click',function(ev){var open=ev.target&&ev.target.closest&&ev.target.closest('#combatPopupButton');if(open){openPopup();return;}var close=ev.target&&ev.target.closest&&ev.target.closest('.combat-popup-close');if(close){closePopup();return;}var root=document.getElementById('combatPopup');if(root&&ev.target===root)closePopup();});
  var oldTurn=window.backroomTurn;if(typeof oldTurn==='function')window.backroomTurn=function(json){var r=oldTurn.call(this,json);inFlight=false;if(combat())step();else stop();return r;};
  window.trueTurnCombatStep=step;window.trueTurnCombatStop=stop;window.openCombatPopup=openPopup;window.closeCombatPopup=closePopup;
  window.setTimeout(step,120);
})();
</script>'''
html = html[:script_start] + new_script + html[script_end:]


for marker in (
    "TRUE_TURN_AUTOPLAY_V3",
    "AUTO_COMBAT_STEP",
    "TRUE TURN AUTO",
    "c.activeActorId",
    "queueSubmit(inFlight||busy?250:STEP_MS)",
    "function syncTurnHeader(c)",
):
    if marker not in html:
        raise RuntimeError("True-turn WebView marker missing: " + marker)
if "queue(80)" in html or "Exactly one authoritative AUTO_COMBAT resolve" in html:
    raise RuntimeError("Legacy visual-only autoplay description survived")
INDEX.write_text(html, encoding="utf-8")


# ---------------------------------------------------------------------------
# Regression coverage: core actor ownership, cursor persistence and round scope.
# Diệp Minh's large HP pool keeps the test deterministic even when Kai procs a
# first-round gun skill.
# ---------------------------------------------------------------------------
test = TEST.read_text(encoding="utf-8")
new_tests = r'''
  @Test fun trueTurnAutoCombatCommitsKaiEntityLuciaEntityAsIndependentSubturns() {
    val initial = LuciaCanon.ensure(GameState.initial())
    var state = initial.copy(party = PartyState(memberIds = listOf(KAI_ID, LUCIA_ID)))
    state = CombatRuntime.start(state, "diep_minh")
    assertEquals("kai", CombatRuntime.toJson(state)!!.getString("activeActorId"))
    assertEquals("file:///android_asset/kai_entity_overlay.png", CombatRuntime.toJson(state)!!.getString("activeActorOverlayUri"))

    val kai = CombatRuntime.resolve(state, "AUTO_COMBAT_STEP", "auto")
    assertTrue(kai.handled)
    assertFalse(kai.roundCompleted)
    assertFalse(kai.reply.contains("Lucia \"Lục\""))
    assertFalse(kai.reply.contains("phản công: Kai"))
    assertEquals(1, CombatRuntime.active(kai.state)!!.eventCounter)
    assertEquals("entity:kai", CombatRuntime.toJson(kai.state)!!.getString("activeActorId"))
    assertEquals("file:///android_asset/kai_entity_overlay.png", CombatRuntime.toJson(kai.state)!!.getString("activeActorOverlayUri"))

    val entityKai = CombatRuntime.resolve(kai.state, "AUTO_COMBAT_STEP", "auto")
    assertTrue(entityKai.handled)
    assertFalse(entityKai.roundCompleted)
    assertEquals(1, CombatRuntime.active(entityKai.state)!!.eventCounter)
    assertEquals("lucia", CombatRuntime.toJson(entityKai.state)!!.getString("activeActorId"))
    assertEquals("file:///android_asset/lucia_entity_overlay.png", CombatRuntime.toJson(entityKai.state)!!.getString("activeActorOverlayUri"))

    val lucia = CombatRuntime.resolve(entityKai.state, "AUTO_COMBAT_STEP", "auto")
    assertTrue(lucia.handled)
    assertFalse(lucia.roundCompleted)
    assertTrue(lucia.reply.contains("Lucia \"Lục\""))
    assertEquals(1, CombatRuntime.active(lucia.state)!!.eventCounter)
    assertEquals("entity:lucia", CombatRuntime.toJson(lucia.state)!!.getString("activeActorId"))
    assertEquals("file:///android_asset/lucia_entity_overlay.png", CombatRuntime.toJson(lucia.state)!!.getString("activeActorOverlayUri"))

    val entityLucia = CombatRuntime.resolve(lucia.state, "AUTO_COMBAT_STEP", "auto")
    assertTrue(entityLucia.handled)
    assertTrue(entityLucia.roundCompleted)
    assertEquals(1, CombatRuntime.active(entityLucia.state)!!.eventCounter)
    assertEquals("kai", CombatRuntime.toJson(entityLucia.state)!!.getString("activeActorId"))
  }

  @Test fun trueTurnAutoCombatExposesSyvialOverlayAndIndependentSubturns() {
    val initial = SpecialFollowersCanon.ensure(GameState.initial())
    var state = initial.copy(party = PartyState(memberIds = listOf(KAI_ID, SYVIAL_ID)))
    state = CombatRuntime.start(state, "diep_minh")

    val kai = CombatRuntime.resolve(state, "AUTO_COMBAT_STEP", "auto")
    val entityKai = CombatRuntime.resolve(kai.state, "AUTO_COMBAT_STEP", "auto")
    val syvialJson = CombatRuntime.toJson(entityKai.state)!!
    assertEquals("syvial", syvialJson.getString("activeActorId"))
    assertEquals("file:///android_asset/syvial_entity_overlay.png", syvialJson.getString("activeActorOverlayUri"))

    val syvial = CombatRuntime.resolve(entityKai.state, "AUTO_COMBAT_STEP", "auto")
    assertFalse(syvial.roundCompleted)
    assertTrue(syvial.reply.contains("Syvial"))
    assertEquals("entity:syvial", CombatRuntime.toJson(syvial.state)!!.getString("activeActorId"))

    val entitySyvial = CombatRuntime.resolve(syvial.state, "AUTO_COMBAT_STEP", "auto")
    assertTrue(entitySyvial.roundCompleted)
    assertEquals("kai", CombatRuntime.toJson(entitySyvial.state)!!.getString("activeActorId"))
  }

  @Test fun trueTurnAutoCombatWithoutLuciaCompletesAfterEntityTargetsKai() {
    var state = CombatRuntime.start(GameState.initial(), "diep_minh")
    val kai = CombatRuntime.resolve(state, "AUTO_COMBAT_STEP", "auto")
    assertFalse(kai.roundCompleted)
    assertEquals("entity:kai", CombatRuntime.toJson(kai.state)!!.getString("activeActorId"))

    val entityKai = CombatRuntime.resolve(kai.state, "AUTO_COMBAT_STEP", "auto")
    assertTrue(entityKai.roundCompleted)
    assertEquals(1, CombatRuntime.active(entityKai.state)!!.eventCounter)
    assertEquals("kai", CombatRuntime.toJson(entityKai.state)!!.getString("activeActorId"))
  }

  @Test fun trueTurnAutoCombatUsesLivePartySlotOrderForEveryAttackResponsePair() {
    val withLucia = LuciaCanon.ensure(GameState.initial())
    val initial = SpecialFollowersCanon.ensure(withLucia)
    var state = initial.copy(party = PartyState(memberIds = listOf(KAI_ID, SYVIAL_ID, LUCIA_ID)))
    state = CombatRuntime.start(state, "diep_minh")
    val order = CombatRuntime.toJson(state)!!.getJSONArray("autoOrder")
    assertEquals(
      listOf("kai", "entity:kai", "syvial", "entity:syvial", "lucia", "entity:lucia"),
      (0 until order.length()).map { order.getString(it) }
    )

    val kai = CombatRuntime.resolve(state, "AUTO_COMBAT_STEP", "auto")
    val entityKai = CombatRuntime.resolve(kai.state, "AUTO_COMBAT_STEP", "auto")
    assertEquals("syvial", CombatRuntime.toJson(entityKai.state)!!.getString("activeActorId"))
    val syvial = CombatRuntime.resolve(entityKai.state, "AUTO_COMBAT_STEP", "auto")
    assertEquals("entity:syvial", CombatRuntime.toJson(syvial.state)!!.getString("activeActorId"))
    val entitySyvial = CombatRuntime.resolve(syvial.state, "AUTO_COMBAT_STEP", "auto")
    assertFalse(entitySyvial.roundCompleted)
    assertEquals("lucia", CombatRuntime.toJson(entitySyvial.state)!!.getString("activeActorId"))
  }

  @Test fun trueTurnAutoCursorSurvivesCodecSaveLoadBetweenActors() {
    val initial = LuciaCanon.ensure(GameState.initial())
    var state = initial.copy(party = PartyState(memberIds = listOf(KAI_ID, LUCIA_ID)))
    state = CombatRuntime.start(state, "diep_minh")
    val kai = CombatRuntime.resolve(state, "AUTO_COMBAT_STEP", "auto")
    val loaded = GameStateCodec.decode(GameStateCodec.encode(kai.state))
    assertEquals("entity:kai", CombatRuntime.toJson(loaded)!!.getString("activeActorId"))
    assertEquals(1, CombatRuntime.active(loaded)!!.eventCounter)
  }

  @Test fun trueTurnAutoCombatBuildsFourMemberOrderAndUsesRealPartySlots() {
    val initial = SpecialFollowersCanon.ensure(LuciaCanon.ensure(GameState.initial()))
    var state = initial.copy(party = PartyState(memberIds = listOf(KAI_ID, LUCIA_ID, SYVIAL_ID, IRIS_ID)))
    state = CombatRuntime.start(state, "diep_minh")
    val expected = listOf("kai", "entity:kai", "lucia", "entity:lucia", "syvial", "entity:syvial", "iris", "entity:iris")
    val order = CombatRuntime.toJson(state)!!.getJSONArray("autoOrder")
    assertEquals(expected, (0 until order.length()).map { order.getString(it) })

    expected.forEachIndexed { cursor, actorId ->
      val projected = state.copy(metadata = state.metadata + ("combat.autoCursor" to cursor.toString()))
      val json = CombatRuntime.toJson(projected)!!
      assertEquals(actorId, json.getString("activeActorId"))
      assertEquals(cursor / 2, json.getInt("activeActorSlot"))
      assertEquals(if (actorId.startsWith("entity:")) actorId.removePrefix("entity:") else "diep_minh", json.getString("activeActorTargetId"))
    }
    assertEquals("", CombatRuntime.toJson(state.copy(metadata = state.metadata + ("combat.autoCursor" to "6")))!!.getString("activeActorOverlayUri"))
  }

  @Test fun trueTurnAutoCombatPreservesReorderedLivePartyOrder() {
    val initial = SpecialFollowersCanon.ensure(LuciaCanon.ensure(GameState.initial()))
    val reordered = initial.copy(party = PartyState(memberIds = listOf(KAI_ID, SYVIAL_ID, LUCIA_ID, IRIS_ID)))
    val state = CombatRuntime.start(reordered, "diep_minh")
    val order = CombatRuntime.toJson(state)!!.getJSONArray("autoOrder")
    assertEquals(
      listOf("kai", "entity:kai", "syvial", "entity:syvial", "lucia", "entity:lucia", "iris", "entity:iris"),
      (0 until order.length()).map { order.getString(it) }
    )
  }

  @Test fun trueTurnAutoCombatSkipsDeadSeparatedAndMissingPartyMembers() {
    val ensured = SpecialFollowersCanon.ensure(LuciaCanon.ensure(GameState.initial()))
    val deadIris = ensured.characters.getValue(IRIS_ID).copy(
      vitalState = ensured.characters.getValue(IRIS_ID).vitalState.copy(currentHp = 0)
    )
    val separatedLucia = ensured.characters.getValue(LUCIA_ID).copy(presence = CharacterPresence.SEPARATED)
    val filtered = ensured.copy(
      party = PartyState(memberIds = listOf(KAI_ID, LUCIA_ID, SYVIAL_ID, IRIS_ID)),
      characters = ensured.characters + (IRIS_ID to deadIris) + (LUCIA_ID to separatedLucia)
    )
    val state = CombatRuntime.start(filtered, "diep_minh")
    val order = CombatRuntime.toJson(state)!!.getJSONArray("autoOrder")
    assertEquals(listOf("kai", "entity:kai", "syvial", "entity:syvial"), (0 until order.length()).map { order.getString(it) })

    val missingState = CombatRuntime.start(
      ensured.copy(party = PartyState(memberIds = listOf(KAI_ID, SYVIAL_ID, "missing-member"))),
      "diep_minh"
    )
    val missingOrder = CombatRuntime.toJson(missingState)!!.getJSONArray("autoOrder")
    assertEquals(listOf("kai", "entity:kai", "syvial", "entity:syvial"), (0 until missingOrder.length()).map { missingOrder.getString(it) })
  }

  @Test fun trueTurnEntityResponseDamagesOnlyItsEncodedPartyMember() {
    val initial = SpecialFollowersCanon.ensure(LuciaCanon.ensure(GameState.initial())).copy(
      party = PartyState(memberIds = listOf(KAI_ID, LUCIA_ID, SYVIAL_ID, IRIS_ID))
    )
    val base = CombatRuntime.start(initial, "slenderman")
    val targets = listOf(KAI_ID, LUCIA_ID, SYVIAL_ID, IRIS_ID)
    fun hp(state: GameState, id: String): Int = if (id == KAI_ID) {
      CombatRuntime.active(state)!!.playerHp
    } else {
      state.characters.getValue(id).vitalState.currentHp
    }

    targets.forEachIndexed { slot, targetId ->
      var observedDamage = false
      for (counter in 1..128) {
        val candidate = base.copy(metadata = base.metadata + mapOf(
          "combat.autoCursor" to (slot * 2 + 1).toString(),
          "combat.eventCounter" to counter.toString()
        ))
        val before = targets.associateWith { hp(candidate, it) }
        val response = CombatRuntime.resolve(candidate, "AUTO_COMBAT_STEP", "auto")
        val after = targets.associateWith { hp(response.state, it) }
        targets.filter { it != targetId }.forEach { otherId -> assertEquals(before[otherId], after[otherId]) }
        if (after.getValue(targetId) < before.getValue(targetId)) {
          observedDamage = true
          break
        }
      }
      assertTrue(observedDamage, "Expected an Entity response to damage $targetId")
    }
  }

  @Test fun trueTurnAutoCursorSurvivesSaveLoadBeforeFourthMemberResponse() {
    val initial = SpecialFollowersCanon.ensure(LuciaCanon.ensure(GameState.initial())).copy(
      party = PartyState(memberIds = listOf(KAI_ID, LUCIA_ID, SYVIAL_ID, IRIS_ID))
    )
    val started = CombatRuntime.start(initial, "diep_minh")
    val irisTurn = started.copy(metadata = started.metadata + ("combat.autoCursor" to "6"))
    val iris = CombatRuntime.resolve(irisTurn, "AUTO_COMBAT_STEP", "auto")
    assertEquals("entity:iris", CombatRuntime.toJson(iris.state)!!.getString("activeActorId"))
    val loaded = GameStateCodec.decode(GameStateCodec.encode(iris.state))
    assertEquals("entity:iris", CombatRuntime.toJson(loaded)!!.getString("activeActorId"))
    assertEquals(3, CombatRuntime.toJson(loaded)!!.getInt("activeActorSlot"))
  }

  @Test fun trueTurnKaiSubturnDoesNotRunLegacyCompanionAssists() {
    val initial = SpecialFollowersCanon.ensure(LuciaCanon.ensure(GameState.initial())).copy(
      party = PartyState(memberIds = listOf(KAI_ID, LUCIA_ID, SYVIAL_ID, IRIS_ID))
    )
    val state = CombatRuntime.start(initial, "diep_minh")
    val kai = CombatRuntime.resolve(state, "AUTO_COMBAT_STEP", "auto")
    for (legacyAssist in listOf("Lucia \"Lục\"", "ARGUS", "Syvial", "Rift Sever", "Crimson Guillotine")) {
      assertFalse(kai.reply.contains(legacyAssist), "Kai subturn must not execute $legacyAssist")
    }

    val irisTurn = state.copy(metadata = state.metadata + ("combat.autoCursor" to "6"))
    val iris = CombatRuntime.resolve(irisTurn, "AUTO_COMBAT_STEP", "auto")
    assertTrue(iris.reply.contains("Iris"))
    assertEquals("entity:iris", CombatRuntime.toJson(iris.state)!!.getString("activeActorId"))
  }
'''
if "trueTurnAutoCombatCommitsKaiEntityLuciaEntityAsIndependentSubturns" not in test:
    close = test.rfind("}\n")
    if close < 0:
        raise RuntimeError("CombatRuntimeTest closing brace missing")
    test = test[:close] + new_tests + test[close:]
for marker in (
    "trueTurnAutoCombatCommitsKaiEntityLuciaEntityAsIndependentSubturns",
    "trueTurnAutoCombatWithoutLuciaCompletesAfterEntityTargetsKai",
    "trueTurnAutoCursorSurvivesCodecSaveLoadBetweenActors",
    "trueTurnAutoCombatExposesSyvialOverlayAndIndependentSubturns",
    "trueTurnAutoCombatUsesLivePartySlotOrderForEveryAttackResponsePair",
    "trueTurnAutoCombatBuildsFourMemberOrderAndUsesRealPartySlots",
    "trueTurnAutoCombatPreservesReorderedLivePartyOrder",
    "trueTurnAutoCombatSkipsDeadSeparatedAndMissingPartyMembers",
    "trueTurnEntityResponseDamagesOnlyItsEncodedPartyMember",
    "trueTurnAutoCursorSurvivesSaveLoadBeforeFourthMemberResponse",
    "trueTurnKaiSubturnDoesNotRunLegacyCompanionAssists",
    'CombatRuntime.resolve(state, "AUTO_COMBAT_STEP", "auto")',
    'assertEquals("entity:lucia", CombatRuntime.toJson(lucia.state)!!.getString("activeActorId"))',
    'assertEquals("file:///android_asset/kai_entity_overlay.png", CombatRuntime.toJson(kai.state)!!.getString("activeActorOverlayUri"))',
    'assertEquals("file:///android_asset/lucia_entity_overlay.png", CombatRuntime.toJson(lucia.state)!!.getString("activeActorOverlayUri"))',
    'assertEquals("file:///android_asset/syvial_entity_overlay.png", syvialJson.getString("activeActorOverlayUri"))',
):
    if marker not in test:
        raise RuntimeError("True-turn combat regression marker missing: " + marker)
TEST.write_text(test, encoding="utf-8")

print(
    "True Turn Combat V3 applied: Party-slot attack/response pairs, stable 2-second target overlays, "
    "separate Combat Round header, round-scoped time/regen, and save-persistent actor cursor."
)

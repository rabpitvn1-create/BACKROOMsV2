package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Single authoritative story state machine for the opening of Level 0.
 *
 * Providers may narrate a deterministic story directive, but they never own storyArc,
 * storyContinuity, luciaEncounter, or Lucia's Party milestone. The same pure normalizer is
 * used for pre-audit preview and final GameCore commit, so story state cannot disagree across
 * the canon boundary.
 */
object StoryProgressionPolicy {
  const val PROLOGUE_ENTRY = "STORY.PROLOGUE.ENTRY_COMPLETE"
  const val LEVEL0_ARRIVAL = "STORY.LEVEL0.ARRIVAL"
  const val LEVEL0_FIRST_CONTACT = "STORY.LEVEL0.FIRST_CONTACT_COMPLETE"
  const val LEVEL0_LUCIA_DECISION_COMPLETE = "STORY.LEVEL0.LUCIA_DECISION_COMPLETE"
  private const val LEVEL0_LUCIA_DECISION = "STORY.LEVEL0.LUCIA_DECISION"
  private const val LEVEL0_EPSILON_ENTRY = "STORY.LEVEL0.EPSILON.ENTRY"

  private enum class Directive {
    OUTSIDE_LEVEL0,
    HOLD_PROLOGUE,
    ARRIVAL_ONLY,
    HOLD_ARRIVAL,
    LUCIA_FIRST_CONTACT,
    HOLD_FIRST_CONTACT,
    LUCIA_JOIN_DECISION,
    STORY_COMPLETE,
  }

  /** Human-readable machine directive injected into writer/auditor prompts. */
  @JvmStatic
  fun directive(before: JSONObject, action: String): String = when (directiveKind(before, action)) {
    Directive.OUTSIDE_LEVEL0 -> "OUTSIDE_LEVEL0: no Level 0 story mutation."
    Directive.HOLD_PROLOGUE -> "HOLD_PROLOGUE: keep STORY.PROLOGUE.ENTRY_COMPLETE; Lucia must not appear."
    Directive.ARRIVAL_ONLY -> "ARRIVAL_ONLY: complete STORY.LEVEL0.ARRIVAL this turn; Lucia must not appear yet."
    Directive.HOLD_ARRIVAL -> "HOLD_ARRIVAL: keep STORY.LEVEL0.ARRIVAL; do not invent Lucia contact."
    Directive.LUCIA_FIRST_CONTACT -> "LUCIA_FIRST_CONTACT: this turn deterministically completes Lucia first contact, but Lucia remains outside Party."
    Directive.HOLD_FIRST_CONTACT -> "HOLD_FIRST_CONTACT: Lucia has been met but no mutual Party decision occurs this turn."
    Directive.LUCIA_JOIN_DECISION -> "LUCIA_JOIN_DECISION: this explicit together/team-up action completes the authored mutual tactical Party decision."
    Directive.STORY_COMPLETE -> "STORY_COMPLETE: preserve the already-completed Lucia decision and later story state."
  }

  /**
   * Removes provider ownership of story state and applies exactly one deterministic story step
   * from the state that began the turn plus the player's action.
   */
  @JvmStatic
  fun normalizeCandidate(before: JSONObject, rawCandidate: JSONObject, action: String): JSONObject {
    val candidate = JSONObject(rawCandidate.toString())
    if (currentLevel(before, candidate) != 0) return candidate

    val beforeFlags = before.optJSONObject("flags") ?: JSONObject()
    val flags = ensureFlags(candidate, beforeFlags)
    restoreStoryOwnedState(flags, beforeFlags)
    removeLuciaFromParty(candidate)

    val beforeArc = beforeFlags.optJSONObject("storyArc")
    val completedBefore = completed(beforeArc)
    val turn = candidate.optInt("turn", before.optInt("turn", 1) + 1)

    when (directiveKind(before, action)) {
      Directive.OUTSIDE_LEVEL0 -> Unit
      Directive.HOLD_PROLOGUE -> keepPrologue(flags, completedBefore)
      Directive.ARRIVAL_ONLY -> applyArrival(flags, completedBefore, turn)
      Directive.HOLD_ARRIVAL -> keepArrival(flags, completedBefore)
      Directive.LUCIA_FIRST_CONTACT -> applyFirstContact(flags, completedBefore, turn)
      Directive.HOLD_FIRST_CONTACT -> keepFirstContact(flags, completedBefore)
      Directive.LUCIA_JOIN_DECISION -> {
        applyJoinDecision(flags, completedBefore, turn)
        ensureLuciaInParty(candidate, before)
      }
      Directive.STORY_COMPLETE -> ensureLuciaInParty(candidate, before)
    }

    return candidate
  }

  fun isLevel0ExplorationAction(action: String): Boolean {
    val text = action.trim().lowercase(Locale.ROOT)
    if (text.isEmpty()) return false

    val explicit = listOf(
      "khám phá", "thăm dò", "dò đường", "explore", "survey the area", "continue exploring"
    )
    if (explicit.any(text::contains)) return true

    val verbs = listOf(
      "tìm kiếm", "quan sát", "kiểm tra", "lắng nghe", "nghe ngóng", "di chuyển", "đi tiếp", "tiến sâu", "đi quanh", "tìm đường",
      "search", "inspect", "listen", "move", "walk", "advance", "continue", "check"
    )
    val environment = listOf(
      "level 0", "level zero", "phòng", "hành lang", "tường", "đèn", "trần", "sàn", "góc", "xung quanh", "lối", "đường",
      "room", "corridor", "wall", "light", "ceiling", "floor", "corner", "surroundings", "route"
    )
    return verbs.any(text::contains) && environment.any(text::contains)
  }

  private fun directiveKind(before: JSONObject, action: String): Directive {
    if (before.optJSONObject("level")?.optInt("number", 0) != 0) return Directive.OUTSIDE_LEVEL0
    val flags = before.optJSONObject("flags") ?: JSONObject()
    val arc = flags.optJSONObject("storyArc")
    val done = completed(arc)
    if (LEVEL0_LUCIA_DECISION_COMPLETE in done) return Directive.STORY_COMPLETE

    val arrival = LEVEL0_ARRIVAL in done
    val firstContact = LEVEL0_FIRST_CONTACT in done
    val beat = arc?.optString("currentBeat", "").orEmpty()
    val prologueLocked = !arrival && (beat == PROLOGUE_ENTRY || PROLOGUE_ENTRY in done)

    if (!arrival) {
      return if (prologueLocked && isLevel0ExplorationAction(action)) Directive.ARRIVAL_ONLY else Directive.HOLD_PROLOGUE
    }
    if (!firstContact) {
      return if (isLevel0ExplorationAction(action)) Directive.LUCIA_FIRST_CONTACT else Directive.HOLD_ARRIVAL
    }
    return if (isLuciaJoinDecisionAction(action)) Directive.LUCIA_JOIN_DECISION else Directive.HOLD_FIRST_CONTACT
  }

  private fun isLuciaJoinDecisionAction(action: String): Boolean {
    val text = action.trim().lowercase(Locale.ROOT)
    if (text.isEmpty()) return false
    val together = listOf(
      "đi cùng", "đi chung", "cùng đi", "tiếp tục cùng", "đồng hành", "lập đội", "vào đội", "tham gia đội", "hợp tác",
      "travel together", "move together", "team up", "join me", "join us", "work together"
    )
    return together.any(text::contains)
  }

  private fun restoreStoryOwnedState(flags: JSONObject, beforeFlags: JSONObject) {
    for (key in listOf("storyArc", "storyContinuity", "luciaEncounter")) {
      val beforeValue = beforeFlags.opt(key)
      if (beforeValue == null || beforeValue === JSONObject.NULL) {
        flags.remove(key)
      } else {
        flags.put(key, deepCopy(beforeValue))
      }
    }
  }

  private fun applyArrival(flags: JSONObject, completedBefore: Set<String>, turn: Int) {
    val arc = ensureStoryArc(flags)
    val done = linkedSetOf<String>().apply {
      addAll(completedBefore)
      add(PROLOGUE_ENTRY)
      add(LEVEL0_ARRIVAL)
      remove(LEVEL0_FIRST_CONTACT)
      remove(LEVEL0_LUCIA_DECISION_COMPLETE)
    }
    arc.put("current", "MAIN.LEVEL0")
    arc.put("currentBeat", LEVEL0_ARRIVAL)
    arc.put("nextBeat", LEVEL0_FIRST_CONTACT)
    arc.put("completed", JSONArray(done.toList()))
    flags.remove("luciaEncounter")

    val continuity = ensureContinuity(flags)
    appendUnique(
      continuity,
      "events",
      JSONObject()
        .put("id", "EVT.$turn.LEVEL0.ARRIVAL")
        .put("turn", turn)
        .put("type", "story-beat")
        .put("fact", "Kai completed the initial solo exploration phase of Level 0; Lucia was not encountered in this transition turn.")
    )
    appendUnique(
      continuity,
      "knowledge",
      JSONObject()
        .put("id", "KNOW.STORY.LEVEL0.ARRIVAL")
        .put("factId", LEVEL0_ARRIVAL)
        .put("turn", turn)
        .put("knownBy", JSONArray().put("kai"))
    )
    continuity.put("lastTurn", turn)
  }

  private fun applyFirstContact(flags: JSONObject, completedBefore: Set<String>, turn: Int) {
    val arc = ensureStoryArc(flags)
    val done = linkedSetOf<String>().apply {
      addAll(completedBefore)
      add(PROLOGUE_ENTRY)
      add(LEVEL0_ARRIVAL)
      add(LEVEL0_FIRST_CONTACT)
      remove(LEVEL0_LUCIA_DECISION_COMPLETE)
    }
    arc.put("current", "MAIN.LEVEL0")
    arc.put("currentBeat", LEVEL0_FIRST_CONTACT)
    arc.put("nextBeat", LEVEL0_LUCIA_DECISION)
    arc.put("completed", JSONArray(done.toList()))
    flags.put(
      "luciaEncounter",
      JSONObject()
        .put("status", "met")
        .put("level", 0)
        .put("sublevelId", "")
        .put("partyEligible", true)
        .put("joinPending", true)
        .put("knowledge", "human_survivor_confirmed")
        .put("relationship", "initial_tactical_trust")
    )

    val continuity = ensureContinuity(flags)
    appendUnique(
      continuity,
      "events",
      JSONObject()
        .put("id", "EVT.$turn.LEVEL0.LUCIA_CONTACT")
        .put("turn", turn)
        .put("type", "story-beat")
        .put("fact", "Kai met Lucia in Level 0; both established only limited tactical trust and Lucia did not join the Party in this turn.")
    )
    appendUnique(
      continuity,
      "knowledge",
      JSONObject()
        .put("id", "KNOW.STORY.LEVEL0.LUCIA")
        .put("factId", LEVEL0_FIRST_CONTACT)
        .put("turn", turn)
        .put("knownBy", JSONArray().put("kai").put("lucia"))
    )
    continuity.put("lastTurn", turn)
  }

  private fun applyJoinDecision(flags: JSONObject, completedBefore: Set<String>, turn: Int) {
    val arc = ensureStoryArc(flags)
    val done = linkedSetOf<String>().apply {
      addAll(completedBefore)
      add(PROLOGUE_ENTRY)
      add(LEVEL0_ARRIVAL)
      add(LEVEL0_FIRST_CONTACT)
      add(LEVEL0_LUCIA_DECISION_COMPLETE)
    }
    arc.put("current", "MAIN.LEVEL0")
    arc.put("currentBeat", LEVEL0_LUCIA_DECISION_COMPLETE)
    arc.put("nextBeat", LEVEL0_EPSILON_ENTRY)
    arc.put("completed", JSONArray(done.toList()))
    flags.put(
      "luciaEncounter",
      JSONObject()
        .put("status", "joined")
        .put("level", 0)
        .put("sublevelId", "")
        .put("partyEligible", true)
        .put("joinPending", false)
        .put("knowledge", "human_survivor_confirmed")
        .put("relationship", "earned_tactical_trust")
        .put("romance", "none")
        .put("playerAgency", "mutual-party-decision")
    )

    val continuity = ensureContinuity(flags)
    appendUnique(
      continuity,
      "events",
      JSONObject()
        .put("id", "EVT.$turn.LEVEL0.LUCIA_DECISION")
        .put("turn", turn)
        .put("type", "story-beat")
        .put("fact", "Kai and Lucia mutually chose to travel together under explicit tactical boundaries; no romantic state was established.")
    )
    continuity.put("lastTurn", turn)
  }

  private fun keepPrologue(flags: JSONObject, completedBefore: Set<String>) {
    val arc = ensureStoryArc(flags)
    val done = linkedSetOf<String>().apply {
      addAll(completedBefore)
      add(PROLOGUE_ENTRY)
      remove(LEVEL0_ARRIVAL)
      remove(LEVEL0_FIRST_CONTACT)
      remove(LEVEL0_LUCIA_DECISION_COMPLETE)
    }
    arc.put("current", "MAIN.PROLOGUE")
    arc.put("currentBeat", PROLOGUE_ENTRY)
    arc.put("nextBeat", LEVEL0_ARRIVAL)
    arc.put("completed", JSONArray(done.toList()))
    flags.remove("luciaEncounter")
  }

  private fun keepArrival(flags: JSONObject, completedBefore: Set<String>) {
    val arc = ensureStoryArc(flags)
    val done = linkedSetOf<String>().apply {
      addAll(completedBefore)
      add(PROLOGUE_ENTRY)
      add(LEVEL0_ARRIVAL)
      remove(LEVEL0_FIRST_CONTACT)
      remove(LEVEL0_LUCIA_DECISION_COMPLETE)
    }
    arc.put("current", "MAIN.LEVEL0")
    arc.put("currentBeat", LEVEL0_ARRIVAL)
    arc.put("nextBeat", LEVEL0_FIRST_CONTACT)
    arc.put("completed", JSONArray(done.toList()))
    flags.remove("luciaEncounter")
  }

  private fun keepFirstContact(flags: JSONObject, completedBefore: Set<String>) {
    val arc = ensureStoryArc(flags)
    val done = linkedSetOf<String>().apply {
      addAll(completedBefore)
      add(PROLOGUE_ENTRY)
      add(LEVEL0_ARRIVAL)
      add(LEVEL0_FIRST_CONTACT)
      remove(LEVEL0_LUCIA_DECISION_COMPLETE)
    }
    arc.put("current", "MAIN.LEVEL0")
    arc.put("currentBeat", LEVEL0_FIRST_CONTACT)
    arc.put("nextBeat", LEVEL0_LUCIA_DECISION)
    arc.put("completed", JSONArray(done.toList()))
    val encounter = flags.optJSONObject("luciaEncounter") ?: JSONObject()
    encounter.put("status", "met")
    encounter.put("level", 0)
    encounter.put("partyEligible", true)
    encounter.put("joinPending", true)
    if (!encounter.has("relationship")) encounter.put("relationship", "initial_tactical_trust")
    flags.put("luciaEncounter", encounter)
  }

  private fun ensureFlags(candidate: JSONObject, beforeFlags: JSONObject): JSONObject {
    val current = candidate.optJSONObject("flags")
    if (current != null) return current
    return JSONObject(beforeFlags.toString()).also { candidate.put("flags", it) }
  }

  private fun ensureStoryArc(flags: JSONObject): JSONObject =
    flags.optJSONObject("storyArc") ?: JSONObject().also { flags.put("storyArc", it) }

  private fun ensureContinuity(flags: JSONObject): JSONObject =
    flags.optJSONObject("storyContinuity") ?: JSONObject().also { flags.put("storyContinuity", it) }

  private fun completed(arc: JSONObject?): Set<String> {
    val out = linkedSetOf<String>()
    val values = arc?.optJSONArray("completed") ?: return out
    for (i in 0 until values.length()) values.optString(i, "").takeIf(String::isNotBlank)?.let(out::add)
    return out
  }

  private fun removeLuciaFromParty(candidate: JSONObject) {
    val party = candidate.optJSONArray("party") ?: return
    val filtered = JSONArray()
    for (i in 0 until party.length()) {
      val raw = party.opt(i)
      val identity = when (raw) {
        is JSONObject -> raw.optString("id", "") + " " + raw.optString("name", "")
        else -> raw?.toString().orEmpty()
      }.lowercase(Locale.ROOT)
      if (!isLucia(identity)) filtered.put(raw)
    }
    candidate.put("party", filtered)
  }

  private fun ensureLuciaInParty(candidate: JSONObject, before: JSONObject) {
    removeLuciaFromParty(candidate)
    val party = candidate.optJSONArray("party") ?: JSONArray().also { candidate.put("party", it) }
    val beforeParty = before.optJSONArray("party")
    var preserved: JSONObject? = null
    if (beforeParty != null) {
      for (i in 0 until beforeParty.length()) {
        val member = beforeParty.optJSONObject(i) ?: continue
        val identity = (member.optString("id", "") + " " + member.optString("name", "")).lowercase(Locale.ROOT)
        if (isLucia(identity)) {
          preserved = JSONObject(member.toString())
          break
        }
      }
    }
    party.put(
      preserved ?: JSONObject()
        .put("id", "lucia")
        .put("name", "Lucia")
        .put("joinConfirmed", true)
        .put("present", true)
    )
  }

  private fun isLucia(value: String): Boolean =
    value.contains("lucia") || value.contains("hứa thuý mai") || value.contains("hứa thúy mai")

  private fun currentLevel(before: JSONObject, candidate: JSONObject): Int {
    before.optJSONObject("level")?.let { return it.optInt("number", 0) }
    candidate.optJSONObject("level")?.let { return it.optInt("number", 0) }
    return 0
  }

  private fun deepCopy(value: Any): Any = when (value) {
    is JSONObject -> JSONObject(value.toString())
    is JSONArray -> JSONArray(value.toString())
    else -> value
  }

  private fun appendUnique(root: JSONObject, key: String, value: JSONObject) {
    val array = root.optJSONArray(key) ?: JSONArray().also { root.put(key, it) }
    val id = value.optString("id", "")
    for (i in 0 until array.length()) if (array.optJSONObject(i)?.optString("id", "") == id) return
    array.put(value)
  }
}

package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Authoritative story gate for the opening of Level 0.
 *
 * The GM may narrate the exploration, but it does not own the Prologue -> Arrival transition.
 * That transition is deterministic so a model omission cannot leave the campaign stuck forever,
 * and a model cannot collapse Arrival, Lucia first contact, and Lucia joining into one turn.
 */
object StoryProgressionPolicy {
  const val PROLOGUE_ENTRY = "STORY.PROLOGUE.ENTRY_COMPLETE"
  const val LEVEL0_ARRIVAL = "STORY.LEVEL0.ARRIVAL"
  const val LEVEL0_FIRST_CONTACT = "STORY.LEVEL0.FIRST_CONTACT_COMPLETE"
  const val LEVEL0_LUCIA_DECISION_COMPLETE = "STORY.LEVEL0.LUCIA_DECISION_COMPLETE"
  private const val LEVEL0_LUCIA_DECISION = "STORY.LEVEL0.LUCIA_DECISION"

  fun normalizeCandidate(before: JSONObject, rawCandidate: JSONObject, action: String): JSONObject {
    val candidate = JSONObject(rawCandidate.toString())
    if (currentLevel(before, candidate) != 0) return candidate

    val beforeFlags = before.optJSONObject("flags") ?: JSONObject()
    val beforeArc = beforeFlags.optJSONObject("storyArc")
    val completedBefore = completed(beforeArc)
    val arrivalBefore = LEVEL0_ARRIVAL in completedBefore
    val firstContactBefore = LEVEL0_FIRST_CONTACT in completedBefore
    val beatBefore = beforeArc?.optString("currentBeat", "").orEmpty()
    val prologueLocked = !arrivalBefore && (beatBefore == PROLOGUE_ENTRY || PROLOGUE_ENTRY in completedBefore)

    if (!arrivalBefore) {
      // First contact is locked by the state that began the turn. A provider cannot use the
      // same response to complete Arrival and materialize Lucia.
      removeLuciaFromParty(candidate)
      val flags = ensureFlags(candidate, beforeFlags)
      flags.remove("luciaEncounter")
      if (prologueLocked) {
        if (isLevel0ExplorationAction(action)) {
          applyArrival(flags, completedBefore, candidate.optInt("turn", before.optInt("turn", 1) + 1))
        } else {
          keepPrologue(flags, completedBefore)
        }
      }
      return candidate
    }

    val flags = ensureFlags(candidate, beforeFlags)
    val arc = ensureStoryArc(flags, beforeArc)
    val candidateCompleted = completed(arc).toMutableSet()
    candidateCompleted += completedBefore
    candidateCompleted += LEVEL0_ARRIVAL

    if (!firstContactBefore) {
      // The first-contact turn may establish Lucia's encounter state, but never Party membership
      // or the later decision milestone. Meeting Lucia is not the join decision.
      removeLuciaFromParty(candidate)
      val validFirstContact = isValidFirstContactCandidate(flags, candidateCompleted)
      if (validFirstContact) {
        candidateCompleted += LEVEL0_FIRST_CONTACT
        candidateCompleted.remove(LEVEL0_LUCIA_DECISION_COMPLETE)
        arc.put("current", "MAIN.LEVEL0")
        arc.put("currentBeat", LEVEL0_FIRST_CONTACT)
        arc.put("nextBeat", LEVEL0_LUCIA_DECISION)
      } else {
        candidateCompleted.remove(LEVEL0_FIRST_CONTACT)
        candidateCompleted.remove(LEVEL0_LUCIA_DECISION_COMPLETE)
        flags.remove("luciaEncounter")
        arc.put("current", "MAIN.LEVEL0")
        arc.put("currentBeat", LEVEL0_ARRIVAL)
        arc.put("nextBeat", LEVEL0_FIRST_CONTACT)
      }
      arc.put("completed", JSONArray(candidateCompleted.toList()))
      return candidate
    }

    // First contact was already complete when this turn began. Party membership still requires
    // the separate, machine-verifiable Lucia decision/join confirmation.
    candidateCompleted += LEVEL0_FIRST_CONTACT
    val joinedBefore = isValidLuciaJoinState(before)
    val candidateJoinAllowed = isValidLuciaJoinState(flags, candidateCompleted)
    val joinAllowed = joinedBefore || candidateJoinAllowed
    if (!joinAllowed) {
      removeLuciaFromParty(candidate)
      if (LEVEL0_LUCIA_DECISION_COMPLETE !in completedBefore) {
        candidateCompleted.remove(LEVEL0_LUCIA_DECISION_COMPLETE)
      }
    }

    arc.put("completed", JSONArray(candidateCompleted.toList()))
    val beat = arc.optString("currentBeat", "")
    when {
      LEVEL0_LUCIA_DECISION_COMPLETE in completedBefore -> {
        if (beat.isBlank() || beat == PROLOGUE_ENTRY || beat == LEVEL0_ARRIVAL || beat == LEVEL0_FIRST_CONTACT) {
          arc.put("current", "MAIN.LEVEL0")
          arc.put("currentBeat", LEVEL0_LUCIA_DECISION_COMPLETE)
        }
      }
      candidateJoinAllowed -> {
        arc.put("current", "MAIN.LEVEL0")
        arc.put("currentBeat", LEVEL0_LUCIA_DECISION_COMPLETE)
      }
      beat.isBlank() || beat == PROLOGUE_ENTRY || beat == LEVEL0_ARRIVAL || beat == LEVEL0_LUCIA_DECISION_COMPLETE -> {
        arc.put("current", "MAIN.LEVEL0")
        arc.put("currentBeat", LEVEL0_FIRST_CONTACT)
        arc.put("nextBeat", LEVEL0_LUCIA_DECISION)
      }
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

  private fun applyArrival(flags: JSONObject, completedBefore: Set<String>, turn: Int) {
    val arc = ensureStoryArc(flags, flags.optJSONObject("storyArc"))
    val completed = linkedSetOf<String>()
    completed += completedBefore
    completed += PROLOGUE_ENTRY
    completed += LEVEL0_ARRIVAL
    arc.put("current", "MAIN.LEVEL0")
    arc.put("currentBeat", LEVEL0_ARRIVAL)
    arc.put("nextBeat", LEVEL0_FIRST_CONTACT)
    arc.put("completed", JSONArray(completed.toList()))

    val continuity = flags.optJSONObject("storyContinuity") ?: JSONObject().also { flags.put("storyContinuity", it) }
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

  private fun keepPrologue(flags: JSONObject, completedBefore: Set<String>) {
    val arc = ensureStoryArc(flags, flags.optJSONObject("storyArc"))
    val completed = linkedSetOf<String>()
    completed += completedBefore
    completed += PROLOGUE_ENTRY
    arc.put("current", "MAIN.PROLOGUE")
    arc.put("currentBeat", PROLOGUE_ENTRY)
    arc.put("nextBeat", LEVEL0_ARRIVAL)
    arc.put("completed", JSONArray(completed.toList()))
  }

  private fun ensureFlags(candidate: JSONObject, beforeFlags: JSONObject): JSONObject {
    val current = candidate.optJSONObject("flags")
    if (current != null) return current
    return JSONObject(beforeFlags.toString()).also { candidate.put("flags", it) }
  }

  private fun ensureStoryArc(flags: JSONObject, fallback: JSONObject?): JSONObject {
    val current = flags.optJSONObject("storyArc")
    if (current != null) return current
    return JSONObject((fallback ?: JSONObject()).toString()).also { flags.put("storyArc", it) }
  }

  private fun completed(arc: JSONObject?): Set<String> {
    val out = linkedSetOf<String>()
    val values = arc?.optJSONArray("completed") ?: return out
    for (i in 0 until values.length()) values.optString(i, "").takeIf(String::isNotBlank)?.let(out::add)
    return out
  }

  private fun isValidFirstContactCandidate(flags: JSONObject, completed: Set<String>): Boolean {
    if (LEVEL0_FIRST_CONTACT !in completed) return false
    val encounter = flags.optJSONObject("luciaEncounter") ?: return false
    val status = encounter.optString("status", "").lowercase(Locale.ROOT)
    val level = if (encounter.has("level")) encounter.optInt("level", -1) else 0
    return level == 0 && status in setOf("met", "contact", "contacted", "first_contact", "first-contact")
  }

  private fun isValidLuciaJoinState(state: JSONObject): Boolean {
    val flags = state.optJSONObject("flags") ?: return false
    return isValidLuciaJoinState(flags, completed(flags.optJSONObject("storyArc")))
  }

  private fun isValidLuciaJoinState(flags: JSONObject, completed: Set<String>): Boolean {
    if (LEVEL0_FIRST_CONTACT !in completed || LEVEL0_LUCIA_DECISION_COMPLETE !in completed) return false
    val encounter = flags.optJSONObject("luciaEncounter") ?: return false
    val status = encounter.optString("status", "").lowercase(Locale.ROOT)
    return status == "joined" && encounter.optBoolean("partyEligible", false) && !encounter.optBoolean("joinPending", true)
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

  private fun isLucia(value: String): Boolean =
    value.contains("lucia") || value.contains("hứa thuý mai") || value.contains("hứa thúy mai")

  private fun currentLevel(before: JSONObject, candidate: JSONObject): Int {
    before.optJSONObject("level")?.let { return it.optInt("number", 0) }
    candidate.optJSONObject("level")?.let { return it.optInt("number", 0) }
    return 0
  }

  private fun appendUnique(root: JSONObject, key: String, value: JSONObject) {
    val array = root.optJSONArray(key) ?: JSONArray().also { root.put(key, it) }
    val id = value.optString("id", "")
    for (i in 0 until array.length()) if (array.optJSONObject(i)?.optString("id", "") == id) return
    array.put(value)
  }
}

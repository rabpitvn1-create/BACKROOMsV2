package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.LinkedHashMap
import java.util.Locale
import java.util.TimeZone

object RuntimeDebugLog {
  private const val SCHEMA_VERSION = 1
  private const val REDACTED = "[REDACTED]"
  private val startedWallMs = System.currentTimeMillis()
  private val startedMonoNs = System.nanoTime()
  private var session = JSONObject()
  private val events = JSONArray()
  private val turns = LinkedHashMap<Int, JSONObject>()

  @JvmStatic @Synchronized fun initializeSession(metadata: JSONObject?) {
    session = sanitizeObject(metadata ?: JSONObject())
    session.put("startedAt", iso(startedWallMs))
    session.put("startedElapsedMs", 0)
    session.put("schemaVersion", SCHEMA_VERSION)
  }

  @JvmStatic @Synchronized fun resetForTests() {
    session = JSONObject()
    while (events.length() > 0) events.remove(events.length() - 1)
    turns.clear()
  }

  @JvmStatic @Synchronized fun beginTurn(turn: Int, action: String?, meta: Boolean, before: JSONObject?) {
    val record = turnRecord(turn)
    record.put("turn", turn)
    record.put("startedAt", iso(System.currentTimeMillis()))
    record.put("input", JSONObject().put("action", redactString(action.orEmpty())).put("meta", meta).put("stateBefore", sanitizeValue(before ?: JSONObject())))
    recordStageInternal(turn, "stateBefore", before ?: JSONObject())
    recordEventInternal("input", "turn_begin", turn, JSONObject().put("action", action.orEmpty()).put("meta", meta).put("stateBefore", before ?: JSONObject()))
  }

  @JvmStatic @Synchronized fun recordTurnStage(turn: Int, stage: String, payload: Any?) {
    recordStageInternal(turn, stage, payload)
  }

  @JvmStatic @Synchronized fun appendTurnStage(turn: Int, stage: String, payload: Any?) {
    val record = turnRecord(turn)
    var array = record.optJSONArray(stage)
    if (array == null) { array = JSONArray(); record.put(stage, array) }
    array.put(sanitizeValue(payload))
  }

  @JvmStatic @Synchronized fun recordEvent(category: String, event: String, turn: Int, payload: Any?) {
    recordEventInternal(category, event, turn, payload)
  }

  @JvmStatic @Synchronized fun finishTurn(turn: Int, after: JSONObject?, reply: String?, error: String?) {
    val record = turnRecord(turn)
    if (after != null) { record.put("stateAfter", sanitizeValue(after)); recordStageInternal(turn, "stateAfter", after) }
    if (reply != null) record.put("finalReply", redactString(reply))
    if (!error.isNullOrBlank()) record.put("error", redactString(error))
    record.put("finishedAt", iso(System.currentTimeMillis()))
    record.put("finishedElapsedMs", elapsedMs())
    recordEventInternal("final_commit", if (error.isNullOrBlank()) "turn_finished" else "turn_failed", turn,
      JSONObject().put("stateAfter", after ?: JSONObject.NULL).put("reply", reply ?: "").put("error", error ?: ""))
  }

  @JvmStatic @Synchronized fun exportJson(currentState: JSONObject?): String {
    val sessionCopy = JSONObject(session.toString())
    if (currentState != null) {
      sessionCopy.put("currentTurn", currentState.optInt("turn", 0))
      sessionCopy.put("currentLevel", currentState.optJSONObject("level")?.optInt("number", -1) ?: -1)
      sessionCopy.put("currentState", sanitizeValue(currentState))
    }
    sessionCopy.put("exportedAt", iso(System.currentTimeMillis()))
    sessionCopy.put("exportedElapsedMs", elapsedMs())
    val turnArray = JSONArray(); turns.values.forEach { turnArray.put(JSONObject(it.toString())) }
    return JSONObject().put("schemaVersion", SCHEMA_VERSION).put("session", sessionCopy).put("events", JSONArray(events.toString())).put("turns", turnArray).toString(2)
  }

  @JvmStatic fun sanitizeForExport(value: Any?): Any? = synchronized(this) { sanitizeValue(value) }

  private fun turnRecord(turn: Int): JSONObject = turns.getOrPut(turn) { JSONObject().put("turn", turn) }
  private fun recordStageInternal(turn: Int, stage: String, payload: Any?) { turnRecord(turn).put(stage, sanitizeValue(payload)) }
  private fun recordEventInternal(category: String, event: String, turn: Int, payload: Any?) {
    events.put(JSONObject().put("timestamp", iso(System.currentTimeMillis())).put("elapsedMs", elapsedMs()).put("category", category).put("event", event).put("turn", turn).put("payload", sanitizeValue(payload)))
  }

  private fun sanitizeValue(value: Any?): Any = when (value) {
    null, JSONObject.NULL -> JSONObject.NULL
    is JSONObject -> sanitizeObject(value)
    is JSONArray -> JSONArray().also { out -> for (index in 0 until value.length()) out.put(sanitizeValue(value.opt(index))) }
    is Map<*, *> -> JSONObject().also { out -> value.forEach { (key, item) -> val name = key?.toString().orEmpty(); out.put(name, if (isSensitiveKey(name)) REDACTED else sanitizeValue(item)) } }
    is Iterable<*> -> JSONArray().also { out -> value.forEach { out.put(sanitizeValue(it)) } }
    is String -> redactString(value)
    is Number, is Boolean -> value
    else -> redactString(value.toString())
  }

  private fun sanitizeObject(source: JSONObject): JSONObject {
    val out = JSONObject(); source.keys().forEach { key -> out.put(key, if (isSensitiveKey(key)) REDACTED else sanitizeValue(source.opt(key))) }; return out
  }

  private fun isSensitiveKey(key: String): Boolean {
    val k = key.lowercase(Locale.ROOT).replace("-", "").replace("_", "").replace(" ", "")
    return listOf("authorization", "apikey", "token", "secret", "credential", "password").any(k::contains)
  }

  private fun redactString(source: String): String {
    var value = source.replace(Regex("(?i)Bearer\\s+[^\\s,;]+"), REDACTED)
    value = value.replace(Regex("AIza[0-9A-Za-z_-]{16,}"), REDACTED)
    value = value.replace(Regex("(?i)\\bsk-[A-Za-z0-9_-]{12,}\\b"), REDACTED)
    return value
  }

  private fun elapsedMs(): Long = (System.nanoTime() - startedMonoNs) / 1_000_000L
  private fun iso(timestamp: Long): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US); formatter.timeZone = TimeZone.getTimeZone("UTC"); return formatter.format(Date(timestamp))
  }
}

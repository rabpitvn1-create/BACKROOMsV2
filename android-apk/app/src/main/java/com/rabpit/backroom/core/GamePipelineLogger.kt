package com.rabpit.backroom.core

import android.util.Log
import org.json.JSONObject

fun interface GamePipelineLogger {
  fun log(event: PipelineLogEvent)
}

data class PipelineLogEvent(
  val stage: String,
  val turnId: String? = null,
  val commandId: String? = null,
  val source: CommandSource? = null,
  val intent: GameIntent? = null,
  val confidence: Float? = null,
  val details: Map<String, String> = emptyMap()
)

object NoOpGamePipelineLogger : GamePipelineLogger { override fun log(event: PipelineLogEvent) = Unit }

class AndroidGamePipelineLogger(private val enabled: Boolean) : GamePipelineLogger {
  override fun log(event: PipelineLogEvent) {
    val turnNumber = event.turnId?.substringAfterLast('_')?.toIntOrNull() ?: 0
    RuntimeDebugLog.recordEvent(
      "game_core",
      event.stage.lowercase(),
      turnNumber,
      JSONObject()
        .put("turnId", event.turnId ?: "")
        .put("commandId", event.commandId ?: "")
        .put("source", event.source?.name ?: "")
        .put("intent", event.intent?.name ?: "")
        .put("confidence", event.confidence ?: JSONObject.NULL)
        .put("details", JSONObject(event.details))
    )

    if (!enabled) return
    Log.d("BackroomGameCore", listOfNotNull(
      "stage=${event.stage}", event.turnId?.let { "turn=$it" }, event.commandId?.let { "command=$it" },
      event.source?.let { "source=$it" }, event.intent?.let { "intent=$it" }, event.confidence?.let { "confidence=$it" },
      event.details.takeIf { it.isNotEmpty() }?.entries?.joinToString(prefix = "details=", separator = ",") { "${it.key}:${it.value.take(80)}" }
    ).joinToString(" "))
  }
}

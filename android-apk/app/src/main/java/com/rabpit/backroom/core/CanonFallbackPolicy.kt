package com.rabpit.backroom.core

import org.json.JSONObject

/** Backward-compatible entry point for the final repaired-turn recovery gate. */
object CanonFallbackPolicy {
  @JvmStatic
  fun isEligible(
    before: JSONObject,
    candidate: JSONObject,
    generated: JSONObject,
    rolls: JSONObject,
    action: String,
    meta: Boolean,
    repaired: Boolean,
  ): Boolean = CanonRecoveryPolicy.isEligible(
    before,
    candidate,
    generated,
    rolls,
    action,
    meta,
    repaired,
  )
}

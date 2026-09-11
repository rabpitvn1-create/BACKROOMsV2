package com.rabpit.backroom.core

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainCampaignLevel01Test {
  private fun html(): String {
    val p = listOf(
      File("src/main/assets/index.html"),
      File("app/src/main/assets/index.html"),
      File("android-apk/app/src/main/assets/index.html")
    ).firstOrNull { it.isFile }
    return p?.readText(Charsets.UTF_8) ?: error("index.html not found")
  }

  @Test fun level01StateIsLocked() {
    val text = html()
    assertTrue(text.contains("LEVEL 0.1 / DEEP EMPTINESS — BORROWED SHELTER"))
    assertTrue(text.contains("currentBeat:\"STORY.LEVEL0.1.COMPLETE\""))
    assertTrue(text.contains("nextBeat:\"STORY.LEVEL0.11.ENTRY\""))
    assertTrue(text.contains("sublevelId:\"SUBLEVEL.00.1\""))
    assertTrue(text.contains("rating:5,source:\"WIKI_DIRECT\",wikiClass:\"CLASS 5\""))
    assertTrue(text.contains("relationship:\"earned_tactical_trust\",romance:\"none\""))
  }

  @Test fun level01DoesNotPromoteUnknownEvidence() {
    val text = html()
    assertTrue(text.contains("Chưa biết là đồ dùng được."))
    assertTrue(text.contains("Không dấu Async mà họ có thể xác nhận."))
    assertFalse(text.contains("Async đã tạo ra Backrooms"))
    assertFalse(text.contains("currentBeat:\"STORY.LEVEL0.11.ENTRY\""))
  }
}

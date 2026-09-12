package com.rabpit.backroom.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainCampaignLevel01Test {
  @Test fun level01HistoryRemainsMaterializedInLockedOrder() {
    val text = materializedCampaignHtml()
    assertTrue(text.contains("LEVEL 0.1 / DEEP EMPTINESS — BORROWED SHELTER"))
    assertTrue(text.contains("STORY.LEVEL0.1.COMPLETE"))
    assertTrue(text.contains("STORY.LEVEL0.11.ENTRY"))
    assertTrue(text.contains("KNOW.STORY.LEVEL0.1"))
    assertTrue(text.contains("THREAD.MAIN.LEVEL0.1"))
    assertTrue(text.contains("relationship:\"earned_tactical_trust\",romance:\"none\""))
  }

  @Test fun level01DoesNotPromoteUnknownEvidence() {
    val text = materializedCampaignHtml()
    assertTrue(text.contains("Chưa biết là đồ dùng được."))
    assertTrue(text.contains("Không dấu Async mà họ có thể xác nhận."))
    assertFalse(text.contains("Async đã tạo ra Backrooms"))
    assertFalse(text.contains("currentBeat:\"STORY.LEVEL0.11.ENTRY\""))
  }
}

package com.rabpit.backroom.core

import org.junit.Assert.assertTrue
import org.junit.Test

class MainCampaignLevel011Test {
  @Test fun level011HistoryRemainsMaterializedInLockedOrder() {
    val text = materializedCampaignHtml()
    assertTrue(text.contains("LEVEL 0.11 / WATER DAMAGE — MEASURE THE CURRENT"))
    assertTrue(text.contains("STORY.LEVEL0.11.COMPLETE"))
    assertTrue(text.contains("STORY.LEVEL0.22.ENTRY"))
    assertTrue(text.contains("KNOW.STORY.LEVEL0.11"))
    assertTrue(text.contains("THREAD.MAIN.LEVEL0.11"))
    assertTrue(text.contains("relationship:\"earned_tactical_trust\",romance:\"none\""))
  }
}

package com.rabpit.backroom.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainCampaignLevel022Test {
  @Test fun level022HistoryRemainsMaterialized() {
    val text = materializedCampaignHtml()

    assertTrue(text.contains("LEVEL 0.22 / FULLY REMODELED — USEFUL IS NOT SAFE"))
    assertTrue(text.contains("STORY.LEVEL0.22.COMPLETE"))
    assertTrue(text.contains("STORY.LEVEL0.23.ENTRY"))
    assertTrue(text.contains("KNOW.STORY.LEVEL0.22"))
    assertTrue(text.contains("THREAD.MAIN.LEVEL0.22"))
    assertTrue(text.contains("salvaging only verified portable construction materials"))
    assertFalse(text.contains("STORY.LEVEL0.22.COMPLETE\",nextBeat:\"STORY.LEVEL1"))
  }
}

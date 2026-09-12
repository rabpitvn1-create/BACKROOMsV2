package com.rabpit.backroom.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainCampaignLevel023Test {
  @Test fun level023HistoryRemainsMaterializedInLockedOrder() {
    val text = materializedCampaignHtml()

    assertTrue(text.contains("LEVEL 0.23 / HALF FINISHED — TRUST THE EDGE, NOT THE PROMISE"))
    assertTrue(text.contains("STORY.LEVEL0.23.COMPLETE"))
    assertTrue(text.contains("STORY.LEVEL0.41.ENTRY"))
    assertTrue(text.contains("KNOW.STORY.LEVEL0.23"))
    assertTrue(text.contains("THREAD.MAIN.LEVEL0.23"))
    assertTrue(text.contains("recording structural state mismatches without assigning an unobserved cause"))
    assertTrue(text.contains("preserving uncertainty about structural changes and their cause"))
    assertFalse(text.contains("STORY.LEVEL0.23.COMPLETE\",nextBeat:\"STORY.LEVEL1"))
  }
}

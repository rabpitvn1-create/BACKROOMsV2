package com.rabpit.backroom.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainCampaignLevel001Test {
  @Test fun level001HistoryRemainsMaterializedInLockedOrder() {
    val html = materializedCampaignHtml()
    assertTrue(html.contains("LEVEL 0.01 / THE EXIT ? — FALSE PROMISE"))
    assertTrue(html.contains("STORY.LEVEL0.01.COMPLETE"))
    assertTrue(html.contains("STORY.LEVEL0.1.ENTRY"))
    assertTrue(html.contains("KNOW.STORY.LEVEL0.01"))
    assertTrue(html.contains("THREAD.MAIN.LEVEL0.01"))
    assertTrue(html.contains("treating EXIT signage, route loops, silence, and an unknown chalk mark as observations"))
  }

  @Test fun level001TreatsExitCuesAsEvidenceToTestNotTruth() {
    val html = materializedCampaignHtml()
    assertTrue(html.contains("Biển không phải bằng chứng."))
    assertTrue(html.contains("Một nét phấn không phải bằng chứng rằng một trong hai người đã ở đây."))
    assertTrue(html.contains("treating EXIT signage, route loops, silence, and an unknown chalk mark as observations"))
    assertTrue(html.contains("relationship:\"earned_tactical_trust\",romance:\"none\""))
    assertFalse(html.contains("Async đã tạo ra Backrooms"))
    assertFalse(html.contains("currentBeat:\"STORY.LEVEL0.1.ENTRY\""))
  }
}

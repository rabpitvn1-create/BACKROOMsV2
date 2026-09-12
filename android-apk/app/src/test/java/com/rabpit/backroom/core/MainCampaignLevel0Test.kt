package com.rabpit.backroom.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainCampaignLevel0Test {
  @Test fun latestCampaignBuildRetainsLevel0FirstContactBeforeLaterJoin() {
    val html = materializedCampaignHtml()

    assertTrue(html.contains("LEVEL 0 / THE LOBBY — FIRST CONTACT"))
    assertTrue(html.contains("STORY.LEVEL0.ARRIVAL"))
    assertTrue(html.contains("STORY.LEVEL0.FIRST_CONTACT_COMPLETE"))
    assertTrue(html.contains("campaignLevel0Signature"))
    assertTrue(html.contains("Kai met Lucia at Level 0; both confirmed only a limited tactical cooperation and Lucia has not yet joined the party."))
    assertTrue(html.contains("relationship advanced from stranger contact to earned tactical trust only", ignoreCase = true))
    assertFalse(html.contains("relationship:\"romantic\""))
  }

  @Test fun level0FirstContactPreservesKnowledgeBoundaries() {
    val html = materializedCampaignHtml()

    assertTrue(html.contains("Không ai nói đó là sinh vật."))
    assertTrue(html.contains("Có bằng chứng về lối ra?"))
    assertTrue(html.contains("“Chưa.”"))
    assertTrue(html.contains("Không lời hứa. Không tin tưởng vô điều kiện."))
    assertTrue(html.contains("without inventing an exit, resident Entity, or Async attribution"))
    assertFalse(html.contains("relationship:\"romantic\""))
    assertFalse(html.contains("Async đã tạo ra Backrooms"))
  }
}

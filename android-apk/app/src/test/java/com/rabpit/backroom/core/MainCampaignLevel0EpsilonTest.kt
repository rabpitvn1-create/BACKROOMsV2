package com.rabpit.backroom.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainCampaignLevel0EpsilonTest {
  @Test fun latestCampaignBuildRetainsEpsilonAsLevelZeroChildBeat() {
    val html = materializedCampaignHtml()

    assertTrue(html.contains("LEVEL ε / INCESSANT HUM-BUZZ — STRUCTURAL DRIFT"))
    assertTrue(html.contains("window.campaignLevel0EpsilonSignature={currentBeat:\"STORY.LEVEL0.EPSILON.COMPLETE\",nextBeat:\"STORY.LEVEL0.01.ENTRY\""))
    assertTrue(html.contains("parentLevel:0,sublevelId:\"SUBLEVEL.00.EPSILON\",difficulty:3"))
    assertTrue(html.contains("STORY.LEVEL0.EPSILON.COMPLETE"))
    assertTrue(html.contains("KNOW.STORY.LEVEL0.EPSILON"))
  }

  @Test fun epsilonHistoryReinforcesTrustWithoutInventingCanonEvidence() {
    val html = materializedCampaignHtml()

    assertTrue(html.contains("Shared navigation procedure was reinforced under structural and auditory pressure"))
    assertTrue(html.contains("relationship remains earned tactical trust with no romance"))
    assertTrue(html.contains("without inventing an exit, resident Entity, or Async attribution"))
    assertTrue(html.contains("Không có lý do để biến sự thay đổi âm thanh thành một Entity."))
    assertTrue(html.contains("knowledgeBoundary:\"observed-structure-only\""))

    assertFalse(html.contains("relationship:\"romantic\""))
    assertFalse(html.contains("Async đã tạo ra Backrooms"))
  }
}

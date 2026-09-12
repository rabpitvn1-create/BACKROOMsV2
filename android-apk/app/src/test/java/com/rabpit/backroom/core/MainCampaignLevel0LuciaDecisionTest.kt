package com.rabpit.backroom.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainCampaignLevel0LuciaDecisionTest {
  @Test fun latestCampaignBuildRetainsMutualLuciaPartyDecision() {
    val html = materializedCampaignHtml()

    assertTrue(html.contains("LEVEL 0 / THE LOBBY — DECISION TO MOVE TOGETHER"))
    assertTrue(html.contains("window.campaignLevel0LuciaDecisionSignature={currentBeat:\"STORY.LEVEL0.LUCIA_DECISION_COMPLETE\",nextBeat:\"STORY.LEVEL0.EPSILON.ENTRY\""))
    assertTrue(html.contains("party:[{id:\"lucia\",name:\"Lucia\"}]"))
    assertTrue(html.contains("relationship:\"earned_tactical_trust\",romance:\"none\""))
    assertTrue(html.contains("mutually chose to travel together under explicit tactical boundaries"))
    assertTrue(html.contains("playerAgency:\"mutual-party-decision\""))
  }

  @Test fun luciaDecisionHistoryDoesNotInventEvidenceOrRomance() {
    val html = materializedCampaignHtml()

    assertTrue(html.contains("sublevelId:\"\",playerAgency:\"mutual-party-decision\""))
    assertTrue(html.contains("THREAD.ASYNC.EVIDENCE"))
    assertTrue(html.contains("No local Level 0 observation has yet been verified as Async evidence."))
    assertTrue(html.contains("no romantic state is established"))
    assertFalse(html.contains("relationship:\"romantic\""))
    assertFalse(html.contains("Async đã tạo ra Backrooms"))
  }
}

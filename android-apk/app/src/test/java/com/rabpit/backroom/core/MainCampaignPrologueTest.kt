package com.rabpit.backroom.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainCampaignPrologueTest {
  @Test fun latestCampaignBuildRetainsCurrentSruAsyncSpatialGatePrologue() {
    val html = materializedCampaignHtml()

    assertTrue(html.contains("SRU / ASYNC / SPATIAL GATE"))
    assertTrue(html.contains("Năm 2299."))
    assertTrue(html.contains("SRU_ASYNC_INVESTIGATION"))
    assertTrue(html.contains("entry:\"voluntary_spatial_gate\""))
    assertTrue(html.contains("origin:\"UNKNOWN\""))
    assertTrue(html.contains("STORY.PROLOGUE.ENTRY_COMPLETE"))
    assertTrue(html.contains("level:{number:0,name:\"The Lobby\"}"))
    assertTrue(html.contains("sru:\"OFFLINE\""))
    assertTrue(html.contains("frontrooms:\"OFFLINE\""))
    assertTrue(html.contains("THREAD.ASYNC.EVIDENCE"))
    assertTrue(html.contains("campaignPrologueSignature"))

    assertFalse(html.contains("Bữa tối bắt đầu như bao lần khác"))
    assertFalse(html.contains("Nhà hàng nằm trên một tầng cao"))
    assertFalse(html.contains("communication:{blackBlood:"))
  }

  @Test fun prologueHistoryDoesNotInventLocalEvidence() {
    val html = materializedCampaignHtml()

    assertTrue(html.contains("No local Level 0 observation has yet been verified as Async evidence."))
    assertTrue(html.contains("Iris remains separated; her location is unknown to Kai."))
    assertTrue(html.contains("Syvial remains separated; her location is unknown to Kai."))
    assertTrue(html.contains("Kai, Iris and Syvial voluntarily crossed the SRU-monitored Async-linked spatial gate and were separated"))
    assertFalse(html.contains("Async đã tạo ra Backrooms"))
  }
}

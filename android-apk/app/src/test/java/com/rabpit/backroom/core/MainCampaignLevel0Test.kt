package com.rabpit.backroom.core

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainCampaignLevel0Test {
  private fun indexHtml(): String {
    val relative = "src/main/assets/index.html"
    val roots = generateSequence(File(".").canonicalFile) { it.parentFile }.take(6).toList()
    val candidates = roots.flatMap { root ->
      listOf(File(root, relative), File(root, "app/$relative"), File(root, "android-apk/app/$relative"))
    }.distinctBy { it.absolutePath }
    return candidates.firstOrNull { it.isFile }?.readText(Charsets.UTF_8)
      ?: error("index.html not found; searched: " + candidates.joinToString { it.absolutePath })
  }

  @Test fun level0ArrivalCompletesBeforeLuciaJoinDecision() {
    val html = indexHtml()

    assertTrue(html.contains("LEVEL 0 / THE LOBBY — FIRST CONTACT"))
    assertTrue(html.contains("STORY.LEVEL0.ARRIVAL"))
    assertTrue(html.contains("STORY.LEVEL0.FIRST_CONTACT_COMPLETE"))
    assertTrue(html.contains("nextBeat:\"STORY.LEVEL0.LUCIA_DECISION\""))
    assertTrue(html.contains("luciaEncounter:{status:\"met\",level:0,sublevelId:\"\""))
    assertTrue(html.contains("partyEligible:true,joinPending:true"))
    assertTrue(html.contains("relationship:\"initial_tactical_trust\""))
    assertTrue(html.contains("Lucia chưa tự động gia nhập Party"))
  }

  @Test fun level0BeatPreservesKnowledgeAndWorldBoundaries() {
    val html = indexHtml()

    assertTrue(html.contains("Chưa có Entity cư trú, lối thoát hay dấu vết Async nào được xác nhận"))
    assertTrue(html.contains("Không ai nói đó là sinh vật."))
    assertTrue(html.contains("Có bằng chứng về lối ra?"))
    assertTrue(html.contains("“Chưa.”"))
    assertTrue(html.contains("Không lời hứa. Không tin tưởng vô điều kiện."))
    assertTrue(html.contains("campaignLevel0Signature"))

    // Lucia's encounter is fixed, but the story beat must not manufacture a Party join.
    assertFalse(html.contains("luciaEncounter:{status:\"joined\""))
    assertFalse(html.contains("relationship:\"romantic\""))
    assertFalse(html.contains("Async đã tạo ra Backrooms"))
  }
}

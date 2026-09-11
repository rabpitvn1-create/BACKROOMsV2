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

  @Test fun latestCampaignBuildRetainsLevel0FirstContactBeforeLaterJoin() {
    val html = indexHtml()

    assertTrue(html.contains("LEVEL 0 / THE LOBBY — FIRST CONTACT"))
    assertTrue(html.contains("STORY.LEVEL0.ARRIVAL"))
    assertTrue(html.contains("STORY.LEVEL0.FIRST_CONTACT_COMPLETE"))
    assertTrue(html.contains("campaignLevel0Signature"))
    assertTrue(html.contains("Kai met Lucia at Level 0; both confirmed only a limited tactical cooperation and Lucia has not yet joined the party."))
    assertTrue(html.contains("relationship advanced from stranger contact to earned tactical trust only", ignoreCase = true))

    // The active initial state may be a later authored beat; this test locks the
    // historical first-contact record rather than requiring Lucia to remain unjoined.
    assertFalse(html.contains("relationship:\"romantic\""))
  }

  @Test fun level0FirstContactPreservesKnowledgeBoundaries() {
    val html = indexHtml()

    assertTrue(html.contains("Không ai nói đó là sinh vật."))
    assertTrue(html.contains("Có bằng chứng về lối ra?"))
    assertTrue(html.contains("“Chưa.”"))
    assertTrue(html.contains("Không lời hứa. Không tin tưởng vô điều kiện."))
    assertTrue(html.contains("without inventing an exit, resident Entity, or Async attribution"))
    assertFalse(html.contains("relationship:\"romantic\""))
    assertFalse(html.contains("Async đã tạo ra Backrooms"))
  }
}

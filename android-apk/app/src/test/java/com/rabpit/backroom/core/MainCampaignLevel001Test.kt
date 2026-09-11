package com.rabpit.backroom.core

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainCampaignLevel001Test {
  private fun indexHtml(): String {
    val relative = "src/main/assets/index.html"
    val roots = generateSequence(File(".").canonicalFile) { it.parentFile }.take(6).toList()
    val candidates = roots.flatMap { root ->
      listOf(File(root, relative), File(root, "app/$relative"), File(root, "android-apk/app/$relative"))
    }.distinctBy { it.absolutePath }
    return candidates.firstOrNull { it.isFile }?.readText(Charsets.UTF_8)
      ?: error("index.html not found; searched: " + candidates.joinToString { it.absolutePath })
  }

  @Test fun level001UsesChildStateAndAdvancesInLockedOrder() {
    val html = indexHtml()
    assertTrue(html.contains("LEVEL 0.01 / THE EXIT ? — FALSE PROMISE"))
    assertTrue(html.contains("currentBeat:\"STORY.LEVEL0.01.COMPLETE\""))
    assertTrue(html.contains("nextBeat:\"STORY.LEVEL0.1.ENTRY\""))
    assertTrue(html.contains("exploration:{sublevelId:\"SUBLEVEL.00.01\""))
    assertTrue(html.contains("difficulty:{rating:2,source:\"WIKI_DIRECT\",wikiClass:\"CLASS 2\"}"))
    assertTrue(html.contains("luciaEncounter:{status:\"joined\",level:0,sublevelId:\"SUBLEVEL.00.01\""))
  }

  @Test fun level001TreatsExitCuesAsEvidenceToTestNotTruth() {
    val html = indexHtml()
    assertTrue(html.contains("Biển không phải bằng chứng."))
    assertTrue(html.contains("Một nét phấn không phải bằng chứng rằng một trong hai người đã ở đây."))
    assertTrue(html.contains("treating EXIT signage, route loops, silence, and an unknown chalk mark as observations"))
    assertTrue(html.contains("relationship:\"earned_tactical_trust\",romance:\"none\""))
    assertFalse(html.contains("Async đã tạo ra Backrooms"))
    assertFalse(html.contains("currentBeat:\"STORY.LEVEL0.1.ENTRY\""))
  }
}

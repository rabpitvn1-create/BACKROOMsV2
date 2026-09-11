package com.rabpit.backroom.core

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainCampaignLevel0LuciaDecisionTest {
  private fun indexHtml(): String {
    val relative = "src/main/assets/index.html"
    val roots = generateSequence(File(".").canonicalFile) { it.parentFile }.take(6).toList()
    val candidates = roots.flatMap { root ->
      listOf(File(root, relative), File(root, "app/$relative"), File(root, "android-apk/app/$relative"))
    }.distinctBy { it.absolutePath }
    return candidates.firstOrNull { it.isFile }?.readText(Charsets.UTF_8)
      ?: error("index.html not found; searched: " + candidates.joinToString { it.absolutePath })
  }

  @Test fun luciaJoinsOnlyAfterMutualTacticalDecision() {
    val html = indexHtml()

    assertTrue(html.contains("LEVEL 0 / THE LOBBY — DECISION TO MOVE TOGETHER"))
    assertTrue(html.contains("currentBeat:\"STORY.LEVEL0.LUCIA_DECISION_COMPLETE\""))
    assertTrue(html.contains("nextBeat:\"STORY.LEVEL0.EPSILON.ENTRY\""))
    assertTrue(html.contains("party:[{id:\"lucia\",name:\"Lucia\"}]"))
    assertTrue(html.contains("status:\"joined\",level:0,sublevelId:\"\""))
    assertTrue(html.contains("relationship:\"earned_tactical_trust\",romance:\"none\""))
    assertTrue(html.contains("mutually chose to travel together under explicit tactical boundaries"))
  }

  @Test fun luciaDecisionDoesNotSkipIntoEpsilonOrInventEvidence() {
    val html = indexHtml()

    assertTrue(html.contains("exploration:{sublevelId:\"\"}"))
    assertTrue(html.contains("beat hiện tại không tự chuyển sublevel"))
    assertTrue(html.contains("Không có lối thoát, Entity cư trú hay dấu vết Async nào được xác nhận từ beat này"))
    assertTrue(html.contains("no romantic state is established"))

    // The next module owns the Level epsilon transition; this beat only makes it eligible.
    assertFalse(html.contains("currentBeat:\"STORY.LEVEL0.EPSILON.ENTRY\""))
    assertFalse(html.contains("relationship:\"romantic\""))
    assertFalse(html.contains("Async đã tạo ra Backrooms"))
  }
}

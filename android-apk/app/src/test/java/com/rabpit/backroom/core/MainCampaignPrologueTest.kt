package com.rabpit.backroom.core

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainCampaignPrologueTest {
  private fun indexHtml(): String {
    val relative = "src/main/assets/index.html"
    val roots = generateSequence(File(".").canonicalFile) { it.parentFile }
      .take(6)
      .toList()
    val candidates = roots.flatMap { root ->
      listOf(
        File(root, relative),
        File(root, "app/$relative"),
        File(root, "android-apk/app/$relative")
      )
    }.distinctBy { it.absolutePath }
    val file = candidates.firstOrNull { it.isFile }
      ?: error("index.html not found; searched: " + candidates.joinToString { it.absolutePath })
    return file.readText(Charsets.UTF_8)
  }

  @Test fun newGameUsesCurrentSruAsyncSpatialGatePrologue() {
    val html = indexHtml()

    assertTrue(html.contains("SRU / ASYNC / SPATIAL GATE"))
    assertTrue(html.contains("Năm 2299."))
    assertTrue(html.contains("SRU_ASYNC_INVESTIGATION"))
    assertTrue(html.contains("entry:\"voluntary_spatial_gate\""))
    assertTrue(html.contains("origin:\"UNKNOWN\""))
    assertTrue(html.contains("STORY.PROLOGUE.ENTRY_COMPLETE"))
    assertTrue(html.contains("nextBeat:\"STORY.LEVEL0.ARRIVAL\""))
    assertTrue(html.contains("level:{number:0,name:\"The Lobby\"}"))
    assertTrue(html.contains("exploration:{sublevelId:\"\"}"))
    assertTrue(html.contains("sru:\"OFFLINE\""))
    assertTrue(html.contains("frontrooms:\"OFFLINE\""))
    assertTrue(html.contains("THREAD.ASYNC.EVIDENCE"))

    // Regression locks for the retired accidental-entry opening.
    assertFalse(html.contains("Bữa tối bắt đầu như bao lần khác"))
    assertFalse(html.contains("Nhà hàng nằm trên một tầng cao"))
    assertFalse(html.contains("communication:{blackBlood:"))
  }

  @Test fun prologueLeavesGameplayAtLevelZeroWithoutInventingLocalEvidence() {
    val html = indexHtml()

    assertTrue(html.contains("Kai đang ở một mình tại Level 0"))
    assertTrue(html.contains("Không có lối thoát, Entity hay dấu vết Async nào được xác nhận chỉ từ phần mở đầu"))
    assertTrue(html.contains("No local Level 0 observation has yet been verified as Async evidence."))
    assertTrue(html.contains("Iris remains separated; her location is unknown to Kai."))
    assertTrue(html.contains("Syvial remains separated; her location is unknown to Kai."))
  }
}

package com.rabpit.backroom.core

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class MainCampaignLevel011Test {
  private fun html(): String {
    val p = listOf(File("src/main/assets/index.html"), File("app/src/main/assets/index.html"), File("android-apk/app/src/main/assets/index.html")).firstOrNull { it.isFile }
    return p?.readText(Charsets.UTF_8) ?: error("index.html not found")
  }

  @Test fun level011StateIsLocked() {
    val text = html()
    assertTrue(text.contains("LEVEL 0.11 / WATER DAMAGE — MEASURE THE CURRENT"))
    assertTrue(text.contains("currentBeat:\"STORY.LEVEL0.11.COMPLETE\""))
    assertTrue(text.contains("nextBeat:\"STORY.LEVEL0.22.ENTRY\""))
    assertTrue(text.contains("sublevelId:\"SUBLEVEL.00.11\""))
    assertTrue(text.contains("rating:3,source:\"PROJECT_DESIGNED\",wikiClass:\"UNVERIFIED\""))
    assertTrue(text.contains("relationship:\"earned_tactical_trust\",romance:\"none\""))
    assertTrue(text.contains("STORY.LEVEL0.11.COMPLETE"))
  }
}

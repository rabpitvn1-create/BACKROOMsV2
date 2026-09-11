package com.rabpit.backroom.core

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainCampaignLevel041Test {
  @Test fun level041StateAndKnowledgeBoundaryAreLocked() {
    val file = listOf(
      File("src/main/assets/index.html"),
      File("app/src/main/assets/index.html"),
      File("android-apk/app/src/main/assets/index.html")
    ).firstOrNull { it.isFile } ?: error("index.html not found")
    val text = file.readText(Charsets.UTF_8)

    assertTrue(text.contains("LEVEL 0.41 / DISEASE — DO NOT NAME WHAT YOU HAVE NOT TESTED"))
    assertTrue(text.contains("SUBLEVEL.00.41"))
    assertTrue(text.contains("difficulty:{rating:4,source:\"WIKI_DIRECT\",wikiClass:\"CLASS 4\"}"))
    assertTrue(text.contains("STORY.LEVEL0.41.COMPLETE"))
    assertTrue(text.contains("STORY.LEVEL0.5.ENTRY"))
    assertTrue(text.contains("relationship:\"earned_tactical_trust\",romance:\"none\""))
    assertTrue(text.contains("recording a locally observed Pause-like anomaly without inferring its mechanism"))
    assertTrue(text.contains("transient symptoms and Pause-like observations as hazards to manage rather than proof of an unknown mechanism"))
    assertFalse(text.contains("STORY.LEVEL0.41.COMPLETE\",nextBeat:\"STORY.LEVEL1"))
    assertFalse(text.contains("luciaEncounter:{status:\"joined\",level:1"))
  }
}

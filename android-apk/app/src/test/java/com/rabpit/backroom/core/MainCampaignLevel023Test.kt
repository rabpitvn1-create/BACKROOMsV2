package com.rabpit.backroom.core

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainCampaignLevel023Test {
  @Test fun level023StateAndKnowledgeBoundaryAreLocked() {
    val file = listOf(
      File("src/main/assets/index.html"),
      File("app/src/main/assets/index.html"),
      File("android-apk/app/src/main/assets/index.html")
    ).firstOrNull { it.isFile } ?: error("index.html not found")
    val text = file.readText(Charsets.UTF_8)

    assertTrue(text.contains("LEVEL 0.23 / HALF FINISHED — TRUST THE EDGE, NOT THE PROMISE"))
    assertTrue(text.contains("SUBLEVEL.00.23"))
    assertTrue(text.contains("difficulty:{rating:3,source:\"WIKI_DIRECT\",wikiClass:\"CLASS 3\"}"))
    assertTrue(text.contains("STORY.LEVEL0.23.COMPLETE"))
    assertTrue(text.contains("STORY.LEVEL0.41.ENTRY"))
    assertTrue(text.contains("relationship:\"earned_tactical_trust\",romance:\"none\""))
    assertTrue(text.contains("recording structural state mismatches without assigning an unobserved cause"))
    assertTrue(text.contains("preserving uncertainty about structural changes and their cause"))
    assertFalse(text.contains("STORY.LEVEL0.23.COMPLETE\",nextBeat:\"STORY.LEVEL1"))
  }
}

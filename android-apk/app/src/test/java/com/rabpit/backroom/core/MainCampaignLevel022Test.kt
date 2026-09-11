package com.rabpit.backroom.core

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class MainCampaignLevel022Test {
  @Test fun level022StateIsLocked() {
    val file = listOf(File("src/main/assets/index.html"), File("app/src/main/assets/index.html"), File("android-apk/app/src/main/assets/index.html")).firstOrNull { it.isFile } ?: error("index.html not found")
    val text = file.readText(Charsets.UTF_8)
    assertTrue(text.contains("LEVEL 0.22 / FULLY REMODELED — USEFUL IS NOT SAFE"))
    assertTrue(text.contains("STORY.LEVEL0.22.COMPLETE"))
    assertTrue(text.contains("STORY.LEVEL0.23.ENTRY"))
    assertTrue(text.contains("SUBLEVEL.00.22"))
  }
}

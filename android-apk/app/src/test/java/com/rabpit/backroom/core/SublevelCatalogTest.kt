package com.rabpit.backroom.core

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SublevelCatalogTest {
  private fun catalog(): JSONObject {
    val candidates = listOf(
      File("app/src/main/assets/knowledge/sublevels_0_6_source.json"),
      File("android-apk/app/src/main/assets/knowledge/sublevels_0_6_source.json")
    )
    val file = candidates.firstOrNull { it.isFile }
      ?: error("sublevels_0_6_source.json not found from ${File(".").absolutePath}")
    return JSONObject(file.readText(Charsets.UTF_8))
  }

  @Test fun currentWikiListContainsExactlyTheExpectedLevelZeroToSixSublevels() {
    val root = catalog()
    val records = root.getJSONArray("records")
    assertEquals(33, records.length())

    val counts = IntArray(7)
    val ids = linkedSetOf<String>()
    val designations = linkedSetOf<String>()
    for (index in 0 until records.length()) {
      val record = records.getJSONObject(index)
      val parent = record.getInt("parentLevel")
      assertTrue("parent out of range: $parent", parent in 0..6)
      counts[parent] += 1
      assertTrue(ids.add(record.getString("id")))
      assertTrue(designations.add(record.getString("designation")))
      assertTrue(record.getString("wiki").startsWith("https://backrooms.fandom.com/wiki/"))
      assertTrue(record.getString("summary").isNotBlank())
      assertTrue(record.getString("status").isNotBlank())
    }

    assertEquals(listOf(12, 4, 3, 2, 3, 3, 6), counts.toList())
    assertFalse("Level 5.3 is not on the current Fandom Levels 0-8 list", "Level 5.3" in designations)
    assertTrue("Level ε" in designations)
    assertTrue("Level φ" in designations)
    assertTrue("Level e" in designations)
    assertTrue("Level π" in designations)
    assertTrue("Level τ" in designations)
  }

  @Test fun catalogCannotOverrideIntegerParentLevelOrProjectLevelSixCanon() {
    val root = catalog()
    assertEquals("EXTERNAL_REFERENCE_ONLY", root.getString("authority"))
    val rule = root.getString("projectRule")
    assertTrue(rule.contains("never replace integer parent level.number"))
    assertTrue(rule.contains("Level 6 parent remains the project outdoor dark-tundra baseline"))

    val records = root.getJSONArray("records")
    val levelSix = (0 until records.length())
      .map { records.getJSONObject(it) }
      .filter { it.getInt("parentLevel") == 6 }
    assertEquals(6, levelSix.size)
    assertTrue(levelSix.all { it.getString("id").startsWith("SUBLEVEL.06.") })
  }
}

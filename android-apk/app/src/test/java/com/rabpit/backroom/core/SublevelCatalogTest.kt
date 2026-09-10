package com.rabpit.backroom.core

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SublevelCatalogTest {
  private val allowedDifficultySources = setOf(
    "WIKI_DIRECT",
    "WIKI_NONSTANDARD_MAPPED",
    "PROJECT_DESIGNED"
  )

  private fun catalog(): JSONObject {
    val relative = "src/main/assets/knowledge/sublevels_0_6_source.json"
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
      ?: error(
        "sublevels_0_6_source.json not found; searched: " +
          candidates.joinToString { it.absolutePath }
      )
    return JSONObject(file.readText(Charsets.UTF_8))
  }

  private fun assertDifficulty(owner: String, difficulty: JSONObject) {
    val rating = difficulty.getInt("rating")
    val source = difficulty.getString("source")
    val wikiClass = difficulty.getString("wikiClass")
    val rationale = difficulty.getString("rationale")
    assertTrue("$owner rating out of range: $rating", rating in 1..5)
    assertTrue("$owner difficulty source invalid: $source", source in allowedDifficultySources)
    assertTrue("$owner wikiClass missing", wikiClass.isNotBlank())
    assertTrue("$owner rationale missing", rationale.isNotBlank())
    if (source == "WIKI_DIRECT") {
      assertEquals("$owner direct Wiki class must match rating", "CLASS $rating", wikiClass)
    }
  }

  @Test fun currentWikiListContainsExactlyTheExpectedLevelZeroToSixSublevels() {
    val root = catalog()
    assertEquals(2, root.getInt("schemaVersion"))
    val records = root.getJSONArray("records")
    assertEquals(33, records.length())

    val counts = IntArray(7)
    val ids = linkedSetOf<String>()
    val designations = linkedSetOf<String>()
    for (index in 0 until records.length()) {
      val record = records.getJSONObject(index)
      val parent = record.getInt("parentLevel")
      val id = record.getString("id")
      assertTrue("parent out of range: $parent", parent in 0..6)
      counts[parent] += 1
      assertTrue(ids.add(id))
      assertTrue(designations.add(record.getString("designation")))
      assertTrue(record.getString("wiki").startsWith("https://backrooms.fandom.com/wiki/"))
      assertTrue(record.getString("summary").isNotBlank())
      assertTrue(record.getString("status").isNotBlank())
      assertEquals("$id snapshot must stay empty until artwork is supplied", "", record.getString("snapshot"))
      assertDifficulty(id, record.getJSONObject("difficulty"))
    }

    assertEquals(listOf(12, 4, 3, 2, 3, 3, 6), counts.toList())
    assertFalse("Level 5.3 is not on the current Fandom Levels 0-8 list", "Level 5.3" in designations)
    assertTrue("Level ε" in designations)
    assertTrue("Level φ" in designations)
    assertTrue("Level e" in designations)
    assertTrue("Level π" in designations)
    assertTrue("Level τ" in designations)
  }

  @Test fun parentAndSublevelDifficultyPolicyPreservesProjectAuthorityAndGlobalRoaming() {
    val root = catalog()
    assertEquals("EXTERNAL_REFERENCE_ONLY", root.getString("authority"))
    val rule = root.getString("projectRule")
    assertTrue(rule.contains("never replace integer parent level.number"))
    assertTrue(rule.contains("Level 6 parent remains the project outdoor dark-tundra baseline"))
    assertTrue(rule.contains("including Levels 0, 4 and 6"))

    val policy = root.getJSONObject("difficultyPolicy")
    assertEquals("1-5", policy.getString("scale"))
    assertTrue(policy.getString("rule").contains("PROJECT_DESIGNED"))
    assertTrue(policy.getString("rule").contains("never presented as Wiki canon"))
    val roaming = policy.getString("roamingEntityOverride")
    assertTrue(roaming.contains("every parent Level 0-6"))
    assertTrue(roaming.contains("every listed sublevel"))

    val parentDifficulties = root.getJSONArray("parentDifficulties")
    assertEquals(7, parentDifficulties.length())
    val parents = linkedSetOf<Int>()
    for (index in 0 until parentDifficulties.length()) {
      val record = parentDifficulties.getJSONObject(index)
      val parent = record.getInt("parentLevel")
      assertTrue("duplicate parent difficulty: $parent", parents.add(parent))
      assertTrue(record.getString("wiki").startsWith("https://backrooms.fandom.com/wiki/"))
      assertDifficulty("Level $parent", record.getJSONObject("difficulty"))
    }
    assertEquals((0..6).toSet(), parents)

    val records = root.getJSONArray("records")
    val levelSix = (0 until records.length())
      .map { records.getJSONObject(it) }
      .filter { it.getInt("parentLevel") == 6 }
    assertEquals(6, levelSix.size)
    assertTrue(levelSix.all { it.getString("id").startsWith("SUBLEVEL.06.") })
  }
}

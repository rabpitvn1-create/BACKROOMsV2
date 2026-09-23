package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StoryRepositoryTest {
  private static String readRepoAsset(String relativePath) throws Exception {
    Path[] candidates = new Path[] {
        Paths.get("src/main/assets", relativePath),
        Paths.get("app/src/main/assets", relativePath),
        Paths.get("android-apk/app/src/main/assets", relativePath)
    };
    for (Path path : candidates) {
      if (Files.isRegularFile(path)) {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
      }
    }
    throw new IllegalStateException("Unable to locate story asset: " + relativePath);
  }

  @Test public void finalLevelZeroMetadataReferencesThirtyAuthoredChapters() throws Exception {
    JSONObject root = new JSONObject(readRepoAsset("story/generated/level_0/level0.story.json"));
    assertEquals(1, root.getInt("schemaVersion"));
    assertEquals("level0-final-2026-09-23", root.getString("sourceRevision"));

    JSONArray chapters = root.getJSONArray("chapters");
    assertEquals(30, chapters.length());
    assertEquals("L0_C01", chapters.getJSONObject(0).getString("id"));
    assertEquals("L0_C30", chapters.getJSONObject(29).getString("id"));
    assertEquals("", chapters.getJSONObject(29).getString("nextChapter"));

    for (int i = 1; i <= 30; i++) {
      String source = String.format(java.util.Locale.ROOT,
          "story/source/level_0/LEVEL0_CH%02d.md", i);
      String text = readRepoAsset(source);
      assertTrue("Missing chapter heading for " + source, text.startsWith("# Level 0 — Chương"));
      assertTrue("Chapter source unexpectedly short: " + source, text.length() > 4000);
    }
  }

  @Test public void chapterThirtyEndsAtUnvalidatedBoundaryNotLevelOne() throws Exception {
    JSONObject root = new JSONObject(readRepoAsset("story/generated/level_0/level0.story.json"));
    JSONObject chapter30 = root.getJSONArray("chapters").getJSONObject(29);

    JSONArray events = chapter30.getJSONArray("eventsOnExit");
    boolean boundary = false;
    for (int i = 0; i < events.length(); i++) {
      if ("LEVEL0_ARC_BOUNDARY_REACHED".equals(events.getJSONObject(i).optString("type"))) {
        boundary = true;
      }
    }
    assertTrue(boundary);

    String all = chapter30.toString();
    assertTrue(all.contains("Do not claim a validated Level 0 -> Level 1 transition"));
    assertFalse(all.contains("\"type\":\"LEVEL_TRANSITION\""));
  }

  @Test public void markdownSegmentationPreservesParagraphsAndStripsOnlyHeadingMarker() {
    String markdown = "# Level 0 — Chương 01: Test\n\n"
        + "Đoạn một giữ nguyên.\n\n"
        + "Đoạn hai cũng giữ nguyên.\n\n"
        + "Đoạn ba kết thúc.";
    List<String> segments = StoryRepository.splitMarkdown(markdown, 20, 50);

    assertFalse(segments.isEmpty());
    String joined = String.join("\n\n", segments);
    assertTrue(joined.startsWith("Level 0 — Chương 01: Test"));
    assertTrue(joined.contains("Đoạn một giữ nguyên."));
    assertTrue(joined.contains("Đoạn hai cũng giữ nguyên."));
    assertTrue(joined.contains("Đoạn ba kết thúc."));
    assertFalse(joined.contains("# Level 0"));
  }

  @Test public void cutawayChaptersAreExplicitlyMarked() throws Exception {
    JSONObject root = new JSONObject(readRepoAsset("story/generated/level_0/level0.story.json"));
    JSONArray chapters = root.getJSONArray("chapters");
    int cutaways = 0;
    for (int i = 0; i < chapters.length(); i++) {
      JSONObject chapter = chapters.getJSONObject(i);
      if ("cutaway".equals(chapter.getString("visibility"))) {
        cutaways++;
        assertEquals("luc_tram", chapter.getString("thread"));
      }
    }
    assertEquals(4, cutaways);
  }
}

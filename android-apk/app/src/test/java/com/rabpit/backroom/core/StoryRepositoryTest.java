package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

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

  @Test public void finalLevelZeroStoryRunsEndToEndWithAuthoredMilestones() throws Exception {
    String metadata = readRepoAsset("story/generated/level_0/level0.story.json");
    Map<String, String> sources = new LinkedHashMap<>();
    for (int i = 1; i <= 30; i++) {
      String path = String.format(java.util.Locale.ROOT,
          "story/source/level_0/LEVEL0_CH%02d.md", i);
      sources.put(path, readRepoAsset(path));
    }

    StoryRepository repository = StoryRepository.fromText(metadata, sources);
    StoryCore core = StoryCore.withRepository(repository);
    CharacterEncounterCore characterCore = new CharacterEncounterCore(bound -> bound - 1);
    JSONObject state = new JSONObject()
        .put("currentLevel", 0)
        .put("currentLevelKey", "0")
        .put("turn", 1)
        .put("party", new JSONArray())
        .put("flags", new JSONObject());

    Set<String> cutawayChapters = new LinkedHashSet<>();
    String firstParallelChapter = "";
    String firstReunionChapter = "";
    String firstAccompanyChapter = "";
    String firstPartyChapter = "";
    String firstNamPresentChapter = "";
    String firstNamMissingChapter = "";
    int delivered = 0;

    while (core.hasPendingAuthoredStory(state)) {
      StoryCore.AuthoredTurn turn =
          core.advanceAndRender(state, StoryCore.ADVANCE_ACTION_VI, characterCore);
      if (turn == null) break;
      delivered++;
      if ("cutaway".equals(turn.visibility)) cutawayChapters.add(turn.chapterId);

      String lucStatus = StoryCore.characterStatus(state, "luc_tram");
      if (firstParallelChapter.isEmpty() && StoryCore.STATUS_PARALLEL.equals(lucStatus)) {
        firstParallelChapter = turn.chapterId;
      }
      if (firstReunionChapter.isEmpty() && StoryCore.STATUS_REUNITED.equals(lucStatus)) {
        firstReunionChapter = turn.chapterId;
      }
      if (firstAccompanyChapter.isEmpty() && StoryCore.STATUS_ACCOMPANYING.equals(lucStatus)) {
        firstAccompanyChapter = turn.chapterId;
      }
      if (firstPartyChapter.isEmpty() && StoryCore.STATUS_PARTY_MEMBER.equals(lucStatus)) {
        firstPartyChapter = turn.chapterId;
      }

      JSONObject story = state.getJSONObject(StoryCore.ROOT_KEY);
      JSONObject characters = story.getJSONObject("characters");
      JSONObject nam = characters.optJSONObject("nam");
      if (nam != null) {
        String presence = nam.optString("presence", StoryCore.PRESENCE_UNKNOWN);
        if (firstNamPresentChapter.isEmpty() && StoryCore.PRESENCE_PRESENT.equals(presence)) {
          firstNamPresentChapter = turn.chapterId;
        }
        if (firstNamMissingChapter.isEmpty() && StoryCore.PRESENCE_MISSING.equals(presence)) {
          firstNamMissingChapter = turn.chapterId;
        }
      }

      state.put("turn", state.optInt("turn", 1) + 1);
      assertTrue("Story loop exceeded safety bound", delivered < 1000);
    }

    assertTrue("No authored segments were delivered", delivered > 30);
    assertEquals(new LinkedHashSet<>(java.util.Arrays.asList(
        "L0_C03", "L0_C05", "L0_C07", "L0_C09")), cutawayChapters);
    assertEquals("L0_C03", firstParallelChapter);
    assertEquals("L0_C10", firstReunionChapter);
    assertEquals("L0_C11", firstAccompanyChapter);
    assertEquals("L0_C12", firstPartyChapter);
    assertEquals("L0_C05", firstNamPresentChapter);
    assertEquals("L0_C19", firstNamMissingChapter);

    JSONObject story = state.getJSONObject(StoryCore.ROOT_KEY);
    assertTrue(story.getBoolean("arcComplete"));
    assertTrue(story.getJSONObject("flags").getBoolean("level0_arc_boundary_reached"));
    assertEquals(StoryCore.STATUS_PARTY_MEMBER, StoryCore.characterStatus(state, "luc_tram"));
    assertEquals(1, state.getJSONArray("party").length());
    assertEquals("luc_tram", state.getJSONArray("party").getJSONObject(0).getString("id"));

    JSONObject nam = story.getJSONObject("characters").getJSONObject("nam");
    assertEquals(StoryCore.PRESENCE_MISSING, nam.getString("presence"));

    assertEquals(0, state.getInt("currentLevel"));
    assertEquals("0", state.getString("currentLevelKey"));
    assertFalse(core.hasPendingAuthoredStory(state));
  }


  @Test public void compiledInteractionRequiresFreshSourceDigest() throws Exception {
    String sourcePath = "story/source/level_0/LEVEL0_CH01.md";
    String source = "# Level 0 — Chương 01: Test\n\nĐoạn một.";
    String metadata = "{"
        + "\"schemaVersion\":1,"
        + "\"sourceRevision\":\"digest-test\","
        + "\"segmentTargetChars\":1900,"
        + "\"segmentMaxChars\":2400,"
        + "\"startChapter\":\"L0_C01\","
        + "\"chapters\":[{"
        + "\"id\":\"L0_C01\","
        + "\"title\":\"Test\","
        + "\"source\":\"" + sourcePath + "\","
        + "\"thread\":\"cao_minh\","
        + "\"visibility\":\"player\","
        + "\"nextChapter\":\"\","
        + "\"eventsOnEnter\":[],"
        + "\"eventsOnExit\":[],"
        + "\"requiredFacts\":[],"
        + "\"forbiddenClaims\":[]"
        + "}]}";

    String digest = StoryRepository.sourceDigest(source);
    String interactions = "{"
        + "\"schemaVersion\":1,"
        + "\"sourceRevision\":\"digest-test\","
        + "\"chapters\":{"
        + "\"L0_C01\":{"
        + "\"sourceDigest\":\"" + digest + "\","
        + "\"segments\":{"
        + "\"L0_C01_P001\":{"
        + "\"mode\":\"INTERACTIVE\","
        + "\"choices\":["
        + "{\"text\":\"A\",\"action\":\"Quan sát A\"},"
        + "{\"text\":\"B\",\"action\":\"Quan sát B\"},"
        + "{\"text\":\"C\",\"action\":\"Quan sát C\"}"
        + "],"
        + "\"interactionGuard\":\"Không thay đổi sự kiện kế tiếp.\""
        + "}}}}}";

    Map<String, String> sources = new LinkedHashMap<>();
    sources.put(sourcePath, source);
    StoryRepository fresh = StoryRepository.fromText(metadata, sources, interactions);
    StoryRepository.Segment freshSegment = fresh.segment("L0_C01", 0);
    assertEquals(StoryRepository.MODE_INTERACTIVE, freshSegment.mode);
    assertEquals(3, freshSegment.choices.length());

    sources.put(sourcePath, source + "\n\nĐã sửa bản thảo.");
    StoryRepository stale = StoryRepository.fromText(metadata, sources, interactions);
    StoryRepository.Segment staleSegment = stale.segment("L0_C01", 0);
    assertEquals(StoryRepository.MODE_LINEAR, staleSegment.mode);
    assertEquals(0, staleSegment.choices.length());
  }

}

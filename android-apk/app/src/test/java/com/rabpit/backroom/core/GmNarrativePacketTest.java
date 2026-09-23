package com.rabpit.backroom.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.json.JSONObject;
import org.junit.Test;

public class GmNarrativePacketTest {
  private static String readRepoAsset(String relativePath) throws Exception {
    Path[] candidates = new Path[] {
        Paths.get("src/main/assets", relativePath),
        Paths.get("app/src/main/assets", relativePath),
        Paths.get("android-apk/app/src/main/assets", relativePath)
    };
    for (Path path : candidates) {
      if (Files.isRegularFile(path)) return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
    throw new IllegalStateException("Unable to locate test asset: " + relativePath);
  }

  @Test public void projectionDropsHeavyCanonAndHiddenHistory() throws Exception {
    JSONObject state = new JSONObject()
        .put("currentLevel", 0)
        .put("currentLevelKey", "0")
        .put("characterCanon", "FULL_CANON_MARKER".repeat(200))
        .put("levelRoute", new JSONObject().put("streak", 9))
        .put(StoryCore.ROOT_KEY, new JSONObject().put("hiddenReveal", "DO_NOT_LEAK"))
        .put("log", new org.json.JSONArray().put(new JSONObject().put("text", "secret history")));

    JSONObject projected = GmNarrativePacket.projectState(state);

    assertFalse(projected.has("characterCanon"));
    assertFalse(projected.has("levelRoute"));
    assertFalse(projected.has(StoryCore.ROOT_KEY));
    assertFalse(projected.has("log"));
    assertTrue(state.has("characterCanon"));
  }

  @Test public void ordinaryLevelZeroPacketUsesRealKnowledgeAndStaysBudgeted() throws Exception {
    LevelCore core = LevelCore.withKnowledge(
        readRepoAsset("knowledge/level_knowledge.json"), bound -> 0);
    JSONObject state = new JSONObject()
        .put("currentLevel", 0)
        .put("currentLevelKey", "0")
        .put("turn", 2)
        .put("location", "Hành lang vàng nhạt")
        .put("player", new JSONObject().put("name", "Cao Minh"))
        .put("flags", new JSONObject())
        .put("characterCanon", "FULL_CANON_MARKER".repeat(250));

    String levelContext = core.promptContext(state, "Cao Minh đi tiếp theo hành lang");
    String packet = GmNarrativePacket.build(
        levelContext,
        "ENTITY CORE: no active Entity encounter this turn.",
        "ITEM CORE: không có loot rời trong scene.",
        "CHARACTER ENCOUNTER CORE: Joined: none. Pending intro: none.",
        "GM: Cao Minh vừa đi qua một đoạn hành lang.\nPLAYER: Cao Minh tiếp tục tiến lên.",
        state,
        "Cao Minh đi tiếp theo hành lang",
        readRepoAsset("knowledge/gm_style_examples.json"));

    System.out.println("GM_PACKET_METRICS level0ExploreChars=" + levelContext.length()
        + " packetChars=" + packet.length());
    assertTrue("Ordinary narrative packet should stay under 13k chars, was: " + packet.length(),
        packet.length() < 13000);
    assertFalse(packet.contains("FULL_CANON_MARKER"));
    assertTrue(packet.contains("\"transitionTarget\""));
    assertTrue(packet.contains("sceneLabel chỉ là nhãn mô tả"));
  }

  @Test public void packetIncludesStoryCoreContextWithoutRequiringCompiledStory() throws Exception {
    JSONObject state = new JSONObject()
        .put("currentLevel", 0)
        .put("currentLevelKey", "0")
        .put("turn", 1)
        .put("party", new org.json.JSONArray());

    StoryCore storyCore = new StoryCore();
    storyCore.normalizeState(state);
    String storyContext = storyCore.promptContext(state);

    String packet = GmNarrativePacket.build(
        "LEVEL",
        "ENTITY",
        "ITEM",
        "CHARACTER",
        storyContext,
        "(chưa có lượt trước)",
        state,
        "Cao Minh quan sát",
        "");

    assertTrue(packet.contains("STORY CORE:"));
    assertTrue(packet.contains("No authored manuscript chapter is currently bound"));
    assertTrue(packet.contains("authored Story state"));
  }

}

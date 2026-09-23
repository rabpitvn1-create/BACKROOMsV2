package com.rabpit.backroom.core;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Loads generated story metadata and exact authored Markdown from APK assets. */
final class StoryRepository {
  static final String LEVEL_ZERO_METADATA_ASSET = "story/generated/level_0/level0.story.json";

  static final class Chapter {
    final String id;
    final String title;
    final String source;
    final String thread;
    final String visibility;
    final String nextChapter;
    final JSONArray eventsOnEnter;
    final JSONArray eventsOnExit;
    final JSONArray requiredFacts;
    final JSONArray forbiddenClaims;

    Chapter(JSONObject json) {
      id = json.optString("id", "").trim();
      title = json.optString("title", "").trim();
      source = json.optString("source", "").trim();
      thread = json.optString("thread", "cao_minh").trim();
      visibility = json.optString("visibility", "player").trim();
      nextChapter = json.optString("nextChapter", "").trim();
      eventsOnEnter = copyArray(json.optJSONArray("eventsOnEnter"));
      eventsOnExit = copyArray(json.optJSONArray("eventsOnExit"));
      requiredFacts = copyArray(json.optJSONArray("requiredFacts"));
      forbiddenClaims = copyArray(json.optJSONArray("forbiddenClaims"));
    }
  }

  static final class Segment {
    final String id;
    final String chapterId;
    final int index;
    final int count;
    final String text;

    Segment(String id, String chapterId, int index, int count, String text) {
      this.id = id;
      this.chapterId = chapterId;
      this.index = index;
      this.count = count;
      this.text = text;
    }
  }

  interface AssetReader {
    String read(String path) throws Exception;
  }

  private final AssetReader reader;
  private final Map<String, Chapter> chapters = new LinkedHashMap<>();
  private final Map<String, List<Segment>> segmentCache = new LinkedHashMap<>();
  private String sourceRevision = "";
  private String startChapter = "";
  private int targetChars = 1900;
  private int maxChars = 2400;

  StoryRepository(Context context) {
    this(context == null ? null : path -> readAsset(context, path));
  }

  StoryRepository(AssetReader reader) {
    this.reader = reader;
    if (reader != null) {
      try {
        loadMetadata(reader.read(LEVEL_ZERO_METADATA_ASSET));
      } catch (Exception ignored) {
        chapters.clear();
      }
    }
  }

  static StoryRepository fromText(String metadataJson, Map<String, String> sources) {
    StoryRepository repository = new StoryRepository(path -> {
      String value = sources.get(path);
      if (value == null) throw new IllegalArgumentException("Missing test story asset: " + path);
      return value;
    });
    repository.loadMetadata(metadataJson);
    return repository;
  }

  boolean available() {
    return !chapters.isEmpty() && !startChapter.isEmpty();
  }

  String sourceRevision() {
    return sourceRevision;
  }

  String startChapter() {
    return startChapter;
  }

  Chapter chapter(String id) {
    return chapters.get(id == null ? "" : id.trim());
  }

  Segment segment(String chapterId, int index) {
    List<Segment> segments = segments(chapterId);
    if (index < 0 || index >= segments.size()) return null;
    return segments.get(index);
  }

  int segmentCount(String chapterId) {
    return segments(chapterId).size();
  }

  private List<Segment> segments(String chapterId) {
    String key = chapterId == null ? "" : chapterId.trim();
    List<Segment> cached = segmentCache.get(key);
    if (cached != null) return cached;

    List<Segment> result = new ArrayList<>();
    Chapter chapter = chapters.get(key);
    if (chapter == null || reader == null || chapter.source.isEmpty()) {
      segmentCache.put(key, result);
      return result;
    }

    try {
      String manuscript = reader.read(chapter.source);
      List<String> blocks = splitMarkdown(manuscript, targetChars, maxChars);
      for (int i = 0; i < blocks.size(); i++) {
        String id = chapter.id + "_P" + String.format(java.util.Locale.ROOT, "%03d", i + 1);
        result.add(new Segment(id, chapter.id, i, blocks.size(), blocks.get(i)));
      }
    } catch (Exception ignored) {
      result.clear();
    }

    segmentCache.put(key, result);
    return result;
  }

  void loadMetadata(String json) {
    chapters.clear();
    segmentCache.clear();
    try {
      JSONObject root = new JSONObject(json == null ? "{}" : json);
      if (root.optInt("schemaVersion", 0) != 1) return;
      sourceRevision = root.optString("sourceRevision", "").trim();
      startChapter = root.optString("startChapter", "").trim();
      targetChars = Math.max(800, root.optInt("segmentTargetChars", 1900));
      maxChars = Math.max(targetChars, root.optInt("segmentMaxChars", 2400));

      JSONArray list = root.optJSONArray("chapters");
      if (list == null) return;
      for (int i = 0; i < list.length(); i++) {
        JSONObject raw = list.optJSONObject(i);
        if (raw == null) continue;
        Chapter chapter = new Chapter(raw);
        if (chapter.id.isEmpty() || chapter.source.isEmpty() || chapters.containsKey(chapter.id)) continue;
        chapters.put(chapter.id, chapter);
      }
      if (!chapters.containsKey(startChapter)) {
        chapters.clear();
        startChapter = "";
      }
    } catch (Exception ignored) {
      chapters.clear();
      startChapter = "";
    }
  }

  static List<String> splitMarkdown(String markdown, int targetChars, int maxChars) {
    String source = markdown == null ? "" : markdown.replace("\r\n", "\n").replace('\r', '\n').trim();
    List<String> paragraphs = new ArrayList<>();
    if (source.isEmpty()) return paragraphs;

    String[] raw = source.split("\\n\\s*\\n+");
    for (String value : raw) {
      String paragraph = value == null ? "" : value.trim();
      if (paragraph.isEmpty()) continue;
      if (paragraph.startsWith("# ")) paragraph = paragraph.substring(2).trim();
      if (!paragraph.isEmpty()) paragraphs.add(paragraph);
    }

    List<String> output = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    for (String paragraph : paragraphs) {
      int addedLength = paragraph.length() + (current.length() == 0 ? 0 : 2);
      boolean overTarget = current.length() >= targetChars;
      boolean wouldExceedMax = current.length() > 0 && current.length() + addedLength > maxChars;
      if (current.length() > 0 && (overTarget || wouldExceedMax)) {
        output.add(current.toString());
        current.setLength(0);
      }
      if (current.length() > 0) current.append("\n\n");
      current.append(paragraph);
    }
    if (current.length() > 0) output.add(current.toString());
    return output;
  }

  private static JSONArray copyArray(JSONArray source) {
    try {
      return source == null ? new JSONArray() : new JSONArray(source.toString());
    } catch (Exception ignored) {
      return new JSONArray();
    }
  }

  private static String readAsset(Context context, String path) throws Exception {
    StringBuilder text = new StringBuilder();
    try (InputStream input = context.getAssets().open(path);
         BufferedReader buffered = new BufferedReader(new InputStreamReader(input, "UTF-8"))) {
      String line;
      while ((line = buffered.readLine()) != null) text.append(line).append('\n');
    }
    return text.toString();
  }
}

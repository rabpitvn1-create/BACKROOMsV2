package com.rabpit.backroom.core;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Loads authored Markdown plus compiler-owned story decision contracts. */
final class StoryRepository {
  static final String STORY_CATALOG_ASSET = "story/generated/story_catalog.json";
  static final String LEVEL_ZERO_METADATA_ASSET = "story/generated/level_0/level0.story.json";
  static final String LEVEL_ZERO_INTERACTIONS_ASSET = "story/generated/level_0/level0.interactions.json";
  private static final int MAX_CACHED_CHAPTERS = 3;

  static final String MODE_LINEAR = "LINEAR";
  static final String MODE_DECISION = "DECISION";
  static final String MODE_ENTITY_GATE = "ENTITY_GATE";
  static final String MODE_CUTAWAY = "CUTAWAY";
  static final String MODE_LOCKED_EVENT = "LOCKED_EVENT";

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
    final JSONObject eventsAfterSegment;
    final JSONArray segmentIds;
    final String sourceDigest;
    final String compilerFingerprint;
    final String metadataPath;
    final String interactionsPath;

    Chapter(JSONObject json) {
      this(json, "", "");
    }

    Chapter(JSONObject json, String metadataPath, String interactionsPath) {
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
      eventsAfterSegment = copyObject(json.optJSONObject("eventsAfterSegment"));
      segmentIds = copyArray(json.optJSONArray("segmentIds"));
      sourceDigest = json.optString("sourceDigest", "").trim().toLowerCase(Locale.ROOT);
      compilerFingerprint = json.optString("compilerFingerprint", "").trim();
      this.metadataPath = metadataPath == null ? "" : metadataPath.trim();
      this.interactionsPath = interactionsPath == null ? "" : interactionsPath.trim();
    }

    boolean isLazyDescriptor() {
      return !metadataPath.isEmpty() && segmentIds.length() == 0;
    }
  }

  private static final class CatalogEntry {
    final String storyId;
    final String levelKey;
    final String metadataPath;

    CatalogEntry(String levelKey, JSONObject json) {
      this.levelKey = levelKey == null ? "" : levelKey.trim();
      storyId = json == null ? "" : json.optString("storyId", "").trim();
      metadataPath = json == null ? "" : json.optString("metadata", "").trim();
    }
  }

  static final class Segment {
    final String id;
    final String chapterId;
    final int index;
    final int count;
    final String text;
    final String mode;
    final JSONObject decisionContract;

    Segment(
        String id,
        String chapterId,
        int index,
        int count,
        String text,
        String mode,
        JSONObject decisionContract) {
      this.id = id;
      this.chapterId = chapterId;
      this.index = index;
      this.count = count;
      this.text = text;
      this.mode = mode;
      this.decisionContract = copyObject(decisionContract);
    }
  }

  private static final class DecisionSpec {
    final String mode;
    final JSONObject contract;

    DecisionSpec(String mode, JSONObject rawContract) {
      String normalizedMode = normalizeMode(mode);
      JSONObject sanitized = sanitizeDecisionContract(normalizedMode, rawContract);
      if ((MODE_DECISION.equals(normalizedMode) || MODE_ENTITY_GATE.equals(normalizedMode))
          && sanitized.length() == 0) {
        normalizedMode = MODE_LINEAR;
      }
      this.mode = normalizedMode;
      this.contract = (MODE_DECISION.equals(normalizedMode) || MODE_ENTITY_GATE.equals(normalizedMode))
          ? sanitized : new JSONObject();
    }
  }

  interface AssetReader {
    String read(String path) throws Exception;
  }

  private final AssetReader reader;
  private final Map<String, CatalogEntry> catalog = new LinkedHashMap<>();
  private final Map<String, Chapter> chapters = new LinkedHashMap<>();
  private final Map<String, List<Segment>> segmentCache =
      new LinkedHashMap<String, List<Segment>>(8, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, List<Segment>> eldest) {
          return size() > MAX_CACHED_CHAPTERS;
        }
      };
  private final Map<String, Map<String, DecisionSpec>> v2DecisionCache =
      new LinkedHashMap<String, Map<String, DecisionSpec>>(8, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Map<String, DecisionSpec>> eldest) {
          return size() > MAX_CACHED_CHAPTERS;
        }
      };
  private final Map<String, String> decisionChapterDigests = new LinkedHashMap<>();
  private final Map<String, DecisionSpec> decisionBySegment = new LinkedHashMap<>();
  private String sourceRevision = "";
  private String startChapter = "";
  private String storyId = "";
  private String levelKey = "";
  private String compilerFingerprint = "";
  private boolean v2Bound = false;
  private int targetChars = 1900;
  private int maxChars = 2400;

  StoryRepository(Context context) {
    this(context == null ? null : path -> readAsset(context, path));
  }

  StoryRepository(AssetReader reader) {
    this.reader = reader;
    if (reader != null) {
      try {
        loadCatalog(reader.read(STORY_CATALOG_ASSET));
        bindLevel("0");
        return;
      } catch (Exception ignored) {
        catalog.clear();
      }
      try {
        loadMetadata(reader.read(LEVEL_ZERO_METADATA_ASSET));
        // Legacy metadata can still be read, but decisions without authored local
        // reactions are never exposed to a running game.
      } catch (Exception ignored) {
        chapters.clear();
        clearDecisions();
      }
    }
  }

  static StoryRepository fromText(String metadataJson, Map<String, String> sources) {
    return fromText(metadataJson, sources, null);
  }

  static StoryRepository fromText(
      String metadataJson, Map<String, String> sources, String decisionsJson) {
    StoryRepository repository = new StoryRepository(path -> {
      String value = sources.get(path);
      if (value == null) throw new IllegalArgumentException("Missing test story asset: " + path);
      return value;
    });
    repository.loadMetadata(metadataJson);
    if (decisionsJson != null) repository.loadDecisions(decisionsJson);
    return repository;
  }

  boolean available() {
    return !chapters.isEmpty() && !startChapter.isEmpty();
  }

  boolean hasStoryForLevel(String requestedLevelKey) {
    if (catalog.isEmpty()) return "0".equals(requestedLevelKey) && available();
    return catalog.containsKey(requestedLevelKey == null ? "" : requestedLevelKey.trim());
  }

  synchronized boolean bindLevel(String requestedLevelKey) {
    String requested = requestedLevelKey == null ? "" : requestedLevelKey.trim();
    if (catalog.isEmpty()) return "0".equals(requested) && available();
    if (v2Bound && requested.equals(levelKey) && available()) return true;
    CatalogEntry entry = catalog.get(requested);
    clearBoundArc();
    if (entry == null || reader == null) return false;
    try {
      JSONObject root = new JSONObject(reader.read(entry.metadataPath));
      if (root.optInt("schemaVersion", 0) != 2) return false;
      if (!entry.storyId.equals(root.optString("storyId", "").trim())) return false;
      if (!entry.levelKey.equals(root.optString("levelKey", "").trim())) return false;
      sourceRevision = root.optString("sourceRevision", "").trim();
      compilerFingerprint = root.optString("compilerFingerprint", "").trim();
      startChapter = root.optString("startChapter", "").trim();
      storyId = entry.storyId;
      levelKey = entry.levelKey;
      if (sourceRevision.isEmpty() || compilerFingerprint.isEmpty() || startChapter.isEmpty()) {
        clearBoundArc();
        return false;
      }
      JSONArray list = root.optJSONArray("chapters");
      if (list == null) {
        clearBoundArc();
        return false;
      }
      for (int i = 0; i < list.length(); i++) {
        JSONObject raw = list.optJSONObject(i);
        if (raw == null) continue;
        String metadataPath = raw.optString("metadata", "").trim();
        String interactionsPath = raw.optString("interactions", "").trim();
        Chapter chapter = new Chapter(raw, metadataPath, interactionsPath);
        if (chapter.id.isEmpty() || chapter.source.isEmpty()
            || metadataPath.isEmpty() || interactionsPath.isEmpty()
            || chapters.containsKey(chapter.id)) {
          clearBoundArc();
          return false;
        }
        chapters.put(chapter.id, chapter);
      }
      if (!chapters.containsKey(startChapter)) {
        clearBoundArc();
        return false;
      }
      v2Bound = true;
      return true;
    } catch (Exception ignored) {
      clearBoundArc();
      return false;
    }
  }

  String sourceRevision() {
    return sourceRevision;
  }

  String storyId() {
    return storyId;
  }

  String levelKey() {
    return levelKey;
  }

  String startChapter() {
    return startChapter;
  }

  Chapter chapter(String id) {
    String key = id == null ? "" : id.trim();
    Chapter current = chapters.get(key);
    if (current == null || !v2Bound || !current.isLazyDescriptor()) return current;
    Chapter loaded = loadV2Chapter(current);
    if (loaded != null) {
      chapters.put(key, loaded);
      return loaded;
    }
    return null;
  }

  String rawMarkdown(String chapterId) {
    Chapter value = chapter(chapterId);
    if (value == null || reader == null || value.source.isEmpty()) return "";
    try {
      return reader.read(value.source);
    } catch (Exception ignored) {
      return "";
    }
  }

  Segment segment(String chapterId, int index) {
    List<Segment> segments = segments(chapterId);
    if (index < 0 || index >= segments.size()) return null;
    return segments.get(index);
  }

  Segment nextSegment(String chapterId, int index) {
    Chapter chapter = chapter(chapterId);
    if (chapter == null) return null;
    List<Segment> current = segments(chapterId);
    if (index + 1 < current.size()) return current.get(index + 1);
    if (chapter.nextChapter.isEmpty()) return null;
    List<Segment> next = segments(chapter.nextChapter);
    return next.isEmpty() ? null : next.get(0);
  }

  int segmentCount(String chapterId) {
    return segments(chapterId).size();
  }

  int segmentIndex(String chapterId, String segmentId) {
    String requested = segmentId == null ? "" : segmentId.trim();
    if (requested.isEmpty()) return -1;
    List<Segment> values = segments(chapterId);
    for (int i = 0; i < values.size(); i++) {
      if (requested.equals(values.get(i).id)) return i;
    }
    return -1;
  }

  private List<Segment> segments(String chapterId) {
    String key = chapterId == null ? "" : chapterId.trim();
    List<Segment> cached = segmentCache.get(key);
    if (cached != null) return cached;

    List<Segment> result = new ArrayList<>();
    Chapter chapter = chapter(key);
    if (chapter == null || reader == null || chapter.source.isEmpty()) {
      segmentCache.put(key, result);
      return result;
    }

    try {
      String manuscript = reader.read(chapter.source);
      String digest = sourceDigest(manuscript);
      List<String> blocks = splitMarkdown(manuscript, targetChars, maxChars);
      if (v2Bound) {
        if (!digest.equals(chapter.sourceDigest) || chapter.segmentIds.length() != blocks.size()) {
          segmentCache.put(key, result);
          return result;
        }
        Map<String, DecisionSpec> compiledById = loadV2Decisions(chapter, digest);
        if (compiledById == null) {
          segmentCache.put(key, result);
          return result;
        }
        for (int i = 0; i < blocks.size(); i++) {
          String id = chapter.segmentIds.optString(i, "").trim();
          if (id.isEmpty()) {
            result.clear();
            break;
          }
          DecisionSpec compiled = compiledById.get(id);
          if (compiled == null) {
            result.clear();
            break;
          }
          String mode = "cutaway".equals(chapter.visibility) ? MODE_CUTAWAY : compiled.mode;
          JSONObject contract = compiled.contract;
          result.add(new Segment(id, chapter.id, i, blocks.size(), blocks.get(i), mode, contract));
        }
      } else {
        boolean decisionsFresh = digest.equals(decisionChapterDigests.get(key));
        for (int i = 0; i < blocks.size(); i++) {
          String id = chapter.id + "_P" + String.format(Locale.ROOT, "%03d", i + 1);
          DecisionSpec compiled = decisionsFresh ? decisionBySegment.get(id) : null;
          String fallbackMode = "cutaway".equals(chapter.visibility) ? MODE_CUTAWAY : MODE_LINEAR;
          String mode = compiled == null ? fallbackMode : compiled.mode;
          if ("cutaway".equals(chapter.visibility)) mode = MODE_CUTAWAY;
          JSONObject contract = compiled == null ? new JSONObject() : compiled.contract;
          result.add(new Segment(id, chapter.id, i, blocks.size(), blocks.get(i), mode, contract));
        }
      }
    } catch (Exception ignored) {
      result.clear();
    }

    segmentCache.put(key, result);
    return result;
  }

  void loadMetadata(String json) {
    clearBoundArc();
    catalog.clear();
    v2Bound = false;
    try {
      JSONObject root = new JSONObject(json == null ? "{}" : json);
      if (root.optInt("schemaVersion", 0) != 1) return;
      sourceRevision = root.optString("sourceRevision", "").trim();
      storyId = "level_0_main";
      levelKey = "0";
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

  void loadDecisions(String json) {
    clearDecisions();
    segmentCache.clear();
    try {
      JSONObject root = new JSONObject(json == null ? "{}" : json);
      if (root.optInt("schemaVersion", 0) != 3) return;
      if (!sourceRevision.equals(root.optString("sourceRevision", "").trim())) return;
      JSONObject compiledChapters = root.optJSONObject("chapters");
      if (compiledChapters == null) return;

      java.util.Iterator<String> chapterIds = compiledChapters.keys();
      while (chapterIds.hasNext()) {
        String chapterId = chapterIds.next();
        if (!chapters.containsKey(chapterId)) continue;
        JSONObject compiledChapter = compiledChapters.optJSONObject(chapterId);
        if (compiledChapter == null) continue;
        String digest = compiledChapter.optString("sourceDigest", "").trim().toLowerCase(Locale.ROOT);
        if (!digest.matches("[0-9a-f]{64}")) continue;
        decisionChapterDigests.put(chapterId, digest);

        JSONObject segmentSpecs = compiledChapter.optJSONObject("segments");
        if (segmentSpecs == null) continue;
        java.util.Iterator<String> segmentIds = segmentSpecs.keys();
        while (segmentIds.hasNext()) {
          String segmentId = segmentIds.next();
          if (!segmentId.startsWith(chapterId + "_P")) continue;
          JSONObject spec = segmentSpecs.optJSONObject(segmentId);
          if (spec == null) continue;
          decisionBySegment.put(
              segmentId,
              new DecisionSpec(
                  spec.optString("mode", MODE_LINEAR),
                  spec.optJSONObject("decisionContract")));
        }
      }
    } catch (Exception ignored) {
      clearDecisions();
    }
  }

  static List<String> splitMarkdown(String markdown, int targetChars, int maxChars) {
    String source = canonicalSource(markdown).trim();
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

  static String sourceDigest(String source) {
    try {
      String canonical = canonicalSource(source);
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
      StringBuilder output = new StringBuilder();
      for (byte value : hash) output.append(String.format(Locale.ROOT, "%02x", value & 0xff));
      return output.toString();
    } catch (Exception ignored) {
      return "";
    }
  }

  private static String canonicalSource(String source) {
    String normalized = source == null ? "" : source;
    if (!normalized.isEmpty() && normalized.charAt(0) == '\ufeff') {
      normalized = normalized.substring(1);
    }
    normalized = normalized.replace("\r\n", "\n").replace('\r', '\n').trim();
    return normalized + "\n";
  }

  private void loadCatalog(String json) throws Exception {
    catalog.clear();
    JSONObject root = new JSONObject(json == null ? "{}" : json);
    if (root.optInt("schemaVersion", 0) != 2) {
      throw new IllegalArgumentException("Unsupported story catalog schema");
    }
    compilerFingerprint = root.optString("compilerFingerprint", "").trim();
    JSONObject levels = root.optJSONObject("levels");
    if (levels == null || compilerFingerprint.isEmpty()) {
      throw new IllegalArgumentException("Story catalog is incomplete");
    }
    java.util.Iterator<String> keys = levels.keys();
    while (keys.hasNext()) {
      String key = keys.next();
      CatalogEntry entry = new CatalogEntry(key, levels.optJSONObject(key));
      if (entry.storyId.isEmpty() || entry.metadataPath.isEmpty() || catalog.containsKey(entry.levelKey)) {
        throw new IllegalArgumentException("Invalid story catalog entry");
      }
      catalog.put(entry.levelKey, entry);
    }
  }

  private Chapter loadV2Chapter(Chapter descriptor) {
    try {
      JSONObject root = new JSONObject(reader.read(descriptor.metadataPath));
      if (root.optInt("schemaVersion", 0) != 2) return null;
      if (!storyId.equals(root.optString("storyId", "").trim())) return null;
      if (!levelKey.equals(root.optString("levelKey", "").trim())) return null;
      if (!sourceRevision.equals(root.optString("sourceRevision", "").trim())) return null;
      if (!compilerFingerprint.equals(root.optString("compilerFingerprint", "").trim())) return null;
      if (!descriptor.id.equals(root.optString("id", "").trim())) return null;
      Chapter loaded = new Chapter(root, descriptor.metadataPath, descriptor.interactionsPath);
      if (loaded.segmentIds.length() == 0 || !loaded.sourceDigest.matches("[0-9a-f]{64}")) return null;
      return loaded;
    } catch (Exception ignored) {
      return null;
    }
  }

  private Map<String, DecisionSpec> loadV2Decisions(Chapter chapter, String digest) {
    Map<String, DecisionSpec> cached = v2DecisionCache.get(chapter.id);
    if (cached != null) return cached;
    try {
      JSONObject root = new JSONObject(reader.read(chapter.interactionsPath));
      if (root.optInt("schemaVersion", 0) != 4) return null;
      if (!storyId.equals(root.optString("storyId", "").trim())) return null;
      if (!levelKey.equals(root.optString("levelKey", "").trim())) return null;
      if (!sourceRevision.equals(root.optString("sourceRevision", "").trim())) return null;
      if (!compilerFingerprint.equals(root.optString("compilerFingerprint", "").trim())) return null;
      if (!chapter.id.equals(root.optString("chapterId", "").trim())) return null;
      if (!digest.equals(root.optString("sourceDigest", "").trim().toLowerCase(Locale.ROOT))) return null;
      JSONArray ids = root.optJSONArray("segmentIds");
      JSONObject specs = root.optJSONObject("segments");
      if (ids == null || specs == null || ids.length() != chapter.segmentIds.length()) return null;
      Map<String, DecisionSpec> output = new LinkedHashMap<>();
      for (int i = 0; i < ids.length(); i++) {
        String segmentId = ids.optString(i, "").trim();
        if (!segmentId.equals(chapter.segmentIds.optString(i, "").trim())) return null;
        JSONObject spec = specs.optJSONObject(segmentId);
        if (spec == null) return null;
        output.put(segmentId, new DecisionSpec(
            spec.optString("mode", MODE_LINEAR), spec.optJSONObject("decisionContract")));
      }
      v2DecisionCache.put(chapter.id, output);
      return output;
    } catch (Exception ignored) {
      return null;
    }
  }

  private void clearBoundArc() {
    chapters.clear();
    segmentCache.clear();
    v2DecisionCache.clear();
    clearDecisions();
    sourceRevision = "";
    startChapter = "";
    storyId = "";
    levelKey = "";
    v2Bound = false;
    targetChars = 1900;
    maxChars = 2400;
  }

  private static String normalizeMode(String raw) {
    String mode = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
    if (MODE_DECISION.equals(mode)
        || MODE_ENTITY_GATE.equals(mode)
        || MODE_CUTAWAY.equals(mode)
        || MODE_LOCKED_EVENT.equals(mode)) {
      return mode;
    }
    return MODE_LINEAR;
  }

  private static JSONObject sanitizeDecisionContract(String mode, JSONObject raw) {
    JSONObject output = new JSONObject();
    if (raw == null) return output;
    try {
      if (MODE_DECISION.equals(mode)) {
        String anchor = raw.optString("loopAnchor", "").trim();
        String guard = raw.optString("decisionGuard", "").trim();
        if (anchor.isEmpty() || guard.isEmpty()) return output;
        output.put("loopAnchor", anchor);
        output.put("decisionGuard", guard);
        JSONArray variants = raw.optJSONArray("offlineVariants");
        if (variants != null && variants.length() >= 3) {
          for (int i = 0; i < variants.length(); i++) {
            JSONObject variant = variants.optJSONObject(i);
            if (variant == null || variant.optString("canon", "").trim().isEmpty()
                || variant.optJSONObject("trap") == null
                || variant.optJSONObject("converge") == null) return new JSONObject();
          }
          output.put("offlineVariants", copyArray(variants));
        }
        return output;
      }
      if (MODE_ENTITY_GATE.equals(mode)) {
        String entityKey = raw.optString("entityKey", "").trim().toLowerCase(Locale.ROOT);
        String attackText = raw.optString("attackText", "").trim();
        String anchor = raw.optString("loopAnchor", "").trim();
        if (entityKey.isEmpty() || attackText.isEmpty() || anchor.isEmpty()) return output;
        output.put("entityKey", entityKey);
        output.put("attackText", attackText);
        output.put("loopAnchor", anchor);
        return output;
      }
    } catch (Exception ignored) {
      return new JSONObject();
    }
    return output;
  }


  private void clearDecisions() {
    decisionChapterDigests.clear();
    decisionBySegment.clear();
  }

  private static JSONArray copyArray(JSONArray source) {
    try {
      return source == null ? new JSONArray() : new JSONArray(source.toString());
    } catch (Exception ignored) {
      return new JSONArray();
    }
  }

  private static JSONObject copyObject(JSONObject source) {
    try {
      return source == null ? new JSONObject() : new JSONObject(source.toString());
    } catch (Exception ignored) {
      return new JSONObject();
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

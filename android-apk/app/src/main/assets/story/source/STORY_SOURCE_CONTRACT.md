# Story source contract v2

This contract is compiler input, not runtime gameplay data. Markdown manuscripts remain the creative source of truth. Files under `story/generated/` are compiler output and must not be hand-edited.

## Runtime catalog

The generated top-level catalog is intentionally small:

```json
{
  "schemaVersion": 2,
  "levels": {
    "0":   {"storyId": "level_0_main", "metadata": "story/generated/level_0/arc.json"},
    "0.1": {"storyId": "level_0_1_zenith_station", "metadata": "story/generated/level_0_1/arc.json"}
  }
}
```

It may map a `currentLevelKey` only to a stable `storyId` and the arc metadata path. It must not contain chapter prose, chapter events, destinations, or Level transition rules.

## Source catalog

`story/source/story_catalog.source.json` is compile-time input. Each record declares a stable story id, level key, source revision, manuscript root, annotation file, and generated root. It never declares a Level destination.

## Arc annotations

The annotation file is technical authoring support. Writers do not write generated gameplay JSON. It declares stable chapter IDs/order, thread/visibility, protected facts, and the small set of runtime events that authored continuity actually needs.

Events may attach at `ENTER`, `EXIT`, or `AFTER_TEXT`. `AFTER_TEXT` requires a literal text anchor that occurs exactly once in the canonical manuscript. The compiler resolves that anchor to a generated stable segment id. Hand-written segment indexes are forbidden.

Example:

```json
{
  "schemaVersion": 1,
  "storyId": "level_0_1_zenith_station",
  "levelKey": "0.1",
  "sourceRevision": "zenith-station-r1",
  "startChapter": "L01_C01",
  "chapters": [
    {
      "id": "L01_C01",
      "source": "LEVEL0.1_CH01_ZENITH_STATION.md",
      "thread": "cao_minh",
      "visibility": "player",
      "events": [
        {
          "when": "AFTER_TEXT",
          "anchor": "Nam đã chết.",
          "type": "CHARACTER_PRESENCE",
          "character": "nam",
          "presence": "DECEASED"
        }
      ]
    }
  ]
}
```

## Generated arc layout

Each arc has a small `arc.json` index and per-chapter artifacts:

```text
story/generated/<arc>/
  arc.json
  chapters/<chapterId>.json
  interactions/<chapterId>.json
```

`arc.json` contains arc identity, start chapter, ordered stable chapter ids, and paths to the per-chapter metadata/interactions. It contains no prose. Runtime loads only the bound arc index, current chapter artifact, current manuscript, and limited adjacent data.

## Stable IDs and canonical source

Chapter IDs are author-declared and immutable after release. Segment IDs are content-addressed from canonical segment text and are not array positions or file sequence numbers.

Canonical source is defined identically for compiler and Java runtime:
1. decode UTF-8;
2. remove one leading U+FEFF BOM if present;
3. normalize CRLF and CR to LF;
4. trim only outer Unicode whitespace for digest/segmentation view;
5. append one LF for digest input.

The source file itself is never rewritten. Runtime prose keeps manuscript wording; canonicalization only removes transport artifacts (BOM/line-ending differences).

## Version fields

- `schemaVersion`: generated data shape.
- `sourceRevision`: editorial/layout revision declared by the arc source. Compiler-only refactors do not bump it.
- `sourceDigest`: SHA-256 of canonical manuscript text, per chapter.
- `compilerFingerprint`: semantic compiler contract fingerprint. It changes only when artifact semantics/segmentation compatibility changes, not for an implementation-only refactor.
- generated chapter artifacts contain the exact ordered segment-id set.

## Validation gates

Compilation fails before writing output when any of these are true:
- duplicate levelKey, storyId, chapterId, source path, or generated root;
- catalog/annotation identity mismatch;
- missing start chapter, source file, next chapter, or referenced annotation target;
- chapter graph contains an accidental cycle;
- thread/visibility is unsupported;
- an AFTER_TEXT anchor occurs zero times or more than once;
- a story event carries a Level destination;
- a hand-written segment index is present;
- a generated artifact has wrong schemaVersion/sourceRevision/sourceDigest/compilerFingerprint or segment set/order;
- a generated catalog contains Level transition rules or prose.

Writes are temp-file + atomic replace. CI compiles only affected stories/chapters unless the source catalog, schema, segmentation contract, or artifact schema requires a controlled wider rebuild.

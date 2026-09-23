#!/usr/bin/env python3
import argparse
import concurrent.futures
import hashlib
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
STORY_ROOT = ROOT / "android-apk/app/src/main/assets/story"
METADATA_PATH = STORY_ROOT / "generated/level_0/level0.story.json"
OUTPUT_PATH = STORY_ROOT / "generated/level_0/level0.interactions.json"

SCHEMA_VERSION = 1
COMPILER_VERSION = 1
MAX_INTERACTIVE_PER_CHAPTER = 2
VALID_MODES = {"LINEAR", "INTERACTIVE", "CUTAWAY", "LOCKED_EVENT"}
HAIKU_DEFAULT_BASE_URL = "https://api.anthropic.com/v1/messages"
HAIKU_DEFAULT_MODEL = "claude-haiku-4-5-20251001"
GEMINI_DEFAULT_MODEL = "gemini-3.6-flash"


class CompileError(RuntimeError):
    pass


class HttpStatusError(CompileError):
    def __init__(self, status, message):
        super().__init__(message)
        self.status = status


def java_len(text):
    return len(text.encode("utf-16-le")) // 2


def canonical_source(text):
    return text.replace("\r\n", "\n").replace("\r", "\n").strip() + "\n"


def source_digest(text):
    return hashlib.sha256(canonical_source(text).encode("utf-8")).hexdigest()


def split_markdown(markdown, target_chars, max_chars):
    source = markdown.replace("\r\n", "\n").replace("\r", "\n").strip()
    if not source:
        return []
    paragraphs = []
    for value in re.split(r"\n\s*\n+", source):
        paragraph = value.strip()
        if not paragraph:
            continue
        if paragraph.startswith("# "):
            paragraph = paragraph[2:].strip()
        if paragraph:
            paragraphs.append(paragraph)

    output = []
    current = ""
    for paragraph in paragraphs:
        added_length = java_len(paragraph) + (0 if not current else 2)
        over_target = java_len(current) >= target_chars
        would_exceed_max = bool(current) and java_len(current) + added_length > max_chars
        if current and (over_target or would_exceed_max):
            output.append(current)
            current = ""
        if current:
            current += "\n\n"
        current += paragraph
    if current:
        output.append(current)
    return output


def read_metadata():
    with METADATA_PATH.open("r", encoding="utf-8") as fh:
        metadata = json.load(fh)
    if metadata.get("schemaVersion") != 1:
        raise CompileError("Unsupported story metadata schemaVersion.")
    if not metadata.get("sourceRevision"):
        raise CompileError("Story metadata sourceRevision is required.")
    chapters = metadata.get("chapters")
    if not isinstance(chapters, list) or not chapters:
        raise CompileError("Story metadata chapters are missing.")
    return metadata


def chapter_source(chapter):
    raw = chapter.get("source", "")
    if not raw.startswith("story/source/"):
        raise CompileError(f"Unsafe story source path: {raw}")
    path = STORY_ROOT / raw[len("story/"):]
    if not path.is_file():
        raise CompileError(f"Missing manuscript source: {raw}")
    return path.read_text(encoding="utf-8")


def extract_json(raw):
    text = (raw or "").strip()
    if text.startswith("```"):
        first_newline = text.find("\n")
        if first_newline >= 0:
            text = text[first_newline + 1:]
        fence = text.rfind("```")
        if fence >= 0:
            text = text[:fence]
        text = text.strip()
    start = text.find("{")
    end = text.rfind("}")
    if start < 0 or end <= start:
        raise CompileError("Model did not return a JSON object.")
    try:
        return json.loads(text[start:end + 1])
    except json.JSONDecodeError as exc:
        raise CompileError(f"Model JSON parse failed: {exc}") from exc


def http_json(url, payload, headers, timeout=90):
    request = urllib.request.Request(
        url,
        data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
        headers={"Content-Type": "application/json", **headers},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            body = response.read().decode("utf-8")
            return json.loads(body)
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")[:500]
        raise HttpStatusError(exc.code, f"HTTP {exc.code}: {detail}") from exc
    except urllib.error.URLError as exc:
        raise CompileError(f"Network error: {exc}") from exc


def haiku_base_url():
    base = os.environ.get("HAIKU_BASE_URL", "").strip() or HAIKU_DEFAULT_BASE_URL
    if not base.lower().startswith("https://"):
        raise CompileError("HAIKU_BASE_URL must use HTTPS.")
    return base.rstrip("/")


def haiku_model():
    return os.environ.get("HAIKU_MODEL", "").strip() or HAIKU_DEFAULT_MODEL


def haiku_endpoint(base, suffix):
    if base.endswith(suffix):
        return base
    if base.endswith("/v1"):
        return base + suffix
    if suffix == "/messages" and "/v1" not in base:
        return base + "/v1/messages"
    return base + suffix


def haiku_anthropic(prompt):
    api_key = os.environ.get("HAIKU_API", "").strip()
    if not api_key:
        raise CompileError("HAIKU_API is not configured.")
    base = haiku_base_url()
    payload = {
        "model": haiku_model(),
        "max_tokens": 4096,
        "temperature": 0.1,
        "messages": [{"role": "user", "content": prompt}],
    }
    result = http_json(
        haiku_endpoint(base, "/messages"),
        payload,
        {"x-api-key": api_key, "anthropic-version": "2023-06-01"},
    )
    pieces = []
    for part in result.get("content", []) or []:
        if isinstance(part, dict) and isinstance(part.get("text"), str):
            pieces.append(part["text"].strip())
    output = "\n".join(piece for piece in pieces if piece)
    if not output:
        raise CompileError("Haiku Anthropic response was empty.")
    return output


def haiku_openai(prompt):
    api_key = os.environ.get("HAIKU_API", "").strip()
    if not api_key:
        raise CompileError("HAIKU_API is not configured.")
    base = haiku_base_url()
    payload = {
        "model": haiku_model(),
        "temperature": 0.1,
        "max_tokens": 4096,
        "messages": [{"role": "user", "content": prompt}],
    }
    result = http_json(
        haiku_endpoint(base, "/chat/completions"),
        payload,
        {"Authorization": f"Bearer {api_key}"},
    )
    choices = result.get("choices") or []
    if not choices:
        raise CompileError("Haiku OpenAI response had no choices.")
    content = (choices[0].get("message") or {}).get("content")
    if isinstance(content, str):
        output = content.strip()
    elif isinstance(content, list):
        output = "\n".join(
            str(part.get("text", "")).strip()
            for part in content
            if isinstance(part, dict) and part.get("text")
        )
    else:
        output = ""
    if not output:
        raise CompileError("Haiku OpenAI response was empty.")
    return output


def call_haiku(prompt):
    base = haiku_base_url()
    last = None
    for attempt in range(2):
        try:
            if base.endswith("/chat/completions"):
                return haiku_openai(prompt)
            if base.endswith("/messages") or "api.anthropic.com" in base:
                return haiku_anthropic(prompt)
            try:
                return haiku_openai(prompt)
            except HttpStatusError as exc:
                if exc.status not in {400, 404, 405, 415, 422}:
                    raise
                return haiku_anthropic(prompt)
        except Exception as exc:
            last = exc
            status = getattr(exc, "status", 0)
            if attempt == 0 and (status in {408, 429, 500, 502, 503, 504} or not status):
                time.sleep(1.2)
                continue
            break
    raise CompileError(f"Haiku failed: {last}")


def gemini_keys():
    values = []
    for name in ("GEMINI_API_KEY_1", "GEMINI_API_KEY_2", "GEMINI_API_KEY_3",
                 "GEMINI_API_KEY_4", "GEMINI_API_KEY_5", "GEMINI_API_KEY"):
        value = os.environ.get(name, "").strip()
        if value and value not in values:
            values.append(value)
    return values


def call_gemini(prompt):
    keys = gemini_keys()
    if not keys:
        raise CompileError("No Gemini fallback key is configured.")
    model = os.environ.get("GEMINI_MODEL", "").strip() or GEMINI_DEFAULT_MODEL
    url = f"https://generativelanguage.googleapis.com/v1beta/models/{urllib.parse.quote(model)}:generateContent"
    last = None
    for key in keys:
        try:
            payload = {
                "contents": [{"role": "user", "parts": [{"text": prompt}]}],
                "generationConfig": {"temperature": 0.1, "maxOutputTokens": 4096},
            }
            result = http_json(url, payload, {"x-goog-api-key": key})
            pieces = []
            for candidate in result.get("candidates", []) or []:
                for part in ((candidate.get("content") or {}).get("parts") or []):
                    if isinstance(part, dict) and isinstance(part.get("text"), str):
                        pieces.append(part["text"].strip())
            output = "\n".join(piece for piece in pieces if piece)
            if not output:
                raise CompileError("Gemini response was empty.")
            return output
        except Exception as exc:
            last = exc
            status = getattr(exc, "status", 0)
            if status and status not in {408, 429, 500, 502, 503, 504}:
                break
            time.sleep(0.8)
    raise CompileError(f"Gemini fallback failed: {last}")


def generate(prompt):
    try:
        return call_haiku(prompt), "haiku"
    except Exception as haiku_error:
        print(f"[story-compiler] Haiku failed, using Gemini fallback: {haiku_error}", file=sys.stderr)
        return call_gemini(prompt), "gemini"


def forced_locked_indices(chapter, segment_count):
    locked = set()
    if segment_count <= 0 or chapter.get("visibility") == "cutaway":
        return locked
    if chapter.get("eventsOnEnter"):
        locked.add(0)
    if chapter.get("eventsOnExit"):
        locked.add(segment_count - 1)
    return locked


def build_prompt(chapter, segments, forced_locked):
    payload = {
        "chapter": {
            "id": chapter.get("id"),
            "title": chapter.get("title"),
            "thread": chapter.get("thread"),
            "visibility": chapter.get("visibility"),
            "requiredFacts": chapter.get("requiredFacts") or [],
            "forbiddenClaims": chapter.get("forbiddenClaims") or [],
            "eventsOnEnter": chapter.get("eventsOnEnter") or [],
            "eventsOnExit": chapter.get("eventsOnExit") or [],
        },
        "forcedLockedSegmentIds": [segments[i]["id"] for i in sorted(forced_locked)],
        "segments": segments,
    }
    return """You are the BACKROOMsV2 Story Compiler. Convert authored Vietnamese novel prose into conservative gameplay interaction metadata.

AUTHORIAL AUTHORITY:
- The manuscript decides WHAT happens.
- A player choice may only vary HOW Cao Minh approaches, observes, checks, waits, speaks, or prepares.
- Choices MUST converge back to the authored manuscript. Never create alternate plot outcomes.
- Never add a new character, item, Entity, route, revelation, relationship change, death, survival outcome, Level transition, combat result, or lore fact.
- Never contradict requiredFacts or forbiddenClaims.
- Do not rewrite manuscript prose.

CLASSIFICATION:
- LINEAR: no useful player decision after this segment.
- INTERACTIVE: there is a natural pause after this segment where Cao Minh can make a small local decision before the next authored segment.
- LOCKED_EVENT: this segment contains or directly anchors mandatory plot/dialogue/revelation. No choices.
- CUTAWAY is reserved for compiler-forced reader-only parallel scenes. Do not output CUTAWAY here unless input visibility is cutaway.

INTERACTION RULES:
- At most 2 INTERACTIVE segments in this chapter.
- An INTERACTIVE segment MUST have exactly 3 concise Vietnamese choices.
- Each choice must be a local approach that can receive a short AI reaction and then return to the same manuscript path.
- Do not offer "leave", "refuse the plot", "attack an ally", "change destination", or any choice that would invalidate the next segment.
- interactionGuard must state what must remain unchanged before the next authored segment.
- Prefer LINEAR over a weak or artificial choice.
- Any forcedLockedSegmentId MUST be LOCKED_EVENT.

Return JSON only:
{
  "segments": [
    {
      "id": "exact input segment id",
      "mode": "LINEAR|INTERACTIVE|LOCKED_EVENT",
      "choices": [
        {"text":"...", "action":"..."},
        {"text":"...", "action":"..."},
        {"text":"...", "action":"..."}
      ],
      "interactionGuard":"..."
    }
  ]
}

For LINEAR/LOCKED_EVENT, choices must be [] and interactionGuard must be "".
Every input segment must appear exactly once and in the same order.

INPUT:
""" + json.dumps(payload, ensure_ascii=False, indent=2)


def sanitize_model_chapter(chapter, segments, raw_result, forced_locked):
    result = extract_json(raw_result)
    rows = result.get("segments")
    if not isinstance(rows, list):
        raise CompileError(f"{chapter['id']}: model output missing segments array.")
    expected_ids = [segment["id"] for segment in segments]
    returned_ids = [row.get("id") for row in rows if isinstance(row, dict)]
    if returned_ids != expected_ids:
        raise CompileError(f"{chapter['id']}: model segment IDs/order do not match compiler segmentation.")

    output = {}
    interactive_count = 0
    for index, (segment, row) in enumerate(zip(segments, rows)):
        if not isinstance(row, dict):
            raise CompileError(f"{chapter['id']}: invalid segment output at index {index}.")
        mode = str(row.get("mode", "LINEAR")).strip().upper()
        if mode not in {"LINEAR", "INTERACTIVE", "LOCKED_EVENT"}:
            mode = "LINEAR"
        if index in forced_locked:
            mode = "LOCKED_EVENT"

        choices = row.get("choices") if isinstance(row.get("choices"), list) else []
        guard = str(row.get("interactionGuard", "") or "").strip()

        if mode == "INTERACTIVE":
            if interactive_count >= MAX_INTERACTIVE_PER_CHAPTER:
                mode = "LINEAR"
            elif len(choices) != 3 or not guard:
                mode = "LINEAR"

        clean_choices = []
        if mode == "INTERACTIVE":
            for choice in choices:
                if not isinstance(choice, dict):
                    clean_choices = []
                    break
                text = str(choice.get("text", "") or "").strip()
                action = str(choice.get("action", text) or "").strip()
                if not text or not action or len(text) > 180 or len(action) > 220:
                    clean_choices = []
                    break
                if action.lower() in {"tiếp tục cốt truyện", "continue story"}:
                    clean_choices = []
                    break
                clean_choices.append({"text": text, "action": action})
            if len(clean_choices) != 3:
                mode = "LINEAR"

        if mode == "INTERACTIVE":
            interactive_count += 1
        else:
            clean_choices = []
            guard = ""

        output[segment["id"]] = {
            "mode": mode,
            "choices": clean_choices,
            "interactionGuard": guard,
        }
    return output


def compile_cutaway(chapter, segments):
    return {
        segment["id"]: {
            "mode": "CUTAWAY",
            "choices": [],
            "interactionGuard": "",
        }
        for segment in segments
    }


def compile_chapter(chapter, target, maximum):
    chapter_id = chapter.get("id", "")
    manuscript = chapter_source(chapter)
    blocks = split_markdown(manuscript, target, maximum)
    segments = [
        {"id": f"{chapter_id}_P{index + 1:03d}", "text": text}
        for index, text in enumerate(blocks)
    ]
    if not segments:
        raise CompileError(f"{chapter_id}: manuscript produced no segments.")

    forced_locked = forced_locked_indices(chapter, len(segments))
    if chapter.get("visibility") == "cutaway":
        compiled = compile_cutaway(chapter, segments)
        provider = "deterministic"
    else:
        prompt = build_prompt(chapter, segments, forced_locked)
        raw, provider = generate(prompt)
        compiled = sanitize_model_chapter(chapter, segments, raw, forced_locked)

    interactive = sum(1 for item in compiled.values() if item["mode"] == "INTERACTIVE")
    return {
        "chapterId": chapter_id,
        "provider": provider,
        "segmentCount": len(segments),
        "interactiveCount": interactive,
        "compiledChapter": {
            "source": chapter.get("source"),
            "sourceDigest": source_digest(manuscript),
            "segments": compiled,
        },
    }


def compile_story():
    metadata = read_metadata()
    target = max(800, int(metadata.get("segmentTargetChars", 1900)))
    maximum = max(target, int(metadata.get("segmentMaxChars", 2400)))
    result = {
        "schemaVersion": SCHEMA_VERSION,
        "compilerVersion": COMPILER_VERSION,
        "sourceRevision": metadata["sourceRevision"],
        "generatedBy": "story-compiler-v1",
        "chapters": {},
    }
    providers = set()
    compiled_by_id = {}

    max_workers = max(1, min(4, int(os.environ.get("STORY_COMPILER_WORKERS", "4"))))
    with concurrent.futures.ThreadPoolExecutor(max_workers=max_workers) as executor:
        future_by_id = {
            chapter.get("id", ""): executor.submit(compile_chapter, chapter, target, maximum)
            for chapter in metadata["chapters"]
        }
        for chapter in metadata["chapters"]:
            chapter_id = chapter.get("id", "")
            try:
                compiled_by_id[chapter_id] = future_by_id[chapter_id].result()
            except Exception as exc:
                for future in future_by_id.values():
                    future.cancel()
                raise CompileError(f"{chapter_id}: compilation failed: {exc}") from exc

    for chapter in metadata["chapters"]:
        chapter_id = chapter.get("id", "")
        item = compiled_by_id[chapter_id]
        providers.add(item["provider"])
        result["chapters"][chapter_id] = item["compiledChapter"]
        print(
            f"[story-compiler] {chapter_id}: {item['segmentCount']} segments, "
            f"{item['interactiveCount']} interactive, provider={item['provider']}"
        )

    result["providersUsed"] = sorted(providers)
    validate_generated(result, metadata)
    OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    temporary = OUTPUT_PATH.with_suffix(".json.tmp")
    temporary.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    temporary.replace(OUTPUT_PATH)
    print(f"[story-compiler] wrote {OUTPUT_PATH.relative_to(ROOT)}")


def validate_generated(generated=None, metadata=None):
    metadata = metadata or read_metadata()
    if generated is None:
        if not OUTPUT_PATH.is_file():
            raise CompileError(f"Missing generated interactions: {OUTPUT_PATH.relative_to(ROOT)}")
        generated = json.loads(OUTPUT_PATH.read_text(encoding="utf-8"))

    if generated.get("schemaVersion") != SCHEMA_VERSION:
        raise CompileError("Generated interaction schemaVersion mismatch.")
    if generated.get("sourceRevision") != metadata.get("sourceRevision"):
        raise CompileError("Generated interactions are stale for sourceRevision.")

    generated_chapters = generated.get("chapters")
    if not isinstance(generated_chapters, dict):
        raise CompileError("Generated interactions chapters must be an object.")

    expected_chapters = [chapter.get("id") for chapter in metadata["chapters"]]
    if set(generated_chapters.keys()) != set(expected_chapters):
        raise CompileError("Generated interaction chapter set does not match story metadata.")

    target = max(800, int(metadata.get("segmentTargetChars", 1900)))
    maximum = max(target, int(metadata.get("segmentMaxChars", 2400)))

    for chapter in metadata["chapters"]:
        chapter_id = chapter["id"]
        manuscript = chapter_source(chapter)
        blocks = split_markdown(manuscript, target, maximum)
        expected_ids = [f"{chapter_id}_P{i + 1:03d}" for i in range(len(blocks))]
        compiled_chapter = generated_chapters.get(chapter_id) or {}
        if compiled_chapter.get("sourceDigest") != source_digest(manuscript):
            raise CompileError(f"{chapter_id}: sourceDigest is stale.")
        segments = compiled_chapter.get("segments")
        if not isinstance(segments, dict) or list(segments.keys()) != expected_ids:
            raise CompileError(f"{chapter_id}: generated segment IDs/order are stale.")

        interactive_count = 0
        for index, segment_id in enumerate(expected_ids):
            spec = segments[segment_id]
            mode = spec.get("mode")
            if mode not in VALID_MODES:
                raise CompileError(f"{segment_id}: invalid mode {mode}.")
            choices = spec.get("choices")
            if not isinstance(choices, list):
                raise CompileError(f"{segment_id}: choices must be an array.")
            guard = spec.get("interactionGuard")
            if not isinstance(guard, str):
                raise CompileError(f"{segment_id}: interactionGuard must be a string.")

            if chapter.get("visibility") == "cutaway":
                if mode != "CUTAWAY" or choices or guard:
                    raise CompileError(f"{segment_id}: cutaway must be deterministic and non-interactive.")
                continue

            locked = forced_locked_indices(chapter, len(expected_ids))
            if index in locked and mode != "LOCKED_EVENT":
                raise CompileError(f"{segment_id}: authored event boundary must be LOCKED_EVENT.")

            if mode == "INTERACTIVE":
                interactive_count += 1
                if len(choices) != 3 or not guard.strip():
                    raise CompileError(f"{segment_id}: INTERACTIVE requires exactly 3 choices and a guard.")
                for choice in choices:
                    if not isinstance(choice, dict) or not str(choice.get("text", "")).strip() or not str(choice.get("action", "")).strip():
                        raise CompileError(f"{segment_id}: invalid interaction choice.")
            elif choices or guard:
                raise CompileError(f"{segment_id}: non-interactive modes cannot carry choices/guard.")

        if interactive_count > MAX_INTERACTIVE_PER_CHAPTER:
            raise CompileError(f"{chapter_id}: too many INTERACTIVE segments ({interactive_count}).")

    print("[story-compiler] generated interactions validated against current manuscript.")


def main():
    parser = argparse.ArgumentParser(description="Compile novelist manuscript into conservative story interactions.")
    parser.add_argument("--validate-generated", action="store_true",
                        help="Validate committed generated interactions without calling any model.")
    args = parser.parse_args()
    try:
        if args.validate_generated:
            validate_generated()
        else:
            compile_story()
    except Exception as exc:
        print(f"[story-compiler] ERROR: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

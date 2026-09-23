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

SCHEMA_VERSION = 2
COMPILER_VERSION = 2
MAX_DECISIONS_PER_CHAPTER = 1
VALID_MODES = {"LINEAR", "DECISION", "CUTAWAY", "LOCKED_EVENT"}
FORBIDDEN_CHOICE_PATTERNS = [
    re.compile(r"(?iu)\b(?:đi|tiến|bước|chạy)\s+(?:vào|qua|theo|về|sang|sâu|thẳng|tiếp)\b"),
    re.compile(r"(?iu)\b(?:chọn|đổi|thay đổi)\s+(?:lối|hướng|đường)\b"),
    re.compile(r"(?iu)\b(?:rời|quay lại|trở lại)\b"),
    re.compile(r"(?iu)\btiếp tục\s+(?:đi|khám phá|di chuyển)\b"),
    re.compile(r"(?iu)\btìm kiếm sâu hơn\b"),
    re.compile(r"(?iu)\b(?:đề xuất|đề nghị)\s+(?:đi|vào|rời|quay)\b"),
    re.compile(r"(?iu)\b(?:mở|phá|đập|cạy)\s+(?:cửa|panel|tường|hộp)\b"),
    re.compile(r"(?iu)\b(?:tấn công|giết|đánh)\b"),
    re.compile(r"(?iu)\b(?:uống|ăn|chạm vào)\b"),
]
RECHECK_CHOICE_PATTERNS = [
    re.compile(r"(?iu)\b(?:xác nhận|kiểm tra lại|xem lại|thử lại|đo lại|đếm lại)\b"),
]
HAIKU_DEFAULT_BASE_URL = "https://api.anthropic.com/v1/messages"
HAIKU_DEFAULT_MODEL = "claude-haiku-4-5-20251001"
GEMINI_DEFAULT_MODEL = "gemini-3.6-flash"
STANDARD_INTERACTION_GUARD = (
    "Chỉ mô tả phản ứng cục bộ cho lựa chọn vừa chọn. Không thêm phát hiện mới, không thay đổi "
    "nhân vật hiện diện, vật phẩm, Entity, Party, quan hệ, kiến thức canon, hướng cốt truyện hoặc kết quả authored. "
    "Sau phản ứng, trạng thái phải có thể tiếp tục nguyên văn phân đoạn authored kế tiếp."
)


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


def compiler_fingerprint():
    source = Path(__file__).read_bytes()
    return hashlib.sha256(source).hexdigest()


def existing_output_is_current(metadata):
    if not OUTPUT_PATH.is_file():
        return False
    try:
        generated = json.loads(OUTPUT_PATH.read_text(encoding="utf-8"))
        if generated.get("compilerFingerprint") != compiler_fingerprint():
            return False
        validate_generated(generated, metadata)
        return True
    except Exception:
        return False


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
        return call_gemini(prompt), "gemini"
    except Exception as gemini_error:
        print(
            f"[story-compiler] All Gemini keys failed, using Haiku fallback: {gemini_error}",
            file=sys.stderr,
        )
        return call_haiku(prompt), "haiku"


def forced_locked_indices(chapter, segment_count):
    locked = set()
    if segment_count <= 0 or chapter.get("visibility") == "cutaway":
        return locked
    if chapter.get("eventsOnEnter"):
        locked.add(0)
    if chapter.get("eventsOnExit"):
        locked.add(segment_count - 1)
    return locked


def apply_story_event_snapshot(state, event):
    event_type = str((event or {}).get("type", "")).strip().upper()
    character_id = str((event or {}).get("characterId", "")).strip()
    if event_type == "CHARACTER_PARALLEL_STORY" and character_id:
        state["characters"].setdefault(character_id, {})["status"] = "PARALLEL_STORY"
        state["characters"][character_id]["presence"] = "PRESENT"
    elif event_type == "CHARACTER_REUNION" and character_id:
        state["characters"].setdefault(character_id, {})["status"] = "REUNITED"
        state["characters"][character_id]["presence"] = "PRESENT"
    elif event_type == "CHARACTER_ACCOMPANY" and character_id:
        state["characters"].setdefault(character_id, {})["status"] = "ACCOMPANYING"
        state["characters"][character_id]["presence"] = "PRESENT"
    elif event_type == "CHARACTER_JOIN_PARTY" and character_id:
        state["characters"].setdefault(character_id, {})["status"] = "PARTY_MEMBER"
        state["characters"][character_id]["presence"] = "PRESENT"
    elif event_type == "CHARACTER_PRESENT" and character_id:
        state["characters"].setdefault(character_id, {})["presence"] = "PRESENT"
    elif event_type == "CHARACTER_MISSING" and character_id:
        state["characters"].setdefault(character_id, {})["presence"] = "MISSING"
    elif event_type == "STORY_FLAG_SET":
        key = str((event or {}).get("key", "")).strip()
        if key:
            state["flags"][key] = (event or {}).get("value", True)
    elif event_type == "LEVEL0_ARC_BOUNDARY_REACHED":
        state["arcBoundaryReached"] = True


def build_story_state_snapshots(metadata):
    state = {
        "characters": {
            "luc_tram": {"name": "Lục Trầm", "status": "UNSEEN_IN_STORY", "presence": "UNKNOWN"},
            "nam": {"name": "Nam", "status": "STORY_LOCAL", "presence": "UNKNOWN"},
        },
        "flags": {},
        "arcBoundaryReached": False,
    }
    snapshots = {}
    for chapter in metadata.get("chapters", []):
        for event in chapter.get("eventsOnEnter") or []:
            apply_story_event_snapshot(state, event)
        snapshots[chapter.get("id", "")] = json.loads(json.dumps(state, ensure_ascii=False))
        for event in chapter.get("eventsOnExit") or []:
            apply_story_event_snapshot(state, event)
    return snapshots


def build_prompt(chapter, segments, forced_locked, established_story_state):
    enriched = []
    for index, segment in enumerate(segments):
        enriched.append({
            **segment,
            "pauseAnchor": segment["text"][-900:],
            "nextSegment": segments[index + 1]["text"][:1800] if index + 1 < len(segments) else "",
        })
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
        "establishedStoryState": established_story_state,
        "segments": enriched,
    }
    return """You are the BACKROOMsV2 Story Compiler v2.

The novelist writes canon. Your job is NOT to invent A/B/C. Your job is only to identify a clean decision anchor and phrase the ONE hidden canon intent that naturally leads into the next authored segment.

AUTHORIAL AUTHORITY:
- The manuscript decides exactly what canon does next.
- Never alter, branch, summarize away, or replace authored events.
- Never add characters, items, Entities, routes, revelations, relationships, deaths, Level transitions, combat results, or lore.
- establishedStoryState, requiredFacts and forbiddenClaims are authoritative.
- Do not rewrite manuscript prose.

CLASSIFICATION:
- LINEAR: no clean player decision can be inserted after this segment.
- DECISION: a clean pause exists after this segment and the NEXT authored segment begins with an action/intent that can be phrased as a natural player-facing canon choice.
- LOCKED_EVENT: mandatory plot/dialogue/revelation boundary. No decision.
- CUTAWAY is compiler-forced elsewhere.

DECISION RULES:
- At most 1 DECISION per chapter. Do not force one into every chapter.
- DECISION requires a non-empty nextSegment in the same chapter.
- pauseAnchor is the exact state when the three runtime bullets will later appear.
- canonChoiceText describes ONLY the immediate player intent/action that naturally enters nextSegment. It must not reveal the result of nextSegment.
- canonChoiceText must be concise, natural Vietnamese, <= 160 characters.
- Never use "Tiếp tục cốt truyện", "continue story", A/B/C labels, outcome labels, or meta language.
- If currentSegment ends mid-dialogue/mid-action and nextSegment directly continues it, prefer LINEAR.
- Anything already completed in currentSegment is past. Do not phrase canonChoiceText as redoing it.
- A forcedLockedSegmentId MUST be LOCKED_EVENT.
- loopAnchor is compiler-owned and will be the current segment id.
- decisionGuard is compiler-owned. Return neither field yourself.

Return JSON only:
{
  "segments": [
    {
      "id": "exact input segment id",
      "mode": "LINEAR|DECISION|LOCKED_EVENT",
      "canonChoiceText": ""
    }
  ]
}

For LINEAR/LOCKED_EVENT canonChoiceText must be "".
Every input segment must appear exactly once and in the same order.

INPUT:
""" + json.dumps(payload, ensure_ascii=False, indent=2)


def choice_changes_authored_path(text):
    value = str(text or "").strip()
    return any(pattern.search(value) for pattern in FORBIDDEN_CHOICE_PATTERNS)


def choice_is_recheck_intent(text):
    value = str(text or "").strip()
    return any(pattern.search(value) for pattern in RECHECK_CHOICE_PATTERNS)


def choice_addresses_unavailable_character(text, established_story_state):
    value = str(text or "").strip()
    characters = (established_story_state or {}).get("characters") or {}
    for character_id, character in characters.items():
        presence = str((character or {}).get("presence", "UNKNOWN")).strip().upper()
        if presence == "PRESENT":
            continue
        name = str((character or {}).get("name") or character_id).strip()
        if not name or not re.search(r"(?iu)\b" + re.escape(name) + r"\b", value):
            continue
        direct_patterns = [
            r"(?iu)\bhỏi\s+" + re.escape(name) + r"\b",
            r"(?iu)\bbảo\s+" + re.escape(name) + r"\b",
            r"(?iu)\bgọi(?:\s+to)?(?:\s+lên)?(?:\s+hỏi)?\s+" + re.escape(name) + r"\b",
            r"(?iu)\bnói\s+với\s+" + re.escape(name) + r"\b",
            r"(?iu)\byêu\s+cầu\s+" + re.escape(name) + r"\b",
            r"(?iu)\bnhờ\s+" + re.escape(name) + r"\b",
            r"(?iu)\bquan\s+sát(?:\s+kỹ)?\s+" + re.escape(name) + r"\b",
            r"(?iu)\bkiểm\s+tra(?:\s+tình\s+trạng)?\s+" + re.escape(name) + r"\b",
        ]
        if any(re.search(pattern, value) for pattern in direct_patterns):
            return True
    return False


def sanitize_model_chapter(
        chapter, segments, raw_result, forced_locked, established_story_state):
    result = extract_json(raw_result)
    rows = result.get("segments")
    if not isinstance(rows, list):
        raise CompileError(f"{chapter['id']}: model output missing segments array.")
    expected_ids = [segment["id"] for segment in segments]
    returned_ids = [row.get("id") for row in rows if isinstance(row, dict)]
    if returned_ids != expected_ids:
        raise CompileError(f"{chapter['id']}: model segment IDs/order do not match compiler segmentation.")

    output = {}
    decision_count = 0
    for index, (segment, row) in enumerate(zip(segments, rows)):
        if not isinstance(row, dict):
            raise CompileError(f"{chapter['id']}: invalid segment output at index {index}.")
        mode = str(row.get("mode", "LINEAR")).strip().upper()
        if mode not in {"LINEAR", "DECISION", "LOCKED_EVENT"}:
            mode = "LINEAR"
        if index in forced_locked:
            mode = "LOCKED_EVENT"

        canon_text = str(row.get("canonChoiceText", "") or "").strip()
        if mode == "DECISION":
            if decision_count >= MAX_DECISIONS_PER_CHAPTER:
                mode = "LINEAR"
            elif index + 1 >= len(segments):
                mode = "LINEAR"
            elif (not canon_text or len(canon_text) > 160
                  or canon_text.casefold() in {"tiếp tục cốt truyện", "continue story"}
                  or choice_is_recheck_intent(canon_text)
                  or choice_addresses_unavailable_character(
                      canon_text, established_story_state)):
                mode = "LINEAR"

        if mode == "DECISION":
            decision_count += 1
            contract = {
                "canonChoiceText": canon_text,
                "loopAnchor": segment["id"],
                "decisionGuard": STANDARD_INTERACTION_GUARD,
            }
        else:
            contract = {}

        output[segment["id"]] = {
            "mode": mode,
            "decisionContract": contract,
        }
    return output


def compile_cutaway(chapter, segments):
    return {
        segment["id"]: {
            "mode": "CUTAWAY",
            "decisionContract": {},
        }
        for segment in segments
    }


def compile_safe_linear(segments, forced_locked):
    return {
        segment["id"]: {
            "mode": "LOCKED_EVENT" if index in forced_locked else "LINEAR",
            "decisionContract": {},
        }
        for index, segment in enumerate(segments)
    }


def compile_model_decisions(
        chapter, segments, forced_locked, prompt, established_story_state):
    errors = []
    strict_suffix = (
        "\n\nSTRICT RETRY: Your previous response was unusable. Return one valid JSON object only. "
        "No markdown fence, no commentary, no preface, no suffix. Preserve every segment id exactly."
    )

    for attempt in range(2):
        retry_prompt = prompt if attempt == 0 else prompt + strict_suffix
        try:
            raw, provider = generate(retry_prompt)
            return sanitize_model_chapter(
                chapter, segments, raw, forced_locked, established_story_state), provider
        except Exception as exc:
            errors.append(f"Haiku attempt {attempt + 1}: {exc}")

    try:
        raw = call_gemini(prompt + strict_suffix)
        return sanitize_model_chapter(
            chapter, segments, raw, forced_locked, established_story_state), "gemini"
    except Exception as exc:
        errors.append(f"Gemini repair: {exc}")

    chapter_id = chapter.get("id", "")
    print(
        f"[story-compiler] WARNING {chapter_id}: model output remained invalid; "
        f"falling back to safe LINEAR/LOCKED_EVENT. {' | '.join(errors)}",
        file=sys.stderr,
    )
    return compile_safe_linear(segments, forced_locked), "deterministic-safe-fallback"


def compile_chapter(chapter, target, maximum, established_story_state):
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
        prompt = build_prompt(chapter, segments, forced_locked, established_story_state)
        compiled, provider = compile_model_decisions(
            chapter, segments, forced_locked, prompt, established_story_state)

    decision_count = sum(1 for item in compiled.values() if item["mode"] == "DECISION")
    return {
        "chapterId": chapter_id,
        "provider": provider,
        "segmentCount": len(segments),
        "decisionCount": decision_count,
        "compiledChapter": {
            "source": chapter.get("source"),
            "sourceDigest": source_digest(manuscript),
            "segments": compiled,
        },
    }


def compile_story():
    metadata = read_metadata()
    if existing_output_is_current(metadata):
        print("[story-compiler] committed interactions already match current compiler + manuscript; skipping model calls.")
        return
    target = max(800, int(metadata.get("segmentTargetChars", 1900)))
    maximum = max(target, int(metadata.get("segmentMaxChars", 2400)))
    result = {
        "schemaVersion": SCHEMA_VERSION,
        "compilerVersion": COMPILER_VERSION,
        "compilerFingerprint": compiler_fingerprint(),
        "sourceRevision": metadata["sourceRevision"],
        "generatedBy": "story-compiler-v2",
        "chapters": {},
    }
    providers = set()
    compiled_by_id = {}
    story_state_snapshots = build_story_state_snapshots(metadata)

    max_workers = max(1, min(4, int(os.environ.get("STORY_COMPILER_WORKERS", "4"))))
    with concurrent.futures.ThreadPoolExecutor(max_workers=max_workers) as executor:
        future_by_id = {
            chapter.get("id", ""): executor.submit(
                compile_chapter,
                chapter,
                target,
                maximum,
                story_state_snapshots.get(chapter.get("id", ""), {}),
            )
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
            f"{item['decisionCount']} decisions, provider={item['provider']}"
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
        raise CompileError("Generated decision schemaVersion mismatch.")
    if generated.get("compilerFingerprint") != compiler_fingerprint():
        raise CompileError("Generated decisions were produced by a different compiler revision.")
    if generated.get("sourceRevision") != metadata.get("sourceRevision"):
        raise CompileError("Generated decisions are stale for sourceRevision.")

    generated_chapters = generated.get("chapters")
    if not isinstance(generated_chapters, dict):
        raise CompileError("Generated decisions chapters must be an object.")

    expected_chapters = [chapter.get("id") for chapter in metadata["chapters"]]
    if set(generated_chapters.keys()) != set(expected_chapters):
        raise CompileError("Generated decision chapter set does not match story metadata.")

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

        decision_count = 0
        for index, segment_id in enumerate(expected_ids):
            spec = segments[segment_id]
            mode = spec.get("mode")
            if mode not in VALID_MODES:
                raise CompileError(f"{segment_id}: invalid mode {mode}.")
            contract = spec.get("decisionContract")
            if not isinstance(contract, dict):
                raise CompileError(f"{segment_id}: decisionContract must be an object.")

            if chapter.get("visibility") == "cutaway":
                if mode != "CUTAWAY" or contract:
                    raise CompileError(f"{segment_id}: cutaway must be deterministic and decision-free.")
                continue

            locked = forced_locked_indices(chapter, len(expected_ids))
            if index in locked and mode != "LOCKED_EVENT":
                raise CompileError(f"{segment_id}: authored event boundary must be LOCKED_EVENT.")

            if mode == "DECISION":
                decision_count += 1
                if index + 1 >= len(expected_ids):
                    raise CompileError(f"{segment_id}: DECISION requires a next authored segment.")
                canon_text = str(contract.get("canonChoiceText", "")).strip()
                if not canon_text or len(canon_text) > 160:
                    raise CompileError(f"{segment_id}: invalid canonChoiceText.")
                if canon_text.casefold() in {"tiếp tục cốt truyện", "continue story"}:
                    raise CompileError(f"{segment_id}: meta canon choice is forbidden.")
                if contract.get("loopAnchor") != segment_id:
                    raise CompileError(f"{segment_id}: loopAnchor must equal decision segment id.")
                if contract.get("decisionGuard") != STANDARD_INTERACTION_GUARD:
                    raise CompileError(f"{segment_id}: decision guard must be compiler-owned.")
            elif contract:
                raise CompileError(f"{segment_id}: non-decision modes cannot carry decisionContract.")

        if decision_count > MAX_DECISIONS_PER_CHAPTER:
            raise CompileError(f"{chapter_id}: too many DECISION segments ({decision_count}).")

    print("[story-compiler] generated decisions validated against current manuscript.")


def main():
    parser = argparse.ArgumentParser(description="Compile novelist manuscript into canon-locked decision contracts.")
    parser.add_argument("--validate-generated", action="store_true",
                        help="Validate committed generated decision contracts without calling any model.")
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

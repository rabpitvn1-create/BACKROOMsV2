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
MAX_INTERACTIVE_PER_CHAPTER = 1
VALID_MODES = {"LINEAR", "INTERACTIVE", "CUTAWAY", "LOCKED_EVENT"}
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
        "segments": [
            {
                **segment,
                "pauseAnchor": segment["text"][-900:],
            }
            for segment in segments
        ],
    }
    return """You are the BACKROOMsV2 Story Compiler. Convert authored Vietnamese novel prose into conservative gameplay interaction metadata.

AUTHORIAL AUTHORITY:
- The manuscript decides WHAT happens.
- A player choice may only vary HOW Cao Minh approaches, observes, checks, waits, speaks, or prepares.
- Choices MUST converge back to the authored manuscript. Never create alternate plot outcomes.
- Never add a new character, item, Entity, route, revelation, relationship change, death, survival outcome, Level transition, combat result, or lore fact.
- Never contradict requiredFacts, forbiddenClaims, or establishedStoryState.
- establishedStoryState is authoritative runtime continuity at the start of this chapter.
- If a character presence is MISSING or UNKNOWN, choices may search for evidence or discuss that person but MUST NOT address them, ask them to act, or assume they are physically present.
- If a character is not PARTY_MEMBER, do not give them Party/gameplay authority.
- Do not rewrite manuscript prose.

CLASSIFICATION:
- LINEAR: no useful player decision after this segment.
- INTERACTIVE: there is a natural pause after this segment where Cao Minh can make a small local decision before the next authored segment.
- LOCKED_EVENT: this segment contains or directly anchors mandatory plot/dialogue/revelation. No choices.
- CUTAWAY is reserved for compiler-forced reader-only parallel scenes. Do not output CUTAWAY here unless input visibility is cutaway.

INTERACTION RULES:
- At most 1 INTERACTIVE segment in this chapter. Do not force a choice into every chapter.
- An INTERACTIVE segment MUST have exactly 3 concise Vietnamese choices.
- Each choice is only an INTENT/APPROACH, never a claimed outcome.
- IMPORTANT TIMING: every action, line of dialogue and observation written inside the current segment has ALREADY happened before the choice appears. Never offer a choice that repeats, redoes or "decides" an action already completed in that segment.
- pauseAnchor is the END of that segment and represents the exact physical/conversational state when choices appear. Ground choices in pauseAnchor. An object/place mentioned earlier in the segment is NOT available if the characters have moved away from it by pauseAnchor.
- If the segment ends in the middle of a conversation/action and the next authored segment directly continues it, prefer LINEAR instead of inserting A/B/C into the middle.
- Choice text may use only facts, characters, objects and observations already present in the current segment or earlier segments in this chapter. Never mention a reveal, destination, encounter, person, object or result that first appears later.
- Never ask a character about an earlier event, shared history, technical fact or memory unless the manuscript before this choice explicitly establishes that they know it.
- Never introduce a new theory or interpretation as a choice unless that theory has already been raised in the manuscript before this pause.
- The three choices must all be plausible at that exact pause and must be able to receive a short local reaction before returning to the exact authored path.
- Write natural, grammatical Vietnamese with correct spelling. Reject awkward, corrupted or nonsensical wording rather than trying to preserve it.
- V1 choices should be observational/conversational/preparatory: observe, inspect, ask, listen, wait, mark, compare, warn, prepare, or focus.
- Do NOT make navigation/outcome choices: no choosing another route, entering/leaving an area, turning back, forcing a door, consuming an item, attacking, forcing a meeting, or forcing a discovery.
- Do not offer "leave", "refuse the plot", "attack an ally", "change destination", "force a meeting", "force a discovery", "call a person who has not been confirmed present", or any choice that would invalidate the next authored segment.
- Prefer LINEAR over a weak, fake, spoiler-prone, or artificial choice.
- Any forcedLockedSegmentId MUST be LOCKED_EVENT.
- interactionGuard is ignored by the compiler. Return it as an empty string.

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
      "interactionGuard":""
    }
  ]
}

For every mode, interactionGuard must be "". The compiler supplies its own deterministic guard for INTERACTIVE.
Every input segment must appear exactly once and in the same order.

INPUT:
""" + json.dumps(payload, ensure_ascii=False, indent=2)


def choice_changes_authored_path(text):
    value = str(text or "").strip()
    return any(pattern.search(value) for pattern in FORBIDDEN_CHOICE_PATTERNS)


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
        guard = ""

        if mode == "INTERACTIVE":
            if interactive_count >= MAX_INTERACTIVE_PER_CHAPTER:
                mode = "LINEAR"
            elif len(choices) != 3:
                mode = "LINEAR"

        clean_choices = []
        if mode == "INTERACTIVE":
            seen = set()
            for choice in choices:
                if not isinstance(choice, dict):
                    clean_choices = []
                    break
                text = str(choice.get("text", "") or "").strip()
                normalized = re.sub(r"\\s+", " ", text).casefold()
                if (not text or len(text) > 180 or normalized in seen
                        or normalized in {"tiếp tục cốt truyện", "continue story"}
                        or choice_changes_authored_path(text)
                        or choice_addresses_unavailable_character(
                            text, established_story_state)):
                    clean_choices = []
                    break
                seen.add(normalized)
                clean_choices.append({"text": text, "action": text})
            if len(clean_choices) != 3:
                mode = "LINEAR"

        if mode == "INTERACTIVE":
            interactive_count += 1
            guard = STANDARD_INTERACTION_GUARD
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


def compile_safe_linear(segments, forced_locked):
    return {
        segment["id"]: {
            "mode": "LOCKED_EVENT" if index in forced_locked else "LINEAR",
            "choices": [],
            "interactionGuard": "",
        }
        for index, segment in enumerate(segments)
    }


def compile_model_interactions(
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


def build_review_prompt(chapter, segments, compiled, established_story_state):
    candidates = []
    for index, segment in enumerate(segments):
        spec = compiled.get(segment["id"]) or {}
        if spec.get("mode") != "INTERACTIVE":
            continue
        candidates.append({
            "id": segment["id"],
            "currentSegment": segment["text"],
            "pauseAnchor": segment["text"][-900:],
            "previousSegment": segments[index - 1]["text"] if index > 0 else "",
            "nextSegment": segments[index + 1]["text"] if index + 1 < len(segments) else "",
            "choices": spec.get("choices") or [],
        })

    payload = {
        "chapter": {
            "id": chapter.get("id"),
            "title": chapter.get("title"),
            "thread": chapter.get("thread"),
            "visibility": chapter.get("visibility"),
            "requiredFacts": chapter.get("requiredFacts") or [],
            "forbiddenClaims": chapter.get("forbiddenClaims") or [],
        },
        "establishedStoryState": established_story_state,
        "allChapterSegments": segments,
        "interactiveCandidates": candidates,
    }
    return """You are the strict QA reviewer for BACKROOMsV2 Story Compiler v1.
Review ONLY the candidate A/B/C choices. The manuscript is authoritative.

CRITICAL TIMING:
- A choice appears AFTER currentSegment is fully completed and BEFORE nextSegment begins.
- pauseAnchor is the end of currentSegment and is the authoritative physical/conversational state at the decision point.
- Ground every retained/rewritten choice in pauseAnchor plus stable facts already established. Do NOT send characters back to an object, room, liquid, door, device, voice or clue that occurred earlier in currentSegment if pauseAnchor shows they have already moved on.
- Anything already done, said, observed, tested, decided or called in currentSegment is in the past and MUST NOT be offered again.
- If currentSegment ends mid-conversation or mid-action and nextSegment directly continues that same exchange/action, use LINEAR rather than inserting a choice into the middle.
- nextSegment is visible to you ONLY to check convergence. A choice MUST NOT leak or assume facts that first appear in nextSegment or later.

REVIEWER ROLE:
- You are QA, not a second story designer. The first compiler has already selected this as a candidate pause.
- Do NOT downgrade merely because nextSegment continues the story. A short local reaction is allowed between currentSegment and nextSegment.
- First try to REWRITE all three choices into safe micro-intents grounded in the current pause.
- Use LINEAR only when there is genuinely no way to offer three distinct, natural, local approaches without inventing facts or changing authored progression.
- It is acceptable for choices to inspect, listen, compare, ask a present character about something they demonstrably know, mark a detail, prepare, or briefly observe.
- The three choices do not need different outcomes. They only need different local approaches before convergence.

A candidate is INVALID unless rewritten or downgraded when a choice:
- repeats or redoes an action already completed in currentSegment;
- invents or assumes an object, liquid, sign, route, person, mechanism, injury, sound, ability or fact not actually available at that pause;
- assumes a character knows something the manuscript has not established they know;
- gives a weapon/item an unsupported sensing or reasoning capability;
- introduces a new theory, interpretation, diagnosis or lore claim not already raised before the pause;
- changes route, timing, destination, authored event, relationship, Party state, resource outcome, combat outcome or Level progression;
- asks to wait until a condition that would materially change authored pacing is satisfied;
- is awkward, corrupted, misspelled, vague, redundant, or not natural Vietnamese;
- cannot receive a short local reaction and then return unchanged to nextSegment.

Rewrite bad choices whenever a safe rewrite exists. Downgrade to LINEAR only as the last resort.

Return JSON only:
{
  "reviews": [
    {
      "id": "exact candidate segment id",
      "mode": "INTERACTIVE|LINEAR",
      "choices": [
        {"text":"...","action":"..."},
        {"text":"...","action":"..."},
        {"text":"...","action":"..."}
      ]
    }
  ]
}

For LINEAR, choices must be [].
For INTERACTIVE, exactly 3 concise Vietnamese choices are required.
Every interactiveCandidate id must appear exactly once and in the same order.

INPUT:
""" + json.dumps(payload, ensure_ascii=False, indent=2)


def sanitize_review_result(
        compiled, raw_result, established_story_state):
    result = extract_json(raw_result)
    rows = result.get("reviews")
    if not isinstance(rows, list):
        raise CompileError("review output missing reviews array.")

    expected_ids = [
        segment_id for segment_id, spec in compiled.items()
        if spec.get("mode") == "INTERACTIVE"
    ]
    returned_ids = [row.get("id") for row in rows if isinstance(row, dict)]
    if returned_ids != expected_ids:
        raise CompileError("review segment IDs/order do not match interactive candidates.")

    reviewed = json.loads(json.dumps(compiled, ensure_ascii=False))
    for row in rows:
        segment_id = row["id"]
        mode = str(row.get("mode", "LINEAR")).strip().upper()
        if mode != "INTERACTIVE":
            reviewed[segment_id] = {
                "mode": "LINEAR",
                "choices": [],
                "interactionGuard": "",
            }
            continue

        choices = row.get("choices") if isinstance(row.get("choices"), list) else []
        clean_choices = []
        seen = set()
        if len(choices) == 3:
            for choice in choices:
                if not isinstance(choice, dict):
                    clean_choices = []
                    break
                text = str(choice.get("text", "") or "").strip()
                normalized = re.sub(r"\\s+", " ", text).casefold()
                if (not text or len(text) > 180 or normalized in seen
                        or normalized in {"tiếp tục cốt truyện", "continue story"}
                        or choice_changes_authored_path(text)
                        or choice_addresses_unavailable_character(
                            text, established_story_state)):
                    clean_choices = []
                    break
                seen.add(normalized)
                clean_choices.append({"text": text, "action": text})

        if len(clean_choices) != 3:
            reviewed[segment_id] = {
                "mode": "LINEAR",
                "choices": [],
                "interactionGuard": "",
            }
        else:
            reviewed[segment_id] = {
                "mode": "INTERACTIVE",
                "choices": clean_choices,
                "interactionGuard": STANDARD_INTERACTION_GUARD,
            }
    return reviewed


def downgrade_interactions_to_linear(compiled):
    safe = json.loads(json.dumps(compiled, ensure_ascii=False))
    for segment_id, spec in safe.items():
        if spec.get("mode") == "INTERACTIVE":
            safe[segment_id] = {
                "mode": "LINEAR",
                "choices": [],
                "interactionGuard": "",
            }
    return safe


def review_compiled_interactions(
        chapter, segments, compiled, established_story_state):
    interactive_ids = [
        segment_id for segment_id, spec in compiled.items()
        if spec.get("mode") == "INTERACTIVE"
    ]
    if not interactive_ids:
        return compiled, "no-review-needed"

    prompt = build_review_prompt(
        chapter, segments, compiled, established_story_state)
    strict_suffix = (
        "\n\nSTRICT RETRY: Return one valid JSON object only. "
        "No markdown, commentary, preface or suffix. Preserve candidate ids exactly."
    )
    errors = []
    for attempt in range(2):
        try:
            raw, provider = generate(prompt if attempt == 0 else prompt + strict_suffix)
            reviewed = sanitize_review_result(
                compiled, raw, established_story_state)
            return reviewed, provider + "-review"
        except Exception as exc:
            errors.append(str(exc))

    print(
        f"[story-compiler] WARNING {chapter.get('id', '')}: QA review failed; "
        f"downgrading all interactive candidates to LINEAR. {' | '.join(errors)}",
        file=sys.stderr,
    )
    return downgrade_interactions_to_linear(compiled), "deterministic-review-fallback"


def build_final_audit_prompt(chapter, segments, compiled, established_story_state):
    candidates = []
    for index, segment in enumerate(segments):
        spec = compiled.get(segment["id"]) or {}
        if spec.get("mode") != "INTERACTIVE":
            continue
        candidates.append({
            "id": segment["id"],
            "pauseAnchor": segment["text"][-900:],
            "currentSegment": segment["text"],
            "nextSegment": segments[index + 1]["text"] if index + 1 < len(segments) else "",
            "choices": spec.get("choices") or [],
        })
    payload = {
        "chapter": {
            "id": chapter.get("id"),
            "title": chapter.get("title"),
            "thread": chapter.get("thread"),
            "visibility": chapter.get("visibility"),
            "requiredFacts": chapter.get("requiredFacts") or [],
            "forbiddenClaims": chapter.get("forbiddenClaims") or [],
        },
        "establishedStoryState": established_story_state,
        "candidates": candidates,
    }
    return """You are the FINAL INDEPENDENT SAFETY AUDITOR for BACKROOMsV2 Story Compiler v1.

You are NOT allowed to rewrite, repair, improve, paraphrase, or replace any choice.
For each candidate, output only KEEP or LINEAR.

KEEP only if ALL three existing choices are clearly valid at the exact pause represented by pauseAnchor.
If even ONE choice is questionable, output LINEAR.

A choice is invalid if it:
- repeats or preempts an action/dialogue/test that already happened in currentSegment or is immediately authored in nextSegment;
- requires moving back to an earlier room/object/clue that is no longer present at pauseAnchor;
- invents or assumes an object, phenomenon, capability, terminology, history, knowledge, location, relationship, or fact not established before the pause;
- assumes a character knows Backrooms terminology or prior Backrooms experience not established in the manuscript;
- gives a cultivation system, weapon, skill or character an unsupported sensing/mechanical ability;
- changes route, destination, pacing, authored event, Party state, resource outcome, combat result, Level progression, or mandatory dialogue;
- is awkward, corrupted, vague, semantically wrong, or unnatural Vietnamese;
- cannot receive one short local reaction and then continue nextSegment unchanged.

Do not be generous. False positives are worse than fewer interactions.
Do not reject merely because all three choices converge to the same authored path; convergence is required.

Return JSON only:
{
  "audits": [
    {"id":"exact candidate id","verdict":"KEEP|LINEAR"}
  ]
}
Every candidate id must appear exactly once and in the same order.

INPUT:
""" + json.dumps(payload, ensure_ascii=False, indent=2)


def sanitize_final_audit(compiled, raw_result):
    result = extract_json(raw_result)
    rows = result.get("audits")
    if not isinstance(rows, list):
        raise CompileError("final audit output missing audits array.")

    expected_ids = [
        segment_id for segment_id, spec in compiled.items()
        if spec.get("mode") == "INTERACTIVE"
    ]
    returned_ids = [row.get("id") for row in rows if isinstance(row, dict)]
    if returned_ids != expected_ids:
        raise CompileError("final audit ids/order do not match candidates.")

    audited = json.loads(json.dumps(compiled, ensure_ascii=False))
    for row in rows:
        segment_id = row["id"]
        verdict = str(row.get("verdict", "LINEAR")).strip().upper()
        if verdict != "KEEP":
            audited[segment_id] = {
                "mode": "LINEAR",
                "choices": [],
                "interactionGuard": "",
            }
    return audited


def final_audit_compiled_interactions(
        chapter, segments, compiled, established_story_state):
    if not any(spec.get("mode") == "INTERACTIVE" for spec in compiled.values()):
        return compiled, "no-final-audit-needed"

    prompt = build_final_audit_prompt(
        chapter, segments, compiled, established_story_state)
    errors = []

    # Use a different model family first so the final gate is not merely
    # asking the generator to approve its own homework.
    try:
        raw = call_gemini(prompt)
        return sanitize_final_audit(compiled, raw), "gemini-final-audit"
    except Exception as exc:
        errors.append(f"Gemini: {exc}")

    try:
        raw, provider = generate(prompt)
        return sanitize_final_audit(compiled, raw), provider + "-final-audit"
    except Exception as exc:
        errors.append(f"fallback: {exc}")

    print(
        f"[story-compiler] WARNING {chapter.get('id', '')}: final audit failed; "
        f"downgrading interactions to LINEAR. {' | '.join(errors)}",
        file=sys.stderr,
    )
    return downgrade_interactions_to_linear(compiled), "deterministic-final-audit-fallback"


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
        compiled, provider = compile_model_interactions(
            chapter, segments, forced_locked, prompt, established_story_state)
        compiled, review_provider = review_compiled_interactions(
            chapter, segments, compiled, established_story_state)
        compiled, audit_provider = final_audit_compiled_interactions(
            chapter, segments, compiled, established_story_state)
        provider = provider + "+" + review_provider + "+" + audit_provider

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
        "generatedBy": "story-compiler-v1",
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
    if generated.get("compilerFingerprint") != compiler_fingerprint():
        raise CompileError("Generated interactions were produced by a different compiler revision.")
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
    story_state_snapshots = build_story_state_snapshots(metadata)

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
                    if not isinstance(choice, dict) or not str(choice.get("text", "")).strip():
                        raise CompileError(f"{segment_id}: invalid interaction choice.")
                    if str(choice.get("action", "")).strip() != str(choice.get("text", "")).strip():
                        raise CompileError(f"{segment_id}: action must equal visible Vietnamese choice text.")
                    if choice_changes_authored_path(str(choice.get("text", ""))):
                        raise CompileError(f"{segment_id}: route/outcome-changing choice is forbidden in compiler v1.")
                    if choice_addresses_unavailable_character(
                            str(choice.get("text", "")),
                            story_state_snapshots.get(chapter_id, {})):
                        raise CompileError(
                            f"{segment_id}: choice directly addresses a missing/unknown character.")
                if guard != STANDARD_INTERACTION_GUARD:
                    raise CompileError(f"{segment_id}: interaction guard must be compiler-owned.")
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

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
METADATA_PATH = STORY_ROOT / "generated/level_0/level0.story.json"  # legacy fixture only
OUTPUT_PATH = STORY_ROOT / "generated/level_0/level0.interactions.json"  # legacy fixture only
SOURCE_CATALOG_PATH = STORY_ROOT / "source/story_catalog.source.json"
GENERATED_CATALOG_PATH = STORY_ROOT / "generated/story_catalog.json"

SCHEMA_VERSION = 3
COMPILER_VERSION = 3
ARTIFACT_SCHEMA_VERSION = 2
INTERACTION_SCHEMA_VERSION = 4
COMPILER_SEMANTICS = "multi-arc-v2-content-segments-v1-offline-decisions-v1-events-v1"
VALID_MODES = {"LINEAR", "DECISION", "CUTAWAY", "ENTITY_GATE"}
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
    if text.startswith("\ufeff"):
        text = text[1:]
    return text.replace("\r\n", "\n").replace("\r", "\n").strip() + "\n"


def source_digest(text):
    return hashlib.sha256(canonical_source(text).encode("utf-8")).hexdigest()


def split_markdown(markdown, target_chars, max_chars):
    source = canonical_source(markdown).strip()
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
    # Artifact compatibility is semantic. An implementation-only refactor must
    # not invalidate every installed story pack.
    return hashlib.sha256(COMPILER_SEMANTICS.encode("utf-8")).hexdigest()


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
- The player controls Cao Minh. canonChoiceText must be something Cao Minh chooses/does, never dialogue or an action belonging to Lục Trầm, Nam, or another character.
- If currentSegment already contains Cao Minh making that decision, stating that intention, agreeing to it, or effectively committing to it, classify LINEAR instead of repeating it as a choice.
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


def authored_entity_gate_map(chapter):
    gates = {}
    for raw in chapter.get("authoredEntityEncounters") or []:
        try:
            segment_index = int((raw or {}).get("segmentIndex", 0))
        except Exception:
            segment_index = 0
        entity_key = str((raw or {}).get("entityKey", "")).strip().lower()
        if segment_index > 0 and entity_key:
            gates[segment_index - 1] = entity_key
    return gates


def compile_player_turns(chapter, segments):
    gates = authored_entity_gate_map(chapter)
    authored = {}
    for raw in chapter.get("offlineDecisions") or []:
        anchor = str(raw.get("anchor", ""))
        matches = [i for i, segment in enumerate(segments) if anchor and anchor in segment["text"]]
        index = matches[0] if len(matches) == 1 else -1
        variants = raw.get("variants")
        if (index < 0 or sum(segment["text"].count(anchor) for segment in segments) != 1
                or index in authored or not isinstance(variants, list) or len(variants) < 3
                or (index == len(segments) - 1 and not chapter.get("nextChapter"))):
            raise CompileError(chapter["id"] + ": invalid offline decision position/variants")
        for variant in variants:
            if not isinstance(variant, dict) or set(variant) != {"canon", "trap", "converge"}:
                raise CompileError(chapter["id"] + ": invalid offline decision fields")
            if not isinstance(variant["canon"], str) or not variant["canon"].strip():
                raise CompileError(chapter["id"] + ": missing canon choice")
            for key in ("trap", "converge"):
                value = variant[key]
                if not isinstance(value, dict) or set(value) != {"text", "reply"} or not all(
                        isinstance(v, str) and v.strip() for v in value.values()):
                    raise CompileError(chapter["id"] + ": incomplete " + key + " choice")
                if any(word in value["reply"].lower() for word in ("chọn sai", "reset", "checkpoint")):
                    raise CompileError(chapter["id"] + ": player-facing reply reveals the loop")
            if len({variant["canon"], variant["trap"]["text"], variant["converge"]["text"]}) != 3:
                raise CompileError(chapter["id"] + ": offline choices must be distinct")
        authored[index] = variants
    output = {}
    for index, segment in enumerate(segments):
        entity_key = gates.get(index, "")
        if entity_key:
            output[segment["id"]] = {
                "mode": "ENTITY_GATE",
                "decisionContract": {
                    "entityKey": entity_key,
                    "attackText": "Tấn công",
                    "loopAnchor": segment["id"],
                },
            }
            continue

        if index in authored:
            output[segment["id"]] = {
                "mode": "DECISION",
                "decisionContract": {
                    "loopAnchor": segment["id"],
                    "decisionGuard": STANDARD_INTERACTION_GUARD,
                    "offlineVariants": authored[index],
                },
            }
        else:
            output[segment["id"]] = {
                "mode": "LINEAR",
                "decisionContract": {},
            }
    return output


def compile_safe_linear(segments, forced_locked):
    # Legacy helper kept for compatibility with old call sites. Player-visible
    # compilation no longer uses it: turn decisions are deterministic.
    return {
        segment["id"]: {
            "mode": "LINEAR",
            "decisionContract": {},
        }
        for segment in segments
    }


def compile_model_decisions(
        chapter, segments, forced_locked, prompt, established_story_state):
    errors = []
    strict_suffix = (
        "\n\nSTRICT RETRY: Your previous response was unusable. Return one valid JSON object only. "
        "No markdown fence, no commentary, no preface, no suffix. Preserve every segment id exactly."
    )

    # Gemini owns the primary lane. Each call rotates through keys 1..5.
    # Haiku is intentionally the final provider and is never followed by Gemini again.
    for attempt in range(2):
        retry_prompt = prompt if attempt == 0 else prompt + strict_suffix
        try:
            raw = call_gemini(retry_prompt)
            return sanitize_model_chapter(
                chapter, segments, raw, forced_locked, established_story_state), "gemini"
        except Exception as exc:
            errors.append(f"Gemini pass {attempt + 1}: {exc}")

    try:
        raw = call_haiku(prompt + strict_suffix)
        return sanitize_model_chapter(
            chapter, segments, raw, forced_locked, established_story_state), "haiku"
    except Exception as exc:
        errors.append(f"Haiku final fallback: {exc}")

    chapter_id = chapter.get("id", "")
    print(
        f"[story-compiler] WARNING {chapter_id}: model output remained invalid; "
        f"falling back to safe LINEAR/LOCKED_EVENT. {' | '.join(errors)}",
        file=sys.stderr,
    )
    return compile_safe_linear(segments, forced_locked), "deterministic-safe-fallback"


def audit_decision_contract(chapter, segments, compiled, established_story_state):
    decision_rows = []
    for index, segment in enumerate(segments):
        spec = compiled.get(segment["id"]) or {}
        if spec.get("mode") != "DECISION":
            continue
        contract = spec.get("decisionContract") or {}
        decision_rows.append({
            "id": segment["id"],
            "currentSegment": segment["text"],
            "pauseAnchor": segment["text"][-900:],
            "nextSegment": segments[index + 1]["text"] if index + 1 < len(segments) else "",
            "canonChoiceText": contract.get("canonChoiceText", ""),
        })

    if not decision_rows:
        return compiled, "no-audit-needed"

    payload = {
        "chapter": {
            "id": chapter.get("id"),
            "title": chapter.get("title"),
            "thread": chapter.get("thread"),
            "visibility": chapter.get("visibility"),
        },
        "establishedStoryState": established_story_state,
        "decisions": decision_rows,
    }
    prompt = """You are the FINAL AUDITOR for BACKROOMsV2 Story Compiler v2.

The player controls Cao Minh. For each proposed DECISION, output KEEP or LINEAR only. You may NOT rewrite the canonChoiceText.

KEEP only when ALL are true:
- canonChoiceText describes an immediate action/intention Cao Minh himself can choose at the END of currentSegment.
- That action has NOT already happened, been stated, agreed, decided, or effectively committed to in currentSegment.
- nextSegment actually begins by carrying out that Cao Minh action/intention, or a direct shared action initiated by him.
- The text does not steal dialogue/action that belongs to Lục Trầm, Nam, or another character.
- The text does not reveal the result/consequence of nextSegment.
- The text does not add lore, knowledge, route facts, objects, capabilities, or outcomes not already available at the pause.
- The decision sits at a genuine pause, not in the middle of an unfinished exchange.

Use LINEAR if any criterion fails. Prefer fewer clean decisions over fake interactivity.

Return JSON only:
{"audits":[{"id":"exact id","verdict":"KEEP|LINEAR","reason":"brief concrete reason"}]}

Every decision id must appear exactly once and in input order.

INPUT:
""" + json.dumps(payload, ensure_ascii=False, indent=2)

    try:
        raw, provider = generate(prompt)
        parsed = extract_json(raw)
        rows = parsed.get("audits")
        expected = [row["id"] for row in decision_rows]
        returned = [row.get("id") for row in rows] if isinstance(rows, list) else []
        if returned != expected:
            raise CompileError("decision audit ids/order mismatch")

        audited = json.loads(json.dumps(compiled, ensure_ascii=False))
        for row in rows:
            if str(row.get("verdict", "LINEAR")).strip().upper() != "KEEP":
                audited[row["id"]] = {"mode": "LINEAR", "decisionContract": {}}
        return audited, provider + "-audit"
    except Exception as exc:
        print(
            f"[story-compiler] WARNING {chapter.get('id', '')}: decision audit failed; "
            f"downgrading decision candidates to LINEAR. {exc}",
            file=sys.stderr,
        )
        safe = json.loads(json.dumps(compiled, ensure_ascii=False))
        for row in decision_rows:
            safe[row["id"]] = {"mode": "LINEAR", "decisionContract": {}}
        return safe, "deterministic-audit-fallback"


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

    if chapter.get("visibility") == "cutaway":
        compiled = compile_cutaway(chapter, segments)
        provider = "deterministic-cutaway"
    else:
        compiled = compile_player_turns(chapter, segments)
        provider = "deterministic-turn-loop-v3"

    decision_count = sum(1 for item in compiled.values() if item["mode"] == "DECISION")
    entity_gate_count = sum(1 for item in compiled.values() if item["mode"] == "ENTITY_GATE")
    return {
        "chapterId": chapter_id,
        "provider": provider,
        "segmentCount": len(segments),
        "decisionCount": decision_count,
        "entityGateCount": entity_gate_count,
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
        "generatedBy": "story-compiler-v3-turn-loop",
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
            f"{item['decisionCount']} decisions, {item['entityGateCount']} entity gates, provider={item['provider']}"
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

        gates = authored_entity_gate_map(chapter)
        for index, segment_id in enumerate(expected_ids):
            spec = segments[segment_id]
            mode = spec.get("mode")
            contract = spec.get("decisionContract")
            if mode not in VALID_MODES:
                raise CompileError(f"{segment_id}: invalid mode {mode}.")
            if not isinstance(contract, dict):
                raise CompileError(f"{segment_id}: decisionContract must be an object.")

            if chapter.get("visibility") == "cutaway":
                if mode != "CUTAWAY" or contract:
                    raise CompileError(f"{segment_id}: cutaway must be decision-free.")
                continue

            if index in gates:
                if mode != "ENTITY_GATE":
                    raise CompileError(f"{segment_id}: authored Entity turn must be ENTITY_GATE.")
                if contract.get("entityKey") != gates[index]:
                    raise CompileError(f"{segment_id}: authored Entity key mismatch.")
                if contract.get("attackText") != "Tấn công":
                    raise CompileError(f"{segment_id}: Entity gate must expose only Tấn công.")
                continue

            has_next_authored_turn = index + 1 < len(expected_ids) or bool(str(chapter.get("nextChapter", "")).strip())
            if has_next_authored_turn:
                if mode != "DECISION":
                    raise CompileError(f"{segment_id}: every player-controlled story turn must be DECISION.")
                if contract.get("loopAnchor") != segment_id:
                    raise CompileError(f"{segment_id}: loopAnchor must equal current story turn.")
                if contract.get("decisionGuard") != STANDARD_INTERACTION_GUARD:
                    raise CompileError(f"{segment_id}: decision guard must be compiler-owned.")
                if "canonChoiceText" in contract:
                    raise CompileError(f"{segment_id}: canon wording is runtime-prefetched in v3.")
            else:
                if mode != "LINEAR" or contract:
                    raise CompileError(f"{segment_id}: terminal authored segment must be LINEAR.")

    print("[story-compiler] generated turn loop validated: every player story turn has a decision or authored Entity gate.")



def atomic_write_json(path, payload):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    temporary.replace(path)


def read_source_catalog():
    if not SOURCE_CATALOG_PATH.is_file():
        raise CompileError("Missing story/source/story_catalog.source.json")
    catalog = json.loads(SOURCE_CATALOG_PATH.read_text(encoding="utf-8"))
    if catalog.get("schemaVersion") != 1:
        raise CompileError("Unsupported source catalog schemaVersion.")
    stories = catalog.get("stories")
    if not isinstance(stories, list):
        raise CompileError("Source catalog stories must be an array.")
    seen_story = set()
    seen_level = set()
    seen_generated = set()
    for entry in stories:
        story_id = str(entry.get("storyId", "")).strip()
        level_key = str(entry.get("levelKey", "")).strip()
        generated_root = str(entry.get("generatedRoot", "")).strip()
        if not story_id or not level_key or not generated_root:
            raise CompileError("Every source catalog entry requires storyId, levelKey and generatedRoot.")
        if story_id in seen_story or level_key in seen_level or generated_root in seen_generated:
            raise CompileError("Duplicate storyId, levelKey or generatedRoot in source catalog.")
        if not generated_root.startswith("story/generated/"):
            raise CompileError("Unsafe generatedRoot: " + generated_root)
        seen_story.add(story_id)
        seen_level.add(level_key)
        seen_generated.add(generated_root)
    return catalog


def source_asset_path(raw):
    raw = str(raw or "").strip()
    if not raw.startswith("story/"):
        raise CompileError("Unsafe story asset path: " + raw)
    return STORY_ROOT / raw[len("story/"):]


def read_arc_source(entry):
    annotation_path = source_asset_path(entry.get("annotations"))
    if not annotation_path.is_file():
        raise CompileError("Missing arc annotations: " + str(entry.get("annotations")))
    arc = json.loads(annotation_path.read_text(encoding="utf-8"))
    for key in ("storyId", "levelKey", "sourceRevision"):
        if str(arc.get(key, "")) != str(entry.get(key, "")):
            raise CompileError("Catalog/annotation identity mismatch for " + key + ": " + str(entry.get("storyId")))
    if arc.get("schemaVersion") != 1:
        raise CompileError("Unsupported arc annotation schemaVersion: " + str(entry.get("storyId")))
    chapters = arc.get("chapters")
    if not isinstance(chapters, list) or not chapters:
        raise CompileError("Arc has no chapters: " + str(entry.get("storyId")))
    start = str(arc.get("startChapter", "")).strip()
    ids = [str(ch.get("id", "")).strip() for ch in chapters]
    if not start or start not in ids or any(not value for value in ids) or len(ids) != len(set(ids)):
        raise CompileError("Invalid or duplicate chapter ids for " + str(entry.get("storyId")))
    sources = [str(ch.get("source", "")).strip() for ch in chapters]
    if len(sources) != len(set(sources)):
        raise CompileError("Duplicate chapter source path for " + str(entry.get("storyId")))
    for chapter in chapters:
        if chapter.get("segmentIndex") is not None or chapter.get("authoredEntityEncounters"):
            raise CompileError("Hand-written segment indexes/entity gates are forbidden; use a unique text anchor.")
        if chapter.get("visibility") not in ("player", "cutaway"):
            raise CompileError("Unsupported visibility for " + str(chapter.get("id")))
        if not str(chapter.get("thread", "")).strip():
            raise CompileError("Missing thread for " + str(chapter.get("id")))
        source = source_asset_path(chapter.get("source"))
        if not source.is_file():
            raise CompileError("Missing manuscript source: " + str(chapter.get("source")))
        for event in chapter.get("events") or []:
            if event.get("segmentIndex") is not None:
                raise CompileError("Hand-written segmentIndex is forbidden in " + str(chapter.get("id")))
            event_type = str(event.get("type", "")).upper()
            if event_type == "LEVEL_TRANSITION":
                raise CompileError("Story events must not own Level destinations.")
            forbidden_keys = {"destination", "targetLevel", "targetLevelKey", "nextLevel", "toLevel"}
            if any(key in event for key in forbidden_keys):
                raise CompileError("Story event carries a forbidden Level destination in " + str(chapter.get("id")))
    return arc


def stable_segments(chapter_id, manuscript, target, maximum):
    blocks = split_markdown(manuscript, target, maximum)
    if not blocks:
        raise CompileError(chapter_id + ": manuscript produced no segments.")
    seen = {}
    segments = []
    for text_value in blocks:
        digest = hashlib.sha256(text_value.encode("utf-8")).hexdigest()[:16]
        occurrence = seen.get(digest, 0) + 1
        seen[digest] = occurrence
        suffix = "" if occurrence == 1 else "_R" + str(occurrence)
        segments.append({"id": chapter_id + "_S" + digest + suffix, "text": text_value})
    return segments


def chapter_title(manuscript, fallback):
    source = canonical_source(manuscript)
    first = source.split("\n", 1)[0].strip()
    if first.startswith("# "):
        return first[2:].strip()
    return fallback


def resolved_events(chapter, manuscript, segments):
    enter = []
    exit_events = []
    after = {}
    canonical = canonical_source(manuscript)
    for raw in chapter.get("events") or []:
        event = dict(raw)
        when = str(event.pop("when", "")).upper()
        anchor = str(event.pop("anchor", ""))
        if when == "ENTER":
            enter.append(event)
        elif when == "EXIT":
            exit_events.append(event)
        elif when == "AFTER_TEXT":
            if not anchor:
                raise CompileError(chapter["id"] + ": AFTER_TEXT requires anchor.")
            count = canonical.count(anchor)
            if count != 1:
                raise CompileError(chapter["id"] + ": AFTER_TEXT anchor must occur exactly once; got " + str(count))
            matches = [item["id"] for item in segments if anchor in item["text"]]
            if len(matches) != 1:
                raise CompileError(chapter["id"] + ": anchor crossed/failed segment resolution.")
            after.setdefault(matches[0], []).append(event)
        else:
            raise CompileError(chapter["id"] + ": unsupported event timing " + when)
    return enter, exit_events, after


def prepare_story(entry):
    arc = read_arc_source(entry)
    source_revision = str(entry["sourceRevision"])
    target = max(800, int(arc.get("segmentTargetChars", 1900)))
    maximum = max(target, int(arc.get("segmentMaxChars", 2400)))
    prepared = []
    raw_chapters = arc["chapters"]
    for index, raw in enumerate(raw_chapters):
        chapter = dict(raw)
        chapter_id = str(chapter["id"])
        manuscript_path = source_asset_path(chapter["source"])
        manuscript = manuscript_path.read_text(encoding="utf-8")
        segments = stable_segments(chapter_id, manuscript, target, maximum)
        enter, exit_events, after = resolved_events(chapter, manuscript, segments)
        next_id = str(raw_chapters[index + 1]["id"]) if index + 1 < len(raw_chapters) else ""
        runtime = {
            "id": chapter_id,
            "title": chapter_title(manuscript, chapter_id),
            "source": chapter["source"],
            "thread": chapter["thread"],
            "visibility": chapter["visibility"],
            "nextChapter": next_id,
            "eventsOnEnter": enter,
            "eventsOnExit": exit_events,
            "eventsAfterSegment": after,
            "requiredFacts": chapter.get("requiredFacts") or [],
            "forbiddenClaims": chapter.get("forbiddenClaims") or [],
            "offlineDecisions": chapter.get("offlineDecisions") or [],
        }
        prepared.append({
            "runtime": runtime,
            "manuscript": manuscript,
            "segments": segments,
            "sourceDigest": source_digest(manuscript),
        })
    return arc, prepared, target, maximum


def compile_prepared_chapter(entry, prepared):
    chapter = prepared["runtime"]
    segments = prepared["segments"]
    if chapter.get("visibility") == "cutaway":
        compiled = compile_cutaway(chapter, segments)
        provider = "deterministic-cutaway"
    else:
        compiled = compile_player_turns(chapter, segments)
        provider = "deterministic-turn-loop-v3"
    interaction = {
        "schemaVersion": INTERACTION_SCHEMA_VERSION,
        "storyId": entry["storyId"],
        "levelKey": entry["levelKey"],
        "sourceRevision": entry["sourceRevision"],
        "compilerFingerprint": compiler_fingerprint(),
        "chapterId": chapter["id"],
        "source": chapter["source"],
        "sourceDigest": prepared["sourceDigest"],
        "segmentIds": [segment["id"] for segment in segments],
        "provider": provider,
        "segments": compiled,
    }
    metadata = {
        "schemaVersion": ARTIFACT_SCHEMA_VERSION,
        "storyId": entry["storyId"],
        "levelKey": entry["levelKey"],
        "sourceRevision": entry["sourceRevision"],
        "compilerFingerprint": compiler_fingerprint(),
        **chapter,
        "sourceDigest": prepared["sourceDigest"],
        "segmentIds": [segment["id"] for segment in segments],
    }
    return metadata, interaction


def generated_paths(entry, chapter_id):
    root = source_asset_path(entry["generatedRoot"])
    return (
        root / "arc.json",
        root / "chapters" / (chapter_id + ".json"),
        root / "interactions" / (chapter_id + ".json"),
    )


def write_generated_catalog(catalog):
    levels = {}
    for entry in catalog["stories"]:
        levels[str(entry["levelKey"])] = {
            "storyId": entry["storyId"],
            "metadata": entry["generatedRoot"] + "/arc.json",
        }
    atomic_write_json(GENERATED_CATALOG_PATH, {
        "schemaVersion": ARTIFACT_SCHEMA_VERSION,
        "compilerFingerprint": compiler_fingerprint(),
        "levels": levels,
    })


def write_arc_index(entry, arc, prepared):
    chapters = []
    for item in prepared:
        chapter = item["runtime"]
        chapters.append({
            "id": chapter["id"],
            "metadata": entry["generatedRoot"] + "/chapters/" + chapter["id"] + ".json",
            "interactions": entry["generatedRoot"] + "/interactions/" + chapter["id"] + ".json",
            "source": chapter["source"],
            "thread": chapter["thread"],
            "visibility": chapter["visibility"],
            "nextChapter": chapter["nextChapter"],
        })
    arc_path = source_asset_path(entry["generatedRoot"]) / "arc.json"
    atomic_write_json(arc_path, {
        "schemaVersion": ARTIFACT_SCHEMA_VERSION,
        "storyId": entry["storyId"],
        "levelKey": entry["levelKey"],
        "sourceRevision": entry["sourceRevision"],
        "compilerFingerprint": compiler_fingerprint(),
        "startChapter": arc["startChapter"],
        "chapterCount": len(chapters),
        "chapters": chapters,
    })


def impacted_selection(catalog, story_ids, changed_paths, force_all):
    entries = {entry["storyId"]: entry for entry in catalog["stories"]}
    if story_ids:
        unknown = sorted(set(story_ids) - set(entries))
        if unknown:
            raise CompileError("Unknown storyId(s): " + ", ".join(unknown))
        return {story_id: None for story_id in story_ids}
    if force_all or not changed_paths:
        return {story_id: None for story_id in entries}

    normalized = {path.replace("\\", "/").lstrip("./") for path in changed_paths if path.strip()}
    wide_prefixes = (
        ".github/scripts/compile-story.py",
        "android-apk/app/src/main/assets/story/source/schema/",
        "android-apk/app/src/main/assets/story/source/story_catalog.source.json",
    )
    if any(path == wide_prefixes[0] or path.startswith(wide_prefixes[1]) or path == wide_prefixes[2]
           for path in normalized):
        return {story_id: None for story_id in entries}

    selected = {}
    for story_id, entry in entries.items():
        annotation_repo = "android-apk/app/src/main/assets/" + entry["annotations"]
        source_root_repo = "android-apk/app/src/main/assets/" + entry["sourceRoot"].rstrip("/") + "/"
        if annotation_repo in normalized:
            selected[story_id] = None
            continue
        chapter_ids = set()
        arc = read_arc_source(entry)
        source_to_id = {
            "android-apk/app/src/main/assets/" + str(ch["source"]): str(ch["id"])
            for ch in arc["chapters"]
        }
        for path in normalized:
            if path in source_to_id:
                chapter_ids.add(source_to_id[path])
            elif path.startswith(source_root_repo):
                selected[story_id] = None
                chapter_ids.clear()
                break
        if story_id not in selected and chapter_ids:
            selected[story_id] = chapter_ids
    return selected


def compile_v2(selection, catalog):
    write_generated_catalog(catalog)
    entries = {entry["storyId"]: entry for entry in catalog["stories"]}
    for story_id, chapter_filter in selection.items():
        entry = entries[story_id]
        arc, prepared, _, _ = prepare_story(entry)
        write_arc_index(entry, arc, prepared)
        selected = prepared if chapter_filter is None else [
            item for item in prepared if item["runtime"]["id"] in chapter_filter
        ]
        max_workers = max(1, min(4, int(os.environ.get("STORY_COMPILER_WORKERS", "4"))))
        with concurrent.futures.ThreadPoolExecutor(max_workers=max_workers) as executor:
            futures = {
                item["runtime"]["id"]: executor.submit(compile_prepared_chapter, entry, item)
                for item in selected
            }
            by_id = {chapter_id: future.result() for chapter_id, future in futures.items()}
        for item in selected:
            chapter_id = item["runtime"]["id"]
            metadata, interaction = by_id[chapter_id]
            _, metadata_path, interaction_path = generated_paths(entry, chapter_id)
            atomic_write_json(metadata_path, metadata)
            atomic_write_json(interaction_path, interaction)
            print("[story-compiler] {0}/{1}: {2} stable segments".format(
                story_id, chapter_id, len(item["segments"])))


def validate_v2(selection, catalog):
    generated_catalog = json.loads(GENERATED_CATALOG_PATH.read_text(encoding="utf-8"))
    if generated_catalog.get("schemaVersion") != ARTIFACT_SCHEMA_VERSION:
        raise CompileError("Generated catalog schemaVersion mismatch.")
    if generated_catalog.get("compilerFingerprint") != compiler_fingerprint():
        raise CompileError("Generated catalog compilerFingerprint mismatch.")
    entries = {entry["storyId"]: entry for entry in catalog["stories"]}
    for entry in catalog["stories"]:
        mapped = (generated_catalog.get("levels") or {}).get(str(entry["levelKey"])) or {}
        if mapped.get("storyId") != entry["storyId"] or mapped.get("metadata") != entry["generatedRoot"] + "/arc.json":
            raise CompileError("Generated catalog mapping mismatch for level " + str(entry["levelKey"]))

    for story_id, chapter_filter in selection.items():
        entry = entries[story_id]
        arc, prepared, _, _ = prepare_story(entry)
        arc_path = source_asset_path(entry["generatedRoot"]) / "arc.json"
        generated_arc = json.loads(arc_path.read_text(encoding="utf-8"))
        if generated_arc.get("storyId") != story_id or generated_arc.get("levelKey") != entry["levelKey"]:
            raise CompileError(story_id + ": generated arc identity mismatch.")
        if generated_arc.get("sourceRevision") != entry["sourceRevision"]:
            raise CompileError(story_id + ": generated arc sourceRevision mismatch.")
        expected_order = [item["runtime"]["id"] for item in prepared]
        actual_order = [str(ch.get("id")) for ch in generated_arc.get("chapters") or []]
        if expected_order != actual_order:
            raise CompileError(story_id + ": generated arc chapter order mismatch.")
        selected = prepared if chapter_filter is None else [
            item for item in prepared if item["runtime"]["id"] in chapter_filter
        ]
        for item in selected:
            chapter_id = item["runtime"]["id"]
            _, metadata_path, interaction_path = generated_paths(entry, chapter_id)
            if not metadata_path.is_file() or not interaction_path.is_file():
                raise CompileError(story_id + "/" + chapter_id + ": missing generated artifact.")
            metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
            interaction = json.loads(interaction_path.read_text(encoding="utf-8"))
            expected_ids = [segment["id"] for segment in item["segments"]]
            for payload, label, schema in (
                    (metadata, "metadata", ARTIFACT_SCHEMA_VERSION),
                    (interaction, "interactions", INTERACTION_SCHEMA_VERSION)):
                if payload.get("schemaVersion") != schema:
                    raise CompileError(chapter_id + ": " + label + " schemaVersion mismatch.")
                if payload.get("sourceRevision") != entry["sourceRevision"]:
                    raise CompileError(chapter_id + ": " + label + " sourceRevision mismatch.")
                if payload.get("sourceDigest") != item["sourceDigest"]:
                    raise CompileError(chapter_id + ": " + label + " sourceDigest mismatch.")
                if payload.get("compilerFingerprint") != compiler_fingerprint():
                    raise CompileError(chapter_id + ": " + label + " compilerFingerprint mismatch.")
                if payload.get("segmentIds") != expected_ids:
                    raise CompileError(chapter_id + ": " + label + " segment set/order mismatch.")
            segments = interaction.get("segments")
            if not isinstance(segments, dict) or list(segments.keys()) != expected_ids:
                raise CompileError(chapter_id + ": interaction segment order mismatch.")
            for segment_id in expected_ids:
                spec = segments[segment_id]
                if spec.get("mode") not in VALID_MODES or not isinstance(spec.get("decisionContract"), dict):
                    raise CompileError(segment_id + ": invalid interaction contract.")
    print("[story-compiler] generated multi-arc artifacts validated.")


def load_changed_paths(args):
    values = list(args.changed_path or [])
    if args.changed_file_list:
        path = Path(args.changed_file_list)
        if path.is_file():
            values.extend(line.strip() for line in path.read_text(encoding="utf-8").splitlines())
    return [value for value in values if value]


def main():
    parser = argparse.ArgumentParser(description="Compile every authored story arc through one generic compiler.")
    parser.add_argument("--story-id", action="append", default=[])
    parser.add_argument("--changed-path", action="append", default=[])
    parser.add_argument("--changed-file-list")
    parser.add_argument("--all", action="store_true")
    parser.add_argument("--validate-generated", action="store_true")
    args = parser.parse_args()
    try:
        catalog = read_source_catalog()
        changed = load_changed_paths(args)
        selection = impacted_selection(catalog, args.story_id, changed, args.all)
        if not selection:
            print("[story-compiler] no authored story affected by this change set.")
            return 0
        if args.validate_generated:
            validate_v2(selection, catalog)
        else:
            compile_v2(selection, catalog)
            validate_v2(selection, catalog)
    except Exception as exc:
        print("[story-compiler] ERROR: " + str(exc), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

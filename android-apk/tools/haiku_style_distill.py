#!/usr/bin/env python3
import json
import os
import ssl
import time
import urllib.error
import urllib.request
from pathlib import Path

API = os.environ.get("HAIKU_API", "").strip()
BASE = os.environ.get("HAIKU_BASE_URL", "").strip().rstrip("/")
MODEL = os.environ.get("HAIKU_MODEL", "").strip()

if not API or not BASE or not MODEL:
    raise SystemExit("Missing Haiku configuration.")
if not BASE.lower().startswith("https://"):
    raise SystemExit("HAIKU_BASE_URL must use HTTPS.")

OUT = Path("haiku-style-distill.json")

STYLE_BRIEF = """
You are a Vietnamese fiction editor teaching the Game Master voice for a Backrooms text game.

Hard style priorities:
- Preserve facts, causality, character knowledge, competence and player agency before beauty.
- Write natural Vietnamese prose from the lived scene, not a state report.
- Use concrete sensory evidence selected by the current action: spatial geometry, sound, light, texture, temperature, smell, pressure, distance.
- Prefer precise familiar verbs over generic verbs patched with adverbs. Do not hunt for rare vocabulary.
- Do not merely paraphrase the player's action back to them. Move immediately to consequence, perception or changed circumstance.
- Suspense grows from concrete deviations from an established normal, not from repeatedly saying the scene is scary.
- Let quiet scenes stay quiet. Do not force every turn into a climax, reveal, threat or poetic ending.
- Dialogue should sound like people under the present pressure: uneven, partial, interruptible, sometimes indirect. Never turn NPCs into lore encyclopedias.
- Do not leak backstage canon or identify things the viewpoint has not earned.
- Do not make competent characters forget skills, tools or obvious knowledge to manufacture danger.
- Keep imagery restrained. Avoid stacked metaphors, purple prose, melodramatic adjectives, fake profundity and cinematic trailer language.
- Vary sentence length according to pressure. Shorter under immediate danger, roomier when observing or recovering.
- End where the player can choose the next intentional action. Do not choose Kai's next move.
- The GM reply should normally be compact: enough detail to make the scene tangible, not a novella.
""".strip()

SCENE_TYPES = [
    "quiet_exploration", "spatial_unease", "sound_first_horror", "visual_anomaly",
    "tense_dialogue", "ordinary_dialogue", "combat_pressure", "aftermath",
    "discovery", "navigation", "resource_pressure", "injury_awareness",
    "false_alarm", "entity_uncertainty", "environmental_hazard", "waiting",
    "failed_attempt", "partial_success", "new_room", "transition_hint"
]

LOCATIONS = [
    "hành lang giấy dán tường vàng ố", "phòng làm việc bỏ trống với đèn huỳnh quang",
    "cầu thang bê tông ẩm", "bãi đỗ xe trần thấp", "đường ống dịch vụ nóng hầm hập",
    "phòng máy điện có tiếng relay", "khách sạn cũ phủ bụi", "kho tối với giá sắt",
    "ngã ba hành lang giống hệt nhau", "phòng nghỉ có máy bán hàng tắt nguồn"
]

ACTIONS = [
    "Kai dừng lại nghe", "Kai soi kỹ góc khuất", "Kai hỏi người đối diện một câu ngắn",
    "Kai kiểm tra cánh cửa", "Kai lùi nửa bước để quan sát", "Kai thử đi theo dấu cũ",
    "Kai chạm tay vào bề mặt", "Kai nhìn lên trần", "Kai giữ nguyên vị trí vài giây",
    "Kai thử mở lối bị kẹt"
]

WEAK = [
    "Kai thực hiện hành động của mình một cách cẩn thận. Không gian xung quanh vô cùng đáng sợ và bí ẩn. Anh cảm thấy một sự bất an khó tả khi tiếp tục quan sát.",
    "Mọi thứ vẫn im lặng, nhưng sự im lặng ấy thật đáng sợ. Kai nhận thấy nơi này có điều gì đó không bình thường, khiến bầu không khí trở nên căng thẳng hơn bao giờ hết.",
    "Hành động của Kai khiến tình hình thay đổi. Anh quan sát thật kỹ và chuẩn bị tinh thần cho những gì có thể xảy ra tiếp theo trong thế giới Backrooms đầy nguy hiểm.",
    "Người kia nhìn Kai rồi trả lời bằng giọng nghiêm túc, giải thích khá đầy đủ về tình hình hiện tại để Kai có thể hiểu mọi chuyện đang diễn ra.",
    "Kai nhanh chóng phản ứng bằng kỹ năng của mình. Trận chiến trở nên vô cùng dữ dội và kịch tính, tiếng động vang vọng khắp nơi khiến bầu không khí nghẹt thở."
]

def build_samples():
    samples = []
    for i in range(100):
        samples.append({
            "id": i + 1,
            "scene_type": SCENE_TYPES[i % len(SCENE_TYPES)],
            "context": f"{LOCATIONS[i % len(LOCATIONS)]}; lượt diễn ra trong phạm vi nhỏ, không có dữ kiện mới ngoài những gì nhân vật trực tiếp nhận được.",
            "player_action": ACTIONS[(i * 3) % len(ACTIONS)],
            "weak_reply": WEAK[(i * 7) % len(WEAK)]
        })
    return samples

def endpoint_openai():
    if BASE.endswith("/chat/completions"):
        return BASE
    return BASE + "/chat/completions"

def endpoint_anthropic():
    if BASE.endswith("/messages"):
        return BASE
    return BASE + "/messages"

def post(url, payload, anthropic=False, timeout=90):
    data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    headers = {"Content-Type": "application/json"}
    if anthropic:
        headers["x-api-key"] = API
        headers["anthropic-version"] = "2023-06-01"
    else:
        headers["Authorization"] = "Bearer " + API
    req = urllib.request.Request(url, data=data, headers=headers, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=timeout, context=ssl.create_default_context()) as r:
            return json.loads(r.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", errors="replace")[:300]
        raise RuntimeError(f"Haiku HTTP {e.code}: {body}") from e

def extract_openai(obj):
    if isinstance(obj.get("output_text"), str) and obj["output_text"].strip():
        return obj["output_text"].strip()
    out = []
    for choice in obj.get("choices") or []:
        msg = choice.get("message") or {}
        content = msg.get("content")
        if isinstance(content, str):
            out.append(content)
        elif isinstance(content, list):
            for part in content:
                if isinstance(part, dict) and isinstance(part.get("text"), str):
                    out.append(part["text"])
        elif isinstance(choice.get("text"), str):
            out.append(choice["text"])
    return "\n".join(x.strip() for x in out if x and x.strip()).strip()

def extract_anthropic(obj):
    out = []
    for part in obj.get("content") or []:
        if isinstance(part, dict) and isinstance(part.get("text"), str):
            out.append(part["text"])
    return "\n".join(x.strip() for x in out if x and x.strip()).strip()

def call_haiku(prompt, max_tokens=3200, temperature=0.55):
    openai_first = not BASE.endswith("/messages")
    attempts = ["openai", "anthropic"] if openai_first else ["anthropic", "openai"]
    last = None
    for kind in attempts:
        try:
            if kind == "openai":
                obj = post(endpoint_openai(), {
                    "model": MODEL,
                    "messages": [{"role": "user", "content": prompt}],
                    "temperature": temperature,
                    "max_tokens": max_tokens,
                }, anthropic=False)
                text = extract_openai(obj)
            else:
                obj = post(endpoint_anthropic(), {
                    "model": MODEL,
                    "messages": [{"role": "user", "content": prompt}],
                    "temperature": temperature,
                    "max_tokens": max_tokens,
                }, anthropic=True)
                text = extract_anthropic(obj)
            if text:
                return text
            raise RuntimeError("Haiku returned empty text")
        except Exception as exc:
            last = exc
            if not any(code in str(exc) for code in ("HTTP 400", "HTTP 404", "HTTP 405", "HTTP 415", "HTTP 422")):
                break
    raise last or RuntimeError("Haiku request failed")

def parse_json(text):
    t = text.strip()
    if t.startswith("```"):
        t = t.split("\n", 1)[1] if "\n" in t else t
        if "```" in t:
            t = t.rsplit("```", 1)[0]
    start_obj, end_obj = t.find("{"), t.rfind("}")
    start_arr, end_arr = t.find("["), t.rfind("]")
    candidates = []
    if start_obj >= 0 and end_obj > start_obj:
        candidates.append(t[start_obj:end_obj + 1])
    if start_arr >= 0 and end_arr > start_arr:
        candidates.append(t[start_arr:end_arr + 1])
    for candidate in candidates:
        try:
            return json.loads(candidate)
        except Exception:
            pass
    raise ValueError("Could not parse JSON from Haiku response")

samples = build_samples()
results = []
batch_lessons = []

for batch_idx in range(10):
    batch = samples[batch_idx * 10:(batch_idx + 1) * 10]
    prompt = f"""{STYLE_BRIEF}

TASK
Rewrite each weak GM reply below into a stronger version that obeys the style priorities.
Do not add new canon facts, named entities, loot, exits, injuries or outcomes not present in the sample.
Each rewrite: 55-90 Vietnamese words, one compact GM turn.
Return ONLY valid JSON:
{{
  "samples": [
    {{"id": 1, "rewrite": "...", "lesson": "one short Vietnamese editing lesson"}}
  ],
  "batch_principles": ["...", "...", "..."]
}}

SAMPLES
{json.dumps(batch, ensure_ascii=False, indent=2)}
"""
    raw = call_haiku(prompt, max_tokens=3400, temperature=0.6)
    obj = parse_json(raw)
    rows = obj.get("samples") if isinstance(obj, dict) else None
    if not isinstance(rows, list):
        raise RuntimeError(f"Batch {batch_idx + 1}: missing samples array")
    by_id = {int(row.get("id")): row for row in rows if isinstance(row, dict) and str(row.get("id", "")).isdigit()}
    for source in batch:
        rid = source["id"]
        row = by_id.get(rid)
        if not row or not str(row.get("rewrite", "")).strip():
            raise RuntimeError(f"Batch {batch_idx + 1}: missing rewrite for id {rid}")
        results.append({
            **source,
            "rewrite": str(row["rewrite"]).strip(),
            "lesson": str(row.get("lesson", "")).strip()
        })
    for principle in obj.get("batch_principles", []) if isinstance(obj, dict) else []:
        if isinstance(principle, str) and principle.strip():
            batch_lessons.append(principle.strip())
    print(f"Haiku teacher batch {batch_idx + 1}/10 complete ({len(results)}/100 samples).")
    time.sleep(0.7)

representative = [results[i] for i in (0, 9, 19, 29, 39, 49, 59, 69, 79, 89, 99)]
distill_prompt = f"""{STYLE_BRIEF}

You have completed 100 rewrite samples for this Game Master voice.
Distill the recurring improvements into a compact production prompt for Gemini.
The production prompt must improve prose only. It must not change game mechanics, state authority, JSON schema, routing, validation, notifications, inventory, snapshot logic, or player agency.

Return ONLY valid JSON:
{{
  "voice_contract": ["10-14 concise Vietnamese imperative rules"],
  "anti_patterns": ["6-10 concise things to avoid"],
  "rhythm_rules": ["3-6 concise rules"],
  "dialogue_rules": ["3-6 concise rules"],
  "one_line_style": "single Vietnamese sentence defining the voice"
}}

BATCH LESSONS:
{json.dumps(batch_lessons, ensure_ascii=False, indent=2)}

REPRESENTATIVE REWRITES:
{json.dumps([{"scene_type": row["scene_type"], "rewrite": row["rewrite"], "lesson": row["lesson"]} for row in representative], ensure_ascii=False, indent=2)}
"""
distilled = parse_json(call_haiku(distill_prompt, max_tokens=2200, temperature=0.25))

payload = {
    "sample_count": len(results),
    "model": MODEL,
    "results": results,
    "distilled": distilled
}
OUT.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")

print("DISTILLED_STYLE_BEGIN")
print(json.dumps(distilled, ensure_ascii=False, indent=2))
print("DISTILLED_STYLE_END")
print(f"Wrote {OUT} with {len(results)} Haiku-taught samples.")

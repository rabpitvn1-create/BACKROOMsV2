"""Materialize the authored Level 0 campaign with one consistent startup-state schema.

The older authored beat modules remain the source of their long-form prose and state deltas,
but epsilon/0.01/0.1 were written against two retired startup shapes (`story:` and
`{role:"assistant",content:...}`). This orchestrator reads only their literal authored data,
applies it to the current `storyArc`/`storyContinuity`/`log` schema, and never mutates another
patch file.
"""
from __future__ import annotations

import ast
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parent
INDEX = ROOT / "app/src/main/assets/index.html"
LUCIA = ROOT / "patch-main-campaign-level0-lucia-decision.py"
EPSILON = ROOT / "patch-main-campaign-level0-epsilon.py"
LEVEL001 = ROOT / "patch-main-campaign-level0-01.py"
LEVEL01 = ROOT / "patch-main-campaign-level0-1.py"


def literal_assignment(path: Path, name: str):
    tree = ast.parse(path.read_text(encoding="utf-8"), filename=str(path))
    for node in tree.body:
        if isinstance(node, ast.Assign) and any(isinstance(t, ast.Name) and t.id == name for t in node.targets):
            return ast.literal_eval(node.value)
    raise RuntimeError(f"{path.name}: literal assignment {name!r} not found")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return text.replace(old, new, 1)


def install_authored_stage(html: str, path: Path, marker: str, *, skip_legacy_log: bool = False) -> str:
    if marker in html:
        return html
    beat = literal_assignment(path, "beat")
    replacements = literal_assignment(path, "replacements")
    html = replace_once(html, "const initial={", beat, f"{path.name} beat insertion")
    for old, new in replacements.items():
        if skip_legacy_log and old.startswith('{role:"assistant",content:'):
            continue
        html = replace_once(html, old, new, f"{path.name} state delta")
    return html


# The current predecessor is already schema-correct and recursively materializes the prologue,
# Level 0 first contact, and Lucia's mutual join decision.
subprocess.run(["python3", str(LUCIA)], cwd=ROOT, check=True)
html = INDEX.read_text(encoding="utf-8")

legacy_lucia_signature = 'const campaignLevel0LuciaDecisionSignature="LEVEL 0 / THE LOBBY — DECISION TO MOVE TOGETHER";'
structured_lucia_signature = 'window.campaignLevel0LuciaDecisionSignature={currentBeat:"STORY.LEVEL0.LUCIA_DECISION_COMPLETE",nextBeat:"STORY.LEVEL0.EPSILON.ENTRY",partyMember:"lucia",relationship:"earned_tactical_trust",romance:"none",sublevelId:"",playerAgency:"mutual-party-decision"};'
if structured_lucia_signature not in html:
    html = replace_once(
        html,
        legacy_lucia_signature,
        legacy_lucia_signature + "\n" + structured_lucia_signature,
        "Lucia structured campaign signature",
    )

lucia_summary = '{role:"gm",text:"LƯỢT 1\\n\\nKai và Lucia đã tự nguyện chọn tiếp tục di chuyển cùng nhau sau khi kiểm chứng lợi ích chiến thuật của việc có hai góc quan sát. Lucia hiện ở Party với quan hệ earned tactical trust; chưa có tình cảm lãng mạn. Liên lạc với Iris, Syvial, SRU và Frontrooms vẫn ngoại tuyến. Không có lối thoát, Entity cư trú hay dấu vết Async nào được xác nhận từ beat này. Story beat kế tiếp là tiến vào Level ε — Incessant Hum-Buzz; beat hiện tại không tự chuyển sublevel."}'
epsilon_summary = '{role:"gm",text:"LƯỢT 1\\n\\nKai và Lucia đã hoàn tất beat Level ε trong cùng parent Level 0. Họ chỉ xác nhận structural drift và nhiễu âm thanh quan sát được; không tự suy diễn lối thoát, Entity cư trú hay dấu vết Async. Quan hệ vẫn là earned tactical trust, không romance. Story beat kế tiếp là Level 0.01 — The Exit ?."}'
level001_summary = '{role:"gm",text:"LƯỢT 1\\n\\nKai và Lucia đã hoàn tất Level 0.01 sau khi kiểm chứng các biển EXIT, route loop, vùng im lặng và dấu phấn lạ mà không coi chúng là bằng chứng thoát ra, Entity hay đồng đội mất tích. Story beat kế tiếp là Level 0.1 — Deep Emptiness."}'
level01_summary = '{role:"gm",text:"LƯỢT 1\\n\\nKai và Lucia đã hoàn tất Level 0.1 — Deep Emptiness. Vật tư, âm thanh và dấu vết chưa xác định vẫn giữ trạng thái chưa xác nhận; currentBeat là STORY.LEVEL0.1.COMPLETE và nextBeat là STORY.LEVEL0.11.ENTRY. Người chơi tiếp tục điều khiển Kai từ đây."}'

# Epsilon: use authored prose/state deltas, then advance the current GM log using the schema
# actually consumed by the APK. The retired top-level `story:` pointer is deliberately absent.
epsilon_marker = "LEVEL ε / INCESSANT HUM-BUZZ — STRUCTURAL DRIFT"
if epsilon_marker not in html:
    html = install_authored_stage(html, EPSILON, epsilon_marker)
    epsilon_signature = 'window.campaignLevel0EpsilonSignature={currentBeat:"STORY.LEVEL0.EPSILON.COMPLETE",nextBeat:"STORY.LEVEL0.01.ENTRY",parentLevel:0,sublevelId:"SUBLEVEL.00.EPSILON",difficulty:3,relationship:"earned_tactical_trust",romance:"none",knowledgeBoundary:"observed-structure-only"};'
    if epsilon_signature not in html:
        html = replace_once(
            html,
            structured_lucia_signature,
            structured_lucia_signature + "\n" + epsilon_signature,
            "Level epsilon campaign signature",
        )
    html = replace_once(
        html,
        lucia_summary,
        '{role:"gm",text:"LEVEL ε — INCESSANT HUM-BUZZ\\n\\n"+level0EpsilonStory},' + epsilon_summary,
        "Level epsilon current log",
    )

# 0.01 and 0.1 contained a retired assistant/content log anchor. Their authored state deltas and
# prose remain authoritative; only that stale transport anchor is skipped and replaced here.
level001_marker = "LEVEL 0.01 / THE EXIT ? — FALSE PROMISE"
if level001_marker not in html:
    html = install_authored_stage(html, LEVEL001, level001_marker, skip_legacy_log=True)
    html = replace_once(
        html,
        epsilon_summary,
        '{role:"gm",text:"LEVEL 0.01 — THE EXIT ?\\n\\n"+level001Story},' + level001_summary,
        "Level 0.01 current log",
    )

level01_marker = "LEVEL 0.1 / DEEP EMPTINESS — BORROWED SHELTER"
if level01_marker not in html:
    html = install_authored_stage(html, LEVEL01, level01_marker, skip_legacy_log=True)
    html = replace_once(
        html,
        level001_summary,
        '{role:"gm",text:"LEVEL 0.1 — DEEP EMPTINESS\\n\\n"+level01Story},' + level01_summary,
        "Level 0.1 current log",
    )

for required in (
    structured_lucia_signature,
    'window.campaignLevel0EpsilonSignature=',
    'currentBeat:"STORY.LEVEL0.1.COMPLETE"',
    'nextBeat:"STORY.LEVEL0.11.ENTRY"',
    'sublevelId:"SUBLEVEL.00.1"',
    'LEVEL ε — INCESSANT HUM-BUZZ',
    'LEVEL 0.01 — THE EXIT ?',
    'LEVEL 0.1 — DEEP EMPTINESS',
):
    if required not in html:
        raise RuntimeError("Campaign materialization contract missing: " + required)

for retired in (
    'story:level0LuciaDecision,',
    'story:level0EpsilonStory,',
    '{role:"assistant",content:level0EpsilonStory}',
    '{role:"assistant",content:level001Story}',
):
    if retired in html:
        raise RuntimeError("Retired campaign startup schema survived: " + retired)

INDEX.write_text(html, encoding="utf-8")
print("Main campaign materialized through Level 0.1 using the current storyArc/storyContinuity/GM-log schema.")

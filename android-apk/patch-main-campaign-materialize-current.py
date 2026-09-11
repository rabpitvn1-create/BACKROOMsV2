"""Extend the schema-safe Level 0 materializer through the currently authored 0.23/0.41 beats."""
from __future__ import annotations

import ast
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parent
INDEX = ROOT / "app/src/main/assets/index.html"
BASE = ROOT / "patch-main-campaign-materialize.py"
LEVEL023 = ROOT / "patch-main-campaign-level0-23.py"
LEVEL041 = ROOT / "patch-main-campaign-level0-41.py"


def literal(path: Path, name: str):
    tree = ast.parse(path.read_text(encoding="utf-8"), filename=str(path))
    for node in tree.body:
        if isinstance(node, ast.Assign) and any(isinstance(t, ast.Name) and t.id == name for t in node.targets):
            return ast.literal_eval(node.value)
    raise RuntimeError(f"{path.name}: missing literal assignment {name}")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return text.replace(old, new, 1)


def ensure_thread(text: str, predecessor: str, thread: str, label: str) -> str:
    if thread in text:
        return text
    return replace_once(text, predecessor, predecessor + "," + thread, label)


def apply_stage(text: str, path: Path, marker: str, predecessor_thread: str, thread: str,
                prior_summary: str, story_entry: str, summary: str) -> str:
    if marker in text:
        return text
    text = replace_once(text, "const initial={", literal(path, "beat"), f"{path.name} beat")
    for old, new in literal(path, "replacements").items():
        if old.startswith('{role:"assistant",content:') or old.startswith('{id:"THREAD.MAIN.'):
            continue
        if new in text:
            continue
        text = replace_once(text, old, new, f"{path.name} state")
    text = ensure_thread(text, predecessor_thread, thread, f"{path.name} thread")
    text = replace_once(text, prior_summary, story_entry + "," + summary, f"{path.name} GM log")
    return text


subprocess.run(["python3", str(BASE)], cwd=ROOT, check=True)
html = INDEX.read_text(encoding="utf-8")

thread022 = '{id:"THREAD.MAIN.LEVEL0.22",status:"resolved",turn:1,fact:"Traverse Fully Remodeled while distinguishing verified portable salvage from infrastructure whose persistence, safety and origin remain unknown."}'
thread023 = '{id:"THREAD.MAIN.LEVEL0.23",status:"resolved",turn:1,fact:"Traverse Half Finished while countering isolation with close formation and multi-view verification, preserving uncertainty about structural changes and their cause."}'
thread041 = '{id:"THREAD.MAIN.LEVEL0.41",status:"resolved",turn:1,fact:"Traverse Level 0.41 while treating decay, darkness tears, transient symptoms and Pause-like observations as hazards to manage rather than proof of an unknown mechanism."}'

summary022 = '{role:"gm",text:"LƯỢT 1\\n\\nKai và Lucia đã hoàn tất Level 0.22 — Fully Remodeled. Chỉ vật liệu đã kiểm tra và mang theo mới được coi là tài nguyên; hạ tầng hữu dụng không được mặc định là ổn định hay an toàn. currentBeat là STORY.LEVEL0.22.COMPLETE và nextBeat là STORY.LEVEL0.23.ENTRY."}'
summary023 = '{role:"gm",text:"LƯỢT 1\\n\\nKai và Lucia đã hoàn tất Level 0.23 — Half Finished. Hai người giữ đội hình gần, kiểm tra mép và thay đổi cấu trúc từ nhiều góc nhìn, đồng thời giữ nguyên bất định về nguyên nhân. currentBeat là STORY.LEVEL0.23.COMPLETE và nextBeat là STORY.LEVEL0.41.ENTRY."}'
summary041 = '{role:"gm",text:"LƯỢT 1\\n\\nKai và Lucia đã hoàn tất Level 0.41 — Disease. Những vùng tối, vật liệu mốc, triệu chứng thoáng qua và hiện tượng Pause-like chỉ được ghi nhận như rủi ro quan sát được, không bị nâng thành chẩn đoán hay cơ chế chưa kiểm chứng. currentBeat là STORY.LEVEL0.41.COMPLETE và nextBeat là STORY.LEVEL0.5.ENTRY."}'

html = apply_stage(
    html, LEVEL023, "LEVEL 0.23 / HALF FINISHED — TRUST THE EDGE, NOT THE PROMISE",
    thread022, thread023, summary022,
    '{role:"gm",text:"LEVEL 0.23 — HALF FINISHED\\n\\n"+level023Story}', summary023,
)
html = apply_stage(
    html, LEVEL041, "LEVEL 0.41 / DISEASE — DO NOT NAME WHAT YOU HAVE NOT TESTED",
    thread023, thread041, summary023,
    '{role:"gm",text:"LEVEL 0.41 — DISEASE\\n\\n"+level041Story}', summary041,
)

for required in (
    'currentBeat:"STORY.LEVEL0.41.COMPLETE"',
    'nextBeat:"STORY.LEVEL0.5.ENTRY"',
    'sublevelId:"SUBLEVEL.00.41"',
    'THREAD.MAIN.LEVEL0.23',
    'THREAD.MAIN.LEVEL0.41',
    'LEVEL 0.23 — HALF FINISHED',
    'LEVEL 0.41 — DISEASE',
):
    if required not in html:
        raise RuntimeError("Current campaign materialization contract missing: " + required)
for retired in ('{role:"assistant",content:level023Story}', '{role:"assistant",content:level041Story}'):
    if retired in html:
        raise RuntimeError("Retired campaign log schema survived: " + retired)

INDEX.write_text(html, encoding="utf-8")
print("Main campaign materialized through Level 0.41 with current continuity/log schema.")

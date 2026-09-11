"""Materialize the authored Level 0 campaign chain on clean and already-patched trees.

This is build compatibility only. It does not add World simulation or change gameplay rules.
The epsilon module still expects structured Lucia-decision compatibility markers that the
older predecessor patch did not materialize, so normalize those hand-off anchors before
running the latest authored Level 0.01 module.
"""
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parent
INDEX = ROOT / "app/src/main/assets/index.html"
LUCIA_DECISION = ROOT / "patch-main-campaign-level0-lucia-decision.py"
LATEST = ROOT / "patch-main-campaign-level0-01.py"

subprocess.run(["python3", str(LUCIA_DECISION)], cwd=ROOT, check=True)

html = INDEX.read_text(encoding="utf-8")
legacy_signature = 'const campaignLevel0LuciaDecisionSignature="LEVEL 0 / THE LOBBY — DECISION TO MOVE TOGETHER";'
structured_signature = 'window.campaignLevel0LuciaDecisionSignature={currentBeat:"STORY.LEVEL0.LUCIA_DECISION_COMPLETE",nextBeat:"STORY.LEVEL0.EPSILON.ENTRY",partyMember:"lucia",relationship:"earned_tactical_trust",romance:"none",sublevelId:"",playerAgency:"mutual-party-decision"};'

if structured_signature not in html:
    count = html.count(legacy_signature)
    if count != 1:
        raise RuntimeError(
            f"Campaign continuation compatibility expected one Lucia decision signature, found {count}"
        )
    html = html.replace(legacy_signature, legacy_signature + "\n" + structured_signature, 1)

# The Lucia-decision predecessor advances storyArc/log/Party but historically forgot to
# advance initial.story from the first-contact prose. Epsilon owns the next transition and
# explicitly requires story:level0LuciaDecision, as its predecessor contract. Normalize only
# this stale authored-beat pointer; no gameplay/world state is created here.
expected_story = 'story:level0LuciaDecision,'
previous_story = 'story:level0Arrival,'
if expected_story not in html:
    count = html.count(previous_story)
    if count != 1:
        raise RuntimeError(
            f"Campaign continuation expected one stale Level 0 story binding, found {count}"
        )
    html = html.replace(previous_story, expected_story, 1)

INDEX.write_text(html, encoding="utf-8")
subprocess.run(["python3", str(LATEST)], cwd=ROOT, check=True)
print("Main campaign continuation materialized through Level 0.01 with compatible historical hand-offs.")

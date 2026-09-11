"""Adapt the legacy Drive-canon initializer patch to the current campaign startup state.

The R06 patch still expects the old one-line communication-only flags object. The current
startup state already owns richer mission/story continuity flags. This compatibility pass
changes only that stale build-script anchor so the Drive patch adds its missing madGod
runtime default without replacing or deleting the campaign/story flags.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parent
TARGET = ROOT / "patch-drive-canon-gameplay.py"
text = TARGET.read_text(encoding="utf-8")

old = '''index = replace_once(
    index,
    'flags:{communication:{blackBlood:"OFFLINE",iris:"OFFLINE",syvial:"OFFLINE"}},',
    'flags:{communication:{blackBlood:"OFFLINE",iris:"OFFLINE",syvial:"OFFLINE"},iris:{exists:true,continuity:"SEPARATED",reunionEligible:true},syvial:{exists:true,continuity:"SEPARATED",reunionEligible:true},madGod:{spawned:false,acquired:false}},',
    "initial continuity flags",
)
'''

new = '''legacy_initial_flags = 'flags:{communication:{blackBlood:"OFFLINE",iris:"OFFLINE",syvial:"OFFLINE"}},'
if legacy_initial_flags in index:
    index = replace_once(
        index,
        legacy_initial_flags,
        'flags:{communication:{blackBlood:"OFFLINE",iris:"OFFLINE",syvial:"OFFLINE"},iris:{exists:true,continuity:"SEPARATED",reunionEligible:true},syvial:{exists:true,continuity:"SEPARATED",reunionEligible:true},madGod:{spawned:false,acquired:false}},',
        "initial continuity flags (legacy)",
    )
else:
    campaign_communication = '    communication:{iris:"OFFLINE",syvial:"OFFLINE",sru:"OFFLINE",frontrooms:"OFFLINE",beacon:"OFFLINE",telemetry:"OFFLINE"},\\n'
    campaign_madgod = campaign_communication + '    madGod:{spawned:false,acquired:false},\\n'
    if campaign_communication in index:
        index = replace_once(
            index,
            campaign_communication,
            campaign_madgod,
            "initial continuity flags (campaign startup)",
        )
    elif 'madGod:{spawned:false,acquired:false}' not in index:
        raise RuntimeError("initial continuity flags: neither legacy nor campaign startup anchor found")
'''

count = text.count(old)
if count != 1:
    raise RuntimeError(f"Drive canon continuity compatibility: expected 1 stale block, found {count}")
text = text.replace(old, new, 1)

for required in (
    'initial continuity flags (campaign startup)',
    'story continuity flags',
):
    pass

TARGET.write_text(text, encoding="utf-8")
print("Drive canon startup compatibility installed: preserve campaign/story flags and add only missing runtime defaults.")

from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
APK = ROOT / "android-apk"

ACTIVE = (
    "patch-drive-canon-gameplay.py",
    "patch-inventory-persistence.py",
    "patch-ai-orchestrator.py",
    "patch-state-op-hardening.py",
    "patch-conditional-audit.py",
    "patch-audit-validated-risk.py",
    "patch-gameplay-parity-final.py",
    "patch-final-authority-hardening.py",
    "patch-kai-resource-policy-final.py",
    "patch-search-action-false-warning.py",
    "patch-knowledge-context-builder.py",
    "patch-local-entity-overlay.py",
    "patch-jeff-encounter-2pct.py",
    "patch-jane-killer.py",
    "patch-character-detail-avatar-fallback.py",
    "patch-progression-snapshot-equipment.py",
)

REPLACEMENTS = (
    (", madGod,", ","),
    (" MadGod roll success chỉ mở discovery route, không tự đưa set vào inventory.", ""),
    ("MadGod roll success chỉ mở discovery route, không tự đưa set vào inventory. ", ""),
    ("MadGod Set success chỉ mở đường/vị trí khám phá; acquired mặc định false cho tới khi Kai thực sự tiếp cận và lấy. ", ""),
    (" MadGod Set success chỉ mở đường/vị trí khám phá; acquired mặc định false cho tới khi Kai thực sự tiếp cận và lấy.", ""),
)

for name in ACTIVE:
    path = APK / name
    if not path.exists():
        continue
    text = path.read_text(encoding="utf-8")
    for old, new in REPLACEMENTS:
        text = text.replace(old, new)

    # This historical roll block survived the older purge transform because its source shape drifted.
    if name == "patch-drive-canon-gameplay.py":
        text, count = re.subn(
            r'\n    JSONObject madGod = flags != null \? flags\.optJSONObject\("madGod"\) : null;\n'
            r'    boolean madGodAlready = madGod != null && madGod\.optBoolean\("spawned", false\);\n'
            r'    rolls\.put\("madGodSet", rollSpec\("madGodSet", 1, search && !madGodAlready\)\);\n',
            "\n",
            text,
            count=1,
        )
        if count != 1:
            raise RuntimeError("stubborn retired discovery-roll block not found")

    path.write_text(text, encoding="utf-8")

# The main purge owns the exhaustive target scan. This helper only removes source-shape drift
# that the historical transform cannot recognize without weakening its guarded replacements.
print("Active patch drift sanitized.")

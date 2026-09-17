from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
APK = ROOT / "android-apk"
TARGET = re.compile(
    r"MadGod|Mad God|madgod|mad god|AN_NHIEN|AnNhien|an_nhien|an-nhien|"
    r"(?<![\w])An Nhiên(?![\w])|(?<![\w])An Nhien(?![\w])|annhien",
    re.I,
)

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
    (", madGod", ""),
    ("madGod, ", ""),
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
    path.write_text(text, encoding="utf-8")

# Fail here with the exact active file/line rather than letting a later broad purge obscure it.
for name in ACTIVE:
    path = APK / name
    if not path.exists():
        continue
    text = path.read_text(encoding="utf-8")
    match = TARGET.search(text)
    if match:
        line = text[:match.start()].count("\n") + 1
        raise RuntimeError(f"active residue remains: {name}:{line}:{match.group(0)}")

print("Active patch residue sanitized.")

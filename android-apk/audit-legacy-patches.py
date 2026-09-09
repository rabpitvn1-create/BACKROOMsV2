from __future__ import annotations

from collections import deque
from pathlib import Path
import json
import re

ROOT = Path(__file__).resolve().parent
WORKFLOW = ROOT.parent / ".github/workflows/build-backroom-apk.yml"
PATCH_RE = re.compile(r"patch-[A-Za-z0-9._-]+\.py")


def patch_names(text: str) -> set[str]:
    return set(PATCH_RE.findall(text))


def active_workflow_roots(text: str) -> set[str]:
    roots: set[str] = set()
    in_scripts = False
    script_buffer: list[str] = []
    for raw in text.splitlines():
        line = raw.strip()
        if line.startswith("scripts=("):
            in_scripts = True
            script_buffer.append(line)
            if line.endswith(")"):
                in_scripts = False
            continue
        if in_scripts:
            script_buffer.append(line)
            if line.endswith(")"):
                in_scripts = False
            continue
        # Explicit positive preflight requirements are active roots. Negative
        # retirement guards (`test ! -e`) intentionally do not make a file live.
        if "test -s android-apk/patch-" in line:
            roots.update(patch_names(line))
    roots.update(patch_names("\n".join(script_buffer)))
    return roots


patches = {p.name: p for p in ROOT.glob("patch-*.py") if p.is_file()}
workflow_text = WORKFLOW.read_text(encoding="utf-8")
roots = {name for name in active_workflow_roots(workflow_text) if name in patches}

deps: dict[str, list[str]] = {}
for name, path in sorted(patches.items()):
    refs = patch_names(path.read_text(encoding="utf-8", errors="replace"))
    refs.discard(name)
    deps[name] = sorted(ref for ref in refs if ref in patches)

reachable: set[str] = set()
queue = deque(sorted(roots))
while queue:
    name = queue.popleft()
    if name in reachable:
        continue
    reachable.add(name)
    queue.extend(dep for dep in deps.get(name, ()) if dep not in reachable)

unreachable = sorted(set(patches) - reachable)
referenced_by = {name: [] for name in patches}
for owner, children in deps.items():
    for child in children:
        referenced_by[child].append(owner)

# These patterns are not automatically wrong, but they are high-risk in this
# repository because build-time patches mutate other source/patch files and can
# silently reintroduce retired canon after a newer finalizer.
risk_patterns = {
    "patch_mutates_patch": re.compile(r"PATCH[^\n]*=.*patch-|patch-[A-Za-z0-9._-]+\.py[^\n]*write_text", re.I),
    "dynamic_exec": re.compile(r"\bexec\s*\(\s*compile\s*\("),
    "runpy_chain": re.compile(r"runpy\.run_path"),
    "legacy_black_blood": re.compile(r"Black Blood|Blackblood", re.I),
    "legacy_luna": re.compile(r"\bLuna\b|LUNA_"),
    "legacy_entity_rate": re.compile(r"8\.0000%|\+8 percentage|independent 3% encounter|entityThresholds"),
}
risks: dict[str, list[str]] = {}
for name, path in sorted(patches.items()):
    text = path.read_text(encoding="utf-8", errors="replace")
    hits = [label for label, pattern in risk_patterns.items() if pattern.search(text)]
    if hits:
        risks[name] = hits

report = {
    "patch_file_count": len(patches),
    "workflow_roots": sorted(roots),
    "reachable_count": len(reachable),
    "unreachable_count": len(unreachable),
    "unreachable": unreachable,
    "reachable": sorted(reachable),
    "dependencies": deps,
    "referenced_by": {k: sorted(v) for k, v in referenced_by.items() if v},
    "risk_markers": risks,
}

out = ROOT / "legacy_patch_audit.json"
out.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

print(f"LEGACY_PATCH_AUDIT files={len(patches)} roots={len(roots)} reachable={len(reachable)} unreachable={len(unreachable)}")
print("UNREACHABLE_PATCHES_BEGIN")
for name in unreachable:
    print(name)
print("UNREACHABLE_PATCHES_END")
print("RISK_MARKERS_BEGIN")
for name in sorted(risks):
    print(f"{name}: {','.join(risks[name])}")
print("RISK_MARKERS_END")

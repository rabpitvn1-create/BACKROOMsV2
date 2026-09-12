#!/usr/bin/env python3
from __future__ import annotations

import json
import os
import re
import subprocess
import sys
import time
import unicodedata
import xml.etree.ElementTree as ET
from pathlib import Path

PACKAGE = "com.rabpit.backroom"
COMPONENT = f"{PACKAGE}/.MainActivity"
APK = sys.argv[1] if len(sys.argv) > 1 else "Backroom-under-test.apk"
OUT = Path(os.environ.get("EMU_OUT", "emu-level0-30turn-results"))
TARGET_ACTIONS = int(os.environ.get("TARGET_ACTIONS", "30"))
TURN_TIMEOUT = int(os.environ.get("TURN_TIMEOUT_SECONDS", "180"))
START_TIMEOUT = int(os.environ.get("START_TIMEOUT_SECONDS", "45"))
POLL = float(os.environ.get("POLL_SECONDS", "1.5"))

OUT.mkdir(parents=True, exist_ok=True)
(OUT / "ui").mkdir(exist_ok=True)
(OUT / "screens").mkdir(exist_ok=True)
issues: list[dict] = []
coverage = {
    "launch": False,
    "three_actions": False,
    "keyboard": False,
    "save": False,
    "load": False,
    "inventory_visible": False,
    "party_visible": False,
    "snapshot_seen": False,
    "provider_seen": False,
    "combat_seen": False,
    "new_game": False,
    "delete_save": False,
}
action_counts = {"explore": 0, "search": 0, "execute": 0}
provider_labels: set[str] = set()
story_trace: list[dict] = []


def norm(value: str | None) -> str:
    value = (value or "").replace("đ", "d").replace("Đ", "D").replace("’", "'")
    value = unicodedata.normalize("NFKD", value)
    return "".join(ch for ch in value if not unicodedata.combining(ch)).casefold().strip()


def run(*args: str, check: bool = True, capture: bool = True, timeout: int | None = None) -> subprocess.CompletedProcess:
    return subprocess.run(args, check=check, text=True, capture_output=capture, timeout=timeout)


def adb(*args: str, check: bool = True, capture: bool = True, timeout: int | None = None) -> subprocess.CompletedProcess:
    return run("adb", *args, check=check, capture=capture, timeout=timeout)


def issue(severity: str, code: str, message: str, **details) -> None:
    row = {"severity": severity, "code": code, "message": message, **details}
    issues.append(row)
    print(f"[{severity.upper()}] {code}: {message}", flush=True)


def screenshot(name: str) -> None:
    path = OUT / "screens" / f"{name}.png"
    with path.open("wb") as fh:
        subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=fh, stderr=subprocess.DEVNULL)


def dump_ui(name: str) -> tuple[Path, ET.Element | None]:
    remote = "/sdcard/window.xml"
    path = OUT / "ui" / f"{name}.xml"
    adb("shell", "uiautomator", "dump", remote, check=False)
    pulled = adb("pull", remote, str(path), check=False)
    if pulled.returncode != 0 or not path.exists() or path.stat().st_size == 0:
        return path, None
    try:
        return path, ET.parse(path).getroot()
    except Exception as exc:
        issue("warning", "ui_xml_parse", f"Không parse được UI XML: {exc}", file=str(path))
        return path, None


def nodes(root: ET.Element | None):
    return list(root.iter("node")) if root is not None else []


def text_blob(root: ET.Element | None) -> str:
    return "\n".join(
        norm((n.attrib.get("text") or "") + " " + (n.attrib.get("content-desc") or ""))
        for n in nodes(root)
    )


def node_center(node: ET.Element) -> tuple[int, int] | None:
    m = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds") or "")
    if not m:
        return None
    x1, y1, x2, y2 = map(int, m.groups())
    if x2 <= x1 or y2 <= y1:
        return None
    return (x1 + x2) // 2, (y1 + y2) // 2


def tap_node(node: ET.Element) -> bool:
    center = node_center(node)
    if not center:
        return False
    adb("shell", "input", "tap", str(center[0]), str(center[1]))
    return True


def find_button(root: ET.Element | None, *labels: str, enabled_only: bool = True) -> ET.Element | None:
    wanted = {norm(x) for x in labels}
    for node in nodes(root):
        cls = node.attrib.get("class") or ""
        if not cls.endswith("Button"):
            continue
        if enabled_only and node.attrib.get("enabled", "true") != "true":
            continue
        text = norm(node.attrib.get("text"))
        desc = norm(node.attrib.get("content-desc"))
        if text in wanted or desc in wanted:
            return node
    return None


def find_edit(root: ET.Element | None) -> ET.Element | None:
    for node in nodes(root):
        if (node.attrib.get("class") or "").endswith("EditText") and node.attrib.get("enabled", "true") == "true":
            return node
    return None


def app_resumed() -> bool:
    out = adb("shell", "dumpsys", "activity", "activities", check=False).stdout
    return bool(re.search(rf"(mResumedActivity|topResumedActivity).*{re.escape(PACKAGE)}/\.MainActivity", out))


def dismiss_system_overlay(root: ET.Element | None) -> bool:
    blob = text_blob(root)
    acted = False
    if "viewing full screen" in blob:
        btn = find_button(root, "got it", "ok")
        if btn and tap_node(btn):
            acted = True
    if "responding" in blob:
        btn = find_button(root, "wait")
        if btn and tap_node(btn):
            acted = True
    if "access your contacts" in blob:
        btn = find_button(root, "don't allow", "dont allow", "deny")
        if btn and tap_node(btn):
            acted = True
    if acted:
        time.sleep(1)
    return acted


def ui_turn(root: ET.Element | None) -> int | None:
    values: list[int] = []
    texts = [norm(n.attrib.get("text") or n.attrib.get("content-desc") or "") for n in nodes(root)]
    for i, text in enumerate(texts):
        for pattern in (r"^turn\s+(\d+)\s+da\b", r"snapshot\s+turn\s+(\d+)", r"^turn\s+(\d+)\b"):
            m = re.search(pattern, text)
            if m:
                values.append(int(m.group(1)))
        if text == "turn" and i + 1 < len(texts) and texts[i + 1].isdigit():
            values.append(int(texts[i + 1]))
    return max(values) if values else None


def primary_buttons(root: ET.Element | None) -> dict[str, bool]:
    return {
        "search": find_button(root, "tim kiem", enabled_only=False) is not None,
        "execute": find_button(root, "thuc hien", enabled_only=False) is not None,
        "explore": find_button(root, "kham pha", enabled_only=False) is not None,
    }


def busy(root: ET.Element | None) -> bool:
    blob = text_blob(root)
    return any(token in blob for token in (
        "dang xu ly luot",
        "dang tim kiem khu vuc hien tai",
        "dang kham pha khu vuc chua khao sat",
        "combat auto",
    ))


def visible_error(root: ET.Element | None) -> str:
    for node in nodes(root):
        text = (node.attrib.get("text") or "").strip()
        n = norm(text)
        if n.startswith("loi ") or n.startswith("error ") or "game state core tu choi" in n:
            return text.replace("\n", " ")[:700]
    return ""


def wait_ready(label: str, timeout: int) -> tuple[ET.Element | None, int | None]:
    deadline = time.time() + timeout
    last_root = None
    while time.time() < deadline:
        _, root = dump_ui(label)
        last_root = root
        if root is None:
            time.sleep(POLL)
            continue
        if dismiss_system_overlay(root):
            continue
        if not app_resumed():
            time.sleep(POLL)
            continue
        if busy(root):
            time.sleep(POLL)
            continue
        buttons = primary_buttons(root)
        if buttons["explore"] or buttons["search"] or buttons["execute"]:
            return root, ui_turn(root)
        time.sleep(POLL)
    screenshot(f"{label}-timeout")
    return last_root, ui_turn(last_root)


def parse_maybe_json(value, fallback):
    if isinstance(value, dict):
        return value
    if isinstance(value, str) and value.strip():
        try:
            parsed = json.loads(value)
            if isinstance(parsed, dict):
                return parsed
        except Exception:
            pass
    return fallback if isinstance(fallback, dict) else {}


def read_core_state() -> dict | None:
    proc = adb("exec-out", "run-as", PACKAGE,
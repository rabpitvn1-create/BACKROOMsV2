#!/usr/bin/env python3
"""Adapt the legacy sublevel traversal harness to the player-facing Freedom composer.

The runtime no longer exposes Search/Explore macro buttons. The traversal harness must therefore
enter a deterministic freeform traversal sentence and press the surviving Thực hiện control.
The sentence is contract-locked by android-apk/test-gm-choices-freedom.mjs to classify as EXPLORE.
"""
from pathlib import Path
import re

PATH = Path(__file__).resolve().parent / "emu-sublevels-0-6.sh"
text = PATH.read_text(encoding="utf-8")

old_interaction = """    elif kind == 'explore':
        match = enabled and cls.endswith('Button') and (text == 'kham pha' or desc == 'kham pha')
"""
new_interaction = """    elif kind == 'input':
        match = enabled and cls.endswith('EditText')
    elif kind == 'submit':
        match = enabled and cls.endswith('Button') and (
            rid.endswith(':id/submit') or rid == 'submit' or text == 'thuc hien' or desc == 'thuc hien'
        )
"""
count = text.count(old_interaction)
if count != 1:
    raise RuntimeError(f"node_bounds interaction anchor expected 1 match, found {count}")
text = text.replace(old_interaction, new_interaction, 1)

wait_pattern = re.compile(
    r"wait_until_explore_ready\(\) \{\n.*?\n\}\n\nsubmit_explore\(\) \{",
    re.S,
)
wait_replacement = r'''wait_until_explore_ready() {
  local label="$1" deadline tmp bounds non_app=0
  deadline=$((SECONDS + TURN_TIMEOUT_SECONDS))
  tmp="/tmp/backroom-${label}.xml"
  while (( SECONDS < deadline )); do
    dump_ui_to "$tmp" || { sleep "$POLL_SECONDS"; continue; }
    if is_known_system_overlay "$tmp"; then
      dismiss_system_overlays
      sleep 1
      continue
    fi
    if ! is_app_resumed; then
      non_app=$((non_app + 1))
      if (( non_app >= 5 )); then
        return 1
      fi
      sleep "$POLL_SECONDS"
      continue
    fi
    non_app=0
    if ui_is_busy "$tmp"; then
      sleep "$POLL_SECONDS"
      continue
    fi
    bounds=$(node_bounds "$tmp" input || true)
    if [[ -n "$bounds" ]]; then
      cp "$tmp" "$OUT/ui/${label}.xml"
      return 0
    fi
    sleep "$POLL_SECONDS"
  done
  [[ -s "$tmp" ]] && cp "$tmp" "$OUT/ui/${label}-timeout.xml" || true
  return 1
}

submit_explore() {'''
text, count = wait_pattern.subn(wait_replacement, text, count=1)
if count != 1:
    raise RuntimeError(f"Freedom-ready function patch expected 1 match, found {count}")

submit_pattern = re.compile(
    r"submit_explore\(\) \{\n.*?\n\}\n\nread_core_state\(\) \{",
    re.S,
)
submit_replacement = r'''submit_explore() {
  local index="$1" xml input_bounds typed submit_bounds observed_text
  xml="$OUT/ui/ready-${index}.xml"
  dump_ui_to "$xml" || return 1
  input_bounds=$(node_bounds "$xml" input || true)
  [[ -n "$input_bounds" ]] || return 1

  tap_bounds "$input_bounds"
  adb shell 'input keyevent KEYCODE_MOVE_END; for i in $(seq 1 80); do input keyevent KEYCODE_DEL; done; input text continue%sforward' >/dev/null 2>&1 || true
  sleep 1
  typed="$OUT/ui/typed-${index}.xml"
  dump_ui_to "$typed" || return 1
  observed_text=$(python3 - "$typed" <<'PY'
import sys, xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot()
for node in root.iter('node'):
    if (node.attrib.get('class') or '').endswith('EditText'):
        print(node.attrib.get('text') or '')
        break
PY
  )
  [[ "${observed_text,,}" == *"continue forward"* ]] || return 1

  submit_bounds=$(node_bounds "$typed" submit || true)
  if [[ -z "$submit_bounds" ]]; then
    adb shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
    sleep 1
    dump_ui_to "$typed" || return 1
    submit_bounds=$(node_bounds "$typed" submit || true)
  fi
  [[ -n "$submit_bounds" ]] || return 1

  # One physical tap equals one gameplay action. Never retry after a fixed delay: a fast turn
  # may already have finished and re-enabled the composer before the harness samples again.
  tap_bounds "$submit_bounds"
  echo "action=$index origin=FREEDOM resolvedKind=EXPLORE input=continue forward" | tee -a "$OUT/actions.log"
  return 0
}

read_core_state() {'''
text, count = submit_pattern.subn(submit_replacement, text, count=1)
if count != 1:
    raise RuntimeError(f"Freedom submit patch expected 1 match, found {count}")

replacements = {
    'wait_until_explore_ready "initial" || fail "Khám phá was not ready after fresh Level 0 launch"':
        'wait_until_explore_ready "initial" || fail "Freedom input was not ready after fresh Level 0 launch"',
    'fail "Timed out waiting for Khám phá before action $actions"':
        'fail "Timed out waiting for Freedom input before action $actions"',
    'fail "Real Khám phá tap was not accepted at action $actions"':
        'fail "Real Freedom traversal submit was not accepted at action $actions"',
    'accepted Khám phá actions':
        'accepted Freedom traversal actions classified as EXPLORE',
    '  local label="$1" raw="$OUT/core-${label}.xml"':
        '  local label="$1"\n  local raw="$OUT/core-${label}.xml"',
}
for old, new in replacements.items():
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"sublevel harness anchor expected 1 match, found {count}: {old[:80]}")
    text = text.replace(old, new, 1)

for required in (
    "elif kind == 'input':",
    "elif kind == 'submit':",
    "input text continue%sforward",
    "origin=FREEDOM resolvedKind=EXPLORE",
    "Freedom input was not ready",
):
    if required not in text:
        raise RuntimeError("Freedom sublevel harness contract missing: " + required)

PATH.write_text(text, encoding="utf-8")
print("Sublevel emulator adapted to Freedom composer -> EXPLORE traversal.")

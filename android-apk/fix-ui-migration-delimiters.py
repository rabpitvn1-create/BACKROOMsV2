from pathlib import Path

self_path = Path(__file__)
path = self_path.with_name("migrate-ui-authority.py")
text = path.read_text(encoding="utf-8")
old_open = "presentation_source = r'''\npresentation_script_template = r'''<script id=\"androidGameplayPresentation\">"
new_open = "presentation_source = r\"\"\"\npresentation_script_template = r'''<script id=\"androidGameplayPresentation\">"
if text.count(old_open) != 1:
    raise RuntimeError(f"outer presentation string open anchor count: {text.count(old_open)}")
text = text.replace(old_open, new_open, 1)
old_close = "    html = html.replace(\"</body>\", presentation_script + \"\\n</body>\", 1)\n\n'''\napply = replace_once(apply, \"for forbidden in (\\n\", presentation_source + \"for forbidden in (\\n\", \"canonical gameplay JS insertion\")"
new_close = "    html = html.replace(\"</body>\", presentation_script + \"\\n</body>\", 1)\n\n\"\"\"\napply = replace_once(apply, \"for forbidden in (\\n\", presentation_source + \"for forbidden in (\\n\", \"canonical gameplay JS insertion\")"
if text.count(old_close) != 1:
    raise RuntimeError(f"outer presentation string close anchor count: {text.count(old_close)}")
text = text.replace(old_close, new_close, 1)
old_combined = '''combined = "\\n".join(read(path) for path in [
    apply_path, pressure_path, auto_path, true_turn_path, party_path, entity_rates_path, WORKFLOW,
])'''
new_combined = '''combined = "\\n".join(read(path) for path in [
    apply_path, pressure_path, auto_path, true_turn_path, party_path, entity_rates_path,
])'''
if text.count(old_combined) != 1:
    raise RuntimeError(f"legacy assertion scope anchor count: {text.count(old_combined)}")
text = text.replace(old_combined, new_combined, 1)
path.write_text(text, encoding="utf-8")
self_path.unlink()
print("Fixed one-time migration delimiters and legacy assertion scope.")

from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
path = ROOT / "android-apk/patch-lucia-follower.py"
text = path.read_text(encoding="utf-8")

broken_profile = "profile_anchor = '  val NORMAL = InventoryProfile(maxTypes = 2, maxPerType = 2)\n'"
fixed_profile = "profile_anchor = '  val NORMAL = InventoryProfile(maxTypes = 2, maxPerType = 2)\\n'"
text = text.replace(broken_profile, fixed_profile)

broken_lucia = "policy = replace_once(policy, profile_anchor, '  val LUCIA = InventoryProfile(maxTypes = 3, maxPerType = 100)\n' + profile_anchor, \"Lucia inventory profile\")"
fixed_lucia = "policy = replace_once(policy, profile_anchor, '  val LUCIA = InventoryProfile(maxTypes = 3, maxPerType = 100)\\n' + profile_anchor, \"Lucia inventory profile\")"
text = text.replace(broken_lucia, fixed_lucia)

compile(text, str(path), "exec")
path.write_text(text, encoding="utf-8")
print("Sanitized Lucia patch literals repaired and syntax-checked.")

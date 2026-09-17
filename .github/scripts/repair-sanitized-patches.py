from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

lucia_path = ROOT / "android-apk/patch-lucia-follower.py"
lucia = lucia_path.read_text(encoding="utf-8")
broken_profile = "profile_anchor = '  val NORMAL = InventoryProfile(maxTypes = 2, maxPerType = 2)\n'"
fixed_profile = "profile_anchor = '  val NORMAL = InventoryProfile(maxTypes = 2, maxPerType = 2)\\n'"
lucia = lucia.replace(broken_profile, fixed_profile)
broken_lucia = "policy = replace_once(policy, profile_anchor, '  val LUCIA = InventoryProfile(maxTypes = 3, maxPerType = 100)\n' + profile_anchor, \"Lucia inventory profile\")"
fixed_lucia = "policy = replace_once(policy, profile_anchor, '  val LUCIA = InventoryProfile(maxTypes = 3, maxPerType = 100)\\n' + profile_anchor, \"Lucia inventory profile\")"
lucia = lucia.replace(broken_lucia, fixed_lucia)
compile(lucia, str(lucia_path), "exec")
lucia_path.write_text(lucia, encoding="utf-8")

stats_path = ROOT / "android-apk/patch-character-stat-schema.py"
stats = stats_path.read_text(encoding="utf-8")
stats = stats.replace('    "PROTECTED FOLLOWER / NON-COMBAT",\n', '')
compile(stats, str(stats_path), "exec")
stats_path.write_text(stats, encoding="utf-8")

print("Sanitized patch literals and retired regression markers repaired.")

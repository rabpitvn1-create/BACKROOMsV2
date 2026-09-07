from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core/CombatRuntime.kt"
FACADE = ROOT / "app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
INDEX = ROOT / "app/src/main/assets/index.html"


def section(text: str, start: str, end: str, label: str) -> str:
    a = text.find(start)
    if a < 0:
        raise RuntimeError(f"{label}: start marker missing: {start}")
    b = text.find(end, a + len(start))
    if b < 0:
        raise RuntimeError(f"{label}: end marker missing: {end}")
    return text[a:b]


# Temporary inspection pass. This file is intentionally promoted to the final implementation
# after the generated combat/status patch stack is inspected on CI.
combat = CORE.read_text(encoding="utf-8")
facade = FACADE.read_text(encoding="utf-8")
main = MAIN.read_text(encoding="utf-8")
html = INDEX.read_text(encoding="utf-8")

print("=== PARTY TURN FINAL INSPECTION: CombatRuntime.resolve ===")
print(section(combat, "  fun resolve(state: GameState, actionKind: String, action: String): Resolution {", "\n  fun toJson(state: GameState): JSONObject?", "CombatRuntime.resolve"))
print("=== PARTY TURN FINAL INSPECTION: CombatRuntime model/header ===")
print(combat[:combat.find("  fun resolve(state: GameState")])
print("=== PARTY TURN FINAL INSPECTION: GameCoreFacade.processCombat ===")
print(section(facade, "  fun processCombat(legacyStateJson: String, actionKind: String, action: String): String {", "\n  private fun loadOrMigrate", "GameCoreFacade.processCombat"))
print("=== PARTY TURN FINAL INSPECTION: Android combat bridge ===")
needle = "requireGameCore().processCombat(stateJson, actionKind, action)"
pos = main.find(needle)
if pos < 0:
    raise RuntimeError("Android processCombat bridge missing")
print(main[max(0, pos - 900):pos + 1200])
print("=== PARTY TURN FINAL INSPECTION: renderer role/class ===")
for token in ('x.role===\"player\"', 'PRESSURE_COMBAT_HUD_V1'):
    pos = html.find(token)
    if pos >= 0:
        print(html[max(0, pos - 500):pos + 1200])

print("Party turn final inspection completed.")

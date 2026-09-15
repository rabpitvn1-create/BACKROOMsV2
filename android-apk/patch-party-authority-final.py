from pathlib import Path
import runpy

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
FACADE = ROOT / "app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt"


def method_bounds(source: str, signature: str) -> tuple[int, int]:
    start = source.find(signature)
    if start < 0:
        raise RuntimeError("method not found: " + signature)
    open_brace = source.find("{", start)
    if open_brace < 0:
        raise RuntimeError("opening brace missing: " + signature)
    depth = 0
    state = "code"
    escaped = False
    i = open_brace
    while i < len(source):
        ch = source[i]
        nxt = source[i + 1] if i + 1 < len(source) else ""
        if state == "string":
            if escaped:
                escaped = False
            elif ch == "\\":
                escaped = True
            elif ch == '"':
                state = "code"
        elif state == "char":
            if escaped:
                escaped = False
            elif ch == "\\":
                escaped = True
            elif ch == "'":
                state = "code"
        elif state == "line_comment":
            if ch == "\n":
                state = "code"
        elif state == "block_comment":
            if ch == "*" and nxt == "/":
                state = "code"
                i += 1
        else:
            if ch == '"':
                state = "string"
            elif ch == "'":
                state = "char"
            elif ch == "/" and nxt == "/":
                state = "line_comment"
                i += 1
            elif ch == "/" and nxt == "*":
                state = "block_comment"
                i += 1
            elif ch == "{":
                depth += 1
            elif ch == "}":
                depth -= 1
                if depth == 0:
                    return start, i + 1
        i += 1
    raise RuntimeError("closing brace missing: " + signature)


# Java is now bridge-only for provider Party proposals. The policy decision itself lives in Kotlin.
java = MAIN.read_text(encoding="utf-8")
legacy_call = "else if (characterAddAllowed(before, name, rolls)) party.put(new JSONObject(member.toString()));"
kotlin_call = (
    "else if (com.rabpit.backroom.core.PartyCandidatePolicy.allowsProviderAddition("
    "before.toString(), rolls.toString(), name)) party.put(new JSONObject(member.toString()));"
)
if kotlin_call not in java:
    if java.count(legacy_call) != 1:
        raise RuntimeError(f"Party add bridge anchor count != 1: {java.count(legacy_call)}")
    java = java.replace(legacy_call, kotlin_call, 1)

legacy_remove = (
    'if (party != null && existing >= 0 && containsAny(action, "rời", "tách", "ở lại", "đuổi", '
    '"chia nhóm", "mất dấu")) party.remove(existing);'
)
kotlin_remove = (
    "if (party != null && existing >= 0 && "
    "com.rabpit.backroom.core.PartyCandidatePolicy.allowsRemoval(action)) party.remove(existing);"
)
if kotlin_remove not in java:
    if java.count(legacy_remove) != 1:
        raise RuntimeError(f"Party remove bridge anchor count != 1: {java.count(legacy_remove)}")
    java = java.replace(legacy_remove, kotlin_remove, 1)

signature = "  private boolean characterAddAllowed(JSONObject before, String name, JSONObject rolls)"
if signature in java:
    start, end = method_bounds(java, signature)
    while end < len(java) and java[end] in " \t":
        end += 1
    if end < len(java) and java[end] == "\n":
        end += 1
    java = java[:start] + java[end:]

for marker in (
    "PartyCandidatePolicy.allowsProviderAddition(before.toString(), rolls.toString(), name)",
    "PartyCandidatePolicy.allowsRemoval(action)",
):
    if marker not in java:
        raise RuntimeError("Kotlin Party bridge missing: " + marker)
if "characterAddAllowed(" in java:
    raise RuntimeError("Retired Java Party admission authority survived")
MAIN.write_text(java, encoding="utf-8")


# Recheck the same policy at the trusted Game Core boundary so a caller cannot bypass the Java bridge.
facade = FACADE.read_text(encoding="utf-8")
old_party = '''    val currentFollowers = pending.state.party.memberIds.filter { it != KAI_ID }.toSet()
    (currentFollowers - desiredParty.keys).sorted().forEachIndexed { index, id ->
      commands += PartyCommand("$turnId:GEMINI:PARTY_REMOVE:$index", turnId, KAI_ID, id, CommandSource.GEMINI, PartyCommand.Operation.REMOVE)
    }
    (desiredParty.keys - currentFollowers).sorted().forEachIndexed { index, id ->
      val member = desiredParty.getValue(id)
      val known = pending.state.characters[id]
      commands += PartyCommand(
        "$turnId:GEMINI:PARTY_ADD:$index", turnId, KAI_ID, id, CommandSource.GEMINI, PartyCommand.Operation.ADD,
        consentConfirmed = member.optBoolean("joinConfirmed", false) && known?.metadata?.get("joinEligible") == "true",
        targetPresent = member.optBoolean("present", false) && known?.presence == CharacterPresence.ACTIVE
      )
    }
'''
new_party = '''    val currentFollowers = pending.state.party.memberIds.filter { it != KAI_ID }.toSet()
    if (PartyCandidatePolicy.allowsRemoval(action)) {
      (currentFollowers - desiredParty.keys).sorted().forEachIndexed { index, id ->
        commands += PartyCommand("$turnId:GEMINI:PARTY_REMOVE:$index", turnId, KAI_ID, id, CommandSource.GEMINI, PartyCommand.Operation.REMOVE)
      }
    }
    (desiredParty.keys - currentFollowers).sorted().forEachIndexed { index, id ->
      val member = desiredParty.getValue(id)
      val known = pending.state.characters[id]
      val memberName = member.optString("name").ifBlank { known?.name ?: id }
      val engineAuthorizedJoin = known?.metadata?.get("storyManaged") == "lucia" &&
        known.metadata["joinEligible"] == "true"
      if (!PartyCandidatePolicy.allowsCoreAddition(
          before, rolls, id, memberName, engineAuthorizedJoin
        )) return@forEachIndexed
      commands += PartyCommand(
        "$turnId:GEMINI:PARTY_ADD:$index", turnId, KAI_ID, id, CommandSource.GEMINI, PartyCommand.Operation.ADD,
        consentConfirmed = member.optBoolean("joinConfirmed", false) && known?.metadata?.get("joinEligible") == "true",
        targetPresent = member.optBoolean("present", false) && known?.presence == CharacterPresence.ACTIVE
      )
    }
'''
if new_party not in facade:
    if facade.count(old_party) != 1:
        raise RuntimeError(f"Game Core Party candidate anchor count != 1: {facade.count(old_party)}")
    facade = facade.replace(old_party, new_party, 1)

for marker in (
    "PartyCandidatePolicy.allowsRemoval(action)",
    "PartyCandidatePolicy.allowsCoreAddition(",
    'known?.metadata?.get("storyManaged") == "lucia"',
):
    if marker not in facade:
        raise RuntimeError("Game Core Party authority missing: " + marker)
FACADE.write_text(facade, encoding="utf-8")

print("Party authority final applied: Kotlin owns provider admission/removal policy and rechecks it at Core commit.")

player_authority = ROOT / "patch-player-authority-final.py"
if not player_authority.is_file():
    raise RuntimeError("Player authority finalizer missing: " + player_authority.name)
runpy.run_path(str(player_authority), run_name="__main__")

from pathlib import Path
import json

ROOT = Path(__file__).resolve().parent
KNOWLEDGE_DB = ROOT / "app/src/main/assets/knowledge/knowledge_db.json"
KNOWLEDGE_SOURCE = ROOT / "app/src/main/assets/knowledge/sru_canon.md"
ENGINE = ROOT / "app/src/main/java/com/rabpit/backroom/core/knowledge/KnowledgeContextEngine.kt"
SOURCE_MAP = ROOT / "KNOWLEDGE_SOURCE_MAP.md"

for required in (KNOWLEDGE_DB, KNOWLEDGE_SOURCE, ENGINE, SOURCE_MAP):
    if not required.is_file():
        raise RuntimeError("SRU runtime source missing: " + str(required.relative_to(ROOT)))

source_document = "app/src/main/assets/knowledge/sru_canon.md"

records = [
    {
        "id": "WORLD.SRU.CORE",
        "domain": "WORLD",
        "kind": "organization-hard-canon",
        "text": (
            "SRU (Special Response Unit / Lực lượng Phản ứng Đặc biệt) was founded in 2099 by Eric Ko and is headquartered in Vatican, Italy. "
            "Authority chain: Hội Đồng Bảo An Liên Không Thời Gian -> Cảnh Sát chống hiện tượng dị thường -> SRU. "
            "The Council directly assigns missions. Black Vatican is not SRU command authority; it provides training, military/manpower and deployment support. "
            "Number Ø is Sparda and stands above the combat core of thirteen Numbers I-XIII. Every Number is in roughly the same planetary-catastrophe combat tier; Number labels are not power rankings. "
            "Liz is the confirmed official logistics exception outside the thirteen Numbers. Holder identity, gender, appearance, abilities and history for Numbers II-VIII and XI-XII remain OPEN and must never be invented or revealed."
        ),
        "source": {"document": source_document, "anchor": "1-2"},
        "authority": "USER_CANON",
        "mutability": "IMMUTABLE",
        "priority": 18,
        "tags": ["sru", "special response unit", "13 numbers", "thirteen numbers", "number ø", "black vatican", "vatican"],
        "references": ["WORLD.SRU.NUMBERS", "WORLD.SRU.COMMAND", "WORLD.SRU.MISSIONS"],
        "affordances": []
    },
    {
        "id": "WORLD.SRU.NUMBERS",
        "domain": "WORLD",
        "kind": "organization-roster-lock",
        "text": (
            "SRU Number mapping: Ø Sparda (above I-XIII); I Alastor / Eric Ko; II Leviathan / holder OPEN; III Mammon / holder OPEN; "
            "IV Baal / holder OPEN; V Beelzebub / holder OPEN; VI Rahab / holder OPEN; VII Asmodeus / holder OPEN; VIII Paimon / holder OPEN; "
            "IX Lucifer / Syvial; X Belial / Iris; XI Belphegor / holder OPEN; XII Ashtaroth/Azazel / holder OPEN; XIII Vassago / currently Kai Akechi-Twilight. "
            "The thirteen Number positions represent thirteen Princes of Hell mainly through power/ability correspondence. They are not a strength ranking and all thirteen occupy nearly the same overall combat tier. "
            "Kai is the deliberate XIII exception: he wanted number 13, asked Vassago for it during a drinking session, and Vassago agreed after both became extremely drunk."
        ),
        "source": {"document": source_document, "anchor": "2-3"},
        "authority": "USER_CANON",
        "mutability": "IMMUTABLE",
        "priority": 40,
        "tags": ["sru", "numbers", "number i", "number ix", "number x", "number xiii", "alastor", "lucifer", "belial", "vassago"],
        "references": ["WORLD.SRU.LIZ"],
        "affordances": []
    },
    {
        "id": "WORLD.SRU.COMMAND",
        "domain": "WORLD",
        "kind": "organization-command-lock",
        "text": (
            "Kai Akechi and Eric Ko both hold SRU command authority. Kai is the real top commander in substance, but usually pushes administration and daily reports to Eric, making Eric look like the sole commander from outside. "
            "Ordinary orders require authorization through both Kai and Eric. If one loses the ability to command, the other may decide. "
            "A jointly authorized final order is mandatory for the assigned Number; the Number cannot refuse the objective but retains broad sovereignty over method, tactics and force level unless the order explicitly constrains them. "
            "Doctrine: OBJECTIVE ABSOLUTE / METHOD SOVEREIGN. The thirteen Numbers are not a perfectly synchronized squad and each retains an independent strategic layer. "
            "If Kai and Eric reach an irreconcilable command conflict, RIGHT OF THE LAST STANDING applies: they may fight directly, lethal force is allowed if necessary, and the one still able to stand and continue decides."
        ),
        "source": {"document": source_document, "anchor": "4"},
        "authority": "USER_CANON",
        "mutability": "IMMUTABLE",
        "priority": 38,
        "tags": ["sru", "command", "eric ko", "kai akechi", "final order", "objective absolute", "method sovereign", "last standing"],
        "references": [],
        "affordances": []
    },
    {
        "id": "WORLD.SRU.MISSIONS",
        "domain": "WORLD",
        "kind": "organization-mission-lock",
        "text": (
            "Hội Đồng Bảo An Liên Không Thời Gian directly assigns missions to SRU, but Kai and Eric may reject a mission or alter its objective, scope or conditions before joint approval. "
            "Their rejection right remains valid even in an absolute emergency. If both reject, the Council may transfer the mission to another force. "
            "Very few other forces can handle the highest-tier missions intended for SRU. Huyết Nha is a known alternative force, but generally lacks the capability for those highest-tier SRU missions."
        ),
        "source": {"document": source_document, "anchor": "1,5"},
        "authority": "USER_CANON",
        "mutability": "IMMUTABLE",
        "priority": 42,
        "tags": ["sru", "mission", "hội đồng bảo an liên không thời gian", "huyết nha", "black vatican"],
        "references": [],
        "affordances": []
    },
    {
        "id": "WORLD.SRU.JUDGEMENT",
        "domain": "WORLD",
        "kind": "organization-discipline-lock",
        "text": (
            "SRU has no universal collateral-damage threshold; consequences are judged per mission. Deliberate massacre or intentionally allowing a target to cause severe harm despite a reasonable ability to intervene may trigger Last Judgement. "
            "Last Judgement is judged by Sparda and the thirteen Princes of Hell. Formal judgment is by majority vote, but Sparda's primordial status and power near the Creator make his position close to the final word in practice. "
            "Punishment ranges from a written reprimand to permanent exile in Hell. AFTERMATH ASSESSMENT reviews objective completion, necessary versus avoidable consequences, realistic alternatives available at the time, and whether the Number acted for the mission or personal motives. "
            "Approved Last Judgement flow: ACCUSATION, TESTIMONY, JUDGEMENT."
        ),
        "source": {"document": source_document, "anchor": "6"},
        "authority": "USER_CANON",
        "mutability": "IMMUTABLE",
        "priority": 44,
        "tags": ["sru", "last judgement", "aftermath assessment", "sparda", "phán xét", "kỷ luật"],
        "references": [],
        "affordances": []
    },
    {
        "id": "WORLD.SRU.LIZ",
        "domain": "WORLD",
        "kind": "organization-personnel-lock",
        "text": (
            "Liz is an official SRU member and the confirmed logistics exception outside the thirteen Numbers. She originally held Number XIII as Vassago's representative. "
            "After Vassago agreed to give XIII to Kai during their drunken agreement, Liz ceased to be a Number and became SRU logistics. Kai often teases her with the unofficial nickname 'XIII ½'. "
            "Vassago took Liz in as a child after the Zeiss Event. Locked Zeiss fact: Sparda defeated Michael, Raphael, Gabriel and Ariel. "
            "Liz's deeper identity, ability, age, personality, relationship and history fields remain OPEN and must not be invented."
        ),
        "source": {"document": source_document, "anchor": "3,10"},
        "authority": "USER_CANON",
        "mutability": "IMMUTABLE",
        "priority": 46,
        "tags": ["sru", "liz", "xiii ½", "xiii 1/2", "vassago", "zeiss", "logistics"],
        "references": [],
        "affordances": []
    },
    {
        "id": "WORLD.SRU.BASE_TRAINING",
        "domain": "WORLD",
        "kind": "organization-reference",
        "text": (
            "Approved SRU expansion: protected SRU operational areas in Vatican include Command Chamber, Number Hall and the restricted Zero Gate associated with Number Ø, Hell and Last Judgement; Zero Gate is not routine transport. "
            "Black Vatican training emphasizes power-output control, limiting unnecessary environmental destruction, combat near civilians/protected objectives, fighting alongside another Number without uncontrolled interference, solving lower-power targets without catastrophic force, and mission-specific restrictions. "
            "The core training problem is often not how to hit harder, but how to destroy only what must be destroyed."
        ),
        "source": {"document": source_document, "anchor": "8"},
        "authority": "USER_APPROVED_EXPANSION",
        "mutability": "IMMUTABLE",
        "priority": 52,
        "tags": ["sru", "command chamber", "number hall", "zero gate", "black vatican", "training"],
        "references": [],
        "affordances": []
    },
    {
        "id": "WORLD.SRU.CULTURE_DEPLOYMENT",
        "domain": "WORLD",
        "kind": "organization-reference",
        "text": (
            "Approved SRU expansion: the thirteen Numbers are not automatically friends, family or a perfectly synchronized squad. They may reject proposals, criticize plans and argue before a final order is jointly authorized; after joint authorization, the objective is mandatory while tactical judgment remains personal. "
            "SRU normally avoids deploying more Numbers than necessary because multiple planetary-catastrophe-tier combatants can themselves increase operational risk. Single-Number deployment is common; paired or multi-Number deployment is reserved for broader or layered problems."
        ),
        "source": {"document": source_document, "anchor": "7,9"},
        "authority": "USER_APPROVED_EXPANSION",
        "mutability": "IMMUTABLE",
        "priority": 50,
        "tags": ["sru", "culture", "deployment", "numbers", "team"],
        "references": [],
        "affordances": []
    },
    {
        "id": "STORY.SRU.THIRTEENFOLD",
        "domain": "STORY",
        "kind": "future-canon",
        "text": (
            "FUTURE CANON, NOT CURRENTLY EXPERIENCED CHARACTER KNOWLEDGE: The event 'The BACKROOMS' will be the first operation in which all thirteen Numbers must fight together. "
            "Approved full-deployment protocol THIRTEENFOLD: all Numbers leave lower-priority missions, Black Vatican opens full support capacity, Liz switches SRU logistics to emergency deployment mode, and Number Ø/Sparda is immediately notified. "
            "At the current main-event timeframe this has not happened yet; do not narrate it as past/current experience or reveal future details to characters without valid continuity support."
        ),
        "source": {"document": source_document, "anchor": "7,11"},
        "authority": "USER_CANON",
        "mutability": "BASELINE",
        "priority": 60,
        "tags": ["sru", "thirteenfold", "the backrooms", "future canon", "full thirteen"],
        "references": [],
        "affordances": []
    }
]

knowledge = json.loads(KNOWLEDGE_DB.read_text(encoding="utf-8"))
existing = knowledge.get("records", [])
new_by_id = {record["id"]: record for record in records}
merged = []
seen = set()
for record in existing:
    rid = record.get("id")
    if rid in new_by_id:
        merged.append(new_by_id[rid])
        seen.add(rid)
    else:
        merged.append(record)
for record in records:
    if record["id"] not in seen:
        merged.append(record)
knowledge["records"] = merged

# Retire only stale organization names that directly conflict with the current SRU codex.
for record in knowledge["records"]:
    rid = record.get("id")
    text = record.get("text", "")
    if rid == "STORY.MAIN.SEPARATION":
        text = text.replace("Black Blood/Command", "SRU/Command")
    elif rid == "CHAR.KAI.RUNTIME_CORE":
        text = text.replace("Black Blood captain under Vatican", "SRU captain within Cảnh Sát chống hiện tượng dị thường")
    elif rid == "CHAR.IRIS.RUNTIME_CORE":
        text = text.replace("ARGUS is her Black Blood callsign", "ARGUS is her SRU callsign")
    record["text"] = text

KNOWLEDGE_DB.write_text(json.dumps(knowledge, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

engine = ENGINE.read_text(encoding="utf-8")
mandatory_old = '        "CHAR.KAI.RUNTIME_CORE"\n      ).forEach { add(it, "mandatory hard context") }'
mandatory_new = '        "CHAR.KAI.RUNTIME_CORE",\n        "WORLD.SRU.CORE"\n      ).forEach { add(it, "mandatory hard context") }'
if mandatory_old in engine:
    engine = engine.replace(mandatory_old, mandatory_new, 1)
elif mandatory_new not in engine:
    raise RuntimeError("SRU mandatory knowledge anchor missing in KnowledgeContextEngine.kt")

marker = "      // SRU_RUNTIME_CANON_V1"
if marker not in engine:
    anchor = "      val direct = linkedSetOf<String>()\n"
    if anchor not in engine:
        raise RuntimeError("SRU direct lookup insertion anchor missing in KnowledgeContextEngine.kt")
    block = '''      // SRU_RUNTIME_CANON_V1
      if (hasAny(actionText, "sru", "special response unit", "13 numbers", "thirteen numbers", "13 con số", "number ø")) direct += "WORLD.SRU.CORE"
      if (hasAny(actionText, "number i", "number ix", "number x", "number xiii", "alastor", "vassago")) direct += "WORLD.SRU.NUMBERS"
      if (hasAny(actionText, "eric ko", "final order", "objective absolute", "method sovereign", "last standing")) direct += "WORLD.SRU.COMMAND"
      if (hasAny(actionText, "hội đồng bảo an liên không thời gian", "huyết nha", "nhiệm vụ sru")) direct += "WORLD.SRU.MISSIONS"
      if (hasAny(actionText, "last judgement", "aftermath assessment", "phán xét sru")) direct += "WORLD.SRU.JUDGEMENT"
      if (hasAny(actionText, "liz", "xiii ½", "xiii 1/2", "zeiss")) direct += "WORLD.SRU.LIZ"
      if (hasAny(actionText, "command chamber", "number hall", "zero gate", "huấn luyện black vatican")) direct += "WORLD.SRU.BASE_TRAINING"
      if (hasAny(actionText, "thirteenfold", "full thirteen", "the backrooms")) direct += "STORY.SRU.THIRTEENFOLD"
'''
    engine = engine.replace(anchor, anchor + block, 1)
ENGINE.write_text(engine, encoding="utf-8")

source_map = SOURCE_MAP.read_text(encoding="utf-8")
section_marker = "## User-locked SRU canon supplement"
if section_marker not in source_map:
    source_map += '''

## User-locked SRU canon supplement

Latest explicit user canon for SRU is packaged in `app/src/main/assets/knowledge/sru_canon.md`. This supplement has `USER_CANON` authority and does not infer OPEN Number-holder identities. The future `The BACKROOMS` / THIRTEENFOLD entry is future canon only and must not become current character knowledge without continuity support.

| Runtime ID | Source | Anchor | Mutability |
| --- | --- | --- | --- |
| `WORLD.SRU.CORE` | `app/src/main/assets/knowledge/sru_canon.md` | 1-2 | IMMUTABLE |
| `WORLD.SRU.NUMBERS` | `app/src/main/assets/knowledge/sru_canon.md` | 2-3 | IMMUTABLE |
| `WORLD.SRU.COMMAND` | `app/src/main/assets/knowledge/sru_canon.md` | 4 | IMMUTABLE |
| `WORLD.SRU.MISSIONS` | `app/src/main/assets/knowledge/sru_canon.md` | 1,5 | IMMUTABLE |
| `WORLD.SRU.JUDGEMENT` | `app/src/main/assets/knowledge/sru_canon.md` | 6 | IMMUTABLE |
| `WORLD.SRU.LIZ` | `app/src/main/assets/knowledge/sru_canon.md` | 3,10 | IMMUTABLE |
| `WORLD.SRU.BASE_TRAINING` | `app/src/main/assets/knowledge/sru_canon.md` | 8 | IMMUTABLE |
| `WORLD.SRU.CULTURE_DEPLOYMENT` | `app/src/main/assets/knowledge/sru_canon.md` | 7,9 | IMMUTABLE |
| `STORY.SRU.THIRTEENFOLD` | `app/src/main/assets/knowledge/sru_canon.md` | 7,11 | BASELINE / FUTURE |
'''
SOURCE_MAP.write_text(source_map, encoding="utf-8")

# Final contracts.
final = json.loads(KNOWLEDGE_DB.read_text(encoding="utf-8"))
final_records = final.get("records", [])
ids = [record.get("id") for record in final_records]
for rid in new_by_id:
    if ids.count(rid) != 1:
        raise RuntimeError(f"SRU knowledge contract: expected exactly one {rid}, found {ids.count(rid)}")

final_text = "\n".join(record.get("text", "") for record in final_records)
for forbidden in (
    "Black Blood captain under Vatican",
    "ARGUS is her Black Blood callsign",
    "Black Blood/Command",
):
    if forbidden in final_text:
        raise RuntimeError("Stale organization canon survived SRU patch: " + forbidden)

for required in (
    "OBJECTIVE ABSOLUTE / METHOD SOVEREIGN",
    "RIGHT OF THE LAST STANDING",
    "Last Judgement",
    "XIII ½",
    "FUTURE CANON, NOT CURRENTLY EXPERIENCED CHARACTER KNOWLEDGE",
):
    if required not in final_text:
        raise RuntimeError("SRU knowledge marker missing: " + required)

engine_final = ENGINE.read_text(encoding="utf-8")
for required in ("WORLD.SRU.CORE", "SRU_RUNTIME_CANON_V1", "STORY.SRU.THIRTEENFOLD"):
    if required not in engine_final:
        raise RuntimeError("SRU engine marker missing: " + required)

# Protect unpublished Number-holder identities: the source/runtime may name only OPEN for these slots.
numbers_record = next(record for record in final_records if record.get("id") == "WORLD.SRU.NUMBERS")
for roman in ("II", "III", "IV", "V", "VI", "VII", "VIII", "XI", "XII"):
    if f"{roman} " not in numbers_record["text"] or "/ holder OPEN" not in numbers_record["text"]:
        raise RuntimeError("SRU OPEN holder contract missing for Number " + roman)

print("SRU runtime canon wired: organization, Numbers, command, missions, discipline, logistics and future-boundary retrieval.")

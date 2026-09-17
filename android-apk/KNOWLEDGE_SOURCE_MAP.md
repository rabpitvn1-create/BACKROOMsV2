# The BACKROOMS — Drive Source Map for Runtime Knowledge

Status: CURRENT SOURCE MAP
Last character sync: 2026-09-17

This file maps runtime knowledge to its authoritative Google Drive source. Local mirrors, old saves/logs and retired patch artifacts are not canon authorities when they conflict with a newer Drive source.

## Authority order

1. Latest explicit user instruction.
2. Current Text Game rules and current campaign state for runtime facts.
3. Current Character Codex for immutable identity, personality, abilities, equipment, knowledge limits, relationship baselines, address rules and hard locks.
4. Current World canon for world/level/entity/item facts.
5. Current story/continuity for mutable campaign state.
6. Writing rules for prose/dialogue/POV/knowledge boundaries.
7. Reference/benchmark material only where its own source says it applies.

`UNKNOWN`, `OPEN`, `CHƯA KHÓA` or equivalent remains unknown. Runtime must not fill these fields by inference.

## Current machine-readable character canon

Runtime character source: `app/src/main/assets/knowledge/characters_current.json`.
Human audit wiki: `CHARACTER_CODEX_CURRENT.md`.

The old `app/src/main/assets/knowledge/knowledge_db.json` predates the 2026-09-17 Characters Drive sync and contains legacy character rows. Those legacy character rows are **not authoritative** for Kai/Iris/Syvial/Lucia. World/level/entity records in that file may still be used only when they do not conflict with a newer Drive source.

Current game state embeds the compact current character canon so the Java-only Gemini path receives the latest character locks without restoring the retired Kotlin/LiteRT knowledge engine.

## Character Drive audit

Directly opened from the Google Drive folder `02_CHARACTERS` before this sync:

- `Kai_Codex.docx` — Drive ID `1TDBphEo1wxrdlRXTI9WUOJPinmWv1PHq` — current revision R12.
- `Iris_Codex.docx` — Drive ID `1rx00_WLp1fmf-GPDMw8-ewxwgLaHeJ9k` — current revision R07.
- `Syvial_Codex.docx` — Drive ID `1Bqg24Nix78nhzSoE-YuiUdEt4oWrvdCY` — current Drive canon.
- `Lucia_Codex.docx` — Drive ID `1pv5gvg51oHqNtcCaz5xbjUie5xvxDM6wKCvlAlKFiUQ` — current revision R03.

### Kai source map

| Runtime concept | Source anchor | Current lock |
| --- | --- | --- |
| Identity / organization | `KAI-ID-01`, quick lock | Kai Akechi / Twilight; SRU Captain in anti-anomalous police. Legacy Black Blood/Vatican organization continuity is not current. Public SRU classification is human; half-human/half-demon son of Sparda and Eve is KNOWLEDGE_LOCK. Origin era 2299 is not birth year; true age unknown. |
| Sparda Core | `KAI-CORE-SPARDA-01` | Infinite demon power; no intrinsic depletion/cooldown/corruption/berserk. Powers regeneration and self-repair of all currently equipped gear. |
| Devil Trigger | `KAI-DT-01` | No intrinsic duration cap/cooldown/backlash/control loss. |
| Guilty Crown Override | `KAI-ULT-GCO-01` | Exactly 24 Sparda demonic 5.56×45 mm shots through SRU Assault Rifle MK19 while external time is fully stopped. |
| SRU Assault Rifle MK19 | `KAI-EQP-SRU-AR-MK19-01` | Current signature weapon. Physical 5.56×45 NATO, 30-round physical magazine, 700–950 rpm, 368 mm barrel, effective range about 500–600 m; physical ammo finite. Sparda demonic 5.56×45 forms directly from Core and does not consume physical ammo. |
| SRU-MK20 | `KAI-EQP-SRU-MK20-01` | Current powered armor. R12 visual lock: black/gunmetal + ivory, small brass/gold accents, head/face exposed, very long silver-white high ponytail. |
| Omnivault | `KAI-EQP-OMNIVAULT-01`, `KAI-WEAK-01` | Infinite storage/retrieval for inanimate objects + Restore of the same existing equipment. 24h per-item Restore cooldown. Old Scan/Copy/Create/Marked/Upgrade mechanics are removed. |

### Iris source map

| Runtime concept | Source anchor | Current lock |
| --- | --- | --- |
| Identity / organization | `IRIS-QUICK-01`, identity sections | Iris has no locked surname; ARGUS is SRU combat callsign. Scout / Target Eliminator in Kai's SRU team. |
| Origin | `IRIS-BELIAL-01`, `IRIS-ORIGIN-KAI-01` | Half-human/half-demon daughter of Belial and a deceased human mother. Mother's identity/details and Iris's birth era remain UNKNOWN. Kai was the first SRU member to discover her; SRU later standardized Project 07. |
| ARGUS Terrain Read | `IRIS-SCOUT-TERRAIN-01`, `IRIS-SCOUT-GROUND-01`, `IRIS-SCOUT-TARGET-01` | Direct observation + local Recon Frame sensors + terrain/route/cover/trace analysis. No omniscience, wall vision, remote cameras or automatic true-form detection. |
| Thousandfold Cognition | `IRIS-THOUSANDFOLD-01` | Information processing up to 1:1000; does not accelerate body/weapon mechanics 1000x. |
| Ivory & Ebony | `IRIS-COMBAT-TWIN-01`, `IRIS-AMMO-DEMONIC-01` | Twin mechanical pistols; bullets form directly from Iris's demon power. Infinite source does not mean infinite ROF/durability/damage/accuracy. |
| Recon Frame | `IRIS-EQP-ARMOR-01` | Current SRU Recon Frame R03, mobile scout/ranged-combat configuration. |
| Unknown combat tier | `IRIS-UNKNOWN-01`, canon gate | Exact combat tier remains UNKNOWN; do not infer UR+ from Kai/Syvial. |
| Kai relationship | `IRIS-REL-KAI-01` | Iris has romantic feelings for Kai; Kai knows but reciprocation is not locked. Iris uses `anh–em` with Kai. |
| Syvial relationship | `IRIS-REL-SYVIAL-01` | Friends/trusted teammates with romantic rivalry; not enemies. Exact Iris↔Syvial address remains UNKNOWN. |

### Syvial source map

| Runtime concept | Source anchor | Current lock |
| --- | --- | --- |
| Identity / organization | `SYVIAL-ID-01`, `SYVIAL-OVERVIEW-01` | Half-human/half-demon daughter of Lucifer; mother unknown. Origin era 2299, true age unknown. UR+, same overall tier as Kai. SRU deputy leader of Kai's team. |
| Personality / yandere | `SYVIAL-YANDERE-01`, relationship/action locks | Heavy yandere toward Kai while lucid, intelligent and socially capable. Values Kai's voluntary choice; no random murder, mission sabotage, mind-control or memory erasure merely to keep him. |
| Lucifer Core | `SYVIAL-CORE-LUCIFER-01` | Infinite demon power; powers/enhances established physiology, armor and GodKiller. Not a second intelligence. |
| Devil Trigger | `SYVIAL-DT-01` | No invented intrinsic duration/cooldown/control loss. |
| GodKiller | `SYVIAL-EQP-GODKILLER-01` | Massive purely mechanical greatsword; never a gun/gunblade. Can be recalled unless its link is directly blocked. |
| Lucifer Armor | `SYVIAL-EQP-LUCIFER-ARMOR-01` | Mobile black/gunmetal/silver mecha armor with magenta accents. Pointed head modules are mechanical. Very durable and fast self-repairing, not absolutely indestructible. |
| GodKiller Override | `SYVIAL-ULT-GKO-01` | Exactly 24 cuts while external time is fully stopped. |

### Lucia source map

| Runtime concept | Source anchor | Current lock |
| --- | --- | --- |
| Identity | R03 identity / quick lock | Legal name Hứa Thuý Mai. Lucia Lục is military nickname/callsign. Human Vietnamese woman, age 19, Hoa Kiều, great-great-granddaughter of the Hứa family. |
| Background | R03 quick lock | One year military service/training via Vietnam + USA; entrance result excellent. Skilled human tactical riflewoman, not supernatural/UR+. |
| Story ownership | Level 0 encounter lock | Fixed story-owned Level 0 encounter; not random/quest spawn and not model-created on demand. Not part of the initial SRU Async group; initial group is Kai/Iris/Syvial. |
| Gameplay stats | R03 gameplay lock | HP 100, STR 7, DF 7, AGI 8, CRIT 7; +2 HP after every 3 separately completed turns with save/load persistence. |
| Equipment | R03 equipment sections | Personalized M4A1, combat dagger, military navigation watch. Baseline M4A1 physical ammo: 60 in main magazines + 90 reserve = 150. Black handgun visible in thigh holster but exact model/ammo/gameplay remain OPEN. |
| Inventory | R03 gameplay lock | 8 item types, maximum 100 units each; Equipment separate from Inventory. |
| Relationship/address | R03 open fields | Kai relationship and address remain OPEN. |

## Relationship / address locks

- `Iris -> Kai`: `anh–em`.
- `Syvial -> Kai`: Syvial uses `em`, normally calls him `anh` or `Kai`; continuity controls deeper romantic address.
- `Kai -> Syvial`: Kai uses `anh`, calls her `Syvial` or `em`.
- `Iris <-> Syvial`: exact address remains UNKNOWN unless a newer source locks it.
- `Lucia <-> Kai`: relationship/address remain OPEN.

## World source map

World data remains sourced from Drive, independent of the character sync:

| Runtime domain | Source |
| --- | --- |
| World core | `01_WORLD/world.md` |
| Levels | `01_WORLD/level.md` |
| Entities | `01_WORLD/entity.md` |
| Items/resources | `01_WORLD/items.md` |

Adding future levels/entities remains append-only. Character codex must not be used to invent world facts.

## Game / story sources

| Domain | Source |
| --- | --- |
| Text Game hard rules | `05_TextGame/RULES/TEXT_GAME_RULES.md` |
| GM fairness / consequences | `05_TextGame/RULES/GAME_MASTER_RULES.md` |
| Main campaign premise/objectives | `05_TextGame/STORY/MAIN_STORY.md` |
| Mutable campaign facts | Live validated save / committed runtime continuity |

## Writing sources

| Domain | Source |
| --- | --- |
| Dialogue generation/address discipline | `00_RULES/CONVERSATION.MD` + character relationship locks |
| Knowledge boundary | `00_RULES/worldcodex.md` + forbidden-writing rules |
| Competence preservation | forbidden-writing rules + `worldcodex.md` + GM rules |
| POV/prose | `00_RULES/WayOfWriting.md` |
| Horror | `00_RULES/Supernatural_Horror_Craft_Guide.md` |
| Intimacy | `00_RULES/Intimacy_Writing_Guide.md` |

## Java-only runtime notes

The former patch-generated Kotlin/LiteRT knowledge pipeline is retired. Current Android runtime is Java 17 plus WebView HTML/JavaScript. Character canon is injected into the game state as a compact `characterCanon` block so the existing Java Gemini orchestration receives it every AI turn without Python, Kotlin or LiteRT.

Rules:

1. Current Drive character canon overrides conflicting legacy character facts in old logs/local mirrors.
2. Runtime campaign state still controls mutable facts such as current location, injuries, acquired/lost items and relationship development when those facts do not contradict immutable character canon.
3. KNOWLEDGE_LOCK never becomes character knowledge automatically.
4. UNKNOWN/OPEN stays unknown.
5. Lucia's codex is knowledge that she exists in the setting; it is not permission to spawn her outside her fixed Level 0 story encounter.
6. New Turn 1 baseline uses SRU Assault Rifle MK19 + SRU-MK20 + Omnivault Ring. Untouched Turn 1 saves with the old White Wraith baseline are migrated; progressed campaigns are not blindly rewritten.

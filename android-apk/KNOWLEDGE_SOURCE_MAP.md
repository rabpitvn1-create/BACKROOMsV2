# The BACKROOMS — Drive Source Map for Runtime Knowledge

Status: CURRENT SOURCE MAP
Last character sync: 2026-09-21

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
Runtime compact mirror: `app/src/main/assets/index.html` → `CURRENT_CHARACTER_CANON`.

Compatibility note: the machine-readable file retains internal key `characters.kai` only for existing save/runtime compatibility. Its canonical identity is Cao Minh; the internal key must never leak into narration or UI.

The current `app/src/main/assets/knowledge/knowledge_db.json` contains Cao Minh R15 core/story records. Any residual legacy protagonist wording outside the Cao Minh character records is not a character-canon authority and must not override the current Drive source.

Current game state embeds the compact current character canon so the Java-only Gemini path receives the latest character locks without restoring the retired Kotlin/LiteRT knowledge engine.

## Character Drive audit

Directly opened from the Google Drive folder `02_CHARACTERS` before this sync:

- `Cao_Minh_Codex.docx` — Drive ID `1TDBphEo1wxrdlRXTI9WUOJPinmWv1PHq` — current revision R15.
- `Iris_Codex.docx` — Drive ID `1rx00_WLp1fmf-GPDMw8-ewxwgLaHeJ9k` — current revision R07.
- `Syvial_Codex.docx` — Drive ID `1Bqg24Nix78nhzSoE-YuiUdEt4oWrvdCY` — current Drive canon.
- `Lucia_Codex.docx` — Drive ID `1pv5gvg51oHqNtcCaz5xbjUie5xvxDM6wKCvlAlKFiUQ` — current revision R03.

### Cao Minh source map

| Runtime concept | Source anchor | Current lock |
| --- | --- | --- |
| Identity / title | `CAO-ID-01`, `CAO-QUICK-01` | Cao Minh / Vạn Giới Ma Tôn; Ma Đạo Kiếm Tu; strongest known cultivator of original world; no sect/empire/organization ownership. Original world name/era/detailed realms remain OPEN. |
| Hidden origin | `CAO-SECRET-01` | KNOWLEDGE_LOCK: half-human/half-demon, ancient inheritance linked to Vạn Quỷ Ma Tôn. Vạn Quỷ Ma Tôn is inheritance source, not Cao Minh's title. |
| Visual | `CAO-VIS-01` | R15: long black high ponytail, dark-red eyes, exposed face, black-red Huyết Ma Chiến Khải and a large straight broad Huyết Ma Kiếm with red inscriptions/Ma Luân. |
| Personality / code | `CAO-PER-01`, `CAO-CODE-01` | Confident, relaxed, lazy, wine-loving, sarcastic; no proactive harm to innocents; protects accepted allies when practical rescue remains. |
| Combat / sword sense | `CAO-COMBAT-01` | High sword/divine sense, weak-point and trajectory reading, sword control, close combat and multi-angle handling; attacks outside direct sight still require valid information. |
| Vạn Quỷ Ma Tâm | `CAO-CORE-VQM-01` | Đạo Cơ + Ma Hải + central origin; vast ma-nguyên source with no canonically locked intrinsic ceiling; enhances body/reflex/perception/divine sense/regeneration and nourishes bound artifacts. |
| Vạn Quỷ Ma Thân | `CAO-VQMS-01` | Full release without inherent control loss, corruption, invented duration cap or backlash. |
| Huyết Ma Nhị Thập Tứ Trảm | `CAO-ULT-HUYETMA24-01` | External time fully stops; exactly 24 strikes; time resumes only after strike 24. |
| Huyết Ma Kiếm | `CAO-EQP-HUYET-MA-KIEM-01` | Bound demonic greatsword; divine-sense control/recall; existing sword can progressively regenerate, never duplicates itself. |
| Huyết Ma Chiến Khải | `CAO-EQP-HUYET-MA-KHAI-01` | Bound black-red Ma Khải, second-skin operation, Ma Trảo/Huyết Ấn/Đạp Hư Ma Văn; can progressively re-form if damaged. |
| Vạn Tàng Giới | `CAO-EQP-VANTANG-01`, `CAO-WEAK-01` | Stores inanimate matter. Phản Bổn restores the same existing artifact to its own best historical state; no copy/create/upgrade-beyond-history/living storage. One-day-one-night per-artifact Phản Bổn cooldown after success. |
| Backrooms entry / knowledge | `CAO-BACKROOMS-01` | Ma Sơn → unexplained fall; no default Level/Entity/rule knowledge; investigate from evidence; returning home is secondary. |
| Dialogue / Diệp Minh | `CAO-DLG-01` | Natural complete speech with humor/sarcasm; no constant “bổn tọa”. Only the existence of a feud with Diệp Minh is locked; details remain OPEN. |
| Gameplay projection | `CAO-GAMEPLAY-R15-01` | Tứ Liên 30%/4 hits/170% + Bleeding; Trấn Hồn 20%/4 hits/130% + Stun; Ma Độn 20%/2 hits/147%; Thiên Ma Bộ 30%/+50pp Evasion/3 turns; Ultimate every turn 3×n = 24×10 HP, Accuracy 200%, ignore Evasion, suppress AUTO rolls. |
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
| Story ownership | Level 0 encounter lock | Fixed story-owned Level 0 encounter; not random/quest spawn and not model-created on demand. Not part of the Cao Minh Prologue; old SRU/Async initial-group continuity is retired. |
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
6. New Turn 1 baseline uses Cao Minh with Huyết Ma Kiếm + Huyết Ma Chiến Khải + Vạn Tàng Giới. Untouched Turn 1 saves from the Kai/SRU opening are migrated; progressed saves are not blindly rewritten.

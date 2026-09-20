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

- `Cao_Minh_Codex.docx` — Drive ID `1TDBphEo1wxrdlRXTI9WUOJPinmWv1PHq` — current revision R15.
- `Iris_Codex.docx` — Drive ID `1rx00_WLp1fmf-GPDMw8-ewxwgLaHeJ9k` — current revision R07.
- `Syvial_Codex.docx` — Drive ID `1Bqg24Nix78nhzSoE-YuiUdEt4oWrvdCY` — current Drive canon.
- `Lucia_Codex.docx` — Drive ID `1pv5gvg51oHqNtcCaz5xbjUie5xvxDM6wKCvlAlKFiUQ` — current revision R03.

### Cao Minh source map

| Area | Drive anchors | Current lock |
|---|---|---|
| Identity / position | `CAO-ID-01`, `CAO-OVERVIEW-01` | Cao Minh / Vạn Giới Ma Tôn; Ma Đạo Kiếm Tu; đệ nhất cường giả của thế giới nguyên sinh trước khi rơi vào Backrooms. |
| Personality / code | `CAO-PER-01`, `CAO-CODE-01` | Phóng túng, hay châm chọc, không tự xem mình là anh hùng; không chủ động hại người vô tội; phản ứng rất nhanh khi nguy hiểm thật. |
| Vạn Quỷ Ma Tâm | `CAO-CORE-VQM-01` | Đạo Cơ / Ma Hải / nguồn ma nguyên trung tâm; không tự bịa trần nội tại, cooldown, phản phệ hoặc mất kiểm soát. |
| Vạn Quỷ Ma Thân | `CAO-VQMS-01` | Giải phóng bản nguyên nhưng giữ nguyên ý thức, ký ức, tính cách và phán đoán. |
| Huyết Ma Kiếm | `CAO-EQP-HUYET-MA-KIEM-01` | Bản mệnh đại kiếm ma đạo; kiếm thể, ngự kiếm, Huyết Sát Ma Khí. |
| Huyết Ma Chiến Khải | `CAO-EQP-HUYET-MA-KHAI-01` | Bản mệnh Ma Khải đen–đỏ, liên kết trực tiếp với ma nguyên. |
| Vạn Tàng Giới | `CAO-EQP-VANTANG-01` | Lưu trữ vật vô tri; Phản Bổn chính pháp bảo hiện hữu, không tạo bản sao/sinh linh. |
| Huyết Ma Nhị Thập Tứ Trảm | `CAO-ULT-HUYETMA24-01` | Đúng 24 trảm trong thời gian ngoại giới dừng hoàn toàn. |
| Backrooms entry | `CAO-BACKROOMS-01` | Từ Ma Sơn rơi khỏi thế giới nguyên sinh; không biết trước tên Level/Entity/quy luật; ưu tiên khám phá và kiểm chứng, đường về là mục tiêu thứ hai. |
| Secret origin | `CAO-SECRET-01` | Huyết thống bán nhân/bán ma và truyền thừa Vạn Quỷ Ma Tôn là KNOWLEDGE_LOCK. |

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

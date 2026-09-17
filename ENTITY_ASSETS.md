# BACKROOMS Entity Assets

Toàn bộ sprite Entity dùng trong APK nằm trực tiếp tại:

`android-apk/app/src/main/assets/entity/`

Runtime overlay dùng canonical Entity key trùng chính xác với tên file bỏ phần mở rộng `.png`. Không có manifest từ xa và không tải ảnh Entity từ mạng.

| Canonical Entity key | Local asset |
|---|---|
| `hound` | `hound.png` |
| `clump` | `clump.png` |
| `duller` | `duller.png` |
| `deathmoth` | `deathmoth.png` |
| `hostile_faceling` | `hostile_faceling.png` |
| `false_puddle` | `false_puddle.png` |
| `paintings` | `paintings.png` |
| `smiler` | `smiler.png` |
| `skin-stealer` | `skin-stealer.png` |
| `predatory_window` | `predatory_window.png` |
| `biological_pipeline` | `biological_pipeline.png` |
| `wretch` | `wretch.png` |
| `cable_mimic` | `cable_mimic.png` |
| `the_beast_of_level_5` | `the_beast_of_level_5.png` |
| `hotel_corpse_lure` | `hotel_corpse_lure.png` |
| `jeff_the_killer` | `jeff_the_killer.png` |
| `jane_the_killer` | `jane_the_killer.png` |
| `slenderman` | `slenderman.png` |
| `diep_minh` | `diep_minh.png` |

## Encounter runtime

Main Game Core owns Entity spawning through `EntityCore` and `app/src/main/assets/knowledge/entity_encounters.json`.

- There is no shared spawn-rate pool.
- Every current-canon auto-spawn Entity has its own fixed independent roll between **1.00% and 1.50%** on each eligible world-advancing gameplay turn.
- Eligibility is constrained by the current Level according to the current `01_WORLD/entity.md` canon.
- If an Entity encounter is already active, Core does not roll a replacement Entity.
- If multiple independent rolls succeed on the same turn, Core selects one of those successful rolls because runtime supports one active encounter overlay at a time.
- Gemini does not choose the spawned Entity and cannot replace `flags.entityEncounterKey`.
- `jane_the_killer`, `slenderman` and `diep_minh` remain local overlay assets for legacy-save compatibility but are not auto-spawned because the current Entity canon does not define them as Level 0–6 encounter records.

Current fixed rates are stored only in `entity_encounters.json`; that file is the machine-readable encounter authority so documentation and runtime cannot quietly grow two different probability systems.

Snapshot overlay reads:

`file:///android_asset/entity/<canonical-key>.png`

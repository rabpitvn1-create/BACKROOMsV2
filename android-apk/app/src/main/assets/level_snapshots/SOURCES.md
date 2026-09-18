# Level Snapshot image sources

The APK packages snapshot assets locally so they are available offline.

## Legacy Escape the Backrooms snapshot set

These seven legacy files were retrieved from the Escape the Backrooms Wiki CDN on 2026-08-20 and are preserved for backward compatibility.

| Local asset | Wiki page | Original CDN asset |
| --- | --- | --- |
| `level_0.webp` | https://escapethebackrooms.fandom.com/wiki/Level_0 | https://static.wikia.nocookie.net/escapethebackrooms/images/3/33/Lobby.png/revision/latest |
| `level_1.webp` | https://escapethebackrooms.fandom.com/wiki/Level_1 | https://static.wikia.nocookie.net/escapethebackrooms/images/6/69/Level_1.png/revision/latest |
| `level_2.webp` | https://escapethebackrooms.fandom.com/wiki/Level_2 | https://static.wikia.nocookie.net/escapethebackrooms/images/c/cb/Level_2.jpg/revision/latest |
| `level_3.webp` | https://escapethebackrooms.fandom.com/wiki/Level_3 | https://static.wikia.nocookie.net/escapethebackrooms/images/e/ed/Level_3.png/revision/latest |
| `level_4.webp` | https://escapethebackrooms.fandom.com/wiki/Level_4 | https://static.wikia.nocookie.net/escapethebackrooms/images/2/29/Level_4.png/revision/latest |
| `level_5.webp` | https://escapethebackrooms.fandom.com/wiki/Level_5 | https://static.wikia.nocookie.net/escapethebackrooms/images/5/52/Level_5.png/revision/latest |
| `level_6.webp` | https://escapethebackrooms.fandom.com/wiki/Level_6 | https://static.wikia.nocookie.net/escapethebackrooms/images/8/88/Level_6.jpg/revision/latest |

## Curated Backrooms Wikidot snapshot pool

Imported on 2026-09-17 from the Backrooms Wiki/Wikidot level pages, then audited again on 2026-09-18 for actual in-game Snapshot use.

The runtime selects a local fallback image only from the current Level and turn number. It does **not** know whether the player is currently inside a special sub-area, historical location, blackout event, crate encounter, railway section, Boiler Room, Red Room, or other conditional scene. Because of that, the retained pool is intentionally stricter than the source wiki gallery: only broadly representative environmental scenes remain.

Images that were valid illustrations on the wiki but misleading or visually unhelpful as an unconditional gameplay Snapshot were removed rather than replaced.

### Level 0 — 3 retained assets

Page: https://backrooms-wiki.wikidot.com/level-0

- `wiki/level_0/01.webp` — general Level 0 / original Backrooms scene.
- `wiki/level_0/02.webp` — general architectural variation.
- `wiki/level_0/03.webp` — general pillar/hall variation.

Removed from the gameplay pool: the hole-grid, blackout-zone, and Red Room-specific visuals because the fallback selector cannot know that the player is currently in those conditions.

### Level 1 — 2 retained assets

Page: https://backrooms-wiki.wikidot.com/level-1

- `wiki/level_1/01.webp` — general Habitable Zone hall.
- `wiki/level_1/02.webp` — general Habitable Zone interior.

Removed: Flickering-event and crate-specific visuals. Those are conditional phenomena and were being shown on unrelated turns.

### Level 2 — 2 retained assets

Page: https://backrooms-wiki.wikidot.com/level-2

- `wiki/level_2/01.webp` — representative utility tunnel.
- `wiki/level_2/02.webp` — representative pipe-heavy corridor.

Removed: close-ups of fluorescent fixtures/doors and historical or location-specific imagery tied to the Macchina, Aiding Stragglers, B.N.T.G. railway construction/tracks, and blackout state. Those images are valid article illustrations but poor unconditional scene snapshots.

### Level 3 — 5 retained assets

Page: https://backrooms-wiki.wikidot.com/level-3

- `wiki/level_3/01.webp`
- `wiki/level_3/02.webp`
- `wiki/level_3/03.webp`
- `wiki/level_3/04.webp`
- `wiki/level_3/05.webp`

These are retained as general Electrical Station hallway/environment references.

Removed: Electrical Room, Assembly Line, Boiler Room, and Sanctum-specific gallery images because they represent particular rooms that the fallback selector cannot infer from gameplay state.

### Level 4 — 1 retained asset

Page: https://backrooms-wiki.wikidot.com/level-4

- `wiki/level_4/01.webp`

### Level 5 — 1 retained asset

Page: https://backrooms-wiki.wikidot.com/level-5

- `wiki/level_5/01.webp` — Main Hall, the broadly representative Level 5 scene.

Removed: both Boiler Room-specific images. The game can be in the Main Hall, Beverly Room, or elsewhere in Level 5, so rotating Boiler Room images unconditionally was misleading.

### Level 6 — no retained local fallback image

Page: https://backrooms-wiki.wikidot.com/level-6

The hand-drawn map was removed from the Snapshot pool. It is useful as wiki documentation, but it is not a scene and therefore reads as meaningless when displayed as the game's current visual state. No replacement was added.

## Attribution and licensing

Do not assume one blanket license for every image merely because it appears on the wiki. Before redistributing these assets outside this project, check the image-specific attribution/licensing information and the Licensing/Citations section of the corresponding source page, and preserve any required credit or share-alike terms.

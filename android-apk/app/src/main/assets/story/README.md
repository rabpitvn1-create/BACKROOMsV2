# BACKROOMsV2 — Authored Story Data

> **Status:** Architecture contract / story data specification.  
> **Important:** The presence of this directory does **not** mean StoryCore, story-driven exits, scripted encounters, save migration, or route-streak replacement are already implemented in runtime code.

## Purpose

This directory is the canonical home for **authored story content** used by BACKROOMsV2.

The intended architecture is:

**AUTHOR/STORY DATA → PLAYER A/B/C → CORE STATE/CONSEQUENCE → AI PERFORMANCE**

In plain terms:

- the author decides what is actually allowed to happen in the story;
- A/B/C choices drive real story progression;
- Core systems own game state, consequences, mechanics, exits, encounters, and validation;
- AI narrates and performs the current scene;
- AI is **not** the primary story director.

The goal is **AUTHORED GAME, GENERATIVE PERFORMANCE**.

---

## What belongs here

Put authored story data here, including:

- Level story structure;
- Chapters;
- Story nodes/scenes;
- authored narrative source;
- required story facts/events;
- forbidden reveals;
- choice A/B/C definitions;
- deterministic next-node links;
- story flags;
- important dialogue anchors;
- snapshot bindings;
- scripted encounter requests;
- story conditions for making an exit ready.

Do **not** use this directory as a replacement for:

- world/Level canon;
- character canon;
- Entity registry or encounter rules;
- combat mechanics;
- inventory/item definitions;
- live save state;
- general AI style examples.

Existing machine-readable knowledge remains under:

`app/src/main/assets/knowledge/`

The current runtime authority map is documented in:

`android-apk/KNOWLEDGE_SOURCE_MAP.md`

Story content must respect current world, character, Entity, Level, and live-save authority. Story files must not silently override them.

---

## Recommended directory layout

```text
story/
├── README.md
├── story_manifest.json
├── level_0/
│   ├── chapter_01.json
│   ├── chapter_02.json
│   └── ...
├── level_1/
│   └── ...
└── ...
```

Prefer **one Chapter per file**.

A Chapter may contain roughly 5,000–6,000 words of total authored source, but that does **not** mean a player receives 5,000–6,000 words in one turn.

A Chapter is played across many StoryNodes/turns.

Do not load or send an entire Chapter to the AI when only one current scene is needed.

---

## Story manifest

`story_manifest.json` is the index of available authored story content.

Conceptual example:

```json
{
  "schemaVersion": 1,
  "levels": {
    "0": {
      "startChapter": "L0_C01",
      "chapters": [
        "level_0/chapter_01.json",
        "level_0/chapter_02.json"
      ]
    }
  }
}
```

The manifest should identify files. It should not duplicate the full Chapter contents.

---

## Chapter structure

A Chapter should be a deterministic graph/state machine, not a single giant prose block.

Conceptual example:

```json
{
  "schemaVersion": 1,
  "levelId": "0",
  "chapterId": "L0_C01",
  "title": "Example Chapter",
  "startNode": "L0_C01_S001",
  "nodes": []
}
```

Recommended node IDs:

```text
L{LEVEL}_C{CHAPTER}_S{SCENE}
```

Examples:

```text
L0_C01_S001
L0_C01_S002A
L0_C01_S002B
```

IDs are save-facing identifiers. Once released and used by saves, do not rename them casually.

---

## StoryNode

A StoryNode represents one playable authored scene/state.

Conceptual shape:

```json
{
  "id": "L0_C01_S001",
  "type": "story",
  "snapshot": null,
  "charactersPresent": ["cao_minh"],
  "requiredFacts": [],
  "forbiddenReveals": [],
  "dialogueAnchors": [],
  "narrativeSource": "",
  "choices": {
    "A": {
      "text": "",
      "setFlags": [],
      "clearFlags": [],
      "nextNode": ""
    },
    "B": {
      "text": "",
      "setFlags": [],
      "clearFlags": [],
      "nextNode": ""
    },
    "C": {
      "text": "",
      "setFlags": [],
      "clearFlags": [],
      "nextNode": ""
    }
  }
}
```

This is a design contract, not a guarantee that every field above is already supported by runtime code.

Before implementing the loader, verify the schema against the actual StoryCore implementation and tests.

---

## Meaning of important fields

### `narrativeSource`

Authored source for the scene.

It defines what the scene is actually about and supplies material for AI narration.

It is **not automatically a verbatim script** that the AI must repeat word-for-word.

Important locked dialogue or text that must remain exact should use an explicit locked mechanism rather than relying on the AI to preserve wording.

### `requiredFacts`

Facts/events that the generated scene must preserve.

Examples:

- an object must be discovered;
- a character must notice a specific clue;
- a door must remain closed;
- an injury must persist;
- a specific story event must occur.

AI may change presentation, not the truth of these facts.

### `forbiddenReveals`

Information that must not be revealed in this node.

This protects mysteries, character knowledge boundaries, and future story beats.

Do not rely only on the prompt for critical state protection. Core validation should reject or repair invalid output where practical.

### `charactersPresent`

Characters actually present in the scene.

Presence does not imply knowledge.

Character knowledge must still respect current canon and live continuity.

### `dialogueAnchors`

Important dialogue facts, beats, or locked disclosures.

Use these for information that must be communicated without requiring every connecting sentence to be authored.

### `snapshot`

Optional visual anchor for a StoryNode or major scene.

A snapshot belongs to the scene, not mechanically to "turn number X".

### `choices`

A/B/C are the authoritative progression inputs.

A choice may:

- set flags;
- clear flags;
- apply consequences through Core;
- request a scripted event;
- choose the next StoryNode.

The AI must not choose the next StoryNode on its own.

---

## A/B/C versus free text

### A/B/C

A/B/C changes real story/world progression.

Expected flow:

```text
current StoryNode
+ selected A/B/C
→ Core validates choice
→ Core applies consequences
→ Core determines next StoryNode
→ AI receives the resulting scene packet
→ AI narrates the scene
```

### Free text

Free text is primarily for:

- roleplay;
- talking to characters;
- asking questions;
- inspecting;
- flavor interaction.

Free text should not silently advance a StoryNode unless a future explicit rule says otherwise.

Example:

```text
A. Open the door
B. Go back
C. Wait
```

The player may type:

```text
Cao Minh looks at Lucia and asks what she thinks.
```

AI may perform the conversation, but the progression node remains unchanged until a valid progression action is committed.

---

## AI contract

Normal story narration should use a compact scene packet.

Conceptually:

```text
CURRENT CHAPTER / NODE
PLAYER CHOICE
REQUIRED EVENTS
FORBIDDEN REVEALS
RELEVANT STORY FLAGS
RECENT CONTINUITY
RELEVANT CHARACTER DATA
RELEVANT LEVEL DATA
TASK: NARRATE THIS SCENE
```

Do not send the full Level story or full 5–6k-word Chapter on every turn.

The AI may perform:

- narration;
- atmosphere;
- gestures;
- minor dialogue;
- character reactions;
- banter;
- combat prose after Core resolves mechanics.

The AI must not independently:

- pick the next StoryNode;
- unlock a Level transition;
- create authoritative loot;
- mutate HP/status/state;
- spawn an Entity;
- override LevelCore;
- reveal forbidden facts;
- invent canon to repair missing data.

---

## World and character knowledge boundaries

Authored story does not grant characters omniscience.

A fact existing in story data means the **author/runtime** knows it. It does not mean every character knows it.

Every disclosure must respect:

- what the character previously knew;
- what they directly observed;
- what another character told them;
- what live continuity has established;
- what current canon permits.

If a StoryNode requires a character to know something new, the story must provide a legitimate way for that knowledge to be acquired.

---

## Level progression authority

Story data may decide **when the story is ready for an exit**.

It must not directly bypass LevelCore.

Intended authority:

```text
Story progression
→ story requirement completed
→ EXIT_READY
→ LevelCore determines valid destinations
→ player crosses
→ LevelCore validates transition
→ next Level
```

Existing anti-skip and Level transition validation remain authoritative.

Never encode a StoryNode as a backdoor that teleports the player to an otherwise invalid Level.

---

## Entity encounters

There are two conceptually different encounter types.

### Roaming encounter

Owned by EntityCore.

Random roaming encounters are gameplay interrupts, not required authored story beats.

Expected flow:

```text
StoryNode active
→ roaming encounter starts
→ story is paused
→ encounter/combat resolves
→ HP/status/loot/time consequences persist
→ same story flow resumes
```

### Scripted story encounter

Requested by authored story because the plot requires it.

Story data may request a specific scripted encounter, but EntityCore should remain responsible for actually creating and owning the encounter.

Do not ask AI prose to "spawn" an authoritative Entity.

---

## Encounter pacing

Random encounters must eventually support pacing protection.

Examples of story states where roaming may need suppression:

```text
KEY_DIALOGUE
KEY_STORY_SCENE
SCRIPTED_ENCOUNTER
POST_COMBAT_GRACE
```

A random encounter should not repeatedly interrupt important authored dialogue or immediately chain combat into combat.

These rules belong to runtime policy, not prose.

---

## Save compatibility

StoryNode IDs, Chapter IDs, flags, and schema versions may become persistent save data.

Therefore:

- use stable IDs;
- include `schemaVersion`;
- do not rename released IDs without migration;
- do not delete a released node if an existing save may point to it unless a migration/fallback exists;
- validate missing/corrupt story state safely;
- preserve backward compatibility with older saves.

Older saves may contain legacy route/streak state.

Do not assume legacy fields have disappeared merely because story-driven progression is introduced.

Any removal of hidden route streak must happen only after migration and regression coverage are ready.

---

## Adding a new Level

When adding a Level later:

1. create `story/level_X/`;
2. add Chapter files;
3. register them in `story_manifest.json`;
4. use stable Level/Chapter/Node IDs;
5. verify all referenced characters, snapshots, flags, encounters, and destination logic exist;
6. verify story facts against current canon;
7. test every A/B/C edge;
8. test save/resume from representative nodes;
9. test roaming interruption and resume;
10. test Level transition validation.

Do not copy an old Level and blindly rename IDs. That is how graph corruption acquires a family tree.

---

## Adding a new Chapter

Before merging a Chapter:

- every node ID must be unique;
- every non-terminal choice must resolve to a valid node;
- unreachable nodes should be intentional;
- required facts must not contradict current canon;
- forbidden reveals must cover important future mysteries;
- character presence and knowledge must be valid;
- snapshots must exist if referenced;
- scripted encounters must reference supported runtime definitions;
- terminal nodes must define the intended Chapter/Level continuation;
- tests should detect broken links.

---

## Validation requirements

Story data should eventually be validated automatically in CI.

At minimum validate:

- valid JSON;
- supported `schemaVersion`;
- unique Level/Chapter/Node IDs;
- valid `startNode`;
- valid choice targets;
- no dangling node references;
- valid snapshot references;
- valid character IDs;
- valid scripted encounter IDs;
- valid Level references;
- safe terminal nodes;
- no duplicate flags where duplicates are invalid.

Runtime must also fail safely if story data is malformed.

---

## Implementation order

Do not rewrite the whole game at once.

Preferred order:

1. story schema;
2. story manifest;
3. StoryState save model;
4. loader/repository;
5. deterministic StoryNode + A/B/C transitions;
6. compact AI scene packet;
7. snapshot binding;
8. dialogue/relationship state;
9. story-driven EXIT_READY;
10. keep LevelCore transition authority;
11. scripted Entity API;
12. roaming suppression/grace;
13. route-streak retirement only after regression confidence;
14. old-save migration;
15. tests + CI + APK verification.

---

## Current warning for future contributors and AI agents

**Do not infer implementation from this README.**

Before modifying story runtime:

1. inspect repository HEAD;
2. trace current save/state flow;
3. inspect choice handling;
4. inspect LevelCore transition validation;
5. inspect EntityCore/combat ownership;
6. inspect tests;
7. make the smallest compatible change.

This document describes the intended architecture and data contract.

Actual code at repository HEAD is the source of truth for what is currently implemented.

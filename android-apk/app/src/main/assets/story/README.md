# BACKROOMsV2 — Novelist-First Story Pipeline

> **Status:** Level 0 novelist-first runtime is implemented on the current branch.  
> **Source revision:** `level0-final-2026-09-23`.

## Current implementation status

Implemented:

- all 30 final Level 0 Chapters are committed verbatim under `story/source/level_0/`;
- `StoryRepository` loads generated metadata plus the authored Markdown directly from APK assets;
- authored prose is split only at paragraph boundaries and emitted verbatim by Java Core;
- linear authored segments expose a single neutral **• Tiếp tục** control; decision scenes never mix this control with gameplay choices;
- free exploration remains available between authored sections;
- Level 0 hidden route-streak rolls are suppressed while StoryCore owns the active Level 0 story;
- `StoryCore` owns save-facing chapter, segment, thread, visibility, story flags and story-character state;
- Lục Trầm is permanently removed from random character encounter rolls;
- Lục Trầm progresses through `PARALLEL_STORY → REUNITED → ACCOMPANYING → PARTY_MEMBER` only through authored events;
- Nam is tracked as a story-local character and becomes `MISSING` at the authored Chapter 19 event;
- Chapters 3, 5, 7 and 9 are explicit Lục Trầm cutaways;
- cutaway facts are excluded from Cao Minh's recent-context knowledge;
- free player input is disabled and Core-rejected while a cutaway is active;
- AI candidate state cannot overwrite StoryCore state;
- Chapter 30 sets `LEVEL0_ARC_BOUNDARY_REACHED` but does **not** claim or perform a Level 1 transition;
- regression tests validate the 30 source files, metadata, paragraph segmentation and a complete Chapter 1 → Chapter 30 StoryCore run.

Implemented by Story Compiler v2 + Decision Prefetch Runtime:

- `.github/scripts/compile-story.py` compiles authored chapters into conservative `LINEAR / DECISION / CUTAWAY / LOCKED_EVENT` metadata;
- a `DECISION` contains only a compiler-owned canon contract: `canonChoiceText`, `loopAnchor`, and `decisionGuard`;
- the compiler does **not** generate the final three player choices;
- the compiler permits at most one `DECISION` per Chapter and audits that the canon intent belongs to Cao Minh, has not already happened, and genuinely enters the next authored beat;
- each compiled Chapter is SHA-256 bound to its manuscript source and the generated file carries a compiler fingerprint;
- when a DECISION segment is displayed, Android immediately starts one background prefetch while the player is reading;
- runtime provider order is **Gemini key 1 → 2 → 3 → 4 → 5**, then **Haiku fallback** only after all Gemini keys fail;
- that single prefetch call receives the fixed canon intent and creates exactly two additional branches: `TRAP_LOOP` and `CONVERGE`, including their complete prepared reactions;
- `CANON_PROGRESS` uses the already-authored next manuscript segment as its prepared result;
- all three outcomes are fully prepared before choices become clickable, so tapping a choice performs no model call;
- public UI state contains only opaque `choiceId + text`; hidden `CANON_PROGRESS / TRAP_LOOP / CONVERGE` mappings remain private inside Android Core state and are stripped before state reaches WebView/localStorage;
- choices are deterministically shuffled for the current decision package so no screen position identifies canon;
- story choices render as three visually equal **•** rows with no A/B/C labels and no canon/trap styling;
- `TRAP_LOOP` plays its prefetched consequence and returns to the same story anchor without announcing that the player was wrong;
- `CONVERGE` plays its prefetched local consequence and then rejoins the exact authored next beat;
- `CANON_PROGRESS` advances directly into the authored next beat;
- free text, inventory mutation and stat upgrades are locked while a story decision is unresolved so the prefetched package cannot be invalidated by unrelated state changes;
- decision packages are bound to a context hash; stale packages are rejected instead of being applied to changed state;
- cutaways remain deterministic and never receive player decisions;
- authored event boundaries remain `LOCKED_EVENT`;
- decision resolution does not roll route, loot, Entity, or random character encounters.

Still intentionally not implemented:

- scripted Entity encounter events authored from manuscript metadata;
- automatic snapshot binding per authored segment;
- roaming encounter grace/suppression policies beyond authored turns themselves;
- a post-Chapter-30 Level transition, because the current manuscript does not establish that the unnamed metal region is Level 1.

The existing `LevelCore` route-streak code still exists for non-story-owned progression, but it is not allowed to advance Level 0 while the authored Level 0 story is active.

## Core principle

**THE AUTHOR WRITES A NOVEL. THE SYSTEM TURNS IT INTO GAMEPLAY.**

BACKROOMsV2 is designed for a **novelist-first workflow**.

The author is not expected to be a game designer, programmer, narrative scripter, or JSON editor.

The author should be able to write ordinary prose as a novel and provide that manuscript to the project. Technical story structures are derived afterward by tooling/AI and runtime code.

The intended architecture is:

~~~text
NOVEL MANUSCRIPT
      ↓
STORY COMPILER / AI ANALYSIS
      ↓
GENERATED STORY STRUCTURE
      ↓
PLAYER INTERACTION
      ↓
CORE STATE / CONSEQUENCES
      ↓
AI PERFORMANCE
~~~

Short version:

**The novel is the creative source of truth. Generated game data is only a technical translation of it.**

---

## What the author is responsible for

The author writes:

- prose;
- chapters;
- scenes;
- dialogue;
- character behavior;
- mysteries;
- revelations;
- emotional beats;
- important events;
- consequences;
- the actual intended story.

The author does **not** need to write:

- StoryNode IDs;
- JSON;
- flags;
- nextNode;
- requiredFacts;
- forbiddenReveals;
- A/B/C metadata;
- save-state fields;
- encounter metadata;
- LevelCore rules;
- runtime graph logic.

Those are technical responsibilities.

Do not push implementation concerns back onto the novelist.

---

## Source of truth

The authored manuscript is the primary story source.

Recommended layout:

~~~text
story/
├── README.md
├── source/
│   └── level_0/
│       ├── LEVEL0_CH01.md
│       ├── LEVEL0_CH02.md
│       ├── ...
│       └── LEVEL0_CH30.md
└── generated/
    ├── story_manifest.json
    └── level_0/
        ├── level0.story.json
        └── level0.interactions.json
~~~

The current implementation uses this separation, and future Levels should preserve it:

~~~text
source/     = human-authored manuscript
generated/  = machine-derived gameplay data
~~~

Files under generated/ should be treated as rebuildable artifacts.

They must not become the place where the canonical story is manually rewritten.

---

## How the author may write

The manuscript may be written as normal prose.

Example:

~~~markdown
# Level 0

## Chapter 1

Cao Minh mở mắt.

Ánh đèn huỳnh quang kéo dài trên trần nhà. Tiếng ù đều đặn
len vào khoảng không tĩnh lặng đến mức khó phân biệt nó đang vang
trong căn phòng hay bên trong đầu hắn.

Hắn chống tay xuống lớp thảm ẩm và đứng dậy.

...

Cao Minh dừng lại trước ngã ba.

Hành lang bên trái tối hơn hẳn. Bên phải, ánh đèn chớp tắt từng nhịp.
Phía sau là con đường hắn vừa đi qua.
~~~

That is enough as authored source.

The author does **not** have to add:

~~~text
A. Go left
B. Go right
C. Go back
~~~

The author also does not have to mark every StoryNode or gameplay transition.

Chapter headings and scene headings are useful for readability, but the intended pipeline must not require the novelist to learn a custom scripting language.

---

## Whole-novel input is valid

The system should support receiving a large manuscript and deriving structure from it.

For example, level_0.md may contain:

~~~text
Chapter 1
Chapter 2
Chapter 3
...
Chapter 10
~~~

The compiler may split it into technical Chapter/Scene units afterward.

The author may also choose one Markdown file per Chapter for convenience, but that is an editorial preference, not a gameplay requirement.

---

## Story compilation

Story Compiler v2 converts authored prose into runtime-safe **decision contracts**, not final multiple-choice dialogue.

The committed manuscript remains the creative source of truth. The compiler writes only derived metadata under `story/generated/`.

Conceptually:

~~~text
manuscript
→ detect Chapters / paragraph-safe segments
→ preserve mandatory authored events
→ identify a clean decision anchor
→ derive ONE hidden canon intent that enters the next authored beat
→ audit ownership/timing against Cao Minh and the manuscript
→ bind source digest + compiler fingerprint
→ emit LINEAR / DECISION / CUTAWAY / LOCKED_EVENT metadata
~~~

At runtime:

~~~text
DECISION segment appears
→ player starts reading
→ background prefetch starts immediately
→ Gemini keys 1..5, then Haiku fallback
→ generate TRAP_LOOP + CONVERGE choice/reaction
→ combine with compiler-fixed CANON_PROGRESS
→ validate + cache all 3 outcomes
→ expose only three anonymous • choices
→ tap resolves instantly from cache
~~~

The compiler and runtime must preserve authorial intent.

They translate the novel into gameplay without turning the model into a second story director.

---

## Player decisions are generated, not authored by default

The novelist does not write A/B/C and the runtime does not show A/B/C labels.

The compiler only identifies a decision anchor and the hidden canon intent. While the player reads the current prose, the runtime prepares the other two possibilities.

Visible UI:

~~~text
• Kiểm tra dấu vừa để lại trước khi di chuyển.

• Lắng nghe thêm một nhịp ở giao lộ.

• Tiến sát mép tường để quan sát hành lang.
~~~

There is no public indication of which row is canon, convergent, or a trap.

### Hidden outcome classes

Internally a prepared package contains exactly:

~~~text
CANON_PROGRESS
CONVERGE
TRAP_LOOP
~~~

- `CANON_PROGRESS`: immediately enters the already-authored next beat.
- `CONVERGE`: plays a cached local reaction, then enters the same authored next beat.
- `TRAP_LOOP`: plays a cached consequence and returns to the current decision anchor.

Outcome classes never belong in visible WebView/localStorage state.

### Do not force decisions everywhere

If a scene has no clean decision pause, keep it `LINEAR`.

Important dialogue, reveals and mandatory events may be linear or `LOCKED_EVENT`.

A decision should exist because reading the situation can matter, not because a UI template demands three buttons.

---

## Decision generation must not rewrite the plot

The runtime may vary the local route **to** the next authored beat, but the authored beat itself remains fixed.

A good trap is not an obviously stupid option. It should look reasonable and fail because the player missed or misread a rule/clue already established by the story.

Conceptual flow:

~~~text
                    hidden outcomes

• plausible action  ─────→ TRAP_LOOP ─────→ decision anchor
• plausible action  ─────→ CONVERGE  ─────→ authored next beat
• plausible action  ─────→ CANON      ─────→ authored next beat
~~~

All three public choices should have comparable length, tone and plausibility.

The player should infer danger from the fictional world, never from:
- A/B/C position;
- color or icon;
- wording that obviously marks a “wrong” option;
- instant-versus-slow response timing;
- a visible “continue canon” button beside two AI-generated options.

At click time there must be no narration-provider request. All three results are already cached.

---

## Authorial truth versus generated freedom

The compiler should separate story information into two broad categories.

### Locked authored truth

Things that must remain true:

- major events;
- discoveries;
- deaths or survival;
- important relationships;
- mandatory dialogue information;
- mystery boundaries;
- character knowledge;
- story consequences;
- Level progression requirements.

### Generative performance freedom

Things AI may vary:

- wording;
- connective narration;
- gestures;
- atmosphere;
- minor reactions;
- incidental banter;
- pacing within safe limits;
- combat prose after mechanics are resolved.

The AI performs the story.

It does not own the story.

---

## Generated story data

Generated story metadata contains technical structures such as:

- Chapter and segment IDs;
- source digests;
- compiler fingerprint;
- `LINEAR / DECISION / CUTAWAY / LOCKED_EVENT` classification;
- canon decision intent;
- loop anchor;
- decision guard;
- authored events and story facts.

Conceptual compiler artifact:

~~~json
{
  "L0_C06_P003": {
    "mode": "DECISION",
    "decisionContract": {
      "canonChoiceText": "Lặng lẽ tiến về hướng phát ra tiếng kim loại.",
      "loopAnchor": "L0_C06_P003",
      "decisionGuard": "Do not change authored progression."
    }
  }
}
~~~

The runtime package is different and transient. It combines the canon contract with two prefetched alternatives, then keeps hidden outcomes inside Android Core.

The WebView receives only:

~~~json
{
  "choices": [
    {"id": "opaque-1", "text": "..."},
    {"id": "opaque-2", "text": "..."},
    {"id": "opaque-3", "text": "..."}
  ]
}
~~~

It must not receive the hidden outcome map.

Generated compiler files and runtime decision packages are technical artifacts.

They are not the novel.

---

## Generated files must not become a second source of truth

Avoid copying and manually maintaining the same prose in both Markdown and JSON.

Preferred rule:

~~~text
SOURCE MANUSCRIPT = canonical creative content
GENERATED JSON     = derived runtime metadata
~~~

If the manuscript changes, generated data should be regenerated and revalidated.

Generated files should eventually carry a clear warning such as:

~~~text
DO NOT EDIT MANUALLY.
Generated from authored story source.
~~~

This prevents the manuscript and runtime graph from slowly becoming two contradictory versions of the same story.

---

## AI narration contract

At runtime, AI should receive only the material needed for the current scene.

Conceptually:

~~~text
CURRENT AUTHORED SCENE
PLAYER ACTION / CHOICE
LOCKED STORY FACTS
FORBIDDEN REVEALS
RELEVANT LIVE STATE
RELEVANT CHARACTER DATA
RELEVANT LEVEL DATA
RECENT CONTINUITY
TASK: PERFORM / NARRATE THIS SCENE
~~~

Do not send the entire novel or an entire 5–6k-word Chapter every turn when a smaller scene packet is sufficient.

AI may narrate and perform.

AI must not independently:

- rewrite major authored events;
- choose the next authoritative StoryNode;
- reveal future mysteries;
- invent character knowledge;
- unlock a Level transition;
- create authoritative loot;
- mutate HP/status/game state;
- spawn an authoritative Entity;
- override LevelCore;
- invent canon because source data is missing.

---

## Free-text player input

Free text remains useful for:

- roleplay;
- talking to characters;
- asking questions;
- inspecting surroundings;
- expressing intent;
- flavor interaction.

Free text should not automatically destroy authored progression.

The runtime may allow free-form interaction inside the current scene while keeping the main story state anchored until a valid progression action occurs.

---

## Character knowledge

The manuscript may contain information that the reader or author knows but a character does not.

The compiler and runtime must preserve this distinction.

A character may only act on knowledge legitimately available through:

- prior established knowledge;
- direct observation;
- dialogue;
- discovered evidence;
- live continuity;
- current canon.

Do not let AI convert author knowledge into character omniscience.

---

## Supporting characters discovered from the manuscript

The novelist does not need to create a character database before writing a supporting character.

If a new person appears naturally in the manuscript, the story pipeline should detect and classify that character automatically.

Conceptual flow:

~~~text
manuscript mentions a character
→ detect name / aliases / identity clues
→ compare with current character canon
→ existing character?
   → yes: bind existing runtime/canon id
   → no: create a story-local character record
→ character appears again or becomes important?
   → no: keep story-local
   → yes: promote to recurring character
→ character becomes canonically important?
   → generate/update canonical profile
→ character needs combat/party mechanics?
   → create gameplay projection separately
~~~

The author should not have to stop writing in order to create IDs, JSON, stats, relationship variables, or a full Character Codex.

### Story-local characters

A one-scene or minor supporting character should remain lightweight.

Conceptual generated record:

~~~text
id: tran_vu
name: Trần Vũ
scope: story_local
firstAppearance: Level 0 / Chapter 3
knownFacts:
  - middle-aged man
  - met Cao Minh in a corridor
  - warned the group about a sound ahead
knowledge:
  - heard something dangerous ahead
relationships:
  cao_minh: just_met
~~~

Only information supported by the manuscript may be recorded as fact.

If the manuscript does not state an exact age, occupation, hometown, history, abilities, or motive, leave it unknown/open.

Do not invent extra biography merely to make a profile look complete.

### Recurring characters

If a story-local character reappears across scenes or Chapters, the compiler may promote the character to a recurring record.

The generated profile may accumulate only newly established facts from later manuscript content.

Example:

~~~text
Chapter 2:
Trần Vũ is quiet and cautious.

Chapter 6:
The manuscript reveals he was an emergency doctor.

Generated profile after Chapter 6:
occupation: emergency doctor
source: Chapter 6
~~~

The earlier record should be enriched, not rewritten with retroactive inventions.

### Canonical character promotion

A recurring character may be promoted to a canonical character when the manuscript establishes that the character has continuing importance, such as:

- becoming a long-term companion;
- joining the party;
- becoming a major ally, rival, antagonist, or relationship anchor;
- carrying important long-term knowledge;
- recurring across Levels or major story arcs.

Promotion means the technical pipeline may create or update a canonical character profile and bind it to a stable runtime ID.

Promotion must not change the authored personality, history, knowledge, or relationships.

### Gameplay projection is separate from character canon

A character becoming playable or combat-capable does not mean the novelist must design numbers.

Use this separation:

~~~text
NOVEL CHARACTER
      ↓
CANON PROFILE
      ↓
GAMEPLAY PROJECTION
~~~

The manuscript defines what the character can actually do in the story.

The gameplay layer may derive or implement:

- HP;
- damage;
- accuracy/evasion;
- combat skills;
- status effects;
- equipment slots;
- encounter/party behavior.

Gameplay values are technical projections.

They must not silently become new lore.

For example, if the manuscript says a character is skilled with a fire axe, the game may need damage values for that axe. Those values do not authorize the pipeline to invent military training, supernatural strength, or a hidden combat history.

### Relationship state

Relationships should grow from authored events and live gameplay continuity.

A generated/runtime relationship model may track technical state such as:

~~~text
trust
respect
suspicion
knownFacts
sharedEvents
unresolvedQuestions
~~~

These values support continuity.

They do not replace the manuscript.

Do not infer deep friendship, romance, loyalty, hatred, or other major relationship changes from a single generic interaction unless the authored story or accumulated live continuity supports it.

### Character knowledge remains scoped

A newly detected supporting character only knows what the manuscript and live continuity establish that character knows.

Do not copy:

- author knowledge;
- reader knowledge;
- backstage canon;
- another character's private knowledge;

into the supporting character's runtime knowledge state.

### Identity matching must be conservative

Before creating a new character record, compare:

- exact name;
- aliases;
- known runtime IDs;
- contextual identity clues.

If identity is uncertain, surface the ambiguity for review instead of merging two people or inventing a duplicate identity.

This is especially important when:

- aliases are used;
- titles replace names;
- the same surname appears repeatedly;
- a character is intentionally unnamed;
- a reveal later establishes that two apparent identities are the same person.

### Unnamed supporting characters

The manuscript may contain characters such as:

~~~text
the injured survivor
the old woman
the guard
the child in the corridor
~~~

Do not force a permanent canonical identity immediately.

They may use temporary story-local identifiers until the manuscript establishes a stable identity.

If the person later receives a name, the generated data should preserve continuity and bind the earlier temporary record to the revealed identity rather than creating a second person.

### Source traceability

Generated character facts should remain traceable to the manuscript location that established them whenever practical.

This allows future compilation or review to distinguish:

- authored fact;
- inferred technical metadata;
- unresolved/open information;
- gameplay-only projection.

The pipeline should prefer "unknown" over unsupported certainty.

---

## Relationship with existing canon

This directory is for authored story, not for replacing other authoritative systems.

Existing machine-readable knowledge remains under:

app/src/main/assets/knowledge/

The current runtime authority map is documented in:

android-apk/KNOWLEDGE_SOURCE_MAP.md

Story compilation must respect current:

- character canon;
- world canon;
- Level canon;
- Entity rules;
- item definitions;
- live save state;
- combat state.

The manuscript may intentionally introduce new story facts, but integration must be explicit rather than silently overwriting unrelated runtime canon.

---

## Level progression authority

The authored story may determine when the narrative requirement for leaving an area has been satisfied.

It must not bypass LevelCore.

Intended flow:

~~~text
authored story progression
→ story requirement completed
→ EXIT_READY
→ LevelCore determines valid destination
→ player actually crosses
→ LevelCore validates transition
→ next Level
~~~

Story controls **when the plot permits leaving**.

LevelCore controls **where the game actually permits going**.

AI only narrates the event.

---

## Entity encounters

Random roaming encounters and scripted story encounters remain separate concepts.

### Roaming encounters

Owned by EntityCore.

They may interrupt story gameplay when pacing rules allow it.

After resolution, story progression resumes with persistent mechanical consequences.

### Scripted story encounters

A story compiler may derive or register a scripted encounter when the manuscript clearly requires one.

Story data requests it.

EntityCore owns the actual authoritative encounter.

AI prose does not spawn Entities by itself.

---

## Pacing protection

The future runtime should be able to suppress random encounters during sensitive authored scenes, for example:

~~~text
KEY_DIALOGUE
KEY_STORY_SCENE
SCRIPTED_ENCOUNTER
POST_COMBAT_GRACE
~~~

A roaming encounter should not repeatedly destroy the pacing of an important conversation or authored reveal.

This is runtime policy, not something the novelist should have to micromanage in prose.

---

## Snapshots

Snapshots are visual anchors for important scenes.

The novelist should not be required to think in terms of turn numbers.

The compiler/runtime may bind available snapshots to suitable scenes afterward.

Snapshot metadata is technical data.

The prose remains the authored source.

---

## Save policy

Old saves are not a compatibility requirement for this story-system transition.

The new story runtime should optimize for a clean, deterministic state model rather than carrying migration logic for obsolete saves.

Generated Chapter IDs, StoryNode IDs, flags, and schema versions may still become save-facing data for saves created **after** the new story system ships. From that point onward, stable identifiers and safe regeneration matter so an in-progress new-format game is not corrupted by later manuscript recompilation.

---

## Regeneration safety

Once generated story data becomes part of released saves, regeneration cannot blindly renumber every node.

The compiler will need stable identity rules.

Possible future strategies include:

- stable source anchors;
- persistent generated IDs;
- content-independent scene keys;
- migration maps when structural edits occur.

The exact mechanism must be chosen from the actual implementation.

Do not invent one in content files before StoryCore exists.

---

## Expected author workflow

The intended human workflow is deliberately simple:

~~~text
1. Write the novel.
2. Put the manuscript in story/source/.
3. Run the story compilation pipeline.
4. Review important generated decision anchors/canon intents if needed.
5. Validate.
6. Build the game.
~~~

The author should not have to manually maintain generated JSON after every prose edit.

---

## Expected technical workflow

For developers or AI agents working on the pipeline:

~~~text
Inspect manuscript
→ parse structure
→ derive canon-locked decision contracts
→ preserve authored truth
→ generate runtime metadata
→ validate references
→ run regression tests
→ preserve current-format save integrity
~~~

Do not make the novelist manually solve technical problems that the pipeline can solve deterministically.

---

## Future automation

The target experience may eventually be exposed as a command such as:

~~~text
./gradlew compileStory
~~~

or an equivalent build/tooling command.

A future CI flow may look like:

~~~text
story/source/*.md changed
→ parse manuscript
→ compile story
→ validate story graph
→ validate references
→ run tests
→ produce generated story data
~~~

This is a target architecture, not a statement that such tooling currently exists.

---

## Validation requirements

Generated story data should eventually be validated automatically.

At minimum validate:

- source files can be read;
- generated JSON is valid;
- supported schema version;
- unique Chapter/StoryNode IDs;
- valid start node;
- valid graph edges;
- valid decision anchors and next authored beats;
- valid character references;
- valid snapshot references;
- valid scripted encounter references;
- valid Level references;
- safe terminal nodes;
- preservation of required authored events;
- current-format save integrity where generated IDs are already in use.

Compiler uncertainty should be surfaced instead of silently inventing major story logic.

---

## Implementation order

Do not rewrite the entire game in one pass.

Preferred order:

1. lock the novelist-first source format;
2. create source/generated directory boundaries;
3. define generated schema;
4. implement parser/compiler;
5. add graph validation;
6. add StoryState save model;
7. add loader/repository;
8. add deterministic progression;
9. generate compact AI scene packets;
10. bind snapshots;
11. add dialogue/relationship continuity support;
12. integrate story-driven EXIT_READY;
13. preserve LevelCore transition authority;
14. add scripted Entity API;
15. add roaming suppression/grace;
16. replace legacy progression only after regression confidence;
17. add CI and APK verification.

---

## Warning for future contributors and AI agents

**Do not assume the novelist should write game data.**

If a proposed workflow requires the author to manually create StoryNodes, flags, graph edges, JSON, or A/B/C metadata for ordinary story writing, stop and reconsider the design.

The project requirement is:

> **A novelist should be able to provide prose, and the technical pipeline should do the conversion work.**

Also:

**Do not infer current implementation from this README.**

Before changing runtime code:

1. inspect repository HEAD;
2. trace current save/state flow;
3. inspect current choice handling;
4. inspect LevelCore transition validation;
5. inspect EntityCore/combat ownership;
6. inspect tests;
7. make the smallest compatible change.

This README defines the intended author experience and architecture.

Actual repository HEAD remains the source of truth for what is currently implemented.

# web/core — JS port of the Java game core (cluster C1)

Line-faithful port of `android-apk/app/src/main/java/com/rabpit/backroom/core/`:

| Java | JS | Notes |
|---|---|---|
| `GameCoreRules.java` (69 lines) | `rules.js` | All 9 patterns + 7 helpers |
| `GameCoreFacade.java` (~270 logic lines) | `facade.js` | Minus Android glue (below) |
| `GameCoreRulesTest.java` (33 lines) | `test/rules.test.js` | Assertions re-expressed verbatim, plus fixture extension |
| — | `test/facade.test.js` | (state, action) / (before, candidate, action) fixture corpus |

NOT ported, deliberately: the facade's SharedPreferences touchpoints
(`persist`, `currentCoreState`, `clear`) and `Log.d` — persistence belongs to
the C4 save layer; this module is pure in-memory computation, like the Java
logic it mirrors. `MainActivity`'s orchestration (prompt, proxy call, log
append after commit) is C2's, not here.

## API (for the C2 shim)

All functions are synchronous and side-effect free. State/candidate inputs
accept a plain object **or** a JSON string; inputs are never mutated.

```js
import { processRule, processValidatedCandidate } from "./core/index.js";

// 1) Local rules first — mirrors GameCoreFacade.processRule.
const r = processRule(state, action);
// r = { handled, state, reason, error?, reply? }
//   reason "committed"            -> handled, r.state is the new state, r.reply is the canned reply
//   reason "validation_rejected"  -> handled, error "player_pickup_unavailable", turn NOT incremented
//   reason "fallback_required"    -> not handled: run the GM pipeline, then step 2
//   reason "core_error"           -> not handled, r.error carries the message

// 2) Validate a GM-proposed candidate — mirrors processValidatedCandidate.
const v = processValidatedCandidate(beforeState, candidateJson, action);
// v = { handled, state, reason }  reason: "gemini_delta_committed" | "gemini_delta_rejected"
//   On commit, v.state already carries: inventory locked-or-normalized,
//   party sanitized, saveVersion = 3, gameTime rebuilt from the BEFORE state.
```

`rules.js` also exports the individual classifiers (`isDirectPlayerPickupAction`,
`isRestoreAction`, `inventoryMutationLocked`, `isInventoryQuery`, `isPartyQuery`),
`estimateMinutes(action)` and `stableItemId(name)` if C2 ever needs them singly.

## Fidelity notes (what is and is not claimed)

- **Deep equality, not byte-identical strings.** Committed states are asserted
  deep-equal to the Java outcomes. Byte-identical state *strings* vs the APK
  are not claimed: `org.json`'s serialization is a Java-side artifact. Within
  this port, key order is deterministic anyway — objects keep parse/insertion
  order (the same order AOSP `org.json`'s LinkedHashMap produces for the
  facade's own writes), and the facade's stamped fields are written in the
  Java put-order.
- **Regexes.** Java sources are kept verbatim in `rules.js`; flags
  `CASE_INSENSITIVE | UNICODE_CASE` map to JS `iu`. Two engine gaps are closed
  by construction: Java `\s` is ASCII-only while JS `\s` also matches NBSP /
  U+3000 etc., so every `\s` is substituted with the exact ASCII class; and
  Java `String.trim()` (chars ≤ U+0020) is reproduced as `javaTrim` instead of
  JS `trim()`. One residual is accepted and commented in `rules.js`: Java `.`
  also excludes U+0085 (NEL), JS `.` matches it — only reachable if player
  text contains NEL. `.{0,80}` counts UTF-16 units in Java vs code points
  under JS `u` — identical for BMP text (all Vietnamese), different only for
  astral characters inside the assertion window.
- **org.json accessors** are hand-matched in `facade.js` (see its header):
  `optString`/`optInt`/`optLong`/`optBoolean` coercion and defaults,
  `optJSONObject`/`optJSONArray` returning null on type mismatch, `has()`
  true for JSON-null values. Residual: a JSON literal `5.0` is a Double in
  org.json (`optString` → `"5.0"`) but `5` in JS (`optString` → `"5"`) — only
  observable if a string-typed field carries a decimal-form number.
- **parseState leniency.** org.json's tokener accepts some non-strict JSON
  (e.g. unquoted keys); `JSON.parse` does not, and unparseable input becomes
  `{}` — identical to the Java outcome for every machine-generated input this
  game actually produces (page `JSON.stringify`, GM JSON output).
- **copyField** deep-copies the copied value where Java shares the reference
  into the response; serialization-equivalent, and it keeps the caller's
  `before` state safe from later mutation of the committed state.

## Tests

```sh
node --test "web/core/test/*.test.js"   # from the repo root; Node >= 18, no dependencies
```

/*
 * Fixed-scene story graph test for the VN rewrite.
 *
 * Validates android-apk/app/src/main/assets/story/opening-chapter.json:
 * schema shape per scene, every choice target exists, every scene is
 * reachable from `start`, and at least one terminal scene (choices: [])
 * exists. Pure Node, no dependencies.
 *
 * Run:  node web/tests/story.test.mjs
 */
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { fileURLToPath } from "node:url";

const here = fileURLToPath(new URL(".", import.meta.url));
const storyPath = join(here, "..", "..", "android-apk", "app", "src", "main", "assets", "story", "opening-chapter.json");
const story = JSON.parse(readFileSync(storyPath, "utf8"));

assert.equal(typeof story.start, "string", "story.start must be a string");
assert.ok(Array.isArray(story.scenes) && story.scenes.length > 0, "story.scenes must be a non-empty array");

const byId = new Map();
for (const sc of story.scenes) {
  assert.ok(typeof sc.id === "string" && sc.id.length > 0, "scene id must be a non-empty string");
  assert.ok(!byId.has(sc.id), `duplicate scene id: ${sc.id}`);
  byId.set(sc.id, sc);
  assert.ok(sc.stage && Number.isInteger(sc.stage.level) && sc.stage.level >= 0 && sc.stage.level <= 9,
    `${sc.id}: stage.level must be an integer 0..9`);
  assert.ok(sc.stage.entity === null || typeof sc.stage.entity === "string",
    `${sc.id}: stage.entity must be a canonical key string or null`);
  assert.ok(sc.stage.companion === undefined || sc.stage.companion === null || typeof sc.stage.companion === "string",
    `${sc.id}: stage.companion must be a canonical key string or null when present`);
  assert.ok(Array.isArray(sc.lines) && sc.lines.length > 0, `${sc.id}: lines must be a non-empty array`);
  for (const ln of sc.lines) {
    assert.ok(typeof ln.text === "string" && ln.text.length > 0, `${sc.id}: every line needs text`);
    assert.ok(ln.speaker === undefined || typeof ln.speaker === "string", `${sc.id}: speaker must be a string when present`);
    if (ln.if !== undefined) {
      assert.ok(ln.if && typeof ln.if === "object" && !Array.isArray(ln.if), `${sc.id}: line.if must be an object`);
      for (const [k, v] of Object.entries(ln.if)) assert.ok(typeof v === "string", `${sc.id}: line.if.${k} must be a string`);
    }
  }
  assert.ok(Array.isArray(sc.choices), `${sc.id}: choices must be an array`);
  for (const c of sc.choices) {
    assert.ok(typeof c.label === "string" && c.label.length > 0, `${sc.id}: choice label required`);
    assert.ok(byId.has(c.next) || story.scenes.some((s) => s.id === c.next), `${sc.id}: choice target missing: ${c.next}`);
    if (c.set !== undefined) assert.ok(c.set && typeof c.set === "object" && !Array.isArray(c.set), `${sc.id}: choice.set must be an object`);
  }
}
assert.ok(byId.has(story.start), `start scene missing: ${story.start}`);

// Every flag a line is conditioned on must be settable by some choice.
const settable = new Set();
for (const sc of story.scenes) for (const c of sc.choices) if (c.set) for (const k of Object.keys(c.set)) settable.add(k);
for (const sc of story.scenes) for (const ln of sc.lines) if (ln.if) {
  for (const k of Object.keys(ln.if)) assert.ok(settable.has(k), `${sc.id}: line.if references flag no choice sets: ${k}`);
}

// Reachability: BFS from start must cover every scene.
const seen = new Set([story.start]);
const queue = [story.start];
while (queue.length) {
  const cur = byId.get(queue.shift());
  for (const c of cur.choices) if (!seen.has(c.next)) { seen.add(c.next); queue.push(c.next); }
}
assert.equal(seen.size, byId.size, `unreachable scenes: ${[...byId.keys()].filter((id) => !seen.has(id)).join(", ")}`);

// At least one terminal scene, and it must be reachable (covered above).
const terminals = story.scenes.filter((s) => s.choices.length === 0);
assert.ok(terminals.length > 0, "story needs at least one terminal scene (choices: [])");

console.log(`story.test: OK — ${byId.size} scenes, all reachable from "${story.start}", ${terminals.length} terminal scene(s): ${terminals.map((s) => s.id).join(", ")}`);

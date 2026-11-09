// Glue wiring check — glue.mjs is the one file C5 writes at the seam
// between C1's ES-module core and C2's global-probing adapter. In Node we
// stand in for the browser window, import the real glue, and verify it
// assigns a working GameCore global.
// Run: node web/tests/glue.test.mjs
import assert from "node:assert/strict";

globalThis.window = globalThis;
await import("../glue.mjs");

assert.equal(typeof window.GameCore, "object", "glue assigns window.GameCore");
assert.equal(typeof window.GameCore.processRule, "function");
assert.equal(window.GameCore.processValidatedCandidate, undefined, "no candidate lane in the offline core");

// The assigned functions are the real core, not stubs: an inventory query
// is handled locally with the facade's canned reply.
const state = {
  turn: 1, mode: "story", title: "Backrooms", location: "Level 0",
  player: { name: "Cao Minh" }, party: [],
  inventory: [{ name: "Almond Water", quantity: 2 }],
  flags: {}, log: []
};
const r = window.GameCore.processRule(state, "xem inventory");
assert.equal(r.handled, true);
assert.equal(r.state.turn, 2);
assert.equal(r.reply, "Inventory hiện có: Almond Water x2.");

console.log("PASS glue assigns a working window.GameCore from the real C1 core");

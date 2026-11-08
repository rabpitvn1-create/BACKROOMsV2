// Fixture corpus for the GameCoreFacade port (synthesis §5/C1 evidence):
// (state, action) and (before, candidate, action) cases asserted by deep
// equality on the full resulting state, plus explicit checks on the stamped
// fields (saveVersion, gameTime, normalized inventory ids/quantities).
// Byte-identical state strings vs the APK are NOT claimed (org.json key
// ordering is a Java-side artifact); deep equality is the bar.
import { test } from "node:test";
import assert from "node:assert/strict";
import { processRule, processValidatedCandidate } from "../facade.js";

const clone = (o) => JSON.parse(JSON.stringify(o));

function baseState() {
  return {
    title: "Backrooms",
    turn: 5,
    mode: "story",
    location: "Level 0",
    player: { name: "Cao Minh", hp: 80 },
    party: [],
    inventory: [{ id: "almond-water", name: "Almond Water", quantity: 2 }],
    flags: {},
    log: [{ role: "gm", text: "Mở đầu." }],
    gameTime: { elapsedSubjectiveMinutes: 120, lastAdvanceMinutes: 5, lastAdvanceReason: "player_action" },
    saveVersion: 3,
  };
}

// ---------- processRule ----------

test("processRule: direct pickup rejected, turn unchanged, log appended", () => {
  const state = baseState();
  const snapshot = clone(state);
  const res = processRule(state, "Cao Minh nhặt chai Almond Water lên");
  assert.equal(res.handled, true);
  assert.equal(res.reason, "validation_rejected");
  assert.equal(res.error, "player_pickup_unavailable");
  assert.equal(res.reply, "[Warning] This action is not available.");
  const expected = baseState();
  expected.log.push(
    { role: "player", text: "Cao Minh nhặt chai Almond Water lên" },
    { role: "gm", text: "[Warning] This action is not available." },
  );
  assert.deepEqual(res.state, expected); // turn still 5, gameTime untouched
  assert.deepEqual(state, snapshot); // input not mutated
});

test("processRule: inventory query commits with canned reply, +1 turn, time advanced", () => {
  const res = processRule(baseState(), "xem inventory");
  assert.equal(res.handled, true);
  assert.equal(res.reason, "committed");
  assert.equal(res.error, undefined);
  assert.equal(res.reply, "Inventory hiện có: Almond Water x2.");
  const expected = baseState();
  expected.turn = 6;
  expected.gameTime = { elapsedSubjectiveMinutes: 121, lastAdvanceMinutes: 1, lastAdvanceReason: "player_action" };
  expected.log.push(
    { role: "player", text: "xem inventory" },
    { role: "gm", text: "Inventory hiện có: Almond Water x2." },
  );
  assert.deepEqual(res.state, expected);
});

test("processRule: inventory reply formats quantities (x1 bare, xN suffixed)", () => {
  const state = baseState();
  state.inventory = [
    { id: "dan-9mm", name: "Đạn 9mm", quantity: 1 },
    { id: "thuoc", name: "Thuốc", quantity: 3 },
  ];
  const res = processRule(state, "kiểm tra kho đồ");
  assert.equal(res.reply, "Inventory hiện có: Đạn 9mm, Thuốc x3.");
});

test("processRule: inventory query on empty inventory", () => {
  const state = baseState();
  state.inventory = [];
  const res = processRule(state, "xem inventory");
  assert.equal(res.reply, "Inventory hiện đang trống.");
  assert.equal(res.state.turn, 6);
});

test("processRule: party query commits with canned reply", () => {
  const state = baseState();
  state.party = [{ id: "lucia", name: "Lucia", present: true }];
  const res = processRule(state, "party có ai");
  assert.equal(res.handled, true);
  assert.equal(res.reason, "committed");
  assert.equal(res.reply, "Party hiện có Cao Minh, Lucia.");
  assert.equal(res.state.turn, 6);
  assert.equal(res.state.gameTime.elapsedSubjectiveMinutes, 121);
});

test("processRule: party query on empty party and id-only member name fallback", () => {
  assert.equal(processRule(baseState(), "xem đội hình").reply, "Cao Minh hiện không có đồng đội trong party.");
  const state = baseState();
  state.party = [{ id: "iris", present: true }];
  assert.equal(processRule(state, "xem đội hình").reply, "Party hiện có Cao Minh, iris.");
});

test("processRule: normal action falls back with state untouched", () => {
  const state = baseState();
  const res = processRule(state, "Cao Minh kiểm tra hành lang phía trước");
  assert.equal(res.handled, false);
  assert.equal(res.reason, "fallback_required");
  assert.equal(res.error, undefined);
  assert.equal(res.reply, undefined);
  assert.deepEqual(res.state, baseState());
});

test("processRule: blank action falls back; missing turn/gameTime get defaults", () => {
  assert.equal(processRule(baseState(), "   ").reason, "fallback_required");
  const bare = { inventory: [], log: [] };
  const res = processRule(bare, "xem inventory");
  assert.equal(res.state.turn, 2); // max(1, default 1) + 1
  assert.deepEqual(res.state.gameTime, {
    elapsedSubjectiveMinutes: 1,
    lastAdvanceMinutes: 1,
    lastAdvanceReason: "player_action",
  });
  assert.equal(res.state.saveVersion, 3);
});

test("processRule: accepts a JSON string state identically", () => {
  const viaObject = processRule(baseState(), "xem inventory");
  const viaString = processRule(JSON.stringify(baseState()), "xem inventory");
  assert.deepEqual(viaString, viaObject);
});

// ---------- processValidatedCandidate ----------

function beforeState() {
  return {
    title: "Backrooms",
    turn: 5,
    mode: "ai",
    location: "Level 0",
    player: { name: "Cao Minh", hp: 80 },
    party: [{ id: "lucia", name: "Lucia", present: true }],
    inventory: [{ id: "almond-water", name: "Almond Water", quantity: 2 }],
    flags: {},
    log: [],
    gameTime: { elapsedSubjectiveMinutes: 120, lastAdvanceMinutes: 5, lastAdvanceReason: "player_action" },
    saveVersion: 3,
  };
}

test("candidate: normal GM commit — normalization, party gate, stamps", () => {
  const before = beforeState();
  const beforeSnapshot = clone(before);
  const candidate = beforeState();
  candidate.turn = 6; // MainActivity bumps the turn before validating; facade keeps it
  candidate.saveVersion = 99; // must be forced back to 3
  candidate.gameTime = { elapsedSubjectiveMinutes: 9999 }; // must be discarded
  candidate.inventory = [
    { name: "  Đạn 9mm  ", quantity: "3" }, // trimmed, slug id, string qty coerced
    { name: "Almond Water", quantity: 0 }, // qty clamped to 1, then overwritten below
    { name: "   ", quantity: 9 }, // dropped: empty name
    42, // dropped: not an object
    { name: "Almond Water", quantity: 7 }, // duplicate id: last wins, first position
    { name: "Băng Gạc", id: " custom-id ", quantity: 2, note: "y tế" }, // id trimmed, extras kept
  ];
  candidate.party = [
    { id: "lucia", name: "Lucia", present: true, hp: 55 }, // existing member: kept
    { id: "iris", name: "Iris", joinConfirmed: true, present: true }, // confirmed join: kept
    { id: "ghost", name: "Ghost", joinConfirmed: true, present: false }, // not present: dropped
    { id: "syvial", name: "Syvial", present: true }, // unconfirmed: dropped
    { name: "Cao Minh Clone" }, // no id, unconfirmed: dropped
  ];
  const candidateSnapshot = clone(candidate);

  const res = processValidatedCandidate(before, candidate, "Cao Minh đi tiếp");
  assert.equal(res.handled, true);
  assert.equal(res.reason, "gemini_delta_committed");
  assert.equal(res.error, undefined);

  assert.deepEqual(res.state.inventory, [
    { name: "Đạn 9mm", quantity: 3, id: "đạn-9mm" },
    { name: "Almond Water", quantity: 7, id: "almond-water" },
    { name: "Băng Gạc", id: "custom-id", quantity: 2, note: "y tế" },
  ]);
  assert.deepEqual(res.state.party, [
    { id: "lucia", name: "Lucia", present: true, hp: 55 },
    { id: "iris", name: "Iris", joinConfirmed: true, present: true },
  ]);
  assert.equal(res.state.saveVersion, 3);
  assert.equal(res.state.turn, 6);
  // gameTime rebuilt from BEFORE (120) + MOVE estimate for "Cao Minh đi tiếp" (5),
  // never from the candidate's forged 9999.
  assert.deepEqual(res.state.gameTime, {
    elapsedSubjectiveMinutes: 125,
    lastAdvanceMinutes: 5,
    lastAdvanceReason: "player_action",
  });
  assert.deepEqual(before, beforeSnapshot); // inputs not mutated
  assert.deepEqual(candidate, candidateSnapshot);
});

test("candidate: mutation-locked actions restore inventory from before", () => {
  for (const action of ["Cao Minh nhặt hộp đạn lên", "hoàn nguyên vật thể trong nhẫn"]) {
    const candidate = beforeState();
    candidate.inventory = [{ name: "Súng Lục", quantity: 1 }];
    const res = processValidatedCandidate(beforeState(), candidate, action);
    assert.equal(res.reason, "gemini_delta_committed");
    assert.deepEqual(res.state.inventory, beforeState().inventory, action);
  }
});

test("candidate: locked + before has no inventory key -> key removed from result", () => {
  const before = beforeState();
  delete before.inventory;
  const candidate = beforeState(); // has an inventory
  const res = processValidatedCandidate(before, candidate, "Cao Minh nhặt hộp đạn lên");
  assert.equal("inventory" in res.state, false);
});

test("candidate: no inventory key in candidate -> copied from before", () => {
  const candidate = beforeState();
  delete candidate.inventory;
  const res = processValidatedCandidate(beforeState(), candidate, "Cao Minh đi tiếp");
  assert.deepEqual(res.state.inventory, beforeState().inventory);
});

test("candidate: inventory explicitly null -> normalized to empty array", () => {
  const candidate = beforeState();
  candidate.inventory = null; // org.json has("inventory") is true for JSON null
  const res = processValidatedCandidate(beforeState(), candidate, "Cao Minh đi tiếp");
  assert.deepEqual(res.state.inventory, []);
});

test("candidate: missing party in candidate -> stamped as empty array", () => {
  const candidate = beforeState();
  delete candidate.party;
  const res = processValidatedCandidate(beforeState(), candidate, "Cao Minh đi tiếp");
  assert.deepEqual(res.state.party, []);
});

test("candidate: party membership by lowercased name when id is absent", () => {
  const before = beforeState();
  before.party = [{ name: "Lucia Lục", present: true }];
  const candidate = beforeState();
  candidate.party = [{ name: "Lucia Lục", hp: 50 }]; // no id, no joinConfirmed — but existing
  const res = processValidatedCandidate(before, candidate, "Cao Minh đi tiếp");
  assert.deepEqual(res.state.party, [{ name: "Lucia Lục", hp: 50 }]);
});

test("candidate: accepts JSON strings for before and candidate", () => {
  const candidate = beforeState();
  candidate.turn = 6;
  const viaObjects = processValidatedCandidate(beforeState(), candidate, "Cao Minh đi tiếp");
  const viaStrings = processValidatedCandidate(
    JSON.stringify(beforeState()),
    JSON.stringify(candidate),
    "Cao Minh đi tiếp",
  );
  assert.deepEqual(viaStrings, viaObjects);
});

test("candidate: unparseable candidate JSON validates the empty object", () => {
  // Java parseState() swallows parse errors into {} — the commit then carries
  // before's inventory, an empty party, and the stamps. Faithful by design.
  const res = processValidatedCandidate(beforeState(), "{not json", "Cao Minh đi tiếp");
  assert.equal(res.handled, true);
  assert.deepEqual(res.state.inventory, beforeState().inventory);
  assert.deepEqual(res.state.party, []);
  assert.equal(res.state.saveVersion, 3);
});

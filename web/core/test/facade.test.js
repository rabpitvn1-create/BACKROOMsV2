// Fixture corpus for the GameCoreFacade port (synthesis §5/C1 evidence):
// (state, action) cases asserted by deep equality on the full resulting
// state, plus explicit checks on the stamped fields (saveVersion,
// gameTime). Byte-identical state strings vs the APK are NOT claimed
// (org.json key ordering is a Java-side artifact); deep equality is the bar.
import { test } from "node:test";
import assert from "node:assert/strict";
import { processRule } from "../facade.js";

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

test("processRule: free action commits locally with the neutral state reply", () => {
  const state = baseState();
  const snapshot = clone(state);
  const action = "Cao Minh kiểm tra hành lang phía trước";
  const reply =
    "Cao Minh thực hiện hành động: \"" + action + "\".\n" +
    "Vị trí hiện tại: Level 0.\n" +
    "Cao Minh hiện không có đồng đội trong party.\n" +
    "Inventory hiện có: Almond Water x2.";
  const res = processRule(state, action);
  assert.equal(res.handled, true);
  assert.equal(res.reason, "committed");
  assert.equal(res.error, undefined);
  assert.equal(res.reply, reply);
  const expected = baseState();
  expected.turn = 6;
  expected.gameTime = { elapsedSubjectiveMinutes: 121, lastAdvanceMinutes: 1, lastAdvanceReason: "player_action" };
  expected.log.push({ role: "player", text: action }, { role: "gm", text: reply });
  assert.deepEqual(res.state, expected);
  assert.deepEqual(state, snapshot); // input not mutated
});

test("processRule: free action reply omits the location line when location is blank", () => {
  const state = baseState();
  delete state.location;
  state.party = [{ id: "lucia", name: "Lucia", present: true }];
  const res = processRule(state, "nhìn quanh");
  assert.equal(res.handled, true);
  assert.equal(res.reason, "committed");
  assert.equal(res.reply,
    "Cao Minh thực hiện hành động: \"nhìn quanh\".\n" +
    "Party hiện có Cao Minh, Lucia.\n" +
    "Inventory hiện có: Almond Water x2.");
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

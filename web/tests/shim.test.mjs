// C2 shim tests — offline bridge contract. The web build has no API lane:
// fetch is stubbed to RECORD (any call is a failure), and the scenarios run
// against the real C1 core port (web/core/index.js) behind the same
// GameCore global the glue sets, plus bespoke fake cores for the error
// branches. Mirrors MainActivity.GameBridge: submitTurn -> processRule ->
// backroomTurn(state) when handled, else backroomError(core message or the
// native catch-all); requestSnapshot is a no-op.
// Run: node web/tests/shim.test.mjs   (exit 0 = all green)
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import vm from "node:vm";
import { fileURLToPath, pathToFileURL } from "node:url";
import { dirname, join } from "node:path";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..", "..");
const SOURCES = ["web/core-adapter.js", "web/shim.js"]
  .map((f) => readFileSync(join(ROOT, f), "utf8"));

const PROCESS_ERROR = "Không thể xử lý lượt.";
const NOT_READY = "Game State Core chưa sẵn sàng trên bản web.";

const STATE = {
  turn: 1, mode: "story", title: "Backrooms", location: "Level 0",
  player: { name: "Cao Minh", hp: 100 }, party: [], inventory: [],
  flags: {}, log: [{ role: "gm", text: "Prologue." }], characterCanon: { cao_minh: {} }
};
const ACTION = "đi về phía cánh cửa";

const realCore = await import(pathToFileURL(join(ROOT, "web/core/index.js")).href)
  .then((core) => ({ processRule: core.processRule }));

function loadWorld(gameCore) {
  const calls = [];
  const events = { turns: [], errors: [] };
  const sandbox = {
    console, setTimeout, clearTimeout, JSON, Promise,
    fetch: (url, opts = {}) => {
      calls.push({ url: String(url), method: opts.method || "GET" });
      return Promise.resolve({ ok: true, status: 200, text: async () => "{}" });
    },
    backroomTurn: (json) => events.turns.push(json),
    backroomError: (msg) => events.errors.push(msg)
  };
  if (gameCore) sandbox.GameCore = gameCore;
  sandbox.window = sandbox;
  vm.createContext(sandbox);
  for (const src of SOURCES) vm.runInContext(src, sandbox);
  return { sandbox, calls, events };
}

async function until(fn, what) {
  const deadline = Date.now() + 3000;
  while (!fn()) {
    if (Date.now() > deadline) throw new Error("timeout waiting for " + what);
    await new Promise((r) => setTimeout(r, 5));
  }
}
const tick = (ms = 30) => new Promise((r) => setTimeout(r, ms));

const tests = [];
const test = (name, fn) => tests.push([name, fn]);

test("load: bridge installed, zero network (no warm-up, no fetch at all)", async () => {
  const { sandbox, calls } = loadWorld(realCore);
  assert.equal(typeof sandbox.Android.submitTurn, "function");
  assert.equal(typeof sandbox.Android.requestSnapshot, "function");
  await tick(60);
  assert.equal(calls.length, 0);
});

test("local-rule turn: handled by the core and emitted verbatim", async () => {
  const canned = { ...STATE, turn: 2, log: [...STATE.log, { role: "player", text: "xem inventory" }, { role: "gm", text: "Inventory hiện đang trống." }] };
  const core = { processRule: (state, action) => (action === "xem inventory" ? { handled: true, state: canned } : { handled: false }) };
  const { sandbox, calls, events } = loadWorld(core);
  sandbox.Android.submitTurn(JSON.stringify(STATE), "xem inventory");
  await until(() => events.turns.length === 1, "backroomTurn");
  assert.deepEqual(JSON.parse(events.turns[0]), canned);
  assert.equal(events.errors.length, 0);
  assert.equal(calls.length, 0);
});

test("free action (real core): commits locally with the exact neutral reply", async () => {
  const { sandbox, calls, events } = loadWorld(realCore);
  sandbox.Android.submitTurn(JSON.stringify(STATE), ACTION);
  await until(() => events.turns.length === 1, "backroomTurn");
  const committed = JSON.parse(events.turns[0]);
  assert.equal(committed.turn, 2);
  assert.equal(committed.log.length, STATE.log.length + 2);
  assert.deepEqual(committed.log.at(-2), { role: "player", text: ACTION });
  assert.deepEqual(committed.log.at(-1), {
    role: "gm",
    text: "Cao Minh thực hiện hành động: \"" + ACTION + "\".\n" +
      "Vị trí hiện tại: Level 0.\n" +
      "Cao Minh hiện không có đồng đội trong party.\n" +
      "Inventory hiện đang trống."
  });
  assert.equal(events.errors.length, 0);
  assert.equal(calls.length, 0);
});

test("inventory query (real core): canned reply commits, zero network", async () => {
  const { sandbox, calls, events } = loadWorld(realCore);
  sandbox.Android.submitTurn(JSON.stringify(STATE), "xem inventory");
  await until(() => events.turns.length === 1, "backroomTurn");
  const committed = JSON.parse(events.turns[0]);
  assert.equal(committed.turn, 2);
  assert.equal(committed.log.at(-1).text, "Inventory hiện đang trống.");
  assert.equal(calls.length, 0);
});

test("blank action (real core): native catch-all error, nothing committed", async () => {
  const { sandbox, calls, events } = loadWorld(realCore);
  sandbox.Android.submitTurn(JSON.stringify(STATE), "   ");
  await until(() => events.errors.length === 1, "backroomError");
  assert.deepEqual(events.errors, [PROCESS_ERROR]);
  assert.equal(events.turns.length, 0);
  assert.equal(calls.length, 0);
});

test("unhandled with a core error: the core's message surfaces verbatim", async () => {
  const core = { processRule: () => ({ handled: false, error: "core boom" }) };
  const { sandbox, events } = loadWorld(core);
  sandbox.Android.submitTurn(JSON.stringify(STATE), ACTION);
  await until(() => events.errors.length === 1, "backroomError");
  assert.deepEqual(events.errors, ["core boom"]);
  assert.equal(events.turns.length, 0);
});

test("missing core: not-ready error, no throw escapes", async () => {
  const { sandbox, events } = loadWorld(null);
  sandbox.Android.submitTurn(JSON.stringify(STATE), ACTION);
  await until(() => events.errors.length === 1, "backroomError");
  assert.deepEqual(events.errors, [NOT_READY]);
  assert.equal(events.turns.length, 0);
});

test("requestSnapshot: no-op — no callbacks, no fetch, no throw", async () => {
  const { sandbox, calls, events } = loadWorld(realCore);
  const out = sandbox.Android.requestSnapshot(JSON.stringify({ ...STATE, turn: 7 }));
  assert.equal(out, undefined);
  await tick(60);
  assert.equal(events.turns.length, 0);
  assert.equal(events.errors.length, 0);
  assert.equal(calls.length, 0);
});

test("turns are serialized on the chain (turn 2 then turn 3)", async () => {
  const { sandbox, events } = loadWorld(realCore);
  sandbox.Android.submitTurn(JSON.stringify(STATE), "xem inventory");
  await until(() => events.turns.length === 1, "first backroomTurn");
  sandbox.Android.submitTurn(events.turns[0], "xem party");
  await until(() => events.turns.length === 2, "second backroomTurn");
  assert.equal(JSON.parse(events.turns[0]).turn, 2);
  assert.equal(JSON.parse(events.turns[1]).turn, 3);
  assert.equal(events.errors.length, 0);
});

let failed = 0;
for (const [name, fn] of tests) {
  try {
    await fn();
    console.log("PASS", name);
  } catch (e) {
    failed++;
    console.error("FAIL", name, "\n ", e.message);
  }
}
console.log(failed === 0 ? `ALL ${tests.length} TESTS PASSED` : `${failed}/${tests.length} FAILED`);
process.exit(failed === 0 ? 0 : 1);

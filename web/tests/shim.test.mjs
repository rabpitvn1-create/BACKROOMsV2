// C2 shim tests — stubbed fetch + fake C1 core, replaying the native contract.
// Run: node web/tests/shim.test.mjs   (exit 0 = all green)
//      SHIM_CORE=real node web/tests/shim.test.mjs
//        — re-runs the scenarios that share one core against the REAL C1 port
//          (web/core/index.js) behind the same GameCore global the glue sets.
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import vm from "node:vm";
import { fileURLToPath, pathToFileURL } from "node:url";
import { dirname, join } from "node:path";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..", "..");
const SOURCES = ["web/config.js", "web/core-adapter.js", "web/shim.js"]
  .map((f) => readFileSync(join(ROOT, f), "utf8"));

const GEMINI = "https://backrooms-gemini-proxy.onrender.com";
const PREMIUM = "https://backrooms-premium-proxy.onrender.com";
const EMPTY_REPLY = "AI trả về phản hồi rỗng, lượt này không được ghi.";
const BAD_JSON = "AI trả JSON không hợp lệ.";
const NO_CONTENT = "Gemini không trả nội dung.";
const CONN_ERR = "Không kết nối được proxy Gemini. Kiểm tra mạng rồi thử lại.";
const SNAPSHOT_MSG = "Snapshot chưa khả dụng trên bản web.";

const STATE = {
  turn: 1, mode: "story", title: "Backrooms", location: "Level 0",
  player: { name: "Kai Akechi", hp: 100 }, party: [], inventory: [],
  flags: {}, log: [{ role: "gm", text: "Prologue." }], characterCanon: { kai: {} }
};
const ACTION = "đi về phía cánh cửa";

function geminiOk(payloadText) {
  return {
    ok: true, status: 200,
    text: async () => JSON.stringify({
      candidates: [{ content: { role: "model", parts: [{ text: payloadText }] } }]
    })
  };
}

function loadWorld(fetchHandler, gameCore) {
  const calls = [];
  const events = { turns: [], errors: [], snapshots: [], snapshotErrors: [] };
  const sandbox = {
    console, setTimeout, clearTimeout, AbortController, JSON, Promise,
    fetch: (url, opts = {}) => {
      calls.push({ url, method: opts.method || "GET", body: opts.body ? JSON.parse(opts.body) : undefined });
      return fetchHandler(url, opts);
    },
    backroomTurn: (json) => events.turns.push(json),
    backroomError: (msg) => events.errors.push(msg),
    backroomSnapshot: (json) => events.snapshots.push(json),
    backroomSnapshotError: (json) => events.snapshotErrors.push(json)
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

// SHIM_CORE=real swaps this shared core for the real C1 port (same global
// shape the glue assigns to window.GameCore). The two scenarios further down
// that inject bespoke cores (canned local reply, forced candidate rejection)
// keep their own fakes in both modes — they test shim branches, not the core.
const passthroughCore = process.env.SHIM_CORE === "real"
  ? await import(pathToFileURL(join(ROOT, "web/core/index.js")).href).then((core) => ({
      processRule: core.processRule,
      processValidatedCandidate: core.processValidatedCandidate
    }))
  : {
      processRule: () => ({ handled: false }),
      validateCandidate: (before, candidate) => ({
        handled: true,
        state: { ...candidate, saveVersion: 3, gameTime: { elapsedSubjectiveMinutes: 5, lastAdvanceMinutes: 5, lastAdvanceReason: "player_action" } }
      })
    };

const tests = [];
const test = (name, fn) => tests.push([name, fn]);

test("warm-up pings GET /health on both proxies at load", async () => {
  const { calls } = loadWorld(async () => ({ ok: true, status: 200, text: async () => "{}" }), passthroughCore);
  const health = calls.filter((c) => c.url.endsWith("/health"));
  assert.equal(health.length, 2);
  assert.deepEqual(health.map((c) => c.url).sort(), [GEMINI + "/health", PREMIUM + "/health"]);
  assert.ok(health.every((c) => c.method === "GET"));
});

test("success turn: request body field-by-field + committed callback payload", async () => {
  const candidate = {
    reply: "Bạn bước tới cánh cửa màu vàng.", title: "", location: "Level 0 — hành lang",
    player: { name: "Kai Akechi", hp: 100 }, party: [], inventory: [], flags: {}
  };
  const { sandbox, calls, events } = loadWorld(
    async (url) => (url.endsWith("/gemini") ? geminiOk(JSON.stringify(candidate)) : { ok: true, status: 200, text: async () => "{}" }),
    passthroughCore
  );
  sandbox.Android.submitTurn(JSON.stringify(STATE), ACTION);
  await until(() => events.turns.length === 1, "backroomTurn");

  const posts = calls.filter((c) => c.url === GEMINI + "/gemini");
  assert.equal(posts.length, 1);
  assert.equal(posts[0].method, "POST");
  assert.equal(posts[0].body.model, "gemini-3.6-flash");
  assert.deepEqual(posts[0].body.generationConfig, { responseMimeType: "application/json", temperature: 0.8 });
  const expectedPrompt =
    "Bạn là Game Master của text game Backrooms. Xử lý đúng một lượt và trả DUY NHẤT JSON hợp lệ, không markdown. " +
    "Viết tiếng Việt tự nhiên, đầy đủ ý. Không trả lời bằng câu rỗng. Không thay đổi dữ kiện chưa có căn cứ. Người chơi chỉ điều khiển Kai Akechi. " +
    "State hiện tại: " + JSON.stringify(STATE) + "\nHành động: " + ACTION +
    "\nJSON bắt buộc: {\"reply\":\"phản hồi Game Master\",\"title\":\"giữ nguyên hoặc cập nhật\",\"location\":\"vị trí sau lượt\",\"player\":{},\"party\":[],\"inventory\":[],\"flags\":{}}";
  assert.deepEqual(posts[0].body.contents, [{ role: "user", parts: [{ text: expectedPrompt }] }]);

  assert.equal(events.errors.length, 0);
  const committed = JSON.parse(events.turns[0]);
  assert.equal(committed.turn, 2);
  assert.equal(committed.mode, "ai");
  assert.equal(committed.title, "Backrooms"); // empty candidate title keeps the old one
  assert.equal(committed.location, "Level 0 — hành lang");
  assert.equal(committed.saveVersion, 3);
  assert.equal(committed.log.length, STATE.log.length + 2);
  assert.deepEqual(committed.log.at(-2), { role: "player", text: ACTION });
  assert.deepEqual(committed.log.at(-1), { role: "gm", text: candidate.reply });
});

test("proxy 502 all-keys-failed: proxy error text surfaces, no retry, no commit", async () => {
  const { sandbox, calls, events } = loadWorld(
    async (url) => (url.endsWith("/gemini")
      ? { ok: false, status: 502, text: async () => JSON.stringify({ error: "all keys failed", model: "gemini-3.6-flash", keyErrors: ["k1 429"] }) }
      : { ok: true, status: 200, text: async () => "{}" }),
    passthroughCore
  );
  sandbox.Android.submitTurn(JSON.stringify(STATE), ACTION);
  await until(() => events.errors.length === 1, "backroomError");
  assert.deepEqual(events.errors, ["all keys failed"]);
  assert.equal(events.turns.length, 0);
  assert.equal(calls.filter((c) => c.url.endsWith("/gemini")).length, 1); // HTTP responses are never retried
});

test("empty GM reply: native rejection string, state untouched", async () => {
  const { sandbox, events } = loadWorld(
    async (url) => (url.endsWith("/gemini")
      ? geminiOk(JSON.stringify({ reply: "   ", title: "", location: "", player: {}, party: [], inventory: [], flags: {} }))
      : { ok: true, status: 200, text: async () => "{}" }),
    passthroughCore
  );
  sandbox.Android.submitTurn(JSON.stringify(STATE), ACTION);
  await until(() => events.errors.length === 1, "backroomError");
  assert.deepEqual(events.errors, [EMPTY_REPLY]);
  assert.equal(events.turns.length, 0);
});

test("malformed JSON reply: native parse error, state untouched", async () => {
  const { sandbox, events } = loadWorld(
    async (url) => (url.endsWith("/gemini") ? geminiOk("Dưới đây là kết quả của bạn, chúc vui vẻ") : { ok: true, status: 200, text: async () => "{}" }),
    passthroughCore
  );
  sandbox.Android.submitTurn(JSON.stringify(STATE), ACTION);
  await until(() => events.errors.length === 1, "backroomError");
  assert.deepEqual(events.errors, [BAD_JSON]);
  assert.equal(events.turns.length, 0);
});

test("response with no candidate text: native no-content error", async () => {
  const { sandbox, events } = loadWorld(
    async (url) => (url.endsWith("/gemini")
      ? { ok: true, status: 200, text: async () => JSON.stringify({ candidates: [] }) }
      : { ok: true, status: 200, text: async () => "{}" }),
    passthroughCore
  );
  sandbox.Android.submitTurn(JSON.stringify(STATE), ACTION);
  await until(() => events.errors.length === 1, "backroomError");
  assert.deepEqual(events.errors, [NO_CONTENT]);
  assert.equal(events.turns.length, 0);
});

test("network throw: exactly one retry, then the turn commits", async () => {
  let attempts = 0;
  const candidate = { reply: "Ổn rồi.", title: "", location: "", player: {}, party: [], inventory: [], flags: {} };
  const { sandbox, calls, events } = loadWorld(
    async (url) => {
      if (!url.endsWith("/gemini")) return { ok: true, status: 200, text: async () => "{}" };
      attempts++;
      if (attempts === 1) throw new TypeError("fetch failed");
      return geminiOk(JSON.stringify(candidate));
    },
    passthroughCore
  );
  sandbox.Android.submitTurn(JSON.stringify(STATE), ACTION);
  await until(() => events.turns.length === 1, "backroomTurn after retry");
  assert.equal(calls.filter((c) => c.url.endsWith("/gemini")).length, 2);
  assert.equal(events.errors.length, 0);
});

test("network throw twice: fixed connection error after the single retry", async () => {
  const { sandbox, calls, events } = loadWorld(
    async (url) => {
      if (!url.endsWith("/gemini")) return { ok: true, status: 200, text: async () => "{}" };
      throw new TypeError("fetch failed");
    },
    passthroughCore
  );
  sandbox.Android.submitTurn(JSON.stringify(STATE), ACTION);
  await until(() => events.errors.length === 1, "backroomError");
  assert.deepEqual(events.errors, [CONN_ERR]);
  assert.equal(events.turns.length, 0);
  assert.equal(calls.filter((c) => c.url.endsWith("/gemini")).length, 2); // 1 attempt + 1 retry
});

test("local-rule turn: handled by the core, fetch is never called for a turn", async () => {
  const canned = { ...STATE, turn: 2, log: [...STATE.log, { role: "player", text: "xem inventory" }, { role: "gm", text: "Inventory hiện đang trống." }] };
  const core = {
    processRule: (state, action) => (action === "xem inventory" ? { handled: true, state: canned } : { handled: false }),
    validateCandidate: () => { throw new Error("must not be called"); }
  };
  const { sandbox, calls, events } = loadWorld(async () => ({ ok: true, status: 200, text: async () => "{}" }), core);
  sandbox.Android.submitTurn(JSON.stringify(STATE), "xem inventory");
  await until(() => events.turns.length === 1, "backroomTurn");
  assert.deepEqual(JSON.parse(events.turns[0]), canned);
  assert.equal(calls.filter((c) => c.url.endsWith("/gemini")).length, 0);
  assert.equal(events.errors.length, 0);
});

test("core rejection of the candidate: native refusal prefix, no commit", async () => {
  const candidate = { reply: "GM muốn sửa inventory.", title: "", location: "", player: {}, party: [], inventory: [{ name: "Súng", quantity: 1 }], flags: {} };
  const core = {
    processRule: () => ({ handled: false }),
    validateCandidate: () => ({ handled: false, error: "inventory_locked" })
  };
  const { sandbox, events } = loadWorld(
    async (url) => (url.endsWith("/gemini") ? geminiOk(JSON.stringify(candidate)) : { ok: true, status: 200, text: async () => "{}" }),
    core
  );
  sandbox.Android.submitTurn(JSON.stringify(STATE), ACTION);
  await until(() => events.errors.length === 1, "backroomError");
  assert.deepEqual(events.errors, ["Game State Core từ chối Gemini delta: inventory_locked"]);
  assert.equal(events.turns.length, 0);
});

test("snapshot floor: immediate native-shaped error, no image call attempted", async () => {
  const { sandbox, calls, events } = loadWorld(async () => ({ ok: true, status: 200, text: async () => "{}" }), passthroughCore);
  const before = calls.length;
  sandbox.Android.requestSnapshot(JSON.stringify({ ...STATE, turn: 7 }));
  await until(() => events.snapshotErrors.length === 1, "backroomSnapshotError");
  assert.deepEqual(JSON.parse(events.snapshotErrors[0]), { turn: 7, message: SNAPSHOT_MSG });
  assert.equal(events.snapshots.length, 0);
  assert.equal(calls.length, before); // the floor makes no network call at all
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

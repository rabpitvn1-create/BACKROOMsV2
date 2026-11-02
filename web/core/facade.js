// Port of com.rabpit.backroom.core.GameCoreFacade (290 lines) minus the Android
// glue: the SharedPreferences persist()/currentCoreState()/clear() and Log.d are
// NOT ported — persistence is the web save layer's job (cluster C4). Everything
// else mirrors the Java control flow function for function.
//
// Inputs: state/candidate may be a plain JS object or a JSON string (the Java
// signatures are String-in/String-out; C2's shim passes parsed objects).
// Inputs are never mutated: every mutation happens on a deep copy, exactly as
// the Java deepCopy() discipline does. Fallback/rejection results return the
// (unmutated) input state object itself, mirroring the Java `legacy`/`before`
// reference in the response.
//
// org.json semantics are hand-matched by the opt* helpers below (AOSP org.json):
// - optString: missing/null -> default; String -> itself; Number/Boolean ->
//   their Java toString; object/array -> its JSON serialization.
// - optInt/optLong: Number -> Java (int)/(long) cast (truncate toward zero,
//   clamp, NaN -> 0); numeric String -> parsed as double then cast; anything
//   else -> default.
// - optBoolean: Boolean -> itself; String "true"/"false" (case-insensitive);
//   else default.
// - optJSONObject/optJSONArray: wrong type -> null (never a fresh {} / []).
// - has(): true when the key exists, even if its value is JSON null.
// Known residual: a JSON literal like 5.0 parses to Double in org.json
// (optString -> "5.0") but to 5 in JS (optString -> "5"). Only reachable if a
// string-typed field (name/id) carries a decimal-form number — not in real data.

import {
  estimateMinutes,
  inventoryMutationLocked,
  isDirectPlayerPickupAction,
  isInventoryQuery,
  isPartyQuery,
  javaTrim,
  stableItemId,
} from "./rules.js";

const CURRENT_SAVE_VERSION = 3;

const isObject = (v) => typeof v === "object" && v !== null && !Array.isArray(v);
const has = (o, key) => Object.prototype.hasOwnProperty.call(o, key);

function optString(o, key, fallback) {
  const v = o[key];
  if (v === undefined || v === null) return fallback;
  if (typeof v === "string") return v;
  if (typeof v === "number" || typeof v === "boolean") return String(v);
  return JSON.stringify(v);
}

function intCast(d) {
  // Java (int) cast of a double: NaN -> 0, clamp to int range, truncate.
  if (Number.isNaN(d)) return 0;
  if (d >= 2147483647) return 2147483647;
  if (d <= -2147483648) return -2147483648;
  return Math.trunc(d);
}

function optInt(o, key, fallback) {
  const v = o[key];
  if (typeof v === "number") return intCast(v);
  if (typeof v === "string" && javaTrim(v) !== "") {
    const d = Number(v);
    if (!Number.isNaN(d)) return intCast(d);
  }
  return fallback;
}

function optLong(o, key, fallback) {
  // Same coercion as optInt; the clamp is JS's safe-integer range instead of
  // Java's long range — elapsed minutes never come near either.
  const v = o[key];
  if (typeof v === "number") return Math.trunc(v) || 0;
  if (typeof v === "string" && javaTrim(v) !== "") {
    const d = Number(v);
    if (!Number.isNaN(d)) return Math.trunc(d);
  }
  return fallback;
}

function optBoolean(o, key, fallback) {
  const v = o[key];
  if (typeof v === "boolean") return v;
  if (typeof v === "string") {
    const s = v.toLowerCase();
    if (s === "true") return true;
    if (s === "false") return false;
  }
  return fallback;
}

function optJSONObject(o, key) {
  const v = o[key];
  return isObject(v) ? v : null;
}

function optJSONArray(o, key) {
  const v = o[key];
  return Array.isArray(v) ? v : null;
}

// Java parseState(): null/blank/unparseable/non-object JSON -> {}.
function parseState(input) {
  if (input == null) return {};
  if (typeof input === "object") return isObject(input) ? input : {};
  if (typeof input !== "string" || javaTrim(input) === "") return {};
  try {
    const parsed = JSON.parse(input);
    return isObject(parsed) ? parsed : {};
  } catch {
    return {};
  }
}

// Java deepCopy(): serialization round-trip; null source -> {}.
function deepCopy(source) {
  if (source == null) return {};
  return JSON.parse(JSON.stringify(source));
}

function copyField(source, target, key) {
  // Java puts the source's value reference; a deep copy is serialization-
  // equivalent here and cannot alias the caller's `before` state.
  if (source != null && has(source, key)) target[key] = deepCopy(source[key]);
  else delete target[key];
}

function normalizeInventory(input) {
  const output = [];
  if (input == null) return output;
  const deduplicated = new Map(); // LinkedHashMap: overwrite keeps first position
  for (const raw of input) {
    const item = isObject(raw) ? raw : null;
    if (item == null) continue;
    const name = javaTrim(optString(item, "name", ""));
    if (name === "") continue;
    let id = javaTrim(optString(item, "id", ""));
    if (id === "") id = stableItemId(name);
    const quantity = Math.max(1, optInt(item, "quantity", 1));
    const normalized = deepCopy(item);
    normalized.id = id;
    normalized.name = name;
    normalized.quantity = quantity;
    deduplicated.set(id, normalized);
  }
  for (const value of deduplicated.values()) output.push(value);
  return output;
}

function memberId(member) {
  const id = javaTrim(optString(member, "id", ""));
  if (id !== "") return id;
  return javaTrim(optString(member, "name", "")).toLowerCase();
}

function sanitizeParty(before, candidate) {
  const safeBefore = before == null ? [] : before;
  const safeCandidate = candidate == null ? [] : candidate;
  const existing = new Map();
  for (const raw of safeBefore) {
    const member = isObject(raw) ? raw : null;
    if (member == null) continue;
    const id = memberId(member);
    if (id !== "") existing.set(id, member);
  }
  const output = [];
  for (const raw of safeCandidate) {
    const member = isObject(raw) ? raw : null;
    if (member == null) continue;
    const id = memberId(member);
    if (id === "") continue;
    const alreadyMember = existing.has(id);
    const confirmedJoin =
      optBoolean(member, "joinConfirmed", false) && optBoolean(member, "present", false);
    if (alreadyMember || confirmedJoin) output.push(deepCopy(member));
  }
  return output;
}

function incrementTurn(state) {
  state.turn = Math.max(1, optInt(state, "turn", 1)) + 1;
}

function advanceGameTime(state, action) {
  let gameTime = optJSONObject(state, "gameTime");
  if (gameTime == null) gameTime = {};
  const minutes = estimateMinutes(action);
  const elapsed = Math.max(0, optLong(gameTime, "elapsedSubjectiveMinutes", 0));
  gameTime.elapsedSubjectiveMinutes = elapsed + minutes;
  gameTime.lastAdvanceMinutes = minutes;
  gameTime.lastAdvanceReason = "player_action";
  state.gameTime = gameTime;
  state.saveVersion = CURRENT_SAVE_VERSION;
}

function advanceGameTimeFromBefore(before, candidate, action) {
  const prior = optJSONObject(before, "gameTime");
  const elapsed = prior == null ? 0 : Math.max(0, optLong(prior, "elapsedSubjectiveMinutes", 0));
  const minutes = estimateMinutes(action);
  candidate.gameTime = {
    elapsedSubjectiveMinutes: elapsed + minutes,
    lastAdvanceMinutes: minutes,
    lastAdvanceReason: "player_action",
  };
}

function inventoryReply(inventory) {
  if (inventory == null || inventory.length === 0) return "Inventory hiện đang trống.";
  let reply = "Inventory hiện có: ";
  let first = true;
  for (const raw of inventory) {
    const item = isObject(raw) ? raw : null;
    if (item == null) continue;
    const name = javaTrim(optString(item, "name", ""));
    if (name === "") continue;
    if (!first) reply += ", ";
    first = false;
    reply += name;
    const quantity = Math.max(1, optInt(item, "quantity", 1));
    if (quantity > 1) reply += " x" + quantity;
  }
  if (first) return "Inventory hiện đang trống.";
  return reply + ".";
}

function partyReply(party) {
  if (party == null || party.length === 0) return "Kai hiện không có đồng đội trong party.";
  let reply = "Party hiện có Kai";
  for (const raw of party) {
    const member = isObject(raw) ? raw : null;
    if (member == null) continue;
    const name = javaTrim(optString(member, "name", optString(member, "id", "")));
    if (name !== "") reply += ", " + name;
  }
  return reply + ".";
}

function appendLog(state, action, reply) {
  let log = optJSONArray(state, "log");
  if (log == null) log = [];
  log.push({ role: "player", text: action });
  log.push({ role: "gm", text: reply });
  state.log = log;
}

function response(handled, state, error, reason, reply) {
  const output = { handled, state: state == null ? {} : state, reason: reason == null ? "" : reason };
  if (error != null && error !== "") output.error = error;
  if (reply != null && reply !== "") output.reply = reply;
  return output;
}

function safeMessage(e) {
  if (e == null || e.message == null || javaTrim(String(e.message)) === "") {
    return "Game State Core error";
  }
  return e.message;
}

/**
 * Port of GameCoreFacade.processRule. Tries local handling of the action.
 * @param {object|string} stateInput current game state (object or JSON string)
 * @param {string} action raw player action text
 * @returns {{handled: boolean, state: object, reason: string, error?: string, reply?: string}}
 *   reason: "committed" | "validation_rejected" | "fallback_required" | "core_error".
 *   On "fallback_required" the caller (C2 orchestration) runs the GM pipeline
 *   and then calls processValidatedCandidate.
 */
export function processRule(stateInput, action) {
  const legacy = parseState(stateInput);
  try {
    const text = action == null ? "" : javaTrim(action);
    if (text === "") return response(false, legacy, null, "fallback_required", null);

    if (isDirectPlayerPickupAction(text)) {
      const result = deepCopy(legacy);
      const reply = "[Warning] This action is not available.";
      appendLog(result, text, reply);
      return response(true, result, "player_pickup_unavailable", "validation_rejected", reply);
    }

    if (isInventoryQuery(text)) {
      const result = deepCopy(legacy);
      incrementTurn(result);
      advanceGameTime(result, text);
      const reply = inventoryReply(optJSONArray(result, "inventory"));
      appendLog(result, text, reply);
      return response(true, result, null, "committed", reply);
    }

    if (isPartyQuery(text)) {
      const result = deepCopy(legacy);
      incrementTurn(result);
      advanceGameTime(result, text);
      const reply = partyReply(optJSONArray(result, "party"));
      appendLog(result, text, reply);
      return response(true, result, null, "committed", reply);
    }

    return response(false, legacy, null, "fallback_required", null);
  } catch (e) {
    return response(false, legacy, safeMessage(e), "core_error", null);
  }
}

/**
 * Port of GameCoreFacade.processValidatedCandidate. Validates/sanitizes a
 * GM-proposed candidate state against the pre-turn state and commits it.
 * @param {object|string} beforeInput state before the turn
 * @param {object|string} candidateInput GM-proposed next state
 * @param {string} action the player action that produced the candidate
 * @returns {{handled: boolean, state: object, reason: string, error?: string}}
 *   reason: "gemini_delta_committed" | "gemini_delta_rejected".
 */
export function processValidatedCandidate(beforeInput, candidateInput, action) {
  const before = parseState(beforeInput);
  try {
    const candidate = parseState(candidateInput);
    const sanitized = deepCopy(candidate);

    if (inventoryMutationLocked(action)) {
      copyField(before, sanitized, "inventory");
    } else if (has(candidate, "inventory")) {
      sanitized.inventory = normalizeInventory(optJSONArray(candidate, "inventory"));
    } else {
      copyField(before, sanitized, "inventory");
    }

    sanitized.party = sanitizeParty(optJSONArray(before, "party"), optJSONArray(candidate, "party"));
    sanitized.saveVersion = CURRENT_SAVE_VERSION;
    advanceGameTimeFromBefore(before, sanitized, action);

    return response(true, sanitized, null, "gemini_delta_committed", null);
  } catch (e) {
    return response(false, before, safeMessage(e), "gemini_delta_rejected", null);
  }
}

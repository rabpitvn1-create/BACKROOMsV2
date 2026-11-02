// Web core port of the BACKROOMsV2 Java game core (cluster C1).
// API surface consumed by the C2 bridge shim — see README.md.
export {
  estimateMinutes,
  inventoryMutationLocked,
  isDirectPlayerPickupAction,
  isInventoryQuery,
  isPartyQuery,
  isRestoreAction,
  javaTrim,
  stableItemId,
} from "./rules.js";
export { processRule, processValidatedCandidate } from "./facade.js";

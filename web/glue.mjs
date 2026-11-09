// Glue between the C1 core port (ES modules under core/) and C2's
// core-adapter.js, which probes a global provider object. This file is the
// only place the two surfaces meet: it imports C1's entry point and assigns
// the exact global the adapter looks for.
//
// Loaded as <script type="module" src="glue.mjs"> ahead of core-adapter.js
// in document order. Module scripts are deferred by spec, so the assignment
// lands after document parsing — safe because core-adapter probes the
// global lazily on the first turn, never at load, and the page cannot
// submit a turn before boot completes.
import { processRule } from "./core/index.js";

window.GameCore = { processRule };

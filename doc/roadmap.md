# Roadmap

This roadmap tracks the planned development phases for `trader1`.

Use it as the index:

- `Current` shows the phase we are currently working through or have just completed.
- `Next` shows the phase that should start after the current one is tested.
- `Later` captures ideas that matter, but are not yet scheduled.

Detailed scope, tasks, and acceptance criteria live in [`doc/phases/`](./phases/).

## Current

- Phase 01: Development planning foundation - done
- Phase 02: Cleanup and runtime configuration - done
- Phase 03: IB open orders improvements - done
- Phase 04: IB order submission - in progress

## Next
- Phase 16: Baseline CI - planned

## Later

- Phase 17: Security gates - planned
- Phase 18: CI visibility and reliability - planned
- Phase 19: Staging CD - planned
- Phase 20: Production CD and hardening - planned
- Phase 21: Advanced quality and supply chain - planned
- Phase 15: First-run setup wizard - planned
- Phase 05: Kraken order submission - planned
- Phase 06: Kraken order management - planned
- Phase 07: Unified order ticket - planned
- Phase 08: Safety and audit layer - planned
- Phase 09: Historical portfolio tracking - planned
- Phase 10: Annual transaction report - planned
- Phase 11: Instrument grouping and exposure views - planned
- Phase 12: Alerts and thresholds - planned
- Phase 13: Connection health dashboard - planned
- Phase 14: Sandbox mode - planned
- Phase 15: Hyperliquid integration - planned

## Notes On Sequence

- IB trading work comes before Kraken because it is the most important workflow and likely sets the stricter backend requirements.
- CI and security delivery work should start immediately after the current IB order-submission hardening is stable enough to benefit from consistent automation.
- Delivery phases are numbered after the existing product phases to avoid renumbering the current roadmap files, but they are intentionally sequenced ahead of more feature expansion.
- Kraken order entry and management are split so submission can land before cancellation and lifecycle improvements.
- Instrument work is treated as grouping and exposure reporting, not as a full normalization problem.
- Hyperliquid comes later so it can plug into a cleaner service shape once the broker and exchange flows are more mature.

## How To Use This

1. Pick the top phase from `Current` or `Next`.
2. Refine its phase file until scope and acceptance criteria are clear.
3. Implement the phase end-to-end.
4. Test the phase against its acceptance criteria.
5. Update this roadmap to move the next phase into `Current`.

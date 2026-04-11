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
- Phase 03: IB open orders improvements - in progress

## Next
- Phase 04: IB order submission - planned

## Later

- Phase 15: First-run setup wizard - planned
- Phase 05: Kraken order submission - planned
- Phase 06: Kraken order management - planned
- Phase 07: Unified order ticket - planned
- Phase 08: Safety and audit layer - planned
- Phase 09: Historical portfolio tracking - planned
- Phase 10: Instrument grouping and exposure views - planned
- Phase 11: Alerts and thresholds - planned
- Phase 12: Connection health dashboard - planned
- Phase 13: Sandbox mode - planned
- Phase 14: Hyperliquid integration - planned

## Notes On Sequence

- IB trading work comes before Kraken because it is the most important workflow and likely sets the stricter backend requirements.
- Kraken order entry and management are split so submission can land before cancellation and lifecycle improvements.
- Instrument work is treated as grouping and exposure reporting, not as a full normalization problem.
- Hyperliquid comes later so it can plug into a cleaner service shape once the broker and exchange flows are more mature.

## How To Use This

1. Pick the top phase from `Current` or `Next`.
2. Refine its phase file until scope and acceptance criteria are clear.
3. Implement the phase end-to-end.
4. Test the phase against its acceptance criteria.
5. Update this roadmap to move the next phase into `Current`.

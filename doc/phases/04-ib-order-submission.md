# Phase 04: IB Order Submission

## Status

In progress

## Goal

Extend the initial IB order-entry path into a more complete and safer submission workflow with clearer validation, feedback, and broader order support.

## Why Now

IB order submission is a primary product goal. After Phase 03 lands a minimal order panel and first submission path, this phase can focus on hardening, validation quality, and expanding the supported workflow without redoing the first vertical slice.

## Scope

- [x] Harden the initial IB order submission path with clearer validation and safer defaults
- [x] Expand the order panel beyond the first minimal workflow where justified
- [x] Improve success, failure, and reconciliation feedback after submission
- [x] Support follow-on order-entry requirements that do not fit cleanly into Phase 03

## Out Of Scope

- Editing submitted orders
- Advanced order types such as bracket, stop-limit, or algo orders
- Multi-account workflows
- Full execution history

## Technical Areas

- `vendor/ib-cl-wrap` or a local IB adapter layer
- backend request validation
- websocket updates or post-submit refresh flow
- frontend order form
- tests for request validation and submission handling

## Tasks

- [x] Review the minimal Phase 03 order panel and identify gaps that still block safe day-to-day use
- [x] Confirm whether wrapper changes are required and where they should live
- [x] Tighten the backend order request shape and validation rules
- [x] Expand the order-entry UI where the minimal panel is too limiting
- [x] Improve post-submit reconciliation and operator feedback
- [x] Add tests for malformed input, validation edge cases, and backend submission handling
- [ ] Validate the flow against a safe IB environment before calling the phase done

## Acceptance Criteria

- [x] The initial IB order-entry path from Phase 03 is hardened enough for repeatable use
- [x] Invalid input is rejected clearly before or during submission
- [x] Successful submission is reflected in the UI within a reasonable time
- [x] Open orders update without requiring a full application restart
- [x] Backend behavior is covered by focused tests

## Notes

Recommended initial scope:

- stocks only
- market and limit orders only
- buy and sell only

Phase 03 now owns the first small, trustworthy trading path. This phase should build on that baseline rather than reintroduce it.

Implementation note:

- Backend requests are now normalized and validated before contract lookup, including whole-share quantity rules, DAY/GTC support, and safer defaults for outside regular trading hours.
- The dashboard now shows explicit submission status plus whether the immediate open-orders refresh succeeded after placement.
- Wrapper changes were not required for this slice; the hardening work stays in the local request-validation and reconciliation layer.
- Phase status remains in progress until the updated flow is validated against a safe live or paper IB session.

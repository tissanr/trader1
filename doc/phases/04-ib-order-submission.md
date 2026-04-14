# Phase 04: IB Order Submission

## Status

Planned

## Goal

Extend the initial IB order-entry path into a more complete and safer submission workflow with clearer validation, feedback, and broader order support.

## Why Now

IB order submission is a primary product goal. After Phase 03 lands a minimal order panel and first submission path, this phase can focus on hardening, validation quality, and expanding the supported workflow without redoing the first vertical slice.

## Scope

- [ ] Harden the initial IB order submission path with clearer validation and safer defaults
- [ ] Expand the order panel beyond the first minimal workflow where justified
- [ ] Improve success, failure, and reconciliation feedback after submission
- [ ] Support follow-on order-entry requirements that do not fit cleanly into Phase 03

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

- [ ] Review the minimal Phase 03 order panel and identify gaps that still block safe day-to-day use
- [ ] Confirm whether wrapper changes are required and where they should live
- [ ] Tighten the backend order request shape and validation rules
- [ ] Expand the order-entry UI where the minimal panel is too limiting
- [ ] Improve post-submit reconciliation and operator feedback
- [ ] Add tests for malformed input, validation edge cases, and backend submission handling
- [ ] Validate the flow against a safe IB environment before calling the phase done

## Acceptance Criteria

- [ ] The initial IB order-entry path from Phase 03 is hardened enough for repeatable use
- [ ] Invalid input is rejected clearly before or during submission
- [ ] Successful submission is reflected in the UI within a reasonable time
- [ ] Open orders update without requiring a full application restart
- [ ] Backend behavior is covered by focused tests

## Notes

Recommended initial scope:

- stocks only
- market and limit orders only
- buy and sell only

Phase 03 now owns the first small, trustworthy trading path. This phase should build on that baseline rather than reintroduce it.

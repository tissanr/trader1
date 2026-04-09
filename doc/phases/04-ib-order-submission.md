# Phase 04: IB Order Submission

## Status

Planned

## Goal

Allow the user to submit simple Interactive Brokers orders from the application with clear validation, feedback, and order-state refresh behavior.

## Why Now

IB order submission is a primary product goal. It follows the open-orders improvement phase so the app has a trustworthy representation of existing broker orders before it starts creating new ones.

## Scope

- [ ] Support initial submission of a small set of IB order types
- [ ] Add a backend path for order submission and result handling
- [ ] Add a UI flow for entering order details
- [ ] Refresh or reconcile open orders after submission
- [ ] Show success and failure feedback to the user

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

- [ ] Decide the initial supported IB instrument and order scope
- [ ] Confirm whether wrapper changes are required and where they should live
- [ ] Define the backend order request shape
- [ ] Implement server-side validation for required fields and safe defaults
- [ ] Build a minimal order entry UI
- [ ] Trigger open-order refresh after submission or subscribe to relevant events
- [ ] Add tests for malformed input and backend submission handling
- [ ] Validate the flow against a safe IB environment before calling the phase done

## Acceptance Criteria

- [ ] A user can submit at least one simple IB order type end-to-end
- [ ] Invalid input is rejected clearly before or during submission
- [ ] Successful submission is reflected in the UI within a reasonable time
- [ ] Open orders update without requiring a full application restart
- [ ] Backend behavior is covered by focused tests

## Notes

Recommended initial scope:

- stocks only
- market and limit orders only
- buy and sell only

This phase should optimize for a small, trustworthy first trading path rather than broad order-type coverage.

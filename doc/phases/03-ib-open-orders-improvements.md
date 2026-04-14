# Phase 03: IB Open Orders Improvements

## Status

Done

## Goal

Make Interactive Brokers open orders more reliable, understandable, and useful in the dashboard, and add a minimal IB order panel that is tightly coupled to the portfolio and open-orders view.

## Why Now

If users cannot trust what the app shows for existing IB orders, adding order submission will increase confusion and risk. This phase now covers both sides of that workflow: users need to see open orders clearly, and they need a minimal order-entry panel in the IB portfolio area so submission and visibility can be tested together.

## Scope

- [x] Review how IB open orders are fetched, normalized, and pushed to the UI
- [x] Improve data completeness and status clarity where the wrapper and API allow it
- [x] Make disconnected or partial-data states visible to the user
- [x] Reduce ambiguity between "no orders", "loading", "timed out", and "disconnected"
- [x] Add a minimal IB order panel that is accessible from the IB portfolio section
- [x] Support a small first order-entry path that feeds directly into the open-orders view
- [x] Prepare the data shape and UI flow for later, broader order submission feedback

## Out Of Scope

- Editing existing IB orders
- Supporting every IB order type
- Advanced order types beyond the first minimal panel
- Multi-step trade workflows, execution history, or risk tooling

## Technical Areas

- `vendor/ib-cl-wrap`
- backend IB snapshot and event flow
- websocket payload shape
- frontend orders table and cell error handling
- frontend IB portfolio/order panel layout
- backend order submission path and post-submit refresh behavior
- tests around order normalization, submission handling, and failure modes

## Tasks

- [x] Inspect the current IB open-orders flow from API wrapper to UI table
- [x] Decide which order fields are required for a useful operator view
- [x] Improve normalization of status, quantities, prices, and timestamps if available
- [x] Make timeout and disconnected behavior clearer in payloads and UI
- [x] Define the minimal order panel fields and where it lives in the IB portfolio section
- [x] Implement a first IB order-entry path with safe defaults and limited scope
- [x] Refresh or reconcile open orders immediately after submission
- [x] Add or update tests for normalized order rows, submission handling, and error states
- [x] Validate behavior with live or simulated IB data

## Acceptance Criteria

- [x] The IB orders table clearly distinguishes empty state, timeout state, and disconnected state
- [x] Order rows include the most important fields needed for trading decisions
- [x] A user can access a minimal IB order panel directly from the IB portfolio section
- [x] A user can submit at least one simple IB order path and see the result reflected in the open-orders area within a reasonable time
- [x] The data shape and UI flow are stable enough to reuse in later order submission phases
- [x] Relevant tests cover normalization, submission, and error handling
- [x] Existing IB views still work after the changes

## Notes

Open planning change:

- Phase 03 now includes a minimal IB order panel and first submission path, so Phase 04 should narrow toward hardening and broader order-entry scope rather than introducing the first UI from scratch.

Completion note:

- Phase 03 now covers explicit IB open-order state handling, live order visibility updates, a minimal order panel in the IB portfolio area, and the first market/limit submission path for stocks.

Likely useful fields:

- symbol
- action
- order type
- quantity
- limit price
- status
- filled
- remaining
- account

Recommended first panel scope:

- stocks only
- market and limit orders only
- buy and sell only
- panel lives inside or directly adjacent to the IB portfolio section

If execution-related timestamps are available cheaply, they may be useful later but are not required for this phase.

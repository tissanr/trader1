# Phase 03: IB Open Orders Improvements

## Status

In progress

## Goal

Make Interactive Brokers open orders more reliable, understandable, and useful in the dashboard before adding order submission.

## Why Now

If users cannot trust what the app shows for existing IB orders, adding order submission will increase confusion and risk. This phase improves visibility into the current broker state first.

## Scope

- [ ] Review how IB open orders are fetched, normalized, and pushed to the UI
- [ ] Improve data completeness and status clarity where the wrapper and API allow it
- [ ] Make disconnected or partial-data states visible to the user
- [ ] Reduce ambiguity between "no orders", "loading", "timed out", and "disconnected"
- [ ] Prepare the data shape for later order submission feedback

## Out Of Scope

- Submitting new IB orders
- Editing existing IB orders
- Supporting every IB order type

## Technical Areas

- `vendor/ib-cl-wrap`
- backend IB snapshot and event flow
- websocket payload shape
- frontend orders table and cell error handling
- tests around order normalization and failure modes

## Tasks

- [ ] Inspect the current IB open-orders flow from API wrapper to UI table
- [ ] Decide which order fields are required for a useful operator view
- [ ] Improve normalization of status, quantities, prices, and timestamps if available
- [ ] Make timeout and disconnected behavior clearer in payloads and UI
- [ ] Add or update tests for normalized order rows and error states
- [ ] Validate behavior with live or simulated IB data

## Acceptance Criteria

- [ ] The IB orders table clearly distinguishes empty state, timeout state, and disconnected state
- [ ] Order rows include the most important fields needed for trading decisions
- [ ] The data shape is stable enough to reuse in later order submission phases
- [ ] Relevant tests cover normalization and error handling
- [ ] Existing IB views still work after the changes

## Notes

Key open question:

- Should this phase extend `ib-cl-wrap`, or can the needed improvements be done in `trader1` with better mapping and UI handling?

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

If execution-related timestamps are available cheaply, they may be useful later but are not required for this phase.

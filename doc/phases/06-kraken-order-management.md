# Phase 06: Kraken Order Management

## Status

Planned

## Goal

Add order management for Kraken so open orders can be cancelled and tracked more clearly after submission.

## Why Now

Order submission without order management leaves the operator with an incomplete workflow. This phase turns Kraken order entry into a more usable trading loop before the app moves toward a unified ticket.

## Scope

- [ ] Add cancel support for eligible Kraken open orders
- [ ] Improve order status visibility and refresh behavior
- [ ] Add clear feedback for cancellation success and failure
- [ ] Make the Kraken orders view more operationally useful

## Out Of Scope

- IB order cancellation
- editing or replacing Kraken orders
- advanced lifecycle analytics

## Technical Areas

- `trader1.kraken`
- websocket updates for changed order state
- frontend Kraken orders table and actions
- tests around cancellation flow and error handling

## Tasks

- [ ] Implement backend support for cancelling Kraken orders
- [ ] Define how order IDs and cancel actions are represented in the UI
- [ ] Refresh open orders after cancellation
- [ ] Improve how status changes are shown in the UI
- [ ] Add tests for successful and failed cancel operations
- [ ] Validate safe behavior when an order changes state between UI render and cancel attempt

## Acceptance Criteria

- [ ] A user can cancel an eligible Kraken open order from the app
- [ ] The UI gives clear feedback if cancellation succeeds, fails, or the order is no longer open
- [ ] The open-orders table remains accurate after cancellation attempts
- [ ] Relevant tests cover backend cancellation behavior and error mapping

## Notes

This phase may also refine how the app distinguishes:

- newly submitted
- open
- partially filled
- closed or canceled

Keep the behavior operationally clear even if the first pass is not visually elaborate.

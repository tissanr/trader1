# Phase 05: Kraken Order Submission

## Status

Planned

## Goal

Allow the user to submit simple Kraken orders from the application with clear validation and visible order feedback.

## Why Now

Kraken order entry is an important complement to IB trading, but it should follow the IB work so the application learns from the stricter broker flow first. Kraken can then reuse some UI ideas while keeping its own backend semantics.

## Scope

- [ ] Support initial Kraken order submission for a limited set of order types
- [ ] Validate order input on the server
- [ ] Show user feedback for success and failure
- [ ] Refresh Kraken open orders after submission
- [ ] Expose the minimal fields needed for a trustworthy first release

## Out Of Scope

- Cancel or edit workflows
- Margin, leverage, or advanced Kraken order features
- A unified cross-service ticket

## Technical Areas

- `trader1.kraken`
- authentication and credential handling
- websocket payload updates for Kraken orders
- frontend Kraken order form
- tests for request signing, payload handling, and validation

## Tasks

- [ ] Define the first supported Kraken order request shape
- [ ] Confirm symbol and pair conventions for the UI and backend
- [ ] Implement backend submission flow using Kraken private endpoints
- [ ] Add server-side validation for side, quantity, price, and order type
- [ ] Build a minimal Kraken-specific order form
- [ ] Refresh open orders after successful submission
- [ ] Add focused tests around validation and error mapping

## Acceptance Criteria

- [ ] A user can submit at least one simple Kraken order end-to-end
- [ ] Submission errors are visible and understandable
- [ ] Open orders reflect newly submitted Kraken orders without manual refresh steps
- [ ] Existing Kraken balance and order views still behave correctly
- [ ] Relevant backend tests cover validation and API error handling

## Notes

Recommended initial scope:

- spot orders only
- market and limit orders only
- a small allowed set of trading pairs first

Kraken and IB should not be forced into the same backend shape yet. Shared UI ideas are fine, but service-specific behavior should remain explicit.

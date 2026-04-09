# Phase 14: Hyperliquid Integration

## Status

Planned

## Goal

Add Hyperliquid as another service integration in a way that fits the existing application without prematurely forcing a large architectural rewrite.

## Why Now

Hyperliquid is strategically interesting, but it is best added after the app has clearer service boundaries, trading flows, and operational patterns from IB and Kraken.

## Scope

- [ ] Add a first Hyperliquid service client or namespace in `trader1`
- [ ] Support a narrow first release such as read-only balances, positions, market data, or open orders
- [ ] Integrate the data into the existing dashboard and websocket model where appropriate
- [ ] Decide whether inline integration is sufficient or whether later extraction is justified

## Out Of Scope

- Immediate creation of a separate subproject
- Full feature parity with Kraken and IB in the first pass
- Advanced Hyperliquid trading flows on day one

## Technical Areas

- new backend service namespace
- configuration
- websocket payloads
- frontend service views
- tests around normalization and error handling

## Tasks

- [ ] Research the minimal useful Hyperliquid feature set for the first pass
- [ ] Add configuration and credential handling as needed
- [ ] Implement a clean service module parallel to `trader1.kraken`
- [ ] Normalize returned data enough for dashboard use
- [ ] Add UI views for the selected Hyperliquid data
- [ ] Add tests for parsing, mapping, and error conditions
- [ ] Re-evaluate whether a separate project is justified after the first pass

## Acceptance Criteria

- [ ] Hyperliquid data can be loaded and shown in the app for the selected first-pass scope
- [ ] The integration fits the current codebase without creating unnecessary architectural weight
- [ ] The service can be configured and reasoned about separately from Kraken and IB
- [ ] Tests cover the first-pass mapping and failure behavior

## Notes

Recommended first approach:

- start with a `trader1.hyperliquid` namespace
- follow the same general client and mapping pattern used for `trader1.kraken`
- avoid extracting a separate project until reuse pressure is real

Potential first-pass features:

- balances or positions
- open orders
- market prices
- portfolio value contribution

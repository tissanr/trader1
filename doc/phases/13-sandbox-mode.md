# Phase 13: Sandbox Mode

## Status

Planned

## Goal

Provide a safer development and evaluation mode where trading workflows can be exercised without sending live orders.

## Why Now

After order entry exists across multiple services, a safer testing mode becomes very valuable for development, demos, and experimentation.

## Scope

- [ ] Define what sandbox mode means per service
- [ ] Add feature flags or routing so order flows can operate in non-live mode
- [ ] Make the active mode visible in the UI
- [ ] Prevent confusion between sandbox and live behavior

## Out Of Scope

- A perfect broker-grade simulation engine
- Full market replay
- Strategy backtesting

## Technical Areas

- configuration and feature flags
- backend order routing
- frontend mode indicators
- tests around mode gating

## Tasks

- [ ] Decide whether sandbox mode is simulated, paper-account-backed, or mixed by service
- [ ] Add mode configuration and visibility in the UI
- [ ] Prevent live trading actions when sandbox mode is active
- [ ] Add tests to ensure mode gating behaves correctly
- [ ] Document the limitations of sandbox behavior clearly

## Acceptance Criteria

- [ ] The app has a clear non-live operating mode
- [ ] Users can tell immediately whether they are in sandbox or live mode
- [ ] Trading behavior is gated correctly by mode
- [ ] Limitations are documented so sandbox behavior is not mistaken for real execution quality

## Notes

This phase may differ by service:

- IB may rely on paper accounts
- Kraken may need a simulated or disabled-submit path
- later services may have their own constraints

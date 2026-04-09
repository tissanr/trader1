# Phase 09: Historical Portfolio Tracking

## Status

Planned

## Goal

Store and visualize portfolio history over time so the dashboard can show change, not only current state.

## Why Now

Historical tracking becomes more valuable once the core trading workflows exist. It adds context for performance, drawdowns, and exposure shifts without blocking earlier order-entry work.

## Scope

- [ ] Capture periodic portfolio snapshots for supported services
- [ ] Define a storage strategy for snapshot history
- [ ] Build at least one historical chart or summary view
- [ ] Document retention and snapshot frequency decisions

## Out Of Scope

- Tax reporting
- Realized and unrealized PnL attribution across every edge case
- Tick-level market data storage

## Technical Areas

- backend snapshot jobs
- storage format and retention
- websocket or page-load history delivery
- frontend charting
- tests around snapshot persistence

## Tasks

- [ ] Define the snapshot schema
- [ ] Decide what totals and breakdowns are captured first
- [ ] Implement periodic snapshot persistence
- [ ] Build a first-pass historical view in the UI
- [ ] Add tests for snapshot writing and reading
- [ ] Document operational expectations for local deployments

## Acceptance Criteria

- [ ] The app stores portfolio snapshots on a predictable schedule
- [ ] A user can see at least one historical trend in the UI
- [ ] Snapshot retention and storage behavior are documented
- [ ] The feature works without destabilizing the live dashboard

## Notes

A narrow first release is fine:

- total account value over time
- service-level breakdown over time

Detailed position history can come later if needed.

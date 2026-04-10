# Phase 10: Annual Transaction Report

## Status

Planned

## Goal

Add a dedicated report page that assembles a full year of transactions in historical order across Kraken and Interactive Brokers.

## Why Now

This report depends on the trading workflows and historical data model becoming stable enough to reconstruct activity across both services. It fits after historical portfolio tracking because it needs durable time-based data, not only live dashboard state.

## Scope

- [ ] Add a new page accessible from the dashboard for annual transaction reporting
- [ ] Support selecting or requesting a given calendar year
- [ ] Combine Kraken and IB transactions into one chronological report
- [ ] Show enough transaction detail for review and later export work
- [ ] Document the data sources, known gaps, and ordering rules

## Out Of Scope

- Tax calculation
- Jurisdiction-specific reporting rules
- PnL attribution across every edge case
- CSV, PDF, or third-party export formats in the first pass

## Technical Areas

- backend history loading and normalization
- cross-service transaction ordering
- page routing and page-level UI
- report table rendering
- tests for normalization and ordering

## Tasks

- [ ] Define the common transaction shape for Kraken and IB events
- [ ] Decide which fields are mandatory for the first report view
- [ ] Add backend support for querying one report year
- [ ] Normalize Kraken and IB transactions into one ordered stream
- [ ] Add a dedicated report page reachable from the dashboard
- [ ] Render the transactions in historical order with clear source labeling
- [ ] Add tests for ordering, normalization, and missing-data behavior
- [ ] Document known limits where one broker does not expose enough detail cleanly

## Acceptance Criteria

- [ ] A user can open a dedicated transaction report page from the dashboard
- [ ] A user can request at least one full calendar year of transactions
- [ ] Kraken and IB transactions appear in one chronological list
- [ ] Each row clearly identifies source, instrument, side, quantity, and price when available
- [ ] Ordering and normalization logic are covered by focused tests

## Notes

First-pass row fields should likely include:

- timestamp
- source (`kraken` or `ib`)
- symbol or pair
- side
- order type when available
- quantity
- price
- status
- account or venue identifier when useful

If one source lacks a field, the report should leave it explicit rather than inventing values.

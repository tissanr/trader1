# Phase 10: Instrument Grouping And Exposure Views

## Status

Planned

## Goal

Improve portfolio understanding by grouping holdings and orders into broader exposure categories without trying to solve full cross-market instrument normalization.

## Why Now

As more services and more instruments are added, raw symbols become less useful for portfolio understanding. Grouping provides better reporting and charting while avoiding a much larger and riskier normalization project.

## Scope

- [ ] Introduce a lightweight grouping model for exposures such as `BTC`, `ETH`, `USD`, or similar broad keys
- [ ] Keep raw broker and exchange symbols intact for detailed views
- [ ] Add grouped summaries or charts where they improve understanding
- [ ] Document grouping rules and known limitations

## Out Of Scope

- A universal instrument master
- Perfect mapping across all derivatives, ETFs, ETCs, and synthetic products
- Aggressive assumptions that hide meaningful differences between instruments

## Technical Areas

- backend mapping logic
- portfolio aggregation
- frontend charts and summary tables
- tests around grouping rules

## Tasks

- [ ] Define a narrow grouping model and naming rules
- [ ] Decide which services and instruments participate in the first pass
- [ ] Add grouping metadata to portfolio aggregation where appropriate
- [ ] Update charts or summaries to use grouping keys
- [ ] Add tests for grouping behavior and known exceptions
- [ ] Document where grouping is approximate rather than exact

## Acceptance Criteria

- [ ] The UI can show grouped exposure views without hiding raw instrument identities
- [ ] Grouping improves charts and summaries for common assets
- [ ] The implementation avoids pretending distinct instruments are fully identical
- [ ] Grouping rules are testable and documented

## Notes

Examples:

- keep exact symbol for tables and detailed rows
- derive a grouping key for broader exposure reporting
- allow some instruments to remain ungrouped if confidence is too low

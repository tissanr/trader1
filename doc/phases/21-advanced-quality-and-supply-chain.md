# Phase 21: Advanced Quality And Supply Chain

## Status

Planned

## Goal

Add higher-maturity release engineering checks such as performance budgets, richer UI verification, and stronger software supply-chain guarantees once the basic pipeline is already trusted.

## Why Now

These controls add the most value only after CI and CD are already part of normal development. Before that point they tend to create noise or busywork.

## Scope

- [ ] Add advanced quality checks that protect user experience or release confidence
- [ ] Add stronger supply-chain metadata and artifact integrity checks
- [ ] Add automation that reduces release toil for maintainers

## Out Of Scope

- Foundational CI correctness
- First staging or production deployment path
- Replacing human review with automation

## Technical Areas

- performance and bundle budgets
- browser or regression automation
- SBOM and attestations
- release automation
- compatibility and contract checks

## Tasks

- [ ] Choose which advanced checks have the best signal-to-noise ratio for this repo
- [ ] Add frontend bundle or performance budget checks where useful
- [ ] Add browser smoke or regression automation if the UI warrants it
- [ ] Generate an SBOM and evaluate artifact attestations
- [ ] Add release-note or changelog automation if release cadence justifies it
- [ ] Add compatibility checks around external integration contracts where the cost is justified

## Acceptance Criteria

- [ ] Advanced checks catch meaningful regressions that baseline CI misses
- [ ] Supply-chain metadata is generated consistently for releases
- [ ] Release engineering work is more repeatable and less manual
- [ ] The added automation is maintained because it has clear value

## Notes

- This phase should stay selective. More checks are not better unless they protect real risks in `trader1`.

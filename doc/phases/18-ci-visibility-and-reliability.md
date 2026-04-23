# Phase 18: CI Visibility And Reliability

## Status

Planned

## Goal

Make CI runs easier to trust and easier to debug by preserving useful artifacts, adding scheduled validation, and surfacing vendor drift before it surprises development work.

## Why Now

After the baseline CI and security gates are in place, the next problem is operational friction: failures need to be diagnosable, and slow drift needs to be noticed before it blocks feature work.

## Scope

- [ ] Upload useful CI artifacts on failure or when explicitly needed
- [ ] Add scheduled validation runs
- [ ] Add visibility into `vendor/ib-cl-wrap` drift
- [ ] Improve CI result readability for maintainers

## Out Of Scope

- Deployments
- Browser automation
- Performance regression budgets

## Technical Areas

- GitHub Actions artifacts
- scheduled workflows
- vendor drift reporting
- CI summaries and logs

## Tasks

- [ ] Decide which artifacts are worth storing from failed or manual runs
- [ ] Upload frontend build output or logs where they help debugging
- [ ] Add a nightly or periodic scheduled CI run
- [ ] Add a workflow or report for `vendor/ib-cl-wrap` drift
- [ ] Improve step naming and workflow summaries for common failure cases
- [ ] Verify artifact retention and scheduled workflow behavior

## Acceptance Criteria

- [ ] CI failures produce enough output to debug common build and test issues
- [ ] Scheduled validation catches drift even when no PR is open
- [ ] Wrapper drift is visible before it becomes an emergency integration task
- [ ] Maintainers can tell quickly which part of CI failed and why

## Notes

- This phase is about maintainability, not about adding more gates for their own sake.
- Keep artifact volume modest so the workflow stays practical and affordable.

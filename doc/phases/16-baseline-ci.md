# Phase 16: Baseline CI

## Status

Planned

## Goal

Add a reliable baseline CI workflow that proves `trader1` still installs, builds, and passes automated tests on every pull request and protected-branch push.

## Why Now

The repo now has enough backend, frontend, and vendor-wrapper integration work that manual verification is no longer enough. A failing PR should be blocked before it reaches the main branch.

## Scope

- [ ] Replace the current minimal GitHub Actions workflow with a CI workflow that matches the real project commands
- [ ] Run backend tests and frontend compilation in CI
- [ ] Ensure CI checks out `vendor/ib-cl-wrap` correctly
- [ ] Add baseline workflow hardening such as explicit permissions and concurrency cancellation

## Out Of Scope

- Security scanning and dependency policy enforcement
- Automated deployments
- Browser or end-to-end UI automation

## Technical Areas

- GitHub Actions workflow files
- backend test command
- frontend build command
- submodule checkout
- CI caching and execution environment

## Tasks

- [ ] Audit the current workflow against the real repo commands
- [ ] Define the supported CI runtime versions for Java and Node
- [ ] Add a workflow that checks out submodules recursively
- [ ] Run `npm ci`, `lein deps`, `npx shadow-cljs compile app`, and `lein test`
- [ ] Add dependency caches where they reduce CI time without hiding failures
- [ ] Add concurrency cancellation so stale runs do not pile up
- [ ] Verify the workflow on pull requests and protected-branch pushes

## Acceptance Criteria

- [ ] Every pull request gets a consistent backend and frontend validation run
- [ ] CI uses the same core commands documented for local development
- [ ] Submodule-dependent builds do not fail because `vendor/ib-cl-wrap` was skipped
- [ ] Stale workflow runs are cancelled automatically when superseded

## Notes

- This phase should stay narrow: correctness first, speed second.
- CI should validate the repo as it exists today, not an idealized future packaging model.

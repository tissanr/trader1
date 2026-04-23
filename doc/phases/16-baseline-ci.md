# Phase 16: Baseline CI

## Status

In progress

## Goal

Add a reliable baseline CI workflow that proves `trader1` still installs, builds, and passes automated tests on every pull request and protected-branch push.

## Why Now

The repo now has enough backend, frontend, and vendor-wrapper integration work that manual verification is no longer enough. A failing PR should be blocked before it reaches the main branch.

## Scope

- [x] Replace the current minimal GitHub Actions workflow with a CI workflow that matches the real project commands
- [x] Run backend tests and frontend compilation in CI
- [x] Ensure CI checks out `vendor/ib-cl-wrap` correctly
- [x] Add baseline workflow hardening such as explicit permissions and concurrency cancellation

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

- [x] Audit the current workflow against the real repo commands
- [x] Define the supported CI runtime versions for Java and Node
- [x] Add a workflow that checks out submodules recursively
- [x] Run `npm ci`, `lein deps`, `npx shadow-cljs compile app`, and `lein test`
- [x] Add dependency caches where they reduce CI time without hiding failures
- [x] Add concurrency cancellation so stale runs do not pile up
- [ ] Verify the workflow on pull requests and protected-branch pushes

## Acceptance Criteria

- [ ] Every pull request gets a consistent backend and frontend validation run
- [x] CI uses the same core commands documented for local development
- [x] Submodule-dependent builds do not fail because `vendor/ib-cl-wrap` was skipped
- [x] Stale workflow runs are cancelled automatically when superseded

## Notes

- This phase should stay narrow: correctness first, speed second.
- CI should validate the repo as it exists today, not an idealized future packaging model.
- Local validation is complete, but the phase remains in progress until the new workflow has run successfully on GitHub for both a pull request and a `main` push.

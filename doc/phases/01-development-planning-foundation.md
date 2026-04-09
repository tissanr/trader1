# Phase 01: Development Planning Foundation

## Status

Done

## Goal

Create a lightweight development layer for `trader1` so future work is planned, implemented, and tested in clearly defined phases.

## Why Now

The codebase already has a solid technical structure, but it does not yet have a consistent planning structure. Adding one now will make future feature work easier to sequence, review, and validate without losing context.

## Scope

- [x] Add a roadmap document that shows current, next, and later phases
- [x] Add a reusable phase template for future work
- [x] Define basic rules for what a phase should contain
- [x] Create an initial list of near-term candidate phases for this repo
- [x] Link the planning docs from the main project documentation

## Out Of Scope

- Implementing feature work from later phases
- Replacing GitHub issues or commit history
- Building a custom planning tool or automation

## Technical Areas

- project documentation
- development workflow

## Tasks

- [x] Create `doc/roadmap.md`
- [x] Create `doc/phases/README.md`
- [x] Create `doc/phases/phase-template.md`
- [x] Create this initial phase definition
- [x] Add a short pointer in `README.md`

## Acceptance Criteria

- [x] A contributor can find the roadmap quickly
- [x] A contributor can start a new phase from a standard template
- [x] The roadmap makes the next planned phase visible
- [x] The new documentation fits the current repo structure cleanly

## Notes

Suggested next phases after this foundation:

- Phase 02 could focus on settings and runtime configuration so more behavior moves out of hardcoded values.
- Phase 03 could focus on IB reliability, especially reconnect and clearer disconnected-state behavior.
- Phase 04 could focus on historical portfolio tracking and charts once the real-time path feels stable.

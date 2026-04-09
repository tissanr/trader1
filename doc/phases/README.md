# Development Phases

This folder contains the project phases for `trader1`.

Each phase should be a small, vertical slice that can be:

- planned clearly
- implemented end-to-end
- tested with explicit acceptance criteria
- marked done without ambiguity

## Naming

Use numbered files so the order stays obvious:

- `01-some-phase-name.md`
- `02-another-phase-name.md`

## Guidelines

- Prefer user-visible or operator-visible outcomes over technical buckets.
- Keep scope small enough that one phase can be completed and validated cleanly.
- Add both `Scope` and `Out of scope` sections to prevent drift.
- Include acceptance checks that match this repo: backend behavior, WebSocket payloads, frontend state, and UI when relevant.
- Move follow-up ideas into a later phase instead of stretching the current one.

Start new phases from [`phase-template.md`](./phase-template.md).

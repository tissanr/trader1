# Phase 08: Safety And Audit Layer

## Status

Planned

## Goal

Add safety controls and trading audit visibility so order entry is easier to trust in day-to-day use.

## Why Now

Once the app can submit orders, operational safety becomes a first-class concern. This phase adds guardrails and traceability before expanding into more analytics and more services.

## Scope

- [ ] Add confirmation or friction for higher-risk trading actions
- [ ] Record a user-visible audit trail for submitted and canceled orders
- [ ] Add feature flags or controls for enabling live trading flows
- [ ] Improve observability around trading actions and errors

## Out Of Scope

- Full regulatory or compliance tooling
- Long-term archival infrastructure
- Multi-user permission models beyond the essentials

## Technical Areas

- backend order-action logging
- frontend confirmation and audit views
- configuration and feature flags
- tests around logging and guardrails

## Tasks

- [ ] Define the minimum audit event model
- [ ] Decide where audit events live initially
- [ ] Add confirmation behavior for risky actions
- [ ] Add a basic audit/history view in the UI
- [ ] Add feature flags for enabling or disabling live order submission
- [ ] Add tests for logging and guardrail behavior

## Acceptance Criteria

- [ ] Trading actions produce a visible audit record
- [ ] Higher-risk actions require clear user intent
- [ ] Live trading can be gated by configuration
- [ ] Operators can inspect recent order activity without reading server logs

## Notes

Useful audit fields likely include:

- timestamp
- service
- account or context
- action type
- request summary
- result summary
- error message if applicable

# Phase 07: Unified Order Ticket

## Status

Planned

## Goal

Provide a single order ticket experience that can drive both IB and Kraken trading while still respecting service-specific rules.

## Why Now

Once both IB and Kraken support basic trading, the UI should stop duplicating order forms. A unified ticket can reduce cognitive load and create a cleaner operator workflow.

## Scope

- [ ] Design a shared order ticket UI with a clear service selector
- [ ] Support service-specific validation and field rules
- [ ] Reuse common form components and feedback patterns
- [ ] Make it obvious which service and account the order will target

## Out Of Scope

- Full backend unification of all order semantics
- Advanced order types across all services
- Portfolio-level smart routing

## Technical Areas

- frontend order ticket UI
- frontend validation state
- backend request routing
- shared order request contracts where practical
- tests for service-specific validation behavior

## Tasks

- [ ] Identify the true common denominator between IB and Kraken order entry
- [ ] Design the ticket around shared fields plus service-specific sections
- [ ] Define a backend contract that routes to the correct service safely
- [ ] Preserve service-specific validation and error messages
- [ ] Add tests for the ticket state model and routing behavior
- [ ] Validate that the unified ticket does not hide important service differences

## Acceptance Criteria

- [ ] A user can place supported IB and Kraken orders from one consistent ticket flow
- [ ] The UI clearly communicates which service rules apply
- [ ] Service-specific errors are still understandable
- [ ] The ticket reduces duplicated UI without introducing ambiguous trading behavior

## Notes

This phase should unify the operator experience, not flatten the meaning of every backend service.

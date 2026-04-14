# Phase 13: Connection Health Dashboard

## Status

Planned

## Goal

Make service health visible in one place so the operator can quickly understand whether Kraken, IB, and later services are connected and updating normally.

## Why Now

As the app grows into a multi-service trading surface, connection issues become a bigger operational problem. A dedicated health view can reduce guesswork and make alerts easier to interpret.

## Scope

- [ ] Show service-level health and freshness information
- [ ] Distinguish healthy, degraded, and disconnected states
- [ ] Surface recent service errors in a compact operational view
- [ ] Reuse health data already present where possible

## Out Of Scope

- Full infrastructure monitoring
- External uptime integrations
- Automatic service recovery orchestration

## Technical Areas

- backend health-state tracking
- websocket payloads for service status
- frontend dashboard components
- tests around state transitions

## Tasks

- [ ] Define the health model for each service
- [ ] Track freshness and recent error state in the backend
- [ ] Add a frontend health view or panel
- [ ] Connect existing connection and error events into the health model
- [ ] Add tests for state transitions such as healthy to degraded to disconnected

## Acceptance Criteria

- [ ] A user can quickly tell whether each service is healthy
- [ ] The health view reflects stale data and disconnections clearly
- [ ] Existing error handling feeds into the health model coherently
- [ ] Tests cover key service-state transitions

## Notes

Potential health inputs:

- websocket connected or disconnected
- last successful Kraken poll
- last successful IB snapshot
- recent error count

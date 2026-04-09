# Phase 11: Alerts And Thresholds

## Status

Planned

## Goal

Notify the operator when important conditions occur, such as price movements, connection failures, or threshold breaches.

## Why Now

Alerts become much more useful after the app can trade and track history. At that point the dashboard is not just for observation but for operational awareness.

## Scope

- [ ] Define an initial set of alertable conditions
- [ ] Add a configurable threshold model
- [ ] Surface active or recent alerts clearly in the UI
- [ ] Keep the first implementation small and operationally useful

## Out Of Scope

- Full notification delivery to every external channel
- Complex strategy automation
- Highly customizable rules engines

## Technical Areas

- backend alert evaluation
- configuration
- frontend alert display
- tests around threshold evaluation

## Tasks

- [ ] Decide which alert types matter most for the first release
- [ ] Define how thresholds are configured
- [ ] Build backend evaluation for selected conditions
- [ ] Add a UI area for active and recent alerts
- [ ] Add tests for threshold and alert behavior

## Acceptance Criteria

- [ ] The app can raise at least a few meaningful alerts
- [ ] Threshold configuration is understandable
- [ ] Alerts are visible without forcing the user to inspect logs
- [ ] False confidence is avoided where data is stale or incomplete

## Notes

Good first candidates:

- service disconnected
- IB timeout repeated
- portfolio value drops below threshold
- asset exposure exceeds threshold

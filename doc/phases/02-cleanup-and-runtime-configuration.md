# Phase 02: Cleanup And Runtime Configuration

## Status

Planned

## Goal

Clean up the current runtime and configuration model so future broker and exchange features can be added with less hardcoded behavior and less uncertainty about where settings belong.

## Why Now

This phase lays the foundation for everything that follows. Before adding order entry, more service integrations, and richer dashboards, the app should have clearer boundaries around settings, service toggles, polling intervals, and connection parameters.

## Scope

- [ ] Review hardcoded runtime values in backend code and identify which should move into config
- [ ] Expand the settings model to support service-specific configuration cleanly
- [ ] Add enable and disable flags for major services where practical
- [ ] Clarify which settings are local-only, required, optional, or planned
- [ ] Improve documentation so a contributor can understand runtime setup without reading implementation details

## Out Of Scope

- Building a full settings UI
- Large refactors unrelated to configuration boundaries
- Implementing new trading functionality

## Technical Areas

- backend configuration loading
- `trader1.specs`
- documentation
- test coverage for configuration parsing and defaults

## Tasks

- [ ] Audit current hardcoded values in `trader1.core`, `trader1.web`, `trader1.kraken`, and related namespaces
- [ ] Define a clearer config shape for runtime services
- [ ] Add or improve spec coverage for settings and defaults
- [ ] Move selected hardcoded settings into `config/settings.edn` or environment-backed config
- [ ] Document the configuration model in the README and example config files
- [ ] Confirm the app still starts with sensible defaults

## Acceptance Criteria

- [ ] Important runtime values are no longer scattered through the code without explanation
- [ ] Service-related settings have a documented home
- [ ] The app can still boot with a predictable local setup
- [ ] Automated checks cover parsing or validation for the updated settings structure
- [ ] This phase makes future service integrations easier rather than adding configuration sprawl

## Notes

Candidates for cleanup:

- IB host, port, and timeout behavior
- polling intervals per service
- service enable and disable flags
- settings file structure for future Kraken, IB, and Hyperliquid options

This phase should favor clarity over maximum abstraction.

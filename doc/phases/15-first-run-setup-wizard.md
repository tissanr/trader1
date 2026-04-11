# Phase 15: First-Run Setup Wizard

## Status

Planned

## Goal

Eliminate all manual file-editing steps required before a new user can log in for the first time. When the app is unconfigured, it should redirect the user to a setup page in the browser where they can create their admin account and optionally enter Kraken API credentials. After submitting the form, the user is taken to the login page and the app behaves like a normal web application from that point on.

## Why Now

After the core IB trading workflow lands in Phase 04, the app is in a state worth sharing with other users. The current first-run experience requires opening a Clojure REPL to generate a bcrypt hash, manually editing `config/auth.edn`, and manually editing `credentials/kraken.key`. That level of friction is a barrier for anyone who is not already a Clojure developer. Fixing this before the feature set grows further means every phase that follows is easier to demo and distribute.

## Scope

- [ ] Detect when setup is needed: `config/auth.edn` is missing or still contains the placeholder hash `REPLACE_WITH_BCRYPT_HASH`
- [ ] Auto-redirect to `/setup` from any route when the app is unconfigured
- [ ] `GET /setup` renders a setup form with username, password, confirm-password, and optional Kraken API key and secret fields
- [ ] `POST /setup` validates input, writes `config/auth.edn`, optionally writes `credentials/kraken.key`, and redirects to `/login`
- [ ] Once setup is complete, `/setup` redirects to `/login` and cannot be revisited
- [ ] Tests cover the new auth helpers and the setup route

## Out Of Scope

- Multi-user management through the UI
- Changing passwords after initial setup (separate settings concern)
- Docker or container packaging
- Automating the `lib/ibapi.jar` download (not possible due to IB licensing)
- HTTPS or TLS termination

## Technical Areas

- `trader1.auth` — detection and config writing
- `trader1.security` — credential file writing
- `trader1.web` — setup page, handler, routes, redirect guards
- tests

## Tasks

- [ ] Add `needs-setup?` to `trader1.auth`: returns true if `config/auth.edn` is missing or contains the placeholder hash
- [ ] Add `write-config!` to `trader1.auth`: hashes the password and writes a valid `config/auth.edn`
- [ ] Add `write-credentials!` to `trader1.security`: writes `credentials/kraken.key` from plaintext key and secret
- [ ] Add `setup-page` to `trader1.web`: hiccup HTML with a username/password form and an optional Kraken credentials section
- [ ] Add `setup-handler` to `trader1.web`: validates input, writes config files, redirects to `/login`
- [ ] Add `GET /setup` and `POST /setup` routes
- [ ] Update existing route guards: redirect to `/setup` when `needs-setup?` returns true, including `GET /`, `GET /login`, `GET /dashboard`, `GET /settings`, and the WebSocket endpoint
- [ ] Add tests for `needs-setup?` and `write-config!` in `trader1.auth-test`
- [ ] Verify manually: delete `config/auth.edn`, start server, visit `/`, complete setup form, log in

## Acceptance Criteria

- [ ] A brand-new checkout with no `config/auth.edn` redirects every route to `/setup`
- [ ] Completing the setup form allows login with the chosen credentials
- [ ] After setup, `/setup` redirects to `/login` and does not overwrite the config
- [ ] Submitting mismatched passwords shows an inline error without losing form state
- [ ] The Kraken key fields are optional: skipping them does not prevent setup from completing
- [ ] Existing dashboard behavior does not regress
- [ ] Relevant automated tests pass

## Notes

- The placeholder string `"REPLACE_WITH_BCRYPT_HASH"` in `config/auth.edn.example` is the detection trigger. Do not change that string without updating the detection logic.
- `write-credentials!` should be a no-op if both Kraken fields are left blank, not write an empty file.
- The setup page does not need to be styled heavily; clarity matters more than visual polish at this stage.
- A follow-up could add a `/setup/change-password` flow, but that is explicitly out of scope here.

# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
lein deps          # Install dependencies
lein test          # Run all tests
lein test trader1.core-test   # Run a single test namespace
lein run           # Start the web server (default port 3000)
lein uberjar       # Build standalone JAR
PORT=8080 lein run # Run on a custom port
```

## Architecture

This is a cryptocurrency trading dashboard that queries Kraken exchange APIs and presents data via a web UI with real-time WebSocket updates.

### Namespace overview

- **trader1.core** — Low-level HTTP wrappers (`get-path`, `post-form-path`, `throw-if-err`) and the `-main` entry point that starts the HTTP server on `PORT` (default 3000).
- **trader1.kraken** — Kraken API v0 client. Public endpoints: ticker, assets, asset pairs, server time. Private endpoints: account balance, open orders. Uses HMAC-SHA512 signing with nonce.
- **trader1.security** — Reads `credentials/kraken.key` (line 1 = API key, line 2 = base64-encoded secret).
- **trader1.auth** — Loads users from `config/auth.edn`, verifies passwords using bcrypt+sha512 via `buddy-hashers`. Use `hash-password` to generate hashes for new users.
- **trader1.web** — HTTP server (http-kit), Compojure routing, Hiccup HTML, session-protected dashboard, WebSocket endpoint, and a background broadcaster thread that pushes Kraken data to connected clients (ticker every 5s, orders every 15s, balance every 30s).

### Data flow

```
Browser → login → session cookie
       ↓
  Dashboard ← WebSocket ← background broadcaster ← Kraken API
```

### Configuration files

- `credentials/kraken.key` — Kraken API credentials (gitignored; see `credentials/kraken.key.example`)
- `config/auth.edn` — Web UI user accounts (gitignored; see `config/auth.edn.example`)

### Key dependencies

| Library | Role |
|---------|------|
| `http-kit` | Async HTTP server + WebSocket |
| `compojure` | Route definitions |
| `hiccup` | HTML generation |
| `ring-defaults` | Session, CSRF, middleware |
| `buddy-hashers` | bcrypt+sha512 password hashing |
| `clj-http` + `cheshire` | Outbound HTTP + JSON |

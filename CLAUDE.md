# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

### Backend (Clojure / Leiningen)

```bash
lein deps          # Install Clojure dependencies
lein test          # Run all tests
lein test trader1.core-test   # Run a single test namespace
lein run           # Start the web server (default port 3000)
lein uberjar       # Build standalone JAR
PORT=8080 lein run # Run on a custom port
```

### Frontend (ClojureScript / shadow-cljs)

```bash
npm install                  # Install JS dependencies (react, react-dom, recharts)
npx shadow-cljs compile app  # One-shot production build → resources/public/js/main.js
npx shadow-cljs watch app    # Dev build with hot-reload (keep running in background)
```

### Full development setup (two terminals)

```bash
# Terminal 1 — CLJS compiler with hot-reload
npx shadow-cljs watch app

# Terminal 2 — Clojure server
lein run
```

### Vendor (ib-cl-wrap submodule)

```bash
make vendor-status  # Show commits ahead/behind upstream ib-cl-wrap
make vendor-update  # Fetch latest from local ib-cl-wrap clone and stage
```

## Architecture

This is a cryptocurrency + Interactive Brokers trading dashboard. The Clojure backend queries Kraken and IB APIs and pushes live data to the browser via WebSocket. The frontend is a ClojureScript/Reagent SPA with Recharts charts.

### Data flow

```
Browser → login → session cookie
       ↓
  React SPA ← WebSocket ← background broadcaster ← Kraken API
                                                  ← IB API (ib-cl-wrap)
```

### Backend namespace overview

- **trader1.core** — Low-level HTTP wrappers (`get-path`, `post-form-path`, `throw-if-err`) and the `-main` entry point that starts the HTTP server on `PORT` (default 3000).
- **trader1.kraken** — Kraken API v0 client. Public endpoints: ticker, assets, asset pairs, server time. Private endpoints: account balance, open orders. Uses HMAC-SHA512 signing with nonce.
- **trader1.security** — Reads `credentials/kraken.key` (line 1 = API key, line 2 = base64-encoded secret).
- **trader1.auth** — Loads users from `config/auth.edn`, verifies passwords using bcrypt+sha512 via `buddy-hashers`. Use `hash-password` to generate hashes for new users.
- **trader1.web** — HTTP server (http-kit), Compojure routing, session-protected routes, WebSocket endpoint, and background broadcaster threads. Dashboard page is now a minimal HTML shell (`<div id="app">`) that loads the compiled CLJS bundle.

### Frontend namespace overview (`src/trader1/frontend/`)

| Namespace | Role |
|-----------|------|
| `trader1.frontend.core` | Entry point — `init!` mounts the React app and starts the WebSocket; `reload!` is called by shadow-cljs hot-reload |
| `trader1.frontend.state` | Single `app-state` reagent atom; WebSocket connection + reconnect logic; `dispatch!` updates state from incoming JSON messages |
| `trader1.frontend.components.dashboard` | Top-level layout: header, Kraken grid, portfolio charts, IB grid, debug panel |
| `trader1.frontend.components.kraken` | Kraken portfolio value, ticker, balance list, open orders |
| `trader1.frontend.components.ib` | IB portfolio balance, positions table, orders table, debug button panel |
| `trader1.frontend.components.charts` | Recharts donut pie charts: asset distribution in USD, Kraken vs IB portfolio breakdown |

### WebSocket message types (server → client)

| `type` | `data` shape |
|--------|-------------|
| `connection` | `{status: "connected"\|"disconnected"}` |
| `kraken-balance` | `{XXBT: "0.5", ZUSD: "1000.0", …}` |
| `kraken-orders` | `{open: {txid → order}}` |
| `kraken-ticker` | `{pairName → {c, a, b}}` |
| `kraken-portfolio-value` | `{total-usd: "12345.67"}` |
| `portfolio-balance` | `{value: "…", currency: "USD"}` |
| `positions` | `[{symbol, sec-type, currency, position, avg-cost}]` |
| `orders` | `[{symbol, action, order-type, quantity, limit-price, status, filled, remaining}]` |
| `cell-error` | `{cell: "balance"\|"positions"\|"orders", message: null\|string}` |
| `ib-error` | `{message: string}` |

### Configuration files

- `credentials/kraken.key` — Kraken API credentials (gitignored; see `credentials/kraken.key.example`)
- `config/auth.edn` — Web UI user accounts (gitignored; see `config/auth.edn.example`)
- `config/settings.edn` — Polling intervals (gitignored)

### Build outputs (gitignored)

- `resources/public/js/` — Compiled CLJS bundle (`main.js` + runtime files)
- `node_modules/` — npm packages
- `.shadow-cljs/` — shadow-cljs build cache

### Key dependencies

| Library | Role |
|---------|------|
| `http-kit` | Async HTTP server + WebSocket |
| `compojure` | Route definitions |
| `hiccup` | HTML for login/settings pages |
| `ring-defaults` | Session, CSRF, middleware |
| `buddy-hashers` | bcrypt+sha512 password hashing |
| `clj-http` + `cheshire` | Outbound HTTP + JSON |
| `reagent` | React wrapper for ClojureScript |
| `recharts` (npm) | Pie/bar charts for portfolio visualisation |
| `shadow-cljs` | ClojureScript build tool with npm integration |
| `vendor/ib-cl-wrap` | Interactive Brokers TWS API wrapper (git submodule) |

# trader1

A Clojure cryptocurrency trading dashboard that queries Kraken exchange APIs and presents data via a web UI with real-time WebSocket updates.

## Setup

### Prerequisites

- Java 8+
- [Leiningen](https://leiningen.org/)

### Install dependencies

```bash
lein deps
```

### Credentials

Private Kraken API endpoints (e.g. account balance) require API keys.

1. Copy the example file:
   ```bash
   cp credentials/kraken.key.example credentials/kraken.key
   ```
2. Edit `credentials/kraken.key` — put your API key on line 1 and your base64-encoded secret on line 2:
   ```
   your-api-key-here
   your-base64-secret-here
   ```
3. The file is gitignored and will not be committed.

Public endpoints (ticker, symbols, server time) work without credentials.

### Web UI users

1. Copy the example file:
   ```bash
   cp config/auth.edn.example config/auth.edn
   ```
2. Generate a password hash in the REPL:
   ```clojure
   (require '[trader1.auth :as auth])
   (auth/hash-password "your-password")
   ```
3. Paste the hash into `config/auth.edn`.

---

## Running

```bash
lein run            # Start on port 3000
PORT=8080 lein run  # Start on a custom port
```

Open `http://localhost:3000` and log in with the credentials from `config/auth.edn`.

---

## Namespaces

### `trader1.core`

Low-level HTTP utilities shared by all exchange namespaces.

| Function | Signature | Description |
|---|---|---|
| `get-path` | `[url path]` | HTTP GET — concatenates `url` and `path`, returns parsed JSON response |
| `post-form-path` | `[url form-params headers]` | HTTP POST with `application/x-www-form-urlencoded` body (used for Kraken private endpoints) |
| `throw-if-err` | `[reply]` | Throws an `Exception` if the response map contains a non-empty `:error` field |

---

### `trader1.security`

Reads Kraken API credentials from disk.

| Function | Signature | Description |
|---|---|---|
| `read-in-security-pair` | `[]` | Reads `credentials/kraken.key` and returns `{:key "..." :secret "..."}` |

The credentials file path is defined as `credentials-file` and defaults to `"credentials/kraken.key"` (relative to the project root).

---

### `trader1.auth`

Web UI user management.

| Function | Signature | Description |
|---|---|---|
| `load-users` | `[]` | Reads and parses `config/auth.edn` |
| `find-user` | `[username]` | Returns the user map for the given username, or `nil` |
| `authenticate` | `[username password]` | Returns the user map if credentials are valid, else `nil` |
| `hash-password` | `[plaintext]` | Produces a bcrypt+sha512 hash suitable for `config/auth.edn` |

---

### `trader1.kraken`

Kraken exchange API client. Base URL: `https://api.kraken.com/0/`.

#### Public endpoints (no credentials required)

| Function | Signature | Description |
|---|---|---|
| `request-server-time` | `[]` | Returns the Kraken server time. Useful for connectivity checks. |
| `request-symbols` | `[]` | Returns a map of all tradable assets with metadata (alt name, asset class, decimal precision). |
| `request-symbol-pairs` | `[]` | Returns a map of all tradable asset pairs (e.g. `XBTZUSD`). |
| `request-ticker` | `[asset-pairs]` | Returns ticker data for the given pairs (e.g. `["XBTUSD"]`). |

##### Ticker response fields

Each asset pair in the result map contains:

| Key | Description |
|---|---|
| `:a` | Ask `[price, whole-lot-volume, lot-volume]` |
| `:b` | Bid `[price, whole-lot-volume, lot-volume]` |
| `:c` | Last trade closed `[price, lot-volume]` |
| `:v` | Volume `[today, last-24h]` |
| `:p` | Volume-weighted average price `[today, last-24h]` |
| `:t` | Number of trades `[today, last-24h]` |
| `:l` | Low `[today, last-24h]` |
| `:h` | High `[today, last-24h]` |
| `:o` | Today's opening price |

#### Private endpoints (credentials required)

| Function | Signature | Description |
|---|---|---|
| `request-balance` | `[]` | Returns account balances for all assets as a map of `asset → balance-string`. |
| `request-open-orders` | `[]` | Returns open orders as `{:open {"<txid>" {...}}}`. |

##### Authentication

Private requests are signed using Kraken's HMAC-SHA512 scheme:
1. A nonce (current time in milliseconds) is generated.
2. The POST body is URL-encoded: `nonce=<value>`.
3. The signature is `base64(HMAC-SHA512(base64_decoded_secret, path_bytes + SHA256(nonce + post_data)))`.
4. `API-Key` and `API-Sign` headers are added to the request.

---

### `trader1.web`

HTTP server, routing, HTML templates, WebSocket endpoint, and background data broadcaster.

- Login/logout with session cookie and CSRF protection
- Dashboard served only to authenticated users
- WebSocket pushes ticker every 5s, open orders every 15s, balance every 30s

---

## Usage examples

```clojure
(require '[trader1.kraken :as kraken])

;; Check Kraken server time
(kraken/request-server-time)

;; Get BTC/USD ticker
(kraken/request-ticker ["XBTUSD"])
;; => {:XXBTZUSD {:c ["63821.00000" "0.02648261"], :a [...], :b [...], ...}}

;; Get all available asset pairs
(kraken/request-symbol-pairs)

;; Get account balance (requires credentials/kraken.key)
(kraken/request-balance)
;; => {"XXBT" "0.5000000", "ZUSD" "1234.56", ...}

;; Get open orders (requires credentials/kraken.key)
(kraken/request-open-orders)
```

---

## Running tests

```bash
lein test
```

All tests use mocks — no live API calls or credential files are required.

| Test namespace | Covers |
|---|---|
| `trader1.core-test` | `throw-if-err`, `get-path`, `post-form-path` |
| `trader1.security-test` | Credential file parsing (uses test fixture) |
| `trader1.kraken-test` | All public and private Kraken endpoints (mocked) |
| `trader1.auth-test` | `load-users`, `find-user`, `authenticate`, `hash-password` |
| `trader1.web-test` | HTML templates, route handlers, WebSocket broadcast |

---

## Dependencies

| Library | Purpose |
|---|---|
| `http-kit` | Async HTTP server + WebSocket |
| `compojure` | Route definitions |
| `hiccup` | HTML generation |
| `ring/ring-defaults` | Session, CSRF, middleware |
| `buddy-hashers` | bcrypt+sha512 password hashing |
| `clj-http` | Outbound HTTP client |
| `cheshire` | JSON parsing |
| `digest` | SHA hashing (Kraken signing) |
| `org.clojure/data.codec` | Base64 encoding |

---

## License

Copyright © 2018 Stephan Reiter
Distributed under the Eclipse Public License, version 1.0 or later.

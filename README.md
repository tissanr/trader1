# trader1

A Clojure library for querying cryptocurrency exchange APIs (Kraken, Bitfinex).

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

---

## Namespaces

### `trader1.core`

Low-level HTTP utilities shared by all exchange namespaces.

| Function | Signature | Description |
|---|---|---|
| `get-path` | `[url path]` | HTTP GET — concatenates `url` and `path`, returns parsed JSON response |
| `get-csv` | `[url path]` | HTTP GET — returns response as CSV |
| `post-path` | `[url post-data header]` | HTTP POST with a JSON body |
| `post-form-path` | `[url form-params headers]` | HTTP POST with `application/x-www-form-urlencoded` body (used for Kraken private endpoints) |
| `throw-if-err` | `[reply]` | Throws an `Exception` if the response map contains a non-empty `:error` field |
| `filter-by-symbol` | `[symbol structure]` | Filters a map of exchange entries, keeping keys that match `symbol` (case-insensitive) |
| `get-number-of-threads` | `[]` | Returns the number of available processor threads |

---

### `trader1.security`

Reads Kraken API credentials from disk.

| Function | Signature | Description |
|---|---|---|
| `read-in-security-pair` | `[]` | Reads `credentials/kraken.key` and returns `{:key "..." :secret "..."}` |

The credentials file path is defined as `credentials-file` and defaults to `"credentials/kraken.key"` (relative to the project root).

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

##### Authentication

Private requests are signed using Kraken's HMAC-SHA512 scheme:
1. A nonce (current time in milliseconds) is generated.
2. The POST body is URL-encoded: `nonce=<value>`.
3. The signature is `base64(HMAC-SHA512(base64_decoded_secret, path_bytes + SHA256(nonce + post_data)))`.
4. `API-Key` and `API-Sign` headers are added to the request.

---

### `trader1.bitfinex`

Bitfinex v1 API client. Base URL: `https://api.bitfinex.com/v1/`.

| Function | Signature | Description |
|---|---|---|
| `request-symbols-bitfinex` | `[]` | Returns a list of all supported trading pair symbols (e.g. `["btcusd" "ethusd" ...]`). |
| `request-course-bitfinex` | `[course]` | Returns the ticker for the given symbol string (e.g. `"btcusd"`). |
| `get-btc-usd-bitfinex` | `[]` | Convenience wrapper — returns the current BTC/USD ticker from Bitfinex. |

---

## Usage examples

```clojure
(require '[trader1.kraken :as kraken])
(require '[trader1.bitfinex :as bitfinex])

;; Check Kraken server time
(kraken/request-server-time)

;; Get BTC/USD ticker from Kraken
(kraken/request-ticker ["XBTUSD"])
;; => {:XXBTZUSD {:c ["63821.00000" "0.02648261"], :a [...], :b [...], ...}}

;; Get all available asset pairs
(kraken/request-symbol-pairs)

;; Get account balance (requires credentials/kraken.key)
(kraken/request-balance)
;; => {:XXBT "0.5000000", :ZUSD "1234.56", ...}

;; Get BTC/USD ticker from Bitfinex
(bitfinex/get-btc-usd-bitfinex)
;; => {:bid "63800.0", :ask "63802.0", :last_price "63801.0", ...}
```

---

## Running tests

```bash
lein test
```

The test suite covers:
- `trader1.core-test` — `throw-if-err` error handling
- `trader1.security-test` — credential file reading (requires `credentials/kraken.key`)
- `trader1.kraken-test` — live public endpoint calls (server time, symbols, asset pairs, ticker)

---

## Dependencies

| Library | Purpose |
|---|---|
| `clj-http` | HTTP client |
| `cheshire` | JSON parsing |
| `clj-time` | Date/time utilities |
| `digest` | SHA hashing |
| `org.clojure/data.codec` | Base64 encoding |

---

## License

Copyright © 2018 Stephan Reiter
Distributed under the Eclipse Public License, version 1.0 or later.

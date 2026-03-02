# trader1

Clojure Dashboard fuer Interactive Brokers (TWS/IB Gateway) mit WebSocket-UI.

## Setup

### Voraussetzungen

- Java 8+
- Leiningen
- TWS oder IB Gateway mit aktivierter API
- `ibapi.jar` unter `lib/ibapi.jar`

### Dependency

`ib-cl-wrap` ist als gepinnte Git-Dependency via Submodule eingebunden:

- Repo: `https://github.com/reiterstephan-tech/ib-cl-wrap`
- Commit: `a559b1648a969334554662d048b4c6e586c20a0d`

Submodule initialisieren/aktualisieren:

```bash
git submodule update --init --recursive
```

### IB API Ports

Typische Ports:

- TWS Paper: `7497`
- TWS Live: `7496`
- Gateway Paper: `4002`
- Gateway Live: `4001`

Per ENV konfigurierbar:

- `IB_HOST` (default `127.0.0.1`)
- `IB_PORT` (default `7497`)

Wichtig: Verbindung wird mit `client-id = 0` aufgebaut, damit auch manuelle TWS Orders im Open-Orders-Snapshot sichtbar sind.

### Web UI Nutzer

1. Beispiel kopieren:
   ```bash
   cp config/auth.edn.example config/auth.edn
   ```
2. Passwort-Hash in REPL erzeugen:
   ```clojure
   (require '[trader1.auth :as auth])
   (auth/hash-password "your-password")
   ```
3. Hash in `config/auth.edn` eintragen.

## Start

```bash
lein run
```

Oder mit anderem HTTP-Port:

```bash
PORT=8080 lein run
```

Dashboard: `http://localhost:3000`

## Dashboard Daten

Das Dashboard zeigt in einer 4-Spalten-Zeile:

1. `Portfolio Balance (USD)` aus `NetLiquidation`
2. leere Spacer-Spalte
3. Positionen (`Symbol`, `SecType`, `Currency`, `Position`, `AvgCost`)
4. Open Orders (`Symbol`, `Action`, `OrderType`, `Quantity`, `LimitPrice`, `Status`, `Filled`, `Remaining`)

Verwendete Wrapper-APIs:

- `ib.account/account-summary-snapshot!` mit `{:group "All" :tags ["NetLiquidation"] :timeout-ms 5000}`
- `ib.positions/positions-snapshot!` mit `{:timeout-ms 5000}`
- `ib.open-orders/open-orders-snapshot!` mit `{:mode :open :timeout-ms 5000}`

Refresh laeuft asynchron ueber `core.async/go` (default alle 10s).

## Fehlerbehandlung

- Timeout pro Bereich: `IB Timeout`
- Verbindungsverlust: `Disconnected`
- IB Error Events: werden geloggt und als UI-Event gemeldet
- Open-Orders-Snapshots laufen nicht parallel (In-Flight-Guard)

## Tests

```bash
lein test
```

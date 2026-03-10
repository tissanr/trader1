# trader1

## 1. Project Overview

`trader1` is a Clojure trading dashboard application for Interactive Brokers.

It connects to Interactive Brokers TWS or IB Gateway through the async wrapper [`ib-cl-wrap`](https://github.com/reiterstephan-tech/ib-cl-wrap) and displays:

- Portfolio Balance (USD)
- Current portfolio positions
- Open orders, including manual TWS orders (via `client-id = 0`)

## 2. Architecture

Main components:

- `trader1`: HTTP server, WebSocket broadcaster, authenticated dashboard UI
- `ib-cl-wrap`: async IB API wrapper (`core.async` + snapshot helpers)
- IB TWS / IB Gateway: broker runtime endpoint
- IB Java API (`ibapi.jar`): required Java classes used by the wrapper

```text
TWS / IB Gateway
        |
        v
   ib-cl-wrap
        |
        v
     trader1
        |
        v
   Dashboard UI
```

## 3. Requirements

- Java 17+ (required by shadow-cljs build tooling and modern Clojure dependencies; Java 21 LTS recommended)
- Leiningen (this repository is Leiningen-based)
- Interactive Brokers TWS or IB Gateway
- IB API JAR file (`ibapi.jar`)

## 4. Interactive Brokers Setup

In TWS (or equivalent Gateway API settings):

- Enable API socket access (`Enable ActiveX and Socket Clients`)
- Allow local connections (`127.0.0.1` / localhost)

Default IB API ports:

- Paper TWS: `7497`
- Live TWS: `7496`
- Paper Gateway: `4002`
- Live Gateway: `4001`

Important:

- `client-id = 0` is required to include manual TWS orders in open-order snapshots.
- Using other client IDs generally restricts visibility to API-submitted orders.

## 5. Installation

Clone the repository:

```bash
git clone https://github.com/reiterstephan-tech/trader1.git
cd trader1
```

Initialize submodules (required):

```bash
git submodule update --init --recursive
```

IB API JAR:

- Download `ibapi.jar` separately from Interactive Brokers.
- Place it at:

```text
lib/ibapi.jar
```

Dependency details:

- `ib-cl-wrap` is included as a pinned git submodule dependency.
- Current pinned commit:
  - `a559b1648a969334554662d048b4c6e586c20a0d`

## 6. Configuration

Runtime IB connection is configured via environment variables in the current implementation:

- `IB_HOST` (default: `127.0.0.1`)
- `IB_PORT` (default: `7497`)
- `client-id` is fixed to `0` in code
- snapshot timeout is fixed to `5000 ms` in code

Reference configuration map (conceptual shape):

```clojure
{:ib {:host "127.0.0.1"
      :port 7497
      :client-id 0
      :timeout-ms 5000}}
```

Field meaning:

- `:host`: IB API host
- `:port`: IB API socket port
- `:client-id`: IB client identifier (`0` required for manual TWS orders)
- `:timeout-ms`: snapshot timeout per IB request

## 7. Running the Application

Start the server:

```bash
lein run
```

Optional custom HTTP port:

```bash
PORT=8080 lein run
```

Startup behavior:

- Connects to IB (`ib.client/connect!`) with `client-id = 0`
- Subscribes to IB events
- Starts async snapshot refresh loop (default every 10 seconds)
- Loads and pushes dashboard data for:
  - Portfolio balance (`NetLiquidation`)
  - Positions
  - Open orders

Open:

```text
http://localhost:3000
```

## 8. Dashboard Layout

The dashboard main row has four columns:

| Column | Content |
|---|---|
| 1 | Portfolio Balance (USD) |
| 2 | Empty spacer cell |
| 3 | Portfolio Positions |
| 4 | Open Orders |

## 9. Error Handling

Current behavior:

- IB timeout in a snapshot cell: shows `IB Timeout` in that cell only
- Lost IB session / disconnected runtime: affected cells show `Disconnected`
- Missing `ibapi.jar`: IB connection initialization fails immediately; startup logs and UI reflect IB connection error
- IB error events: logged server-side and emitted to the UI

## 10. Development

Run tests:

```bash
lein test
```

Notes:

- Snapshot calls use async flows (`core.async/go`) through `ib-cl-wrap`
- No blocking logic is run inside IB callback threads

## 11. Security Notice

- `client-id = 0` binds manual TWS orders into the open-order view.
- This increases operational sensitivity when connected to live accounts.
- Validate behavior against Paper accounts first, then move to Live deliberately.

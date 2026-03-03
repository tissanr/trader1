(function () {
  "use strict";

  var ws;

  function connect() {
    var proto = location.protocol === "https:" ? "wss:" : "ws:";
    ws = new WebSocket(proto + "//" + location.host + "/ws");

    ws.onopen = function () {
      document.body.classList.remove("disconnected");
    };

    ws.onmessage = function (event) {
      var msg;
      try { msg = JSON.parse(event.data); }
      catch (e) { console.error("[trader1] Bad JSON:", e); return; }

      switch (msg.type) {
        case "connection":
          updateConnection(msg.data);
          break;
        case "kraken-balance":
          updateKrakenBalance(msg.data);
          break;
        case "kraken-orders":
          updateKrakenOrders(msg.data);
          break;
        case "kraken-portfolio-value":
          updateKrakenPortfolioValue(msg.data);
          break;
        case "kraken-ticker":
          updateKrakenTicker(msg.data);
          break;
        case "portfolio-balance":
          updatePortfolioBalance(msg.data);
          break;
        case "positions":
          updatePositions(msg.data);
          break;
        case "orders":
          updateOrders(msg.data);
          break;
        case "cell-error":
          updateCellError(msg.data);
          break;
        case "ib-error":
          appendIbLog("error", msg.data && msg.data.message ? msg.data.message : "(no message)");
          break;
        default:
          console.warn("[trader1] Unknown message type:", msg.type);
      }
    };

    ws.onclose = function () {
      document.body.classList.add("disconnected");
      setTimeout(connect, 5000);
    };

    ws.onerror = function () {
      ws.close();
    };
  }

  function appendIbLog(type, text) {
    var log = document.getElementById("ib-live-log");
    if (!log) return;
    var entry = document.createElement("div");
    entry.className = "ib-log-entry ib-log-" + type;
    var ts = new Date().toLocaleTimeString();
    entry.textContent = "[" + ts + "] " + (text || "");
    log.insertBefore(entry, log.firstChild);
    while (log.children.length > 100) { log.removeChild(log.lastChild); }
  }

  function updateConnection(data) {
    if (!data || !data.status) return;
    appendIbLog("connection", "IB connection: " + data.status);
    if (data.status === "connected") {
      document.body.classList.remove("disconnected");
      return;
    }
    document.body.classList.add("disconnected");
  }

  function updateCellError(data) {
    if (!data || !data.cell) return;
    if (data.message) {
      appendIbLog("cell-error", data.cell + ": " + data.message);
    }
    var id = data.cell + "-error";
    var el = document.getElementById(id);
    if (!el) {
      el = document.createElement("p");
      el.className = "cell-error";
      el.id = id;
      var cell = document.getElementById(data.cell + "-cell") || document.getElementById("portfolio-balance-cell");
      if (cell) cell.appendChild(el);
    }
    el.textContent = data.message || "";
    el.style.display = data.message ? "block" : "none";
  }

  function updatePortfolioBalance(data) {
    if (!data || data.value == null) return;
    var currency = data.currency || "USD";
    var value = parseFloat(data.value);
    var formatted = isNaN(value)
      ? String(data.value)
      : value.toLocaleString("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    setText("portfolio-balance-value", formatted + " " + currency);
  }

  function updateKrakenPortfolioValue(data) {
    if (!data || data["total-usd"] == null) return;
    var value = parseFloat(data["total-usd"]);
    var formatted = isNaN(value)
      ? String(data["total-usd"])
      : value.toLocaleString("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    setText("kraken-portfolio-value", formatted + " USD");
  }

  function updateKrakenTicker(data) {
    if (!data) return;
    var pair = data["XXBTZUSD"] || data["XBTUSD"] || data[Object.keys(data)[0]];
    if (!pair) return;
    setText("kraken-ticker-last", pair.c && pair.c[0]);
    setText("kraken-ticker-ask", pair.a && pair.a[0]);
    setText("kraken-ticker-bid", pair.b && pair.b[0]);
  }

  function updateKrakenBalance(data) {
    if (!data) return;
    var list = document.getElementById("kraken-balance-list");
    if (!list) return;
    list.innerHTML = "";
    var assets = Object.keys(data);
    if (assets.length === 0) {
      list.innerHTML = "<li class='empty'>No balance data</li>";
      return;
    }
    assets.forEach(function (asset) {
      var li = document.createElement("li");
      li.textContent = asset + ": " + data[asset];
      list.appendChild(li);
    });
  }

  function updateKrakenOrders(data) {
    if (!data) return;
    var list = document.getElementById("kraken-orders-list");
    if (!list) return;
    list.innerHTML = "";
    var open = data.open || {};
    var txids = Object.keys(open);
    if (txids.length === 0) {
      list.innerHTML = "<li class='empty'>No open orders</li>";
      return;
    }
    txids.forEach(function (txid) {
      var order = open[txid] || {};
      var descr = order.descr || {};
      var li = document.createElement("li");
      li.textContent =
        (descr.pair || "--") + " " +
        (descr.type || "--") + " " +
        (descr.ordertype || "--") + " @ " +
        (descr.price || "--") + " vol " +
        (order.vol || "--");
      list.appendChild(li);
    });
  }

  function updatePositions(rows) {
    var body = document.getElementById("positions-body");
    if (!body) return;
    body.innerHTML = "";

    if (!rows || rows.length === 0) {
      body.innerHTML = "<tr><td colspan='5' class='empty'>No positions</td></tr>";
      return;
    }

    rows.forEach(function (row) {
      var tr = document.createElement("tr");
      appendCell(tr, row.symbol);
      appendCell(tr, row["sec-type"]);
      appendCell(tr, row.currency);
      appendCell(tr, row.position);
      appendCell(tr, row["avg-cost"]);
      body.appendChild(tr);
    });
  }

  function updateOrders(rows) {
    var body = document.getElementById("orders-body");
    if (!body) return;
    body.innerHTML = "";

    if (!rows || rows.length === 0) {
      body.innerHTML = "<tr><td colspan='8' class='empty'>No open orders</td></tr>";
      return;
    }

    rows.forEach(function (row) {
      var tr = document.createElement("tr");
      appendCell(tr, row.symbol);
      appendCell(tr, row.action);
      appendCell(tr, row["order-type"]);
      appendCell(tr, row.quantity);
      appendCell(tr, row["limit-price"] == null ? "--" : row["limit-price"]);
      appendCell(tr, row.status || "--");
      appendCell(tr, row.filled == null ? "--" : row.filled);
      appendCell(tr, row.remaining == null ? "--" : row.remaining);
      body.appendChild(tr);
    });
  }

  function appendCell(tr, value) {
    var td = document.createElement("td");
    td.textContent = value == null ? "--" : String(value);
    tr.appendChild(td);
  }

  function setText(id, value) {
    var el = document.getElementById(id);
    if (el && value != null) el.textContent = value;
  }

  // IB debug buttons
  function ibPost(url) {
    var output = document.getElementById("ib-debug-output");
    if (output) output.textContent = "Loading…";
    var meta = document.querySelector("meta[name='csrf-token']");
    var headers = { "Content-Type": "application/json" };
    if (meta) headers["X-CSRF-Token"] = meta.getAttribute("content");
    fetch(url, { method: "POST", headers: headers })
      .then(function (r) { return r.json(); })
      .then(function (data) {
        if (output) output.textContent = JSON.stringify(data, null, 2);
      })
      .catch(function (e) {
        if (output) output.textContent = "Error: " + e.message;
      });
  }

  // Script is at bottom of body — DOM is already available, no DOMContentLoaded needed
  document.querySelectorAll(".ib-btn").forEach(function (btn) {
    btn.addEventListener("click", function () {
      ibPost(btn.getAttribute("data-action"));
    });
  });

  connect();
}());

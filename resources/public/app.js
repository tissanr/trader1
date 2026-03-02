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
          console.error("[trader1] IB Error:", msg.data && msg.data.message);
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

  function updateConnection(data) {
    if (!data || !data.status) return;
    if (data.status === "connected") {
      document.body.classList.remove("disconnected");
      return;
    }
    document.body.classList.add("disconnected");
  }

  function updateCellError(data) {
    if (!data || !data.cell) return;
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

  connect();
}());

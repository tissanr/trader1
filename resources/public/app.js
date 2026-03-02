(function () {
  "use strict";

  var ws;

  function connect() {
    var proto = location.protocol === "https:" ? "wss:" : "ws:";
    ws = new WebSocket(proto + "//" + location.host + "/ws");

    ws.onopen = function () {
      console.log("[trader1] WebSocket connected");
      document.body.classList.remove("disconnected");
    };

    ws.onmessage = function (event) {
      var msg;
      try { msg = JSON.parse(event.data); }
      catch (e) { console.error("[trader1] Bad JSON:", e); return; }

      switch (msg.type) {
        case "ticker":          updateTicker(msg.data);         break;
        case "balance":         updateBalance(msg.data);        break;
        case "orders":          updateOrders(msg.data);         break;
        case "portfolio-value": updatePortfolioValue(msg.data); break;
        default: console.warn("[trader1] Unknown message type:", msg.type);
      }
    };

    ws.onclose = function () {
      console.log("[trader1] WebSocket closed, reconnecting in 5s...");
      document.body.classList.add("disconnected");
      setTimeout(connect, 5000);
    };

    ws.onerror = function (err) {
      console.error("[trader1] WebSocket error:", err);
      ws.close();
    };
  }

  // Kraken ticker result:
  // { "XXBTZUSD": { "a":["ask","lot","lotWhole"], "b":["bid",...],
  //                 "c":["lastPrice","lot"], "v":["today","24h"], ... } }
  function updateTicker(data) {
    if (!data) return;
    var pair = data["XXBTZUSD"] || data[Object.keys(data)[0]];
    if (!pair) return;
    setText("ticker-last", pair.c && pair.c[0]);
    setText("ticker-ask",  pair.a && pair.a[0]);
    setText("ticker-bid",  pair.b && pair.b[0]);
    setText("ticker-vol",  pair.v && pair.v[1]);
  }

  // Kraken balance result: { "ZUSD": "12345.00", "XXBT": "0.5000", ... }
  function updateBalance(data) {
    if (!data) return;
    var ul = document.getElementById("balance-list");
    ul.innerHTML = "";
    Object.keys(data).forEach(function (asset) {
      var li = document.createElement("li");
      li.textContent = asset + ": " + data[asset];
      ul.appendChild(li);
    });
  }

  // Kraken open-orders result:
  // { "open": { "<txid>": { "descr": { "pair":"XBTUSD","type":"buy",
  //                                    "ordertype":"limit","price":"30000" },
  //                         "vol":"0.01", "status":"open" } } }
  function updateOrders(data) {
    if (!data) return;
    var ul = document.getElementById("orders-list");
    ul.innerHTML = "";
    var open = (data.open) || {};
    var txids = Object.keys(open);
    if (txids.length === 0) {
      ul.innerHTML = "<li class='empty'>No open orders</li>";
      return;
    }
    txids.forEach(function (txid) {
      var order = open[txid];
      var d = order.descr || {};
      var li = document.createElement("li");
      li.innerHTML =
        "<span class='txid'>" + txid.slice(0, 8) + "&hellip;</span> " +
        "<span class='pair'>"  + (d.pair      || "") + "</span> " +
        "<span class='side "   + (d.type || "") + "'>" + (d.type || "") + "</span> " +
        "<span class='otype'>" + (d.ordertype || "") + "</span> " +
        "@ <span class='oprice'>" + (d.price || "") + "</span> " +
        "vol <span class='vol'>" + (order.vol || "") + "</span>";
      ul.appendChild(li);
    });
  }

  function updatePortfolioValue(data) {
    if (!data || data.total_usd == null) return;
    var val = parseFloat(data.total_usd);
    setText("portfolio-total",
      "$\u202f" + val.toLocaleString("en-US", {minimumFractionDigits: 2, maximumFractionDigits: 2}));
  }

  function setText(id, value) {
    var el = document.getElementById(id);
    if (el && value != null) el.textContent = value;
  }

  connect();
}());

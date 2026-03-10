(ns trader1.frontend.components.kraken
  (:require [trader1.frontend.state :refer [app-state]]))

(defn- fmt-price [s]
  (when s
    (-> (js/parseFloat s)
        (.toLocaleString "en-US" #js {:minimumFractionDigits 2 :maximumFractionDigits 2}))))

(defn portfolio-value []
  (let [pv    (:kraken-portfolio-value @app-state)
        total (some-> pv :total-value     fmt-price)
        pos   (some-> pv :positions-value fmt-price)
        cash  (some-> pv :cash-usd        fmt-price)]
    [:section#kraken-portfolio-cell
     [:h2 "Kraken Portfolio Value"]
     [:div.kp-row
      [:span.kp-label "Total Portfolio"]
      [:span.kp-total (if total (str "$" total) "--")]]
     [:div.kp-row
      [:span.kp-label "Positions"]
      [:span.kp-amount (if pos (str "$" pos) "--")]]
     [:div.kp-row
      [:span.kp-label "Cash (USD)"]
      [:span.kp-amount (if cash (str "$" cash) "--")]]]))

(defn ticker []
  (let [t          (:kraken-ticker @app-state)
        pair-data  (first (vals t))
        last-price (some-> pair-data :c first fmt-price)
        ask-price  (some-> pair-data :a first fmt-price)
        bid-price  (some-> pair-data :b first fmt-price)]
    [:section#kraken-ticker-cell
     [:h2 "Kraken Ticker (XBT/USD)"]
     [:div.row [:span.label "Last"] [:span (or last-price "--")]]
     [:div.row [:span.label "Ask"]  [:span (or ask-price  "--")]]
     [:div.row [:span.label "Bid"]  [:span (or bid-price  "--")]]]))

(defn- asset-label [k]
  (let [s (name k)]
    (case s
      "XXBT" "BTC" "XETH" "ETH" "ZUSD" "USD"
      "XLTC" "LTC" "XXLM" "XLM" "XXRP" "XRP"
      (if (re-matches #"[XZ].+" s) (subs s 1) s))))

(defn balance []
  (let [bal   (:kraken-balance @app-state)
        error (get-in @app-state [:errors :balance])]
    [:section#kraken-balance-cell
     [:h2 "Kraken Balance"]
     (when error [:p.cell-error error])
     (if (seq bal)
       [:ul
        (for [[asset amount] (sort-by (comp name key) bal)]
          ^{:key (name asset)}
          [:li (str (asset-label asset) ": " amount)])]
       [:ul [:li.empty "Connecting…"]])]))

(defn orders []
  (let [raw        (:kraken-orders @app-state)
        open-map   (:open raw)]
    [:section#kraken-orders-cell
     [:h2 "Kraken Open Orders"]
     (if (seq open-map)
       [:ul
        (for [[txid order] open-map]
          ^{:key (name txid)}
          [:li (or (get-in order [:descr :order]) (name txid))])]
       [:ul [:li.empty "No open orders"]])]))

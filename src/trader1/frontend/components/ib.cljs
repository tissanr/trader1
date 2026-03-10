(ns trader1.frontend.components.ib
  (:require [trader1.frontend.state :refer [app-state]]))

;; ── CSRF + HTTP helpers ───────────────────────────────────────────────────────

(defn- csrf-token []
  (some-> (js/document.querySelector "meta[name='csrf-token']")
          (.getAttribute "content")))

(defn- ib-post! [action]
  (swap! app-state assoc :ib-debug-output "Loading…")
  (-> (js/fetch action #js {:method  "POST"
                             :headers #js {"X-CSRF-Token" (or (csrf-token) "")
                                           "Content-Type" "application/x-www-form-urlencoded"}})
      (.then #(.json %))
      (.then #(swap! app-state assoc :ib-debug-output (js/JSON.stringify % nil 2)))
      (.catch #(swap! app-state assoc :ib-debug-output (str "Error: " %)))))

;; ── Portfolio balance ────────────────────────────────────────────────────────

(defn portfolio-balance []
  (let [bal   (:portfolio-balance @app-state)
        error (get-in @app-state [:errors :balance])
        text  (when bal (str (:value bal) " " (or (:currency bal) "USD")))]
    [:section#portfolio-balance-cell
     [:h2 "Portfolio Balance (USD)"]
     (when error [:p.cell-error error])
     [:p.value (or text "--")]]))

;; ── Positions table ──────────────────────────────────────────────────────────

(defn positions []
  (let [rows  (:positions @app-state)
        error (get-in @app-state [:errors :positions])]
    [:section#positions-cell
     [:h2 "Positionen"]
     (when error [:p.cell-error error])
     [:table.data-table
      [:thead
       [:tr [:th "Symbol"] [:th "SecType"] [:th "Currency"] [:th "Position"] [:th "AvgCost"]]]
      [:tbody
       (if (seq rows)
         (for [row rows]
           ^{:key (str (:symbol row) (:sec-type row))}
           [:tr
            [:td (:symbol row)]
            [:td (:sec-type row)]
            [:td (:currency row)]
            [:td (:position row)]
            [:td (:avg-cost row)]])
         [:tr [:td {:col-span 5 :class "empty"} "No positions"]])]]]))

;; ── Orders table ─────────────────────────────────────────────────────────────

(defn orders []
  (let [rows  (:orders @app-state)
        error (get-in @app-state [:errors :orders])]
    [:section#orders-cell
     [:h2 "Offene Orders"]
     (when error [:p.cell-error error])
     [:table.data-table
      [:thead
       [:tr [:th "Symbol"] [:th "Action"] [:th "OrderType"]
            [:th "Quantity"] [:th "LimitPrice"] [:th "Status"]
            [:th "Filled"] [:th "Remaining"]]]
      [:tbody
       (if (seq rows)
         (for [row rows]
           ^{:key (str (:symbol row) (:action row) (:status row))}
           [:tr
            [:td (:symbol row)]
            [:td (:action row)]
            [:td (:order-type row)]
            [:td (:quantity row)]
            [:td (:limit-price row)]
            [:td (:status row)]
            [:td (:filled row)]
            [:td (:remaining row)]])
         [:tr [:td {:col-span 8 :class "empty"} "No open orders"]])]]]))

;; ── Debug panel ──────────────────────────────────────────────────────────────

(defn- log-class [entry-type]
  (case entry-type
    "error"      "ib-log-entry ib-log-error"
    "connection" "ib-log-entry ib-log-connection"
    "cell-error" "ib-log-entry ib-log-cell-error"
    "ib-log-entry"))

(defn- fmt-ts [ts]
  (-> (js/Date. ts)
      (.toLocaleTimeString "en-US" #js {:hour12 false})))

(defn debug-panel []
  (let [output (:ib-debug-output @app-state)
        log    (:ib-log @app-state)]
    [:section#ib-debug
     [:h2 "IB API Test"]
     [:div#ib-buttons
      (for [[label action] [["Ping IB"            "/ib/ping"]
                            ["Reconnect IB"        "/ib/reconnect"]
                            ["Refresh Balance"     "/ib/refresh/balance"]
                            ["Refresh Positions"   "/ib/refresh/positions"]
                            ["Refresh Orders"      "/ib/refresh/orders"]
                            ["AAPL Quote"          "/ib/quote?symbol=AAPL"]]]
        ^{:key label}
        [:button.ib-btn {:on-click #(ib-post! action)} label])]
     [:div.ib-panels
      [:div.ib-panel
       [:span.ib-panel-label "Last response"]
       [:pre#ib-debug-output (or output "Click a button to test the IB API.")]]
      [:div.ib-panel
       [:span.ib-panel-label "Live IB messages"]
       [:div#ib-live-log
        (if (seq log)
          (for [[i entry] (map-indexed vector (rseq (vec log)))]
            ^{:key i}
            [:div {:class (log-class (:type entry))}
             (str "[" (fmt-ts (:ts entry)) "] " (:text entry))])
          [:div.ib-log-entry "No messages yet"])]]]]))

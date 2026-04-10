(ns trader1.frontend.components.ib
  (:require [clojure.string :as str]
            [reagent.core :as r]
            [trader1.frontend.state :refer [app-state]]))

;; ── CSRF + HTTP helpers ───────────────────────────────────────────────────────

(defn- csrf-token []
  (some-> (js/document.querySelector "meta[name='csrf-token']")
          (.getAttribute "content")))

(defonce order-form
  (r/atom {:symbol "AAPL"
           :action "BUY"
           :order-type "MKT"
           :quantity "1"
           :limit-price ""}))

(defonce order-feedback
  (r/atom {:status nil
           :message nil}))

(defn- pretty-json [value]
  (js/JSON.stringify (clj->js value) nil 2))

(defn- set-debug-output! [value]
  (swap! app-state assoc :ib-debug-output (pretty-json value)))

(defn- set-order-feedback! [status message]
  (reset! order-feedback {:status status :message message}))

(defn- request-url [path params]
  (let [query (->> params
                   (remove (fn [[_ v]] (or (nil? v) (= "" v))))
                   (map (fn [[k v]]
                          (str (js/encodeURIComponent (name k))
                               "="
                               (js/encodeURIComponent (str v)))))
                   (str/join "&"))]
    (if (str/blank? query)
      path
      (str path "?" query))))

(defn- submit-request! [path params on-success]
  (swap! app-state assoc :ib-debug-output "Loading…")
  (-> (js/fetch (request-url path params)
                #js {:method "POST"
                     :headers #js {"X-CSRF-Token" (or (csrf-token) "")
                                   "Content-Type" "application/x-www-form-urlencoded"}})
      (.then #(.json %))
      (.then (fn [payload]
               (let [data (js->clj payload :keywordize-keys true)]
                 (set-debug-output! data)
                 (when on-success
                   (on-success data)))))
      (.catch (fn [err]
                (swap! app-state assoc :ib-debug-output (str "Error: " err))
                (set-order-feedback! :error (str err))))))

(defn- ib-post! [action]
  (submit-request! action nil nil))

(defn- limit-order? []
  (= "LMT" (:order-type @order-form)))

(defn- update-order-form! [k value]
  (swap! order-form assoc k value)
  (when (and (= k :order-type) (= value "MKT"))
    (swap! order-form assoc :limit-price "")))

(defn- normalized-order-params []
  {:symbol (some-> (:symbol @order-form) str/upper-case str/trim)
   :action (:action @order-form)
   :order-type (:order-type @order-form)
   :quantity (str/trim (or (:quantity @order-form) ""))
   :limit-price (when (limit-order?)
                  (str/trim (or (:limit-price @order-form) "")))
   :exchange "SMART"
   :currency "USD"})

(defn- client-order-error [params]
  (cond
    (str/blank? (:symbol params)) "Symbol is required"
    (not (#{"BUY" "SELL"} (:action params))) "Action must be BUY or SELL"
    (not (#{"MKT" "LMT"} (:order-type params))) "Order type must be MKT or LMT"
    (or (str/blank? (:quantity params))
        (js/isNaN (js/Number (:quantity params)))
        (<= (js/Number (:quantity params)) 0)) "Quantity must be greater than 0"
    (and (= "LMT" (:order-type params))
         (or (str/blank? (:limit-price params))
             (js/isNaN (js/Number (:limit-price params)))
             (<= (js/Number (:limit-price params)) 0))) "Limit price must be greater than 0"
    :else nil))

(defn- submit-order! []
  (let [params (normalized-order-params)]
    (if-let [error (client-order-error params)]
      (set-order-feedback! :error error)
      (do
        (set-order-feedback! :pending "Submitting order…")
        (submit-request! "/ib/order" params
                         (fn [response]
                           (if (:ok response)
                             (set-order-feedback!
                              :success
                              (str (:action response) " "
                                   (:quantity response) " "
                                   (:symbol response)
                                   (when (= "LMT" (:order-type response))
                                     (str " @ " (:limit-price response)))
                                   " submitted"))
                             (set-order-feedback!
                              :error
                              (or (:message response)
                                  (:error response)
                                  "Order submission failed")))))))))

(defn order-panel []
  (let [positions (:positions @app-state)
        {:keys [status message]} @order-feedback
        symbols (->> positions
                     (map :symbol)
                     (remove str/blank?)
                     distinct
                     sort
                     vec)]
    [:section#ib-order-panel
     [:h2 "IB Order Panel"]
     [:p.ib-order-help "Submit a minimal IB stock order from the portfolio area. Supported: BUY/SELL, MKT/LMT, SMART/USD."]
     (when message
       [:p {:class (case status
                     :success "cell-info"
                     :pending "cell-info"
                     "cell-error")}
        message])
     [:div.ib-order-form
      [:label
       [:span "Symbol"]
       [:input {:type "text"
                :list "ib-order-symbols"
                :value (:symbol @order-form)
                :on-change #(update-order-form! :symbol (.. % -target -value))}]]
      [:label
       [:span "Action"]
       [:select {:value (:action @order-form)
                 :on-change #(update-order-form! :action (.. % -target -value))}
        [:option {:value "BUY"} "BUY"]
        [:option {:value "SELL"} "SELL"]]]
      [:label
       [:span "Order Type"]
       [:select {:value (:order-type @order-form)
                 :on-change #(update-order-form! :order-type (.. % -target -value))}
        [:option {:value "MKT"} "MKT"]
        [:option {:value "LMT"} "LMT"]]]
      [:label
       [:span "Quantity"]
       [:input {:type "number"
                :min "1"
                :step "1"
                :value (:quantity @order-form)
                :on-change #(update-order-form! :quantity (.. % -target -value))}]]
      [:label
       [:span "Limit Price"]
       [:input {:type "number"
                :min "0"
                :step "0.01"
                :disabled (not (limit-order?))
                :placeholder (if (limit-order?) "e.g. 400.00" "Only for LMT")
                :value (:limit-price @order-form)
                :on-change #(update-order-form! :limit-price (.. % -target -value))}]]]
     [:datalist#ib-order-symbols
      (for [symbol symbols]
        ^{:key symbol}
        [:option {:value symbol}])]
     [:div.ib-order-actions
      [:button.ib-btn {:type "button"
                       :on-click submit-order!}
       "Submit Order"]
      [:button.ib-btn {:type "button"
                       :on-click #(ib-post! "/ib/refresh/orders")}
       "Refresh Orders"]]]))

;; ── Portfolio balance ────────────────────────────────────────────────────────

(defn portfolio-balance []
  (let [rows  (:portfolio-balance @app-state)
        error (get-in @app-state [:errors :balance])]
    [:section#portfolio-balance-cell
     [:h2 "Portfolio Balance"]
     (when error [:p.cell-error error])
     [:table.data-table
      [:thead
       [:tr [:th "Account"] [:th "Net Liquidation"] [:th "Buying Power"] [:th "Currency"]]]
      [:tbody
       (if (seq rows)
         (for [row rows]
           ^{:key (:account row)}
           [:tr
            [:td (:account row)]
            [:td (:net-liquidation row)]
            [:td (:buying-power row)]
            [:td (:currency row)]])
         [:tr [:td {:col-span 4 :class "empty"} "--"]])]]]))

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
  (let [rows (:orders @app-state)
        {:keys [status message]} (:orders-state @app-state)
        error (get-in @app-state [:errors :orders])
        notice (or message error)]
    [:section#orders-cell
     [:h2 "Offene Orders"]
     (when notice
       [:p {:class (if (= "loading" status) "cell-info" "cell-error")} notice])
     [:table.data-table
      [:thead
       [:tr [:th "OrderId"] [:th "Account"] [:th "Symbol"] [:th "Action"] [:th "OrderType"]
            [:th "Quantity"] [:th "LimitPrice"] [:th "Status"]
            [:th "Filled"] [:th "Remaining"]]]
      [:tbody
       (if (seq rows)
         (for [row rows]
           ^{:key (str (:order-id row))}
           [:tr
            [:td (:order-id row)]
            [:td (:account-id row)]
            [:td (:symbol row)]
            [:td (:action row)]
            [:td (:order-type row)]
            [:td (:quantity row)]
            [:td (:limit-price row)]
            [:td (:status row)]
            [:td (:filled row)]
            [:td (:remaining row)]])
         [:tr
          [:td {:col-span 10 :class "empty"}
           (case status
             "loading" "Loading open orders..."
             "timeout" "Open orders snapshot timed out"
             "disconnected" "Reconnect to IB to load open orders"
             "error" "Open orders snapshot failed"
             "ready" "No open orders"
             "Waiting for first open-orders snapshot...")]])]]]))

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
                            ["AAPL Quote"          "/ib/quote?symbol=AAPL&exchange=SMART&currency=USD"]
                            ["Buy 10 AAPL (MKT)"   "/ib/order?symbol=AAPL&exchange=SMART&currency=USD&action=BUY&quantity=10"]
                            ["Sell 1 AAPL (MKT)"   "/ib/order?symbol=AAPL&exchange=SMART&currency=USD&action=SELL&quantity=1"]
                            ["Sell 1 AAPL @ 400 LMT" "/ib/order?symbol=AAPL&exchange=SMART&currency=USD&action=SELL&quantity=1&order-type=LMT&limit-price=400"]
                            ["Account Summary"      "/ib/account-summary"]]]
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

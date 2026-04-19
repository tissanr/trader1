(ns trader1.web
  (:require [clojure.core.async :as async]
            [clojure.string :as str]
            [compojure.core :refer [defroutes GET POST]]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring.util.anti-forgery :refer [anti-forgery-field]]
            [ring.util.response :as resp]
            [hiccup.page :refer [html5 include-css include-js]]
            [cheshire.core :as json]
            [org.httpkit.server :as httpkit]
            [ib.account :as ib.account]
            [ib.client :as ib.client]
            [ib.contract :as ib.contract]
            [ib.market-data :as ib.market-data]
            [ib.open-orders :as ib.orders]
            [ib.positions :as ib.positions]
            [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]
            [trader1.auth :as auth]
            [trader1.kraken :as kraken]
            [trader1.security :as security]
            [trader1.specs :as specs]
            [trader1.settings :as settings]))

(defonce connected-channels (atom #{}))
(defonce ib-runtime (atom nil))
(defonce ui-state
  (atom {:balance nil
         :positions []
         :orders []
         :orders-state {:status "idle"}
         :order-submission {:status "idle"}
         :kraken-balance nil
         :kraken-orders nil
         :kraken-portfolio-value nil
         :kraken-ticker nil
         :errors {:balance nil :positions nil :orders nil}
         :connection :disconnected}))
(defonce last-order-status (atom {}))
(defonce last-open-orders (atom (sorted-map)))

(defn- validated [spec value context]
  (specs/assert-valid! spec value context))

(defn- websocket-message [type data]
  (specs/assert-websocket-message! {:type type :data data}))

(defn- send-payload! [ch type data]
  (httpkit/send! ch (json/generate-string (websocket-message type data))))

(defn broadcast!
  "Sends a JSON-encoded payload map to all connected WebSocket clients."
  [payload-map]
  (let [msg (json/generate-string (specs/assert-websocket-message! payload-map))]
    (doseq [ch @connected-channels]
      (httpkit/send! ch msg))))

(defn- set-cell-error! [cell message]
  (swap! ui-state assoc-in [:errors cell] message)
  (broadcast! {:type "cell-error" :data {:cell (name cell) :message message}}))

(defn- clear-cell-error! [cell]
  (swap! ui-state assoc-in [:errors cell] nil)
  (broadcast! {:type "cell-error" :data {:cell (name cell) :message nil}}))

(defn- set-orders-state!
  ([status]
   (set-orders-state! status nil))
  ([status message]
   (let [data (cond-> {:status status}
                (some? message) (assoc :message message)
                (#{"ready" "timeout" "error"} status) (assoc :order-count (count (:orders @ui-state)))
                (= "ready" status) (assoc :updated-at (System/currentTimeMillis)))]
     (swap! ui-state assoc :orders-state data)
     (broadcast! {:type "orders-state" :data data}))))

(defn- set-disconnected! []
  (reset! last-order-status {})
  (reset! last-open-orders (sorted-map))
  (swap! ui-state assoc :connection :disconnected
         :orders []
         :orders-state {:status "disconnected"
                        :message "Disconnected from IB"}
         :order-submission {:status "error"
                            :message "Disconnected from IB"})
  (doseq [cell [:balance :positions :orders]]
    (set-cell-error! cell "Disconnected"))
  (broadcast! {:type "orders" :data []})
  (broadcast! {:type "orders-state" :data {:status "disconnected"
                                           :message "Disconnected from IB"}})
  (broadcast! {:type "order-submission" :data {:status "error"
                                               :message "Disconnected from IB"}})
  (broadcast! {:type "connection" :data {:status "disconnected"}}))

(defn- mark-connected! []
  (swap! ui-state assoc :connection :connected)
  (broadcast! {:type "connection" :data {:status "connected"}}))

(defn- ib-config []
  (get-in @settings/settings [:services :ib]))

(defn- ib-snapshot-timeout-ms []
  (get-in @settings/settings [:services :ib :snapshot-timeout-ms]))

(defn- ib-refresh-ms []
  (get-in @settings/settings [:services :ib :refresh-ms]))

(defn- kraken-refresh-ms []
  (get-in @settings/settings [:services :kraken :refresh-ms]))

(defn- summary-values->rows [summary-values]
  (validated
   ::specs/portfolio-balance
   (vec (for [[account tags] summary-values
              :let [nl (get tags "NetLiquidation")
                    bp (get tags "BuyingPower")]
              :when (or nl bp)]
          {:account         account
           :net-liquidation (some-> nl :value)
           :buying-power    (some-> bp :value)
           :currency        (or (some-> nl :currency) (some-> bp :currency) "USD")}))
   "portfolio balance rows"))

(defn- balance-timeout? [result]
  (or (= :timeout (:error result))
     (= :timeout (get-in result [:ib-error :reason]))
     (= 504 (get-in result [:ib-error :code]))))

(defn- positions-timeout? [result]
  (or (= :timeout (:reason result))
      (= 504 (:code result))))

(defn- orders-timeout? [result]
  (or (= :timeout (:error result))
      (= 504 (:code result))))

(defn- parse-long-param [value default]
  (Long/parseLong (or value default)))

(defn- parse-double-param [value]
  (when (some? value)
    (Double/parseDouble value)))

(defn- parse-long-param-safe [value]
  (try
    (when-not (str/blank? (str value))
      (Long/parseLong (str value)))
    (catch Exception _
      nil)))

(defn- parse-double-param-safe [value]
  (try
    (when-not (str/blank? (str value))
      (Double/parseDouble (str value)))
    (catch Exception _
      nil)))

(defn- order-status-for [order-id]
  (get @last-order-status order-id {}))

(defn- benign-ib-message? [message]
  (let [message' (some-> message str/lower-case)]
    (boolean
     (and message'
          (or (str/includes? message' "data farm connection is broken")
              (str/includes? message' "data farm connection is ok")
              (str/includes? message' "hmds data farm connection is broken")
              (str/includes? message' "hmds data farm connection is ok")
              (str/includes? message' "sec-def data farm connection is broken")
              (str/includes? message' "sec-def data farm connection is ok"))))))

(defn- to-order-row [order-event]
  (let [{:keys [order-id contract order order-state]} order-event
        status-from-stream (order-status-for order-id)]
    (validated
     ::specs/order-row
     {:order-id (or order-id "--")
      :account-id (or (:account order-event) "--")
      :symbol (or (:symbol contract) "--")
      :action (or (:action order) "--")
      :order-type (or (:orderType order) "--")
      :quantity (or (:totalQuantity order) "--")
      :limit-price (:lmtPrice order)
      :status (or (:status-text status-from-stream)
                  (:status order-state)
                  "--")
      :filled (:filled status-from-stream)
      :remaining (:remaining status-from-stream)}
     "order row")))

(defn- to-position-row [position-event]
  (let [{:keys [contract position avg-cost]} position-event]
    (validated
     ::specs/position-row
     {:symbol (or (:symbol contract) "--")
      :sec-type (or (:secType contract) "--")
      :currency (or (:currency contract) "--")
      :position (or position "--")
      :avg-cost (or avg-cost "--")}
     "position row")))

(defn- push-state-to-client! [ch]
  (let [{:keys [balance positions orders orders-state errors connection
                order-submission kraken-balance kraken-orders
                kraken-portfolio-value kraken-ticker]} @ui-state]
    (send-payload! ch "connection" {:status (name connection)})
    (send-payload! ch "kraken-balance" kraken-balance)
    (send-payload! ch "kraken-orders" kraken-orders)
    (send-payload! ch "kraken-portfolio-value" kraken-portfolio-value)
    (send-payload! ch "kraken-ticker" kraken-ticker)
    (send-payload! ch "portfolio-balance" balance)
    (send-payload! ch "positions" positions)
    (send-payload! ch "orders" orders)
    (send-payload! ch "orders-state" orders-state)
    (send-payload! ch "order-submission" order-submission)
    (doseq [[cell message] errors]
      (send-payload! ch "cell-error" {:cell (name cell)
                                      :message message}))))

(defn- set-order-submission!
  [data]
  (let [payload (validated ::specs/order-submission-data data "order submission")]
    (swap! ui-state assoc :order-submission payload)
    (broadcast! {:type "order-submission" :data payload})))

(defn- publish-order-rows! [rows]
  (clear-cell-error! :orders)
  (swap! ui-state assoc :orders rows)
  (set-orders-state! "ready")
  (broadcast! {:type "orders" :data rows}))

(defn- sync-open-orders! []
  (let [rows (->> @last-open-orders
                  vals
                  (sort-by :order-id)
                  (mapv to-order-row))]
    (publish-order-rows! rows)
    rows))

(defn- remember-open-orders! [order-events]
  (reset! last-open-orders
          (into (sorted-map)
                (map (fn [evt] [(:order-id evt) evt]))
                order-events))
  (sync-open-orders!))

(defn- remember-open-order! [order-event]
  (swap! last-open-orders assoc (:order-id order-event) order-event)
  (sync-open-orders!))

(defn- refresh-open-orders!
  ([conn]
   (refresh-open-orders! conn :all))
  ([conn mode]
   (let [result (async/<!! (ib.orders/open-orders-snapshot!
                             conn {:mode mode
                                   :timeout-ms (ib-snapshot-timeout-ms)}))]
     (if (:ok result)
       (let [rows (remember-open-orders! (:orders result))]
         {:ok true :rows rows})
       (let [timed-out? (orders-timeout? result)]
         (set-cell-error! :orders (if timed-out? "IB Timeout" "IB Error"))
         (set-orders-state! (if timed-out? "timeout" "error")
                            (if timed-out?
                              "Open orders snapshot timed out."
                              "Open orders snapshot failed."))
         {:ok false :error (some-> (:error result) str)})))))

(defn- ib-json-response [body]
  {:status 200
   :headers {"Content-Type" "application/json; charset=utf-8"}
   :body (json/generate-string body)})

(defn- ib-conn [] (:conn @ib-runtime))

(defn- submission-base [order-req]
  (cond-> {:symbol (:symbol order-req)
           :action (:action order-req)
           :order-type (:order-type order-req)
           :tif (:tif order-req)
           :outside-rth (:outside-rth order-req)
           :submitted-at (System/currentTimeMillis)}
    (some? (:quantity order-req))
    (assoc :quantity (:quantity order-req))
    (= "LMT" (:order-type order-req))
    (assoc :limit-price (:limit-price order-req))))

(defn- error-response [message]
  {:ok false :message message})

(defn- normalize-order-request [params]
  (let [symbol      (some-> (or (:symbol params) "") str/trim str/upper-case)
        action      (some-> (or (:action params) "BUY") str/trim str/upper-case)
        order-type  (some-> (or (:order-type params) "MKT") str/trim str/upper-case)
        exchange    (some-> (or (:exchange params) "SMART") str/trim str/upper-case)
        currency    (some-> (or (:currency params) "USD") str/trim str/upper-case)
        tif         (some-> (or (:tif params) "DAY") str/trim str/upper-case)
        quantity    (parse-long-param-safe (or (:quantity params) "1"))
        limit-price (parse-double-param-safe (:limit-price params))
        outside-rth (contains? #{"true" "on" "1" true} (:outside-rth params))]
    (cond-> {:symbol symbol
             :action action
             :order-type order-type
             :quantity quantity
             :exchange exchange
             :currency currency
             :tif tif
             :outside-rth outside-rth}
      (= "LMT" order-type) (assoc :limit-price limit-price))))

(defn- validate-order-request [order-req]
  (cond
    (str/blank? (:symbol order-req))
    "Symbol is required"

    (not (#{"BUY" "SELL"} (:action order-req)))
    "Action must be BUY or SELL"

    (not (#{"MKT" "LMT"} (:order-type order-req)))
    "Order type must be MKT or LMT"

    (str/blank? (:exchange order-req))
    "Exchange is required"

    (str/blank? (:currency order-req))
    "Currency is required"

    (not (#{"DAY" "GTC"} (:tif order-req)))
    "Time in force must be DAY or GTC"

    (nil? (:quantity order-req))
    "Quantity must be a whole number greater than 0"

    (<= (:quantity order-req) 0)
    "Quantity must be a whole number greater than 0"

    (and (= "LMT" (:order-type order-req))
         (or (nil? (:limit-price order-req))
             (<= (:limit-price order-req) 0)))
    "Limit price must be greater than 0 for LMT orders"

    (and (not= "LMT" (:order-type order-req))
         (contains? order-req :limit-price))
    "Limit price is only allowed for LMT orders"

    :else
    (try
      (validated ::specs/ib-order-request order-req "IB order request")
      nil
      (catch Exception _
        "Invalid order request"))))

(defn login-page [error-msg]
  (html5
    [:head
     [:meta {:charset "utf-8"}]
     [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
     [:title "Trader1 - Login"]
     (include-css "/style.css")]
    [:body
     [:div#login-box
      [:h1 "Trader1"]
      (when error-msg [:p.error error-msg])
      [:form {:method "post" :action "/login"}
       (anti-forgery-field)
       [:label "Username"
        [:input {:type "text" :name "username" :autofocus true :required true}]]
       [:label "Password"
        [:input {:type "password" :name "password" :required true}]]
       [:button {:type "submit"} "Sign in"]]]]))

(defn- html-response [body]
  (-> (resp/response body)
      (resp/content-type "text/html; charset=utf-8")))

(defn- setup-page
  ([] (setup-page nil nil nil))
  ([error-msg username kraken-key]
   (html5
     [:head
      [:meta {:charset "utf-8"}]
      [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
      [:title "Trader1 - Setup"]
      (include-css "/style.css")]
     [:body
      [:div#login-box
       [:h1 "Trader1"]
       [:p "Create your admin account to get started."]
       (when error-msg [:p.error error-msg])
       [:form {:method "post" :action "/setup"}
        (anti-forgery-field)
        [:label "Username"
         [:input {:type "text" :name "username" :value (or username "admin")
                  :autofocus true :required true}]]
        [:label "Password"
         [:input {:type "password" :name "password" :required true}]]
        [:label "Confirm password"
         [:input {:type "password" :name "confirm-password" :required true}]]
        [:details {:style "margin-top:1rem"}
         [:summary "Kraken API credentials (optional)"]
         [:div {:style "margin-top:0.5rem"}
          [:label "API key"
           [:input {:type "text" :name "kraken-key" :value (or kraken-key "")
                    :autocomplete "off"}]]
          [:label "API secret"
           [:input {:type "password" :name "kraken-secret" :autocomplete "off"}]]]]
        [:button {:type "submit"} "Set up Trader1"]]]])))

(defn- setup-handler [request]
  (when (auth/needs-setup?)
    (let [{:keys [username password confirm-password kraken-key kraken-secret]}
          (:params request)
          username        (str/trim (or username ""))
          kraken-key      (str/trim (or kraken-key ""))
          kraken-secret   (str/trim (or kraken-secret ""))]
      (cond
        (str/blank? username)
        (html-response (setup-page "Username cannot be blank." nil nil))

        (str/blank? password)
        (html-response (setup-page "Password cannot be blank." username kraken-key))

        (not= password confirm-password)
        (html-response (setup-page "Passwords do not match." username kraken-key))

        :else
        (do
          (auth/write-config! username password)
          (when (and (not (str/blank? kraken-key))
                     (not (str/blank? kraken-secret)))
            (security/write-credentials! kraken-key kraken-secret))
          (resp/redirect "/login"))))))

(defn dashboard-page []
  (html5
    [:head
     [:meta {:charset "utf-8"}]
     [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
     [:meta {:name "csrf-token" :content (force *anti-forgery-token*)}]
     [:title "Trader1 - Dashboard"]
     (include-css "/style.css")]
    [:body
     [:div#app]
     (include-js "/js/main.js")]))

(defn- interval-option [name-attr current-ms value-ms label]
  [:option {:value (if (nil? value-ms) "manual" (str value-ms))
            :selected (when (= current-ms value-ms) "selected")}
   label])

(defn settings-page []
  (let [cfg (get-in @settings/settings [:services :kraken])]
    (html5
      [:head
       [:meta {:charset "utf-8"}]
       [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
       [:title "Trader1 - Settings"]
       (include-css "/style.css")]
      [:body
       [:header
        [:h1 "Trader1"]
        [:nav
         [:a {:href "/dashboard"} "Dashboard"]
         [:a {:href "/logout"} "Logout"]]]
       [:main
        [:section#settings
         [:h2 "Polling Intervals"]
         [:form {:method "post" :action "/settings"}
          (anti-forgery-field)
          [:div.setting-row
           [:label "Ticker"]
           [:select {:name "ticker-ms"}
            (interval-option "ticker-ms" (:ticker-ms cfg) 5000    "5 seconds (default)")
            (interval-option "ticker-ms" (:ticker-ms cfg) 300000  "5 minutes")
            (interval-option "ticker-ms" (:ticker-ms cfg) 600000  "10 minutes")
            (interval-option "ticker-ms" (:ticker-ms cfg) nil     "Manual")]]
          [:div.setting-row
           [:label "Balance"]
           [:select {:name "balance-ms"}
            (interval-option "balance-ms" (:balance-ms cfg) 30000   "30 seconds (default)")
            (interval-option "balance-ms" (:balance-ms cfg) 300000  "5 minutes")
            (interval-option "balance-ms" (:balance-ms cfg) 600000  "10 minutes")
            (interval-option "balance-ms" (:balance-ms cfg) nil     "Manual")]]
          [:div.setting-row
           [:label "Orders"]
           [:select {:name "orders-ms"}
            (interval-option "orders-ms" (:orders-ms cfg) 15000   "15 seconds (default)")
            (interval-option "orders-ms" (:orders-ms cfg) 300000  "5 minutes")
            (interval-option "orders-ms" (:orders-ms cfg) 600000  "10 minutes")
            (interval-option "orders-ms" (:orders-ms cfg) nil     "Manual")]]
          [:button {:type "submit"} "Save"]]]]])))

(defn login-handler [request]
  (let [{:keys [username password]} (:params request)]
    (if-let [user (auth/authenticate username password)]
      (-> (resp/redirect "/dashboard")
          (assoc :session {:identity (:username user)}))
      (html-response (login-page "Invalid username or password.")))))

(defn logout-handler [_]
  (-> (resp/redirect "/login")
      (assoc :session nil)))

(defn- parse-interval [s]
  (when (not= s "manual")
    (Long/parseLong s)))

(defn settings-handler [request]
  (let [{:keys [ticker-ms balance-ms orders-ms]} (:params request)]
    (settings/save! (assoc-in @settings/settings
                              [:services :kraken]
                              (merge (get-in @settings/settings [:services :kraken])
                                     {:ticker-ms  (parse-interval ticker-ms)
                                      :balance-ms (parse-interval balance-ms)
                                      :orders-ms  (parse-interval orders-ms)})))
    (resp/redirect "/dashboard")))

(defn websocket-handler [request]
  (if (get-in request [:session :identity])
    (httpkit/with-channel request ch
      (swap! connected-channels conj ch)
      (push-state-to-client! ch)
      (httpkit/on-close ch (fn [_] (swap! connected-channels disj ch)))
      (httpkit/on-receive ch (fn [_] nil)))
    {:status 401 :body "Unauthorized"}))

(declare stop-ib-runtime! start-ib-runtime!)

(defn- ib-ping-handler [_]
  (let [cfg (ib-config)]
    (ib-json-response
     {:ok        true
      :connected (= :connected (:connection @ui-state))
      :runtime   (some? @ib-runtime)
      :host      (:host cfg)
      :port      (:port cfg)
      :status    (name (:connection @ui-state))})))

(defn- ib-reconnect-handler [_]
  (stop-ib-runtime!)
  (let [ok? (start-ib-runtime!)]
    (ib-json-response {:ok ok?
                       :message (if ok? "Connected to IB" "Failed to connect to IB")})))

(defn- ib-refresh-balance-handler [_]
  (let [conn (ib-conn)]
    (if-not conn
      (ib-json-response {:ok false :message "Not connected to IB"})
      (let [result (async/<!! (ib.account/account-summary-snapshot!
                                conn {:group "All"
                                      :tags ["NetLiquidation" "BuyingPower"]
                                      :timeout-ms (ib-snapshot-timeout-ms)}))]
        (when (:ok result)
          (let [rows (summary-values->rows (:values result))]
            (when (seq rows)
              (swap! ui-state assoc :balance rows)
              (broadcast! {:type "portfolio-balance" :data rows}))))
        (ib-json-response {:ok    (:ok result)
                           :error (some-> (:error result) str)})))))

(defn- ib-refresh-positions-handler [_]
  (let [conn (ib-conn)]
    (if-not conn
      (ib-json-response {:ok false :message "Not connected to IB"})
      (let [result (async/<!! (ib.positions/positions-snapshot!
                                conn {:timeout-ms (ib-snapshot-timeout-ms)}))]
        (if (and (vector? result) (every? #(= :ib/position (:type %)) result))
          (let [rows (mapv to-position-row result)]
            (swap! ui-state assoc :positions rows)
            (broadcast! {:type "positions" :data rows})
            (ib-json-response {:ok true :rows rows}))
          (ib-json-response {:ok false :error (some-> (:reason result) str)}))))))

(defn- ib-refresh-orders-handler [_]
  (let [conn (ib-conn)]
    (if-not conn
      (ib-json-response {:ok false :message "Not connected to IB"})
      (do
        (set-orders-state! "loading" "Refreshing open orders...")
        (ib-json-response (refresh-open-orders! conn :all))))))

(defn- ib-quote-handler [request]
  (try
    (let [symbol   (or (get-in request [:params :symbol])   "AAPL")
          exchange (or (get-in request [:params :exchange]) "SMART")
          currency (or (get-in request [:params :currency]) "USD")
          conn     (ib-conn)]
      (if-not conn
        (ib-json-response {:ok false :message "Not connected to IB"})
        ;; Step 1: resolve contract via ib.contract/contract-details-snapshot!
        (let [cd-result (async/<!! (ib.contract/contract-details-snapshot!
                                     conn
                                     {:symbol   symbol
                                      :exchange exchange
                                      :currency currency}
                                     {:timeout-ms (ib-snapshot-timeout-ms)}))]
          (if-not (:ok cd-result)
            (ib-json-response cd-result)
            (let [contract (-> cd-result :contracts first :contract)]
              (if-not contract
                (ib-json-response {:ok false :error :no-results :symbol symbol})
                ;; Step 2: market data snapshot using the resolved contract map
                (ib-json-response
                 (async/<!! (ib.market-data/market-data-snapshot!
                              conn
                              (:symbol contract)
                              {:con-id (:conId contract)
                               :sec-type (:secType contract)
                               :exchange (or (:primaryExch contract)
                                             (:exchange contract)
                                             exchange)
                               :primary-exch (:primaryExch contract)
                               :currency (or (:currency contract) currency)
                               :timeout-ms (ib-snapshot-timeout-ms)})))))))))
    (catch Throwable t
      (ib-json-response {:ok false :error :exception
                         :message (str (class t) ": " (.getMessage t))}))))

(defn- ib-place-order-handler [request]
  (let [order-req (normalize-order-request (:params request))
        conn      (ib-conn)
        validation-error (validate-order-request order-req)]
    (cond
      validation-error
      (let [message validation-error]
        (set-order-submission! (assoc (submission-base order-req)
                                      :status "error"
                                      :message message))
        (ib-json-response (error-response message)))

      (not conn)
      (do
        (set-order-submission! (assoc (submission-base order-req)
                                      :status "error"
                                      :message "Not connected to IB"))
        (ib-json-response (error-response "Not connected to IB")))

      :else
      (try
        (set-orders-state! "loading" "Submitting order and refreshing open orders...")
        (set-order-submission! (assoc (submission-base order-req)
                                      :status "pending"
                                      :message "Submitting order..."))
        (let [cd-result (async/<!! (ib.contract/contract-details-snapshot!
                                     conn
                                     {:symbol (:symbol order-req)
                                      :exchange (:exchange order-req)
                                      :currency (:currency order-req)}
                                     {:timeout-ms (ib-snapshot-timeout-ms)}))]
          (if-not (:ok cd-result)
            (let [message (or (:message cd-result)
                              (some-> (:error cd-result) str)
                              "Contract lookup failed")]
              (set-order-submission! (assoc (submission-base order-req)
                                            :status "error"
                                            :message message))
              (set-orders-state! "error" message)
              (ib-json-response (assoc (error-response message)
                                       :error (:error cd-result))))
            (let [contract (-> cd-result :contracts first :contract)]
              (if-not contract
                (let [message (str "No IB contract match found for " (:symbol order-req))]
                  (set-order-submission! (assoc (submission-base order-req)
                                                :status "error"
                                                :message message))
                  (set-orders-state! "error" message)
                  (ib-json-response {:ok false
                                     :error :no-results
                                     :symbol (:symbol order-req)
                                     :message message}))
                (let [con-id (:conId contract)
                      primary-exch (or (:primaryExch contract) (:exchange contract))
                      order-id (ib.client/place-order!
                                conn
                                {:contract {:symbol (:symbol order-req)
                                            :sec-type "STK"
                                            :exchange (:exchange order-req)
                                            :primary-exch primary-exch
                                            :currency (:currency order-req)
                                            :con-id con-id}
                                 :order {:action (:action order-req)
                                         :order-type (:order-type order-req)
                                         :total-quantity (:quantity order-req)
                                         :lmt-price (:limit-price order-req)
                                         :tif (:tif order-req)
                                         :outside-rth (:outside-rth order-req)
                                         :transmit true}})
                      orders-result (refresh-open-orders! conn :all)]
                  (set-order-submission!
                   (assoc (submission-base order-req)
                          :status "success"
                          :message (if (:ok orders-result)
                                     "Order submitted and open orders refreshed."
                                     "Order submitted, but open orders could not be refreshed immediately.")
                          :order-id order-id
                          :refresh-ok (boolean (:ok orders-result))))
                  (ib-json-response {:ok true
                                     :order-id order-id
                                     :symbol (:symbol order-req)
                                     :action (:action order-req)
                                     :quantity (:quantity order-req)
                                     :order-type (:order-type order-req)
                                     :limit-price (:limit-price order-req)
                                     :tif (:tif order-req)
                                     :outside-rth (:outside-rth order-req)
                                     :con-id con-id
                                     :orders-refresh-ok (:ok orders-result)}))))))
        (catch Exception e
          (let [message (or (.getMessage e) "Order submission failed")]
            (set-order-submission! (assoc (submission-base order-req)
                                          :status "error"
                                          :message message))
            (set-orders-state! "error" message)
            (ib-json-response (error-response message))))))))

(defonce ^:private acct-summary-req-id (atom 700000))

(defn- ib-account-summary-handler [_request]
  (let [conn (ib-conn)]
    (if-not conn
      (ib-json-response {:ok false :message "Not connected to IB"})
      (let [rid        (swap! acct-summary-req-id inc)
            sub-ch     (ib.client/subscribe-events! conn {:buffer-size 128})
            timeout-ch (async/timeout 10000)]
        (try
          (ib.client/req-account-summary! conn {:req-id rid})
          (loop [rows []]
            (let [[val port] (async/alts!! [sub-ch timeout-ch])]
              (cond
                (= port timeout-ch)
                (ib-json-response {:ok false :error "timeout" :rows rows})

                (nil? val)
                (ib-json-response {:ok false :error "stream-closed" :rows rows})

                (and (= :ib/account-summary-end (:type val))
                     (= rid (:req-id val)))
                (ib-json-response {:ok true :rows rows})

                (and (= :ib/account-summary (:type val))
                     (= rid (:req-id val)))
                (recur (conj rows {:account  (:account val)
                                   :tag      (:tag val)
                                   :value    (:value val)
                                   :currency (:currency val)}))

                :else (recur rows))))
          (finally
            (try (ib.client/cancel-account-summary! conn rid) (catch Throwable _ nil))
            (ib.client/unsubscribe-events! conn sub-ch)
            (async/close! sub-ch)))))))

(defn- setup-redirect [] (resp/redirect "/setup"))

(defroutes app-routes
  (GET  "/setup"     _   (if (auth/needs-setup?)
                             (html-response (setup-page))
                             (resp/redirect "/login")))
  (POST "/setup"     req (or (setup-handler req) (resp/redirect "/login")))
  (GET  "/"          _   (if (auth/needs-setup?)
                             (setup-redirect)
                             (resp/redirect "/dashboard")))
  (GET  "/login"     _   (if (auth/needs-setup?)
                             (setup-redirect)
                             (html-response (login-page nil))))
  (POST "/login"     req (if (auth/needs-setup?)
                             (setup-redirect)
                             (login-handler req)))
  (GET  "/logout"    req (logout-handler req))
  (GET  "/dashboard" req (if (auth/needs-setup?)
                             (setup-redirect)
                             (if (get-in req [:session :identity])
                               (html-response (dashboard-page))
                               (resp/redirect "/login"))))
  (GET  "/settings"  req (if (auth/needs-setup?)
                             (setup-redirect)
                             (if (get-in req [:session :identity])
                               (html-response (settings-page))
                               (resp/redirect "/login"))))
  (POST "/settings"  req (if (auth/needs-setup?)
                             (setup-redirect)
                             (if (get-in req [:session :identity])
                               (settings-handler req)
                               {:status 401 :body "Unauthorized"})))
  (GET  "/ws"        req (if (auth/needs-setup?)
                             {:status 403 :body "Setup required"}
                             (websocket-handler req)))
  (POST "/ib/ping"              req (if (get-in req [:session :identity]) (ib-ping-handler req)              {:status 401 :body "Unauthorized"}))
  (POST "/ib/reconnect"         req (if (get-in req [:session :identity]) (ib-reconnect-handler req)         {:status 401 :body "Unauthorized"}))
  (POST "/ib/refresh/balance"   req (if (get-in req [:session :identity]) (ib-refresh-balance-handler req)   {:status 401 :body "Unauthorized"}))
  (POST "/ib/refresh/positions" req (if (get-in req [:session :identity]) (ib-refresh-positions-handler req) {:status 401 :body "Unauthorized"}))
  (POST "/ib/refresh/orders"    req (if (get-in req [:session :identity]) (ib-refresh-orders-handler req)    {:status 401 :body "Unauthorized"}))
  (POST "/ib/quote"             req (if (get-in req [:session :identity]) (ib-quote-handler req)             {:status 401 :body "Unauthorized"}))
  (POST "/ib/order"             req (if (get-in req [:session :identity]) (ib-place-order-handler req)       {:status 401 :body "Unauthorized"}))
  (POST "/ib/account-summary"   req (if (get-in req [:session :identity]) (ib-account-summary-handler req)   {:status 401 :body "Unauthorized"}))
  (route/resources "/")
  (route/not-found "Not found"))

(def app
  (wrap-defaults app-routes site-defaults))

(defn- fetch-with-fallback [f]
  (try
    (f)
    (catch Exception _
      nil)))

(defn- fiat-asset?
  "Returns true if the Kraken asset code represents a fiat currency.
  Kraken prefixes all fiat codes with 'Z' (e.g. ZUSD, ZEUR, ZGBP)."
  [asset-name]
  (= \Z (first asset-name)))

(defn- calculate-cash-usd
  "Computes total cash value in USD from all fiat balances in the Kraken balance map.

  Contract:
  - balance   : map of keyword asset-code → string amount (from kraken/request-balance)
  - usd-pairs : map of string asset-code → {:canonical pair-name ...} (from kraken/asset-usd-pairs)
  - ticker    : map of keyword pair-name → {:c [last-price ...] ...} (from kraken/request-ticker)

  Fiat assets are identified by a leading 'Z' in their Kraken code.
  - ZUSD is taken at face value.
  - Other fiat (e.g. ZEUR, ZGBP) are converted to USD using the Kraken ticker for
    their USD pair. Falls back to face value if no rate is available.

  Returns total cash as a double (0.0 when balance is empty or all amounts are zero)."
  [balance usd-pairs ticker]
  (reduce (fn [total [k v]]
            (let [asset  (name k)
                  amount (Double/parseDouble v)]
              (if (and (fiat-asset? asset) (pos? amount))
                (if (= asset "ZUSD")
                  (+ total amount)
                  (if-let [{:keys [canonical]} (get usd-pairs asset)]
                    (if-let [pair-data (get ticker (keyword canonical))]
                      (+ total (* amount (Double/parseDouble (first (:c pair-data)))))
                      (+ total amount))    ; no rate available — fall back to face value
                    (+ total amount)))     ; not in usd-pairs — fall back to face value
                total)))
          0.0
          (or balance {})))

(defn- calculate-positions-value
  "Computes the total USD value of non-fiat (crypto/token) positions in the
  Kraken balance map.

  Contract:
  - balance   : map of keyword asset-code → string amount (from kraken/request-balance)
  - usd-pairs : map of string asset-code → {:canonical pair-name ...} (from kraken/asset-usd-pairs)
  - ticker    : map of keyword pair-name → {:c [last-price ...] ...} (from kraken/request-ticker)

  Non-fiat assets are those whose Kraken code does NOT start with 'Z'
  (e.g. XXBT for BTC, XETH for ETH). Each asset is priced in USD via the
  Kraken ticker using its canonical USD pair. Assets with no known USD pair
  are excluded from the total.

  Returns total positions value as a double (0.0 when no non-fiat assets are found)."
  [balance usd-pairs ticker]
  (reduce (fn [total [k v]]
            (let [asset  (name k)
                  amount (Double/parseDouble v)]
              (if (and (not (fiat-asset? asset)) (pos? amount))
                (if-let [{:keys [canonical]} (get usd-pairs asset)]
                  (if-let [pair-data (get ticker (keyword canonical))]
                    (+ total (* amount (Double/parseDouble (first (:c pair-data)))))
                    total)
                  total)
                total)))
          0.0
          (or balance {})))

(defn- start-event-forwarder! [conn events-ch stop-ch]
  (async/go-loop []
    (let [[evt port] (async/alts! [events-ch stop-ch])]
      (cond
        (= port stop-ch)
        :stopped

        (nil? evt)
        (do
          (println "IB event channel closed")
          (set-disconnected!))

        (= :ib/open-order (:type evt))
        (do
          (remember-open-order! evt)
          (recur))

        (= :ib/order-status (:type evt))
        (do
          (swap! last-order-status assoc (:order-id evt) evt)
          (when (contains? @last-open-orders (:order-id evt))
            (sync-open-orders!))
          (recur))

        (= :ib/error (:type evt))
        (do
          (let [msg (or (:message evt) "IB Error")]
            (when-not (benign-ib-message? msg)
              (println "IB Error Event:" (select-keys evt [:code :message :request-id]))
              (broadcast! {:type "ib-error" :data {:message msg}}))
            (when (= :disconnected (:connection @ui-state))
              (set-disconnected!)))
          (recur))

        (= :ib/disconnected (:type evt))
        (do
          (println "IB disconnected event received")
          (set-disconnected!))

        :else
        (recur)))))

(defn- start-kraken-loop! [stop-ch]
  (let [kraken-usd-pairs (atom nil)
        last-kraken-ticker (atom 0)
        last-kraken-balance (atom 0)
        last-kraken-orders (atom 0)]
    (async/go-loop []
      (when (seq @connected-channels)
        (when (nil? @kraken-usd-pairs)
          (reset! kraken-usd-pairs (fetch-with-fallback kraken/asset-usd-pairs)))

        (let [now (System/currentTimeMillis)
              cfg @settings/settings
              kraken-enabled? (get-in cfg [:services :kraken :enabled])
              kraken-balance (when (and kraken-enabled?
                                        (get-in cfg [:services :kraken :balance-ms]))
                               (let [ms (get-in cfg [:services :kraken :balance-ms])]
                                 (when (> (- now @last-kraken-balance) ms)
                                   (let [b (fetch-with-fallback kraken/request-balance)]
                                     (reset! last-kraken-balance now)
                                     b))))
              kraken-orders (when (and kraken-enabled?
                                       (get-in cfg [:services :kraken :orders-ms]))
                              (let [ms (get-in cfg [:services :kraken :orders-ms])]
                                (when (> (- now @last-kraken-orders) ms)
                                  (let [o (fetch-with-fallback kraken/request-open-orders)]
                                    (reset! last-kraken-orders now)
                                    o))))
              kraken-ticker-pairs (if (and kraken-balance @kraken-usd-pairs)
                                    (->> kraken-balance
                                         (remove (fn [[k _]] (= (name k) "ZUSD")))
                                         (filter (fn [[_ v]] (pos? (Double/parseDouble v))))
                                         (keep (fn [[k _]] (get-in @kraken-usd-pairs [(name k) :altname])))
                                         (into ["XBTUSD"])
                                         distinct vec)
                                    ["XBTUSD"])
              kraken-ticker (when (and kraken-enabled?
                                       (get-in cfg [:services :kraken :ticker-ms]))
                              (let [ms (get-in cfg [:services :kraken :ticker-ms])]
                                (when (> (- now @last-kraken-ticker) ms)
                                  (let [t (fetch-with-fallback #(kraken/request-ticker kraken-ticker-pairs))]
                                    (reset! last-kraken-ticker now)
                                    t))))
              effective-ticker      (or kraken-ticker (:kraken-ticker @ui-state))
              kraken-portfolio-value (when (and kraken-balance @kraken-usd-pairs effective-ticker)
                                       (let [cash      (calculate-cash-usd
                                                          kraken-balance @kraken-usd-pairs effective-ticker)
                                             positions (calculate-positions-value
                                                          kraken-balance @kraken-usd-pairs effective-ticker)]
                                         {:total-value     (format "%.2f" (+ cash positions))
                                          :positions-value (format "%.2f" positions)
                                          :cash-usd        (format "%.2f" cash)}))]
          (when kraken-balance
            (swap! ui-state assoc :kraken-balance kraken-balance)
            (broadcast! {:type "kraken-balance" :data kraken-balance}))
          (when kraken-orders
            (swap! ui-state assoc :kraken-orders kraken-orders)
            (broadcast! {:type "kraken-orders" :data kraken-orders}))
          (when kraken-ticker
            (swap! ui-state assoc :kraken-ticker kraken-ticker)
            (broadcast! {:type "kraken-ticker" :data kraken-ticker}))
          (when kraken-portfolio-value
            (swap! ui-state assoc :kraken-portfolio-value kraken-portfolio-value)
            (broadcast! {:type "kraken-portfolio-value" :data kraken-portfolio-value}))))
      (let [[_ port] (async/alts! [(async/timeout (kraken-refresh-ms)) stop-ch])]
        (when-not (= port stop-ch)
          (recur))))))

(defn- start-snapshot-loop! [conn stop-ch]
  (let [balance-in-flight? (atom false)
        positions-in-flight? (atom false)
        orders-in-flight? (atom false)]
    (async/go-loop []
      (when (seq @connected-channels)
        (when (compare-and-set! balance-in-flight? false true)
          (async/go
            (try
              (let [result (async/<! (ib.account/account-summary-snapshot!
                                       conn
                                       {:group "All"
                                        :tags ["NetLiquidation" "BuyingPower"]
                                        :timeout-ms (ib-snapshot-timeout-ms)}))]
                (if (:ok result)
                  (let [rows (summary-values->rows (:values result))]
                    (if (seq rows)
                      (do
                        (clear-cell-error! :balance)
                        (swap! ui-state assoc :balance rows)
                        (broadcast! {:type "portfolio-balance" :data rows}))
                      (set-cell-error! :balance "NetLiquidation missing")))
                  (set-cell-error! :balance (if (balance-timeout? result)
                                              "IB Timeout"
                                              "IB Error"))))
              (finally
                (reset! balance-in-flight? false)))))

        (when (compare-and-set! positions-in-flight? false true)
          (async/go
            (try
              (let [result (async/<! (ib.positions/positions-snapshot!
                                       conn
                                       {:timeout-ms (ib-snapshot-timeout-ms)}))]
                (if (and (vector? result)
                         (every? #(= :ib/position (:type %)) result))
                  (let [rows (mapv to-position-row result)]
                    (clear-cell-error! :positions)
                    (swap! ui-state assoc :positions rows)
                    (broadcast! {:type "positions" :data rows}))
                  (set-cell-error! :positions (if (positions-timeout? result)
                                                "IB Timeout"
                                                "IB Error"))))
              (finally
                (reset! positions-in-flight? false)))))

        (when (compare-and-set! orders-in-flight? false true)
          (async/go
            (try
              (when (contains? #{"idle" "disconnected"} (get-in @ui-state [:orders-state :status]))
                (set-orders-state! "loading" "Loading open orders..."))
              (refresh-open-orders! conn :open)
              (finally
                (reset! orders-in-flight? false))))))
      (let [refresh-ms (ib-refresh-ms)]
        (let [[_ port] (async/alts! [(async/timeout refresh-ms) stop-ch])]
          (when-not (= port stop-ch)
            (recur)))))))

(defn- stop-ib-runtime! []
  (when-let [{:keys [conn events-ch stop-ch]} @ib-runtime]
    (when stop-ch
      (async/close! stop-ch))
    (when events-ch
      (try
        (ib.client/unsubscribe-events! conn events-ch)
        (catch Throwable _ nil))
      (async/close! events-ch))
    (when conn
      (try
        (ib.client/disconnect! conn)
        (catch Throwable _ nil))))
  (reset! ib-runtime nil)
  (set-disconnected!))

(defn- start-ib-runtime! []
  (let [cfg (ib-config)]
    (if-not (:enabled cfg)
      (do
        (println "IB runtime disabled in config/settings.edn")
        (set-disconnected!)
        false)
      (try
        (let [conn (ib.client/connect! cfg)
              events-ch (ib.client/subscribe-events! conn {:buffer-size 512})
              stop-ch (async/chan)]
          (reset! ib-runtime {:conn conn
                              :events-ch events-ch
                              :stop-ch stop-ch})
          (mark-connected!)
          (doseq [cell [:balance :positions :orders]]
            (clear-cell-error! cell))
          (set-orders-state! "loading" "Loading open orders...")
          (start-event-forwarder! conn events-ch stop-ch)
          (start-snapshot-loop! conn stop-ch)
          true)
        (catch Throwable t
          (println "Failed to connect to IB:" (.getMessage t))
          (set-disconnected!)
          (broadcast! {:type "ib-error" :data {:message (.getMessage t)}})
          false)))))

(defn- maybe-wrap-reload [handler]
  (try
    (require '[ring.middleware.reload :refer [wrap-reload]])
    (println "Dev mode: hot-reload enabled")
    ((resolve 'ring.middleware.reload/wrap-reload) handler)
    (catch Exception _
      handler)))

(defn start-server!
  "Loads settings, starts Kraken and IB runtimes, and starts the http-kit web server.
  Returns a stop function that shuts down all background loops."
  [port]
  (settings/load!)
  (let [kraken-ch (async/chan)]
    (start-kraken-loop! kraken-ch)
    (start-ib-runtime!)
    (let [stop-http (httpkit/run-server (maybe-wrap-reload app) {:port port})]
      (fn []
        (stop-http)
        (async/close! kraken-ch)
        (stop-ib-runtime!)))))
 

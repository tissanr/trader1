(ns trader1.web
  (:require [clojure.core.async :as async]
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
            [trader1.settings :as settings]))

(def refresh-ms 10000)
(def snapshot-timeout-ms 5000)

(defonce connected-channels (atom #{}))
(defonce ib-runtime (atom nil))
(defonce ui-state
  (atom {:balance nil
         :positions []
         :orders []
         :kraken-balance nil
         :kraken-orders nil
         :kraken-portfolio-value nil
         :kraken-ticker nil
         :errors {:balance nil :positions nil :orders nil}
         :connection :disconnected}))
(defonce last-order-status (atom {}))

(defn broadcast!
  "Sends a JSON-encoded payload map to all connected WebSocket clients."
  [payload-map]
  (let [msg (json/generate-string payload-map)]
    (doseq [ch @connected-channels]
      (httpkit/send! ch msg))))

(defn- set-cell-error! [cell message]
  (swap! ui-state assoc-in [:errors cell] message)
  (broadcast! {:type "cell-error" :data {:cell (name cell) :message message}}))

(defn- clear-cell-error! [cell]
  (swap! ui-state assoc-in [:errors cell] nil)
  (broadcast! {:type "cell-error" :data {:cell (name cell) :message nil}}))

(defn- set-disconnected! []
  (swap! ui-state assoc :connection :disconnected)
  (doseq [cell [:balance :positions :orders]]
    (set-cell-error! cell "Disconnected"))
  (broadcast! {:type "connection" :data {:status "disconnected"}}))

(defn- mark-connected! []
  (swap! ui-state assoc :connection :connected)
  (broadcast! {:type "connection" :data {:status "connected"}}))

(defn- parse-int [s default]
  (try
    (Integer/parseInt (str s))
    (catch Exception _
      default)))

(defn- ib-config []
  {:host (or (System/getenv "IB_HOST") "127.0.0.1")
   :port (parse-int (or (System/getenv "IB_PORT") "4002") 4002)
   :client-id 0
   :event-buffer-size 2048
   :overflow-strategy :sliding})

(defn- summary-values->rows [summary-values]
  (vec (for [[account tags] summary-values
             :let [nl (get tags "NetLiquidation")
                   bp (get tags "BuyingPower")]
             :when (or nl bp)]
         {:account         account
          :net-liquidation (some-> nl :value)
          :buying-power    (some-> bp :value)
          :currency        (or (some-> nl :currency) (some-> bp :currency) "USD")})))

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

(defn- order-status-for [order-id]
  (get @last-order-status order-id {}))

(defn- to-order-row [order-event]
  (let [{:keys [order-id contract order order-state]} order-event
        status-from-stream (order-status-for order-id)]
    {:symbol (or (:symbol contract) "--")
     :action (or (:action order) "--")
     :order-type (or (:orderType order) "--")
     :quantity (or (:totalQuantity order) "--")
     :limit-price (:lmtPrice order)
     :status (or (:status-text status-from-stream)
                 (:status order-state)
                 "--")
     :filled (:filled status-from-stream)
     :remaining (:remaining status-from-stream)}))

(defn- to-position-row [position-event]
  (let [{:keys [contract position avg-cost]} position-event]
    {:symbol (or (:symbol contract) "--")
     :sec-type (or (:secType contract) "--")
     :currency (or (:currency contract) "--")
     :position (or position "--")
     :avg-cost (or avg-cost "--")}))

(defn- push-state-to-client! [ch]
  (let [{:keys [balance positions orders errors connection
                kraken-balance kraken-orders kraken-portfolio-value kraken-ticker]} @ui-state]
    (httpkit/send! ch (json/generate-string {:type "connection"
                                             :data {:status (name connection)}}))
    (httpkit/send! ch (json/generate-string {:type "kraken-balance"
                                             :data kraken-balance}))
    (httpkit/send! ch (json/generate-string {:type "kraken-orders"
                                             :data kraken-orders}))
    (httpkit/send! ch (json/generate-string {:type "kraken-portfolio-value"
                                             :data kraken-portfolio-value}))
    (httpkit/send! ch (json/generate-string {:type "kraken-ticker"
                                             :data kraken-ticker}))
    (httpkit/send! ch (json/generate-string {:type "portfolio-balance"
                                             :data balance}))
    (httpkit/send! ch (json/generate-string {:type "positions"
                                             :data positions}))
    (httpkit/send! ch (json/generate-string {:type "orders"
                                             :data orders}))
    (doseq [[cell message] errors]
      (httpkit/send! ch (json/generate-string {:type "cell-error"
                                               :data {:cell (name cell)
                                                      :message message}})))))

(defn- ib-json-response [body]
  {:status 200
   :headers {"Content-Type" "application/json; charset=utf-8"}
   :body (json/generate-string body)})

(defn- ib-conn [] (:conn @ib-runtime))

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
  (let [cfg @settings/settings]
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
    (settings/save! {:ticker-ms  (parse-interval ticker-ms)
                     :balance-ms (parse-interval balance-ms)
                     :orders-ms  (parse-interval orders-ms)})
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
                                      :timeout-ms snapshot-timeout-ms}))]
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
                                conn {:timeout-ms snapshot-timeout-ms}))]
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
      (let [result (async/<!! (ib.orders/open-orders-snapshot!
                                conn {:mode :all :timeout-ms snapshot-timeout-ms}))]
        (if (:ok result)
          (let [rows (mapv to-order-row (:orders result))]
            (swap! ui-state assoc :orders rows)
            (broadcast! {:type "orders" :data rows})
            (ib-json-response {:ok true :rows rows}))
          (ib-json-response {:ok false :error (some-> (:error result) str)}))))))

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
                                     {:timeout-ms snapshot-timeout-ms}))]
          (if-not (:ok cd-result)
            (ib-json-response cd-result)
            (let [contract (-> cd-result :contracts first :contract)]
              (if-not contract
                (ib-json-response {:ok false :error :no-results :symbol symbol})
                ;; Step 2: market data snapshot using the resolved contract map
                (ib-json-response (async/<!! (ib.market-data/contract-details-snapshot!
                                               conn
                                               (:symbol contract)
                                               (assoc contract :timeout-ms snapshot-timeout-ms))))))))))
    (catch Throwable t
      (ib-json-response {:ok false :error :exception
                         :message (str (class t) ": " (.getMessage t))}))))

(defn- ib-place-order-handler [request]
  (let [symbol   (or (get-in request [:params :symbol])   "AAPL")
        exchange (or (get-in request [:params :exchange]) "SMART")
        currency (or (get-in request [:params :currency]) "USD")
        action   (or (get-in request [:params :action])   "BUY")
        quantity (Long/parseLong (or (get-in request [:params :quantity]) "1"))
        conn     (ib-conn)]
    (if-not conn
      (ib-json-response {:ok false :message "Not connected to IB"})
      (try
        ;; Step 1: resolve conId via ib.contract/contract-details-snapshot!
        (let [cd-result (async/<!! (ib.contract/contract-details-snapshot!
                                     conn
                                     {:symbol   symbol
                                      :exchange exchange
                                      :currency currency}
                                     {:timeout-ms snapshot-timeout-ms}))]
          (if-not (:ok cd-result)
            (ib-json-response cd-result)
            (let [contract (-> cd-result :contracts first :contract)]
              (if-not contract
                (ib-json-response {:ok false :error :no-results :symbol symbol})
                ;; Step 2: place order using resolved conId and primary exchange
                (let [con-id       (:conId contract)
                      primary-exch (:exchange contract)
                      order-id     (ib.client/place-order!
                                     conn
                                     {:contract {:symbol       symbol
                                                 :sec-type     "STK"
                                                 :exchange     "SMART"
                                                 :primary-exch primary-exch
                                                 :currency     currency
                                                 :con-id       con-id}
                                      :order    {:action         action
                                                 :order-type     "MKT"
                                                 :total-quantity quantity
                                                 :transmit       true}})]
                  (ib-json-response {:ok true :order-id order-id
                                     :symbol symbol :action action :quantity quantity
                                     :con-id con-id}))))))
        (catch Exception e
          (ib-json-response {:ok false :message (.getMessage e)}))))))

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

(defroutes app-routes
  (GET  "/"          _   (resp/redirect "/dashboard"))
  (GET  "/login"     _   (html-response (login-page nil)))
  (POST "/login"     req (login-handler req))
  (GET  "/logout"    req (logout-handler req))
  (GET  "/dashboard" req (if (get-in req [:session :identity])
                             (html-response (dashboard-page))
                             (resp/redirect "/login")))
  (GET  "/settings"  req (if (get-in req [:session :identity])
                             (html-response (settings-page))
                             (resp/redirect "/login")))
  (POST "/settings"  req (if (get-in req [:session :identity])
                             (settings-handler req)
                             {:status 401 :body "Unauthorized"}))
  (GET  "/ws"        req (websocket-handler req))
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

        (= :ib/order-status (:type evt))
        (do
          (swap! last-order-status assoc (:order-id evt) evt)
          (recur))

        (= :ib/error (:type evt))
        (do
          (println "IB Error Event:" (select-keys evt [:code :message :request-id]))
          (let [msg (or (:message evt) "IB Error")]
            (broadcast! {:type "ib-error" :data {:message msg}})
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
              kraken-balance (when-let [ms (:balance-ms cfg)]
                               (when (> (- now @last-kraken-balance) ms)
                                 (let [b (fetch-with-fallback kraken/request-balance)]
                                   (reset! last-kraken-balance now)
                                   b)))
              kraken-orders (when-let [ms (:orders-ms cfg)]
                              (when (> (- now @last-kraken-orders) ms)
                                (let [o (fetch-with-fallback kraken/request-open-orders)]
                                  (reset! last-kraken-orders now)
                                  o)))
              kraken-ticker-pairs (if (and kraken-balance @kraken-usd-pairs)
                                    (->> kraken-balance
                                         (remove (fn [[k _]] (= (name k) "ZUSD")))
                                         (filter (fn [[_ v]] (pos? (Double/parseDouble v))))
                                         (keep (fn [[k _]] (get-in @kraken-usd-pairs [(name k) :altname])))
                                         (into ["XBTUSD"])
                                         distinct vec)
                                    ["XBTUSD"])
              kraken-ticker (when-let [ms (:ticker-ms cfg)]
                              (when (> (- now @last-kraken-ticker) ms)
                                (let [t (fetch-with-fallback #(kraken/request-ticker kraken-ticker-pairs))]
                                  (reset! last-kraken-ticker now)
                                  t)))
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
      (let [[_ port] (async/alts! [(async/timeout refresh-ms) stop-ch])]
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
                                        :timeout-ms snapshot-timeout-ms}))]
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
                                       {:timeout-ms snapshot-timeout-ms}))]
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
              (let [result (async/<! (ib.orders/open-orders-snapshot!
                                       conn
                                       {:mode :open
                                        :timeout-ms snapshot-timeout-ms}))]
                (if (:ok result)
                  (let [rows (mapv to-order-row (:orders result))]
                    (clear-cell-error! :orders)
                    (swap! ui-state assoc :orders rows)
                    (broadcast! {:type "orders" :data rows}))
                  (set-cell-error! :orders (if (orders-timeout? result)
                                             "IB Timeout"
                                             "IB Error"))))
              (finally
                (reset! orders-in-flight? false))))))
      (let [[_ port] (async/alts! [(async/timeout refresh-ms) stop-ch])]
        (when-not (= port stop-ch)
          (recur))))))

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
        (start-event-forwarder! conn events-ch stop-ch)
        (start-snapshot-loop! conn stop-ch)
        true)
      (catch Throwable t
        (println "Failed to connect to IB:" (.getMessage t))
        (set-disconnected!)
        (broadcast! {:type "ib-error" :data {:message (.getMessage t)}})
        false))))

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
 
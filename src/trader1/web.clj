(ns trader1.web
  (:require [clojure.core.async :as async]
            [compojure.core :refer [defroutes GET POST]]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring.util.anti-forgery :refer [anti-forgery-field]]
            [ring.util.response :as resp]
            [hiccup.core :refer [html]]
            [hiccup.page :refer [html5 include-css include-js]]
            [cheshire.core :as json]
            [org.httpkit.server :as httpkit]
            [ib.account :as ib.account]
            [ib.client :as ib.client]
            [ib.open-orders :as ib.orders]
            [ib.positions :as ib.positions]
            [trader1.auth :as auth]
            [trader1.settings :as settings]))

(def refresh-ms 10000)
(def snapshot-timeout-ms 5000)

(defonce connected-channels (atom #{}))
(defonce ib-runtime (atom nil))
(defonce ui-state
  (atom {:balance nil
         :positions []
         :orders []
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
   :port (parse-int (or (System/getenv "IB_PORT") "7497") 7497)
   :client-id 0
   :event-buffer-size 2048
   :overflow-strategy :sliding})

(defn- extract-net-liquidation [summary-values]
  (some (fn [[_account tags]]
          (when-let [entry (get tags "NetLiquidation")]
            {:value (:value entry)
             :currency (:currency entry)}))
        summary-values))

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
  (let [{:keys [balance positions orders errors connection]} @ui-state]
    (httpkit/send! ch (json/generate-string {:type "connection"
                                             :data {:status (name connection)}}))
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
     [:title "Trader1 - Dashboard"]
     (include-css "/style.css")]
    [:body
     [:header
      [:h1 "Trader1"]
      [:nav
       [:a {:href "/settings"} "Settings"]
       [:a {:href "/logout"} "Logout"]]]
     [:main#dashboard-grid
      [:section#portfolio-balance-cell
       [:h2 "Portfolio Balance (USD)"]
       [:p.value#portfolio-balance-value "--"]]
      [:section#dashboard-empty-cell {:aria-hidden "true"}
       [:h2 ""]]
      [:section#positions-cell
       [:h2 "Positionen"]
       [:table.data-table
        [:thead
         [:tr
          [:th "Symbol"]
          [:th "SecType"]
          [:th "Currency"]
          [:th "Position"]
          [:th "AvgCost"]]]
        [:tbody#positions-body
         [:tr [:td {:colspan "5" :class "empty"} "Connecting..."]]]]]
      [:section#orders-cell
       [:h2 "Offene Orders"]
       [:table.data-table
        [:thead
         [:tr
          [:th "Symbol"]
          [:th "Action"]
          [:th "OrderType"]
          [:th "Quantity"]
          [:th "LimitPrice"]
          [:th "Status"]
          [:th "Filled"]
          [:th "Remaining"]]]
        [:tbody#orders-body
         [:tr [:td {:colspan "8" :class "empty"} "Connecting..."]]]]]]
     (include-js "/app.js")]))

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
  (route/resources "/")
  (route/not-found "Not found"))

(def app
  (wrap-defaults app-routes site-defaults))

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
                                        :tags ["NetLiquidation"]
                                        :timeout-ms snapshot-timeout-ms}))]
                (if (:ok result)
                  (if-let [{:keys [value currency]} (extract-net-liquidation (:values result))]
                    (do
                      (clear-cell-error! :balance)
                      (let [payload {:value value :currency (or currency "USD")}]
                        (swap! ui-state assoc :balance payload)
                        (broadcast! {:type "portfolio-balance" :data payload})))
                    (set-cell-error! :balance "NetLiquidation missing"))
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

(defn start-server!
  "Loads settings, starts IB runtime, and starts the http-kit web server.
  Returns a stop function that also disconnects from IB."
  [port]
  (settings/load!)
  (start-ib-runtime!)
  (let [stop-http (httpkit/run-server app {:port port})]
    (fn []
      (stop-http)
      (stop-ib-runtime!))))

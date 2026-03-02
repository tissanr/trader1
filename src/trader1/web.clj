(ns trader1.web
  (:require [compojure.core :refer [defroutes GET POST]]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring.util.anti-forgery :refer [anti-forgery-field]]
            [ring.util.response :as resp]
            [hiccup.core :refer [html]]
            [hiccup.page :refer [html5 include-css include-js]]
            [cheshire.core :as json]
            [org.httpkit.server :as httpkit]
            [trader1.auth :as auth]
            [trader1.kraken :as kraken]
            [trader1.settings :as settings]))

;; --- WebSocket channel registry ---

(defonce connected-channels (atom #{}))

(defn broadcast!
  "Sends a JSON-encoded payload map to all connected WebSocket clients."
  [payload-map]
  (let [msg (json/generate-string payload-map)]
    (doseq [ch @connected-channels]
      (httpkit/send! ch msg))))

;; --- HTML templates ---

(defn login-page [error-msg]
  (html5
    [:head
     [:meta {:charset "utf-8"}]
     [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
     [:title "Trader1 — Login"]
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
     [:title "Trader1 — Dashboard"]
     (include-css "/style.css")]
    [:body
     [:header
      [:h1 "Trader1"]
      [:nav
       [:a {:href "/settings"} "Settings"]
       [:a {:href "/logout"} "Logout"]]]
     [:main
      [:section#portfolio
       [:h2 "Total Portfolio Value"]
       [:p.portfolio-total [:span#portfolio-total "--"] " USD"]]
      [:section#ticker
       [:h2 "BTC / USD"]
       [:p.price [:span#ticker-last "--"]]
       [:div.row
        [:span.label "Ask"] [:span#ticker-ask "--"]
        [:span.label "Bid"] [:span#ticker-bid "--"]]
       [:div.row
        [:span.label "Vol 24h"] [:span#ticker-vol "--"]]]
      [:section#balance
       [:h2 "Account Balance"]
       [:ul#balance-list [:li "Connecting..."]]]
      [:section#orders
       [:h2 "Open Orders"]
       [:ul#orders-list [:li "Connecting..."]]]]
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
       [:title "Trader1 — Settings"]
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

;; --- Route handlers ---

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
    (resp/redirect "/settings")))

(defn websocket-handler [request]
  (if (get-in request [:session :identity])
    (httpkit/with-channel request ch
      (swap! connected-channels conj ch)
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

;; --- Background broadcaster ---

(defn- fetch-with-fallback [f]
  (try (f) (catch Exception _ nil)))

(defn- compute-portfolio-usd [balance usd-pairs ticker]
  (when (and balance usd-pairs ticker)
    (reduce (fn [total [k v]]
              (let [asset  (name k)
                    amount (Double/parseDouble v)]
                (if (zero? amount)
                  total
                  (if (= asset "ZUSD")
                    (+ total amount)
                    (if-let [{:keys [canonical]} (get usd-pairs asset)]
                      (if-let [pair-data (get ticker (keyword canonical))]
                        (+ total (* amount (Double/parseDouble (first (:c pair-data)))))
                        total)
                      total)))))
            0.0
            balance)))

(defn start-broadcaster!
  "Spawns a background thread that pushes live data to all WebSocket clients.
  Intervals are read from the settings atom each loop; nil means manual (no auto-fetch)."
  []
  (let [last-ticker  (atom 0)
        last-balance (atom 0)
        last-orders  (atom 0)
        usd-pairs    (atom nil)]
    (future
      (loop []
        (try
          (when (seq @connected-channels)
            (when (nil? @usd-pairs)
              (reset! usd-pairs (fetch-with-fallback kraken/asset-usd-pairs)))
            (let [now (System/currentTimeMillis)
                  cfg @settings/settings
                  balance (when-let [ms (:balance-ms cfg)]
                            (when (> (- now @last-balance) ms)
                              (let [b (fetch-with-fallback kraken/request-balance)]
                                (reset! last-balance now) b)))
                  orders  (when-let [ms (:orders-ms cfg)]
                            (when (> (- now @last-orders) ms)
                              (let [o (fetch-with-fallback kraken/request-open-orders)]
                                (reset! last-orders now) o)))
                  ticker-pairs (if (and balance @usd-pairs)
                                 (->> balance
                                      (remove (fn [[k _]] (= (name k) "ZUSD")))
                                      (filter (fn [[_ v]] (pos? (Double/parseDouble v))))
                                      (keep   (fn [[k _]] (get-in @usd-pairs [(name k) :altname])))
                                      (into ["XBTUSD"])
                                      distinct vec)
                                 ["XBTUSD"])
                  ticker  (when-let [ms (:ticker-ms cfg)]
                            (when (> (- now @last-ticker) ms)
                              (let [t (fetch-with-fallback #(kraken/request-ticker ticker-pairs))]
                                (reset! last-ticker now) t)))
                  portfolio-usd (compute-portfolio-usd balance @usd-pairs ticker)]
              (when ticker        (broadcast! {:type "ticker"          :data ticker}))
              (when balance       (broadcast! {:type "balance"         :data balance}))
              (when orders        (broadcast! {:type "orders"          :data orders}))
              (when portfolio-usd (broadcast! {:type "portfolio-value"
                                               :data {:total_usd (format "%.2f" portfolio-usd)}}))))
          (catch Exception e
            (println "Broadcaster error:" (.getMessage e))))
        (Thread/sleep 5000)
        (recur)))))

(defn start-server!
  "Loads settings, starts the background broadcaster, and starts the http-kit web server.
  Returns the stop function (call it to shut down)."
  [port]
  (settings/load!)
  (start-broadcaster!)
  (httpkit/run-server app {:port port}))

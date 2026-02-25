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
            [trader1.kraken :as kraken]))

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
      [:a {:href "/logout"} "Logout"]]
     [:main
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
  (GET  "/ws"        req (websocket-handler req))
  (route/resources "/")
  (route/not-found "Not found"))

(def app
  (wrap-defaults app-routes site-defaults))

;; --- Background broadcaster ---

(defn- fetch-with-fallback [f]
  (try (f) (catch Exception _ nil)))

(defn start-broadcaster!
  "Spawns a background thread that pushes live data to all WebSocket clients.
  Ticker: every 5s. Orders: every 15s. Balance: every 30s."
  []
  (let [last-balance (atom 0)
        last-orders  (atom 0)]
    (future
      (loop []
        (try
          (when (seq @connected-channels)
            (let [now (System/currentTimeMillis)
                  ticker  (fetch-with-fallback #(kraken/request-ticker ["XBTUSD"]))
                  balance (when (> (- now @last-balance) 30000)
                            (let [b (fetch-with-fallback kraken/request-balance)]
                              (reset! last-balance now)
                              b))
                  orders  (when (> (- now @last-orders) 15000)
                            (let [o (fetch-with-fallback kraken/request-open-orders)]
                              (reset! last-orders now)
                              o))]
              (when ticker  (broadcast! {:type "ticker"  :data ticker}))
              (when balance (broadcast! {:type "balance" :data balance}))
              (when orders  (broadcast! {:type "orders"  :data orders}))))
          (catch Exception e
            (println "Broadcaster error:" (.getMessage e))))
        (Thread/sleep 5000)
        (recur)))))

(defn start-server!
  "Starts the background broadcaster and the http-kit web server on port.
  Returns the stop function (call it to shut down)."
  [port]
  (start-broadcaster!)
  (httpkit/run-server app {:port port}))

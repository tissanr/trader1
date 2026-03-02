(ns trader1.web-test
  (:require [clojure.test :refer [deftest is testing]]
            [cheshire.core :as json]
            [org.httpkit.server :as httpkit]
            [trader1.auth :as auth]
            [trader1.kraken :as kraken]
            [trader1.settings :as settings]
            [trader1.web :refer [broadcast! connected-channels
                                  login-page dashboard-page
                                  settings-page settings-handler
                                  login-handler logout-handler
                                  websocket-handler app-routes
                                  start-broadcaster!]]))

;; --- login-page ---

(deftest login-page-test
  (testing "contains form, title and submit button"
    (let [html (login-page nil)]
      (is (string? html))
      (is (.contains html "Trader1"))
      (is (.contains html "Sign in"))
      (is (.contains html "action=\"/login\""))))
  (testing "renders error message when provided"
    (is (.contains (login-page "Invalid username or password.") "Invalid username or password.")))
  (testing "no error element when message is nil"
    (is (not (.contains (login-page nil) "class=\"error\"")))))

;; --- dashboard-page ---

(deftest dashboard-page-test
  (testing "contains all dashboard sections"
    (let [html (dashboard-page)]
      (is (.contains html "Trader1"))
      (is (.contains html "Total Portfolio Value"))
      (is (.contains html "BTC / USD"))
      (is (.contains html "Account Balance"))
      (is (.contains html "Open Orders"))))
  (testing "contains settings and logout links"
    (let [html (dashboard-page)]
      (is (.contains html "/settings"))
      (is (.contains html "/logout")))))

;; --- settings-page ---

(deftest settings-page-test
  (testing "renders all three interval selects"
    (let [html (settings-page)]
      (is (.contains html "Ticker"))
      (is (.contains html "Balance"))
      (is (.contains html "Orders"))
      (is (.contains html "ticker-ms"))
      (is (.contains html "balance-ms"))
      (is (.contains html "orders-ms"))))
  (testing "reflects current settings as selected options"
    (reset! settings/settings {:ticker-ms 300000 :balance-ms nil :orders-ms 15000})
    (try
      (let [html (settings-page)]
        (is (.contains html "Manual"))
        (is (.contains html "5 minutes")))
      (finally
        (reset! settings/settings settings/defaults))))
  (testing "contains save button and form action"
    (let [html (settings-page)]
      (is (.contains html "action=\"/settings\""))
      (is (.contains html "Save")))))

;; --- settings-handler ---

(deftest settings-handler-test
  (testing "saves parsed settings and redirects to /settings"
    (let [saved (atom nil)]
      (with-redefs [settings/save! (fn [s] (reset! saved s))]
        (let [resp (settings-handler {:params {:ticker-ms  "5000"
                                               :balance-ms "300000"
                                               :orders-ms  "manual"}})]
          (is (= 302 (:status resp)))
          (is (= "/settings" (get-in resp [:headers "Location"])))
          (is (= {:ticker-ms 5000 :balance-ms 300000 :orders-ms nil} @saved))))))
  (testing "handles all-manual settings"
    (let [saved (atom nil)]
      (with-redefs [settings/save! (fn [s] (reset! saved s))]
        (settings-handler {:params {:ticker-ms "manual" :balance-ms "manual" :orders-ms "manual"}})
        (is (= {:ticker-ms nil :balance-ms nil :orders-ms nil} @saved)))))
  (testing "handles all numeric settings"
    (let [saved (atom nil)]
      (with-redefs [settings/save! (fn [s] (reset! saved s))]
        (settings-handler {:params {:ticker-ms "600000" :balance-ms "600000" :orders-ms "600000"}})
        (is (= {:ticker-ms 600000 :balance-ms 600000 :orders-ms 600000} @saved))))))

;; --- login-handler ---

(deftest login-handler-test
  (testing "redirects to /dashboard and stores identity in session on success"
    (with-redefs [auth/authenticate (fn [_ _] {:username "admin"})]
      (let [resp (login-handler {:params {:username "admin" :password "correct"}})]
        (is (= 302 (:status resp)))
        (is (= "/dashboard" (get-in resp [:headers "Location"])))
        (is (= "admin" (get-in resp [:session :identity]))))))
  (testing "returns login page with error on failed auth"
    (with-redefs [auth/authenticate (fn [_ _] nil)]
      (let [resp (login-handler {:params {:username "admin" :password "wrong"}})]
        (is (= 200 (:status resp)))
        (is (.contains (:body resp) "Invalid username or password"))))))

;; --- logout-handler ---

(deftest logout-handler-test
  (testing "redirects to /login"
    (let [resp (logout-handler {})]
      (is (= 302 (:status resp)))
      (is (= "/login" (get-in resp [:headers "Location"])))))
  (testing "clears the session"
    (let [resp (logout-handler {:session {:identity "admin"}})]
      (is (nil? (:session resp))))))

;; --- websocket-handler ---

(deftest websocket-handler-test
  (testing "returns 401 when session has no identity"
    (is (= 401 (:status (websocket-handler {:session {}})))))
  (testing "returns 401 when no session at all"
    (is (= 401 (:status (websocket-handler {}))))))

;; --- app-routes ---

(def ^:private base-req {:params {} :session {} :headers {}})

(deftest app-routes-test
  (testing "GET / redirects to /dashboard"
    (let [resp (app-routes (assoc base-req :request-method :get :uri "/"))]
      (is (= 302 (:status resp)))
      (is (= "/dashboard" (get-in resp [:headers "Location"])))))
  (testing "GET /login returns login page"
    (let [resp (app-routes (assoc base-req :request-method :get :uri "/login"))]
      (is (= 200 (:status resp)))
      (is (.contains (:body resp) "Sign in"))))
  (testing "POST /login with valid credentials redirects to /dashboard"
    (with-redefs [auth/authenticate (fn [_ _] {:username "admin"})]
      (let [resp (app-routes (assoc base-req :request-method :post :uri "/login"
                                    :params {:username "admin" :password "correct"}))]
        (is (= 302 (:status resp)))
        (is (= "/dashboard" (get-in resp [:headers "Location"]))))))
  (testing "POST /login with invalid credentials returns login page with error"
    (with-redefs [auth/authenticate (fn [_ _] nil)]
      (let [resp (app-routes (assoc base-req :request-method :post :uri "/login"
                                    :params {:username "admin" :password "wrong"}))]
        (is (= 200 (:status resp)))
        (is (.contains (:body resp) "Invalid username or password")))))
  (testing "GET /dashboard without auth redirects to /login"
    (let [resp (app-routes (assoc base-req :request-method :get :uri "/dashboard"))]
      (is (= 302 (:status resp)))
      (is (= "/login" (get-in resp [:headers "Location"])))))
  (testing "GET /dashboard with auth returns dashboard page"
    (let [resp (app-routes (assoc base-req :request-method :get :uri "/dashboard"
                                  :session {:identity "admin"}))]
      (is (= 200 (:status resp)))
      (is (.contains (:body resp) "BTC / USD"))))
  (testing "GET /logout redirects to /login and clears session"
    (let [resp (app-routes (assoc base-req :request-method :get :uri "/logout"
                                  :session {:identity "admin"}))]
      (is (= 302 (:status resp)))
      (is (= "/login" (get-in resp [:headers "Location"])))
      (is (nil? (:session resp)))))
  (testing "GET /settings without auth redirects to /login"
    (let [resp (app-routes (assoc base-req :request-method :get :uri "/settings"))]
      (is (= 302 (:status resp)))
      (is (= "/login" (get-in resp [:headers "Location"])))))
  (testing "GET /settings with auth returns settings page"
    (let [resp (app-routes (assoc base-req :request-method :get :uri "/settings"
                                  :session {:identity "admin"}))]
      (is (= 200 (:status resp)))
      (is (.contains (:body resp) "Polling Intervals"))))
  (testing "POST /settings without auth returns 401"
    (let [resp (app-routes (assoc base-req :request-method :post :uri "/settings"))]
      (is (= 401 (:status resp)))))
  (testing "GET /ws without auth returns 401"
    (let [resp (app-routes (assoc base-req :request-method :get :uri "/ws"))]
      (is (= 401 (:status resp)))))
  (testing "unknown route returns 404"
    (let [resp (app-routes (assoc base-req :request-method :get :uri "/no-such-page"))]
      (is (= 404 (:status resp))))))

;; --- broadcast! ---

(deftest broadcast-test
  (testing "sends JSON-encoded payload to all connected channels"
    (let [sent    (atom [])
          fake-ch1 (Object.)
          fake-ch2 (Object.)]
      (reset! connected-channels #{fake-ch1 fake-ch2})
      (try
        (with-redefs [httpkit/send! (fn [ch msg] (swap! sent conj {:ch ch :msg msg}))]
          (broadcast! {:type "ticker" :data {:price "50000"}})
          (is (= 2 (count @sent)))
          (is (= #{fake-ch1 fake-ch2} (set (map :ch @sent))))
          (let [parsed (map #(json/parse-string (:msg %)) @sent)]
            (is (every? #(= "ticker" (get % "type")) parsed))
            (is (every? #(= {"price" "50000"} (get % "data")) parsed))))
        (finally
          (reset! connected-channels #{})))))
  (testing "does nothing when no channels are connected"
    (let [sent (atom [])]
      (reset! connected-channels #{})
      (with-redefs [httpkit/send! (fn [_ msg] (swap! sent conj msg))]
        (broadcast! {:type "ticker" :data {}})
        (is (empty? @sent))))))

;; --- start-broadcaster! ---
;;
;; The broadcaster loop runs its first iteration immediately (Thread/sleep comes
;; *after* the work).  last-balance and last-orders start at 0, so
;; (currentTimeMillis - 0) > 30000/15000 is always true on the first pass —
;; all three data types are fetched.  Promises are used for coordination so
;; tests complete as soon as the iteration finishes, without artificial sleeps.

(deftest start-broadcaster-no-channels-test
  (testing "does not fetch or broadcast when no channels are connected"
    (let [ticker-called (promise)]
      (reset! connected-channels #{})
      (try
        (with-redefs [kraken/request-ticker (fn [_] (deliver ticker-called true) {})]
          (start-broadcaster!)
          ;; If the channel guard works, ticker is never called within 300 ms.
          (is (= :not-called (deref ticker-called 300 :not-called))))
        (finally
          (reset! connected-channels #{}))))))

(deftest start-broadcaster-all-types-test
  (testing "broadcasts ticker, balance, orders and portfolio-value on the first iteration"
    (let [broadcast-types (atom #{})
          all-done        (promise)
          fake-ch         (Object.)]
      (reset! connected-channels #{fake-ch})
      (reset! settings/settings settings/defaults)
      (try
        (with-redefs [kraken/asset-usd-pairs     (fn [] {"XXBT" {:altname "XBTUSD" :canonical "XXBTZUSD"}})
                      kraken/request-ticker      (fn [_] {:XXBTZUSD {:c ["50000" "1"]}})
                      kraken/request-balance     (fn [] {"XXBT" "1.5"})
                      kraken/request-open-orders (fn [] {:open {}})
                      broadcast! (fn [payload]
                                   (swap! broadcast-types conj (:type payload))
                                   (when (= 4 (count @broadcast-types))
                                     (deliver all-done true)))]
          (start-broadcaster!)
          (is (= true (deref all-done 1000 :timeout)))
          (is (= #{"ticker" "balance" "orders" "portfolio-value"} @broadcast-types)))
        (finally
          (reset! connected-channels #{})
          (reset! settings/settings settings/defaults))))))

(deftest start-broadcaster-payload-structure-test
  (testing "ticker broadcast wraps the kraken result under :data with type \"ticker\""
    (let [ticker-data     {:XBTUSD {:a ["50000"] :b ["49999"]}}
          received-ticker (promise)
          fake-ch         (Object.)]
      (reset! connected-channels #{fake-ch})
      (reset! settings/settings settings/defaults)
      (try
        (with-redefs [kraken/asset-usd-pairs     (fn [] {})
                      kraken/request-ticker      (fn [_] ticker-data)
                      kraken/request-balance     (fn [] {})
                      kraken/request-open-orders (fn [] {})
                      broadcast! (fn [payload]
                                   (when (= "ticker" (:type payload))
                                     (deliver received-ticker payload)))]
          (start-broadcaster!)
          (let [result (deref received-ticker 1000 nil)]
            (is (some? result))
            (is (= {:type "ticker" :data ticker-data} result))))
        (finally
          (reset! connected-channels #{})
          (reset! settings/settings settings/defaults))))))

(deftest start-broadcaster-fetch-exception-test
  (testing "exception thrown by a fetcher is swallowed; other types still broadcast"
    (let [broadcasts (atom [])
          done       (promise)
          fake-ch    (Object.)]
      (reset! connected-channels #{fake-ch})
      (reset! settings/settings settings/defaults)
      (try
        (with-redefs [kraken/asset-usd-pairs     (fn [] {"XXBT" {:altname "XBTUSD" :canonical "XXBTZUSD"}})
                      kraken/request-ticker      (fn [_] (throw (Exception. "ticker API down")))
                      kraken/request-balance     (fn [] {"XXBT" "1.0"})
                      kraken/request-open-orders (fn [] {:open {}})
                      broadcast! (fn [payload]
                                   (swap! broadcasts conj payload)
                                   (when (= 2 (count @broadcasts))
                                     (deliver done true)))]
          (start-broadcaster!)
          (is (= true (deref done 1000 :timeout)))
          (is (not-any? #(= "ticker" (:type %)) @broadcasts))
          (is (not-any? #(= "portfolio-value" (:type %)) @broadcasts))
          (is (some    #(= "balance" (:type %)) @broadcasts))
          (is (some    #(= "orders"  (:type %)) @broadcasts)))
        (finally
          (reset! connected-channels #{})
          (reset! settings/settings settings/defaults))))))

(deftest start-broadcaster-portfolio-value-test
  (testing "broadcasts portfolio-value with computed total_usd"
    (let [portfolio-payload (promise)
          fake-ch           (Object.)]
      (reset! connected-channels #{fake-ch})
      (reset! settings/settings settings/defaults)
      (try
        (with-redefs [kraken/asset-usd-pairs     (fn [] {"XXBT" {:altname "XBTUSD" :canonical "XXBTZUSD"}})
                      kraken/request-balance     (fn [] {"XXBT" "0.5" "ZUSD" "10000.0"})
                      kraken/request-open-orders (fn [] {:open {}})
                      kraken/request-ticker      (fn [_] {:XXBTZUSD {:c ["50000.00" "1"]}})
                      broadcast! (fn [payload]
                                   (when (= "portfolio-value" (:type payload))
                                     (deliver portfolio-payload payload)))]
          (start-broadcaster!)
          (let [result (deref portfolio-payload 1000 nil)]
            (is (some? result))
            (is (= "35000.00" (get-in result [:data :total_usd])))))
        (finally
          (reset! connected-channels #{})
          (reset! settings/settings settings/defaults))))))

(deftest start-broadcaster-manual-interval-test
  (testing "does not auto-fetch ticker when ticker-ms is nil"
    (let [ticker-called (promise)
          fake-ch       (Object.)]
      (reset! connected-channels #{fake-ch})
      (reset! settings/settings {:ticker-ms nil :balance-ms 30000 :orders-ms 15000})
      (try
        (with-redefs [kraken/asset-usd-pairs     (fn [] {})
                      kraken/request-ticker      (fn [_] (deliver ticker-called true) {})
                      kraken/request-balance     (fn [] {})
                      kraken/request-open-orders (fn [] {:open {}})
                      broadcast! (fn [_] nil)]
          (start-broadcaster!)
          (is (= :not-called (deref ticker-called 500 :not-called))))
        (finally
          (reset! connected-channels #{})
          (reset! settings/settings settings/defaults))))))

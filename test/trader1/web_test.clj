(ns trader1.web-test
  (:require [clojure.core.async :as async]
            [clojure.test :refer [deftest is testing]]
            [cheshire.core :as json]
            [org.httpkit.server :as httpkit]
            [trader1.auth :as auth]
            [trader1.settings :as settings]
            [trader1.web :as web]
            [trader1.web :refer [broadcast! connected-channels
                                  login-page dashboard-page
                                  settings-page settings-handler
                                  login-handler logout-handler
                                  websocket-handler app-routes]]))

(deftest login-page-test
  (testing "contains form, title and submit button"
    (let [html (login-page nil)]
      (is (string? html))
      (is (.contains html "Trader1"))
      (is (.contains html "Sign in"))
      (is (.contains html "action=\"/login\""))))
  (testing "renders error message when provided"
    (is (.contains (login-page "Invalid username or password.") "Invalid username or password."))))

(deftest dashboard-page-test
  (let [html (dashboard-page)]
    (is (.contains html "<div id=\"app\"></div>"))
    (is (.contains html "<meta content=\"width=device-width, initial-scale=1\" name=\"viewport\">"))
    (is (.contains html "/style.css"))
    (is (.contains html "/js/main.js"))))

(deftest settings-page-test
  (testing "renders all three interval selects"
    (let [html (settings-page)]
      (is (.contains html "Ticker"))
      (is (.contains html "Balance"))
      (is (.contains html "Orders"))
      (is (.contains html "ticker-ms"))
      (is (.contains html "balance-ms"))
      (is (.contains html "orders-ms")))))

(deftest settings-handler-test
  (testing "saves parsed settings and redirects to /dashboard"
    (let [saved (atom nil)]
      (reset! settings/settings settings/defaults)
      (with-redefs [settings/save! (fn [s] (reset! saved s))]
        (let [resp (settings-handler {:params {:ticker-ms  "5000"
                                               :balance-ms "300000"
                                               :orders-ms  "manual"}})]
          (is (= 302 (:status resp)))
          (is (= "/dashboard" (get-in resp [:headers "Location"])))
          (is (= {:server {:port 3001}
                  :services {:ib {:enabled true
                                  :host "127.0.0.1"
                                  :port 4002
                                  :client-id 0
                                  :snapshot-timeout-ms 5000
                                  :refresh-ms 10000
                                  :event-buffer-size 2048
                                  :overflow-strategy :sliding}
                             :kraken {:enabled true
                                      :refresh-ms 10000
                                      :ticker-ms 5000
                                      :balance-ms 300000
                                      :orders-ms nil}}}
                 @saved)))))))

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

(deftest logout-handler-test
  (let [resp (logout-handler {:session {:identity "admin"}})]
    (is (= 302 (:status resp)))
    (is (= "/login" (get-in resp [:headers "Location"])))
    (is (nil? (:session resp)))))

(deftest websocket-handler-test
  (is (= 401 (:status (websocket-handler {:session {}}))))
  (is (= 401 (:status (websocket-handler {})))))

(def ^:private base-req {:params {} :session {} :headers {}})

(deftest app-routes-test
  (testing "GET / redirects to /dashboard"
    (let [resp (app-routes (assoc base-req :request-method :get :uri "/"))]
      (is (= 302 (:status resp)))
      (is (= "/dashboard" (get-in resp [:headers "Location"])))))
  (testing "GET /dashboard without auth redirects to /login"
    (let [resp (app-routes (assoc base-req :request-method :get :uri "/dashboard"))]
      (is (= 302 (:status resp)))
      (is (= "/login" (get-in resp [:headers "Location"])))))
  (testing "GET /dashboard with auth returns dashboard page"
    (let [resp (app-routes (assoc base-req :request-method :get :uri "/dashboard"
                                  :session {:identity "admin"}))]
      (is (= 200 (:status resp)))
      (is (.contains (:body resp) "<div id=\"app\"></div>"))
      (is (.contains (:body resp) "/js/main.js"))))
  (testing "POST /settings without auth returns 401"
    (let [resp (app-routes (assoc base-req :request-method :post :uri "/settings"))]
      (is (= 401 (:status resp))))))

(deftest broadcast-test
  (testing "sends JSON-encoded payload to all connected channels"
    (let [sent    (atom [])
          fake-ch1 (Object.)
          fake-ch2 (Object.)]
      (reset! connected-channels #{fake-ch1 fake-ch2})
      (try
        (with-redefs [httpkit/send! (fn [ch msg] (swap! sent conj {:ch ch :msg msg}))]
          (broadcast! {:type "connection" :data {:status "connected"}})
          (is (= 2 (count @sent)))
          (let [parsed (map #(json/parse-string (:msg %)) @sent)]
            (is (every? #(= "connection" (get % "type")) parsed))
            (is (every? #(= "connected" (get-in % ["data" "status"])) parsed))))
        (finally
          (reset! connected-channels #{})))))
  (testing "rejects malformed websocket payloads"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
          #"websocket payload for connection"
          (broadcast! {:type "connection" :data {:status "maybe"}})))))

(deftest to-order-row-test
  (testing "normalizes IB open order rows with broker identifiers and stream status"
    (reset! web/last-order-status
            {11 {:order-id 11
                 :status-text "Submitted"
                 :filled 2.0
                 :remaining 8.0}})
    (is (= {:order-id 11
            :account-id "DU123"
            :symbol "AAPL"
            :action "BUY"
            :order-type "LMT"
            :quantity 10.0
            :limit-price 150.25
            :status "Submitted"
            :filled 2.0
            :remaining 8.0}
           (#'web/to-order-row {:order-id 11
                                :account "DU123"
                                :contract {:symbol "AAPL"}
                                :order {:action "BUY"
                                        :orderType "LMT"
                                        :totalQuantity 10.0
                                        :lmtPrice 150.25}
                                :order-state {:status "PreSubmitted"}})))))

(deftest benign-ib-message-test
  (testing "suppresses routine IB farm connectivity chatter"
    (is (true? (#'web/benign-ib-message? "Sec-def data farm connection is broken:secdefil")))
    (is (true? (#'web/benign-ib-message? "Market data farm connection is OK:usfarm")))
    (is (false? (#'web/benign-ib-message? "Order rejected: insufficient margin")))))

(deftest ib-quote-handler-test
  (testing "requests market data with the resolved contract identifiers"
    (let [market-data-call (atom nil)]
      (with-redefs [web/ib-conn (fn [] :fake-conn)
                    web/ib-snapshot-timeout-ms (fn [] 5000)
                    ib.contract/contract-details-snapshot!
                    (fn [_ contract-opts opts]
                      (let [ch (async/chan 1)]
                        (async/>!! ch {:ok true
                                       :contracts [{:contract {:symbol "AAPL"
                                                               :conId 265598
                                                               :secType "STK"
                                                               :exchange "SMART"
                                                               :primaryExch "NASDAQ"
                                                               :currency "USD"}}]})
                        (async/close! ch)
                        ch))
                    ib.market-data/market-data-snapshot!
                    (fn [conn symbol opts]
                      (reset! market-data-call {:conn conn :symbol symbol :opts opts})
                      (let [ch (async/chan 1)]
                        (async/>!! ch {:ok true :symbol symbol :ticks {:last 200.0}})
                        (async/close! ch)
                        ch))]
        (let [resp (#'web/ib-quote-handler {:params {:symbol "AAPL"
                                                     :exchange "SMART"
                                                     :currency "USD"}})
              body (json/parse-string (:body resp) true)]
          (is (= 200 (:status resp)))
          (is (= {:ok true :symbol "AAPL" :ticks {:last 200.0}} body))
          (is (= {:conn :fake-conn
                  :symbol "AAPL"
                  :opts {:con-id 265598
                         :sec-type "STK"
                         :exchange "NASDAQ"
                         :primary-exch "NASDAQ"
                         :currency "USD"
                         :timeout-ms 5000}}
                 @market-data-call)))))))

(ns trader1.web-test
  (:require [clojure.test :refer [deftest is testing]]
            [cheshire.core :as json]
            [org.httpkit.server :as httpkit]
            [trader1.auth :as auth]
            [trader1.settings :as settings]
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
      (with-redefs [settings/save! (fn [s] (reset! saved s))]
        (let [resp (settings-handler {:params {:ticker-ms  "5000"
                                               :balance-ms "300000"
                                               :orders-ms  "manual"}})]
          (is (= 302 (:status resp)))
          (is (= "/dashboard" (get-in resp [:headers "Location"])))
          (is (= {:ticker-ms 5000 :balance-ms 300000 :orders-ms nil} @saved)))))))

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

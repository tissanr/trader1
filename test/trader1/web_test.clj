(ns trader1.web-test
  (:require [clojure.test :refer [deftest is testing]]
            [cheshire.core :as json]
            [org.httpkit.server :as httpkit]
            [trader1.auth :as auth]
            [trader1.web :refer [broadcast! connected-channels
                                  login-page dashboard-page
                                  login-handler logout-handler
                                  websocket-handler app-routes]]))

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
      (is (.contains html "BTC / USD"))
      (is (.contains html "Account Balance"))
      (is (.contains html "Open Orders"))))
  (testing "contains logout link"
    (is (.contains (dashboard-page) "/logout"))))

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

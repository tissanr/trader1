(ns trader1.kraken-test
  (:require [clojure.test :refer [deftest is testing]]
            [trader1.core :as core]
            [trader1.security :as security]
            [trader1.kraken :refer [request-server-time
                                    request-symbols
                                    request-symbol-pairs
                                    request-ticker
                                    request-balance
                                    request-open-orders]]))

;; private-api-sign calls Base64/getDecoder on the secret, so it must be valid base64
(def ^:private fake-creds {:key "fake-api-key" :secret "dGVzdGtleQ=="})

;; --- Public endpoints ---

(deftest request-server-time-test
  (testing "calls get-path with Time endpoint and returns raw response"
    (let [calls (atom [])]
      (with-redefs [core/get-path (fn [url path]
                                    (swap! calls conj [url path])
                                    {:status 200 :body {:error [] :result {:unixtime 1700000000}}})]
        (let [result (request-server-time)]
          (is (= 1 (count @calls)))
          (is (= "https://api.kraken.com/0/public/" (first (first @calls))))
          (is (= "Time" (second (first @calls))))
          (is (= 1700000000 (get-in result [:body :result :unixtime]))))))))

(deftest request-symbols-test
  (testing "returns :result map on success"
    (with-redefs [core/get-path (fn [_ _]
                                  {:body {:error [] :result {:XXBT {:altname "XBT" :decimals 10}}}})]
      (is (= {:XXBT {:altname "XBT" :decimals 10}} (request-symbols)))))
  (testing "calls Assets endpoint"
    (let [called-path (atom nil)]
      (with-redefs [core/get-path (fn [_ path] (reset! called-path path) {:body {:error [] :result {}}})]
        (request-symbols)
        (is (= "Assets" @called-path)))))
  (testing "throws on API error"
    (with-redefs [core/get-path (fn [_ _] {:body {:error ["EAPI:Invalid"] :result nil}})]
      (is (thrown? Exception (request-symbols))))))

(deftest request-symbol-pairs-test
  (testing "returns :result map on success"
    (with-redefs [core/get-path (fn [_ _]
                                  {:body {:error [] :result {:XBTUSD {:altname "XBTUSD"}}}})]
      (is (= {:XBTUSD {:altname "XBTUSD"}} (request-symbol-pairs)))))
  (testing "calls AssetPairs endpoint"
    (let [called-path (atom nil)]
      (with-redefs [core/get-path (fn [_ path] (reset! called-path path) {:body {:error [] :result {}}})]
        (request-symbol-pairs)
        (is (= "AssetPairs" @called-path)))))
  (testing "throws on API error"
    (with-redefs [core/get-path (fn [_ _] {:body {:error ["EAPI:Invalid"] :result nil}})]
      (is (thrown? Exception (request-symbol-pairs))))))

(deftest request-ticker-test
  (testing "joins single pair into URL"
    (let [called-path (atom nil)]
      (with-redefs [core/get-path (fn [_ path] (reset! called-path path) {:body {:error [] :result {}}})]
        (request-ticker ["XBTUSD"])
        (is (= "Ticker?pair=XBTUSD" @called-path)))))
  (testing "joins multiple pairs with comma"
    (let [called-path (atom nil)]
      (with-redefs [core/get-path (fn [_ path] (reset! called-path path) {:body {:error [] :result {}}})]
        (request-ticker ["XBTUSD" "ETHUSD"])
        (is (= "Ticker?pair=XBTUSD,ETHUSD" @called-path)))))
  (testing "returns :result on success"
    (with-redefs [core/get-path (fn [_ _]
                                  {:body {:error [] :result {:XBTUSD {:a ["50000" 1 "50000"]}}}})]
      (is (= {:XBTUSD {:a ["50000" 1 "50000"]}} (request-ticker ["XBTUSD"])))))
  (testing "throws on API error"
    (with-redefs [core/get-path (fn [_ _] {:body {:error ["EQuery:Unknown asset pair"] :result nil}})]
      (is (thrown? Exception (request-ticker ["BADPAIR"]))))))

;; --- Private endpoints ---

(deftest request-balance-test
  (testing "calls Balance endpoint and returns :result"
    (with-redefs [security/read-in-security-pair (fn [] fake-creds)
                  core/post-form-path (fn [url _ _]
                                        (is (= "https://api.kraken.com/0/private/Balance" url))
                                        {:body {:error [] :result {"XXBT" "1.5" "ZUSD" "10000.0"}}})]
      (is (= {"XXBT" "1.5" "ZUSD" "10000.0"} (request-balance)))))
  (testing "includes nonce in form-params"
    (let [posted-params (atom nil)]
      (with-redefs [security/read-in-security-pair (fn [] fake-creds)
                    core/post-form-path (fn [_ form-params _]
                                          (reset! posted-params form-params)
                                          {:body {:error [] :result {}}})]
        (request-balance)
        (is (contains? @posted-params "nonce")))))
  (testing "throws on API error"
    (with-redefs [security/read-in-security-pair (fn [] fake-creds)
                  core/post-form-path (fn [_ _ _] {:body {:error ["EAPI:Invalid key"] :result nil}})]
      (is (thrown? Exception (request-balance))))))

(deftest request-open-orders-test
  (testing "calls OpenOrders endpoint and returns :result"
    (with-redefs [security/read-in-security-pair (fn [] fake-creds)
                  core/post-form-path (fn [url _ _]
                                        (is (= "https://api.kraken.com/0/private/OpenOrders" url))
                                        {:body {:error [] :result {:open {}}}})]
      (is (= {:open {}} (request-open-orders)))))
  (testing "includes nonce in form-params"
    (let [posted-params (atom nil)]
      (with-redefs [security/read-in-security-pair (fn [] fake-creds)
                    core/post-form-path (fn [_ form-params _]
                                          (reset! posted-params form-params)
                                          {:body {:error [] :result {:open {}}}})]
        (request-open-orders)
        (is (contains? @posted-params "nonce")))))
  (testing "returns populated orders map"
    (let [fake-orders {:open {"TXID-1" {:status "open" :descr {:pair "XBTUSD"}}}}]
      (with-redefs [security/read-in-security-pair (fn [] fake-creds)
                    core/post-form-path (fn [_ _ _] {:body {:error [] :result fake-orders}})]
        (is (= fake-orders (request-open-orders))))))
  (testing "throws on API error"
    (with-redefs [security/read-in-security-pair (fn [] fake-creds)
                  core/post-form-path (fn [_ _ _] {:body {:error ["EAPI:Invalid key"] :result nil}})]
      (is (thrown? Exception (request-open-orders))))))

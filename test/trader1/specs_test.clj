(ns trader1.specs-test
  (:require [clojure.test :refer [deftest is testing]]
            [trader1.specs :as specs]))

(deftest settings-spec-test
  (testing "accepts valid settings maps"
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
                                :balance-ms nil
                                :orders-ms 600000}}}
           (specs/assert-settings! {:server {:port 3001}
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
                                                        :balance-ms nil
                                                        :orders-ms 600000}}}))))
  (testing "rejects invalid settings maps"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
          #"Spec validation failed for settings"
          (specs/assert-settings! {:server {:port 3001}
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
                                                       :ticker-ms "fast"
                                                       :balance-ms 30000
                                                       :orders-ms 15000}}})))))

(deftest websocket-message-spec-test
  (testing "accepts a normalized websocket portfolio message"
    (is (= {:type "portfolio-balance"
            :data [{:account "DU123"
                    :net-liquidation "12345.67"
                    :buying-power "4000.00"
                    :currency "USD"}]}
           (specs/assert-websocket-message!
            {:type "portfolio-balance"
             :data [{:account "DU123"
                     :net-liquidation "12345.67"
                     :buying-power "4000.00"
                     :currency "USD"}]}))))
  (testing "accepts open-orders state payloads"
    (is (= {:type "orders-state"
            :data {:status "ready"
                   :order-count 0
                   :updated-at 1234567890}}
           (specs/assert-websocket-message!
            {:type "orders-state"
             :data {:status "ready"
                    :order-count 0
                    :updated-at 1234567890}}))))
  (testing "accepts order submission payloads"
    (is (= {:type "order-submission"
            :data {:status "success"
                   :message "Order submitted and open orders refreshed."
                   :order-id 12345
                   :symbol "AAPL"
                   :action "BUY"
                   :quantity 1
                   :order-type "LMT"
                   :limit-price 200.0
                   :tif "DAY"
                   :outside-rth false
                   :submitted-at 1234567890
                   :refresh-ok true}}
           (specs/assert-websocket-message!
            {:type "order-submission"
             :data {:status "success"
                    :message "Order submitted and open orders refreshed."
                    :order-id 12345
                    :symbol "AAPL"
                    :action "BUY"
                    :quantity 1
                    :order-type "LMT"
                    :limit-price 200.0
                    :tif "DAY"
                    :outside-rth false
                    :submitted-at 1234567890
                    :refresh-ok true}}))))
  (testing "rejects unknown websocket message types"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
          #"Spec validation failed for websocket message"
          (specs/assert-websocket-message! {:type "mystery" :data {}}))))
  (testing "rejects malformed payloads for known message types"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
          #"websocket payload for positions"
          (specs/assert-websocket-message!
           {:type "positions"
            :data [{:symbol "AAPL"
                    :sec-type "STK"
                    :currency "USD"
                    :position nil
                    :avg-cost 123.45}]})))))

(deftest ib-order-request-spec-test
  (testing "accepts normalized market and limit requests"
    (is (= {:symbol "AAPL"
            :action "BUY"
            :order-type "MKT"
            :quantity 10
            :exchange "SMART"
            :currency "USD"
            :tif "DAY"
            :outside-rth false}
           (specs/assert-valid! ::specs/ib-order-request
                                {:symbol "AAPL"
                                 :action "BUY"
                                 :order-type "MKT"
                                 :quantity 10
                                 :exchange "SMART"
                                 :currency "USD"
                                 :tif "DAY"
                                 :outside-rth false}
                                "IB order request")))
    (is (= {:symbol "AAPL"
            :action "SELL"
            :order-type "LMT"
            :quantity 5
            :limit-price 300.25
            :exchange "SMART"
            :currency "USD"
            :tif "GTC"
            :outside-rth true}
           (specs/assert-valid! ::specs/ib-order-request
                                {:symbol "AAPL"
                                 :action "SELL"
                                 :order-type "LMT"
                                 :quantity 5
                                 :limit-price 300.25
                                 :exchange "SMART"
                                 :currency "USD"
                                 :tif "GTC"
                                 :outside-rth true}
                                "IB order request"))))
  (testing "rejects malformed limit and quantity data"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
          #"Spec validation failed for IB order request"
          (specs/assert-valid! ::specs/ib-order-request
                               {:symbol "AAPL"
                                :action "BUY"
                                :order-type "MKT"
                                :quantity 0
                                :exchange "SMART"
                                :currency "USD"
                                :tif "DAY"
                                :outside-rth false}
                               "IB order request")))
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
          #"Spec validation failed for IB order request"
          (specs/assert-valid! ::specs/ib-order-request
                               {:symbol "AAPL"
                                :action "BUY"
                                :order-type "MKT"
                                :quantity 1
                                :limit-price 1.0
                                :exchange "SMART"
                                :currency "USD"
                                :tif "DAY"
                                :outside-rth false}
                               "IB order request")))))

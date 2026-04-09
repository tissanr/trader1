(ns trader1.specs-test
  (:require [clojure.test :refer [deftest is testing]]
            [trader1.specs :as specs]))

(deftest settings-spec-test
  (testing "accepts valid settings maps"
    (is (= {:ticker-ms 5000 :balance-ms nil :orders-ms 600000}
           (specs/assert-settings! {:ticker-ms 5000
                                    :balance-ms nil
                                    :orders-ms 600000}))))
  (testing "rejects invalid settings maps"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
          #"Spec validation failed for settings"
          (specs/assert-settings! {:ticker-ms "fast"
                                   :balance-ms 30000
                                   :orders-ms 15000})))))

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

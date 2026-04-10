(ns trader1.market-data-test
  (:require [clojure.core.async :as async]
            [clojure.test :refer [deftest is testing]]
            [ib.market-data :as market-data]))

(deftest market-data-snapshot-delayed-data-notice-test
  (testing "ignores the delayed-data availability notice and keeps waiting for ticks"
    (let [events-ch (async/chan 10)
          unsubscribed? (atom false)
          cancelled-req-id (atom nil)]
      (with-redefs [ib.client/subscribe-events! (fn [_ _] events-ch)
                    ib.client/unsubscribe-events! (fn [_ _] (reset! unsubscribed? true))
                    ib.client/req-market-data-type! (fn [_ _] true)
                    ib.client/req-mkt-data! (fn [_ _] true)
                    ib.client/cancel-mkt-data! (fn [_ req-id]
                                                 (reset! cancelled-req-id req-id)
                                                 true)]
        (let [out (market-data/market-data-snapshot! {:dummy true}
                                                     "AAPL"
                                                     {:con-id 265598
                                                      :exchange "NASDAQ"
                                                      :primary-exch "NASDAQ"
                                                      :currency "USD"
                                                      :timeout-ms 200})]
          (async/>!! events-ch {:type :ib/error
                                :request-id 800001
                                :message "Requested market data requires additional subscription for API. Delayed market data is available.AAPL NASDAQ.NMS/TOP/ALL"
                                :code 123})
          (async/>!! events-ch {:type :ib/tick-price
                                :req-id 800001
                                :field-key :last
                                :price 199.5})
          (async/>!! events-ch {:type :ib/tick-snapshot-end
                                :req-id 800001})
          (let [result (async/<!! out)]
            (is (= {:ok true
                    :symbol "AAPL"
                    :ticks {:last 199.5}}
                   result))
            (is (true? @unsubscribed?))
            (is (= 800001 @cancelled-req-id))))))))

(ns trader1.kraken-test
  (:require [clojure.test :refer :all])
  (:require [trader1.kraken :refer :all]))

(deftest request-server-time-test
  (let [response (request-server-time)]
    (is (map? response))
    (is (contains? response :body))
    (let [body (:body response)]
      (is (contains? body :unixtime))
      (is (number? (:unixtime body))))))

(deftest request-symbols-test
  (let [response (request-symbols)]
    (is (map? response))
    (is (contains? response :result))
    (let [result (:result response)]
      (is (map? result))
      (is (contains? result :XXBT))  ; Bitcoin should be there
      (is (string? (:altname (:XXBT result)))))))

(deftest request-symbol-pairs-test
  (let [response (request-symbol-pairs)]
    (is (map? response))
    (is (contains? response :result))
    (let [result (:result response)]
      (is (map? result))
      (is (> (count result) 0))  ; Should have some pairs
      (let [first-pair (val (first result))]
        (is (contains? first-pair :altname))))))

(deftest request-ticker-test
  (try
    (let [response (request-ticker ["XBTUSD"])]
      (is (map? response))
      (is (contains? response :result)))
    (catch Exception e
      ;; If no API keys, it should fail with file not found or auth error
      (is (or (.contains (.getMessage e) "No such file")
              (.contains (.getMessage e) "Permission denied")
              (.contains (.getMessage e) "API key"))))))



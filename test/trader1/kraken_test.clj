(ns trader1.kraken-test
  (:require [clojure.test :refer :all])
  (:require [trader1.kraken :refer :all]))

(deftest request-symbols-test
  (let [s-time (request-server-time)]
    (is (contains? s-time :body))))

(deftest request-symbol-pairs-test
  (let [symbols (request-symbols)]
    (is (contains?  symbols :XXBT))))



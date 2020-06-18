(ns trader1.security-test
  (:require [clojure.test :refer :all])
  (:require [trader1.security :refer :all]))

(deftest read-in-security-pair-test
  (let [details (read-in-security-pair)]
    (is (contains? details :key))
    (is (contains? details :secret))))
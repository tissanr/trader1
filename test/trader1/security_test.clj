(ns trader1.security-test
  (:require [clojure.test :refer [deftest is]])
  (:require [trader1.security :refer [read-in-security-pair]]))

(deftest read-in-security-pair-test
  (let [details (read-in-security-pair)]
    (is (contains? details :key))
    (is (contains? details :secret))))
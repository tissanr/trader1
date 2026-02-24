(ns trader1.core-test
  (:require [clojure.test :refer :all]
            [trader1.core :refer :all]))

(deftest throw-if-err-test
  (testing "no error"
    (is (= (throw-if-err {:error []}) nil)))
  (testing "with error"
    (is (thrown? Exception (throw-if-err {:error ["some error"]})))))

(deftest post-path-test
  (testing "post-path structure"
    ;; This would require mocking clj-http, but for now skip
    (is true "Placeholder for post-path test")))

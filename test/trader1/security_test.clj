(ns trader1.security-test
  (:require [clojure.test :refer [deftest is testing]]
            [trader1.security :as security]))

(def ^:private fixture-file "test/fixtures/test.key")

(deftest read-in-security-pair-test
  (with-redefs [security/credentials-file fixture-file]
    (testing "returns a map with :key and :secret"
      (let [result (security/read-in-security-pair)]
        (is (map? result))
        (is (contains? result :key))
        (is (contains? result :secret))))
    (testing "parses the key from line 1"
      (is (= "fake-api-key" (:key (security/read-in-security-pair)))))
    (testing "parses the secret from line 2"
      (is (= "dGVzdHNlY3JldA==" (:secret (security/read-in-security-pair)))))))

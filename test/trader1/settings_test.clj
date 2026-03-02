(ns trader1.settings-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.edn :as edn]
            [trader1.settings :as settings]))

(deftest defaults-test
  (testing "defaults contain the expected keys and values"
    (is (= 5000  (:ticker-ms  settings/defaults)))
    (is (= 30000 (:balance-ms settings/defaults)))
    (is (= 15000 (:orders-ms  settings/defaults)))))

(deftest load-test
  (testing "is a no-op when config file is missing"
    (reset! settings/settings settings/defaults)
    (with-redefs [settings/config-path "/tmp/nonexistent-trader1-settings.edn"]
      (settings/load!))
    (is (= settings/defaults @settings/settings)))

  (testing "merges file values over defaults, preserving unset defaults"
    (reset! settings/settings settings/defaults)
    (let [tmp (java.io.File/createTempFile "trader1-settings" ".edn")]
      (try
        (spit tmp "{:ticker-ms 300000}")
        (with-redefs [settings/config-path (.getAbsolutePath tmp)]
          (settings/load!))
        (is (= 300000 (:ticker-ms  @settings/settings)))
        (is (= 30000  (:balance-ms @settings/settings)))  ; default preserved
        (is (= 15000  (:orders-ms  @settings/settings)))  ; default preserved
        (finally (.delete tmp)))))

  (testing "supports nil values for manual mode"
    (reset! settings/settings settings/defaults)
    (let [tmp (java.io.File/createTempFile "trader1-settings" ".edn")]
      (try
        (spit tmp "{:ticker-ms nil :balance-ms nil :orders-ms nil}")
        (with-redefs [settings/config-path (.getAbsolutePath tmp)]
          (settings/load!))
        (is (nil? (:ticker-ms  @settings/settings)))
        (is (nil? (:balance-ms @settings/settings)))
        (is (nil? (:orders-ms  @settings/settings)))
        (finally (.delete tmp))))))

(deftest save-test
  (testing "updates the atom and writes valid EDN to the config file"
    (let [tmp (java.io.File/createTempFile "trader1-settings" ".edn")]
      (try
        (with-redefs [settings/config-path (.getAbsolutePath tmp)]
          (settings/save! {:ticker-ms 300000 :balance-ms nil :orders-ms 600000}))
        (is (= 300000 (:ticker-ms  @settings/settings)))
        (is (nil?     (:balance-ms @settings/settings)))
        (is (= 600000 (:orders-ms  @settings/settings)))
        (is (= {:ticker-ms 300000 :balance-ms nil :orders-ms 600000}
               (edn/read-string (slurp tmp))))
        (finally (.delete tmp))))))

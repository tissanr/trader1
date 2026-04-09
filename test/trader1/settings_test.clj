(ns trader1.settings-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.edn :as edn]
            [trader1.settings :as settings]))

(deftest defaults-test
  (testing "defaults contain the expected keys and values"
    (is (= 3000      (get-in settings/defaults [:server :port])))
    (is (= true      (get-in settings/defaults [:services :ib :enabled])))
    (is (= "127.0.0.1" (get-in settings/defaults [:services :ib :host])))
    (is (= 10000     (get-in settings/defaults [:services :kraken :refresh-ms])))
    (is (= 5000      (get-in settings/defaults [:services :kraken :ticker-ms])))
    (is (= 30000     (get-in settings/defaults [:services :kraken :balance-ms])))
    (is (= 15000     (get-in settings/defaults [:services :kraken :orders-ms])))))

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
        (spit tmp "{:services {:kraken {:ticker-ms 300000}}}")
        (with-redefs [settings/config-path (.getAbsolutePath tmp)]
          (settings/load!))
        (is (= 300000 (get-in @settings/settings [:services :kraken :ticker-ms])))
        (is (= 30000  (get-in @settings/settings [:services :kraken :balance-ms])))
        (is (= 15000  (get-in @settings/settings [:services :kraken :orders-ms])))
        (finally (.delete tmp)))))

  (testing "supports nil values for manual mode"
    (reset! settings/settings settings/defaults)
    (let [tmp (java.io.File/createTempFile "trader1-settings" ".edn")]
      (try
        (spit tmp "{:services {:kraken {:ticker-ms nil :balance-ms nil :orders-ms nil}}}")
        (with-redefs [settings/config-path (.getAbsolutePath tmp)]
          (settings/load!))
        (is (nil? (get-in @settings/settings [:services :kraken :ticker-ms])))
        (is (nil? (get-in @settings/settings [:services :kraken :balance-ms])))
        (is (nil? (get-in @settings/settings [:services :kraken :orders-ms])))
        (finally (.delete tmp)))))

  (testing "normalizes legacy flat polling settings"
    (reset! settings/settings settings/defaults)
    (let [tmp (java.io.File/createTempFile "trader1-settings" ".edn")]
      (try
        (spit tmp "{:ticker-ms 600000 :orders-ms nil}")
        (with-redefs [settings/config-path (.getAbsolutePath tmp)]
          (settings/load!))
        (is (= 600000 (get-in @settings/settings [:services :kraken :ticker-ms])))
        (is (= 30000  (get-in @settings/settings [:services :kraken :balance-ms])))
        (is (nil?     (get-in @settings/settings [:services :kraken :orders-ms])))
        (finally (.delete tmp))))))

(deftest save-test
  (testing "updates the atom and writes valid EDN to the config file"
    (let [tmp (java.io.File/createTempFile "trader1-settings" ".edn")]
      (try
        (with-redefs [settings/config-path (.getAbsolutePath tmp)]
          (settings/save! {:server {:port 3000}
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
                                               :ticker-ms 300000
                                               :balance-ms nil
                                               :orders-ms 600000}}}))
        (is (= 300000 (get-in @settings/settings [:services :kraken :ticker-ms])))
        (is (nil?     (get-in @settings/settings [:services :kraken :balance-ms])))
        (is (= 600000 (get-in @settings/settings [:services :kraken :orders-ms])))
        (is (= {:server {:port 3000}
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
                                    :ticker-ms 300000
                                    :balance-ms nil
                                    :orders-ms 600000}}}
               (edn/read-string (slurp tmp))))
        (finally (.delete tmp))))))

(deftest invalid-settings-test
  (testing "save! rejects invalid settings"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
          #"Spec validation failed for settings"
          (settings/save! {:server {:port 3000}
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
                                               :orders-ms 15000}}}))))
  (testing "load! leaves current settings unchanged when file contents are invalid"
    (reset! settings/settings settings/defaults)
    (let [tmp (java.io.File/createTempFile "trader1-settings" ".edn")]
      (try
        (spit tmp "{:services {:kraken {:ticker-ms \"fast\" :balance-ms 30000 :orders-ms 15000}}}")
        (with-redefs [settings/config-path (.getAbsolutePath tmp)]
          (settings/load!))
        (is (= settings/defaults @settings/settings))
        (finally (.delete tmp))))))

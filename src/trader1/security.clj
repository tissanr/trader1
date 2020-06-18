(ns trader1.security
  (:require [clojure.string :as str]))

(defn read-in-security-pair
  "Reads the key and Secret from the kraken file and returns them as 
  {:key <key> :secret <secret>}"
  []
  (let [sec-pair (slurp "/home/yoga/Security/api.key.kraken")]
    (let [sec-pair-list (str/split sec-pair #"\n")]
      {:key (sec-pair-list 0)
       :secret (sec-pair-list 1)})))

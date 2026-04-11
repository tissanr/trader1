(ns trader1.security
  (:require [clojure.string :as str]))

(def credentials-file "credentials/kraken.key")

(defn read-in-security-pair
  "Reads the key and Secret from credentials/kraken.key and returns them as
  {:key <key> :secret <secret>}"
  []
  (let [sec-pair (slurp credentials-file)]
    (let [sec-pair-list (str/split sec-pair #"\n")]
      {:key (sec-pair-list 0)
       :secret (sec-pair-list 1)})))

(defn write-credentials!
  "Writes api-key and api-secret to credentials/kraken.key."
  [api-key api-secret]
  (spit credentials-file (str api-key "\n" api-secret "\n")))

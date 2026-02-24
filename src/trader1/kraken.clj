(ns trader1.kraken
  (:require [trader1.core :as core])
  (:require [trader1.security :as security])
  (:require [clojure.string :as str])
  (:require [digest :as hash])
  (:import java.util.Base64
           java.security.MessageDigest)
  (:import (javax.crypto Mac)
           (javax.crypto.spec SecretKeySpec)))

(def base-url "https://api.kraken.com/0/public/")

(def k-header {:User-Agent "kraken-clj/v0.1"})

(defn request-server-time
  "test server time"
  []
  (core/get-path base-url "Time"))

(defn request-symbols
  "Returns a List of all the assets <asset_name> = asset name
    altname = alternate name
    aclass = asset class
    decimals = scaling decimal places for record keeping
    display_decimals = scaling decimal places for output display"
  []
  (let [reply (:body (core/get-path base-url "Assets"))]
    (core/throw-if-err reply)
    (:result reply)))

(defn request-symbol-pairs
  "they call them tradable asset pairs"
  []
  (let [reply (:body (core/get-path base-url "AssetPairs"))]
    (core/throw-if-err reply)
    (get reply :result)))

(defn- decode-base64 [^String x]
  (.decode (Base64/getDecoder) x))

(defn- secret-key-inst [^String secret ^Mac mac]
  (SecretKeySpec. (.getBytes secret) (.getAlgorithm mac)))

(defn- sha-512
  "Returns the signature of a string with a given
    key, using a SHA-512 HMAC."
  [^String key ^String string]
  (let [mac (Mac/getInstance "HMACSHA512")
        secretKey (secret-key-inst key mac)]
    (-> (doto mac
          (.init secretKey)
          (.update (.getBytes string)))
        .doFinal)))

(defn- sign-message
  [nonce post-data secret]
  (sha-512 (decode-base64 secret) (hash/sha-256 (str nonce post-data))))


(defn- get-header [path nonce post-data]
  (let [sec-pair (security/read-in-security-pair)]
    (conj k-header
          {:API-Key  (sec-pair :key)
           :API-Sign (sha-512
                       (str (decode-base64
                              (:secret (security/read-in-security-pair))))
                       (str path
                            (sign-message nonce post-data (sec-pair :secret))))})))

(defn- get-nonce []
  (System/currentTimeMillis))

(defn- sha256-bytes [^String s]
  (.digest (MessageDigest/getInstance "SHA-256") (.getBytes s "UTF-8")))

(defn- hmac-sha512-bytes [^bytes key-bytes ^bytes data-bytes]
  (let [mac (Mac/getInstance "HMACSHA512")
        secret-key (SecretKeySpec. key-bytes "HMACSHA512")]
    (.init mac secret-key)
    (.doFinal mac data-bytes)))

(defn- private-api-sign [^String path nonce post-data ^String secret]
  (let [decoded-secret (.decode (Base64/getDecoder) secret)
        message (byte-array (concat (.getBytes path "UTF-8")
                                    (sha256-bytes (str nonce post-data))))]
    (.encodeToString (Base64/getEncoder) (hmac-sha512-bytes decoded-secret message))))

(defn- private-headers [path nonce post-data]
  (let [{:keys [key secret]} (security/read-in-security-pair)]
    (merge k-header {:API-Key key
                     :API-Sign (private-api-sign path nonce post-data secret)})))

(defn request-balance
  "Returns account balances for all assets"
  []
  (let [path "/0/private/Balance"
        nonce (get-nonce)
        post-data (str "nonce=" nonce)
        headers (private-headers path nonce post-data)
        reply (:body (core/post-form-path (str "https://api.kraken.com" path)
                                          {"nonce" (str nonce)}
                                          headers))]
    (core/throw-if-err reply)
    (get reply :result)))

(defn request-open-orders
  "Returns open orders from the Kraken private API.
  Result shape: {:open {\"<txid>\" {:descr {...} :vol \"...\" :status \"open\"}}}"
  []
  (let [path "/0/private/OpenOrders"
        nonce (get-nonce)
        post-data (str "nonce=" nonce)
        headers (private-headers path nonce post-data)
        reply (:body (core/post-form-path (str "https://api.kraken.com" path)
                                          {"nonce" (str nonce)}
                                          headers))]
    (core/throw-if-err reply)
    (get reply :result)))

(defn request-ticker
  "requests ticker for certain vector of assetpairs via the public API"
  [asset-pairs]
  (let [pair-str (str/join "," asset-pairs)
        reply (:body (core/get-path base-url (str "Ticker?pair=" pair-str)))]
    (core/throw-if-err reply)
    (get reply :result)))



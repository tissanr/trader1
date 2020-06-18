(ns trader1.kraken
  (:require [trader1.core :as core])
  (:require [trader1.security :as security])
  (:require [digest :as hash])
  ;;(:require [clj-http.client :as client])
  (:require [cheshire.core :as json])
  (:require [clj-time.core :as time])
  (:require [clj-time.coerce :as tc])
  ;;(:require [clojure.data.codec.base64 :as b64])
  (:import java.util.Base64)
  (:import (javax.crypto Mac)
           (javax.crypto.spec SecretKeySpec)))

(def base-url "https://api.kraken.com/0/public/")
(def version 0)
(def public "/public/")
(def private "/private/")

(def method {:assets      "Assets"
             :asset-pairs "AssetPairs"
             :ticker      "Ticker"})

(def private-method {:balance       "Balance"
                     :trade-balance "TradeBalance"
                     :open-orders   "OpenOrders"})

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

#_(:XXBTZEUR (request-symbol-pairs))

(defn- decode-base64 [x]
  (.decode (Base64/getDecoder) x))


(defn- secret-key-inst [secret mac]
  (SecretKeySpec. (.getBytes secret) (.getAlgorithm mac)))


(defn- sha-512 [key string]
  "Returns the signature of a string with a given 
    key, using a SHA-512 HMAC."
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
  (tc/to-long (time/now)))

(defn request-ticker
  "requests ticker for certain vector of assetpairs"
  [asset-pairs]
  (let [path (str base-url (:ticker method))]
    (let [nonce (get-nonce)]
      (let [post-data (json/generate-string {:pair  ["XBTUSD"]
                                             :nonce nonce})]
        (let [header (get-header path nonce post-data)]
          (println path)
          (println post-data)
          (println header)
          (let [reply (:body (core/post-path path
                                             post-data
                                             header))]
            (core/throw-if-err reply)
            (get reply :result)))))))


(defn- to-hex [bytes]
  "Convert bytes to a String"
  (apply str (map #(format "%x" %) bytes)))


(str (sha-512 (str (decode-base64 (:secret (security/read-in-security-pair))))
              "The quick brown fox jumps over the lazy dog."))

#_(str (sha-512 (str (b64/decode
                       (bytes
                         (:secret
                           (security/read-in-security-pair)))))
                "The quick brown fox jumps over the lazy dog."))

#_(to-hex (let [s-pair (security/read-in-security-pair)]
            (decode-b (s-pair :secret))))

#_(defprotocol encode
    "encode stuff"
    (encode-base64 [x]))


#_(extend-protocol encode
    (Class/forName "[B")
    (encode-base64 [x] (.encodeToString (Base64/getEncoder) x)))

#_(defprotocol decode
    "decode stuff"
    (decode-base64 [x]))

#_(extend-protocol decode
    String
    (decode-base64 [x] (decode-base64 (.getBytes x))))

#_(extend-protocol decode
    (Class/forName "[B")
    (decode-base64 [x] (.decode (Base64/getDecoder) x)))

#_(defmulti encode-base64 class)
#_(defmethod encode-base64 java.lang.Byte [to-encode]
    (.encodeToString (Base64/getEncoder) to-encode))
#_(defmethod encode-base64 String [to-encode]
    (encode-base64 (.getBytes to-encode)))

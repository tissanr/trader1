(ns trader1.core
  (:require [clj-http.client :as client])
  (:require [cheshire.core :refer [generate-string]])
  (:gen-class))


(defn get-path
  "request something http"
  [url path]
  (client/get (str url path)
              {:as :json}))

(defn post-form-path
  "POST with application/x-www-form-urlencoded body (used for private API endpoints)"
  [url form-params headers]
  (client/post url
               {:form-params form-params
                :headers headers
                :as :json}))

;;(:body (post-path "https://api.kraken.com/0/public/Ticker"))

(generate-string {:pair ["XBTUSD"]})
  
(defn throw-if-err
  [reply]
  (when (not (empty? (:error reply)))
    (throw (Exception. (str "Error happened: " (:error reply))))))


;; just to find out how to call java methods
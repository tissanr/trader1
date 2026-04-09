(ns trader1.core
  (:require [clj-http.client :as client]
            [cheshire.core :refer [generate-string]]
            [trader1.settings :as settings])
  (:import [java.util.logging Level Logger])
  (:gen-class))


(defn get-path
  "request something http"
  [url path]
  (client/get (str url path)
              {:as :json
               :cookie-policy :ignore-cookies}))

(defn post-form-path
  "POST with application/x-www-form-urlencoded body (used for private API endpoints)"
  [url form-params headers]
  (client/post url
               {:form-params form-params
                :headers headers
                :as :json
                :cookie-policy :ignore-cookies}))

;;(:body (post-path "https://api.kraken.com/0/public/Ticker"))

(generate-string {:pair ["XBTUSD"]})
  
(defn throw-if-err
  [reply]
  (when (not (empty? (:error reply)))
    (throw (Exception. (str "Error happened: " (:error reply))))))

(defn- silence-noisy-http-loggers! []
  ;; Kraken sits behind Cloudflare and Apache HttpClient logs cookie warnings
  ;; for headers we do not use. Suppress that specific logger in dev/runtime.
  (.setLevel (Logger/getLogger "org.apache.http.client.protocol.ResponseProcessCookies")
             Level/SEVERE))


(defn -main [& _args]
  (require 'trader1.web)
  (silence-noisy-http-loggers!)
  (settings/load!)
  (let [port   (settings/get-value [:server :port])
        start! (ns-resolve 'trader1.web 'start-server!)]
    (start! port)
    (println (str "Trader1 dashboard running on http://localhost:" port))))

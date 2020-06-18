(ns trader1.core
  (:require trader1.security)
  (:require [clj-http.client :as client])
  (:require [clojure.string :as str])
  (:require [cheshire.core :refer :all])
  (:gen-class))


(defn get-path
  "request someting http"
  [url path]
  (client/get (str url path)
              {:as :json}))

(defn get-csv
  "request someting http"
  [url path]
  (client/get (str url path)
              {:as :csv}))


(defn post-path
  [url post-data header]
  (client/post url
               {:body post-data
                :header header
                :content-type :json
                :as :json}))

;;(:body (post-path "https://api.kraken.com/0/public/Ticker"))

(generate-string {:pair ["XBTUSD"]})
  
(defn throw-if-err
  [reply]
  (when (not (empty? (:error reply)))
    (throw (Exception. (str "Error happened: " (:error reply))))))


(defn filter-by-symbol
  [symbol structure]
  (filter #(re-find (re-pattern (str/upper-case symbol))
                    (name %))
          structure))


(defn print-result
  [result]
  (let [element (first result)]
    (print (key  element) " ")
    (println (val element)))
  (when (not (empty? (rest result)))
    (print-result (rest result))))


(defn print-keys
  "prints the keys of a Map"
  [structure]
  (print (keys structure)))


;; just to find out how to call java methods
(defn get-number-of-threads
  "return the number of threads the system can handle in parrallel"
  []
  (.availableProcessors (Runtime/getRuntime)))

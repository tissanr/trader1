(ns trader1.unicorn
  (:require [trader1.core :as core]))

(def base-url "http://unicorn.us.com/advdec/")

(defn request-advancers
  "get the advancers of NYSE"
  []
  (let [response (core/get-csv base-url "NYSE_advn.csv")]
    (if (= (:status response) 200)
      (:body response)
      [])))
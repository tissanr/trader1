(ns trader1.settings
  (:require [clojure.edn :as edn]
            [trader1.specs :as specs]))

(def config-path "config/settings.edn")

(def defaults
  {:ticker-ms  5000
   :balance-ms 30000
   :orders-ms  15000})

(defonce settings (atom defaults))

(defn- validated-settings [candidate]
  (specs/assert-settings! candidate))

(defn load!
  "Loads settings from config-path, merging over defaults. No-op if file is missing."
  []
  (try
    (let [loaded (validated-settings
                  (merge defaults (edn/read-string (slurp config-path))))]
      (reset! settings loaded))
    (catch Exception _ nil)))

(defn save!
  "Persists new-settings to config-path and updates the settings atom."
  [new-settings]
  (let [validated (validated-settings new-settings)]
    (reset! settings validated)
    (spit config-path (pr-str validated))))

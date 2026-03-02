(ns trader1.settings
  (:require [clojure.edn :as edn]))

(def config-path "config/settings.edn")

(def defaults
  {:ticker-ms  5000
   :balance-ms 30000
   :orders-ms  15000})

(defonce settings (atom defaults))

(defn load!
  "Loads settings from config-path, merging over defaults. No-op if file is missing."
  []
  (try
    (reset! settings (merge defaults (edn/read-string (slurp config-path))))
    (catch Exception _ nil)))

(defn save!
  "Persists new-settings to config-path and updates the settings atom."
  [new-settings]
  (reset! settings new-settings)
  (spit config-path (pr-str new-settings)))

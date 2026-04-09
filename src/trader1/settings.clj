(ns trader1.settings
  (:require [clojure.edn :as edn]
            [trader1.specs :as specs]))

(def config-path "config/settings.edn")

(def defaults
  {:server {:port 3001}
   :services {:ib {:enabled true
                   :host "127.0.0.1"
                   :port 4002
                   :client-id 0
                   :snapshot-timeout-ms 5000
                   :refresh-ms 10000
                   :event-buffer-size 2048
                   :overflow-strategy :sliding}
              :kraken {:enabled true
                       :refresh-ms 10000
                       :ticker-ms 5000
                       :balance-ms 30000
                       :orders-ms 15000}}})

(defonce settings (atom defaults))

(defn- env-int [env-name default]
  (try
    (Integer/parseInt (or (System/getenv env-name) ""))
    (catch Exception _
      default)))

(defn- apply-env-overrides [cfg]
  (-> cfg
      (assoc-in [:server :port] (env-int "PORT" (get-in cfg [:server :port])))
      (assoc-in [:services :ib :host] (or (System/getenv "IB_HOST")
                                          (get-in cfg [:services :ib :host])))
      (assoc-in [:services :ib :port] (env-int "IB_PORT" (get-in cfg [:services :ib :port])))))

(defn- legacy-polling-config [candidate]
  (let [legacy-keys [:ticker-ms :balance-ms :orders-ms]]
    (when (some #(contains? candidate %) legacy-keys)
      {:services {:kraken (select-keys candidate legacy-keys)}})))

(defn- deep-merge [& maps]
  (apply merge-with
         (fn [left right]
           (if (and (map? left) (map? right))
             (deep-merge left right)
             right))
         maps))

(defn- normalize-settings [candidate]
  (let [legacy (legacy-polling-config candidate)]
    (deep-merge defaults legacy candidate)))

(defn- validated-settings [candidate]
  (specs/assert-settings! candidate))

(defn load!
  "Loads settings from config-path, merging over defaults. No-op if file is missing."
  []
  (try
    (let [loaded (validated-settings
                  (apply-env-overrides
                   (normalize-settings (edn/read-string (slurp config-path)))))]
      (reset! settings loaded))
    (catch java.io.FileNotFoundException _
      (reset! settings (apply-env-overrides defaults)))
    (catch Exception _
      nil)))

(defn save!
  "Persists new-settings to config-path and updates the settings atom."
  [new-settings]
  (let [validated (validated-settings (normalize-settings new-settings))]
    (reset! settings validated)
    (spit config-path (pr-str validated))))

(defn get-value
  [path]
  (get-in @settings path))

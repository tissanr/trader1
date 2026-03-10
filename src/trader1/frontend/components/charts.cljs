(ns trader1.frontend.components.charts
  (:require [reagent.core :as r]
            [clojure.string :as str]
            ["recharts" :refer [PieChart Pie Cell Tooltip Legend ResponsiveContainer]]))

;; ── Asset display names ──────────────────────────────────────────────────────

(def ^:private asset-names
  {"XXBT"  "BTC"
   "XETH"  "ETH"
   "ZUSD"  "USD"
   "XLTC"  "LTC"
   "XXLM"  "XLM"
   "XXRP"  "XRP"
   "XMLN"  "MLN"
   "XZEC"  "ZEC"
   "XDAO"  "DAO"
   "XXMR"  "XMR"
   "XREP"  "REP"
   "ZCAD"  "CAD"
   "ZEUR"  "EUR"
   "ZGBP"  "GBP"
   "ZJPY"  "JPY"})

(defn- asset-label [k]
  (let [s (name k)]
    (or (get asset-names s)
        ;; strip leading X or Z if no mapping found
        (if (re-matches #"[XZ].+" s) (subs s 1) s))))

;; ── Color palette ─────────────────────────────────────────────────────────────

(def ^:private palette
  ["#f0c040" "#4a9eda" "#4acf7a" "#9f7aea"
   "#f0a040" "#4aced0" "#e05050" "#c080e0"
   "#80c0a0" "#e0c080"])

(defn- color-at [i] (nth palette (mod i (count palette))))

;; ── USD value helpers ────────────────────────────────────────────────────────

(defn- asset-usd-price
  "Look up the last price for `asset-key` (e.g. :XXBT) in ticker data.
  Returns a JS number or nil."
  [ticker asset-key]
  (when-not (= (name asset-key) "ZUSD")
    (let [a (name asset-key)]
      (some (fn [[pair info]]
              (when (str/starts-with? (name pair) a)
                (some-> info :c first js/parseFloat)))
            ticker))))

(defn- balance->usd-slices
  "Convert kraken-balance map to [{:name label :value usd-amount}] for the pie."
  [balance ticker]
  (->> balance
       (keep (fn [[asset amount-str]]
               (let [amount (js/parseFloat (str amount-str))]
                 (when (pos? amount)
                   (let [usd (if (= (name asset) "ZUSD")
                               amount
                               (when-let [p (asset-usd-price ticker asset)]
                                 (* amount p)))]
                     (when (and usd (pos? usd))
                       {:name  (asset-label asset)
                        :value (.parseFloat js/Number (.toFixed usd 2))}))))))
       (sort-by :name)
       vec))

;; ── Portfolio breakdown (Kraken total vs IB total) ───────────────────────────

(defn portfolio-breakdown
  [kraken-total-usd ib-balance]
  (let [k-val (some-> kraken-total-usd js/parseFloat)
        i-val (when (seq ib-balance)
                (reduce + 0 (map #(js/parseFloat (:value %)) ib-balance)))
        slices (cond-> []
                 (and k-val (pos? k-val)) (conj {:name "Kraken" :value k-val})
                 (and i-val (pos? i-val)) (conj {:name "IB"     :value i-val}))]
    [:div.chart-card
     [:h3.chart-title "Portfolio Breakdown"]
     (if (seq slices)
       [:> ResponsiveContainer {:width "100%" :height 240}
        [:> PieChart
         [:> Pie {:data          (clj->js slices)
                  :dataKey       "value"
                  :nameKey       "name"
                  :cx            "50%"
                  :cy            "50%"
                  :innerRadius   55
                  :outerRadius   85
                  :paddingAngle  3}
          (map-indexed
           (fn [i entry]
             ^{:key (:name entry)}
             [:> Cell {:fill (color-at i)}])
           slices)]
         [:> Tooltip {:formatter (fn [v] (str "$" (.toLocaleString v "en-US" #js {:maximumFractionDigits 2})))}]
         [:> Legend]]]
       [:p.chart-empty "Waiting for data…"])]))

;; ── Asset distribution (Kraken balance in USD) ──────────────────────────────

(defn asset-distribution
  [kraken-balance kraken-ticker]
  (let [slices (balance->usd-slices kraken-balance kraken-ticker)]
    [:div.chart-card
     [:h3.chart-title "Crypto Distribution (USD)"]
     (if (seq slices)
       [:> ResponsiveContainer {:width "100%" :height 240}
        [:> PieChart
         [:> Pie {:data          (clj->js slices)
                  :dataKey       "value"
                  :nameKey       "name"
                  :cx            "50%"
                  :cy            "50%"
                  :innerRadius   55
                  :outerRadius   85
                  :paddingAngle  3
                  :label         (fn [entry]
                                   (.-name entry))}
          (map-indexed
           (fn [i entry]
             ^{:key (:name entry)}
             [:> Cell {:fill (color-at i)}])
           slices)]
         [:> Tooltip {:formatter (fn [v] (str "$" (.toLocaleString v "en-US" #js {:maximumFractionDigits 2})))}]
         [:> Legend]]]
       [:p.chart-empty "Waiting for balance data…"])]))

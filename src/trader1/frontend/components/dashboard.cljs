(ns trader1.frontend.components.dashboard
  (:require [trader1.frontend.state :refer [app-state]]
            [trader1.frontend.components.kraken :as kraken]
            [trader1.frontend.components.ib     :as ib]
            [trader1.frontend.components.charts :as charts]))

(defn- connection-badge []
  (let [status (:connection-status @app-state)]
    (when (= status "disconnected")
      [:span.disconnected-badge "Disconnected"])))

(defn root []
  (let [{:keys [kraken-portfolio-value portfolio-balance
                kraken-balance kraken-ticker]} @app-state]
    [:div
     [:header
      [:h1 "Trader1"]
      [:nav
       [connection-badge]
       [:a {:href "/settings"} "Settings"]
       [:a {:href "/logout"}   "Logout"]]]
     [:main
      ;; ── Kraken section ────────────────────────────────────────────────
      [:div#kraken-grid
       [kraken/portfolio-value]
       [kraken/ticker]
       [kraken/balance]
       [kraken/orders]]

      ;; ── Portfolio charts ──────────────────────────────────────────────
      [:div#portfolio-charts
       [charts/asset-distribution kraken-balance kraken-ticker]
       [charts/portfolio-breakdown (:total-value kraken-portfolio-value) portfolio-balance]]

      ;; ── IB section ────────────────────────────────────────────────────
      [:div#dashboard-grid
       [ib/portfolio-balance]
       [:section#dashboard-empty-cell {:aria-hidden "true"}]
       [ib/positions]
       [ib/orders]]

      ;; ── IB debug ─────────────────────────────────────────────────────
      [ib/debug-panel]]]))

(ns trader1.frontend.core
  (:require [reagent.dom :as rdom]
            [trader1.frontend.state :as state]
            [trader1.frontend.components.dashboard :as dashboard]))

(defn mount! []
  (rdom/render [dashboard/root] (js/document.getElementById "app")))

(defn init! []
  (state/connect-ws!)
  (mount!))

(defn reload! []
  ;; Called by shadow-cljs after hot-reload; re-render without reconnecting WS
  (mount!))

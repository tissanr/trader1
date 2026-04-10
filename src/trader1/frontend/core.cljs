(ns trader1.frontend.core
  (:require [reagent.dom.client :as rdom]
            [trader1.frontend.state :as state]
            [trader1.frontend.components.dashboard :as dashboard]))

(defonce root (atom nil))

(defn mount! []
  (let [container (js/document.getElementById "app")]
    (when-not @root
      (reset! root (rdom/create-root container)))
    (rdom/render @root [dashboard/root])))

(defn init! []
  (mount!)
  (state/connect-ws!))

(defn reload! []
  ;; Called by shadow-cljs after hot-reload; re-render without reconnecting WS
  (mount!))

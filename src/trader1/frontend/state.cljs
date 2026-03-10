(ns trader1.frontend.state
  (:require [reagent.core :as r]))

(def app-state
  (r/atom {:kraken-balance          {}
           :kraken-orders           {}
           :kraken-ticker           {}
           :kraken-portfolio-value  nil
           :portfolio-balance       nil
           :positions               []
           :orders                  []
           :connection-status       "disconnected"
           :errors                  {}
           :ib-log                  []
           :ib-debug-output         nil}))

(defn- add-ib-log-entry [log entry]
  (vec (take 100 (conj log entry))))

(defn dispatch! [{:keys [type data]}]
  (case type
    "connection"
    (swap! app-state assoc :connection-status (:status data))

    "kraken-balance"
    (swap! app-state assoc :kraken-balance data)

    "kraken-orders"
    (swap! app-state assoc :kraken-orders data)

    "kraken-ticker"
    (swap! app-state assoc :kraken-ticker data)

    "kraken-portfolio-value"
    (swap! app-state assoc :kraken-portfolio-value data)

    "portfolio-balance"
    (swap! app-state assoc :portfolio-balance data)

    "positions"
    (swap! app-state assoc :positions (or data []))

    "orders"
    (swap! app-state assoc :orders (or data []))

    "cell-error"
    (swap! app-state assoc-in [:errors (keyword (:cell data))] (:message data))

    "ib-error"
    (swap! app-state update :ib-log
           add-ib-log-entry {:type "error"
                             :text (:message data)
                             :ts   (js/Date.now)})
    nil))

(defonce ws-conn (atom nil))

(defn ws-url []
  (str (if (= "https:" js/location.protocol) "wss:" "ws:")
       "//" js/location.host "/ws"))

(defn connect-ws! []
  (let [ws (js/WebSocket. (ws-url))]
    (reset! ws-conn ws)
    (set! (.-onmessage ws)
          (fn [e]
            (let [msg (js->clj (js/JSON.parse (.-data e)) :keywordize-keys true)]
              (dispatch! msg))))
    (set! (.-onclose ws)
          (fn [_]
            (swap! app-state assoc :connection-status "disconnected")
            (js/setTimeout connect-ws! 5000)))
    (set! (.-onerror ws)
          (fn [_] (.close ws)))))

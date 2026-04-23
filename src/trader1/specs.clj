(ns trader1.specs
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]))

(def websocket-types
  #{"connection"
    "kraken-balance"
    "kraken-orders"
    "kraken-ticker"
    "kraken-portfolio-value"
    "portfolio-balance"
    "positions"
    "orders"
    "orders-state"
    "order-submission"
    "cell-error"
    "ib-error"})

(defn- non-blank-string? [v]
  (and (string? v) (not (str/blank? v))))

(defn- scalar-display-value? [v]
  (or (string? v) (number? v)))

(defn- asset-code? [v]
  (or (keyword? v)
      (non-blank-string? v)))

(s/def ::ticker-ms (s/nilable pos-int?))
(s/def ::balance-ms (s/nilable pos-int?))
(s/def ::orders-ms (s/nilable pos-int?))
(s/def ::enabled boolean?)
(s/def ::host non-blank-string?)
(s/def ::port pos-int?)
(s/def ::client-id int?)
(s/def ::snapshot-timeout-ms pos-int?)
(s/def ::refresh-ms pos-int?)
(s/def ::event-buffer-size pos-int?)
(s/def ::overflow-strategy #{:sliding :dropping :blocking})
(s/def ::server (s/keys :req-un [::port]))
(s/def ::kraken (s/keys :req-un [::enabled ::refresh-ms ::ticker-ms ::balance-ms ::orders-ms]))
(s/def ::ib (s/keys :req-un [::enabled ::host ::port ::client-id
                             ::snapshot-timeout-ms ::refresh-ms
                             ::event-buffer-size ::overflow-strategy]))
(s/def ::services (s/keys :req-un [::ib ::kraken]))
(s/def ::settings (s/keys :req-un [::server ::services]))

(s/def ::account non-blank-string?)
(s/def ::net-liquidation (s/nilable non-blank-string?))
(s/def ::buying-power (s/nilable non-blank-string?))
(s/def ::currency non-blank-string?)
(s/def ::portfolio-balance-row
  (s/keys :req-un [::account ::net-liquidation ::buying-power ::currency]))
(s/def ::portfolio-balance
  (s/nilable (s/coll-of ::portfolio-balance-row :kind vector?)))

(s/def ::symbol non-blank-string?)
(s/def ::sec-type non-blank-string?)
(s/def ::position scalar-display-value?)
(s/def ::avg-cost scalar-display-value?)
(s/def ::position-row
  (s/keys :req-un [::symbol ::sec-type ::currency ::position ::avg-cost]))
(s/def ::positions
  (s/nilable (s/coll-of ::position-row :kind vector?)))

(s/def ::action non-blank-string?)
(s/def ::order-id scalar-display-value?)
(s/def ::account-id non-blank-string?)
(s/def ::order-type non-blank-string?)
(s/def ::quantity scalar-display-value?)
(s/def ::limit-price (s/nilable scalar-display-value?))
(s/def ::status non-blank-string?)
(s/def ::filled (s/nilable scalar-display-value?))
(s/def ::remaining (s/nilable scalar-display-value?))
(s/def ::order-row
  (s/keys :req-un [::order-id ::account-id ::symbol ::action ::order-type
                   ::quantity ::limit-price ::status ::filled ::remaining]))
(s/def ::orders
  (s/nilable (s/coll-of ::order-row :kind vector?)))

(s/def ::orders-state-status #{"idle" "loading" "ready" "timeout" "error" "disconnected"})
(s/def ::order-count nat-int?)
(s/def ::updated-at nat-int?)
(s/def ::connection-status #{"connected" "disconnected"})
(s/def ::order-submission-status #{"idle" "pending" "success" "error"})
(s/def ::orders-state-data
  (s/and
   (s/keys :opt-un [::message ::order-count ::updated-at])
   #(contains? % :status)
   #(s/valid? ::orders-state-status (:status %))))
(s/def ::connection-data
  (s/and map?
         #(contains? % :status)
         #(s/valid? ::connection-status (:status %))))

(s/def ::asset-balance string?)
(s/def ::kraken-balance
  (s/nilable (s/map-of asset-code? ::asset-balance)))

(s/def ::open map?)
(s/def ::kraken-orders-data
  (s/nilable (s/keys :req-un [::open])))

(s/def ::ticker-entry map?)
(s/def ::kraken-ticker
  (s/nilable (s/map-of asset-code? ::ticker-entry)))

(s/def ::total-value non-blank-string?)
(s/def ::positions-value non-blank-string?)
(s/def ::cash-usd non-blank-string?)
(s/def ::kraken-portfolio-value-data
  (s/nilable (s/keys :req-un [::total-value ::positions-value ::cash-usd])))

(s/def ::cell #{"balance" "positions" "orders"})
(s/def ::message (s/nilable string?))
(s/def ::cell-error-data (s/keys :req-un [::cell ::message]))
(s/def ::ib-error-data (s/keys :req-un [::message]))

(s/def ::submitted-at nat-int?)
(s/def ::refresh-ok boolean?)
(s/def ::tif #{"DAY" "GTC"})
(s/def ::outside-rth boolean?)
(s/def ::order-submission-data
  (s/and
   (s/keys :opt-un [::message ::order-id ::symbol ::action ::quantity
                    ::order-type ::limit-price ::submitted-at ::refresh-ok
                    ::tif ::outside-rth])
   #(contains? % :status)
   #(s/valid? ::order-submission-status (:status %))))

(s/def ::exchange non-blank-string?)
(s/def ::ib-order-request
  (s/and
   (s/keys :req-un [::symbol ::action ::order-type ::quantity
                    ::exchange ::currency ::tif ::outside-rth]
           :opt-un [::limit-price])
   #(pos-int? (:quantity %))
   #(or (not (contains? % :limit-price))
        (and (number? (:limit-price %))
             (pos? (:limit-price %))))
   #(if (= "LMT" (:order-type %))
      (contains? % :limit-price)
      (not (contains? % :limit-price)))))

(defmulti websocket-message-type :type)

(defmethod websocket-message-type "connection" [_]
  (s/keys :req-un [::type ::data]))

(defmethod websocket-message-type "kraken-balance" [_]
  (s/keys :req-un [::type ::data]))

(defmethod websocket-message-type "kraken-orders" [_]
  (s/keys :req-un [::type ::data]))

(defmethod websocket-message-type "kraken-ticker" [_]
  (s/keys :req-un [::type ::data]))

(defmethod websocket-message-type "kraken-portfolio-value" [_]
  (s/keys :req-un [::type ::data]))

(defmethod websocket-message-type "portfolio-balance" [_]
  (s/keys :req-un [::type ::data]))

(defmethod websocket-message-type "positions" [_]
  (s/keys :req-un [::type ::data]))

(defmethod websocket-message-type "orders" [_]
  (s/keys :req-un [::type ::data]))

(defmethod websocket-message-type "orders-state" [_]
  (s/keys :req-un [::type ::data]))

(defmethod websocket-message-type "order-submission" [_]
  (s/keys :req-un [::type ::data]))

(defmethod websocket-message-type "cell-error" [_]
  (s/keys :req-un [::type ::data]))

(defmethod websocket-message-type "ib-error" [_]
  (s/keys :req-un [::type ::data]))

(defmethod websocket-message-type :default [_]
  (s/keys :req-un [::type]))

(s/def ::type websocket-types)
(s/def ::data any?)
(s/def ::websocket-message (s/multi-spec websocket-message-type :type))

(defn explain-str [spec value]
  (with-out-str (s/explain spec value)))

(defn assert-valid!
  [spec value context]
  (when-not (s/valid? spec value)
    (throw (ex-info (str "Spec validation failed for " context)
                    {:spec spec
                     :value value
                     :explain (explain-str spec value)})))
  value)

(defn assert-settings! [value]
  (assert-valid! ::settings value "settings"))

(defn assert-websocket-message! [value]
  (let [value' (assert-valid! ::websocket-message value "websocket message")
        payload-spec (case (:type value')
                       "connection" ::connection-data
                       "kraken-balance" ::kraken-balance
                       "kraken-orders" ::kraken-orders-data
                       "kraken-ticker" ::kraken-ticker
                       "kraken-portfolio-value" ::kraken-portfolio-value-data
                       "portfolio-balance" ::portfolio-balance
                       "positions" ::positions
                       "orders" ::orders
                       "orders-state" ::orders-state-data
                       "order-submission" ::order-submission-data
                       "cell-error" ::cell-error-data
                       "ib-error" ::ib-error-data)]
    (assert-valid! payload-spec (:data value') (str "websocket payload for " (:type value')))
    value'))

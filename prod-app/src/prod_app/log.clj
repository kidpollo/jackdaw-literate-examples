(ns prod-app.log
  "Thin wrappers around cambium's logging fns."
  (:require [cambium.codec :as codec]
            [cambium.core :as cambium-core]
            [cambium.logback.json.flat-layout :as flat]
            [clojure.set :as set]
            [metrics.meters :as meters]))


(flat/set-decoder! codec/destringify-val)

(defmacro debug
  "structured log at the INFO level"
  {:arglists '([msg] [mdc msg] [mdc throwable msg])}
  [& args]
  `(cambium-core/debug ~@args))

(defmacro info
  "structured log at the INFO level"
  {:arglists '([msg] [mdc msg] [mdc throwable msg])}
  [& args]
  `(cambium-core/info ~@args))

(defmacro warn
  "structured log at the WARN level"
  {:arglists '([msg] [mdc msg] [mdc throwable msg])}
  [& args]
  `(cambium-core/warn ~@args))

(defmacro error
  "structured log at the ERROR level"
  {:arglists '([msg] [mdc msg] [mdc throwable msg])}
  [& args]
  `(cambium-core/error ~@args))

(defn ->metric-name [title]
  ["sba-connector" "event" title])

(defn test-metrics [metrics-registry]
  (meters/mark! (meters/meter metrics-registry (->metric-name "test-event"))))

(defn logger
  "Super logger function"
  [{:keys [level event message throwable metrics-registry]
          :or {level :info
               message ""
               event "unknown-event"
               throwable nil
               metrics-registry nil}
          :as all-keys}
   & things]
  (let [other-keys (apply (partial dissoc all-keys) [:level :event :message :metrics-registry])
        log-fn #(cambium-core/log level % throwable message)]
    (as-> (apply merge things) mdc
      (select-keys mdc [:loan-application-id :loan-application-is-complete :problems
                        :opportunity-id :requested-amount :tax-id :id :published-timestamp
                        :published-by])
      (merge mdc
             {:event event}
             other-keys)
      (log-fn mdc)))

  (when metrics-registry
    (meters/mark! (meters/meter metrics-registry (->metric-name event)))))

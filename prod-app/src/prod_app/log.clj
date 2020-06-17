(ns prod-app.log
  "Thin wrappers around cambium's logging fns."
  (:require [cambium.codec :as codec]
            [cambium.core :as cambium-core]
            [cambium.logback.json.flat-layout :as flat]
            [clojure.set :as set]
            [metrics.meters :as meters]))


(flat/set-decoder! codec/destringify-val)

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
      (select-keys mdc [:id
                        :body
                        :status
                        :result
                        :loan-number
                        :topic-name
                        :opportunity-id
                        :loan-application-id
                        :loan-number
                        :sba-loan-number
                        :sba-result
                        :sba-status
                        :metadata/id
                        :sba/status
                        :sba/loan-number
                        :sba/result
                        :metadata/loan-application-id])
      (set/rename-keys mdc {:sba/status :status
                            :sba/loan-number :sba-loan-number
                            :sba/result :result
                            :metadata/id :id
                            :metadata/loan-application-id :loan-application-id})
      (merge mdc
             {:event event}
             other-keys)
      (log-fn mdc)))

  (when metrics-registry
    (meters/mark! (meters/meter metrics-registry (->metric-name event)))))

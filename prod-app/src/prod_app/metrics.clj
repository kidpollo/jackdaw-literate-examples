(ns prod-app.metrics
  (:require [prod-app.log :as log]
            [integrant.core :as ig]
            [metrics.core :refer [new-registry]]
            [metrics.reporters.jmx :as jmx]))

(defmethod ig/init-key ::registry
  [_ _]
  (log/info "Created metrics registry")
  (new-registry))

(defmethod ig/init-key ::prometheus-reporter
  [_ {:keys [registry]}]
  (if registry
    (let [reporter (jmx/reporter registry {:domain "fundingcircle"})]
      (jmx/start reporter)
      (log/info "Initialised Prometheus metrics reporter")
      reporter)
    {:enabled false}))

(ns prod-app.specs.reader-specs
  "Spec for reads from internal state and message streams.
  Use this spec for validation AFTER READING FROM INTERNAL STATE or
  reading messages from Kafka"
  (:require [clojure.spec.alpha :as s]
            [prod-app.specs.attributes]))

(s/def ::loan-application
  (s/keys :req-un [:external/opportunity-id
                   :loan-application/requested-amount
                   :loan-application/loan-application-id
                   :loan-application/tax-id]))

(s/def ::external-loan-application
  (s/keys :req-un [:external/opportunity-id
                   :loan-application/requested-amount
                   :loan-application/tax-id]))

(s/def ::external-trigger
  (s/keys :req-un [:external/opportunity-id
                   :external/trigger-id]))

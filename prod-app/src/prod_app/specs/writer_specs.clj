(ns prod-app.specs.writer-specs
  "Spec for writes to internal state and message streams.
  Use this spec for validation BEFORE WRITING TO INTERNAL STATE or publishing messages to Kafka."
  (:require [clojure.spec.alpha :as s]
            [prod-app.specs.attributes]))

(s/def ::loan-application
  (s/keys :req-un [:metadata/loan-application-id]
          :opt-un [:loan-application/requested-amount
                   :external/opportunity-id
                   :loan-application/tax-id]))

(s/def ::result
  (s/keys :req-un [:sba-response/status
                   :sba-response/result]
          :opt-un [:sba-response/loan-number]))

(s/def ::metadata
  (s/keys :req-un [:metadata/id
                   :metadata/published-timestamp
                   :metadata/published-by]))

(s/def ::sba-loan-application-updated-event
  (s/merge ::loan-application
           (s/keys :req-un [:loan-application/loan-application-is-complete])
           ::metadata
           (s/keys :req-un [:loan-application/problems])))

(s/def ::sba-result-available-event
  (s/merge ::result
           ::loan-application
           ::metadata))

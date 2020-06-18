(ns prod-app.specs.writer-specs
  "Spec for writes to internal state and message streams.
  Use this spec for validation BEFORE WRITING TO INTERNAL STATE or publishing messages to Kafka."
  (:require [clojure.spec.alpha :as s]
            [prod-app.specs.attributes]))

(s/def ::loan-application
  (s/keys :req-un [:writer-specs.metadata/loan-application-id]

          :opt-un [:writer-specs.loan-application/employee-count
                   :writer-specs.loan-application/requested-amount
                   :writer-specs.loan-application/sba-loan-number
                   :writer-specs.company/tax-id
                   :writer-specs.company/business-name
                   :writer-specs.company/city-name
                   :writer-specs.company/country-code
                   :writer-specs.company/state-code
                   :writer-specs.company/street-name
                   :writer-specs.company/zip-code
                   :writer-specs.company/primary-phone]))

(s/def :writer-specs.sba/status #{"success" "failure" "cancelled"})
(s/def :writer-specs.sba/result string?)
(s/def :writer-specs.sba/loan-number (s/nilable string?))

(s/def ::result
  (s/keys :req-un [:writer-specs.sba/status
                   :writer-specs.sba/result]
          :opt-un [:writer-specs.sba/loan-number]))

(s/def :writer-specs.metadata/loan-application-id :metadata/loan-application-id)
(s/def :writer-specs.metadata/exteral-opportunity-id :external/opportunity-id)

(s/def :writer-specs.metadata/id uuid?)
(s/def :writer-specs.metadata/published-timestamp int?)
(s/def :writer-specs.metadata/published-by string?)

(s/def ::metadata
  (s/keys :req-un [:writer-specs.metadata/id
                   :writer-specs.metadata/published-timestamp
                   :writer-specs.metadata/published-by]))

(s/def :writer-specs.metadata/loan-application-is-complete boolean?)
(s/def :writer-specs.metadata/problem string?)

(s/def :writer-specs.metadata/problems
  (s/* :writer-specs.metadata/problem))

(s/def ::sba-loan-application-updated-event
  (s/merge ::loan-application
           (s/keys :req-un [:writer-specs.metadata/loan-application-is-complete])
           ::metadata
           (s/keys :req-un [:writer-specs.metadata/problems])))

(s/def ::sba-result-available-event
  (s/merge ::result
           ::loan-application
           ::metadata))

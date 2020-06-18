(ns prod-app.specs.reader-specs
  "Spec for reads from internal state and message streams.
  Use this spec for validation AFTER READING FROM INTERNAL STATE or
  reading messages from Kafka"
  (:require [clojure.spec.alpha :as s]
            [prod-app.specs.attributes]))

(s/def ::loan-application
  (s/keys :req-un [:external/opportunity-id
                   :metadata/loan-application-id
                   :loan-application/employee-count
                   :loan-application/requested-amount
                   :company/tax-id
                   :company/business-name
                   :company/city-name
                   :company/state-code
                   :company/street-name
                   :company/zip-code
                   :company/primary-phone]
          :opt-un [:loan-application/sba-loan-number
                   :company/country-code]))

(s/def ::external-loan-application
  (s/keys :req-un [:external/opportunity-id
                   :loan-application/employee-count
                   :loan-application/requested-amount
                   :company/tax-id
                   :company/business-name
                   :company/city-name
                   :company/state-code
                   :company/street-name
                   :company/zip-code
                   :company/primary-phone]
          :opt-un [:company/country-code]))

(s/def ::external-trigger
  (s/keys :req-un [:external/opportunity-id
                   :exteral/trigger-id]))

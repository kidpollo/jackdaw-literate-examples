(ns prod-app.specs.attributes
  "This namespace contains attribute specs."
  (:require [clojure.spec.alpha :as s]))

(s/def :loan-application/employee-count string?)
(s/def :loan-application/requested-amount string?)
(s/def :loan-application/sba-loan-number string?)

(s/def :company/tax-id string?)
(s/def :company/business-name string?)
(s/def :company/city-name string?)
(s/def :company/country-code string?)
(s/def :company/state-code string?)
(s/def :company/street-name string?)
(s/def :company/zip-code string?)
(s/def :company/primary-phone string?)

(s/def :metadata/loan-application-id uuid?)

(s/def :external/opportunity-id string?)
(s/def :external/trigger-id uuid?)

(ns prod-app.specs.attributes
  "This namespace contains attribute specs."
  (:require [clojure.string :as str]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen] ))

(s/def :loan-application/employee-count string?)
(s/def :loan-application/requested-amount string?)
(s/def :loan-application/sba-loan-number string?)

(def tax-id?
  (s/with-gen #(re-matches #"[0-9]{10}" %)
    #(gen/return (str/join (map str (take 10 (repeatedly (fn [] (rand-int 10)))))))))

(s/def :company/tax-id tax-id?)
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

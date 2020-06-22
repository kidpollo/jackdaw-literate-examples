(ns prod-app.specs.attributes
  "This namespace contains attribute specs."
  (:require [clojure.string :as str]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen] ))

(s/def :external/opportunity-id string?)
(s/def :external/trigger-id uuid?)

(s/def :loan-application/requested-amount string?)
(s/def :loan-application/loan-application-id uuid?)
(s/def :loan-application/tax-id 
  (s/with-gen #(re-matches #"[0-9]{10}" %)
    #(gen/return (str/join (map str (take 10 (repeatedly (fn [] (rand-int 10)))))))))
(s/def :loan-application/loan-application-is-complete boolean?)
(s/def :loan-application/problem string?)
(s/def :loan-application/problems
  (s/* :loan-application/problem))

(s/def :metadata/id uuid?)
(s/def :metadata/published-timestamp int?)
(s/def :metadata/published-by string?)

(s/def :sba-response/status #{"success" "failure" "cancelled"})
(s/def :sba-response/result string?)
(s/def :sba-response/loan-number (s/nilable string?))

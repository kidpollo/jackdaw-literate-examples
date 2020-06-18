(ns prod-app.topology-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.edn :as edn]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [prod-app.topology :as sc]
            [prod-app.log :as log]
            [prod-app.specs.reader-specs :as r-specs]
            [prod-app.specs.writer-specs :as w-specs]
            [metrics.core :as metrics]
            [metrics.meters :as meters]))

(defn gen-external-loan-app []
  (gen/generate (s/gen ::r-specs/loan-application)))

(defn metric-total [registry metric-name]
  (:total (meters/rates
           (meters/meter
            registry
            (log/->metric-name metric-name)))))

(deftest update-loan-application-test
  (testing "valid loan app"
    (let [state (atom {}) ;; yay transducers !!
          registry (metrics/new-registry)
          external-loan-application (gen-external-loan-app)
          opportunity-id (:opportunity-id external-loan-application)
          [[k v]] (transduce
                   (sc/update-loan-application state
                                               :swap-fn swap!
                                               :registry registry)
                   concat
                   [[opportunity-id external-loan-application]])]
      (is (= opportunity-id k) "output record key matches the opportunity-id")
      (is (s/valid? ::w-specs/sba-loan-application-updated-event v))
      (is (= opportunity-id (:opportunity-id v))
          "input opportunity-id matches the output opportunity-id")
      (is (= true (:loan-application-is-complete v))
          "loan application is set to complete")
      (is (nil? (not-empty (:problems v)))
          "problems are empty")
      (is (= 1 (metric-total registry "loan-application-complete")))))

  (testing "invalid loan app"
    (let [state (atom {})
          registry (metrics/new-registry)
          external-loan-application (dissoc (gen-external-loan-app)
                                            :zip-code)
          opportunity-id (:opportunity-id external-loan-application)
          [[_ v]] (transduce
                   (sc/update-loan-application state
                                               :swap-fn swap!
                                               :registry registry)
                   concat
                   [[opportunity-id external-loan-application]])]
      (is (= false (:loan-application-is-complete v))
          "loan application is set to incomplete")
      (is (not-empty (:problems v))
          "includes the problems")
      (is (= 1 (metric-total registry "loan-application-incomplete"))))))

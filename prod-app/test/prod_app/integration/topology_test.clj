(ns prod-app.integration.topology-test
  (:require
   [clojure.test :as t :refer [deftest is testing]]
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as gen]
   [jackdaw.test :as jd.test]
   [jackdaw.test.commands.watch :as watch]
   [metrics.core :as metrics]
   [prod-app.integration.runner :as test-runner]
   [prod-app.integration.journal :as journal]
   [prod-app.specs.reader-specs :as r-specs]
   [prod-app.specs.writer-specs :as w-specs]
   [prod-app.config :as config]
   [prod-app.specs.attributes]
   [prod-app.streams :as streams]
   [prod-app.topics :as topics]
   [prod-app.topic-metadata :as topic-metadata]))

(defn topology-under-test [tmd]
  (let [{:keys [config topology-builder topic-metadata xforms deref-fn get-fn swap-fn]}
        (assoc (::streams/topology config/sba-connector-app-state)
               :topic-metadata tmd)]
    (streams/build-topology config
                            topology-builder
                            topic-metadata
                            xforms
                            deref-fn
                            get-fn
                            swap-fn
                            (metrics/new-registry))))

(defn run-integration-test [mode commands assertions]
  (binding [watch/*default-watch-timeout* (if (= :mock mode) 1000 10000)]
    (let [tmd (topics/resolve-serdes (topic-metadata/build-topic-metadata 1 1) false)]
      (test-runner/run-test
       mode tmd (topology-under-test tmd)
       (fn [machine]

         (let [{:keys [results journal]} (jd.test/run-test machine commands)]
           (is (test-runner/result-ok? results))
           (journal/summarise-and-export journal)
           (assertions journal)))))))

(defn mock-sba-endpoint [mock-responder]
  (fn [url post-body]
    (let [{:keys [headers body]} post-body]
      (is (= "application/json" (get headers "content-type")))
      (mock-responder body))))

(defmacro with-mock-sba-endpoint [[mock-responder] & body]
  `(with-redefs [clj-http.client/post (mock-sba-endpoint ~mock-responder)]
     ~@body))

(defn gen-external-loan-app []
  (gen/generate (s/gen ::r-specs/loan-application)))

(deftest integration-test
  (testing "SBA Builder"
    (doseq [[api-response loan-application-fn] [[{:status 200
                                                  :headers {"server" "da-government-box"}
                                                  :body
                                                  "{\"loan-number\": \"123\"}"}
                                                 gen-external-loan-app]]]
      (with-mock-sba-endpoint [(fn [req]
                                 api-response)]
        (let [loan-application (loan-application-fn)
              opportunity-id (:opportunity-id loan-application)]
          (run-integration-test
           :mock
           [[:write! :external-loan-application loan-application {:key opportunity-id}]
            [:watch (fn [j]
                      (let [ms (journal/messages j :sba-loan-application-updated)]
                        (> (count ms) 0)))]
            [:write!
             :external-trigger
             {:opportunity-id opportunity-id
              :trigger-id (java.util.UUID/randomUUID)}
             {:key opportunity-id}]
            [:watch (fn [j]
                      (let [ms (journal/messages j :sba-result-available)]
                        (> (count ms) 0)))]]
           (fn [j]
             (let [ms (journal/messages j :sba-result-available)
                   result (first ms)]
               (is (s/valid? ::w-specs/sba-result-available-event result))
               (is (= "123" (:loan-number result)))
               (is (= opportunity-id (:opportunity-id result)))))))))))

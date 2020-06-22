(s/explain-data ::loan-application 
                {:loan-application-id (java.util.UUID/randomUUID)
                 :opportunity-id "external-id-for-a-loan"
                 :requested-amount "100"
                 :tax-id "foo"})

(s/valid? ::loan-application
          {:loan-application-id (java.util.UUID/randomUUID)
           :opportunity-id "external-id-for-a-loan"
           :requested-amount "100"
           :tax-id "1111111111"})

(s/valid? ::external-trigger
          {:trigger-id (java.util.UUID/randomUUID)
           :opportunity-id "external-id-for-a-loan"})

(s/valid? ::sba-loan-application-updated-event
          {:loan-application-id (java.util.UUID/randomUUID)
           :loan-application-is-complete true
           :problems []
           :opportunity-id "external-id-for-a-loan"
           :requested-amount "100"
           :tax-id "1111111111"
           :id (java.util.UUID/randomUUID)
           :published-timestamp 1
           :published-by "test"})

(s/valid? ::sba-result-available-event
          {:loan-application-id (java.util.UUID/randomUUID)
           :opportunity-id "external-id-for-a-loan"
           :requested-amount "100"
           :tax-id "1111111111"
           :id (java.util.UUID/randomUUID)
           :published-timestamp 1
           :published-by "test"
           :status "success"
           :result ""
           :loan-number "123"})

(ns clojure.test)
(run-tests 'prod-app.topology-test)

(require '[prod-app.integration.fixtures]
         '[prod-app.integration.journal]
         '[prod-app.integration.runner])

(ns clojure.test)
(run-tests 'prod-app.integration.topology-test)

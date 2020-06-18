(s/explain-data ::loan-application 
                {:loan-application-id (java.util.UUID/randomUUID)
                 :employee-count "2"
                 :requested-amount "100"
                 :tax-id "1"
                 :business-name "foo"
                 :city-name "bar"
                 :state-code "AZ"
                 :street-name "Abbey Road"
                 :zip-code "666"
                 :primary-phone "1-800-EMPIRE"})

(s/valid? ::loan-application
          {:loan-application-id (java.util.UUID/randomUUID)
           :employee-count "2"
           :requested-amount "100"
           :tax-id "1111111111"
           :business-name "foo"
           :city-name "bar"
           :state-code "AZ"
           :street-name "Abbey Road"
           :zip-code "666"
           :primary-phone "1-800-EMPIRE"})

(ns clojure.test)
(run-tests 'prod-app.topology-test)

(require '[prod-app.integration.fixtures]
         '[prod-app.integration.journal]
         '[prod-app.integration.runner])

(ns clojure.test)
(run-tests 'prod-app.integration.topology-test)

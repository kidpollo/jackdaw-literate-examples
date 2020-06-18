(ns clojure.test)
(run-tests 'prod-app.topology-test)

(require '[prod-app.integration.fixtures]
         '[prod-app.integration.journal]
         '[prod-app.integration.runner])

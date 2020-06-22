(ns prod-app.topic-metadata
  (:require [clojure.edn :as edn]
            [integrant.core :as ig]))

(defn build-topic-metadata
  [replication-factor partition-count]
  {:external-loan-application
   {:topic-name "external-loan-application-1"
    :partition-count partition-count
    :replication-factor replication-factor
    :key-serde {:serde-keyword :jackdaw.serdes/string-serde}
    :value-serde {:serde-keyword :jackdaw.serdes.avro.confluent/serde
                  :schema-filename "schemas/external-loan-application.json"
                  :key? false}}

   :external-trigger
   {:topic-name "external-trigger-1"
    :partition-count partition-count
    :replication-factor replication-factor
    :key-serde {:serde-keyword :jackdaw.serdes/string-serde}
    :value-serde {:serde-keyword :jackdaw.serdes.avro.confluent/serde
                  :schema-filename "schemas/external-trigger.json"
                  :key? false}}

   :sba-loan-application-updated
   {:topic-name "sba-loan-application-updated-1"
    :register-schema? true
    :partition-count partition-count
    :replication-factor replication-factor
    :key-serde {:serde-keyword :jackdaw.serdes/string-serde}
    :value-serde {:serde-keyword :jackdaw.serdes.avro.confluent/serde
                  :schema-filename "schemas/sba-loan-application-updated.json"
                  :key? false}}

   :sba-result-available
   {:topic-name "sba-result-available-1"
    :register-schema? true
    :partition-count partition-count
    :replication-factor replication-factor
    :key-serde {:serde-keyword :jackdaw.serdes/string-serde}
    :value-serde {:serde-keyword :jackdaw.serdes.avro.confluent/serde
                  :schema-filename "schemas/sba-result-available.json"
                  :key? false}}})


(defmethod ig/init-key ::sba-connector [_ {:keys [config]}]
  (let [replication-factor (edn/read-string (:replication-factor config))]
    (build-topic-metadata replication-factor 100)))

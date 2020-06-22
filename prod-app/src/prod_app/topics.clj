(ns prod-app.topics
  (:require [clojure.algo.generic.functor :as functor]
            [clj-http.client :as client]
            [prod-app.log :as log]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [integrant.core :as ig]
            [jackdaw.admin :as ja]
            [jackdaw.serdes.avro :as jsa]
            [jackdaw.serdes.avro.schema-registry :as sr]
            [jackdaw.serdes.resolver :as resolver]))

(defn slurp-avro
  "Slurps a serde."
  [filename]
  (if-let [resource (io/resource filename)]
    (slurp resource)
    (throw (ex-info
            (format "Didn't find schema file %s in resources" filename)
            {}))))

(defn register-schema [topic-name filename schema-registry-url]
  (let [schema (slurp-avro filename)
        json-schema-str (-> schema
                            json/read-str
                            json/write-str)
        payload (json/write-str {:schema json-schema-str})
        url (str schema-registry-url
                 "/subjects/" topic-name "-value/versions")
        response (client/post url {:body payload :content-type "application/json"})
        body (-> response :body json/read-str)]
    (when (= (:status response) 200)
      (log/info
       (format "Successfully registered %s schema with id %s"
               topic-name (get body "id"))))))

(def +type-registry-with-uuid-type+
  (merge jsa/+base-schema-type-registry+ jsa/+UUID-type-registry+))

(defn resolver [schema-registry-url]
 (if schema-registry-url
   (resolver/serde-resolver :schema-registry-url schema-registry-url
                            :type-registry +type-registry-with-uuid-type+)
   (resolver/serde-resolver :schema-registry-url ""
                            :type-registry +type-registry-with-uuid-type+
                            :schema-registry-client (sr/mock-client))))

(defn resolve-serdes [topic-metadata schema-registry-url]
  (functor/fmap #(assoc %
                :key-serde ((resolver schema-registry-url) (:key-serde %))
                :value-serde ((resolver schema-registry-url) (:value-serde %)))
        topic-metadata))

(defmethod ig/init-key ::topics [_ {:keys [config topic-metadata]}]
  (log/info "Creating topics if they dont exist")
  (with-open [client (ja/->AdminClient (:client-config config))]
    (try
      (ja/create-topics! client (vals topic-metadata))
      (catch Exception e
        (log/info (str "Couldnt create topic: " (.getMessage e))))))

  (log/info "Registering schemas")
  (doseq [[_ topic-config] (->> topic-metadata
                                (filter #(:register-schema? (second %))))]
    (register-schema (:topic-name topic-config)
                     (get-in topic-config [:value-serde :schema-filename])
                     (:schema-registry-url config)))

  (log/info topic-metadata "Resolving topic metadata")
  (resolve-serdes topic-metadata (:schema-registry-url config)))

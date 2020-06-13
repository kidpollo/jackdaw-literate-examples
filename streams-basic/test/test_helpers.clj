(ns test-helpers)
(ns test-helpers
  (:require [jackdaw.streams.mock :as j.s.m]
            [jackdaw.test :as j.t]
            [jackdaw.test.fixtures :as j.t.f]))


(def ^:dynamic *use-kafka* false)

(defn test-transport
  [topics build-topology-fn]
  (if *use-kafka*
    (let [kafka-test-config {"bootstrap.servers" "localhost:9092"
                             "group.id" "ce-data-aggregator-test"}]
      (j.t/kafka-transport
       kafka-test-config
       topics))
    (let [mock-driver (-> (j.s.m/streams-builder)
                          (build-topology-fn)
                          (j.s.m/streams-builder->test-driver))]
      (j.t/mock-transport {:driver mock-driver}
                          topics))))


(defn results-ok? [tm-results]
  (every? #(= :ok %) (map :status tm-results)))

(defn run-commands [topics build-topology-fn app-config commands]
  (j.t.f/with-fixtures [(j.t.f/integration-fixture
                         (fn [_]
                           build-topology-fn)
                         {:broker-config {"bootstrap.servers" "localhost:9092"}
                          :topic-metadata topics
                          :app-config (-> app-config
                                          (update "application.id" #(str % "-" (java.util.UUID/randomUUID)))
                                          (assoc "cache.max.bytes.buffering" "0"))
                          :enable? *use-kafka*})]
    (j.t/with-test-machine
      (test-transport topics build-topology-fn)
      (fn [machine]
        (j.t/run-test machine commands)))))


(defn raw-messages
  [journal topic-name]
  (sort-by :offset (get-in journal [:topics topic-name])))

(defn messages
  [journal topic-name]
  (->> (raw-messages journal topic-name)))

(defn messages-by-kv-fn
  [journal topic-name ks pred]
  (->> (messages journal topic-name)
       (filter (fn [m]
                 (pred (get-in m ks))))))

(defn messages-by-kv
  [journal topic-name ks value]
  (messages-by-kv-fn journal topic-name ks #(= value %)))

(defn message-by-kv
  [journal topic-name ks value]
  (first (messages-by-kv-fn journal topic-name ks #(= value %))))

(defn by-key [topic-name ks id]
  (fn [journal]
    (last (messages-by-kv journal topic-name ks id))))

(defn by-keys [topic-name ks ids]
  (fn [journal]
    (messages-by-kv-fn journal topic-name ks (set ids))))

(defn by-id [topic id]
  (by-key topic [:id] id))

(defn by-message-key [topic key]
  (by-key topic [:key] key))

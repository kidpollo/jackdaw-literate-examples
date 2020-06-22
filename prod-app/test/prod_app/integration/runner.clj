(ns prod-app.integration.runner
  (:require
   [clojure.pprint :as pp]
   [jackdaw.streams :as k]
   [jackdaw.test :as jd.test]
   [prod-app.integration.fixtures :as fix]
   [jackdaw.streams.protocols :as proto]
   [jackdaw.serdes.resolver :as jd-resolver]
   [jackdaw.serdes.avro.schema-registry :as reg])
  (:import
   (java.util Properties)
   (org.apache.kafka.streams TopologyTestDriver)))

(def kafka-test-config
  {"schema.registry.url" "http://localhost:8081"
   "processing.guarantee" "exactly_once"
   "bootstrap.servers" "localhost:9092"
   "commit.interval" "30000"
   "num.stream.threads" "3"
   "cache.max.bytes.buffering" "0"
   "replication.factor" "1"
   "kafka.streams.state.dir" "/tmp/kafka_streams"
   "sidecar.port" "9090"
   "sidecar.host" "0.0.0.0"})

(def test-consumer-config
  {"bootstrap.servers" "localhost:9092"
   "group.id" "sba-connector-test"})

(defn schema-reg-client [mode]
  (cond
    (= mode :broker) nil
    (= mode :mock) (reg/mock-client)
    :else
    (throw (ex-info "Unknown test mode. Expected one of [:broker :mock]"
                    {:mode mode}))))

(defn get-env [k]
  (get (System/getenv) k))

(defn app-config-for-test [app-name]
  {:broker-config {"bootstrap.servers" "localhost:9092"}
   :app-config (assoc kafka-test-config "application.id" app-name)})

(defn props-for [x]
  (doto (Properties.)
    (.putAll (reduce-kv (fn [m k v]
                          (assoc m (str k) (str v)))
                        {}
                        x))))

(defn result-ok? [tm-result]
  (doseq [err (remove (fn [r]
                        (= :ok (:status r))) tm-result)]
    (pp/pprint err))
  (every? #(= :ok %) (map :status tm-result)))

(defn messages-for [journal topic]
  (map :value (get-in journal [:topics (name topic)])))

(defn find-by [ml mk value]
  (filter (fn [v]
            (= (get v mk) value)) ml))

(defn watch-by-field [topic mk value]
  (fn [j]
    (not (empty? (find-by (messages-for j topic)
                          mk value)))))

(defn kafka-transport
  [topics]
  (jd.test/kafka-transport test-consumer-config topics))

(defn mock-transport-config [build-topology-fn test-config]
  {:driver (let [builder (k/streams-builder)
                 topology (.build (proto/streams-builder* (build-topology-fn builder)))]
             (TopologyTestDriver.
              topology
              (props-for (:app-config test-config))))})

(defn mock-transport
  [topics build-topology-fn test-config]
  (jd.test/mock-transport (mock-transport-config build-topology-fn test-config)
                          topics))

(defn test-transport
  [mode topics build-topology-fn test-config]
  (cond
    (= mode :broker) (kafka-transport topics)
    (= mode :mock) (mock-transport topics build-topology-fn test-config)
    :else
    (throw (ex-info "Unknown test mode. Expected one of [:broker :mock]"
                    {:mode mode}))))

;; Run a test ...

(defn run-test [mode topics build-topology-fn test-fn]
  (let [test-config (app-config-for-test "sba-test")]
    (fix/with-fixtures [(fix/integration-fixture (fn [_]
                                                   build-topology-fn)
                                                 (assoc test-config
                                                        :topic-metadata topics
                                                        :enable? (= mode :broker)))]
      (jd.test/with-test-machine
        (test-transport mode topics build-topology-fn test-config)
        test-fn))))

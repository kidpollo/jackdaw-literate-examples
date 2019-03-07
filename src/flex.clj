(ns flex)
(ns flex
  ""
  (:gen-class)
  (:require [clojure.string :as str]
            [clojure.tools.logging :refer [info]]
            [jackdaw.streams :as j]
            [jackdaw.serdes.edn :as jse])
  (:import [org.apache.kafka.common.serialization Serdes]))

(defn topic-config
  "Takes a topic name and returns a topic configuration map, which may
  be used to create a topic or produce/consume records."
  [topic-name]
  {:topic-name topic-name
   :partition-count 1
   :replication-factor 1
   :key-serde (jse/serde)
   :value-serde (jse/serde)})

(defn app-config
  "Returns the application config."
  []
  {"application.id" "flex-app"
   "bootstrap.servers" "localhost:9092"
   "cache.max.bytes.buffering" "0"})

(defn build-topology
  ""
  [builder]
  (let [event-stream (j/kstream builder (topic-config "events"))
        user-sources-table (j/ktable builder (topic-config "user-sources"))
        events-by-source (-> event-stream
                             (j/map (fn [[_ v]]
                                      [(:source-id v) v]))
                             (j/through (topic-config "events-by-source")))
        events-by-user-and-source (-> events-by-source
                                      (j/left-join user-sources-table
                                                   (fn [event user-source]
                                                     (merge event user-source))
                                                   (topic-config "")
                                                   (topic-config ""))
                                      (j/map (fn [[_ v]]
                                               [[(:user-id v) (:source-id v)] v]))
                                      (j/through (topic-config "events-by-user-and-source")))]
    (-> events-by-user-and-source
        (j/group-by-key (topic-config ""))
        (j/aggregate (constantly {:count 0 :sum 0})
                     (fn [acc [k v]]
                       (-> acc
                           (update :count inc)
                           (update :sum #(+ % (:value v)))
                           (merge (select-keys v [:name :user-id]))))
                     (topic-config "user-stats"))
        (j/to-kstream)
        (j/to (topic-config "user-stats")))
    builder))

(defn start-app
  "Starts the stream processing application."
  [app-config]
  (let [builder (j/streams-builder)
        topology (build-topology builder)
        app (j/kafka-streams topology app-config)]
    (j/start app)
    (info "flex is up")
    app))

(defn stop-app
  "Stops the stream processing application."
  [app]
  (j/close app)
  (info "flex is down"))

(defn -main
  [& _]
  (start-app (app-config)))

(clojure.pprint/pprint (do (ns user)
(confluent/start)))

(clojure.pprint/pprint (do (ns user)
(stop-pipe)

(Thread/sleep 1000)

(start-pipe)))

(clojure.pprint/pprint (do (ns user)
(list-topics)))

(clojure.pprint/pprint (do (ns user)
(publish (topic-config "input") "mundo")))

(clojure.pprint/pprint (do (ns user)
(get-keyvals (topic-config "output"))))

(clojure.pprint/pprint (do (ns user)
(stop-flex)

(Thread/sleep 1000)

(start-flex)))

(clojure.pprint/pprint (do (ns user)
(list-topics)))

(ns user)
(def user-1 (java.util.UUID/randomUUID))

(def source-1 (java.util.UUID/randomUUID))

(def source-2 (java.util.UUID/randomUUID))

(def user-2 (java.util.UUID/randomUUID))

(def source-3 (java.util.UUID/randomUUID))


(publish (topic-config "user-sources")
         source-1
         {:name "step counter"
          :user-id user-1})

(publish (topic-config "user-sources")
         source-2
         {:name "pushup counter"
          :user-id user-1})

(publish (topic-config "user-sources")
         source-3
         {:name "step counter"
          :user-id user-2})

(publish (topic-config "events")
         {:event-id (java.util.UUID/randomUUID)
          :source-id source-1
          :value 1
          :timestamp (System/currentTimeMillis)})

(publish (topic-config "events")
         {:event-id (java.util.UUID/randomUUID)
          :source-id source-2
          :value 2
          :timestamp (System/currentTimeMillis)})

(publish (topic-config "events")
         {:event-id (java.util.UUID/randomUUID)
          :source-id source-3
          :value 100
          :timestamp (System/currentTimeMillis)})

(publish (topic-config "events")
         {:event-id (java.util.UUID/randomUUID)
          :source-id source-2
          :value 100
          :timestamp (System/currentTimeMillis)})

(clojure.pprint/pprint (do (ns user)
(get-keyvals (topic-config "events"))

(get-keyvals (topic-config "user-sources"))

(get-keyvals (topic-config "events-by-source"))

(get-keyvals (topic-config "events-by-user-and-source"))

(get-keyvals (topic-config "user-stats"))

(get-keyvals (topic-config "flex-app-user-stats-changelog"))))

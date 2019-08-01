(ns flex-test-2)
(ns flex-test-2
  (:require [clojure.test :refer :all]
            [jackdaw.streams.mock :as j.s.m]
            [jackdaw.test :as j.t]
            [jackdaw.test.commands.watch :as watch]
            [test-helpers]))

(defn user-source
  [user source name]
  {:id source
   :name name
   :user-id user})

(defn event
  [source value]
  {:id (java.util.UUID/randomUUID)
   :event-id (java.util.UUID/randomUUID)
   :source-id source
   :value value
   :timestamp (System/currentTimeMillis)})

(deftest topology-test
  (let [topology-topics {:events (flex/topic-config "events")
                         :user-sources (flex/topic-config "user-sources")
                         :events-by-source (flex/topic-config "events-by-source")
                         :events-by-user-and-source (flex/topic-config "events-by-user-and-source")
                         :user-stats (flex/topic-config "user-stats")}

        user-1 (java.util.UUID/randomUUID)
        user-2 (java.util.UUID/randomUUID)

        source-1 (java.util.UUID/randomUUID)
        source-2 (java.util.UUID/randomUUID)
        source-3 (java.util.UUID/randomUUID)

        ;; User Sources
        user-1-step-counter (user-source user-1 source-1 "step counter")
        user-1-pushup-counter (user-source user-1 source-2 "pushup counter")
        user-2-step-counter (user-source user-2 source-3 "step counter")]

    (binding [test-helpers/*use-kafka* true
              watch/*default-watch-timeout* 1000]
      (testing "user-1 takes 1 step"
        (let [step (event source-1 1)
              commands [[:write! :user-sources user-1-step-counter]
                        [:write! :events step]
                        [:watch (test-helpers/by-message-key :user-stats [user-1 source-1])]]
              {:keys [results journal]} (test-helpers/run-commands
                                         topology-topics
                                         flex/build-topology
                                         (flex/app-config)
                                         commands)]
          ;; check status on every command
          (is (test-helpers/results-ok results))
          ;; gets one step entry with one step count
          (is (= 1 (-> ((test-helpers/by-message-key :user-stats [user-1 source-1]) journal)
                       :value
                       :count)))
          (is (= 1 (-> ((test-helpers/by-message-key :user-stats [user-1 source-1]) journal)
                       :value
                       :sum)))))

      (testing "user-2 steps gets 2 counts of 50 steps while user-1 does 3 pushups"
        (let [commands [[:write! :user-sources user-1-step-counter]
                        [:write! :user-sources user-1-pushup-counter]
                        [:write! :user-sources user-2-step-counter]
                        [:write! :events (event source-3 50)]
                        [:write! :events (event source-2 3)]
                        [:write! :events (event source-3 50)]
                        [:watch (test-helpers/by-message-key :user-stats [user-2 source-3])]]
              {:keys [results journal]} (test-helpers/run-commands
                                         topology-topics
                                         flex/build-topology
                                         (flex/app-config)
                                         commands)]
          (is (every? #(= :ok (:status %)) results))
          ;; there where two step updates for user 3
          (is (= 2 (-> ((test-helpers/by-message-key :user-stats [user-2 source-3]) journal)
                       :value
                       :count)))
          ;; total steps for user 3 was 100
          (is (= 100 (-> ((test-helpers/by-message-key :user-stats [user-2 source-3]) journal)
                         :value
                         :sum)))
          ;; gets one pushup entry with 3 for user 1
          (is (= 1 (-> ((test-helpers/by-message-key :user-stats [user-1 source-2]) journal)
                       :value
                       :count)))
          (is (= 3 (-> ((test-helpers/by-message-key :user-stats [user-1 source-2]) journal)
                       :value
                       :sum))))))))

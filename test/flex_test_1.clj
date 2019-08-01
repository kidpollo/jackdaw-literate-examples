(ns flex-test-1)
(ns flex-test-1
  (:require [clojure.test :refer :all]
            [jackdaw.streams.mock :as j.s.m]
            [jackdaw.test :as j.t]))

(defn mock-transport
  [builder topics]
  (let [mock-driver (-> (j.s.m/streams-builder)
                        (builder)
                        (j.s.m/streams-builder->test-driver))]
    (j.t/mock-transport {:driver mock-driver}
                        topics)))

(deftest topology-test
  (let [topology-topics {:events (flex/topic-config "events")
                         :user-sources (flex/topic-config "user-sources")
                         :events-by-source (flex/topic-config "events-by-source")
                         :events-by-user-and-source (flex/topic-config "events-by-user-and-source")
                         :user-stats (flex/topic-config "user-stats")}
        ;; Transport
        transport (mock-transport flex/build-topology topology-topics)

        user-1 (java.util.UUID/randomUUID)
        user-2 (java.util.UUID/randomUUID)
        source-1 (java.util.UUID/randomUUID)
        source-2 (java.util.UUID/randomUUID)
        source-3 (java.util.UUID/randomUUID)

        ;; User Sources
        user-1-step-counter {:id source-1
                             :name "step counter"
                             :user-id user-1}
        user-1-pushup-counter {:id source-2
                               :name "pushup counter"
                               :user-id user-1}
        user-2-step-counter {:id source-3
                             :name "step counter"
                             :user-id user-2}]

    (with-open [machine (j.t/test-machine transport)]
      (testing "user-1 takes 1 step"
        (let [commands [[:write! :user-sources user-1-step-counter]
                        [:write! :events {:id (java.util.UUID/randomUUID)
                                          :event-id (java.util.UUID/randomUUID)
                                          :source-id source-1
                                          :value 1
                                          :timestamp (System/currentTimeMillis)}]
                        [:watch (fn [journal]
                                  (->> (get-in journal [:topics :user-stats])
                                       (filter #(= [user-1 source-1] (:key %)))
                                       (count)
                                       (= 1)))]]
              {:keys [results journal]} (j.t/run-test machine commands)]

          ;; check status on every command
          (is (every? #(= :ok (:status %)) results))
          ;; gets one step entry with one step count
          (is (= 1 (-> journal
                       (get-in [:topics :user-stats])
                       (->>
                        (filter #(= [user-1 source-1] (:key %))))
                       last
                       :value
                       :count)))
          (is (= 1 (-> journal
                       (get-in [:topics :user-stats])
                       (->>
                        (filter #(= [user-1 source-1] (:key %))))
                       last
                       :value
                       :sum)))))

      (testing "user-2 steps gets 2 counts of 50 steps while user-1 does 3 pushups"
        (let [commands [[:write! :user-sources user-1-step-counter]
                        [:write! :user-sources user-1-pushup-counter]
                        [:write! :user-sources user-2-step-counter]
                        [:write! :events {:id (java.util.UUID/randomUUID)
                                          :event-id (java.util.UUID/randomUUID)
                                          :source-id source-3
                                          :value 50
                                          :timestamp (System/currentTimeMillis)}]
                        [:write! :events {:id (java.util.UUID/randomUUID)
                                          :event-id (java.util.UUID/randomUUID)
                                          :source-id source-2
                                          :value 3
                                          :timestamp (System/currentTimeMillis)}]
                        [:write! :events {:id (java.util.UUID/randomUUID)
                                          :event-id (java.util.UUID/randomUUID)
                                          :source-id source-3
                                          :value 50
                                          :timestamp (System/currentTimeMillis)}]
                        [:watch (fn [journal]
                                  (->> (get-in journal [:topics :user-stats])
                                       (filter #(= [user-2 source-3] (:key %)))
                                       (count)
                                       (= 2)))]]
              {:keys [results journal]} (j.t/run-test machine commands)]

          (is (every? #(= :ok (:status %)) results))
          ;; there where two step updates for user 3
          (is (= 2 (-> journal
                       (get-in [:topics :user-stats])
                       (->>
                        (filter #(= [user-2 source-3] (:key %))))
                       last
                       :value
                       :count)))
          ;; total steps for user 3 was 100
          (is (= 100 (-> journal
                         (get-in [:topics :user-stats])
                         (->>
                          (filter #(= [user-2 source-3] (:key %))))
                         last
                         :value
                         :sum)))
          ;; gets one pushup entry with 3 for user 1
          (is (= 1 (-> journal
                       (get-in [:topics :user-stats])
                       (->>
                        (filter #(= [user-1 source-2] (:key %))))
                       last
                       :value
                       :count)))
          (is (= 3 (-> journal
                       (get-in [:topics :user-stats])
                       (->>
                        (filter #(= [user-1 source-2] (:key %))))
                       last
                       :value
                       :sum))))))))

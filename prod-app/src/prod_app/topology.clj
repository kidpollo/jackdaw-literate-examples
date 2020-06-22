(ns prod-app.topology
  (:gen-class)
  (:require [clj-http.client :as http]
            [clj-uuid :as uuid]
            [clojure.data.json :as json]
            [clojure.spec.alpha :as s]
            [clojure.walk :as walk]
            [prod-app.log :as log]
            [prod-app.xform :as jxf]
            [prod-app.specs.reader-specs :as r-specs]
            [prod-app.specs.writer-specs :as w-specs]
            [integrant.core :as ig]
            [jackdaw.streams :as j]))

(defn loan-application
  "returns sba loan application from external data"
  [external-loan-application]
  (let [external-opportunity-id (:opportunity-id external-loan-application)]
    (assoc external-loan-application :loan-application-id
           (uuid/v5 uuid/+namespace-url+ external-opportunity-id))))

(defn update-loan-application
  [state & {:keys [swap-fn registry]}]
  (fn [rf]
    (fn
      ([] (rf))
      ([result] (rf result))
      ([result record]
       (let [[_ v] record
             id (uuid/v5 uuid/+namespace-url+ (:opportunity-id v))
             metadata {:id id
                       :published-timestamp (System/currentTimeMillis)
                       :published-by "sba-connector"}
             loan-app (loan-application v)
             opportunity-id (:opportunity-id loan-app)]
         (if (s/valid? ::w-specs/loan-application loan-app)
           (let [next (as-> loan-app %
                        (swap-fn state merge {opportunity-id %})
                        (get % opportunity-id)
                        (do
                          (log/logger
                           {:level :info
                            :event "loan-application-attribute-validation-success"
                            :metrics-registry registry
                            :message
                            "Loan application attributes satisfy writer spec"}
                           v %)
                          %)
                        (if (s/valid? ::r-specs/loan-application %)
                          (do
                            (log/logger
                             {:level :info
                              :event "loan-application-complete"
                              :metrics-registry registry
                              :message
                              "Loan application satisfies reader spec"}
                             v %)
                            (assoc %
                                   :loan-application-is-complete true
                                   :problems []))
                          (let [problems (:clojure.spec.alpha/problems
                                          (s/explain-data ::r-specs/loan-application %))]
                            (log/logger
                             {:level :info
                              :event "loan-application-incomplete"
                              :problems-count (count problems)
                              :metrics-registry registry
                              :message
                              "Loan application does not satisfy reader spec"}
                             v %)
                            (assoc %
                                   :loan-application-is-complete false
                                   :problems (map str problems))))
                        (merge % metadata)
                        (vector opportunity-id %)
                        (vector %))]
             (rf result next))
           (do
             (log/logger
              {:level :info
               :event "loan-application-attribute-validation-failure"
               :metrics-registry registry
               :message
               "Loan application attributes do not satisfy writer spec"}
              v)
             (rf result []))))))))

(defn parse-sba-http-response
  "Parse sba post request. Gracefully handles a non-json response."
  [response]
  (let [response-data (try (-> (:body response)
                               json/read-str)
                           (catch Exception e
                             {"loan-number" false}))
        loan-number (get response-data "loan-number")]
    {:status (if loan-number "success" "failure")
     :loan-number loan-number
     :result (json/write-str response-data)}))

(defn send-loan-application-to-sba
  [state & {:keys [deref-fn get-fn config registry]}]
  (fn [rf]
    (fn
      ([] (rf))
      ([result] (rf result))
      ([result record]
       (let [[_ v] record
             opportunity-id (:opportunity-id v)
             request-body (json/write-str {:dummy-request (str loan-application)})
             loan-application (get-fn (deref-fn state) opportunity-id)
             loan-application (into {} (remove (comp nil? val) loan-application))
             id (uuid/v5 uuid/+namespace-url+ (:trigger-id v))
             metadata {:id id
                       :published-timestamp (System/currentTimeMillis)
                       :published-by "sba-connector"}]

         (cond
           (nil? loan-application)
           (do
             (log/logger
              {:level :warn
               :event "unknown-loan-application"
               :message "Could not find matching loan application for trigger, ignoring"}
              v metadata {:opportunity-id opportunity-id})
             (rf result []))

           (s/valid? ::r-specs/loan-application loan-application)
           (let [url (get-in config [:sba-config :url])
                 body request-body
                 _ (log/logger
                    {:level :info
                     :event "sba-http-request"
                     :message "New HTTP request to SBA"
                     :metrics-registry registry
                     :body body
                     :url url}
                    v loan-application)
                 response (http/post url {:headers {"content-type" "application/json"}
                                          :body body})
                 next (as-> response %
                        (do (log/logger
                             {:level :debug
                              :event "unparsed-sba-response"
                              :body response
                              :metrics-registry registry
                              :message
                              "Unparsed SBA API post response"}
                             v loan-application metadata)
                            %)
                        (merge (parse-sba-http-response %)
                               loan-application
                               metadata)
                        (do (log/logger
                             {:level :info
                              :event "sba-response-result"
                              :metrics-registry registry
                              :message
                              "SBA response result"}
                             v loan-application metadata)
                            %)
                        (vector opportunity-id %)
                        (vector %))]
             (rf result next))

           :else
           (let [_ (as-> {} %
                     (merge  % {:status "cancelled"
                                :loan-number nil
                                :result (str "Could not send HTTP request. "
                                             "The loan application does not satisfy the reader spec.")}
                             loan-application
                             metadata)
                     (do
                       (log/logger
                        {:level :warn
                         :event "request-cancelled-loan-application-incomplete"
                         :metrics-registry registry
                         :message (:sba/result %)}
                        %)
                       %)
                     (vector opportunity-id %)
                     (vector %))]
             (rf result []))))))))

(defn topology-builder
  [{:keys [external-loan-application
           external-trigger
           sba-loan-application-updated
           sba-result-available]}
   xforms
   registry]
  (fn [builder]
    (jxf/add-state-store! builder)
    (-> (j/kstream builder external-loan-application)
        (j/peek (fn [[k v]]
                  (log/logger
                   {:level :info
                    :opportunity-id k
                    :event "new-external-loan-application"
                    :metrics-registry registry
                    :message
                    "New external loan application snapshot"}
                   v external-loan-application)))
        (jxf/transduce (::update-loan-application xforms))
        (j/peek (fn [[k v]]
                  (log/logger
                   {:level :info
                    :opportunity-id k
                    :event "sba-loan-application-updated-event"
                    :metrics-registry registry
                    :message
                    "SBA loan application updated "}
                   v sba-loan-application-updated)))
        (j/to sba-loan-application-updated))

    (-> (j/kstream builder external-trigger)
        (j/peek (fn [[k v]]
                  (log/logger
                   {:level :info
                    :opportunity-id k
                    :event "external-trigger-event"
                    :metrics-registry registry
                    :message
                    "New external trigger"}
                   v external-trigger)))
        (jxf/transduce (::send-loan-application-to-sba xforms))
        (j/peek (fn [[k v]]
                  (log/logger
                   {:level :info
                    :opportunity-id k
                    :event "sba-result-available-event"
                    :metrics-registry registry
                    :message "SBA result available"}
                   v sba-result-available)))
        (j/to sba-result-available))
    builder))

(defmethod ig/init-key ::app [_ {:keys [config topology] :as opts}]
  (let [streams-app (j/kafka-streams topology (:streams-config config))]
    (log/info "Started sba-connector streams app")
    (j/start streams-app)
    (assoc opts :streams-app streams-app)))

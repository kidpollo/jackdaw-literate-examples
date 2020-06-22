(ns prod-app.exception
  (:require [prod-app.log :as log]
            [honeybadger.core :as honeybadger]
            [integrant.core :as ig]))

(def magic-keys
  "The keys that honeybadger treats special in its metadata."
  [:tags :component :action :context :request])

(defn with-app-meta
  [app raw-metadata]
  (assoc-in raw-metadata [:context :app] app))

(defn groom-meta
  "Cleans up the metadata for honeybadger so we see the data
  we expect in the places we expect.
  Pulls out the magic keys, merges the rest under :context where anything
  goes."
  [raw-metadata]
  (let [;; all the special keys are in this map
        predefined (select-keys raw-metadata magic-keys)

        added-context (apply dissoc raw-metadata magic-keys)]
    (update predefined :context merge added-context)))

(defn hb-notify
  "Notifies Honeybadger of the error.
  `error` can be a string or exception object.
  `metadata` has a specific set of keys supported by honeybadger, others are ignored,
  see the select-keys call, and https://github.com/camdez/honeybadger#metadata"
  [config error raw-metadata]
  (let [metadata (->> raw-metadata
                      (groom-meta)
                      (with-app-meta (:app config)))]
    (log/error {:error error
                :metadata raw-metadata}
               "Notifying HoneyBadger")
    @(honeybadger/notify config error metadata)))

(defn terminate
  "Stop the JVM and exit with an error code."
  []
  (shutdown-agents) ; this may be a no-op
  (System/exit 1))

(defmethod ig/init-key ::honeybadger [_ {:keys [config]}]
  (let [hb-report (partial hb-notify (:honeybadger config))
        handler (reify Thread$UncaughtExceptionHandler
                  (uncaughtException [this thread error]
                    (try
                      (hb-report error {})
                      (catch Throwable t
                        (log/error {:uncaught-exception error
                                    :uncaught-exception-handler-error t}
                                   "UncaughtExceptionHandler fn threw Exception"))
                      (finally (terminate)))))]

    (Thread/setDefaultUncaughtExceptionHandler handler)


    {:report hb-report
     :handler handler}))

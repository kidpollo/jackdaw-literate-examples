(ns prod-app.config
  (:require [clojure.java.io :as io]
            [clojure.walk :as walk]
            [prod-app.exception :as exception]
            [prod-app.metrics :as metrics]
            [prod-app.topology :as sba-connector]
            [prod-app.streams :as streams]
            [prod-app.topic-metadata :as topic-metadata]
            [prod-app.topics :as topics]
            [prod-app.xform :as jxf]
            [integrant.core :as ig]
            [outpace.config :as outpace]
            [outpace.config.bootstrap :as config-bootstrap]))

(defmacro defconfig-warn
  "Ensures that any attempt to use outpace/defconfig explicitly errors, rather
  than just acting strangely.
  Outpace offers no method of reloading config without reloading source,
  as it encourages using defconfig to bind configs to the top level of namespaces.
  Our usage of outpace and reloadable config currently requires not using
  defconfig."
  [lookup]
  (throw (ex-info "invalid usage of config for this app, use outpace.config/lookup inside an integrant method instead"
                  {:config-name lookup})))

(alter-var-root #'outpace/defconfig (constantly @#'defconfig-warn))

(defn reload-config
  "Goes into outpace internals to get the config reload semantics we want."
  [source]
  (alter-var-root #'config-bootstrap/explicit-config-source
                  (constantly source))
  (alter-var-root #'outpace/config
                  (constantly (delay (outpace/load-config)))))

(defn interpolate
  "Given a config template fills in environment specific data."
  [template]
  (walk/postwalk (fn [x]
                   (if (symbol? x)
                     (outpace/lookup x)
                     x))
                 template))

(defn get-config-resource
  [resource-name]
  #(io/resource resource-name))

(def streams-config
  '{"application.id" topology/application-id
    "client.id" "prod-app"
    "processing.guarantee" "exactly_once"
    "acks" "all"
    "bootstrap.servers" kafka/bootstrap-servers
    "replication.factor" kafka/replication-factor
    "cache.max.bytes.buffering" "0"
    "num.stream.threads" "5"})

(def client-config
  '{"bootstrap.servers" kafka/bootstrap-servers})

(def honeybadger
  '{:api-key honeybadger/key
    :env honeybadger/env
    :app :sba-connector})

(def sba-config
  '{:url sba/url})

(def config-template
  "A template that is filled in via outpace (see common.config)
  Each submap should apply to a specific domain of interest and mix symbols,
  which will be looked up in the config map loaded by outpace, with configs
  that don't change on a per-environment basis."
  {:streams-config streams-config
   :client-config client-config
   :sba-config sba-config
   :schema-registry-url 'kafka/schema-registry-url
   :replication-factor 'kafka/replication-factor})

(def defaults
  "Default config loading data.
  This is pulled out of the init-key (and comes in via the core ns) in order to
  simplify using alternate configs without restarting the repl."
  {:get-source (get-config-resource "config.edn")
   :template config-template})

(defmethod ig/init-key ::config [_ {:keys [get-source template]}]
  (reload-config (get-source))
  (let [unprepared-config (interpolate template)]

    unprepared-config))

(def sba-connector-app-state
  "App state for sba connector streams app."
  {::config defaults
   ::topic-metadata/sba-connector {:config (ig/ref ::config)}
   ::topics/topics {:config (ig/ref ::config)
                    :topic-metadata (ig/ref ::topic-metadata/sba-connector)}

   ::exception/honeybadger {:config (ig/ref ::config)}

   ::metrics/registry {}

   ::metrics/prometheus-reporter {:registry (ig/ref ::metrics/registry)}

   ::streams/topology {:config (ig/ref ::config)
                       :topology-builder sba-connector/topology-builder
                       :topics (ig/ref ::topics/topics)
                       :xforms [#'sba-connector/update-loan-application
                                #'sba-connector/send-loan-application-to-sba]
                       :deref-fn identity
                       :get-fn jxf/kv-store-get-fn
                       :swap-fn jxf/kv-store-swap-fn
                       :registry (ig/ref ::metrics/registry)}

   ::sba-connector/app {:config (ig/ref ::config)
                        :topics (ig/ref ::topics/topics)
                        :topology (ig/ref ::streams/topology)}})

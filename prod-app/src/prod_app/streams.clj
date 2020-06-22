(ns prod-app.streams
  (:require [integrant.core :as ig]
            [jackdaw.streams :as j]))


(defn build-topology
  [config topology-builder topic-metadata xforms deref-fn get-fn swap-fn registry]
  (let [xform-map (into {}
                        (map (fn [f]
                               (let [k (keyword (str (:ns (meta f)))
                                                (str (:name (meta f))))
                                     v #(f %
                                           :config config
                                           :deref-fn deref-fn
                                           :get-fn get-fn
                                           :swap-fn swap-fn
                                           :registry registry)]
                                 [k v]))
                             xforms))]
    (topology-builder topic-metadata xform-map registry)))

(defmethod ig/init-key ::topology [_ {:keys [config
                                             topology-builder
                                             topics
                                             xforms
                                             deref-fn
                                             get-fn
                                             swap-fn
                                             registry]}]
  (let [build-fn (build-topology config
                                 topology-builder
                                 topics
                                 xforms
                                 deref-fn
                                 get-fn
                                 swap-fn
                                 registry)
        streams-builder (j/streams-builder)]
    (build-fn streams-builder)))

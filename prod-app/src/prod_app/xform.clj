(ns prod-app.xform
  "Helper functions for working with transducers."
  (:gen-class)
  (:refer-clojure :exclude [transduce])
  (:require [jackdaw.serdes :as js]
            [jackdaw.streams :as j])
  (:import org.apache.kafka.streams.kstream.Transformer
           [org.apache.kafka.streams.state KeyValueStore Stores]
           org.apache.kafka.streams.StreamsBuilder))


(defn fake-kv-store
  "Creates an instance of org.apache.kafka.streams.state.KeyValueStore
  with overrides for get and put."
  [init]
  (let [store (volatile! init)]
    (reify KeyValueStore
      (get [_ k]
        (clojure.core/get @store k))

      (put [_ k v]
        (vswap! store assoc k v)))))


(defn kv-store-get-fn
  "Takes an instance of KeyValueStore and a key k, and gets a value
  from the store in a manner similar to `clojure.core/get`."
  [^KeyValueStore store k]
  (.get store k))


(defn kv-store-swap-fn
  "Takes an instance of KeyValueStore, a function f, and map m, and
  updates the store in a manner similar to `clojure.core/swap!`."
  [^KeyValueStore store f m]
  (let [ks (keys (f {} m))
        prev (reduce (fn [p k]
                       (assoc p k (.get store k)))
                     {}
                     ks)
        next (f prev m)]
    (doall (map (fn [[k v]] (.put store k v)) next))
    next))


(defn add-state-store!
  "Takes a builder and adds a state store."
  [builder]
  (doto ^StreamsBuilder (j/streams-builder* builder)
    (.addStateStore (Stores/keyValueStoreBuilder
                     (Stores/persistentKeyValueStore "state")
                     (js/edn-serde)
                     (js/edn-serde))))
  builder)

(defn transformer
  "Takes a transducer and creates an instance of
  org.apache.kafka.streams.kstream.Transformer with overrides for
  init, transform, and close."
  [xf]
  (let [ctx (atom nil)]
    (reify
      Transformer
      (init [_ context]
        (reset! ctx context))
      (transform [_ k v]
        (let [^KeyValueStore store (.getStateStore @ctx "state")]
          (doseq [[result-k result-v] (first (sequence (xf store) [[k v]]))]
            (.forward @ctx result-k result-v))))
      (close [_]))))


(defn transduce
  "Applies the transducer xf to each element of the kstream."
  [kstream xf]
  (j/transform kstream (fn [] (transformer xf)) ["state"]))

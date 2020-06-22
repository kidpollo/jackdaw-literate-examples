(ns prod-app.integration.journal
  (:require
   [clojure.java.io :as io]
   [clojure.pprint :as pp])
  (:import
   java.io.File))

(defn raw-messages
  [journal topic-name]
  (sort-by :offset (get-in journal [:topics topic-name])))

(defn messages
  [journal topic-name]
  (->> (raw-messages journal topic-name)
       (map :value)))

(defn messages->table [k messages]
  (into {} (map (fn [[k v]]
                  [k (last v)])
                (group-by k messages))))

(defn latest-messages [k messages]
  (vals (messages->table k messages)))

(defn export-journal
  ([journal] (export-journal journal "./test-results"))
  ([journal directory]
   (let [rdir (File. directory)
         rfile (File. rdir (str "journal-" (System/currentTimeMillis)))]
     (when (or (.exists rdir) (.mkdir rdir))
       (println (str "writing results to '" rfile "'"))
       (with-open [f (io/writer rfile)]
         (.write f (str journal)))))))

(defn slurp-journal [file]
  (read-string (slurp file)))

(defn summarise [journal]
  (pp/print-table
   (sort-by :topic
            (map (fn [[k v]]
                   {:topic k :messages (count v)}) (:topics journal)))))

(defn summarise-and-export [journal]
  (summarise journal)
  (export-journal journal))

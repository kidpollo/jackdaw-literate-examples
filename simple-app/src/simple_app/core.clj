(ns simple-app.core)
(defn hello [subject]
  (prn (str "Hello " subject)))

(defn -main [target]
  (hello target))

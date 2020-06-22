(ns prod-app.core
  (:require [prod-app.config :as config]
            [integrant.core :as ig]))





(defonce app
  {})

(defn fresh-app
  [state]
  (def app (ig/init state)))


(defn -main
  "Reloads config.
  Starts and binds the running app."
  []
  (fresh-app config/sba-connector-app-state))

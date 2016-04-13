(ns broker-clj.core
  (:gen-class)
  (:use [org.httpkit.server :only [send! with-channel on-close on-receive run-server]]
        [clojure.tools.logging :only [info]]
        [broker-clj.session :only [query-channels]]
        [broker-clj.queue-handler :only [subscribe]]
        (compojure [core :only [defroutes GET POST]]
                   [route :only [files not-found]]
                   [handler :only [site]]
                   [route :only [not-found]]))
  (:require [broker-clj.common :as common]
            [broker-clj.websocket :as websocket]
            [schema.core :as schema]))

(defn ws-handler
  [request]
  (with-channel request channel
    (on-close channel #(websocket/close-channel channel %))
    (on-receive channel #(websocket/handle-message channel %))))

(defn- wrap-request-logging [handler]
  (fn [{:keys [request-method uri] :as req}]
    (let [resp (handler req)]
      (info (name request-method) (:status resp)
            (if-let [qs (:query-string req)]
              (str uri "?" qs) uri))
      resp)))

(defroutes all-routes
  (GET "/ws" []  ws-handler)
  (not-found "{error: 'not-found'}"))


(defn- taskulu-validate
  [msg-hash]
  (let [validations {:api schema/Int,
                     :skip schema/Bool,
                     :time schema/Num,
                     :random schema/Num,
                     :data (type {}),
                     :users [schema/Str],
                     :session schema/Str,
                     :channel_id schema/Str,
                     :channel_type schema/Str}]

    schema/validate validations msg-hash))

(defn save-for-later
  [{data     :data
    topic    :topic,
    topic-id :topic-id,
    users    :users}]
  )

(defn- send-to-sessions
  [{ids :users
    data-value :data
    topic-id :channel_id
    sender-session :session
    topic :type
    version :api
    skip-session :skip}]
  (let [query {:topic-id      topic-id
               :topic         topic
               :version       version
               :skip-sessions []
               :users []}
        query (if skip-session
                (assoc query :skip-sessions [sender-session])
                query)

        query (assoc query :user ids)

        {missed-ids :missed
         channels   :channels} (query-channels query)]
    (future
      (do
        (as-> data-value $
          (common/hash-to-str $)
          (doall $)
          (pmap #(send! % $) channels))
        (save-for-later {:data     data-value
                         :topic    topic
                         :topic-id topic-id
                         :users    missed-ids})))))

(defn handle-data
  [string-data]
  (-> string-data
      common/json-data
      taskulu-validate
      send-to-sessions))

(defn -main
  [& args]
  (do
    (subscribe "update-events" handle-data)

    (run-server (-> #'all-routes) {:port 9090})
    (info "server started. http://127.0.0.1:9090")))

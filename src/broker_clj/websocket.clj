(ns broker-clj.websocket
  (:use broker-clj.session
        debugger.core
        [clojure.tools.logging :only [info]]
        [org.httpkit.server :only [send! close]])
  (:require [clojure.walk      :as walk]
            [clojure.data.json :as json]
            [broker-clj.common :as common]
            [schema.core :as schema]
            [clojure.core.async
             :as a
             :refer [>! <! >!! <!! go chan buffer close! thread go-loop]]))

(def error-port   (chan 1e6))
(def close-port   (chan 1e6))
(def topic-port   (chan 1e6))
(def auth-port    (chan 1e6))

(defn- reply
  [channel reply-msg]
  (thread
    (->> reply-msg
         common/hash-to-str
         (send! channel))))

(defmulti act-by-request
  (fn [_ data-hash]
    (:command data-hash)))

(defn handle-message
  [channel data]
  (thread
    (some->>
     data
     (#(if (< (count %) 200)
         data))

     common/json-data    ;; might return nil!

     (#(try
         (act-by-request channel %)
         (catch clojure.lang.ExceptionInfo e
           (do
             (>!! error-port e)
             (>!! close-port [channel :exception])
             nil))))
     )))


(defn close-channel
  [channel status]
  (go
    (>! close-port [channel :closed status])))


(defmethod act-by-request "authenticate"
  [channel {:keys [username password] :as request}]
  (let [validations {:command  schema/Any
                     :username schema/Str
                     :password schema/Str}]
    (schema/validate validations request)
    (if (user-by-channel channel)
      (>!! close-port [channel :auth])
      (>!! auth-port  [channel username password]))))

(defn- handle-topic-commands
  [channel {:keys [id topic version command] :as request}]
  (let [validations {:command (schema/enum "listen" "shutup")
                     :id schema/Str
                     :version schema/Str
                     :topic schema/Str}
        action (case command
                 "listen" :add
                 "shutup" :remove)
        user (user-by-channel channel)]

    (schema/validate validations request)
    (if user
      (>!! topic-port [channel [id topic version] action])
      (>!! close-port [channel :topic]))))


(defmethod act-by-request "listen"
  [& args]
  (apply handle-topic-commands args))

(defmethod act-by-request "shutup"
  [& args]
  (apply handle-topic-commands args))


(defmethod act-by-request "hey"
  [channel msg]
  (let [validation {:command schema/Str}
        reply-msg  {:reaction :hi}]
    (schema/validate validation msg)
    (if (user-by-channel channel)
      (reply channel reply-msg)
      (>!! close-port [channel :hey]))))

(defmethod act-by-request :default
  [channel request]
  (do
    (>!! close-port [channel :unknown-request])))

;; topic-port
(go-loop []
  (let [[channel [id topic version] action] (<! topic-port)]
    (case action
      :add    (add-listener!    channel id version topic)
      :remove (remove-listener! channel id version topic))
    (recur)))


;; close-port
(go-loop []
  (let [[channel from & [status]] (<! close-port)]
    (remove-by-channel! channel)
    (if (not= status :going-away)
      (close channel))
    (recur)))

;; auth-port
(go-loop []
  (let [[channel username password] (<! auth-port)
        reply-msg {:reaction :authenticated
                   :session (new-session! channel username)}]
    (reply channel reply-msg)
    (recur)))

;; error-port
(go-loop []
  (let [exception (<! error-port)]
    (info "error" exception)
    (recur)))



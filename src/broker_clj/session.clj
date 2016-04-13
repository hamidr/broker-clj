;(set! *warn-on-reflection* true)

(ns broker-clj.session
  (:use [clojure.set :only [union]]
        [clojure.core.match :only [match]])
  (:require [broker-clj.common :as common]))

(defmacro update-state!
  [state st_name & exprs]
  `(swap! ~state (fn [state#]
                   (as-> state# ~st_name
                     ~@exprs))))

(def state
  "sessions channels topics"
  (atom {:sessions {}
         :channels {}
         :topics   {}}))

(defn get-sessions
  "Keeping the state of all Logged In users; Every logged in user can have multiple sessions all connected(identifiable) via separate webchannels.
  Each session keeps a set of tuples of (channel, id, version).
  sessions = {
                user_id {
                          sessionX channel1
                          sessionY channel2
                        },
                  ...
             }
  "
  []
  (:sessions @state))

(defn get-channels
  "Keeping channels(web channels) is necessary for finding the link between sessions and their channels.
  We pair each channel with their tuple of (session, user_id);
  channels = {
               channel1 [session uid #{topic0, topic1}],
               channel1 [session uid #{[id topic version], [id topic version]}],
             }"
  []
  (:channels @state))

(defn get-topics
  "What people are listening!
  topics = {
     { topic_id1 { topic  #{[uid channel session version]} } }
     { topic_id2 { topic  #{[uid channel session version]} } }
  }
  "
  []
  (:topics @state))

(defn- uuid-string
  []
  (-> (java.util.UUID/randomUUID)
      (.toString)
      (.replace "-" "")))

(defn new-session!
  [channel user_id]
  (let [session (uuid-string)]
    (update-state! state $
                   (update-in $ [:sessions user_id] assoc session channel)
                   (update-in $ [:channels] assoc channel [session user_id #{}]))
    session))

(defn- remove-session
  [col [uid session]]
  (let [sessions (get col uid)
        new-sessions (dissoc sessions session)]
    (if (empty? new-sessions)
      (dissoc col uid)
      (assoc col uid new-sessions))))

(defn- remove-topic
  [col [uid channel session] [[topic_id topic version] & topics]]
  (let [new-col (update-in col [topic_id topic] disj [uid channel session version])]
    (if (empty? topics)
      new-col
      (recur new-col [uid channel session] topics))))


(defn remove-by-channel!
  [channel]
  (match (get (get-channels) channel)
         [session uid ltopics]
         (do (update-state! state $
                            (update-in $ [:channels] dissoc channel)
                            (update-in $ [:sessions] remove-session [uid session])
                            (update-in $ [:topics] remove-topic [uid channel session] (seq ltopics)))
             true)
         :else false))

(defn add-listener!
  [channel id version topic]
  (match (get (get-channels) channel)
         [session uid ctopics]
         (do
           (update-state! state $
                          (update-in $ [:channels channel 2] conj [id topic version])
                          (update-in $ [:topics id topic] (comp set conj) [uid channel session version]))
           true)
         :else false))

(defn remove-listener!
  [channel id version topic]
  (match (get (get-channels) channel)
         [session uid ctopics]
         (do
           (update-state! state $
                          (update-in $ [:channels channel 2] disj [id topic version])
                          (update-in $ [:topics id topic] disj [uid channel session version]))
           true)
         :else false))

(defn split-by-ids
  [users ltopics]
  (let [lids (mapv first ltopics)
        [on-ids off-ids] (split-with (partial contains? lids) users)
        on-topics (filter #(contains? (set on-ids) (first %)) ltopics)
        channels (map second on-topics)]
    [off-ids channels]))

(defn query-channels
  [{skip-sessions :skip-sessions
    version       :version
    topic-id      :topic-id
    topic         :topic
    users         :users}]
  (let [[off-ids channels] (match (some->> [topic-id topic]
                                           (get-in (get-topics) )
                                           (remove (fn [[_ _ session _]] (contains? (set skip-sessions) session)))
                                           (split-by-ids users))
                                  nil [(seq users) ()]
                                  result result)]
    {:missed  off-ids
     :channels channels}))

(defn users-by-topics
  ([id] (get-in (get-topics) [id]))
  ([id topic] (get-in (get-topics) [id topic]))
  ([id topic version]
   (some->> (users-by-topics id topic)
            (filter #(= (nth % 3) version)))))

(defn user-by-channel
  [channel]
  (some->> channel
           (get (get-channels))
           second))

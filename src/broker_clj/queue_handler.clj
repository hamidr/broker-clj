(ns broker-clj.queue-handler
  (:use
   [clojure.tools.logging :only [info]])
  (:require [langohr.core      :as rmq]
            [langohr.channel   :as lch]
            [langohr.queue     :as lq]
            [langohr.consumers :as lc]
            [langohr.basic     :as lb]))

(defn- message-handler
  [handler]
  (fn [ch {:keys [content-type delivery-tag type] :as meta} ^bytes payload]
    (handler (String. payload "UTF-8"))))


(defn subscribe
  [qname handler]
  (let [conn  (rmq/connect)
        ch    (lch/open conn)]

    (info
     (format "[main] Connected. Channel id: %d" (.getChannelNumber ch)))

    (lq/declare ch qname {:exclusive false :auto-delete false :durable true})
    (lc/subscribe ch qname (message-handler handler) {:auto-ack true})

    (.addShutdownHook (Runtime/getRuntime)
                      (Thread. #(do (rmq/close ch) (rmq/close conn))))))

(ns broker-clj.common
  (:require [clojure.walk      :as walk]
            [clojure.data.json :as json]))

(defn json-data
  [data]
  (walk/keywordize-keys (json/read-str data)))

(defn hash-to-str
  [hash]
  (json/write-str hash))

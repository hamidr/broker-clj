(defproject broker-clj "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "https://github.com/hamidr/broker-clj.git"
  :license {:name "The GNU General Public License v3.0"
            :url "www.gnu.org/licenses/gpl-3.0.en.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/tools.logging "0.2.3"]
                 [prismatic/schema "1.1.0"]
                 [com.novemberain/langohr "3.5.0"]
                 [org.clojure/core.match "0.3.0-alpha4"]
                 [org.clojure/core.async "0.2.374"]
                 [defun "0.3.0-alapha"]
                 [http-kit "2.1.18"]
                 [debugger "0.2.0"]
                 [compojure "1.0.2"]]
  :main ^:skip-aot broker-clj.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})

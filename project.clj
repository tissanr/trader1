(defproject trader1 "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [clj-http "3.9.1"]
                 [cheshire "5.8.0"];;JSON
                 [clj-time "0.15.0"]
                 [digest "1.4.8"]
                 [org.clojure/data.codec "0.1.1"]]
  :main ^:skip-aot trader1.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})

(defproject trader1 "0.1.0-SNAPSHOT"
  :description "Interactive Brokers portfolio dashboard with real-time WebSocket updates"
  :url "https://github.com/reiterstephan-tech/trader1"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.11.4"]
                 [org.clojure/core.async "1.8.741"]
                 [clj-http "3.12.3"]
                 [cheshire "5.13.0"]
                 [clj-time "0.15.2"]
                 [digest "1.4.10"]
                 [org.clojure/data.codec "0.1.0"]
                 [http-kit "2.8.0"]
                 [compojure "1.7.1"]
                 [hiccup "1.0.5"]
                 [ring/ring-defaults "0.5.0"]
                 ;; Pin to Java 8 compatible versions (ring-core 1.12+ requires Java 11)
                 [ring/ring-core "1.11.0"]
                 [commons-io/commons-io "2.13.0"]
                 [buddy/buddy-hashers "2.0.167"]
                 [com.google.protobuf/protobuf-java "4.29.3"]]
  :source-paths ["src" "vendor/ib-cl-wrap/src"]
  :resource-paths ["resources" "lib/TwsApi.jar"]
  :main ^:skip-aot trader1.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})

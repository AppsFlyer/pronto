(defproject pronto "0.1.0-SNAPSHOT"
  :description "clojure support for protocol buffers"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :source-paths ["src/clj"]
  :java-source-paths ["src/java"]
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.10.1"]
                                  [com.google.protobuf/protobuf-java "3.11.3"]
                                  [criterium "0.4.5"]]}}
  :repl-options {:init-ns pronto.core})

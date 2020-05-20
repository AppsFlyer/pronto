(defproject pronto "0.1.0"
  :description "clojure support for protocol buffers"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :source-paths ["src/clj"]
  :java-source-paths ["src/java" "test/java"]
  :deploy-repositories [["releases"  {:url           "***REMOVED***/" :username :***REMOVED***
                                      :password      :***REMOVED***
                                      :sign-releases false}]
                        ["snapshots"  {:url           "***REMOVED***" :username :***REMOVED***
                                       :password      :***REMOVED***
                                       :sign-releases false}]]
  :dependencies [[org.clojure/clojure "1.9.0"]]
  :profiles {:dev {:dependencies      [[org.clojure/clojure "1.10.1"]
                                       [com.google.protobuf/protobuf-java "3.11.3"]
                                       [criterium "0.4.5"]]
                   :java-source-paths ["src/java" "test/java"]}}
  :repl-options {:init-ns pronto.core})

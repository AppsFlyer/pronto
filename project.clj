(defproject pronto "0.1.4"
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
  :profiles {:provided {:dependencies [[com.google.protobuf/protobuf-java "3.10.0"]
                                       [com.google.protobuf/protobuf-java-util "3.10.0"]]}
             :dev      {:dependencies      [[org.clojure/clojure "1.10.1"]
                                            [criterium "0.4.5"]
                                            [clj-kondo "RELEASE"]]
                        :aliases           {"clj-kondo" ["run" "-m" "clj-kondo.main"]}
                        :eftest            {:multithread?   false
                                            :report         eftest.report.junit/report
                                            :report-to-file "target/junit.xml"}
                        :java-source-paths ["src/java" "test/java"]
                        :plugins           [[lein-eftest "0.5.9"]]}}
  :repl-options {:init-ns pronto.core}
  :global-vars {*warn-on-reflection* true})

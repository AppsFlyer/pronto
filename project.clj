(defproject pronto "1.0.7-SNAPSHOT"
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
  :profiles {:provided {:dependencies [[com.google.protobuf/protobuf-java "3.10.0"]]}
             :dev      {:dependencies      [[org.clojure/clojure "1.10.1"]
                                            [clj-kondo "RELEASE"]
                                            [jmh-clojure "0.4.0"]
                                            [com.clojure-goes-fast/clj-java-decompiler "0.3.0"]]
                        :aliases           {"clj-kondo" ["run" "-m" "clj-kondo.main"]}
                        :eftest            {:multithread?   false
                                            :report         eftest.report.junit/report
                                            :report-to-file "target/junit.xml"}
                        :java-source-paths ["src/java" "test/java"]
                        :plugins           [[lein-jmh "0.3.0"]
                                            [lein-eftest "0.5.9"]]}
             :jmh      {:source-paths      ["benchmarks/src/clj"]
                        :java-source-paths ["benchmarks/src/java"]
                        :resource-paths    ["benchmarks/resources"]
                        ;; Removing jvm-opts so it won't accidentally disable jvm optimizations
                        :jvm-opts          []}}

  :repl-options {:init-ns pronto.core}
  :global-vars {*warn-on-reflection* true})

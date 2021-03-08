(def protobuf-version "3.9.0")


(defproject pronto "2.0.1-SNAPSHOT"
  :description "clojure support for protocol buffers"
  :url "https://***REMOVED***/clojure/pronto"
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
  :repositories [["releases" {:url "***REMOVED***/"}]]

  :plugins [[lein-protodeps "0.1.21"]
            [lein-codox "0.10.7"]]

  :dependencies [[org.clojure/clojure "1.10.1"]
                 [potemkin "0.4.5"]]
  :profiles {:provided {:dependencies [[com.google.protobuf/protobuf-java ~protobuf-version]]}
             :dev      {:dependencies      [[clj-kondo "RELEASE"] ;; TODO: pin this, or use it as a binary
                                            [jmh-clojure "0.4.0"]
                                            [com.clojure-goes-fast/clj-java-decompiler "0.3.0"]
                                            [org.openjdk.jol/jol-core "0.13"]
                                            [criterium "0.4.6"]
                                            [com.clojure-goes-fast/clj-memory-meter "0.1.3"]]
                        :jvm-opts          ["-Djdk.attach.allowAttachSelf"]
                        :aliases           {"clj-kondo" ["run" "-m" "clj-kondo.main"]}
                        :eftest            {:multithread?   false
                                            :report         eftest.report.junit/report
                                            :report-to-file "target/junit.xml"}
                        :java-source-paths ["src/java" "test/java"]
                        :plugins           [[lein-jmh "0.3.0"]
                                            [lein-eftest "0.5.9"]
                                            [lein-cloverage "1.2.2"]]
                        :cloverage         {:ns-exclude-regex [#"pronto.potemkin-types"]}}
             :jmh      {:source-paths      ["benchmarks/src/clj"]
                        :java-source-paths ["benchmarks/src/java"]
                        :resource-paths    ["benchmarks/resources"]
                        ;; Removing jvm-opts so it won't accidentally disable jvm optimizations
                        :jvm-opts          []}}

  :repl-options {:init-ns pronto.core}
  :global-vars {*warn-on-reflection* true}

  :codox {:namespaces [pronto.core]}

  :lein-protodeps {:output-path   "test/java/"
                   :proto-version ~protobuf-version
                   :repos         {:examples {:repo-type    :filesystem
                                              :config       {:path "."}
                                              :proto-paths  ["resources"]
                                              :dependencies [[resources/proto]]}}})

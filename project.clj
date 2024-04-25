(def protobuf-version "3.15.0")


(defproject com.appsflyer/pronto "3.0.0"
  :description "clojure support for protocol buffers"
  :url "https://github.com/AppsFlyer/pronto"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :source-paths ["src/clj"]
  :java-source-paths ["src/java" "test/java"]
  :deploy-repositories [["releases" {:url "https://repo.clojars.org"
                                     :sign-releases false
                                     :username :env/clojars_username
                                     :password :env/clojars_password}]
                        ["snapshots" {:url "https://repo.clojars.org"
                                      :username :env/clojars_username
                                      :password :env/clojars_password}]]

  :plugins [[com.appsflyer/lein-protodeps "1.0.5"]
            [lein-codox "0.10.7"]]

  :dependencies [[org.clojure/clojure "1.10.1"]
                 [potemkin "0.4.5"]]
  :profiles {:provided {:dependencies [[com.google.protobuf/protobuf-java ~protobuf-version]]}
             :dev      {:dependencies      [[clj-kondo "RELEASE"] ;; TODO: pin this, or use it as a binary
                                            [jmh-clojure "0.4.0"]
                                            [com.clojure-goes-fast/clj-java-decompiler "0.3.0" :exclusions [org.bitbucket.mstrobel/procyon-compilertools]]
                                            [org.bitbucket.mstrobel/procyon-compilertools "0.5.36"]
                                            [org.openjdk.jol/jol-core "0.13"]
                                            [criterium "0.4.6"]
                                            [com.clojure-goes-fast/clj-memory-meter "0.1.3"]
                                            [org.clojure/tools.analyzer.jvm "1.2.2"]]
                        :jvm-opts          ["-XX:-OmitStackTraceInFastThrow" "-Djdk.attach.allowAttachSelf"]
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

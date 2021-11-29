(def protobuf-version "3.19.1")

(defproject examples "0.1.0-SNAPSHOT"
  :plugins [[com.appsflyer/lein-protodeps "1.0.2"]]
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [com.appsflyer/pronto "2.0.10"]
                 [com.google.protobuf/protobuf-java ~protobuf-version]]
  :repl-options {:init-ns examples.core}
  :java-source-paths ["src/java"]

  :lein-protodeps {:output-path "src/java"
                   :proto-version ~protobuf-version
                   :repos {:examples {:repo-type :filesystem
                                      :config {:path "."}
                                      :proto-paths ["resources"]
                                      :dependencies [resources/schemas]}}})

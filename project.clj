(defproject pronto "0.1.0-SNAPSHOT"
  :description "clojure support for protocol buffers"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :source-paths ["src/main/clojure"]
  :java-source-paths ["src/main/java"]
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [com.google.protobuf/protobuf-java "3.11.3"]
                 [org.ow2.asm/asm "7.3.1"]
                 [org.ow2.asm/asm-util "7.3.1"]]
  :repl-options {:init-ns pronto.core}
  ;:jvm-opts ["-Dclojure.compiler.direct-linking=true"]
  :aot [pronto.core])

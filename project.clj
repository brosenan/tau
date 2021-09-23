(defproject tau "0.1.0-SNAPSHOT"
  :description "Everything's a type"
  :url "https://github.com/brosenan/tau"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1"]]
  :repl-options {:init-ns tau.core}
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}
             :dev {:dependencies [[midje "1.10.3"]]
                   :plugins [[lein-midje "3.2.1"]]}})

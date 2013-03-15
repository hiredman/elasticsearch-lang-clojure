(defproject com.thelastcitadel/elasticsearch-lang-clojure "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.elasticsearch/elasticsearch "0.90.0.Beta1"]
                 [org.clojure/tools.logging "0.2.6"]]
  :profiles {:dev {:dependencies [[com.spatial4j/spatial4j "0.3"]
                                  [log4j/log4j "1.2.16"]]}}
  :aot #{com.thelastcitadel.es.engine
         com.thelastcitadel.es.plugin})

(require 'leiningen.compile
         'leiningen.uberjar
         'leiningen.clean
         'leiningen.core.utils
         'leiningen.core.classpath)

(def ^:dynamic x false)

(defn package
  ""
  [project]
  (leiningen.clean/clean project)
  (leiningen.compile/compile project)
  (binding [x true]
    (leiningen.uberjar/uberjar
     (assoc project
       :uberjar-name (str (:name project) "-" (:version project) "-plugin" ".jar"))
     nil)))

(alter-var-root #'leiningen.core.utils/require-resolve
                (fn [f]
                  (fn [& args]
                    (if (= '[leiningen.package/package] args)
                      #'package
                      (apply f args)))))

(alter-var-root #'leiningen.core.classpath/resolve-dependencies
                (fn [f]
                  (fn [k v & r]
                    (if x
                      (apply f k
                             (update-in v [:dependencies]
                                        (partial remove
                                                 (comp (partial = 'org.elasticsearch/elasticsearch) first)))
                             r)
                      (apply f k v r)))))

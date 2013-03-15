(ns com.thelastcitadel.es.plugin
  (:gen-class
   :extends org.elasticsearch.plugins.AbstractPlugin
   :methods [[onModule [org.elasticsearch.script.ScriptModule]
              void]]))

(defn -name [_]
  "lang-clojure")

(defn -description [_]
  "Adds Clojure scripting to ElasticSearch")

(defn -onModule [_ module]
  (.addScriptEngine module (Class/forName "com.thelastcitadel.es.engine")))

(ns com.thelastcitadel.es.engine
  (:gen-class
   :extends org.elasticsearch.common.component.AbstractComponent
   :implements [org.elasticsearch.script.ScriptEngineService]
   :constructors {^{org.elasticsearch.common.inject.Inject true} [org.elasticsearch.common.settings.Settings]
                  [org.elasticsearch.common.settings.Settings]})
  (require [clojure.tools.logging :as log]
           [com.thelastcitadel.es.core]))

(defn -init [s]
  [[] nil])

(declare executable-script
         search-script)

(def types (into-array String ["clojure" "clj"]))

(def exts (into-array String ["clj"]))

(defn -types [_]
  types)

(defn -extensions [_]
  exts)

(defn -compile [_ script]
  (let [out (java.io.ByteArrayOutputStream.)
        fun (with-open [o (java.io.PrintWriter.
                           (java.io.OutputStreamWriter. out))
                        e (java.io.PrintWriter.
                           (java.io.OutputStreamWriter. out))]
              (binding [*warn-on-reflection* true
                        *out* o
                        *err* e
                        *ns* (find-ns 'com.thelastcitadel.es.core)]
                (eval (read-string (str "(do " script " )")))))
        out (String. (.toByteArray out))]
    (when-not (empty? out)
      (log/info out))
    (fn [env]
      (try
        (fun env)
        (catch Exception e
          (log/info e)
          (throw e))))))

(defn -executable [_ compiled-script env]
  (executable-script compiled-script (into {} env)))

(defn -search [_ compiled-script lookup env]
  (search-script compiled-script  (into {} env) lookup))

(defn -execute [_ compiled-script env]
  (compiled-script env))

(defn -unWrap [_ x]
  x)

(defn -close [_])

(defn executable-script [compiled-script env]
  (let [env (atom env)]
    (reify
      org.elasticsearch.script.ExecutableScript
      (setNextVar [_ name value]
        (swap! env assoc name value))
      (run [_]
        (-execute nil compiled-script @env))
      (unwrap [_ x]
        x))))

(defn search-script [compiled-script env ^org.elasticsearch.search.lookup.SearchLookup lookup]
  (let [env (atom (merge (into {} (.asMap lookup)) env))]
    (reify
      org.elasticsearch.script.SearchScript
      (setNextVar [_ name value]
        (swap! env assoc name value))
      (run [_]
        (compiled-script @env))
      (unwrap [_ x]
        x)
      (setScorer [_ scorer]
        (.setScorer lookup scorer))
      (setNextReader [_ context]
        (.setNextReader lookup context))
      (setNextDocId [_ id]
        (.setNextDocId lookup (int id)))
      (^void setNextSource [_ ^java.util.Map source]
        (-> lookup (.source) (.setNextSource source)))
      (setNextScore [this score]
        (.setNextVar this "_score" score))
      (runAsFloat [this]
        (float (.run this)))
      (runAsLong [this]
        (long (.run this)))
      (runAsDouble [this]
        (double (.run this))))))

(ns com.thelastcitadel.es.test.engine
  (:require [clojure.test :refer :all])
  (:import (org.elasticsearch.node NodeBuilder)
           (org.elasticsearch.common.settings ImmutableSettings)
           (org.elasticsearch.common.network NetworkUtils)
           (org.elasticsearch.index.query QueryBuilders
                                          FilterBuilders)
           (org.elasticsearch.client Requests)
           (org.elasticsearch.common.xcontent XContentFactory)
           (org.elasticsearch.search.sort SortOrder)))

;; https://github.com/elasticsearch/elasticsearch-lang-python/blob/master/src/test/java/org/elasticsearch/script/python/PythonScriptSearchTests.java

(declare ^:dynamic *client*)

(use-fixtures :each
              (fn [f]
                (let [node (-> (NodeBuilder/nodeBuilder)
                               (.settings (-> (ImmutableSettings/settingsBuilder)
                                              (.put "path.data" "target/data")
                                              (.put "cluster.name" (str "test-cluster-" (NetworkUtils/getLocalAddress)))
                                              (.put "gateway.type" "none")
                                              (.put "number_of_shards" 1)))
                               (.node))]
                  (binding [*client* (.client node)]
                    (try
                      (f)
                      (finally
                        (.close *client*)
                        (.close node)))))))

(defn json
  ([m]
     (json (-> (XContentFactory/jsonBuilder)
               (.startObject))
           m))
  ([builder m]
     (.endObject
      (reduce
       (fn [obj [k v]]
         (.field obj (name k) v))
       builder
       m))))

(defn exec [o]
  (.actionGet (.execute o)))

(deftest t-filter
  (doto *client*
    (-> .admin .indices (.prepareCreate "test") exec)
    (-> (.prepareIndex "test" "type1" "1") (.setSource (json {:test "value beck" :num1 1.0})) exec)
    (-> (.prepareIndex "test" "type1" "2") (.setSource (json {:test "value beck" :num1 2.0})) exec)
    (-> (.prepareIndex "test" "type1" "3") (.setSource (json {:test "value beck" :num1 3.0})) exec)
    (-> .admin .indices (.refresh (Requests/refreshRequest (make-array String 0))) .actionGet)
    ;;
    (-> (.prepareSearch (make-array String 0))
        (.setQuery (QueryBuilders/filteredQuery
                    (QueryBuilders/matchAllQuery)
                    (-> (FilterBuilders/scriptFilter
                         (pr-str
                          '(fn [env]
                             (clojure.tools.logging/info "Hello World")
                             (> (value (get (get env "doc") "num1")) 1.0))))
                        (.lang "clojure"))))
        (.addSort "num1" SortOrder/ASC)
        (.addScriptField "sNum1" "clojure"
                         (pr-str '(fn [env] (value (get (get env "doc") "num1")))) nil)
        exec
        (doto (-> .getHits .totalHits (= 2) is))
        (doto (-> .getHits (.getAt 0) .id (= "2") is))
        (doto (-> .getHits (.getAt 0) .fields (.get "sNum1") .values (.get 0) (= 2.0) is))
        (doto (-> .getHits (.getAt 1) .id (= "3") is))
        (doto (-> .getHits (.getAt 1) .fields (.get "sNum1") .values (.get 0) (= 3.0) is)))
    ;;
    (-> (.prepareSearch (make-array String 0))
        (.setQuery (QueryBuilders/filteredQuery
                    (QueryBuilders/matchAllQuery)
                    (-> (FilterBuilders/scriptFilter
                         (pr-str
                          '(fn [env]
                             (> (value (get (get env "doc") "num1"))
                                (get env "param1")))))
                        (.lang "clojure")
                        (.addParam "param1" 2))))
        (.addSort "num1" SortOrder/ASC)
        (.addScriptField "sNum1" "clojure"
                         (pr-str '(fn [env] (value (get (get env "doc") "num1")))) nil)
        exec
        (doto (-> .getHits .totalHits (= 1) is))
        (doto (-> .getHits (.getAt 0) .id (= "3") is))
        (doto (-> .getHits (.getAt 0) .fields (.get "sNum1") .values (.get 0) (= 3.0) is)))
    ;;
    (-> (.prepareSearch (make-array String 0))
        (.setQuery (QueryBuilders/filteredQuery
                    (QueryBuilders/matchAllQuery)
                    (-> (FilterBuilders/scriptFilter
                         (pr-str
                          '(fn [env]
                             (> (value (get (get env "doc") "num1"))
                                (get env "param1")))))
                        (.lang "clojure")
                        (.addParam "param1" -1))))
        (.addSort "num1" SortOrder/ASC)
        (.addScriptField "sNum1" "clojure"
                         (pr-str '(fn [env] (value (get (get env "doc") "num1")))) nil)
        exec
        (doto (-> .getHits .totalHits (= 3) is))
        (doto (-> .getHits (.getAt 0) .id (= "1") is))
        (doto (-> .getHits (.getAt 0) .fields (get "sNum1") .values (.get 0) (= 1.0) is))
        (doto (-> .getHits (.getAt 1) .id (= "2") is))
        (doto (-> .getHits (.getAt 1) .fields (get "sNum1") .values (.get 0) (= 2.0) is))
        (doto (-> .getHits (.getAt 2) .id (= "3") is))
        (doto (-> .getHits (.getAt 2) .fields (get "sNum1") .values (.get 0) (= 3.0) is)))))

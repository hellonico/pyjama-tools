(ns basic-tools.core
  "External tools for web, wiki, and movie operations"
  (:require [basic-tools.web :as web]
            [basic-tools.wiki :as wiki]
            [basic-tools.movie :as movie]
            [clojure.edn :as edn])
  (:gen-class))

(defn -main
  "Entry point for external tool execution.
   Reads EDN from stdin: {:function \"function-name\" :params {...}}
   Writes EDN to stdout: {:status :ok :result ...}"
  [& _args]
  (try
    (let [input (edn/read *in*)
          function-name (:function input)
          params (:params input)

          result (case function-name
                   "web-search" (web/web-search params)
                   "wiki-search" (wiki/wiki-search params)
                   "wiki-movie" (movie/wiki-movie params)
                   "create-movie" (movie/create-movie params)

                   {:status :error
                    :message (str "Unknown function: " function-name)})]

      (println (pr-str result)))

    (catch Exception e
      (println (pr-str {:status :error
                        :message (.getMessage e)
                        :stacktrace (mapv str (.getStackTrace e))})))))

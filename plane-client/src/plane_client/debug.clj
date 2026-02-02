(ns plane-client.debug
  "Quick debug script to inspect API responses"
  (:require [plane-client.core :as core]
            [clojure.pprint :as pp]))

(defn -main [& args]
  (let [settings (core/load-settings)
        ws (core/get-workspace-slug settings)
        path (str "/api/v1/workspaces/" ws "/projects/")
        response (core/get-request settings path {:workspace ws})]

    (println "\n=== RAW RESPONSE ===")
    (pp/pprint response)

    (println "\n=== DATA KEYS ===")
    (when (:data response)
      (if (map? (:data response))
        (println "Data is a map with keys:" (keys (:data response)))
        (println "Data is:" (type (:data response)))))

    (println "\n=== FIRST ITEM (if available) ===")
    (when-let [data (:data response)]
      (when (sequential? data)
        (pp/pprint (first data))))))

(comment
  (-main))

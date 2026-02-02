(ns plane-client.demo.inspect-issue
  "Inspect a work item's full JSON structure to find attachment information"
  (:require [plane-client.core :as plane]
            [plane-client.projects :as projects]
            [plane-client.work-items :as items]
            [cheshire.core :as json]))

(defn pretty-print-json
  "Pretty print a data structure as JSON"
  [data]
  (println (json/generate-string data {:pretty true})))

(defn -main [& _args]
  (println "🔍 Inspecting Work Item Structure")
  (println "==================================\n")

  (let [settings (plane/load-settings)]
    (when-not settings
      (println "✗ Failed to load settings")
      (System/exit 1))

    ;; Find EDEMO project
    (let [existing-projects (projects/list-projects settings)
          demo-project (first (filter #(= "EDEMO" (:identifier %)) existing-projects))]

      (if-not demo-project
        (println "✗ No EDEMO project found")

        (let [project-id (:id demo-project)
              all-items (items/list-work-items settings project-id)]

          (println "📁 Project:" (:name demo-project))
          (println "   ID:" project-id)
          (println "\n📝 Work items:" (count all-items))

          (if (empty? all-items)
            (println "\n✗ No work items found")

            (let [first-item (first all-items)
                  work-item-id (:id first-item)]

              (println "\n🎯 Fetching full details for:")
              (println "   Title:" (:name first-item))
              (println "   ID:" work-item-id)

              ;; Get the full work item details
              (let [full-item (items/get-work-item settings project-id work-item-id)]
                (if-not full-item
                  (println "\n✗ Failed to fetch work item details")

                  (do
                    (println "\n📋 Full Work Item Structure:")
                    (println "============================")
                    (pretty-print-json full-item)

                    ;; Check for attachment-related keys
                    (println "\n\n🔑 Keys in response:")
                    (doseq [k (sort (keys full-item))]
                      (println "  -" k))

                    ;; Look for attachment-related data
                    (println "\n\n🔍 Looking for attachment-related fields...")
                    (doseq [k (keys full-item)]
                      (when (or (re-find #"attach" (str k))
                                (re-find #"file" (str k))
                                (re-find #"asset" (str k)))
                        (println "\n   Found:" k)
                        (println "   Value:" (get full-item k))))))))))))))

(comment
  (-main))

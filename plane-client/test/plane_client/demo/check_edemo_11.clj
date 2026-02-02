(ns plane-client.demo.check-edemo-11
  "Check EDEMO-11 specifically for attachment info"
  (:require [plane-client.core :as plane]
            [plane-client.projects :as projects]
            [plane-client.work-items :as items]
            [cheshire.core :as json]))

(defn pretty-print-json
  "Pretty print a data structure as JSON"
  [data]
  (println (json/generate-string data {:pretty true})))

(defn -main [& _args]
  (println "🔍 Checking EDEMO-11 for Attachments")
  (println "=====================================\n")

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
              all-items (items/list-work-items settings project-id)
              edemo-11 (first (filter #(= 11 (:sequence_id %)) all-items))]

          (if-not edemo-11
            (println "✗ Could not find EDEMO-11")

            (do
              (println "📋 Found EDEMO-11:")
              (println "   Title:" (:name edemo-11))
              (println "   ID:" (:id edemo-11))

              ;; Get full details
              (let [full-item (items/get-work-item settings project-id (:id edemo-11))]
                (println "\n📄 Full Issue Data:")
                (println "===================")
                (pretty-print-json full-item)

                ;; Check for attachment-related keys
                (println "\n\n🔑 All Keys:")
                (doseq [k (sort (keys full-item))]
                  (println "  -" k (str "(" (type (get full-item k)) ")")))))))))))

(comment
  (-main))

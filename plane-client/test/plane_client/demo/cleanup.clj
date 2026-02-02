(ns plane-client.demo.cleanup
  "Cleanup utility to delete all work items from demo project"
  (:require [plane-client.core :as plane]
            [plane-client.projects :as projects]
            [plane-client.work-items :as items]))

(defn delete-all-demo-issues
  "Delete all work items from the EDEMO project"
  []
  (println "🗑️  Cleaning up demo project...")

  (let [settings (plane/load-settings)]
    (when-not settings
      (println "\n✗ Failed to load settings")
      (System/exit 1))

    ;; Find EDEMO project
    (let [existing-projects (projects/list-projects settings)
          demo-project (first (filter #(= "EDEMO" (:identifier %)) existing-projects))]

      (if-not demo-project
        (println "✓ No EDEMO project found - nothing to clean up")

        (let [project-id (:id demo-project)
              all-items (items/list-work-items settings project-id)]

          (println "\n📁 Found project:" (:name demo-project))
          (println "   Project ID:" project-id)
          (println "   Work items to delete:" (count all-items))

          (if (empty? all-items)
            (println "\n✓ No work items to delete")

            (do
              (println "\n🗑️  Deleting work items...")
              (doseq [item all-items]
                (println "   Deleting:" (:name item))
                (items/delete-work-item settings project-id (:id item)))

              (println "\n✓ All work items deleted!")
              (println "   Total deleted:" (count all-items)))))))))

(defn -main [& _args]
  (println "╔══════════════════════════════════════════════════════════════════╗")
  (println "║       CLEANUP DEMO PROJECT                                       ║")
  (println "╚══════════════════════════════════════════════════════════════════╝")

  (try
    (delete-all-demo-issues)
    (println "\n✅ Cleanup complete!")
    (catch Exception e
      (println "\n✗ Error:" (.getMessage e))
      (.printStackTrace e)
      (System/exit 1))))

(comment
  ;; Run cleanup
  (-main))

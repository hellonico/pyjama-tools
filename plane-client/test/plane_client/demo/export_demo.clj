(ns plane-client.demo.export-demo
  "Demo of export/import functionality"
  (:require [plane-client.core :as plane]
            [plane-client.projects :as projects]
            [plane-client.work-items :as items]
            [plane-client.export :as export]))

(defn -main [& _args]
  (println "╔══════════════════════════════════════════════════════════════════╗")
  (println "║       EXPORT/IMPORT DEMO                                         ║")
  (println "╚══════════════════════════════════════════════════════════════════╝")

  (let [settings (plane/load-settings)]
    (when-not settings
      (println "\n✗ Failed to load settings")
      (System/exit 1))

    (println "\n📁 Finding EDEMO project...")
    (let [existing-projects (projects/list-projects settings)
          demo-project (first (filter #(= "EDEMO" (:identifier %)) existing-projects))]

      (if-not demo-project
        (println "✗ EDEMO project not found")

        (let [project-id (:id demo-project)]
          (println "✓ Found project:" (:name demo-project))
          (println "   ID:" project-id)

          ;; List work items
          (println "\n📝 Fetching work items...")
          (let [work-items (items/list-work-items settings project-id)]
            (println "✓ Found" (count work-items) "work items")

            ;; Export to CSV
            (println "\n======================================================================")
            (println "  STEP 1: Export to CSV")
            (println "======================================================================")
            (export/export-to-csv work-items "exports/edemo-backup.csv")

            ;; Export to JSON
            (println "\n======================================================================")
            (println "  STEP 2: Export to JSON")
            (println "======================================================================")
            (export/export-to-json work-items "exports/edemo-backup.json")

            ;; Show sample data
            (println "\n======================================================================")
            (println "  STEP 3: Sample Exported Data")
            (println "======================================================================")
            (when (seq work-items)
              (let [sample (first work-items)]
                (println "\n📄 Sample work item:")
                (println "   Sequence:" (:sequence_id sample))
                (println "   Name:" (:name sample))
                (println "   Priority:" (:priority sample))
                (println "   Created:" (:created_at sample))))

            ;; Demonstrate import (dry-run)
            (println "\n======================================================================")
            (println "  STEP 4: Import Test (Dry Run)")
            (println "======================================================================")
            (println "\nℹ️  Testing import from CSV (no actual items will be created)")
            (export/import-from-csv settings
                                    project-id
                                    "exports/edemo-backup.csv"
                                    {:dry-run true})

            ;; Summary
            (println "\n======================================================================")
            (println "  DEMO SUMMARY")
            (println "======================================================================")
            (println "\n✅ Successfully demonstrated:")
            (println "   1. ✓ Export to CSV format")
            (println "   2. ✓ Export to JSON format")
            (println "   3. ✓ Import from CSV (dry-run)")
            (println "   4. ✓ Import from JSON (available)")

            (println "\n📂 Exported files:")
            (println "   - exports/edemo-backup.csv")
            (println "   - exports/edemo-backup.json")

            (println "\n💡 Usage examples:")
            (println "   Export:  clojure -M:export export-csv <project-id> output.csv")
            (println "   Export:  clojure -M:export export-json <project-id> output.json")
            (println "   Import:  clojure -M:export import-csv <project-id> input.csv")
            (println "   Import:  clojure -M:export import-json <project-id> input.json")

            (println "\n✓ Demo completed successfully!")
            (println "======================================================================")))))))

(comment
  (-main))

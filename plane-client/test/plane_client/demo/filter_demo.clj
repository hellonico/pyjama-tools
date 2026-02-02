(ns plane-client.demo.filter-demo
  "Demo of export/import filtering capabilities"
  (:require [plane-client.core :as plane]
            [plane-client.projects :as projects]
            [plane-client.work-items :as items]
            [plane-client.export :as export]))

(defn -main [& _args]
  (println "╔══════════════════════════════════════════════════════════════════╗")
  (println "║       EXPORT/IMPORT FILTERING DEMO                               ║")
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

          ;; Fetch all work items
          (println "\n📝 Fetching all work items...")
          (let [work-items (items/list-work-items settings project-id)]
            (println "✓ Found" (count work-items) "work items total")

            ;; Show breakdown by priority
            (println "\n📊 Priority breakdown:")
            (doseq [priority ["urgent" "high" "medium" "low" "none"]]
              (let [count (count (filter #(= priority (:priority %)) work-items))]
                (when (pos? count)
                  (println "   " priority ":" count "items"))))

            ;; FILTER 1: Export only urgent issues
            (println "\n======================================================================")
            (println "  FILTER 1: Export Only URGENT Items")
            (println "======================================================================")
            (export/export-to-csv work-items
                                  "exports/urgent-only.csv"
                                  {:priority :urgent})

            ;; FILTER 2: Export high priority issues
            (println "\n======================================================================")
            (println "  FILTER 2: Export HIGH Priority Items")
            (println "======================================================================")
            (export/export-to-json work-items
                                   "exports/high-priority.json"
                                   {:priority "high"})

            ;; FILTER 3: Export multiple priorities
            (println "\n======================================================================")
            (println "  FILTER 3: Export URGENT + HIGH Items")
            (println "======================================================================")
            (export/export-to-csv work-items
                                  "exports/urgent-and-high.csv"
                                  {:priority #{:urgent :high}})

            ;; FILTER 4: Filter by name pattern (BUG items only)
            (println "\n======================================================================")
            (println "  FILTER 4: Export Only BUG Items (Name Pattern)")
            (println "======================================================================")
            (export/export-to-json work-items
                                   "exports/bugs-only.json"
                                   {:name-pattern "\\[BUG\\]"})

            ;; FILTER 5: Filter by sequence range
            (println "\n======================================================================")
            (println "  FILTER 5: Export Items 8-11 (Sequence Range)")
            (println "======================================================================")
            (export/export-to-csv work-items
                                  "exports/sequence-8-11.csv"
                                  {:min-sequence 8
                                   :max-sequence 11})

            ;; FILTER 6: Combined filters (urgent BUGS)
            (println "\n======================================================================")
            (println "  FILTER 6: Export URGENT BUG Items (Combined Filters)")
            (println "======================================================================")
            (export/export-to-json work-items
                                   "exports/urgent-bugs.json"
                                   {:priority :urgent
                                    :name-pattern "\\[BUG\\]"})

            ;; FILTER 7: Date range filter (created today)
            (println "\n======================================================================")
            (println "  FILTER 7: Export Items Created Today")
            (println "======================================================================")
            (let [today "2026-02-02T00:00:00Z"
                  tomorrow "2026-02-03T00:00:00Z"]
              (export/export-to-csv work-items
                                    "exports/created-today.csv"
                                    {:created-from today
                                     :created-to tomorrow}))

            ;; Import filter example
            (println "\n======================================================================")
            (println "  FILTER 8: Import Only HIGH Priority Items (Dry Run)")
            (println "======================================================================")
            (println "\n📥 Demonstrating filtered import from JSON...")
            (export/import-from-json settings
                                     project-id
                                     "exports/edemo-backup.json"
                                     {:dry-run true
                                      :priority :high})

            ;; Summary
            (println "\n======================================================================")
            (println "  DEMO SUMMARY")
            (println "======================================================================")
            (println "\n✅ Filter types demonstrated:")
            (println "   1. ✓ Priority filter (single: :urgent)")
            (println "   2. ✓ Priority filter (multiple: #{:urgent :high})")
            (println "   3. ✓ Name pattern filter (regex: \"\\[BUG\\]\")")
            (println "   4. ✓ Sequence range filter (8-11)")
            (println "   5. ✓ Combined filters (priority + pattern)")
            (println "   6. ✓ Date range filter (created_at)")
            (println "   7. ✓ Import with filters")

            (println "\n📂 Exported files:")
            (println "   - exports/urgent-only.csv")
            (println "   - exports/high-priority.json")
            (println "   - exports/urgent-and-high.csv")
            (println "   - exports/bugs-only.json")
            (println "   - exports/sequence-8-11.csv")
            (println "   - exports/urgent-bugs.json")
            (println "   - exports/created-today.csv")

            (println "\n💡 Available filters:")
            (println "   :priority        - Filter by priority (:urgent, :high, :medium, :low, :none)")
            (println "   :state           - Filter by state ID")
            (println "   :created-from    - Start of created date range (ISO 8601)")
            (println "   :created-to      - End of created date range (ISO 8601)")
            (println "   :updated-from    - Start of updated date range (ISO 8601)")
            (println "   :updated-to      - End of updated date range (ISO 8601)")
            (println "   :name-pattern    - Regex pattern for name matching")
            (println "   :min-sequence    - Minimum sequence ID")
            (println "   :max-sequence    - Maximum sequence ID")

            (println "\n✓ Demo completed successfully!")
            (println "======================================================================")))))))

(comment
  (-main))

(ns plane-client.demo.basic
  "Basic demo of Plane API client capabilities"
  (:require [plane-client.core :as core]
            [plane-client.projects :as projects]
            [plane-client.work-items :as work-items]))

(defn demo-projects
  "Demonstrate project operations"
  [settings]
  (println "\n" (apply str (repeat 60 "=")))
  (println "📁 PROJECTS DEMO")
  (println (apply str (repeat 60 "=")))

  ;; List projects
  (println "\n1. Listing all projects...")
  (let [all-projects (projects/list-projects settings)]
    (projects/print-projects all-projects)
    (when (seq all-projects)
      (println "\n✓ Found" (count all-projects) "project(s)")
      all-projects)))

(defn demo-work-items
  "Demonstrate work item operations"
  [settings project-id]
  (println "\n" (apply str (repeat 60 "=")))
  (println "📝 WORK ITEMS DEMO")
  (println (apply str (repeat 60 "=")))

  ;; List work items
  (println "\n1. Listing work items in project" project-id "...")
  (let [items (work-items/list-work-items settings project-id)]
    (work-items/print-work-items items)
    (when (seq items)
      (println "\n✓ Found" (count items) "work item(s)")
      items)))

(defn demo-api-info
  "Display API connection info"
  [settings]
  (println "\n" (apply str (repeat 60 "=")))
  (println "🌐 API CONNECTION INFO")
  (println (apply str (repeat 60 "=")))
  (println "\nBase URL:" (core/get-base-url settings))
  (println "Workspace:" (core/get-workspace-slug settings))
  (println "API Key:" (str (subs (core/get-api-key settings) 0 15) "..."))
  (println "✓ Configuration loaded successfully"))

(defn -main [& _args]
  (println "╔════════════════════════════════════════════════════════════╗")
  (println "║          Plane API Client Demo                            ║")
  (println "╚════════════════════════════════════════════════════════════╝")

  (try
    ;; Load settings
    (let [settings (core/load-settings)]
      (if-not settings
        (do
          (println "\n✗ Failed to load Plane settings")
          (println "\nPlease configure your Plane API credentials in secrets.edn:")
          (println "{:plane")
          (println "  {:api-key \"plane_api_...\"")
          (println "   :base-url \"https://api.plane.so\"")
          (println "   :workspace-slug \"your-workspace\"}}")
          (System/exit 1))

        ;; Run demos
        (do
          ;; Show connection info
          (demo-api-info settings)

          ;; Demo projects
          (let [all-projects (demo-projects settings)]
            (when (seq all-projects)
              ;; Demo work items for the first project
              (let [first-project (first all-projects)
                    project-id (:id first-project)]
                (demo-work-items settings project-id))))

          (println "\n" (apply str (repeat 60 "=")))
          (println "✓ Demo completed successfully!")
          (println (apply str (repeat 60 "=")))
          (println))))

    (catch Exception e
      (println "\n✗ Error running demo:" (.getMessage e))
      (.printStackTrace e)
      (System/exit 1))))

(comment
  ;; Run the demo from REPL
  (-main))

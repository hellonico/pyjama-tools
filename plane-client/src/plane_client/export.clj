(ns plane-client.export
  "Export and import work items to/from CSV and JSON"
  (:require [plane-client.core :as core]
            [plane-client.work-items :as items]
            [cheshire.core :as json]
            [clojure.string :as str]
            [clojure.java.io :as io]))

;; ============================================================================
;; Filtering Functions
;; ============================================================================

(defn parse-date
  "Parse ISO 8601 date string to instant for comparison"
  [date-str]
  (when date-str
    (try
      (java.time.Instant/parse date-str)
      (catch Exception _ nil))))

(defn filter-by-priority
  "Filter work items by priority level(s).
  
  priority-filter can be:
  - Single keyword: :urgent, :high, :medium, :low, :none
  - Set of keywords: #{:urgent :high}
  - Vector of strings: [\"urgent\" \"high\"]"
  [work-items priority-filter]
  (if-not priority-filter
    work-items
    (let [priorities (cond
                       (keyword? priority-filter) #{(name priority-filter)}
                       (set? priority-filter) (set (map name priority-filter))
                       (coll? priority-filter) (set priority-filter)
                       :else #{(str priority-filter)})]
      (filter #(contains? priorities (:priority %)) work-items))))

(defn filter-by-state
  "Filter work items by state ID(s)"
  [work-items state-filter]
  (if-not state-filter
    work-items
    (let [states (if (coll? state-filter) (set state-filter) #{state-filter})]
      (filter #(contains? states (:state %)) work-items))))

(defn filter-by-date-range
  "Filter work items by date range.
  
  Parameters:
  - field: :created_at or :updated_at
  - from: Start date (ISO 8601 string or Instant)
  - to: End date (ISO 8601 string or Instant)"
  [work-items field from to]
  (let [from-instant (if (string? from) (parse-date from) from)
        to-instant (if (string? to) (parse-date to) to)]
    (filter (fn [item]
              (when-let [item-date-str (get item field)]
                (when-let [item-date (parse-date item-date-str)]
                  (and (or (nil? from-instant) (not (.isBefore item-date from-instant)))
                       (or (nil? to-instant) (not (.isAfter item-date to-instant)))))))
            work-items)))

(defn filter-by-name-pattern
  "Filter work items by name pattern (regex)"
  [work-items pattern]
  (if-not pattern
    work-items
    (let [regex (re-pattern pattern)]
      (filter #(re-find regex (or (:name %) "")) work-items))))

(defn filter-by-sequence-range
  "Filter work items by sequence ID range"
  [work-items min-seq max-seq]
  (filter (fn [item]
            (when-let [seq-id (:sequence_id item)]
              (and (or (nil? min-seq) (>= seq-id min-seq))
                   (or (nil? max-seq) (<= seq-id max-seq)))))
          work-items))

(defn apply-filters
  "Apply multiple filters to work items.
  
  filter-opts can include:
  - :priority - Priority filter (keyword, set, or vector)
  - :state - State ID filter
  - :created-from - Start of created date range
  - :created-to - End of created date range
  - :updated-from - Start of updated date range
  - :updated-to - End of updated date range
  - :name-pattern - Regex pattern for name
  - :min-sequence - Minimum sequence ID
  - :max-sequence - Maximum sequence ID"
  [work-items filter-opts]
  (cond-> work-items
    (:priority filter-opts)
    (filter-by-priority (:priority filter-opts))

    (:state filter-opts)
    (filter-by-state (:state filter-opts))

    (or (:created-from filter-opts) (:created-to filter-opts))
    (filter-by-date-range :created_at (:created-from filter-opts) (:created-to filter-opts))

    (or (:updated-from filter-opts) (:updated-to filter-opts))
    (filter-by-date-range :updated_at (:updated-from filter-opts) (:updated-to filter-opts))

    (:name-pattern filter-opts)
    (filter-by-name-pattern (:name-pattern filter-opts))

    (or (:min-sequence filter-opts) (:max-sequence filter-opts))
    (filter-by-sequence-range (:min-sequence filter-opts) (:max-sequence filter-opts))))

;; ============================================================================
;; CSV Export
;; ============================================================================

(def csv-columns
  "Standard columns for CSV export"
  [:sequence_id :name :priority :state :description_html
   :created_at :updated_at :assignees :labels :id])

(defn escape-csv-field
  "Escape a field for CSV format"
  [field]
  (if (nil? field)
    ""
    (let [s (str field)
          needs-quotes? (or (str/includes? s ",")
                            (str/includes? s "\"")
                            (str/includes? s "\n"))]
      (if needs-quotes?
        (str "\"" (str/replace s "\"" "\"\"") "\"")
        s))))

(defn work-item-to-csv-row
  "Convert a work item to CSV row"
  [work-item]
  (->> csv-columns
       (map #(get work-item %))
       (map #(if (coll? %) (str/join ";" %) %))  ; Join arrays with semicolon
       (map escape-csv-field)
       (str/join ",")))

(defn export-to-csv
  "Export work items to CSV file.
  
  Parameters:
  - work-items: Vector of work item maps
  - output-file: Path to output CSV file
  - opts: Options map with filter options (see apply-filters)
  
  Returns: Number of items exported"
  [work-items output-file & [opts]]
  (let [filtered-items (apply-filters work-items opts)
        original-count (count work-items)
        filtered-count (count filtered-items)]

    (println "📊 Exporting work items to CSV...")
    (when (not= original-count filtered-count)
      (println "   Filtered:" original-count "→" filtered-count "items"))

    (with-open [writer (io/writer output-file)]
      ;; Write header
      (.write writer (str (str/join "," (map name csv-columns)) "\n"))

      ;; Write data rows
      (doseq [item filtered-items]
        (.write writer (str (work-item-to-csv-row item) "\n"))))

    (println "✓ Exported" filtered-count "items to:" output-file)
    filtered-count))

;; ============================================================================
;; JSON Export
;; ============================================================================

(defn export-to-json
  "Export work items to JSON file.
  
  Parameters:
  - work-items: Vector of work item maps
  - output-file: Path to output JSON file
  - opts: Options map:
    - :pretty - Pretty print JSON (default: true)
    - Plus all filter options (see apply-filters)
  
  Returns: Number of items exported"
  [work-items output-file & [opts]]
  (let [filtered-items (apply-filters work-items opts)
        original-count (count work-items)
        filtered-count (count filtered-items)
        pretty? (get opts :pretty true)]

    (println "📊 Exporting work items to JSON...")
    (when (not= original-count filtered-count)
      (println "   Filtered:" original-count "→" filtered-count "items"))

    (let [json-str (json/generate-string filtered-items {:pretty pretty?})]
      (spit output-file json-str))

    (println "✓ Exported" filtered-count "items to:" output-file)
    filtered-count))

;; ============================================================================
;; CSV Import
;; ============================================================================

(defn parse-csv-line
  "Parse a CSV line handling quoted fields"
  [line]
  (let [pattern #",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)"
        fields (str/split line pattern)]
    (mapv (fn [field]
            (let [trimmed (str/trim field)]
              (if (and (str/starts-with? trimmed "\"")
                       (str/ends-with? trimmed "\""))
                (-> trimmed
                    (subs 1 (dec (count trimmed)))
                    (str/replace "\"\"" "\""))
                trimmed)))
          fields)))

(defn csv-row-to-work-item
  "Convert CSV row to work item map"
  [headers row]
  (let [values (parse-csv-line row)]
    (into {}
          (map (fn [header value]
                 [(keyword header)
                  (if (str/blank? value) nil value)])
               headers
               values))))

(defn import-from-csv
  "Import work items from CSV file.
  
  Parameters:
  - settings: Plane settings map
  - project-id: Project ID to import into
  - input-file: Path to input CSV file
  - opts: Options map:
    - :dry-run - Don't actually create items, just parse (default: false)
    - :skip-existing - Skip items that already exist (default: false)
    - Plus all filter options (see apply-filters) - only matching items will be imported
  
  Returns: Map with :imported, :skipped, :failed counts"
  [settings project-id input-file & [opts]]
  (println "📥 Importing work items from CSV:" input-file)

  (let [lines (line-seq (io/reader input-file))
        headers (parse-csv-line (first lines))
        data-lines (rest lines)
        dry-run? (:dry-run opts false)

        ;; Parse all items first
        all-items (keep (fn [row]
                          (when-not (str/blank? row)
                            (try
                              (csv-row-to-work-item headers row)
                              (catch Exception _ nil))))
                        data-lines)

        ;; Apply filters to determine which items to import
        filtered-items (apply-filters all-items opts)
        original-count (count all-items)
        filtered-count (count filtered-items)

        results (atom {:imported 0 :skipped 0 :failed 0})]

    (println "   Found" original-count "work items in file")
    (when (not= original-count filtered-count)
      (println "   Filtered:" original-count "→" filtered-count "items to import"))
    (when dry-run?
      (println "   DRY RUN MODE - no items will be created"))

    (doseq [[idx work-item] (map-indexed vector filtered-items)]
      (try
        (let [name (:name work-item)]
          (when-not (str/blank? name)
            (println (str (inc idx) ".") "Importing:" name)

            (when-not dry-run?
              (let [created (items/create-work-item
                             settings
                             project-id
                             {:name name
                              :description (:description_html work-item)
                              :priority (or (:priority work-item) "none")})]
                (if created
                  (swap! results update :imported inc)
                  (swap! results update :failed inc))))))

        (catch Exception e
          (println "   ✗ Error importing item" (inc idx) ":" (.getMessage e))
          (swap! results update :failed inc))))

    (let [final-results @results]
      (println "\n📊 Import Summary:")
      (println "   Imported:" (:imported final-results))
      (println "   Failed:" (:failed final-results))
      final-results)))

;; ============================================================================
;; JSON Import
;; ============================================================================

(defn import-from-json
  "Import work items from JSON file.
  
  Parameters:
  - settings: Plane settings map
  - project-id: Project ID to import into
  - input-file: Path to input JSON file
  - opts: Options map:
    - :dry-run - Don't actually create items, just parse (default: false)
    - Plus all filter options (see apply-filters) - only matching items will be imported
  
  Returns: Map with :imported, :failed counts"
  [settings project-id input-file & [opts]]
  (println "📥 Importing work items from JSON:" input-file)

  (let [json-data (json/parse-string (slurp input-file) true)
        all-items (if (vector? json-data) json-data [json-data])
        filtered-items (apply-filters all-items opts)
        original-count (count all-items)
        filtered-count (count filtered-items)
        dry-run? (:dry-run opts false)
        results (atom {:imported 0 :failed 0})]

    (println "   Found" original-count "work items in file")
    (when (not= original-count filtered-count)
      (println "   Filtered:" original-count "→" filtered-count "items to import"))
    (when dry-run?
      (println "   DRY RUN MODE - no items will be created"))

    (doseq [[idx item] (map-indexed vector filtered-items)]
      (try
        (let [name (:name item)]
          (when-not (str/blank? name)
            (println (str (inc idx) ".") "Importing:" name)

            (when-not dry-run?
              (let [created (items/create-work-item
                             settings
                             project-id
                             {:name name
                              :description (:description_html item)
                              :priority (or (:priority item) "none")})]
                (if created
                  (swap! results update :imported inc)
                  (swap! results update :failed inc))))))

        (catch Exception e
          (println "   ✗ Error importing item" (inc idx) ":" (.getMessage e))
          (swap! results update :failed inc))))

    (let [final-results @results]
      (println "\n📊 Import Summary:")
      (println "   Imported:" (:imported final-results))
      (println "   Failed:" (:failed final-results))
      final-results)))

;; ============================================================================
;; CLI Interface
;; ============================================================================

(defn -main [& args]
  (let [[command & rest-args] args]
    (cond
      (not command)
      (do
        (println "Usage: clojure -M:export <command> [args]")
        (println)
        (println "Commands:")
        (println "  export-csv <project-id> <output-file>    - Export to CSV")
        (println "  export-json <project-id> <output-file>   - Export to JSON")
        (println "  import-csv <project-id> <input-file>     - Import from CSV")
        (println "  import-json <project-id> <input-file>    - Import from JSON")
        (println)
        (println "Examples:")
        (println "  clojure -M:export export-csv my-project-id issues.csv")
        (println "  clojure -M:export import-json my-project-id backup.json")
        (System/exit 1))

      :else
      (let [settings (core/load-settings)]
        (when-not settings
          (println "✗ Failed to load settings")
          (System/exit 1))

        (case command
          "export-csv"
          (let [[project-id output-file] rest-args]
            (if-not (and project-id output-file)
              (println "Usage: export-csv <project-id> <output-file>")
              (let [work-items (items/list-work-items settings project-id)]
                (export-to-csv work-items output-file))))

          "export-json"
          (let [[project-id output-file] rest-args]
            (if-not (and project-id output-file)
              (println "Usage: export-json <project-id> <output-file>")
              (let [work-items (items/list-work-items settings project-id)]
                (export-to-json work-items output-file))))

          "import-csv"
          (let [[project-id input-file] rest-args]
            (if-not (and project-id input-file)
              (println "Usage: import-csv <project-id> <input-file>")
              (import-from-csv settings project-id input-file)))

          "import-json"
          (let [[project-id input-file] rest-args]
            (if-not (and project-id input-file)
              (println "Usage: import-json <project-id> <input-file>")
              (import-from-json settings project-id input-file)))

          ;; Unknown command
          (do
            (println "Unknown command:" command)
            (System/exit 1)))))))

(comment
  ;; Export examples
  (def settings (core/load-settings))
  (def items (items/list-work-items settings "project-id"))

  (export-to-csv items "issues.csv")
  (export-to-json items "issues.json")

  ;; Import examples
  (import-from-csv settings "project-id" "issues.csv" {:dry-run true})
  (import-from-json settings "project-id" "issues.json"))

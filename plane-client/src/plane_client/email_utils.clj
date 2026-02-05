(ns plane-client.email-utils
  "Utilities for processing emails and extracting metadata"
  (:require [clojure.string :as str]))

;; ============================================================================
;; Priority Detection
;; ============================================================================

(def priority-keywords
  "Keywords that indicate priority levels"
  {:urgent   #{"urgent" "critical" "asap" "emergency" "immediately" "broken" "down" "outage"}
   :high     #{"important" "high" "priority" "blocker" "blocking" "can't" "cannot"}
   :medium   #{"medium" "moderate" "should" "would"}
   :low      #{"low" "minor" "small" "typo" "cosmetic"}})

(defn detect-priority
  "Detect priority from email subject and body.
  
  Returns: :urgent, :high, :medium, :low, or :none"
  [subject body]
  (let [text (str/lower-case (str subject " " body))
        words (set (str/split text #"\W+"))]
    (cond
      ;; Check for urgent keywords
      (some words (:urgent priority-keywords)) :urgent

      ;; Check for high priority keywords
      (some words (:high priority-keywords)) :high

      ;; Check for medium priority keywords
      (some words (:medium priority-keywords)) :medium

      ;; Check for low priority keywords
      (some words (:low priority-keywords)) :low

      ;; Default to none
      :else :none)))

(defn priority-keyword->plane
  "Convert priority keyword to Plane API format"
  [priority]
  (case priority
    :urgent "urgent"
    :high "high"
    :medium "medium"
    :low "low"
    :none "none"
    "none"))

;; ============================================================================
;; Assignment Detection
;; ============================================================================

(def team-assignments
  "Map email patterns to team members"
  {:backend   #{"backend" "api" "database" "server" "performance"}
   :frontend  #{"frontend" "ui" "ux" "css" "html" "display" "layout"}
   :devops    #{"deploy" "deployment" "docker" "kubernetes" "ci" "cd" "pipeline"}
   :qa        #{"test" "testing" "qa" "quality" "bug"}
   :security  #{"security" "auth" "authentication" "authorization" "password" "login"}})

(defn detect-team
  "Detect which team should handle this issue based on keywords.
  
  Returns: :backend, :frontend, :devops, :qa, :security, or nil"
  [subject body]
  (let [text (str/lower-case (str subject " " body))
        words (set (str/split text #"\W+"))]
    (->> team-assignments
         (filter (fn [[_team keywords]] (some words keywords)))
         first
         first)))

(defn get-assignee-for-team
  "Get assignee ID for a team (would come from workspace members API in real implementation)"
  [team]
  (case team
    :backend  {:team "Backend Team" :note "Would assign to backend engineer"}
    :frontend {:team "Frontend Team" :note "Would assign to frontend engineer"}
    :devops   {:team "DevOps Team" :note "Would assign to DevOps engineer"}
    :qa       {:team "QA Team" :note "Would assign to QA engineer"}
    :security {:team "Security Team" :note "Would assign to security engineer"}
    {:team "General" :note "Would assign to general pool"}))

;; ============================================================================
;; Attachment Detection
;; ============================================================================

(defn extract-attachments
  "Extract attachments from email (simulated for demo).
  
  In real implementation, this would parse email MIME parts and extract files."
  [email]
  ;; Simulate finding attachments based on email content
  (let [body (:body email)
        has-screenshot? (str/includes? (str/lower-case body) "screenshot")
        has-log? (str/includes? (str/lower-case body) "log")
        has-trace? (str/includes? (str/lower-case body) "stack trace")]
    (cond-> []
      has-screenshot? (conj {:filename "screenshot.png"
                             :size "245 KB"
                             :type "image/png"
                             :note "Simulated attachment"})
      has-log? (conj {:filename "error.log"
                      :size "12 KB"
                      :type "text/plain"
                      :note "Simulated attachment"})
      has-trace? (conj {:filename "stacktrace.txt"
                        :size "8 KB"
                        :type "text/plain"
                        :note "Simulated attachment"}))))

(defn format-attachments-description
  "Format attachments as markdown for issue description"
  [attachments]
  (when (seq attachments)
    (str "\n\n### Attachments\n"
         (str/join "\n"
                   (map #(str "- **" (:filename %) "** (" (:size %) ") - " (:note %))
                        attachments)))))

;; ============================================================================
;; Email Analysis Summary
;; ============================================================================

(defn analyze-email
  "Analyze email and extract all metadata.
  
  Returns: Map with :priority, :team, :assignee, :attachments"
  [email]
  (let [subject (:subject email)
        body (:body email)
        _ (println "\n🔍 DEBUG: analyze-email called:")
        _ (println "   Raw body type:" (type body))
        _ (println "   Raw body length:" (count (str body)))
        _ (println "   Raw body preview:" (subs (str body) 0 (min 100 (count (str body)))))
        priority (detect-priority subject body)
        team (detect-team subject body)
        assignee (get-assignee-for-team team)
        ;; Use real attachments from email observation, not simulated ones
        attachments (:attachments email [])
        ;; Format attachment list for description if attachments exist
        attachment-desc (when (seq attachments)
                          (str "\n\n### Attachments\n"
                               (str/join "\n"
                                         (map #(str "- **" (:filename %) "** ("
                                                    (or (:size %) "unknown size") ")")
                                              attachments))))
        enhanced-desc (str body attachment-desc)
        _ (println "   Enhanced description length:" (count (str enhanced-desc)))
        _ (println "   Enhanced description preview:" (subs (str enhanced-desc) 0 (min 100 (count (str enhanced-desc)))))]
    {:priority priority
     :priority-plane (priority-keyword->plane priority)
     :team team
     :assignee assignee
     :attachments attachments
     :enhanced-description enhanced-desc}))

(defn print-analysis
  "Pretty-print email analysis"
  [analysis]
  (println "\n📊 Email Analysis:")
  (println "   Priority:" (:priority analysis) "→" (:priority-plane analysis))
  (println "   Team:" (or (:team analysis) "none detected"))
  (println "   Assignment:" (get-in analysis [:assignee :team]))
  (println "   Attachments:" (count (:attachments analysis)))
  (when (seq (:attachments analysis))
    (doseq [att (:attachments analysis)]
      (println "      -" (:filename att) (str "(" (:size att) ")")))))

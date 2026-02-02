(ns plane-client.demo.test-upload
  "Test attachment upload"
  (:require [plane-client.core :as core]
            [plane-client.attachments :as att]
            [clojure.java.io :as io]))

(defn -main [& args]
  (println "🧪 Testing Attachment Upload")
  (println "============================\n")

  (let [settings (core/load-settings)
        project-id "e9a526c9-3ac1-4b10-9437-fa46003ec55a"
        work-item-id "17f842ec-682e-483e-9b0b-ced8c9cc56cb"
        test-file "test-upload.png"]

    (when-not (.exists (io/file test-file))
      (println "✗ Test file not found:" test-file)
      (System/exit 1))

    (println "📁 Project ID:" project-id)
    (println "📝 Work Item ID:" work-item-id)
    (println "📎 File:" test-file)
    (println)

    (let [result (att/upload-attachment settings project-id work-item-id test-file)]
      (if result
        (do
          (println "\n✅ Upload successful!")
          (println "   Attachment ID:" (:id result)))
        (println "\n✗ Upload failed")))))

(comment
  (-main))

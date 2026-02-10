(ns greeter-tool.core
  "A simple greeter tool that demonstrates external tool integration"
  (:require [clojure.edn :as edn]))

(defn greet
  "Generate a personalized greeting message"
  [{:keys [name language style] :or {language "english" style "formal"}}]
  (let [greetings {"english" {:formal "Good day, %s. It is a pleasure to meet you."
                              :casual "Hey %s! What's up?"
                              :enthusiastic "Hello %s!!! So great to see you!"}
                   "french"  {:formal "Bonjour, %s. Enchanté de vous rencontrer."
                              :casual "Salut %s! Ça va?"
                              :enthusiastic "Bonjour %s!!! Ravi de te voir!"}
                   "japanese" {:formal "%sさん、こんにちは。お会いできて光栄です。"
                               :casual "%sさん、やあ！元気？"
                               :enthusiastic "%sさん、こんにちは！！！会えて嬉しい！"}}
        template (get-in greetings [language (keyword style)]
                         "Hello, %s!")
        message (format template name)]
    {:status :ok
     :greeting message
     :metadata {:name name
                :language language
                :style style
                :timestamp (str (java.time.Instant/now))}}))

(defn analyze-name
  "Analyze a name and return interesting facts"
  [{:keys [name]}]
  (let [length (count name)
        vowels (count (filter #{\a \e \i \o \u \A \E \I \O \U} name))
        consonants (- length vowels)
        first-letter (first name)
        last-letter (last name)]
    {:status :ok
     :analysis {:length length
                :vowels vowels
                :consonants consonants
                :first-letter (str first-letter)
                :last-letter (str last-letter)
                :palindrome? (= name (apply str (reverse name)))}}))

(defn -main
  "CLI entry point - reads EDN from stdin, executes function, prints EDN to stdout"
  [& args]
  (let [input (edn/read-string (slurp *in*))
        function-name (:function input)
        params (:params input)
        result (case function-name
                 "greet" (greet params)
                 "analyze-name" (analyze-name params)
                 {:status :error
                  :message (str "Unknown function: " function-name)})]
    (prn result)))

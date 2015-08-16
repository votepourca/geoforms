(ns geoforms.tasks
  (:refer-clojure :exclude [update ref])
  (:require [matchbox.core :as m]
            [clojure.string :as str]
            [clojure.data.csv :as csv]
            [clojure.java.io :refer [reader]]
            [geoforms.firebase :as fb])
  (:gen-class))

(defn update [m k f]
  (if-not (get m k)
    m
    (clojure.core/update m k f)))

(def header-lookup
  {;; idea
   "Timestamp"       :created-at
   "Quartier"        :districts
   "Idee"            :title
   "Categorie"       :category
   "Desc"            :desc
   "URLS"            :urls
   "Courriels"       :supporters
   ;; user
   "Courriel"        :email
   "Votre nom"       :full-name
   "Code postal"     :zip-code
   "Alert district?" :alert-districts?
   "Alert ideas?"    :alert-ideas?
   "Volonteer?"      :alert-volunteer?
   "Dans quelle tranche d'âge vous situez-vous?" :age})

(defn split-list [s]
  (vec (remove empty? (map str/trim (str/split s #",")))))

(defn parse-age [s]
  (let [[_ low high] (re-find #"(\d+)[^\d]+(\d+)*" s)]
    (keyword (str low "-" high))))

(defn parse-bool [s]
  (case s
    "Oui" true
    "Yes" true
    "Non" false
    "No"  false
    false))

(defn read-csv [path]
  (with-open [file (reader path)]
    (let [[header & rows] (csv/read-csv file :separator \tab)
          headers (map header-lookup header)]
      (->> rows
           (map #(zipmap headers %))
           ;; idea?
           (map #(update % :urls split-list))
           (map #(update % :supporters (comp ->set-map split-list)))
           (map #(update % :districts split-list))
           ;; user?
           (map #(update % :age parse-age))
           (map #(update % :alert-districts? parse-bool))
           (map #(update % :alert-ideas? parse-bool))
           (map #(update % :alert-volunteer? parse-bool))
           (doall)))))

(def supported-types
  #{":users" ":ideas"})

(defn load-fn [type]
  (case type
    ":users" fb/create-user!
    ":ideas" fb/create-idea!))

(defn -main [type filepath]
  (println (str "Loading " type " from " filepath))
  (assert (contains? supported-types type)
          (str type " must be one of "
               (str/join ", " supported-types)))
  (let [entities (read-csv filepath)
        loader   (load-fn type)
        remain   (atom (count entities))]
    (println (str "Found " @remain " entities"))
    (doseq [e entities]
      (let [out *out*]
        (loader e (fn [_]
                    (binding [*out* out]
                      (print "."))
                    (swap! remain dec)))))
    (while (pos? @remain)
      (Thread/sleep 100))
    (println "")
    (println "Done!")))

(comment
  (-main ":users" "users.tsv")
  (-main ":ideas" "ideas.tsv"))

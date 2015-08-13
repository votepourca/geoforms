(ns geoforms.db
  (:require [clojure.string :as str]
            [matchbox.core :as m]
            [matchbox.atom :as ma]
            [matchbox.reagent :as mr]
            [reagent.core :refer [atom]]
            [geoforms.config :as config]))

;; helpers

(defn ->id-vec
  "Convert mapping of ids to maps, to vector of maps with id inside.
   eg. in: {:a {:b 2}, ...}
      out: [{:id :a, :b 2}, ...]"
  [hsh]
  (mapv #(assoc %2 :id %1)
        (keys hsh)
        (vals hsh)))

(defn ->map-set
  "Convert a map to a set of the keys"
  [hsh]
  (set (keys hsh)))

(defn ->set-map
  "Convert a set into a map of {element true}"
  [set]
  (zipmap set (repeat true)))

(defn munge- [email]
  (-> email
      (str/replace "@" "_at_")
      (str/replace "." "_dot_")))


;; refs

(def ref (m/connect config/fb-uri [:community-app]))

(def ideas-ref (m/get-in ref [:ideas]))

(def users-ref (m/get-in ref [:users]))

(def categories-ref (m/get-in ref [:categories]))

(def districts-ref (m/get-in ref [:districts]))

(defn supported-ideas-path [email]
  [:users (munge- email) :supports])


;; constants

(def age-groups
  [:17-
   :18-24
   :25-29
   :30-34
   :35-39
   :40-44
   :45-49
   :50-54
   :55-59
   :60-64
   :65-69
   :70-74
   :75-79
   :80-84
   :85+])


;; atoms

(def email (atom nil))

(def ideas (mr/sync-r ideas-ref ->id-vec))

(def categories (mr/sync-list categories-ref))

(def districts (mr/sync-list districts-ref))

(def supported-ideas (atom #{}))

(defn logout! []
  (ma/unlink! supported-ideas)
  (reset! supported-ideas #{}))

(defn login! [email]
  (logout!)
  (reset! email email)
  (mr/sync-r (supported-ideas-path email) ->map-set))


;; mutators

(defn toggle-idea-support!
  "Toggle whether idea is supported by user"
  [id supported?]
  ;; lean on setting to nil to delete
  (if @email
    (m/reset-in! ref
                 (conj (supported-ideas-path @email) id)
                 (when-not supported? true))
    (swap! supported-ideas (if supported? disj conj) id)))

(defn create-idea! [{:keys [title desc category districts] :as idea}]
  (m/conj-in! ref [:ideas]
              {:title     title
               :desc      desc
               :category  category
               :districts districts}))

(defn create-user!
  [{:keys [email] :as user}
   ideas]
  (let [path [:users (munge- email)]]
    ;; upsert the user
    (m/swap-in! ref path
                (fn [{:keys [created-at] :as existing}]
                  (assoc user :created-at (or created-at m/SERVER_TIMESTAMP))))
    ;; upsert the supported ideas
    (m/reset-in! ref (supported-ideas-path email)
                 (->set-map ideas))))


;; fixtures

(defn load-fixtures! [ref values]
  (m/deref ref (fn [data] (when-not (seq data)
                           (doseq [v values]
                             (m/conj! ref v))))))

;; (defonce app-cms
;;   (atom
;;    {:title                 "Support local ideas!"
;;     :subtitle              ""
;;     :instructions-district "1. Choose your district"
;;     :instructions-vote     "2. Check ideas you want to support!"
;;     :instructions-add      "3. Add your won ideas"
;;     :instructions-sign     "4. Sign your choices"}))

(load-fixtures!
 ideas-ref
 [{:created-at "2013-08-10 11:20:22"
   :districts  ["Brooklyn"]
   :title      "New skatepark"
   :desc       "Would be really nice to have a new skatepark"
   :links      ["http://lapresse.ca/article-2"]
   :supporters [""]
   :category   "Other..."}
  {:created-at "2013-08-10 11:20:24"
   :districts  ["Manhattan"]
   :title      "More police to prevent stealing"
   :desc       ""
   :links      ["http://lapresse.ca/article-1"]
   :supporters [""]
   :category   "Security"}
  {:created-at "2013-08-10 11:20:26"
   :districts  ["Manhattan"]
   :title      "Create a park near fifth avenue"
   :desc       "Would be awesome to have a park on broadway near fith avenue."
   :links      ["http://lapresse.ca/article-56"]
   :supporters ["email@email.com" "email2@email.com" "john@hotmail.com"]
   :category   "Green"}])

(load-fixtures!
 users-ref
 [{:created-at       "2013-08-10 11:20:22"
   :fullname         "Leon Talbot"
   :email            "email@email.com"
   :zip-code         "G21 2C5"
   :age              "32"
   :annual-revenue   ""
   :alert-ideas?     true
   :alert-volunteer? false
   :alert-districts? true
   :comments         ""}
  {:created-at       "2013-08-10 11:20:22"
   :fullname         "John Talbot"
   :email            "john@hotmail.com"
   :zip-code         "G21 2C3"
   :age              "33"
   :annual-revenue   ""
   :alert-ideas?     true
   :alert-volunteer? false
   :alert-districts? true
   :comments         ""}
  {:created-at       "2013-08-10 11:20:22"
   :fullname         "Marc Talbot"
   :email            "email2@email.com"
   :zip-code         "G21 2C2"
   :age              "34"
   :annual-revenue   ""
   :alert-ideas?     true
   :alert-volunteer? false
   :alert-districts? true
   :comments         "better UX please. Thanks."}])

(load-fixtures!
 categories-ref
 ["Shops" "Security" "Green" "Other..."])

(load-fixtures!
 districts-ref
 ["Manhattan" "Brooklyn" "Queens" "Other districts"])

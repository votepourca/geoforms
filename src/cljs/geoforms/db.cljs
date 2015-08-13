(ns geoforms.db
  (:require [clojure.string :as str]
            [matchbox.core :as m]
            [matchbox.atom :as ma]
            [matchbox.reagent :as mr]
            [reagent.core :refer [atom]]
            [geoforms.config :as config]))

;; helpers

(def mungings
  {"." "__dot__"
   "#" "__hash__"
   "$" "__dollar__"
   "/" "__slash__"
   "[" "__lbrace__"
   "]" "__rbrace__"})

(defn munge- [s]
  (if-not (string? s)
    s
    (reduce (fn [s [in out]] (str/replace s in out))
            s
            mungings)))

(defn de-munge [s]
  (if-not (string? s)
    s
    (reduce (fn [s [in out]] (str/replace s out in))
            s
            mungings)))

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
  (set (map de-munge (keys hsh))))

(defn ->set-map
  "Convert a set into a map of {element true}"
  [set]
  (zipmap (map munge- set) (repeat true)))

;; refs

(def ref (m/connect config/fb-uri [:community-app]))

(def ideas-ref (m/get-in ref [:ideas]))

(def users-ref (m/get-in ref [:users]))

(def categories-ref (m/get-in ref [:categories]))

(def districts-ref (m/get-in ref [:districts]))

(defn supported-ideas-path [email]
  [:users (munge- email) :supports])

(defn idea-supporters-path [id]
  [:ideas id :supporters])

;; constants

(def language :en)

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

(defn- set-user->idea
  "Update reference from user to idea"
  [user-email idea-id add?]
  ;; lean on setting value to nil to delete keys from map
  (let [path (conj (supported-ideas-path user-email) idea-id)]
    (m/reset-in! ref path add?)))

(defn- set-user->ideas
  "Update user to (only) reference all idea-ids"
  [user-email idea-ids]
  ;; upsert all associations (implicit deletes)
  (let [path (conj (supported-ideas-path (munge- user-email)))]
    (m/reset-in! ref path (->set-map idea-ids))))

(defn- set-idea->user
  "Update reference from idea to user"
  [idea-id user-email add?]
  ;; lean on setting value to nil to delete keys from map
  (let [path (conj (idea-supporters-path idea-id) (munge- user-email))]
    (m/reset-in! ref path add?)))

(defn- set-ideas->user
  "Update all ideas to reference user"
  [idea-ids user-email]
  (doseq [id idea-ids]
    (set-idea->user id user-email true)))

(defn set-user-idea
  "Update references between user in both directions"
  [user-email idea-id add?]
  (set-user->idea user-email idea-id add?)
  (set-idea->user idea-id user-email add?))

(defn set-user-ideas
  "Mass update user to reference only given ideas.
   Updates given ideas to reference back to user
   BEWARE: this does not remove user references from other ideas"
  [user-email idea-ids]
  (set-user->ideas user-email idea-ids)
  (set-ideas->user idea-ids user-email))

(defn set-idea-support!
  "Toggle whether idea is supported by user"
  [id supported?]
  (if @email
    ;; if user is signed in, update synced idea and user data directly
    (let [add? (when supported? true)]
      (set-user-idea @email id add?))
    ;; otherwise update local user->idea map only
    (swap! supported-ideas (if supported? conj disj) id)))

(defn create-idea! [{:keys [title desc category districts] :as idea}]
  (m/conj-in! ref [:ideas]
              {:title     title
               :desc      desc
               :category  category
               :districts districts}))

(defn create-user!
  [{:keys [email] :as user}
   ideas]
  (prn user)
  (let [path [:users (munge- email)]]
    ;; upsert the user
    (m/swap-in! ref path
                (fn [{:keys [created-at] :as existing}]
                  (assoc user :created-at (or created-at m/SERVER_TIMESTAMP))))
    ;; upsert the supported ideas
    (set-user-ideas email ideas)))


;; fixtures

(defn load-fixtures! [ref values]
  (m/deref ref (fn [data] (when-not (seq data)
                           (doseq [v values]
                             (m/conj! ref v))))))

(def app-cms
  (atom
   {:en {:title      "Support local ideas!"
         :subtitle   ""
         :h-district "1. Choose your district"
         :h-vote     "2. Check ideas you want to support!"
         :h-add      "3. Add your won ideas"
         :h-sign     "4. Sign your choices"}
    :fr {:title      "Soutiennent les idées locales!"
         :subtitle   ""
         :h-district "1. Choisissez votre quartier"
         :h-vote     "2. Vérifier les idées que vous voulez soutenir!"
         :h-add     "3. Ajouter vos idées gagné"
         :h-sign     "4. Connectez-vous à votre choix"}}))

(defn snippet [key]
  (get-in @app-cms [language key]))

(load-fixtures!
 ideas-ref
 [{:created-at "2013-08-10 11:20:22"
   :districts  ["Brooklyn"]
   :title      "New skatepark"
   :desc       "Would be really nice to have a new skatepark"
   :links      ["http://lapresse.ca/article-2"]
   :supporters (->set-map [])
   :category   "Other..."}
  {:created-at "2013-08-10 11:20:24"
   :districts  ["Manhattan"]
   :title      "More police to prevent stealing"
   :desc       ""
   :links      ["http://lapresse.ca/article-1"]
   :supporters (->set-map [])
   :category   "Security"}
  {:created-at "2013-08-10 11:20:26"
   :districts  ["Manhattan"]
   :title      "Create a park near fifth avenue"
   :desc       "Would be awesome to have a park on broadway near fith avenue."
   :links      ["http://lapresse.ca/article-56"]
   :supporters (->set-map ["email@email.com" "email2@email.com" "john@hotmail.com"])
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

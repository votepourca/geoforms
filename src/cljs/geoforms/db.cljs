(ns geoforms.db
  (:require-macros [reagent.ratom :refer [reaction]])
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

(def ref (m/connect config/fb-uri))

(def ideas-ref (m/get-in ref [:ideas]))

(def categories-ref (m/get-in ref [:categories]))

(def districts-ref (m/get-in ref [:districts]))

(defn supported-ideas-path [email]
  [:users (munge- email) :supports])

(defn idea-supporters-path [id]
  [:ideas id :supporters])

;; constants

(def language (atom :en))

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

(def category-translations
  (atom {:en ["Greening"
              "Mobility"
              "Infrastructure"
              "Shops"
              "Activities"
              "Habitation"
              "Other"]
         :fr ["Verdissement"
              "Mobilité"
              "Infrastructure"
              "Commerces"
              "Activités"
              "Habitations"
              "Autre"]}))

;; atoms

(def email (atom nil))

(def ideas (mr/sync-r ideas-ref (comp ->id-vec sort)))

(def categories (reaction (get @category-translations @language)))

(def districts
  (atom ["Beauport"
         "Charlesbourg"
         "Lairet"
         "Maizerets"
         "Méandres"
         "Montcalm"
         "Saint-Jean-Baptiste"
         "Saint-Roch"
         "Saint-Sauveur"
         "Sainte-Foy"
         "Sillery"
         "Vieux-Limoilou"
         "Autre"]))

(def supported-ideas (atom #{}))

(defonce selected-district (atom nil))

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
  (let [path [:users (munge- email)]]
    ;; upsert the user
    (m/reset-in! ref path (assoc user :created-at m/SERVER_TIMESTAMP))
    ;; upsert the supported ideas
    (set-user-ideas email ideas)))


;; translations

(def app-cms
  (atom
   {:en {:page-title                  "Support local ideas!"
         :page-subtitle               ""
         :h-district                  "Step 1. Choose your district"
         :h-vote                      "Step 2. Check ideas you want to support!"
         :h-add                       "Step 3. Add your own ideas"
         :h-sign                      "Step 4. Sign your choices"
         :person.alert-ideas?         "I want to get noticed about my supported ideas progress."
         :person.alert-volunteer?     "I want to get noticed about volonteer opportunities regarding ideas I voted for."
         :person.alert-districts?     "I want to get noticed about major updates in my supported district(s)."
         :comments                    "Comments"
         :email                       "Email"
         :last-name                   "Last name"
         :first-name                  "First name"
         :age                         "Age"
         :add-description             "Add a description"
         :add-idea                    "Add your idea"
         :optional                    "(optional)"
         :idea-title                  "Idea title"
         :add-idea-to                 "Add idea to"
         :idea                        "idea"
         :select-category             "Select a category..."
         :add-url                     "Add an URL"
         :select-age                  "Select your age..."
         :submit                      "Submit"
         :not-valid                   "not valid"
         :required                    "required"
         :not-set                     "not set"
         :already-used                "already used"
         :title                       "Title"
         :category                    "Category"
         :yes                         "Yes"
         :no                          "No"
         :new-ideas-saved-once-signed "Your new idea(s) will be saved once you sign and submit (see the bottom of the form)"
         :completed-title             "Thank you!"
         :complted-message           "Your choices have been submitted. If you've added ideas, it should be now public."}
    :fr {:page-title                  "Appuyez et suggérez des idées pour votre quartier!"
         :page-subtitle               ""
         :h-district                  "Étape 1. Choisissez votre quartier (ou vos quartiers)"
         :h-vote                      "Étape 2. Cochez les idées que vous souhaitez appuyer!"
         :h-add                       "Étape 3. Ajoutez vos idées"
         :h-sign                      "Étape 4. Signez vos choix"
         :person.alert-ideas?         "Je veux être tenu au courant de la progression des idées que j'ai appuyées."
         :person.alert-volunteer?     "Je veux être informé des opportunités de bénévolat entourant les idées que j'ai appuyées."
         :person.alert-districts?     "Je veux être informé de développements majeurs sur les activités de Votepour.ca dans mon quartier."
         :comments                    "Commentaires"
         :email                       "Courriel"
         :last-name                   "Nom"
         :first-name                  "Prénom"
         :age                         "Âge"
         :add-description             "Ajoutez une description"
         :add-idea                    "Ajoutez votre idée"
         :optional                    "(facultatif)"
         :idea-title                  "Titre de l'idée"
         :add-idea-to                 "Ajoutez une idée à"
         :idea                        "idée"
         :select-category             "Choisissez une catégorie"
         :add-url                     "Ajoutez une URL de reférence"
         :select-age                  "Selectionnez votre âge"
         :submit                      "Envoyez"
         :not-valid                   "non valide"
         :required                    "requis"
         :not-set                     "non choisie"
         :already-used                "déjà utilisé"
         :title                       "Titre"
         :category                    "Catégorie"
         :yes                         "Oui"
         :no                          "Non"
         :new-ideas-saved-once-signed "Votre idée ou vos idées seront sauvegardées dès que vous signerez et enverrez vos choix (voir le bas du formulaire)"
         :complted-title              "Merci!"
         :completed-message           "Vos choix ont été soumis. Si vous avez ajouté des idées, il devrait être désormais public."}}))

(defn snippet [key]
  (get-in @app-cms [@language key]))


;; auth

(defn init-session []
  ;; TODO: to be more robust, should really handle failures here,
  ;; and defer rest of app loading
  (m/auth-anon ref prn))

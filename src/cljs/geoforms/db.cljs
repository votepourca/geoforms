(ns geoforms.db
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [clojure.string :as str]
            [reagent.core :refer [atom]]
            [matchbox.atom :as ma]
            [matchbox.reagent :as mr]
            [geoforms.config :as config]
            [geoforms.firebase :as fb]))

;; constants

(def language (atom :fr))

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

(def ideas (mr/sync-r fb/ideas-ref (comp fb/->id-vec sort)))

(def categories (reaction (get @category-translations @language)))

(def districts
  (atom ["Ancienne-Lorette"
         "Beauport"
         "Cap-Rouge"
         "Charlesbourg"
         "Duberger-Les-Saules"
         "Lac-Beauport"
         "Lac-Saint-Charles"
         "Lairet"
         "Lebourgneuf"
         "Loretteville"
         "Maizerets"
         "Méandres"
         "Montcalm"
         "Neufchâtel"
         "Saint-Augustin"
         "Saint-Émile"
         "Saint-Jean-Baptiste"
         "Saint-Roch"
         "Saint-Sauveur"
         "Sainte-Foy"
         "Sillery"
         "Stoneham"
         "Val-Bélair"
         "Vanier"
         "Vieux-Limoilou"
         "Vieux-Québec"
         "Autre"]))


(def district-ideas
  (reaction (let [ds @districts
                  is @ideas
                  f (fn [d] (filter (comp #(some #{d} %) :districts) is))]
              (zipmap ds (map f ds)))))

(def district-counts
  (reaction (let [di @district-ideas]
              (zipmap (keys di) (map count (vals di))))))

(def districts-by-count
  (reaction (mapv first (sort-by (comp - last) @district-counts))))

(def supported-ideas (atom #{}))

(defonce selected-district (atom nil))

(defn logout! []
  (ma/unlink! supported-ideas)
  (reset! supported-ideas #{}))

(defn login! [email]
  (logout!)
  (reset! email email)
  (mr/sync-r (fb/supported-ideas-path email) fb/->map-set))

(defn set-idea-support!
  "Toggle whether idea is supported by user"
  [id supported?]
  (if @email
    ;; if user is signed in, update synced idea and user data directly
    (let [add? (when supported? true)]
      (fb/set-user-idea @email id add?))
    ;; otherwise update local user->idea map only
    (swap! supported-ideas (if supported? conj disj) id)))


;; translations

(def app-cms
  (atom
   {:en {:page-title                  "Support local ideas!"
         :page-subtitle               ""
         :h-district                  "Step 1: Choose your district"
         :h-vote                      "Step 2: Check ideas you want to support!"
         :h-vote-p                    "These ideas has been submitted by citizens like you!"
         :h-add                       "Step 3: Add your own ideas"
         :h-sign                      "Last Step: Sign your choices"
         :person.alert-ideas?         "I want to get noticed about my supported ideas progress."
         :person.alert-volunteer?     "I want to get noticed about volonteer opportunities regarding ideas I voted for."
         :person.alert-districts?     "I want to get noticed about major updates in my supported district(s)."
         :comments                    "Comments"
         :email                       "Email"
         :last-name                   "Last name"
         :first-name                  "First name"
         :age                         "Age"
         :zip-code                         "Postal code"
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
         :completed-message           "Your choices have been submitted. If you've added ideas, it should be now public."}
    :fr {:page-title                  "Appuyez et suggérez des idées pour votre quartier!"
         :page-subtitle               ""
         :h-district                  "Étape 1: Choisissez votre quartier (ou vos quartiers)"
         :h-vote                      "Étape 2: Cochez les idées que vous souhaitez appuyer!"
         :h-vote-p                  "Ces idées ont été proposées par des citoyens participants comme vous!"
         :h-add                       "Étape 3: Ajoutez vos idées"
         :h-sign                      "Étape finale: Signez vos choix"
         :person.alert-ideas?         "Je veux être tenu au courant de la progression des idées que j'ai appuyées."
         :person.alert-volunteer?     "Je veux être informé des opportunités de bénévolat entourant les idées que j'ai appuyées."
         :person.alert-districts?     "Je veux être informé de développements majeurs sur les activités de Votepour.ca dans mon quartier."
         :comments                    "Commentaires"
         :email                       "Courriel"
         :last-name                   "Nom"
         :first-name                  "Prénom"
         :age                         "Âge"
         :zip-code                         "Code postal"
         :add-description             "Ajoutez une description"
         :add-idea                    "Ajoutez votre idée"
         :optional                    "(facultatif)"
         :idea-title                  "Titre de l'idée"
         :add-idea-to                 "Ajoutez une idée pour"
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
         :completed-title             "Merci!"
         :completed-message           "Vos choix ont bien été envoyés. Si vous avez ajouté des idées, elles devraient être désormais publiques."}}))

(defn snippet [key]
  (get-in @app-cms [@language key]))

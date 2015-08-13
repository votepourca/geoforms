(ns geoforms.forms
  (:require [reagent.core :as reagent :refer [atom]]
            [reagent-forms.core :refer [bind-fields init-field value-of]]
            [geoforms.db :as db]))

;;; state

(def app-state
  (atom
   {:selected-districts nil
    :added-ideas nil
    :added-user  nil}))

(defn toggle-district! [d]
  (swap! app-state update :selected-districts
         #(if (% d) (disj % d) (conj % d))))

(defn selected-district? [d]
  (if-let [selected (:selected-districts @app-state)]
    (contains? selected d)
    (do (swap! app-state assoc :selected-districts (set @db/districts))
        true)))

(defn district-ideas [d]
  (filter (comp #(some #{d} %) :districts) @db/ideas))

;;; view helpers

(defn row [label input]
  [:div.row
   [:div.col-md-2 [:label label]]
   [:div.col-md-5 input]])

(defn radio [label name value]
  [:span.radio
   [:label
    [:input {:field :radio :name name :value value}]
    label]])

(defn radios-yes-no [label name]
  [:div
   [:p label
    (radio "Yes" name true)
    (radio "No"  name false)]])

(defn input [label type id]
  (row label [:input.form-control {:field type :id id}]))

(defn input-placeholder [label type id]
  [:div.row
   [:div.col
    [:input.form-control {:field type :id id :placeholder label}]]])

;;; COMPONENTS

(defn district-toggle [d]
  ^{:key d}
  [:button.btn.btn-default
   {:active   (selected-district? d)
    :on-click #(toggle-district! d)}
   [(if (selected-district? d) :b :span)
    d]])

(defn select-districts-component []
  [:div.btn-group {:field :multi-select :id :every.position}
   (for [d @db/districts]
     ^{:key d}
     [district-toggle d])])

(defn select-cat-component []
    [:div.form-group
     [:select.form-control {:field :list :id :idea.category}
      [:option
       {:value "" :disabled true :selected true}
       "Pick a category..."]
      (for [c @db/categories]
        [:option {:value c :key c} c])]])

(defn idea-form [district]
  ;; a button to open a modal?
  ;; what about wanting to create an idea for multiple districts?
  )

(defn list-idea-blocks-component []
  (let [districts @db/districts]
    [:div
     (doall
      (for [d     districts
            :when (selected-district? d)
            :let  [ideas (district-ideas d)]]
        [:div {:key d}
         [:p [:strong d " - " (count ideas) " idea(s)"]]
         (for [{:keys [id title]} ideas]
           [:div.checkbox {:key id
                           :on-change #(let [supported? (-> % .-target .-checked)]
                                         (db/set-idea-support! id supported?))}
            [:label [:input {:type :checkbox :id id}] title]])
         [idea-form d]
         [:br]]))]))

(defn add-idea-component []
  [:div
   (input-placeholder "Idea title" :text :idea.idea)
   [select-cat-component]
   [:p
    [:textarea.form-control
     {:rows "4" :placeholder "If necessary, add a description"}]]
   (for [i (range 3)]
     [:div {:key i}
      (input-placeholder "Add a reference URL" :text (symbol (str "idea.url." i)))])
   [:button.btn.btn-default
    {:on-click #(db/create-idea! {:title     "More pizza"
                                  :desc      "Food for me"
                                  :districts ["Brooklyn"]
                                  :category  "Green"
                                  :links     []})}
    "Add your idea"]])

(defn signature-component []
  [:div.well
   (input "first name" :text :person.first-name)
   [:div.row
    [:div.col-md-2]
    [:div.col-md-5
     [:div.alert.alert-danger
      {:field :alert :id :errors.first-name}]]]

   (input "last name" :text :person.last-name)
   [:div.row
    [:div.col-md-2]
    [:div.col-md-5
     [:div.alert.alert-danger
      {:field :alert :id :errors.last-name}]]]

   (input "email" :email :person.email)
   [:div.row
    [:div.col-md-2]
    [:div.col-md-5
     [:div.alert.alert-danger
      {:field :alert :id :errors.email}]]]

   [:div.form-group
    (row "age"
         [:select.form-control {:field :list :id :person.age}
          (for [a db/age-groups]
            [:option {:value a :key a} (name a)])])]

   (radios-yes-no
    "I want to get noticed about my supported ideas progress."
    :person.alert-ideas?)

   (radios-yes-no
    "I want to get noticed about volonteer opportunities regarding ideas I voted for."
    :person.alert-volunteer?)

   (radios-yes-no
    "I want to get noticed about major updates in my supported district(s)."
    :person.alert-districts?)

   (row
    "comments"
    [:textarea.form-control
     {:field :textarea :id :comments}])])

;;; templates

(def form-template
  [:div
   [:h3 "1. Choose your district"]
   [select-districts-component]
   [:hr]

   [:h3 "2. Check ideas you want to support!"]
   [:div.well
    [list-idea-blocks-component]]
   [:hr]
   [:div.well
    [add-idea-component]]
   [:hr]

   [:h3 "3. Sign your choices"]
   (signature-component)])


;;; PAGE

(def email-regex #"^[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\.[a-zA-Z0-9-.]+$")

(defn validate-user
  "Build map of {key error} pairs, where each {error} relates to persion.{key}"
  [doc]
  (let [person (:person doc)]
    (cond-> {}
      (empty? (:first-name person))
      (assoc :first-name "First name is empty")

      (empty? (:last-name person))
      (assoc :last-name "Last name is empty")

      (empty? (:email person))
      (assoc :email "Email is empty")

      (when-let [email (:email person)]
        (nil? (re-find email-regex email)))
      (assoc :email "Email is not valid")

      (nil? (:age person))
      (assoc :age "Age is not set"))))

(defn validate-user!
  "Update errors atom, and return true if there were any errors."
  [doc]
  (let [errors (validate-user @doc)]
    (swap! doc assoc :errors errors)
    (empty? errors)))

(defn submit!
  "Ensure the signing user exists and their support is noted."
  [doc]
  (db/create-user! (:person doc) @db/supported-ideas))

(defn page []
  (let [doc (atom {:person {:first-name       "Blake"
                            :last-name        "Hake"
                            :email            "blake@hake.fake.com"
                            :age              :18-24
                            :alert-ideas?     true
                            :alert-volunteer? true
                            :alert-districts? true}})]
    (fn []
      [:div
       [bind-fields
        form-template
        doc
        (fn [document])
        (fn [document])]

       [:button.btn.btn-default
        {:on-click #(when (validate-user! doc)
                      (submit! @doc))}
        "Submit"]])))

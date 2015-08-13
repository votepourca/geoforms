(ns geoforms.forms
  (:require [reagent.core :as reagent :refer [atom]]
            [reagent-forms.core :refer [bind-fields init-field value-of]]
            [geoforms.db :as db]))

;;; state and logic

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
        (and (seq email)
             (nil? (re-find email-regex email))))
      (assoc :email "Email is not valid")

      (nil? (:age person))
      (assoc :age "Age is not set"))))

(defn validate-idea
  "Build map of {key error} pairs, where each {error} relates to idea.{key}"
  [idea]
  (cond-> {}
    ;; TODO: user has signed

    (empty? (:title idea))
    (assoc :title "Title cannot be blank.")

    (when-let [title (:title idea)]
      (and (seq title)
           (some (comp #{title} :title) @db/ideas)))
    (assoc :title "Title must be unique")

    (empty? (:category idea))
    (assoc :category "Category must be set.")))

(defn validate-user!
  "Update errors atom, and return true if there were any errors."
  [doc]
  (let [errors (validate-user @doc)]
    (swap! doc assoc :errors errors)
    (empty? errors)))

(defn validate-idea!
  "Update errors atom, and return true if there were any errors."
  [doc]
  (let [errors (validate-idea @doc)]
    (prn errors)
    (swap! doc assoc :errors errors)
    (empty? errors)))

(defn submit!
  "Ensure the signing user exists and their support is noted."
  [doc]
  (db/create-user! (:person doc) @db/supported-ideas))

(defn normalize-idea [idea]
  (-> idea
      (update :urls #(into [] (vals (apply sorted-set %))))
      ;; just add to all districts for now
      (assoc :districts (:selected-districts @app-state))))

(defn submit-idea!
  [doc]
  (db/create-idea! doc))

;; forms

(defonce user-doc
  (atom {:person {:alert-ideas?     true
                  :alert-volunteer? true
                  :alert-districts? true}}))

(defonce idea-doc
  (atom {:title     nil
         :desc      nil
         :districts nil
         :category  nil
         :links     nil}))

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

(defn error-field [key]
  [:div.row
   [:div.col-md-2]
   [:div.col-md-5
    [:div.alert.alert-danger
     {:field :alert :id (str ":errors." (name key))}]]])

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
   [:select.form-control
    {:value (get-in @idea-doc [:category])
     :on-change #(swap! idea-doc assoc-in [:category]
                        (-> % .-target .-value))}
    [:option
     {:value "", :disabled true, :selected true}
     "Pick a category..."]
    (for [c @db/categories]
      [:option {:value c :key (symbol c)} c])]])

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
         ;; a button to open a modal?
         ;; what about wanting to create an idea for multiple districts?
         [:a.btn.btn-xs.btn-default {:disabled true} (str "Add idea to " d)]
         [:br]]))]))

(def idea-template
  [:div
   (input "Idea title" :text :title)
   (error-field :title)

   [select-cat-component]
   (error-field :category)

   [:p
    [:textarea.form-control
     {:rows        "4"
      :placeholder "If necessary, add a description"
      :field       :textarea
      :id          :desc}]]

   (for [i (range 3)]
     [:div {:key i}
      (input "URL" :text (str ":urls." i))])])

(defn add-idea-component []
  [:div
   [bind-fields
    idea-template
    idea-doc
    #_ (fn [k v _] (prn k v _))]
   [:button.btn.btn-default
    {:on-click #(when (validate-idea! idea-doc)
                  (-> @idea-doc normalize-idea submit-idea!))}
    "Add your idea"]])

(defn signature-component []
  [:div.well
   (input "first name" :text :person.first-name)
   (error-field :first-name)

   (input "last name" :text :person.last-name)
   (error-field :last-name)

   (input "email" :email :person.email)
   (error-field :email)

   [:div.form-group
    (row "age"
         [:select.form-control {:field :list :id :person.age}
          (cons
           [:option {:value "", :disabled true, :selected true, :key "disabled"}
            "Pick an age..."]
           (for [a db/age-groups]
             [:option {:value a :key a} (name a)]))])]
   (error-field :age)

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

(defn page []
  (fn []
    [:div
     (when (and (seq @db/districts)
                (seq @db/categories)
                (seq @db/ideas))
       [:div
        [bind-fields
         form-template
         user-doc
         (fn [k v _]
           (let [after (assoc-in @user-doc k v)
                 errors (validate-user after)]
             (assoc after :errors errors)))]

        [:button.btn.btn-default
         {:on-click #(when (validate-user! user-doc) (submit! @user-doc))}
         "Submit"]])]))

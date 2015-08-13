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
  [:div.radio
   [:label
    [:input {:field :radio :name name :value value}]
    label]])

(defn radios-yes-no [label name]
  [:div
   [:p label]
   (radio "Yes" name true)
   (radio "No"  name false)])

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
       {:value "" :disabled true}
       "Pick a category..."]]])

(defn list-idea-blocks-component []
  (let [districts @db/districts]
    [:div
     (doall
      (for [d districts
            :when (selected-district? d)
            :let [ideas (district-ideas d)]]
        ^{:key d}
        [:div
         [:p [:strong d " - " (count ideas) " idea(s)"]]
         (for [{:keys [id idea]} ideas]
           ^{:key id}
           [:div.checkbox
            [:label
             [:input {:type :checkbox :id id}]
             idea]])
         ;; should we put just name instead of id?
         (input-placeholder (str "add an idea for " d) :text :idea.idea)
         [:br]]))]))

(defn add-idea-component []
  [:div
   ;; should we put just name instead of id ?
   (input-placeholder "Add your idea" :text :idea.idea)
   [select-cat-component]
   [:p
    [:textarea.form-control
     {:rows "4" :placeholder "If necessary, add a description"}]]
   (input-placeholder "Add a reference URL" :text :idea.url.1)
   (input-placeholder "Add a reference URL" :text :idea.url.2)
   (input-placeholder "Add a reference URL" :text :idea.url.3)
   [:button.btn.btn-default
    #_{:on-click #(handle-add-idea)}
    "Add"]])

(defn signature-component []
  nil)


;;; templates

;; could just generate from the break points
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

(def form-template
  [:div
   [:h3 "1. Choose your district"]
   [select-districts-component]

   [:h3 "2. Check ideas you want to support!"]
   [list-idea-blocks-component]

   [:hr]
   [:p [:em "When 'add an idea' input field is clicked:"]]
   [add-idea-component]

   [:h3 "3. Sign your choices"]
   #_[signature-component]

   [:div
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
      [:div.alert.alert-success
       {:field :alert :id :person.last-name :event empty?}
       "last name is empty!"]]]

    [:div.form-group
     [:label "age"]
     [:select.form-control {:field :list :id :person.age}
      (for [a age-groups]
        [:option {:value a :key a} (name a)])]]

    (input "email" :email :person.email)
    [:div.row
     [:div.col-md-2]
     [:div.col-md-5
      [:div.alert.alert-danger
       {:field :alert :id :errors.email :event empty?}
       "email is empty!"]]]

    (radios-yes-no
     "I want to get noticed about my supported ideas progress."
     :subscribe-idea-alerts)

    (radios-yes-no
     "I want to get noticed about volonteer opportunities regarding ideas I voted for."
     :subscribe-volonteer-idea-alerts)

    (radios-yes-no
     "I want to get noticed about major updates in my supported district(s)."
     :subscribe-district-alerts)

    (row
     "comments"
     [:textarea.form-control
      {:field :textarea :id :comments}])]])


;;; PAGE

(defn validate! [doc]
  (if (empty? (get-in @doc [:person :first-name]))
    (swap! doc assoc-in [:errors :first-name] "first name is empty")))

(defn submit! [doc]
  )

(defn page []
  (let [doc (atom {})]
    (fn []
      [:div
       [:div.page-header [:h1 "Sample Form"]]

       [bind-fields
        form-template
        doc
        (fn [document])
        (fn [document])]

       [:button.btn.btn-default
        {:on-click #(and (validate! doc)
                         (submit! doc))}
        "Submit"]])))

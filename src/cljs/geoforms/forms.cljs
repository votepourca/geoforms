(ns geoforms.forms
  (:require [reagent.core :as reagent :refer [atom]]
            [reagent-forms.core :refer [bind-fields init-field value-of]]
            [geoforms.db :as db :refer [snippet]]))

;;; state and logic

(defonce app-state
  (atom
   {:selected-districts '()
    :added-ideas nil
    :added-user  nil}))

(defn toggle-district! [d]
  (swap! app-state update :selected-districts
         #(let [match? (fn [x] (= d x))
                on?    (some match? %)]
            (when (and on? (match? @db/selected-district))
              (reset! db/selected-district nil))
            (if on? (remove match? %) (conj % d)))))

(defn selected-districts []
  (:selected-districts @app-state))

(defn selected-district? [d]
  (some #(= d %) (selected-districts)))

(def email-regex #"^[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\.[a-zA-Z0-9-.]+$")

(defn validate-user
  "Build map of {key error} pairs, where each {error} relates to persion.{key}"
  [doc]
  (let [person (:person doc)]
    (cond-> {}
      (empty? (:first-name person))
      (assoc :first-name (str (snippet :first-name) " " (snippet :required)))

      (empty? (:last-name person))
      (assoc :last-name (str (snippet :last-name) " " (snippet :required)))

      (empty? (:email person))
      (assoc :email (str (snippet :email) " " (snippet :required)))

      (when-let [email (:email person)]
        (and (seq email)
             (nil? (re-find email-regex email))))
      (assoc :email (str (snippet :email) " " (snippet :not-valid)))

      (nil? (:age person))
      (assoc :age (str (snippet :age) " " (snippet :not-set))))))

(defn validate-idea
  "Build map of {key error} pairs, where each {error} relates to idea.{key}"
  [idea]
  (cond-> {}
    ;; TODO: user has signed

    (empty? (:title idea))
    (assoc :title (str (snippet :title) " " (snippet :required)))

    (when-let [title (:title idea)]
      (and (seq title)
           (some (comp #{title} :title) (@db/district-ideas @db/selected-district))))
    (assoc :title (str (snippet :title) " " (snippet :already-used)))

    (empty? (:category idea))
    (assoc :category (str (snippet :category) " " (snippet :not-set)))))

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
    (swap! doc assoc :errors errors)
    (empty? errors)))

(defn submit!
  "Ensure the signing user exists and their support is noted."
  [doc]
  (db/create-user! (:person doc) @db/supported-ideas))

(defn normalize-idea [idea]
  (-> idea
      (update :urls #(into [] (vals (apply sorted-set %))))
      (assoc :districts [@db/selected-district])))

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
    (radio (snippet :yes) name true)
    (radio (snippet :no) name false)]])

(defn input [label type id]
  (row label [:input.form-control {:field type :id id}]))

(defn input-placeholder [label type id]
  [:div.form-group
   [:input.form-control {:field type :id id :placeholder label}]])

(defn error-field [key]
  [:div.row
   [:div.col-md-2]
   [:div.col-md-5
    [:div.alert.alert-danger
     {:field :alert :id (str ":errors." (name key))}]]])

;;; COMPONENTS

(defn district-toggle [d]
  ^{:key d}
  [:button.btn.btn-sm.btn-default
   {:class   (if (selected-district? d) :active)
    :on-click #(toggle-district! d)}
   [:span d  (str d " (" (@db/district-counts d) ") ")]])

(defn select-districts-component []
  [:div.btn-group {:field :multi-select :id :every.position}
   (for [d @db/districts-by-count]
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
     (snippet :select-category)]
    (for [c @db/categories]
      [:option {:value c :key (symbol c)} c])]])

(defn district-ideas-component [d ideas]
  [:div {:key d}
   [:p [:strong d]]
   (for [{:keys [id title]} ideas]
     [:div.checkbox {:key id
                     :on-change #(let [supported? (-> % .-target .-checked)]
                                   (db/set-idea-support! id supported?))}
      [:label [:input {:type :checkbox :id id}] title]])
   (when (not= d @db/selected-district)
     [:a.btn.btn-xs.btn-success
      {:on-click #(reset! db/selected-district d)}
      (str (snippet :add-idea-to) " " d)])])

(defn list-idea-blocks-component []
  (let [districts @db/districts]
    (into
     [:div]
     (interpose
      [:br]
      (map second
           (for [d     (selected-districts)
                 :let  [ideas (@db/district-ideas d)]]
             [(- (count ideas)) [district-ideas-component d ideas]]))))))

(defn idea-template []
  [:div
   (input-placeholder (snippet :idea-title) :text :title)
   (error-field :title)

   [select-cat-component]
   (error-field :category)

   [:p
    [:textarea.form-control
     {:rows        "4"
      :placeholder (str (snippet :add-description) " " (snippet :optional))
      :field       :textarea
      :id          :desc}]]

   (for [i (range 3)]
     [:div {:key i}
      (input-placeholder (str (snippet :add-url) " " (snippet :optional))
                         :text (str ":urls." i))])])

(defn add-idea-component []
  [:div
   [:div.form-group
    [:input.form-control {:disabled true, :value @db/selected-district}]]
   [bind-fields
    (idea-template)
    idea-doc
    #_ (fn [k v _] (prn k v _))]
   [:button.btn.btn-default
    {:on-click #(when (validate-idea! idea-doc)
                  (-> @idea-doc normalize-idea submit-idea!))}
    (snippet :add-idea)]])

(defn signature-component []
  [:div.well
   (input (snippet :first-name) :text :person.first-name)
   (error-field :first-name)

   (input (snippet :last-name) :text :person.last-name)
   (error-field :last-name)

   (input (snippet :email) :email :person.email)
   (error-field :email)

   [:div.form-group
    (row (snippet :age)
         [:select.form-control {:field :list :id :person.age}
          (cons
           [:option {:value "", :disabled true, :selected true, :key "disabled"}
            (snippet :select-age)]
           (for [a db/age-groups]
             [:option {:value a :key a} (name a)]))])]
   (error-field :age)

   (radios-yes-no
    (snippet :person.alert-ideas?)
    :person.alert-ideas?)

   (radios-yes-no
    (snippet :person.alert-volunteer?)
    :person.alert-volunteer?)

   (radios-yes-no
    (snippet :person.alert-districts?)
    :person.alert-districts?)

   (row
    (snippet :comments)
    [:textarea.form-control
     {:field :textarea :id :comments}])])

;;; templates

(defn step-1 []
  [:div
   [:h3 (snippet :h-district)]
   [select-districts-component]
   [:hr]])

(defn step-2 []
  [:div
   [:h3 (snippet :h-vote)]
   [:div.well
    [list-idea-blocks-component]]
   [:hr]])

(defn step-3 []
  [:div
   [:h3 (snippet :h-add)]
   [:div.well
    [add-idea-component]]
   [:hr]])

(defn step-4 []
  [:div
   [:h3 (snippet :h-sign)]
   (signature-component)])

(defn completed-view []
  [:div
   [:h3 (snippet :completed-title)]
   [:p (snippet :completed-message)]])

;;; PAGE

(defn page []
  [:div
   (when (and (seq @db/districts)
              (seq @db/categories)
              (seq @db/ideas))
     [:div
      [step-1]
      (when (seq (selected-districts))
        [:div
         [step-2]
         (when @db/selected-district
           [step-3])
         [bind-fields
          (step-4)
          user-doc
          #_ (fn [k v _]
               (let [after (assoc-in @user-doc k v)
                     errors (validate-user after)]
                 (assoc after :errors errors)))]

         [:button.btn.btn-primary
          {:on-click #(when (validate-user! user-doc) (submit! @user-doc))}
          (snippet :submit)]])])])

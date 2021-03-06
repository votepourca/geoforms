(ns geoforms.forms
  (:require [reagent.core :as reagent :refer [atom]]
            [reagent-forms.core :refer [bind-fields init-field value-of]]
            [geoforms.db :as db :refer [snippet]]
            [geoforms.firebase :as fb]
            [geoforms.utils :refer [dequeue-in! valid-email? valid-zip-code?]]))

;;; state and logic

(defonce app-state
  (atom
   {:selected-districts '()
    :added-ideas        nil
    :added-user         nil
    :completed?         false
    :pending-ideas      []
    :show-idea-confirm? false}))

(def user-defaults
  {:person {:alert-ideas?     true
            :alert-volunteer? true
            :alert-districts? true}})

(def idea-defaults
  {:title     nil
   :desc      nil
   :districts nil
   :category  nil
   :links     nil})

(defonce user-form
  (atom user-defaults))

(defonce idea-form
  (atom idea-defaults))

(defn completed? []
  (:completed? @app-state))

(defn complete! []
  (reset! user-form user-defaults)
  (reset! idea-form idea-defaults)
  (reset! db/supported-ideas #{})
  (swap! app-state assoc
         :completed? true
         :selected-districts '()))

(defn restart! []
  (swap! app-state assoc :completed? false))

(defn toggle-district! [d]
  (restart!)
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

(defn submit-idea!
  [doc]
  (fb/create-idea! doc))

(defn queue-idea! [idea]
  (swap! app-state update :pending-ideas #(conj % idea)))

(defn confirm-idea-msg! [_]
  (reset! db/selected-district nil)
  (swap! app-state assoc :show-confirm-idea? true)
  (js/setTimeout
   #(swap! app-state assoc :show-confirm-idea? false)
   5000))

(defn submit-ideas! [user-email]
  (loop []
    (when-let [idea (dequeue-in! app-state [:pending-ideas])]
      (let [idea-id (submit-idea! idea)]
        (fb/set-user-idea user-email idea-id true))
      (recur))))

(defn district-ideas [district]
  (let [saved   (@db/district-ideas district)
        pending (map-indexed
                 (fn [i idea] (assoc idea :pending? true :id (str "pending-" i)))
                 (filter (fn [{:keys [districts]}]
                           (some #(= district %) districts))
                         (:pending-ideas @app-state)))]
    (if (seq pending)
      (vec (concat saved pending))
      saved)))

(defn title-unique? [title district]
  (some (comp #{title} :title) (district-ideas district)))

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
        (and (seq email) (valid-email? email)))
      (assoc :email (str (snippet :email) " " (snippet :not-valid)))

      (empty? (:zip-code person))
      (assoc :zip-code (str (snippet :zip-code) " " (snippet :required)))

      (when-let [zip-code (:zip-code person)]
        (and (seq zip-code) (valid-zip-code? zip-code)))
      (assoc :zip-code (str (snippet :zip-code) " " (snippet :not-valid)))

      (nil? (:age person))
      (assoc :age (str (snippet :age) " " (snippet :not-set))))))

(defn validate-idea
  "Build map of {key error} pairs, where each {error} relates to idea.{key}"
  [idea]
  (cond-> {}
    (empty? (:title idea))
    (assoc :title (str (snippet :title) " " (snippet :required)))

    (when-let [title (:title idea)]
      (and (seq title) (title-unique? title @db/selected-district)))
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
  (fb/create-user-ideas! (:person doc) @db/supported-ideas)
  (submit-ideas! (get-in doc [:person :email]))
  (complete!))

(defn normalize-idea [idea]
  (-> idea
      (update :urls #(into [] (vals (apply sorted-set %))))
      (assoc :districts [@db/selected-district])))

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
   [:span (str d " (" (@db/district-counts d) ") ")]])

(defn select-districts-component []
  [:div.btn-group {:field :multi-select :id :every.position}
   (for [d @db/districts-by-count]
     ^{:key d}
     [district-toggle d])])

(defn select-cat-component []
  [:div.form-group
   [:select.form-control
    {:value (get-in @idea-form [:category])
     :on-change #(swap! idea-form assoc-in [:category]
                        (-> % .-target .-value))}
    [:option
     {:value "", :disabled true, :selected true}
     (snippet :select-category)]
    (for [c @db/categories]
      [:option {:value c :key (symbol c)} c])]])

(defn district-ideas-component [d ideas]
  [:div {:key d}
   [:p [:strong d]]
   (for [{:keys [id title pending?]} ideas]
     [:div.checkbox {:key id
                     :on-change #(let [supported? (-> % .-target .-checked)]
                                   (db/set-idea-support! id supported?))}
      [:label [:input {:type :checkbox :id id :checked pending?}]
       [:span {:style {:color (if pending? :green)}} title]]])
   (when (not= d @db/selected-district)
     [:a.btn.btn-sm.btn-success
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
                 :let  [ideas (district-ideas d)]]
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
    idea-form]
   [:button.btn.btn-primary
    {:on-click #(when (validate-idea! idea-form)
                  (-> @idea-form normalize-idea queue-idea! confirm-idea-msg!))}
    (snippet :add-idea)]])

(defn signature-component []
  [:div.well
   (input (snippet :first-name) :text :person.first-name)
   (error-field :first-name)

   (input (snippet :last-name) :text :person.last-name)
   (error-field :last-name)

   (input (snippet :email) :email :person.email)
   (error-field :email)

   (input (snippet :zip-code) :text :person.zip-code)
   (error-field :zip-code)

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
   [:p (snippet :h-vote-p)]
   [:div.well
    [list-idea-blocks-component]]
   [:hr]])

(defn step-3 []
  [:div
   [:h3 (snippet :h-add)]
   [:div.well
    [add-idea-component]
    [:alert]]
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
      (if (completed?)
        [completed-view]
        (when (seq (selected-districts))
          [:div
           [step-2]
           (when @db/selected-district
             [step-3])
           (when (:show-confirm-idea? @app-state)
             [:div.well
              [:div.row
               [:div.col-md-1]
               [:div.col-md-10
                [:div.alert.alert-success
                 {:field :alert, :style {:margin-bottom 0}}
                 [:div (snippet :new-ideas-saved-once-signed)]]]]])
           [bind-fields
            (step-4)
            user-form
            #_ (fn [k v _]
                 (let [after (assoc-in @user-form k v)
                       errors (validate-user after)]
                   (assoc after :errors errors)))]

           [:button.btn.btn-primary.btn-lg
            {:on-click #(when (validate-user! user-form) (submit! @user-form))}
            (snippet :submit)]]))])])

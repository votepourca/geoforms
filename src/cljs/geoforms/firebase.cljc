(ns geoforms.firebase
  (:refer-clojure :exclude [ref])
  (:require [clojure.string :as str]
            [matchbox.core :as m]
            [matchbox.atom :as ma]
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

(defn supported-ideas-path [id]
  [:users id :supports])

(defn idea-supporters-path [id]
  [:ideas id :supporters])


;; mutators

(defn get-user-key [email cb]
  (m/deref-in
   ref [:users-ids (munge- email)]
   (fn [id]
     (cb (or id (let [k (m/key (.push (m/get-in ref :users)))]
                  (m/reset-in! ref [:users-ids (munge- email)] k)
                  (m/reset-in! ref [:users k] {:created-at m/SERVER_TIMESTAMP})
                  k))))))

(defn- set-user->idea
  "Update reference from user to idea"
  [user-email idea-id add?]
  ;; lean on setting value to nil to delete keys from map
  (get-user-key user-email
                #(let [path (conj (supported-ideas-path %) idea-id)]
                  (m/reset-in! ref path add?))))

(defn- set-user->ideas
  "Update user to (only) reference all idea-ids"
  [user-email idea-ids]
  ;; upsert all associations (implicit deletes)
  (get-user-key user-email
                #(let [path (conj (supported-ideas-path %))]
                  (m/reset-in! ref path (->set-map idea-ids)))))

(defn- set-idea->user
  "Update reference from idea to user"
  [idea-id user-email add?]
  ;; lean on setting value to nil to delete keys from map
  (get-user-key user-email
                #(let [path (conj (idea-supporters-path idea-id) %)]
                  (m/reset-in! ref path add?))))

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

(defn key- [x]
  (when x (m/key x)))

(defn create-idea!
  ([idea]    (key- (m/conj-in! ref [:ideas] idea)))
  ([idea cb] (key- (m/conj-in! ref [:ideas] idea cb))))

(defn create-user!
  ([{:keys [email] :as user}]   (get-user-key email #(m/reset-in! ref [:users %] user)))
  ([{:keys [email] :as user} cb] (get-user-key email #(m/reset-in! ref [:users %] user cb))))

(defn create-user-ideas!
  [{:keys [email] :as user}
   ideas]
  (get-user-key
   email
   #(let [path [:users %]]
      (m/deref-in
       ref (conj path :supports)
       (fn [existing-ideas]
         (let [ideas (into ideas (keys existing-ideas))]
           ;; upsert the user
           (m/reset-in! ref path user)
           ;; upsert the supported ideas
           (set-user-ideas email ideas)))))))


;; auth

(defn init-session []
  ;; TODO: to be more robust, should really handle failures here,
  ;; and defer rest of app loading
  (m/auth-anon ref (fn [_])))

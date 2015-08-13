(ns geoforms.core
    (:require [reagent.core :as reagent :refer [atom]]
              [reagent.session :as session]
              [secretary.core :as secretary :include-macros true]
              [goog.events :as events]
              [goog.history.EventType :as EventType]
              [geoforms.forms]
              [geoforms.db :refer [snippet]])
    (:import goog.History))

;; -------------------------
;; Views

(defn home-page []
  [:div [:h2 (snippet :page-title)]
   [geoforms.forms/page]])

(defn about-page []
  [:div [:h2 "About geoforms"]
   [:div [:a {:href "#/"} "go to the home page"]]])

(defn reagent-forms-example []
  [:div [:h2 "Welcome to geoforms"]
   [:div [:a {:href "#/about"} "go to about page"]]
   [:div [:a {:href "#/"} "go to home"]]])

(defn current-page []
  [:div [(session/get :current-page)]])

;; -------------------------
;; Routes
(secretary/set-config! :prefix "#")

(secretary/defroute "/" []
  (session/put! :current-page #'home-page))

(secretary/defroute "/about" []
  (session/put! :current-page #'about-page))

(secretary/defroute "/reagent-forms-example" []
  (session/put! :current-page #'reagent-forms-example))

;; -------------------------
;; History
;; must be called after routes have been defined
(defn hook-browser-navigation! []
  (doto (History.)
    (events/listen
     EventType/NAVIGATE
     (fn [event]
       (secretary/dispatch! (.-token event))))
    (.setEnabled true)))

;; -------------------------
;; Initialize app
(defn mount-root []
  (reagent/render [current-page] (.getElementById js/document "app")))

(defn init! []
  (hook-browser-navigation!)
  (mount-root))

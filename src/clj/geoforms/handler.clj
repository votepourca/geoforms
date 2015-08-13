(ns geoforms.handler
  (:require [compojure.core :refer [GET defroutes]]
            [compojure.route :refer [not-found resources]]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [hiccup.core :refer [html]]
            [hiccup.page :refer [include-js include-css]]
            [prone.middleware :refer [wrap-exceptions]]
            [ring.middleware.reload :refer [wrap-reload]]
            [environ.core :refer [env]]))

(def home-page
  (html
   [:html
    [:head
     [:meta {:charset "utf-8"}]
     [:meta {:name "viewport"
             :content "width=device-width, initial-scale=1"}]
     (include-css "//maxcdn.bootstrapcdn.com/bootstrap/3.2.0/css/bootstrap.min.css")
     (include-css "//maxcdn.bootstrapcdn.com/bootstrap/3.2.0/css/bootstrap-theme.min.css")
     (include-css (if (env :dev) "css/site.css" "css/site.min.css"))
     (include-css "https://www.votepour.ca/assets/stylesheets/geoforms.css")]
    [:body {:style "height: 100%"}
     [:div#app
      [:p "Votepour.ca / Geoforms"]
      [:p "Chargement... / Loading..."]
      (if (env :dev)
        [:p "please run "
         [:b "lein figwheel"]
         " in order to start the compiler"]
        [:p "Problèmes de chargement ? SVP contactez info@votepour.ca / Not loading? Please contact info@votepour.ca"])]
     (include-js "https://cdnjs.cloudflare.com/ajax/libs/es5-shim/4.1.10/es5-shim.min.js")
     (include-js "https://cdnjs.cloudflare.com/ajax/libs/es5-shim/4.1.10/es5-sham.min.js")
     (include-js "js/app.js")
     (include-js "https://www.votepour.ca/assets/javascripts/geoforms.js")]]))

(defroutes routes
  (GET "/" [] home-page)
  (resources "/")
  (not-found "Not Found"))

(def app
  (let [handler (wrap-defaults #'routes site-defaults)]
    (if (env :dev) (-> handler wrap-exceptions wrap-reload) handler)))

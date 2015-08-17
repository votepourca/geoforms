(ns geoforms.config)

(#?(:cljs goog-define, :clj def)
   fb-base "https://matchbox-forms.firebaseio.com/")

(#?(:cljs goog-define, :clj def)
   fb-path "development")

(def fb-uri (str fb-base fb-path))

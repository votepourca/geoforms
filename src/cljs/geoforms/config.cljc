(ns geoforms.config)

#?(:cljs (enable-console-print!))

(#?(:cljs goog-define, :clj def)
   fb-base "https://matchbox-forms.firebaseio.com/")

(#?(:cljs goog-define, :clj def)
   fb-path "development")

(prn fb-base fb-path)

(def fb-uri (str fb-base fb-path))

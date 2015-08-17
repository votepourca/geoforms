(ns geoforms.config
  #?(:cljs (:require-macros [geoforms.config :refer [env-define]])))

#?(:clj
    (defmacro env-define
      [sym env-var default]
      `(def ~sym (or ~(System/getenv env-var) ~default))))

(env-define
   fb-base
  "FB_BASE"
  "https://matchbox-forms.firebaseio.com/")

(#?(:cljs goog-define, :clj def)
   fb-path "development")

(prn fb-base fb-path)

(def fb-uri (str fb-base fb-path))

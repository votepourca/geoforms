(ns geoforms.utils)

(defn dequeue!
  "Pop queue inside an atom, returning the the associated peek."
  [queue]
  (loop []
    (let [q     @queue
          value (peek q)
          nq    (pop q)]
      (if (compare-and-set! queue q nq)
        value
        (recur)))))

(defn dequeue-in!
  "Pop queue inside path within an atom, returning the the associated peek."
  [atom path]
  (loop []
    (let [m @atom
          q (get-in m path)]
      (when (seq q)
        (let [value (peek q)
              nq    (pop q)]
          (if (or (nil? value)
                  (compare-and-set! atom m (assoc-in m path nq)))
            value
            (recur)))))))


;;; regex

(def email-regex #"^[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\.[a-zA-Z0-9-.]+$")

(def canadian-postal-code-regex
  ;; Regex that matches Canadian Postal-Code formats with or without space
  ;; (e.g., T2X 1V4 or T2X1V4).
  #"^[ABCEGHJKLMNPRSTVXY]{1}\d{1}[A-Z]{1} *\d{1}[A-Z]{1}\d{1}$")

(def us-postal-code-regex
  ;; Regex that matches all US format zip code formats
  ;; (e.g., 94105-0011 or 94105)
  #"^\d{5}(-\d{4})?$")

(def us-and-canadian-postal-code-regex
  #"(^\d{5}(-\d{4})?$)|(^[ABCEGHJKLMNPRSTVXY]{1}\d{1}[A-Z]{1} *\d{1}[A-Z]{1}\d{1}$)")


;;; validation

(defn valid-email? [email]
  (when (and email (pos? (.-length email)))
    (nil? (re-find email-regex email))))

(defn valid-zip-code? [zip]
  (when (and zip (pos? (.-length zip)))
    (nil? (re-find canadian-postal-code-regex zip))))

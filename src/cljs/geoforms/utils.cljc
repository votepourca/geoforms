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

(def email-regex #"^[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\.[a-zA-Z0-9-.]+$")

(defn valid-email? [email]
  (when (and email (pos? (.-length email)))
    (nil? (re-find email-regex email))))

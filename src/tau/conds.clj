(ns tau.conds)

(defn always-true? [term]
  (if (boolean? term)
    term
    (let [[op & args] term]
      (case (str op)
        "and" (->> args
                   (map always-true?)
                   (every? identity))
        "or" (->> args
                  (map always-true?)
                  (some identity) some?)
        false))))

(defn subset-term [a b]
  (seq ['subset a b]))

(defn apply-op [op terms]
  (if (= (count terms) 1)
    (first terms)
    (concat [op] terms)))
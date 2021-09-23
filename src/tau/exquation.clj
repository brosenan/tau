(ns tau.exquation
  (:require [clojure.walk :as walk]))

(def binding-sym '%)
(def elipsis-sym '...)

(defn binding? [expr]
  (and (seq? expr)
       (= (first expr) binding-sym)))

(defn bound-var [[_% v & _terms]]
  v)

(defn new-binding [terms new-name]
  (concat [binding-sym (keyword (new-name))] terms))

(defn binding-terms [b]
  (let [[_% var & terms] b]
    (->> terms
         (map #(walk/postwalk-replace {var b} %)))))

(defn subset?
  ([a b]
   (subset? a b {}))
  ([a b assumptions]
   (cond
     (and (seq? a)
          (= (count a) 2)
          (= (second a) elipsis-sym)) (subset? (first a) b assumptions)
     (and (seq? b)
          (= (count b) 2)
          (= (second b) elipsis-sym)) (subset? a (first b) assumptions)
     (binding? a) (cond
                    (and (binding? b)
                         (= (bound-var a) (bound-var b))) true
                    (contains? assumptions (bound-var a)) (subset? (assumptions (bound-var a)) b assumptions)
                    :else (->> (binding-terms a)
                               (map #(subset? % b (assoc assumptions (bound-var a) b)))
                               (every? identity)))
     (binding? b) (->> (binding-terms b)
                       (map #(subset? a % assumptions))
                       (some identity)
                       some?)
     (seq? a) (and (seq? b)
                   (cond
                     (empty? a) (empty? b)
                     :else (and (subset? (first a) (first b) assumptions)
                                (subset? (rest a) (rest b) assumptions))))
     (vector? a) (and (vector? b)
                      (subset? (seq a) (seq b) assumptions))
     :else (= a b))))

(comment (subset? '(% :n (0) (s :n ...)) '(% :k (s :k ...) (0)) {})
         (subset? '(s (% :n (0) (s :n ...)) ...) '(% :k (s :k ...) (0)) {:n '(% :k (s :k ...) (0))})
         (subset? '(s (% :n (0) (s :n ...)) ...) '(s (% :k (s :k ...) (0)) ...) {:n '(% :k (s :k ...) (0))})
         (subset? '(% :n (0) (s :n ...)) '((% :k (s :k ...) (0)) ...) {:n '(% :k (s :k ...) (0))})
         (subset? '(% :k (s :k ...) (0)) '((% :k (s :k ...) (0)) ...) {:n '(% :k (s :k ...) (0))})
         (subset? '(% :k (s :k ...) (0)) '((% :k (s :k ...) (0)) ...) {:n '(% :k (s :k ...) (0))}))
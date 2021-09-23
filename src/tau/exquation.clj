(ns tau.exquation
  (:require [clojure.walk :as walk]))

(def binding-sym '%)

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
                   (every? identity (map #(subset? %1 %2 assumptions) a b)))
     (vector? a) (and (vector? b)
                      (every? identity (map #(subset? %1 %2 assumptions) a b)))
     :else (= a b))))
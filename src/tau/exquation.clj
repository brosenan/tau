(ns tau.exquation
  (:require [clojure.walk :as walk]))

(def binding-sym '%)

(defn binding? [expr]
  (and (seq? expr)
       (= (first expr) binding-sym)))

(defn new-binding [terms new-name]
  (concat [binding-sym (keyword (new-name))] terms))

(defn binding-terms [b]
  (let [[_% var & terms] b]
    (->> terms
         (map #(walk/postwalk-replace {var b} %)))))

(defn subset? [a b]
  (= a b))
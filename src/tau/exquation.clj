(ns tau.exquation
  (:require [clojure.walk :as walk]))

(def binding-sym '%)
(def elipsis-sym '...)
(def int-sym 'int)
(def float-sym 'float)
(def string-sym 'string)

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
     (and (= b int-sym)
          (int? a)) true
     (and (= b float-sym)
          (float? a)) true
     (and (= b string-sym)
          (string? a)) true
     (and (sequential? a)
          (= (count a) 2)
          (= (second a) elipsis-sym)) (recur (first a) b assumptions)
     (and (sequential? b)
          (= (count b) 2)
          (= (second b) elipsis-sym)) (recur a (first b) assumptions)
     (binding? a) (cond
                    (and (binding? b)
                         (= (bound-var a) (bound-var b))) true
                    (contains? assumptions (bound-var a)) (recur (assumptions (bound-var a)) b assumptions)
                    :else (let [assumptions (assoc assumptions (bound-var a) b)]
                            (->> (binding-terms a)
                                 (map #(subset? % b assumptions))
                                 (every? identity))))
     (binding? b) (->> (binding-terms b)
                       (map #(subset? a % assumptions))
                       (some identity)
                       some?)
     (seq? a) (and (seq? b)
                   (cond
                     (empty? a) (empty? b)
                     (empty? b) false
                     :else (and (subset? (first a) (first b) assumptions)
                                (recur (rest a) (rest b) assumptions))))
     (vector? a) (and (vector? b)
                      (cond
                        (empty? a) (empty? b)
                        (empty? b) false
                        :else (and (subset? (first a) (first b) assumptions)
                                   (recur (vec (rest a)) (vec (rest b)) assumptions))))
     :else (= a b))))

(defn wrap-binding [gen-var maps]
  (cond
    (empty? maps) nil
    (> (count maps) 1) (let [keys (keys (first maps))]
                         (->> keys
                              (map (fn [key]
                                     [key (-> (map key maps)
                                              (new-binding gen-var))]))
                              (into {})))
    :else (first maps)))

(defn match
  ([pattern x gen-var]
   (match pattern x gen-var {}))
  ([pattern x gen-var bindings]
   (cond
     (nil? bindings) nil
     (keyword? pattern) (assoc bindings pattern x)
     (and (sequential? pattern)
          (= (count pattern) 2)
          (= (second pattern) elipsis-sym)) (recur (first pattern) x gen-var bindings)
     (and (sequential? x)
          (= (count x) 2)
          (= (second x) elipsis-sym)) (recur pattern (first x) gen-var bindings)
     (binding? x) (->> (binding-terms x)
                       (map #(match pattern % gen-var bindings))
                       (filter #(not (nil? %)))
                       (wrap-binding gen-var))
     (seq? pattern) (cond
                      (not (seq? x)) nil
                      (empty? pattern) (if (empty? x)
                                         bindings
                                         nil)
                      (empty? x) nil
                      :else (->> bindings
                                 (match (first pattern) (first x) gen-var)
                                 (recur (rest pattern) (rest x) gen-var)))
     (and (vector? pattern)
          (vector? x)) (recur (seq pattern) (seq x) gen-var bindings)
     (= pattern x) bindings
     :else nil)))
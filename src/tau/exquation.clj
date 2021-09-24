(ns tau.exquation
  (:require [clojure.walk :as walk]))

(def binding-sym '%)
(def ellipsis-sym '...)
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

(defn ellipsis? [s]
  (and (sequential? s)
       (= (count s) 2)
       (= (second s) ellipsis-sym)))

(declare subset-conds)

(defn- unify-all [as b assumptions conds]
  (if (empty? as)
    conds
    (->> conds
         (subset-conds (first as) b assumptions)
         (unify-all (rest as) b assumptions))))

(defn subset-conds
  ([a b]
   (subset-conds a b {} []))
  ([a b assumptions conds]
   (cond
     (nil? conds) nil
     (and (= b int-sym)
          (int? a)) conds
     (and (= b float-sym)
          (float? a)) conds
     (and (= b string-sym)
          (string? a)) conds
     (ellipsis? a) (recur (first a) b assumptions conds)
     (ellipsis? b) (recur a (first b) assumptions conds)
     (or (keyword? a)
         (keyword? b)) (-> conds
                           (conj [a b]))
     (binding? a) (cond
                    (and (binding? b)
                         (= (bound-var a) (bound-var b))) conds
                    (contains? assumptions (bound-var a)) (recur (assumptions (bound-var a)) b assumptions conds)
                    :else (let [assumptions (assoc assumptions (bound-var a) b)]
                            (unify-all (binding-terms a) b assumptions conds)))
     (binding? b) (->> (binding-terms b)
                       (map #(subset-conds a % assumptions conds))
                       (some identity))  ;; Need to combine the different conditions
     (seq? a) (cond
                (not (seq? b)) nil
                (empty? a) (if (empty? b)
                             conds
                             nil)
                (empty? b) nil
                :else (->> conds
                           (subset-conds (first a) (first b) assumptions)
                           (recur (rest a) (rest b) assumptions)))
     (vector? a) (cond
                   (not (vector? b)) nil
                   (empty? a) (if (empty? b)
                                conds
                                nil)
                   (empty? b) nil
                   :else (and (subset-conds (first a) (first b) assumptions conds)
                              (recur (vec (rest a)) (vec (rest b)) assumptions conds)))
     :else (if (= a b)
             conds
             nil))))

(defn subset? [a b]
  (let [conds (subset-conds a b)]
    (and (not (nil? conds))
         (empty? conds ))))

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
     (ellipsis? pattern) (recur (first pattern) x gen-var bindings)
     (ellipsis? x) (recur pattern (first x) gen-var bindings)
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

(defn pattern-replace [pattern bindmap]
  (cond
    (ellipsis? pattern) (recur (first pattern) bindmap)
    (seq? pattern) (if (empty? pattern)
                     pattern
                     (cons (pattern-replace (first pattern) bindmap)
                           (pattern-replace (rest pattern) bindmap)))
    (vector? pattern) (vec (pattern-replace (seq pattern) bindmap))
    (contains? bindmap pattern) (bindmap pattern)
    :else pattern))
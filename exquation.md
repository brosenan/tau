* [Exquations](#exquations)
  * [Bindings](#bindings)
  * [Exquation Semantics](#exquation-semantics)
```clojure
(ns tau.exquation-test
  (:require [midje.sweet :refer [fact =>]]
            [tau.exquation :refer [binding? new-binding binding-terms subset?]]))

```
# Exquations

An exquation is a marriage between an expression and an equation.
Any valid (s-)expression without keywords (e.g., `:foo`) is also a valid exquation, but additionally,
exquations include _bindings_.

## Bindings

Bindings take the form `(% :x expr1 expr2 ... exprn)`, where `expr1 expr2 ... exprn` are
s-expressions that may contain `:x`.

The function `binding?` returns `true` when given a binding s-expression.
```clojure
(fact
 (binding? '(% :x 1 2 3)) => true
 (binding? '1) => false
 (binding? '(foo bar)) => false)

```
The function `create-binding` takes a collection of terms and a function that generates unique strings
and returns a new binding, where the variable name is the value returned by the function.
```clojure
(fact
 (new-binding '[foo bar baz] (fn [] "x123")) => '(% :x123 foo bar baz))

```
The function `binding-terms` returns the terms underlying a binding as valid exquations.
Since the underlying terms may include the binding keyword and valid exquations cannot have keywords,
every instance of the keyword in the underlying term is replaced by the entire binding.
```clojure
(fact
 (binding-terms '(% :x foo (bar :x))) => '[foo (bar (% :x foo (bar :x)))])

```
This reveals the true nature (and power) of bindings. They are recursive structures, where every use of
the bound keyword inside any of the terms represents the entire binding.

## Exquation Semantics

Every exquation represents a _set of s-expressions_.
The `subset?` function checks if the exquation it is given as a first argument is a subset (not strictly)
of the exquation given as its second argument. We will use this function to define the semantics of
different kinds of exquations.

Exquations that do not contain bindings represent _singleton sets_, containing only themselves.
For such exquations, `subset?` will return `true` if and only if the s-expressions are equal.
```clojure
(fact
 (subset? 'foo 'bar) => false
 (subset? 'foo 'foo) => true)

```
Bindings make things more interesting. A binding `(% :x expr1 expr2 ... exprn)` corresponds to the equation
![eq-binding-meaning](../doc/eq-binding-meaning.png) where ![eq-expri](../doc/eq-expri.png) corresponds to `expri`.

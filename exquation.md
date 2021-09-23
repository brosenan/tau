* [Exquations](#exquations)
  * [Bindings](#bindings)
  * [Exquation Semantics](#exquation-semantics)
    * [Types](#types)
    * [Implementation Details](#implementation-details)
```clojure
(ns tau.exquation-test
  (:require [midje.sweet :refer [fact =>]]
            [tau.exquation :refer [binding? new-binding binding-terms subset? bound-var]]))

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
The function `bound-var` returns the keyword placed in the first position of a binding.
```clojure
(fact
 (bound-var '(% :x 1 2 3)) => :x)

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
 (subset? 'foo 'foo) => true
 (subset? '(foo bar) '(foo bar)) => true
 (subset? '(foo bar) '(foo bar baz)) => false
 (subset? '(foo bar baz) '(foo bar)) => false
 (subset? '(foo bar) '(foo baz)) => false
 (subset? '(foo bar) '[foo bar]) => false
 (subset? '(foo (bar)) '(foo [bar])) => false
 (subset? '[foo bar] '[foo bar]) => true
 (subset? '[foo bar] '(foo bar)) => false
 (subset? '[foo [bar]] '[foo (bar)]) => false)

```
Elipsis (...) at the end of a list/vector mean that the element right before it
represents the rest of the list/vector.
```clojure
(fact
 (subset? '(a b (c d) ...) '(a b c d)) => true
 (subset? '(a b c d) '(a b (c d) ...)) => true
 (subset? '[a b [c d] ...] '[a b c d]) => true
(subset? '[a b c d] '[a b [c d] ...]) => true)

```
Bindings make things more interesting. A binding `(% :x expr1 expr2 ... exprn)` corresponds to the equation
![eq-binding-meaning](https://github.com/brosenan/tau/blob/main/doc/eq-binding-meaning.png?raw=true)
where ![eq-expri](https://github.com/brosenan/tau/blob/main/doc/eq-expri.png?raw=true) corresponds to `expri`,
which may or may not depend on `:x`.

When given an s-expression as its first argument and a binding as its second argument, `subset?` can be used
to determine if the s-expression is a member of the set defined by the binding.
```clojure
(fact
 (subset? 'foo '(% :x foo bar)) => true
 ;; 2 is a natural (peano) number.
 (subset? '(s (s 0)) '(% :n 0 (s :n))) => true
 ;; But this is not.
 (subset? '(s (s 42)) '(% :n 0 (s :n))) => false)

```
When a binding is given as a first argument to the `subset?` function, it has to check whether _every s-expression
that can be represented by this binding_ exists in the set represented by the second argument.
```clojure
(fact
 (subset? '(% :x foo bar) 'foo) => false
 (subset? '(% :x foo bar) '(% :y baz bar foo)) => true
 ;; Equality of the set of natural numbers.
 (subset? '(% :n 0 (s :n)) '(% :k (s :k) 0)) => true
 ;; Every even number is a number.
 (subset? '(% :n 0 (s (s :n))) '(% :k (s :k) 0)) => true
 ;; Not every number is even.
 (subset? '(% :n 0 (s :n)) '(% :k (s (s :k)) 0)) => false)

```
### Types

The symbols `int`, `float` and `string` represent sets of all values (literals) of the corresponding types.
```clojure
(fact
 (subset? 42 'int) => true
 (subset? 3.141592 'float) => true
 (subset? "foobar" 'string) => true)

```
This allows us to define more complex types, such as lists of integers.
```clojure
(fact
 (subset? '(4 6 2 7 2) '(% :l () (int :l ...))) => true
 (subset? '(4 6 2.5 7 2) '(% :l () (int :l ...))) => false)

```
### Implementation Details

A 3-parameter version of `subset?` takes a map of inductive assumptions.
For example, the map `{:x 'baz}` means that it is assumed that the binding that defines `:x` is a subset of
`baz`.
```clojure
(fact
 (subset? '(% :x foo bar) 'baz {:x 'baz}) => true
 ;; Assumptions trump a real evaluation of the binding on the left-hand side.
 (subset? '(% :x foo bar) 'foo {:x 'baz}) => false)

```
A binding is always a subset of a binding of the same bound variable (keyword), regardless of their content
(the assumption is that the keywords are unique, so same keyword implies same variable).
```clojure
(fact
 (subset? '(% :x foo) '(% :x bar)) => true)
```


* [Exquations](#exquations)
  * [Bindings](#bindings)
  * [Exquation Semantics](#exquation-semantics)
    * [Types](#types)
    * [Exquation Patterns](#exquation-patterns)
    * [Implementation Details](#implementation-details)
  * [Patterns](#patterns)
    * [Matching](#matching)
    * [Replacing](#replacing)
```clojure
(ns tau.exquation-test
  (:require [midje.sweet :refer [fact =>]]
            [tau.exquation :refer [binding? new-binding binding-terms subset? subset-conds bound-var match pattern-replace]]))

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
Ellipsis (...) at the end of a list/vector mean that the element right before it
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
 (subset? '(4 6 2.5 7 2) '(% :l () (int :l ...))) => false
 ;; An infinite list of 1's is a list of integers.
 (subset? '(% :x (1 :x ...)) '(% :l () (int :l ...))) => true)

```
### Exquation Patterns

While valid exquations do not contain keywords except within bindings that define them, arbitrary keywords may appear
in _exquation patterns_, where they represent an unknown part of the exquation.

The semantics of exquation patterns is defined by the `subset-conds` function, which takes two takes two exquation patterns
as arguments. If the first _can be_ a subset of the second under some conditinos regarding the keywords on either side,
the function will return a vector of these conditions. If they cannot, it returns `nil`.

When given regular (non-pattern) exquations, `subset-conds` behaves like `subset?`, returning `[]` in place of `true` and
`nil` in place of `false`.
```clojure
(fact
 (subset-conds '(4 6 2 7 2) '(% :l () (int :l ...))) => []
 (subset-conds '(4 6 2.5 7 2) '(% :l () (int :l ...))) => nil)

```
If we take an exquation that is a subset of another, and replace a part of it with a keyword,
`subset-conds` will return a condition under which the pattern would represent a subset.
```clojure
(fact
 (subset-conds :x 'x) => '[[:x x]]
 (subset-conds '(4 6 :x 7 2) '(% :l () (int :l ...))) => '[[:x int]]
 (subset-conds '(4 6 :xs ...) '(% :l () (int :l ...))) => '[[:xs (% :l () (int :l ...))]]
 (subset-conds '(4 6 :x 7 :y) '(% :l () (int :l ...))) => '[[:x int] [:y int]])

```
Taking the last example, it means that for `(4 6 :x 7 :y)` to be a list of integers, `:x` has to be _a subset of_
`int` and so does `y`.

Keywords may also appear in the second argument.
```clojure
(fact
 (subset-conds 'x :x) => '[[x :x]]
 (subset-conds '(% :k () (int :k ...)) '(% :l () (:t :l ...))) => '[[int :t]]
 (subset-conds '(1 2 3) '(% :l () (:t :l ...))) => '[[1 :t] [2 :t] [3 :t]])

```
Taking the last example, for `(1 2 3)` to be a list of type `:t`, our selection of `:t` must be such that all of
{1}, {2} and {3} (singleton sets) need to be subsets of `:t`.

### Implementation Details

A 4-parameter version of `subset-conds` takes a map of induction assumptions and the initial list of conditions.
Induction assumptions work such that the map `{:x 'baz}` means that it is assumed that the binding that defines `:x` is a subset of
`baz`.
```clojure
(fact
 (subset-conds '(% :x foo bar) 'baz {:x 'baz} [[:some :cond]]) => [[:some :cond]]
 ;; Assumptions trump a real evaluation of the binding on the left-hand side.
 (subset-conds '(% :x foo bar) 'foo {:x 'baz} []) => nil)

```
A binding is always a subset of a binding of the same bound variable (keyword), regardless of their content
(the assumption is that the keywords are unique, so same keyword implies same variable).
```clojure
(fact
 (subset? '(% :x foo) '(% :x bar)) => true)

```
## Patterns

A _pattern_ is an s-expression that includes unbound keywords (i.e., keywords that are not defined by an overwrapping binding).
Patterns can be used for _matching_ and _replacing_, where in the former an exquation is matched agaist a pattern
and if it matches it, bindings for the keywords are returned. In the latter, a pattern is provided along with a bindings map.
The result is an exquation that would provide these bindings if matched against that pattern.

### Matching

The `match` function takes a pattern, an exquation and a unique variable name generator function, and returns a bindings map if
the pattern matches, or `nil` if not.
```clojure
(defn gen-var []
  (let [counter (atom 0)]
    (fn []
      (str "v" (swap! counter inc)))))
(fact
 (let [g (gen-var)]
   [(g) (g)]) => ["v1" "v2"]
 (match 'foo 'foo (gen-var)) => {}
 (match 'foo 'bar (gen-var)) => nil
 (match :foo 'foo (gen-var)) => {:foo 'foo}
 (match '(foo :bar) '(foo bar) (gen-var)) => {:bar 'bar}
 (match '(foo :bar) '[foo bar] (gen-var)) => nil
 (match '[foo :bar] '(foo bar) (gen-var)) => nil
 (match '(foo :bar) 2 (gen-var)) => nil
 (match '(foo :bar) '(foo) (gen-var)) => nil
 (match '(:foo) '(foo bar) (gen-var)) => nil
 (match '[foo :bar] '[foo bar] (gen-var)) => {:bar 'bar}
 (match '[foo :bar] 2 (gen-var)) => nil
 (match '[foo :bar] '[foo] (gen-var)) => nil
 (match '[:foo] '[foo bar] (gen-var)) => nil)

```
Both patterns and matched exquations can include ellipses at the end of lists and vector. In both cases, they mean that the
term that comes before tham represents the rest of the list of vector.
```clojure
(fact
 (match '(1 2 :rest ...) '(1 2 3 4) (gen-var)) => {:rest '(3 4)}
 (match '(1 2 :three 4) '(1 2 (3 4) ...) (gen-var)) => {:three 3}
 (match '[1 2 :rest ...] '[1 2 3 4] (gen-var)) => {:rest '(3 4)}
 (match '[1 2 :three 4] '[1 2 (3 4) ...] (gen-var)) => {:three 3})

```
When matching a pattern against an exquation that contains bindings, the match (if found) represents all possible assignments.
```clojure
(fact
 (match :foo '(% :x foo bar) (gen-var)) => {:foo '(% :x foo bar)}
 (match 'foo '(% :x foo bar) (gen-var)) => {}
 (match '(s (s :x)) '(% :n 0 (s :n)) (gen-var)) => {:x '(% :n 0 (s :n))}
 (match '(x :y) '(% :v (x 1) (x 2)) (gen-var)) => {:y '(% :v1 1 2)})

```
### Replacing

The function `pattern-replace` takes a pattern and a bindings map, and returns an exquation that results from
replacing all keywords in the pattern with their corresponding exquations (from the map).
```clojure
(fact
 (pattern-replace 'foo {}) => 'foo
 (pattern-replace :foo {:foo 'foo}) => 'foo
 (pattern-replace '(:foo :bar) {:foo 'foo
                                :bar 'bar}) => '(foo bar)
 (pattern-replace '(:foo [:bar]) {:foo 'foo
                                  :bar 'bar}) => '(foo [bar]))

```
Ellipses work as one may expect.
```clojure
(fact
 (pattern-replace '(1 2 :more ...) {:more '(3 4 5)}) => '(1 2 3 4 5))
```


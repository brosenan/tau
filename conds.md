* [Condition Terms](#condition-terms)
  * [Contruction](#contruction)
  * [Partial Evaluation](#partial-evaluation)
```clojure
(ns tau.conds-test
  (:require [midje.sweet :refer [fact =>]]
            [tau.conds :refer [always-true? subset-term apply-op]]))

```
# Condition Terms

Condition terms are Boolean expressions that represent conditions on keywords.
These are either Boolean values (`true` or `false`) or lists starting with a symbol (either `subset`,
`or` or `and`) where the rest of the elements are Boolean terms themselves.

## Contruction

The function `subset-term` constructs a Boolean term requiring that its first argument be a subset of its second.
```clojure
(fact
 (subset-term :foo 'bar) => '(subset :foo bar))

```
The function `apply-op` takes a collection of Boolean terms and returns a term that applies the given operator (a symbol) to them.
In the case of a single element, the operator is omitted and the single element is returned.
```clojure
(fact
 (apply-op 'and [(subset-term :foo 'bar)]) => '(subset :foo bar)
 (apply-op 'and [(subset-term :foo 'bar)
                 (subset-term :bar 'foo)]) => '(and (subset :foo bar) (subset :bar foo)))

```
## Partial Evaluation

The function `always-true?` returns `true` if the given Boolean term evaluates to true regardless
of any `subset` clauses, or `false` otherwise.
```clojure
(fact
 (always-true? true) => true
 (always-true? false) => false
 (always-true? '(subset :foo :bar)) => false
 (always-true? '(and)) => true
 (always-true? '(and (subset :foo :bar))) => false
 (always-true? '(and (and) true)) => true
 (always-true? '(or)) => false
 (always-true? '(or (and) (or))) => true)
```


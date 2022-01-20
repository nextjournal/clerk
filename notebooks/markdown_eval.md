---
title:
:nextjournal.clerk/eval: false
---
# Controlling Eval in Markdown
Currently, all code fences are evaluated as Clojure, regardless of the language.

```clojure {visibility hide}
(+ 39 3)
```

```
(+ 39 3)
```



We don't always want that. For example we'd like to be able to render the README.md without evaluating code. For this, we'd need a way to turn off evaluation.

Another use case is the [sketch of the bash runner][1], where we parse code but run it differently. Could we let users extend Clerk's eval using functions based on the metadata on forms / code fences?

This metadata can contain:
* a language like `clojure`
* a map following it
* individual attributes that get merged into a map with `true` values

So the following two ways of specifi
```
^{:nextjournal.clerk/viewer :code
  :nextjournal.clerk/visibility :hide}
(do
  "```clojure no-exec {:nextjournal.clerk/visibility :hide}
^{:nextjournal.clerk/visibility :hide}
(ns controlling-eval)
```")
```


In this way it would behave similar to metadata in Clojure.

Lastly, it could also be cool, if we were able to generate a README.md with Clerk, generating certain aspects [like the current version][2]. For this I could see a regular markdown file without eval, but opting into eval using [Pollen's lozenge ◊][3]. You want code fences with a language for github.


[1]: https://github.com/nextjournal/nextjournal/blob/93c433805ad03c1aa22ca87911ce3fc9c73cb8c5/runner/src/nextjournal/runner/cli.clj
[2]: https://github.com/nextjournal/clerk/blob/main/README.md?plain=1#L47
[3]: https://docs.racket-lang.org/pollen/pollen-command-syntax.html

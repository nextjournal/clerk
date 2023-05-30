# 🤺 Markdown Fences
## Handling Clojure blocks

```
'(evaluated :and "highlighted")
```

```clj
'(evaluated :and "highlighted")
```

```clojure
'(evaluated :and "highlighted")
```

```clojure {:nextjournal.clerk/code-listing true}
(1 2 "not evaluated" :but-still-highlighted)
```

```{:nextjournal.clerk/code-listing true}
(1 2 "not evaluated" :but-still-highlighted)
```

## 🏳️‍🌈 Polyglot Highlighting

EDN

```edn
(1 2 "not evaluated" :but-still-highlighted)
```

Javascript

```js
() => {
  if (true) {
    return 'not evaluated'
  } else {
    return 123
  }
}
```

Python

```py
class Foo(object):
    def __init__(self):
        pass
    def do_this(self):
        return 1
```

C++

```c++
#include <iostream>

int main() {
    std::cout << "Hello, world!" << std::endl;
    return 0;
}
```

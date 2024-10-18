# Delay Viewer Eval

## The Problem

* Currently doing eval on read
  * This leads to problems: 
    * no control over when eval happens but sync atoms need to be interned before eval
    * no editscript diff for sync atom values
    * can't show error when clerk-eval fails because errors need to be reset before read
  * Want to move to doing it in postprocess instead

## The Plan

* [x] Move away from doing stuff on read, use qualified symbol to tag a value in plain data and eval in postwalk
* [x] Restore try/catch to not fail update when render-fn can't be evaluated
* [x] Restore cherry evaluator support
* [ ] Drop cherry data readers / writers
* [ ] Consider dropping or keeping viewer fn data readers for unreadable edn, or move to more specific `#clerk/unreadable-edn-atom` tag?
* [ ] Restore `rewrite-for-cherry`
* [ ] Drop `ViewerEval` type

      



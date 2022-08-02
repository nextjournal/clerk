import { sequence, seq, concat, list, symbol } from 'cherry-cljs/cljs.core.js'
var assert_BANG_ = function (_AMPERSAND_form, _AMPERSAND_env, v, msg) {
return sequence(seq(concat(list(symbol("when-not")), list(v), list(sequence(seq(concat(list(symbol("set!")), list(symbol("js/process.exitCode")), list(1))))), list(sequence(seq(concat(list(symbol("throw")), list(sequence(seq(concat(list(symbol("js/Error.")), list(msg))))))))))));
};

export { assert_BANG_ }

import { seq, zero_QMARK_, first, atom, map, reset_BANG_, some_QMARK_, rest, unchecked_inc, chunk_first, _EQ_, conj, truth_, interleave, replace, println, chunk_rest, not, type, chunked_seq_QMARK_, swap_BANG_, vector, _nth, keyword, str, next, count, apply, clj__GT_js, deref, arrayMap } from 'cherry-cljs/cljs.core.js'
import * as cp from 'child_process';
import { chromium } from 'playwright';
var sha = str(cp.execSync("git rev-parse HEAD"));
var index = "https://snapshots.nextjournal.com/clerk/build/{{sha}}/index.html".replace("{{sha}}", sha);
var browser = atom(null);
var default_launch_options = clj__GT_js(arrayMap(keyword("args"), vector("--no-sandbox")));
var headless = some_QMARK_(process.env["CI"]);
var launch_browser = async function () {
let chrome1 = (await chromium.launch(({ "headless": headless })));
return reset_BANG_(browser, chrome1);
};
var close_browser = true;
var goto$ = function (page, url) {
return page["goto"](url, ({ "waitUntil": "networkidle" }));
};
var test_notebook = async function (page, link) {
println("Visiting", link);
(await goto$(page, link));
println("Opened link");
if (truth_((await (await page.locator("div")).first().isVisible()))) {
null} else {
process.exitCode = 1;
throw new Error("should be true")};
if (truth_(false)) {
return null;} else {
process.exitCode = 1;
throw new Error(null)}
};
var console_errors = atom(vector());
var index_page_test = async function () {
return (await (async function () {
 try{
let page2 = (await deref(browser).newPage());
let _3 = page2.on("console", function (msg) {
if (truth_(_EQ_("error", msg.type())&&not(msg.location()["url"].endsWith("favicon.ico")))) {
return swap_BANG_(console_errors, conj, msg);}
});
let _4 = (await goto$(page2, index));
let _5 = (await page2.locator("h1:has-text(\"Clerk\")").isVisible());
let links6 = (await page2.locator("text=/.*\\.clj$/i").allInnerTexts());
let links7 = map(function (link) {
return str(index, "#/", link);
}, links6);
let seq__812 = seq(rest(links7));
let chunk__913 = null;
let count__1014 = 0;
let i__1115 = 0;
while(true){
if (truth_((i__1115 < count__1014))) {
let l16 = _nth(chunk__913, i__1115);
(await test_notebook(page2, l16));
null;
let G__17 = seq__812;
let G__18 = chunk__913;
let G__19 = count__1014;
let G__20 = unchecked_inc(i__1115);
seq__812 = G__17;
chunk__913 = G__18;
count__1014 = G__19;
i__1115 = G__20;
continue;
} else {
let temp__22304__auto__21 = seq(seq__812);
if (truth_(temp__22304__auto__21)) {
let seq__822 = temp__22304__auto__21;
if (truth_(chunked_seq_QMARK_(seq__822))) {
let c__22421__auto__23 = chunk_first(seq__822);
let G__24 = chunk_rest(seq__822);
let G__25 = c__22421__auto__23;
let G__26 = count(c__22421__auto__23);
let G__27 = 0;
seq__812 = G__24;
chunk__913 = G__25;
count__1014 = G__26;
i__1115 = G__27;
continue;
} else {
let l28 = first(seq__822);
(await test_notebook(page2, l28));
null;
let G__29 = next(seq__822);
let G__30 = null;
let G__31 = 0;
let G__32 = 0;
seq__812 = G__29;
chunk__913 = G__30;
count__1014 = G__31;
i__1115 = G__32;
continue;
}}};break;
}
;
if (truth_(zero_QMARK_(count(deref(console_errors))))) {
return null;} else {
return apply(str, interleave(map(function (msg) {
return vector(msg.text(), msg.location());
}, deref(console_errors)), "\n"));}}
catch(err){
return console.log(err);}

})());
};
try{
(await launch_browser());
(await index_page_test())}
finally{
deref(browser).close()}
;

export { default_launch_options, index, close_browser, test_notebook, browser, sha, launch_browser, headless, goto$, index_page_test, console_errors }

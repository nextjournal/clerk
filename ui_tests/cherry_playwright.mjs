import { seq, zero_QMARK_, first, atom, map, reset_BANG_, some_QMARK_, rest, unchecked_inc, chunk_first, _EQ_, conj, truth_, interleave, prn, println, chunk_rest, not, chunked_seq_QMARK_, swap_BANG_, vector, _nth, keyword, str, next, count, apply, clj__GT_js, deref, arrayMap } from 'cherry-cljs/cljs.core.js'
import * as cp from 'child_process';
import { chromium } from 'playwright';
var sha = str.call(null, cp.execSync.call(null, "git rev-parse HEAD")).trim();
var index = "https://snapshots.nextjournal.com/clerk/build/{{sha}}/index.html".replace("{{sha}}", sha);
var browser = atom.call(null, null);
var default_launch_options = clj__GT_js.call(null, arrayMap(keyword("args"), vector("--no-sandbox")));
var headless = some_QMARK_.call(null, process.env["CI"]);
var launch_browser = async function () {
let chrome12 = (await chromium.launch(({ "headless": headless })));
return reset_BANG_.call(null, browser, chrome12);
};
var close_browser = true;
var goto$ = function (page, url) {
return page.goto(url, ({ "waitUntil": "networkidle" }));
};
var test_notebook = async function (page, link) {
println.call(null, "Visiting", link);
(await goto$.call(null, page, link));
println.call(null, "Opened link");
if (truth_((await (await page.locator("div")).first().isVisible()))) {
return null;} else {
process.exitCode = 1;
throw new Error("should be true")}
};
var console_errors = atom.call(null, vector());
var index_page_test = async function () {
return (await (async function () {
 try{
let page13 = (await deref.call(null, browser).newPage());
let _14 = page13.on("console", function (msg) {
if (truth_(_EQ_.call(null, "error", msg.type()) && not.call(null, msg.location()["url"].endsWith("favicon.ico")))) {
return swap_BANG_.call(null, console_errors, conj, msg);}
});
let _15 = prn.call(null, keyword("hello"), index);
let _16 = (await goto$.call(null, page13, index));
let _17 = (await page13.locator("h1:has-text(\"Clerk\")").isVisible());
let links18 = (await page13.locator("text=/.*\\.clj$/i").allInnerTexts());
let links19 = map.call(null, function (link) {
return str.call(null, index, "#/", link);
}, links18);
let seq__2024 = seq.call(null, rest.call(null, links19));
let chunk__2125 = null;
let count__2226 = 0;
let i__2327 = 0;
while(true){
if (truth_((i__2327 < count__2226))) {
let l28 = _nth.call(null, chunk__2125, i__2327);
(await test_notebook.call(null, page13, l28));
null;
let G__29 = seq__2024;
let G__30 = chunk__2125;
let G__31 = count__2226;
let G__32 = unchecked_inc.call(null, i__2327);
seq__2024 = G__29;
chunk__2125 = G__30;
count__2226 = G__31;
i__2327 = G__32;
continue;
} else {
let temp__22454__auto__33 = seq.call(null, seq__2024);
if (truth_(temp__22454__auto__33)) {
let seq__2034 = temp__22454__auto__33;
if (truth_(chunked_seq_QMARK_.call(null, seq__2034))) {
let c__22606__auto__35 = chunk_first.call(null, seq__2034);
let G__36 = chunk_rest.call(null, seq__2034);
let G__37 = c__22606__auto__35;
let G__38 = count.call(null, c__22606__auto__35);
let G__39 = 0;
seq__2024 = G__36;
chunk__2125 = G__37;
count__2226 = G__38;
i__2327 = G__39;
continue;
} else {
let l40 = first.call(null, seq__2034);
(await test_notebook.call(null, page13, l40));
null;
let G__41 = next.call(null, seq__2034);
let G__42 = null;
let G__43 = 0;
let G__44 = 0;
seq__2024 = G__41;
chunk__2125 = G__42;
count__2226 = G__43;
i__2327 = G__44;
continue;
}}};break;
}
;
if (truth_(zero_QMARK_.call(null, count.call(null, deref.call(null, console_errors))))) {
return null;} else {
return apply.call(null, str, interleave.call(null, map.call(null, function (msg) {
return vector(msg.text(), msg.location());
}, deref.call(null, console_errors)), "\n"));}}
catch(err){
return console.log(err);}

})());
};
try{
(await launch_browser.call(null));
(await index_page_test.call(null))}
finally{
deref.call(null, browser).close()}
;

export { default_launch_options, index, close_browser, test_notebook, browser, sha, launch_browser, headless, goto$, index_page_test, console_errors }

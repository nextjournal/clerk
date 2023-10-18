import fs from 'fs'
// import * as ox from '@tailwindcss/oxide'
import { defaultExtractor } from "tailwindcss/lib/lib/defaultExtractor.js"
import { generateRules, resolveMatches } from "tailwindcss/lib/lib/generateRules.js"
import { default as expandTailwindAtRules } from "tailwindcss/lib/lib/expandTailwindAtRules.js"
// import { default as processTailwindFeatures } from "tailwindcss/lib/processTailwindFeatures.js"
import tailwind from "tailwindcss/lib/processTailwindFeatures.js"
import { readdir, readFile } from 'fs'

// https://github.com/tailwindlabs/tailwindcss/blob/master/src/lib/defaultExtractor.js#L3
// https://github.com/tailwindlabs/tailwindcss/blob/master/src/lib/expandTailwindAtRules.js#L8

// root: resources/stylesheets/viewer.css

// https://medium.com/@bomber.marek/whats-tailwind-oxide-engine-the-next-evolution-of-tailwind-css-32e7ef8e19a1

let context = { tailwindConfig: {separator: ':', prefix: ''},
                notClassCache: new Set(),
                classCache: new Map(),
                candidateRuleCache: new Map(),
                candidateRuleMap: new Map()},
    extract = defaultExtractor(context),
    candidates = new Set([...extract("foo bar\n[:div.text-amber-200]\n<div class='mt-2'>ahoi</div>\n<div class='hover:test'>\n<div class='hover:font-bold'></div>"), "*"]),
    sortedCandidates = [...candidates].sort((a, z) => {
      if (a === z) return 0
      if (a < z) return -1
      return 1
    }),
    root = {name: "root",
             walkAtRules: (walkFn) => { console.log('walk@', walkFn.toString()) },
              walkRules: (walkFn) => { console.log('walk', walkFn.toString()) }}

// readFile("package.json", (err, contents) => { console.log(contents) })

// run with `bun tailwind-extractor.ts`
console.log("Extracted",
//              ox,
//               await expandTailwindAtRules(context)(root),
//              await tailwind((input) => { console.log('input', input); return context; })(root, {}),
//             fs.promises.readDir('src', ),
            candidates,
            Array.from(resolveMatches("foo", context)),
            Array.from(resolveMatches("mt-2", context)),
            generateRules(sortedCandidates, context));

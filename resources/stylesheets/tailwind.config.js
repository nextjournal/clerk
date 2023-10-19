module.exports = {
  darkMode: "class",
  content: ["index.clj", "book.clj",
            "notebooks/**.{md,clj,cljc,cljs}",
            "src/**/*.{clj,cljc,cljs}",
            "tw/**.{md,clj,cljc,cljs,txt}"],
  safelist: ['dark'],
  theme: {
    extend: {},
    fontFamily: {
      sans: ["Fira Sans", "-apple-system", "BlinkMacSystemFont", "sans-serif"],
      serif: ["PT Serif", "serif"],
      mono: ["Fira Mono", "monospace"]
    }
  },
  variants: {
    extend: {},
  },
  plugins: [require("@tailwindcss/typography")],
}

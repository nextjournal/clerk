module.exports = {
  darkMode: "class",
  content: ["./public/build/index.html", "./public/build/**/*.html", "./build/viewer.js", "./build/**/*.edn"],
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

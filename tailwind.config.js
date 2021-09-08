module.exports = {
    mode: "jit",
    purge: {
       content: process.env.NODE_ENV == 'production' ? ["./public/js/*.js"] : ["./public/js/cljs-runtime/*.js"]
    },
    theme: {
        extend: {},
    },
    variants: {
        extend: {},
    },
    plugins: [],
};

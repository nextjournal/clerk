// Client components for Clerk's Datastar frontend.
//
// Each component reads its data from attributes and renders into its own
// light DOM. The server marks them with `data-ignore-morph` and an `id`
// derived from their content so Datastar keeps the rendered element as is
// until the content changes, then swaps in a fresh element.
// Libraries are loaded lazily from the CDN on first use.

const VEGA_EMBED = 'https://cdn.jsdelivr.net/npm/vega-embed@6.26.0/+esm'
// As an ES module because the UMD build registers with an AMD `define` (from the viewer bundle) instead
// of setting `window.Plotly`. From esm.sh as jsDelivr's `+esm` conversion of it doesn't parse.
const PLOTLY = 'https://esm.sh/plotly.js-dist-min@2.35.2'
const KATEX = 'https://cdn.jsdelivr.net/npm/katex@0.16.11/+esm'
const MATHJAX = 'https://cdn.jsdelivr.net/npm/mathjax@3.2.2/es5/tex-svg-full.js'

const loading = new Map()

const once = (url, load) => {
  if (!loading.has(url)) loading.set(url, load())
  return loading.get(url)
}

const importModule = (url) => once(url, () => import(url))

const loadScript = (url, global) =>
  once(url, () => new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.src = url
    script.onload = () => resolve(window[global])
    script.onerror = () => reject(new Error(`Failed to load ${url}`))
    document.head.append(script)
  }))

const jsonAttr = (el, name, fallback) => {
  const value = el.getAttribute(name)
  return value == null ? fallback : JSON.parse(value)
}

class ClerkComponent extends HTMLElement {
  connectedCallback () {
    if (this.rendered) return
    this.rendered = true
    this.render().catch((error) => {
      console.error(error)
      this.replaceChildren(Object.assign(document.createElement('div'), {
        className: 'text-xs font-mono text-red-600',
        textContent: String(error?.message ?? error)
      }))
    })
  }
}

customElements.define('clerk-vega-lite', class extends ClerkComponent {
  async render () {
    const { default: embed } = await importModule(VEGA_EMBED)
    const container = document.createElement('div')
    this.replaceChildren(container)
    await embed(container, jsonAttr(this, 'spec'), jsonAttr(this, 'opts', {}))
  }
})

customElements.define('clerk-plotly', class extends ClerkComponent {
  async render () {
    const { default: Plotly } = await importModule(PLOTLY)
    const container = document.createElement('div')
    this.replaceChildren(container)
    await Plotly.react(container, jsonAttr(this, 'figure'))
  }
})

customElements.define('clerk-katex', class extends ClerkComponent {
  async render () {
    const { default: katex } = await importModule(KATEX)
    this.innerHTML = katex.renderToString(this.getAttribute('tex'), {
      displayMode: !this.hasAttribute('inline'),
      throwOnError: false
    })
  }
})

customElements.define('clerk-mathjax', class extends ClerkComponent {
  async render () {
    window.MathJax ??= { startup: { typeset: false } }
    await loadScript(MATHJAX, 'MathJax')
    await window.MathJax.startup.promise
    this.replaceChildren(window.MathJax.tex2svg(this.getAttribute('tex')))
  }
})

// ## Islands
// Values whose viewers need the browser (custom `:render-fn`s) are rendered
// by Clerk's ClojureScript viewer bundle (SCI + Reagent), loaded on first use.

const viewerUrl = () => document.querySelector('meta[name="clerk-viewer-js"]')?.content

const loadViewer = () =>
  once('clerk-viewer', async () => {
    const url = viewerUrl()
    if (!url) throw new Error('Clerk\'s viewer bundle isn\'t available, values needing it require the `:datastar+sci-react-island` frontend.')
    await import(url)
    const viewer = globalThis.nextjournal.clerk.sci_env
    const endpoint = document.querySelector('meta[name="clerk-island-endpoint"]')?.content
    if (endpoint) viewer.connect_http(endpoint)
    return viewer
  })

const islandEnv = () => document.querySelector('clerk-island-env')

const applyEnv = (viewer, env) => {
  if (!env) return
  if (env.hasAttribute('libs')) viewer.eval_island_libs_BANG_(env.getAttribute('libs'))
  if (env.hasAttribute('state')) viewer.set_island_state_BANG_(env.getAttribute('state'))
}

// Carries the notebook's synced atoms and required cljs, applied to the
// viewer bundle once it's loaded and whenever the server changes them.
customElements.define('clerk-island-env', class extends HTMLElement {
  static observedAttributes = ['libs', 'state']

  attributeChangedCallback () {
    if (loading.has('clerk-viewer')) loadViewer().then((viewer) => applyEnv(viewer, this))
  }
})

customElements.define('clerk-island', class extends ClerkComponent {
  async render () {
    const viewer = await loadViewer()
    applyEnv(viewer, islandEnv())
    this.root = viewer.mount_island(this, this.getAttribute('presented'))
  }

  disconnectedCallback () {
    // morphing may move the element, only unmount when it's really gone
    setTimeout(() => {
      if (!this.isConnected && this.root) {
        this.root.unmount()
        this.root = null
        this.rendered = false
      }
    })
  }
})

// ## Squint islands
// Values whose viewers need the browser, rendered by Reagami with their
// `:render-fn` compiled by Squint. The page's import map resolves the imports.

customElements.define('clerk-squint-island', class extends ClerkComponent {
  async render () {
    const [reagami, { render_fn: renderFn }] = await Promise.all([
      importModule('reagami'),
      importModule(`clerk-squint/${this.getAttribute('module')}.js`)
    ])
    reagami.render(this, renderFn(jsonAttr(this, 'value'), jsonAttr(this, 'opts', {})))
  }
})

// ## Table of contents & dark mode
// Per browser ui state kept in localStorage (same keys as the cljs viewer)
// and on the <html> element which morphing never touches.

const TOC_KEY = 'clerk-navbar'
const DARK_MODE_KEY = 'clerk-darkmode'

const storedBoolean = (key) => {
  const value = localStorage.getItem(key)
  return value === 'true' ? true : value === 'false' ? false : null
}

const setTocOpen = (open) => {
  document.documentElement.dataset.clerkToc = open ? 'open' : 'closed'
}

window.clerkToggleToc = (open) => {
  localStorage.setItem(TOC_KEY, String(open))
  setTocOpen(open)
}

window.clerkToggleDarkMode = () => {
  const dark = document.documentElement.classList.toggle('dark')
  localStorage.setItem(DARK_MODE_KEY, String(dark))
}

customElements.define('clerk-toc', class extends HTMLElement {
  connectedCallback () {
    setTocOpen(storedBoolean(TOC_KEY) ?? this.dataset.default === 'open')
  }

  disconnectedCallback () {
    // morphing may move the element, only reset when it's really gone
    setTimeout(() => {
      if (!document.querySelector('clerk-toc')) delete document.documentElement.dataset.clerkToc
    })
  }
})

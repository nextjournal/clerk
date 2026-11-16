// Base class for custom elements defined with `nextjournal.clerk/defelement`.
// Calls `render` with the parsed `value` attribute in a shadow root, which morphing leaves alone.

export class ClerkElement extends HTMLElement {
  static observedAttributes = ['value']

  constructor () {
    super()
    this.attachShadow({ mode: 'open' })
  }

  connectedCallback () {
    this.connected = true
    this.update()
  }

  attributeChangedCallback () {
    if (this.connected) this.update()
  }

  async update () {
    try {
      await this.render(JSON.parse(this.getAttribute('value') ?? 'null'))
    } catch (error) {
      console.error(error)
      this.shadowRoot.replaceChildren(Object.assign(document.createElement('div'), {
        textContent: String(error?.message ?? error),
        style: 'color: #dc2626; font: 12px monospace'
      }))
    }
  }

  render (_value) {}
}

// A tag can only be defined once, so a changed definition reloads the page.
export const defineElement = (tag, cls) => {
  const existing = customElements.get(tag)
  if (!existing) {
    customElements.define(tag, cls)
  } else if (existing !== cls) {
    location.reload()
  }
}

import { enableAutoUnmount, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { h } from 'vue'
import SidePanel from './SidePanel.vue'

/** The app's root, which the open panel makes inert (index.html mounts the app in #app). */
let app: HTMLElement

/** A panel with a bar (two buttons) and content (a heading and a link), mounted in the document, for focus. */
function mountPanel(props: { inline?: boolean } = {}) {
  return mount(SidePanel, {
    props: { labelledby: 'panel-title', ...props },
    slots: {
      bar: () => [h('button', { type: 'button' }, 'Poprzedni'), h('button', { type: 'button' }, 'Zamknij')],
      default: () => [h('h2', { id: 'panel-title', tabindex: '-1' }, 'Tytuł'), h('a', { href: '/x' }, 'Odnośnik')],
    },
    attachTo: app,
  })
}

function dialog(): HTMLElement {
  const found = document.querySelector<HTMLElement>('[role="dialog"]')
  if (!found) throw new Error('no dialog')
  return found
}

function byText(text: string): HTMLElement {
  const found = [...document.querySelectorAll<HTMLElement>('button, a')].find((element) => element.textContent === text)
  if (!found) throw new Error(`no control "${text}"`)
  return found
}

function press(key: string, options: KeyboardEventInit = {}): KeyboardEvent {
  const event = new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true, ...options })
  ;(document.activeElement ?? document.body).dispatchEvent(event)
  return event
}

enableAutoUnmount(afterEach)

describe('SidePanel', () => {
  beforeEach(() => {
    app = document.createElement('div')
    app.id = 'app'
    document.body.append(app)
  })

  afterEach(() => {
    document.body.innerHTML = ''
    document.body.removeAttribute('style')
  })

  it('is a modal dialog named by its content heading, moved out of the app to the body', () => {
    mountPanel()
    const panel = dialog()
    expect(panel.getAttribute('aria-modal')).toBe('true')
    expect(panel.getAttribute('aria-labelledby')).toBe('panel-title')
    expect(panel.parentElement).toBe(document.body)
    expect(app.contains(panel)).toBe(false)
    expect(panel.querySelector('.bar')?.textContent).toBe('PoprzedniZamknij')
    expect(panel.querySelector('.body h2')?.textContent).toBe('Tytuł')
  })

  it('makes the rest of the page inert and stops it scrolling while it is open, and gives both back when it goes', () => {
    document.body.style.overflow = 'auto'
    const wrapper = mountPanel()
    expect(app.hasAttribute('inert')).toBe(true)
    expect(document.body.style.overflow).toBe('hidden')

    wrapper.unmount()

    expect(app.hasAttribute('inert')).toBe(false)
    expect(document.body.style.overflow).toBe('auto')
    expect(document.querySelector('[role="dialog"]')).toBeNull()
  })

  it('takes the focus when it opens, and gives it to its heading on focus()', async () => {
    const wrapper = mountPanel()
    expect(document.activeElement).toBe(dialog())

    wrapper.vm.focus()

    expect(document.activeElement).toBe(document.getElementById('panel-title'))
  })

  it('asks to close on Escape, wherever the focus is', () => {
    const wrapper = mountPanel()
    ;(document.activeElement as HTMLElement).blur()
    press('Escape')
    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  it('asks to close on a click on the scrim', async () => {
    const wrapper = mountPanel()
    document.querySelector<HTMLElement>('.scrim')?.click()
    expect(wrapper.emitted('close')).toHaveLength(1)
    // A click inside the panel is no request to close it.
    byText('Poprzedni').click()
    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  it('keeps Tab inside: from the last control to the first and, with Shift, from the first to the last', () => {
    mountPanel()
    byText('Odnośnik').focus()
    const forward = press('Tab')
    expect(forward.defaultPrevented).toBe(true)
    expect(document.activeElement).toBe(byText('Poprzedni'))

    const backward = press('Tab', { shiftKey: true })
    expect(backward.defaultPrevented).toBe(true)
    expect(document.activeElement).toBe(byText('Odnośnik'))
  })

  it('goes to the last control on Shift+Tab from the dialog itself', () => {
    mountPanel()
    expect(document.activeElement).toBe(dialog())
    press('Tab', { shiftKey: true })
    expect(document.activeElement).toBe(byText('Odnośnik'))
  })

  it('leaves Tab between the controls inside to the browser', () => {
    mountPanel()
    byText('Poprzedni').focus()
    expect(press('Tab').defaultPrevented).toBe(false)
  })

  it('in its place, for the catalogue, is only the frame: no dialog, no scrim, the page untouched', () => {
    const wrapper = mountPanel({ inline: true })
    expect(document.querySelector('[role="dialog"]')).toBeNull()
    expect(document.querySelector('.scrim')).toBeNull()
    expect(app.contains(wrapper.find('.side-panel').element)).toBe(true)
    expect(app.hasAttribute('inert')).toBe(false)
    expect(document.body.style.overflow).toBe('')
    press('Escape')
    expect(wrapper.emitted('close')).toBeUndefined()
  })
})

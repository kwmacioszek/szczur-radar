import { afterEach, describe, expect, it } from 'vitest'
import { restoreFocus } from './focus'

function attach<K extends keyof HTMLElementTagNameMap>(tag: K): HTMLElementTagNameMap[K] {
  const element = document.createElement(tag)
  document.body.append(element)
  return element
}

describe('restoreFocus', () => {
  afterEach(() => {
    document.body.replaceChildren()
  })

  it('gives the focus back to the sender when the browser dropped it to the body', async () => {
    const button = attach('button')
    button.focus()
    button.blur()
    expect(document.activeElement).toBe(document.body)

    await restoreFocus(button)

    expect(document.activeElement).toBe(button)
  })

  it('keeps a focus the page moved on purpose, such as a field with an error', async () => {
    const button = attach('button')
    const field = attach('input')
    field.focus()

    await restoreFocus(button)

    expect(document.activeElement).toBe(field)
  })

  it('does nothing for a sender the page no longer shows', async () => {
    const button = attach('button')
    button.remove()

    await restoreFocus(button)

    expect(document.activeElement).toBe(document.body)
  })

  it('does nothing without a sender', async () => {
    await restoreFocus(null)

    expect(document.activeElement).toBe(document.body)
  })
})

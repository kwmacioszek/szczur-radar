import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import ErrorState from './ErrorState.vue'

describe('ErrorState', () => {
  afterEach(() => {
    document.body.innerHTML = ''
  })

  function error(props: Record<string, unknown> = {}) {
    return mount(ErrorState, {
      props: { title: 'Nie udało się wczytać postów', ...props },
      slots: { default: 'Serwer nie odpowiedział. Twoje filtry zostały.' },
      attachTo: document.body,
    })
  }

  it('is an alert with its icon, title, sentence and a button that retries', async () => {
    const wrapper = error()
    expect(wrapper.attributes('role')).toBe('alert')
    expect(wrapper.classes()).toEqual(expect.arrayContaining(['state', 'state-error']))
    expect(wrapper.find('use').attributes('href')).toBe('#i-circle-alert')
    expect(wrapper.find('.state-title').element.tagName).toBe('P')
    expect(wrapper.find('.state-title').text()).toBe('Nie udało się wczytać postów')
    expect(wrapper.find('p.state-text').text()).toBe('Serwer nie odpowiedział. Twoje filtry zostały.')

    const button = wrapper.find('button')
    expect(button.text()).toBe('Spróbuj ponownie')
    expect(button.find('use').attributes('href')).toBe('#i-refresh-cw')
    expect(button.attributes('aria-busy')).toBeUndefined()

    await button.trigger('click')
    expect(wrapper.emitted('retry')).toHaveLength(1)
  })

  it('takes a level-2 heading as its title, with an id', () => {
    const wrapper = error({ titleTag: 'h2', titleId: 'error-title' })
    const title = wrapper.find('.state-title')
    expect(title.element.tagName).toBe('H2')
    expect(title.attributes('id')).toBe('error-title')
  })

  it('while retrying has no alert role and a busy button with "Wczytywanie…", and does not retry again', async () => {
    const wrapper = error({ retrying: true })
    expect(wrapper.attributes('role')).toBeUndefined()
    const button = wrapper.find('button')
    expect(button.attributes('aria-busy')).toBe('true')
    expect(button.attributes('disabled')).toBeDefined()
    expect(button.text()).toBe('Wczytywanie…')
    expect(button.find('.spinner').exists()).toBe(true)
    expect(wrapper.find('.state-title').text()).toBe('Nie udało się wczytać postów')

    await button.trigger('click')
    expect(wrapper.emitted('retry')).toBeUndefined()
  })

  it('draws the message anew when a retry ends, so a second failure is announced again', async () => {
    const wrapper = error()
    const first = wrapper.element

    await wrapper.setProps({ retrying: true })
    expect(wrapper.element).toBe(first)
    expect(wrapper.attributes('role')).toBeUndefined()

    await wrapper.setProps({ retrying: false })
    expect(wrapper.element).not.toBe(first)
    expect(wrapper.attributes('role')).toBe('alert')
    expect(wrapper.find('button').text()).toBe('Spróbuj ponownie')
  })

  it('does not redraw while it only starts retrying or while the props change otherwise', async () => {
    const wrapper = error()
    const first = wrapper.element
    await wrapper.setProps({ title: 'Nie udało się wczytać posta' })
    await wrapper.setProps({ retrying: true })
    expect(wrapper.element).toBe(first)
  })

  it('puts the focus on its button with focus(), also after it was drawn anew', async () => {
    const wrapper = error()
    ;(wrapper.vm as unknown as { focus: () => void }).focus()
    expect(document.activeElement).toBe(wrapper.find('button').element)

    await wrapper.setProps({ retrying: true })
    await wrapper.setProps({ retrying: false })
    ;(wrapper.vm as unknown as { focus: () => void }).focus()
    expect(document.activeElement).toBe(wrapper.find('button').element)
  })
})

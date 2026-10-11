import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import AppButton from './AppButton.vue'

describe('AppButton', () => {
  it('is a secondary button that never submits a form by default', () => {
    const wrapper = mount(AppButton, { slots: { default: 'Odśwież' } })
    expect(wrapper.element.tagName).toBe('BUTTON')
    expect(wrapper.attributes('type')).toBe('button')
    expect(wrapper.classes()).toEqual(['secondary'])
    expect(wrapper.attributes('disabled')).toBeUndefined()
    expect(wrapper.attributes('aria-busy')).toBeUndefined()
    expect(wrapper.text()).toBe('Odśwież')
  })

  it('takes the primary variant and submits only when asked', () => {
    const wrapper = mount(AppButton, { props: { variant: 'primary', type: 'submit' }, slots: { default: 'Uruchom' } })
    expect(wrapper.classes()).toEqual(['primary'])
    expect(wrapper.attributes('type')).toBe('submit')
  })

  it('while busy is disabled, marked busy and shows the busy label', () => {
    const wrapper = mount(AppButton, {
      props: { busy: true, busyLabel: 'Uruchamianie…' },
      slots: { default: 'Uruchom' },
    })
    expect(wrapper.attributes('disabled')).toBeDefined()
    expect(wrapper.attributes('aria-busy')).toBe('true')
    expect(wrapper.text()).toBe('Uruchamianie…')
  })

  it('keeps its text while busy without a busy label', () => {
    const wrapper = mount(AppButton, { props: { busy: true }, slots: { default: 'Wyloguj' } })
    expect(wrapper.attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toBe('Wyloguj')
  })

  it('is disabled on request and passes clicks through', async () => {
    const onClick = vi.fn()
    const enabled = mount(AppButton, { attrs: { onClick }, slots: { default: 'Odśwież' } })
    await enabled.trigger('click')
    expect(onClick).toHaveBeenCalledTimes(1)

    const disabled = mount(AppButton, { props: { disabled: true }, slots: { default: 'Odśwież' } })
    expect(disabled.attributes('disabled')).toBeDefined()
    expect(disabled.attributes('aria-busy')).toBeUndefined()
  })
})

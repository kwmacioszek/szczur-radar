import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import AppButton from './AppButton.vue'

describe('AppButton', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('is a secondary button that never submits a form by default', () => {
    const wrapper = mount(AppButton, { slots: { default: 'Odśwież' } })
    expect(wrapper.element.tagName).toBe('BUTTON')
    expect(wrapper.attributes('type')).toBe('button')
    expect(wrapper.classes()).toContain('secondary')
    expect(wrapper.classes()).not.toContain('primary')
    expect(wrapper.attributes('disabled')).toBeUndefined()
    expect(wrapper.attributes('aria-busy')).toBeUndefined()
    expect(wrapper.text()).toBe('Odśwież')
  })

  it('takes the primary variant and submits only when asked', () => {
    const wrapper = mount(AppButton, { props: { variant: 'primary', type: 'submit' }, slots: { default: 'Uruchom' } })
    expect(wrapper.classes()).toContain('primary')
    expect(wrapper.classes()).not.toContain('secondary')
    expect(wrapper.attributes('type')).toBe('submit')
  })

  it('while busy is disabled, marked busy and shows the busy label after the mark of work', () => {
    const wrapper = mount(AppButton, {
      props: { busy: true, busyLabel: 'Uruchamianie…' },
      slots: { default: 'Uruchom' },
    })
    expect(wrapper.attributes('disabled')).toBeDefined()
    expect(wrapper.attributes('aria-busy')).toBe('true')
    expect(wrapper.text()).toBe('Uruchamianie…')
    expect(wrapper.find('.spinner').attributes('aria-hidden')).toBe('true')
  })

  it('keeps its text while busy without a busy label', () => {
    const wrapper = mount(AppButton, { props: { busy: true }, slots: { default: 'Wyloguj' } })
    expect(wrapper.attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toBe('Wyloguj')
    expect(wrapper.find('.spinner').exists()).toBe(true)
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

  it('draws an icon before its text, hidden from assistive technology', () => {
    const wrapper = mount(AppButton, { props: { icon: 'i-refresh-cw' }, slots: { default: 'Spróbuj ponownie' } })
    expect(wrapper.find('use').attributes('href')).toBe('#i-refresh-cw')
    expect(wrapper.find('svg').attributes('aria-hidden')).toBe('true')
    expect(wrapper.text()).toBe('Spróbuj ponownie')
  })

  it('takes the large size', () => {
    const wrapper = mount(AppButton, { props: { size: 'lg' }, slots: { default: 'Filtry' } })
    expect(wrapper.classes()).toContain('large')
    expect(mount(AppButton, { slots: { default: 'Filtry' } }).classes()).not.toContain('large')
  })

  it('as an icon-only button has a name from its aria-label and no text', () => {
    const wrapper = mount(AppButton, {
      props: { icon: 'i-chevron-left', iconOnly: true },
      attrs: { 'aria-label': 'Poprzedni post' },
      slots: { default: 'ignored' },
    })
    expect(wrapper.attributes('aria-label')).toBe('Poprzedni post')
    expect(wrapper.text()).toBe('')
    expect(wrapper.classes()).toContain('icon-only')
    expect(wrapper.find('use').attributes('href')).toBe('#i-chevron-left')
  })

  it('warns in development about an icon-only button without a name', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    mount(AppButton, { props: { icon: 'i-x', iconOnly: true } })
    expect(warn).toHaveBeenCalledTimes(1)
  })

  it('replaces the icon with the mark of work while busy and keeps the icon-only name', () => {
    const wrapper = mount(AppButton, {
      props: { icon: 'i-play', iconOnly: true, busy: true },
      attrs: { 'aria-label': 'Uruchom' },
    })
    expect(wrapper.find('use').exists()).toBe(false)
    expect(wrapper.find('.spinner').exists()).toBe(true)
    expect(wrapper.attributes('aria-label')).toBe('Uruchom')
  })
})

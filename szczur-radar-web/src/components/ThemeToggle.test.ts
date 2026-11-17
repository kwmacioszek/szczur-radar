import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'
import { setTheme } from '../theme'
import ThemeToggle from './ThemeToggle.vue'

describe('ThemeToggle', () => {
  beforeEach(() => {
    localStorage.clear()
    setTheme('light')
  })

  it('is pressed in the dark theme and not in the light one, under one name', async () => {
    const wrapper = mount(ThemeToggle)
    expect(wrapper.text()).toBe('Ciemny motyw')
    expect(wrapper.attributes('aria-pressed')).toBe('false')

    setTheme('dark')
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toBe('Ciemny motyw')
    expect(wrapper.attributes('aria-pressed')).toBe('true')
  })

  it('switches the theme on click, both ways', async () => {
    const wrapper = mount(ThemeToggle)

    await wrapper.trigger('click')
    expect(wrapper.attributes('aria-pressed')).toBe('true')
    expect(document.documentElement.dataset.theme).toBe('dark')

    await wrapper.trigger('click')
    expect(wrapper.attributes('aria-pressed')).toBe('false')
    expect(document.documentElement.dataset.theme).toBe('light')
  })
})

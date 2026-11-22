import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import AppBrand from './AppBrand.vue'

describe('AppBrand', () => {
  it('is an inline element named by its visible text', () => {
    const wrapper = mount(AppBrand)
    expect(wrapper.element.tagName.toLowerCase()).toBe('span')
    expect(wrapper.text()).toBe('Szczur Radar')
  })

  it('draws the radar dial of the sprite, hidden from assistive technology', () => {
    const icon = mount(AppBrand).find('svg')
    expect(icon.find('use').attributes('href')).toBe('#i-brand')
    expect(icon.attributes('aria-hidden')).toBe('true')
  })
})

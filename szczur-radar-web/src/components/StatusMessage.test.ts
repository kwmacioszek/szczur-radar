import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import StatusMessage from './StatusMessage.vue'

describe('StatusMessage', () => {
  it.each([
    ['ok', 'status', '#i-circle-check'],
    ['error', 'alert', '#i-circle-alert'],
    ['warning', 'status', '#i-triangle-alert'],
  ] as const)('%s has role %s, the icon of its tone and its text', (tone, role, icon) => {
    const wrapper = mount(StatusMessage, { props: { tone }, slots: { default: 'Uruchomiono: zakres dat.' } })
    expect(wrapper.attributes('role')).toBe(role)
    expect(wrapper.classes()).toContain(tone)
    expect(wrapper.find('use').attributes('href')).toBe(icon)
    expect(wrapper.find('svg').attributes('aria-hidden')).toBe('true')
    expect(wrapper.text()).toBe('Uruchomiono: zakres dat.')
  })

  it('takes the class of its parent on the root', () => {
    const wrapper = mount(StatusMessage, { props: { tone: 'ok' }, attrs: { class: 'notice' }, slots: { default: 'Gotowe.' } })
    expect(wrapper.classes()).toContain('notice')
    expect(wrapper.classes()).toContain('status-message')
  })
})

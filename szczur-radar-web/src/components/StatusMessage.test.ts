import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import StatusMessage from './StatusMessage.vue'

describe('StatusMessage', () => {
  it.each([
    ['ok', 'status'],
    ['error', 'alert'],
  ] as const)('%s is a paragraph with role %s', (tone, role) => {
    const wrapper = mount(StatusMessage, { props: { tone }, slots: { default: 'Uruchomiono: zakres dat.' } })
    expect(wrapper.element.tagName).toBe('P')
    expect(wrapper.attributes('role')).toBe(role)
    expect(wrapper.classes()).toContain(tone)
    expect(wrapper.text()).toBe('Uruchomiono: zakres dat.')
  })
})

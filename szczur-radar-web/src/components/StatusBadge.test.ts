import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import StatusBadge from './StatusBadge.vue'

describe('StatusBadge', () => {
  it.each(['success', 'warning', 'destructive', 'neutral', 'info'] as const)('%s tone is a span with its text', (tone) => {
    const wrapper = mount(StatusBadge, { props: { tone }, slots: { default: 'częściowo' } })
    expect(wrapper.element.tagName).toBe('SPAN')
    expect(wrapper.attributes('data-tone')).toBe(tone)
    expect(wrapper.text()).toBe('częściowo')
  })

  it('takes the class of its parent on the root', () => {
    const wrapper = mount(StatusBadge, { props: { tone: 'success' }, attrs: { class: 'status' }, slots: { default: 'zakończony' } })
    expect(wrapper.classes()).toContain('status')
  })
})

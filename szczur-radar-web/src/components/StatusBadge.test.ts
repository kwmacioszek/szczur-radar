import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import StatusBadge from './StatusBadge.vue'

describe('StatusBadge', () => {
  it.each(['success', 'warning', 'destructive', 'neutral', 'info'] as const)('%s tone is a span with its text', (tone) => {
    const wrapper = mount(StatusBadge, { props: { tone }, slots: { default: 'częściowo' } })
    expect(wrapper.element.tagName).toBe('SPAN')
    expect(wrapper.classes()).toContain('status-badge')
    expect(wrapper.attributes('data-tone')).toBe(tone)
    expect(wrapper.text()).toBe('częściowo')
    expect(wrapper.find('svg').exists()).toBe(false)
  })

  it('takes the class of its parent on the root', () => {
    const wrapper = mount(StatusBadge, { props: { tone: 'success' }, attrs: { class: 'status' }, slots: { default: 'zakończony' } })
    expect(wrapper.classes()).toContain('status')
  })

  it.each([
    ['warning', '#i-triangle-alert'],
    ['destructive', '#i-circle-alert'],
  ] as const)('shows the icon of the %s tone on request, hidden from assistive technology', (tone, href) => {
    const wrapper = mount(StatusBadge, { props: { tone, icon: true }, slots: { default: 'wymaga uwagi' } })
    expect(wrapper.find('use').attributes('href')).toBe(href)
    expect(wrapper.find('svg').attributes('aria-hidden')).toBe('true')
    expect(wrapper.text()).toBe('wymaga uwagi')
  })

  it.each(['success', 'neutral', 'info'] as const)('the %s tone has no icon even on request', (tone) => {
    const wrapper = mount(StatusBadge, { props: { tone, icon: true }, slots: { default: 'trwa' } })
    expect(wrapper.find('svg').exists()).toBe(false)
  })
})

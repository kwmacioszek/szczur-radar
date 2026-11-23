import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import EmptyState from './EmptyState.vue'

describe('EmptyState', () => {
  it('shows its icon, a paragraph title and the sentence, with no role', () => {
    const wrapper = mount(EmptyState, {
      props: { icon: 'i-inbox', title: 'Brak ocenionych postów' },
      slots: { default: 'Pojawią się po najbliższej aktualizacji danych.' },
    })
    expect(wrapper.find('use').attributes('href')).toBe('#i-inbox')
    expect(wrapper.find('svg').attributes('aria-hidden')).toBe('true')
    const title = wrapper.find('.state-title')
    expect(title.element.tagName).toBe('P')
    expect(title.text()).toBe('Brak ocenionych postów')
    expect(wrapper.find('p.state-text').text()).toBe('Pojawią się po najbliższej aktualizacji danych.')
    expect(wrapper.attributes('role')).toBeUndefined()
    expect(wrapper.find('button').exists()).toBe(false)
  })

  it('takes a level-2 heading as its title, with an id for the labelling of the area', () => {
    const wrapper = mount(EmptyState, {
      props: { icon: 'i-search', title: 'Brak postów dla tych filtrów', titleTag: 'h2', titleId: 'empty-title' },
    })
    const title = wrapper.find('.state-title')
    expect(title.element.tagName).toBe('H2')
    expect(title.attributes('id')).toBe('empty-title')
  })

  it('has no id on a title that was given none, and no sentence without a slot', () => {
    const wrapper = mount(EmptyState, { props: { icon: 'i-circle-check', title: 'Brak dni bez danych' } })
    expect(wrapper.find('.state-title').attributes('id')).toBeUndefined()
    expect(wrapper.find('.state-text').exists()).toBe(false)
  })

  it('shows the action after the sentence', () => {
    const wrapper = mount(EmptyState, {
      props: { icon: 'i-search', title: 'Brak postów dla tych filtrów' },
      slots: { default: 'Zmień filtr.', action: '<button type="button">Wyczyść filtry</button>' },
    })
    const action = wrapper.find('.state-action')
    expect(action.find('button').text()).toBe('Wyczyść filtry')
    const [last, beforeLast] = [...wrapper.element.children].reverse()
    expect(last.classList.contains('state-action')).toBe(true)
    expect(beforeLast.classList.contains('state-text')).toBe(true)
  })
})

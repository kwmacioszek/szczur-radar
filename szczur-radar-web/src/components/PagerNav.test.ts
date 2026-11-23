import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import PagerNav from './PagerNav.vue'

function buttons(wrapper: ReturnType<typeof mount>) {
  return wrapper.findAll('button')
}

describe('PagerNav', () => {
  it('is a navigation "Strony" with the newer button, the position and the older button', () => {
    const wrapper = mount(PagerNav, { props: { page: 1, pages: 7 } })
    expect(wrapper.element.tagName).toBe('NAV')
    expect(wrapper.attributes('aria-label')).toBe('Strony')
    const [previous, next] = buttons(wrapper)
    expect(previous.text()).toBe('Nowsze')
    expect(next.text()).toBe('Starsze')
    expect(wrapper.find('.position').text()).toBe('Strona 2 z 7')
    expect(previous.attributes('disabled')).toBeUndefined()
    expect(next.attributes('disabled')).toBeUndefined()
  })

  it('has the arrows on the right sides: left before "Nowsze", right after "Starsze"', () => {
    const wrapper = mount(PagerNav, { props: { page: 1, pages: 7 } })
    const [previous, next] = buttons(wrapper)
    expect(previous.find('use').attributes('href')).toBe('#i-chevron-left')
    expect(previous.element.firstElementChild?.tagName.toLowerCase()).toBe('svg')
    expect(next.find('use').attributes('href')).toBe('#i-chevron-right')
    expect(next.element.lastElementChild?.tagName.toLowerCase()).toBe('svg')
  })

  it('takes the words "Poprzednie" and "Następne" for a list in an order', () => {
    const wrapper = mount(PagerNav, { props: { page: 0, pages: 3, labels: 'order' } })
    const [previous, next] = buttons(wrapper)
    expect(previous.text()).toBe('Poprzednie')
    expect(next.text()).toBe('Następne')
  })

  it('disables the previous button on the first page', () => {
    const [previous, next] = buttons(mount(PagerNav, { props: { page: 0, pages: 7 } }))
    expect(previous.attributes('disabled')).toBeDefined()
    expect(next.attributes('disabled')).toBeUndefined()
  })

  it('disables the next button on the last page', () => {
    const wrapper = mount(PagerNav, { props: { page: 6, pages: 7 } })
    const [previous, next] = buttons(wrapper)
    expect(previous.attributes('disabled')).toBeUndefined()
    expect(next.attributes('disabled')).toBeDefined()
    expect(wrapper.find('.position').text()).toBe('Strona 7 z 7')
  })

  it.each([1, 0])('draws nothing for %i page(s)', (pages) => {
    const wrapper = mount(PagerNav, { props: { page: 0, pages } })
    expect(wrapper.find('nav').exists()).toBe(false)
    expect(wrapper.find('button').exists()).toBe(false)
    expect(wrapper.text()).toBe('')
  })

  it('shows the button of the direction being loaded as busy and leaves the other one', () => {
    const wrapper = mount(PagerNav, { props: { page: 2, pages: 7, busy: 'next' } })
    const [previous, next] = buttons(wrapper)
    expect(next.attributes('aria-busy')).toBe('true')
    expect(next.text()).toBe('Wczytywanie…')
    expect(next.find('.spinner').exists()).toBe(true)
    expect(next.find('use').exists()).toBe(false)
    expect(previous.attributes('aria-busy')).toBeUndefined()
    expect(previous.attributes('disabled')).toBeUndefined()
    expect(previous.text()).toBe('Nowsze')

    const back = buttons(mount(PagerNav, { props: { page: 2, pages: 7, busy: 'previous' } }))
    expect(back[0].text()).toBe('Wczytywanie…')
    expect(back[1].attributes('disabled')).toBeUndefined()
  })

  it('has no busy button when nothing loads', () => {
    const wrapper = mount(PagerNav, { props: { page: 2, pages: 7, busy: null } })
    expect(wrapper.find('[aria-busy]').exists()).toBe(false)
  })

  it('emits previous and next on the clicks of its buttons, and nothing from disabled ones', async () => {
    const wrapper = mount(PagerNav, { props: { page: 1, pages: 3 } })
    const [previous, next] = buttons(wrapper)
    await previous.trigger('click')
    await next.trigger('click')
    expect(wrapper.emitted('previous')).toHaveLength(1)
    expect(wrapper.emitted('next')).toHaveLength(1)

    const first = mount(PagerNav, { props: { page: 0, pages: 3 } })
    await buttons(first)[0].trigger('click')
    expect(first.emitted('previous')).toBeUndefined()
  })
})

import { enableAutoUnmount, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import PageHeader from './PageHeader.vue'

enableAutoUnmount(afterEach)

describe('PageHeader', () => {
  it('has the title as its one h1', () => {
    const wrapper = mount(PageHeader, { props: { title: 'Posty' } })
    const headings = wrapper.findAll('h1')
    expect(headings).toHaveLength(1)
    expect(headings[0].text()).toBe('Posty')
    expect(wrapper.find('h2').exists()).toBe(false)
  })

  it('gives the h1 the id the view labels its section with', () => {
    const wrapper = mount(PageHeader, { props: { title: 'Posty', headingId: 'feed-title' } })
    expect(wrapper.get('h1').attributes('id')).toBe('feed-title')
  })

  it('shows the subtitle only when there is one', () => {
    expect(mount(PageHeader, { props: { title: 'Posty' } }).find('.page-sub').exists()).toBe(false)
    const wrapper = mount(PageHeader, { props: { title: 'Posty', subtitle: 'wtorek, 20 października 2026' } })
    expect(wrapper.get('.page-sub').text()).toBe('wtorek, 20 października 2026')
  })

  it('is focusable by script only and moves the focus to the h1 on focus()', () => {
    const wrapper = mount(PageHeader, { props: { title: 'Posty' }, attachTo: document.body })
    expect(wrapper.get('h1').attributes('tabindex')).toBe('-1')
    expect(document.activeElement).not.toBe(wrapper.get('h1').element)

    wrapper.vm.focus()
    expect(document.activeElement).toBe(wrapper.get('h1').element)
  })

  it('puts the status slot right after the h1, in the same row', () => {
    const wrapper = mount(PageHeader, {
      props: { title: 'Przebieg' },
      slots: { status: '<span class="badge">częściowo</span>' },
    })
    const row = wrapper.get('.title-row')
    expect([...row.element.children].map((child) => child.tagName.toLowerCase())).toEqual(['h1', 'span'])
    expect(row.get('.badge').text()).toBe('częściowo')
  })

  it("sets a text's own title as a reading title and a page's name as a page title", () => {
    expect(mount(PageHeader, { props: { title: 'Posty' } }).get('.page-head').classes()).not.toContain('reading')
    const wrapper = mount(PageHeader, { props: { title: 'Orlen ogłasza buyback', size: 'reading' } })
    expect(wrapper.get('.page-head').classes()).toContain('reading')
  })

  it('shows the tools slot beside the title and nothing when there are none', () => {
    expect(mount(PageHeader, { props: { title: 'Posty' } }).find('.view-tools').exists()).toBe(false)
    const wrapper = mount(PageHeader, { props: { title: 'Posty' }, slots: { tools: '<button type="button">Dzień</button>' } })
    expect(wrapper.get('.view-tools').text()).toBe('Dzień')
  })
})

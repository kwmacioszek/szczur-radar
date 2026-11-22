import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import AuthLayout from './AuthLayout.vue'
import authLayout from './AuthLayout.vue?raw'

function mountLayout(props: { title: string; busy?: boolean } = { title: 'Logowanie' }) {
  return mount(AuthLayout, { props, slots: { default: '<form><button type="submit">Wyślij</button></form>' } })
}

describe('AuthLayout', () => {
  it('has one main with one h1 carrying the title, and the section is named by it', () => {
    const wrapper = mountLayout({ title: 'Ustawienie hasła' })
    expect(wrapper.findAll('main')).toHaveLength(1)
    const headings = wrapper.findAll('h1')
    expect(headings).toHaveLength(1)
    expect(headings[0].text()).toBe('Ustawienie hasła')
    const section = wrapper.find('main section')
    expect(section.attributes('aria-labelledby')).toBe(headings[0].attributes('id'))
  })

  it('shows the brand in the main and the theme switch outside it', () => {
    const wrapper = mountLayout()
    expect(wrapper.find('main').text()).toContain('Szczur Radar')
    const toggle = wrapper.find('button[aria-label="Ciemny motyw"]')
    expect(toggle.exists()).toBe(true)
    expect(wrapper.find('main').find('button[aria-label="Ciemny motyw"]').exists()).toBe(false)
  })

  it('renders the slot inside the section, after the heading', () => {
    const section = mountLayout().find('main section')
    expect(section.find('form button[type="submit"]').text()).toBe('Wyślij')
    expect(section.element.firstElementChild?.tagName.toLowerCase()).toBe('h1')
  })

  it('marks the section aria-busy only while busy', async () => {
    const wrapper = mountLayout()
    expect(wrapper.find('section').attributes('aria-busy')).toBeUndefined()
    await wrapper.setProps({ busy: true })
    expect(wrapper.find('section').attributes('aria-busy')).toBe('true')
  })

  // jsdom does no layout: the full-width rule of the submit button is read from the component's own style.
  it('widens the submit button of a form in the slot through its own style', () => {
    expect(authLayout).toContain(":deep(form > button[type='submit'])")
    expect(authLayout).toMatch(/:deep\(form > button\[type='submit'\]\)\s*\{[^}]*width:\s*100%/)
  })

  it('drops the margin of a paragraph directly in the card or in its form, so the gap alone spaces it', () => {
    expect(authLayout).toMatch(/\.auth > :deep\(p\),\s*\.auth :deep\(form > p\)\s*\{[^}]*margin:\s*0/)
  })
})

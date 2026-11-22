import { enableAutoUnmount, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { Freshness } from '../api/freshness'
import AppBar from './AppBar.vue'

enableAutoUnmount(afterEach)

const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/', name: 'home', component: { render: () => null } },
    { path: '/admin', name: 'admin', component: { render: () => null } },
  ],
})

const ITEMS = [
  { label: 'Posty', to: { name: 'home' }, current: true },
  { label: 'Panel admina', to: { name: 'admin' }, current: false },
]

const FRESHNESS: Freshness = {
  sources: [
    { source: 'wykop', lastUpdatedAt: '2026-10-13T03:12:00Z' },
    { source: 'reddit', lastUpdatedAt: '2026-10-11T03:12:00Z' },
  ],
}

function mountBar(props: Partial<InstanceType<typeof AppBar>['$props']> = {}, slots: Record<string, string> = {}) {
  return mount(AppBar, {
    props: { items: ITEMS, freshness: FRESHNESS, email: 'admin@example.com', loggingOut: false, ...props },
    slots,
    global: { plugins: [router] },
  })
}

describe('AppBar', () => {
  it('is the banner with the name of the application leading to the home page', () => {
    const wrapper = mountBar()
    expect(wrapper.element.tagName).toBe('HEADER')
    const brand = wrapper.get('a.brand')
    expect(brand.text()).toBe('Szczur Radar')
    expect(brand.attributes('href')).toBe('/')
    // The mark is a decoration: the name beside it is the link's.
    expect(brand.get('svg').attributes('aria-hidden')).toBe('true')
  })

  it('lists the links of its items in the main navigation, in order', () => {
    const links = mountBar().get('nav[aria-label="Główna"]').findAll('a')
    expect(links.map((link) => link.text())).toEqual(['Posty', 'Panel admina'])
    expect(links.map((link) => link.attributes('href'))).toEqual(['/', '/admin'])
  })

  it('marks only the current item with aria-current, also where the address is not exactly its own', () => {
    const wrapper = mountBar({ items: [{ ...ITEMS[0], current: false }, { ...ITEMS[1], current: true }] })
    const [posts, admin] = wrapper.get('nav').findAll('a')
    expect(posts.attributes('aria-current')).toBeUndefined()
    expect(admin.attributes('aria-current')).toBe('page')
    expect(wrapper.findAll('[aria-current]')).toHaveLength(1)
  })

  it('shows a line per source with its day and hour, under "Dane:"', () => {
    const wrapper = mountBar()
    const label = wrapper.get('.freshness')
    expect(label.get('span').text()).toBe('Dane:')
    expect(label.findAll('.freshness-list > span').map((row) => row.text())).toEqual([
      'Wykop 13.10.2026, 05:12',
      'Reddit 11.10.2026, 05:12',
    ])
    const times = label.findAll('time')
    expect(times.map((time) => time.attributes('datetime'))).toEqual(['2026-10-13T03:12:00Z', '2026-10-11T03:12:00Z'])
    expect(times.map((time) => time.text())).toEqual(['13.10.2026, 05:12', '11.10.2026, 05:12'])
    // A page's state is announced by the page, not by the date in the bar.
    expect(wrapper.find('[role="status"]').exists()).toBe(false)
  })

  it('says that nothing has been updated yet when no source has an update', () => {
    const wrapper = mountBar({ freshness: { sources: [] } })
    expect(wrapper.get('.freshness').text()).toBe('Dane nie były jeszcze aktualizowane.')
    expect(wrapper.find('time').exists()).toBe(false)
  })

  it('shows no label when the date could not be read', () => {
    const wrapper = mountBar({ freshness: null })
    expect(wrapper.find('.freshness').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('Dane')
  })

  it('shows the e-mail, the theme switch and the sign-out button', () => {
    const wrapper = mountBar()
    expect(wrapper.text()).toContain('admin@example.com')
    expect(wrapper.find('button[aria-label="Ciemny motyw"]').exists()).toBe(true)
    expect(wrapper.findAll('button').map((button) => button.text())).toContain('Wyloguj')
  })

  it('leaves out the e-mail when there is none', () => {
    expect(mountBar({ email: null }).find('.who').exists()).toBe(false)
  })

  it('asks to sign out on a click of "Wyloguj"', async () => {
    const wrapper = mountBar()
    await wrapper.findAll('button').find((button) => button.text() === 'Wyloguj')!.trigger('click')
    expect(wrapper.emitted('logout')).toHaveLength(1)
  })

  it('marks the sign-out button busy and does not ask again while signing out', async () => {
    const wrapper = mountBar({ loggingOut: true })
    const button = wrapper.findAll('button').find((candidate) => candidate.text() === 'Wyloguj')!
    expect(button.attributes('aria-busy')).toBe('true')
    expect(button.attributes('disabled')).toBeDefined()
    await button.trigger('click')
    expect(wrapper.emitted('logout')).toBeUndefined()
  })

  it('puts the content of the notifications slot among the controls, before the theme switch', () => {
    const wrapper = mountBar({}, { notifications: '<button type="button">Dzwonek</button>' })
    const names = wrapper.findAll('.actions button').map((button) => button.attributes('aria-label') ?? button.text())
    expect(names).toEqual(['Dzwonek', 'Ciemny motyw', 'Wyloguj'])
  })
})

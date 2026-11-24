import { enableAutoUnmount, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import AdminPartsNav, { type AdminPart } from './AdminPartsNav.vue'

enableAutoUnmount(afterEach)

const router = createRouter({
  history: createMemoryHistory(),
  routes: [{ path: '/:pathMatch(.*)*', component: { render: () => null } }],
})

function parts(failedCount?: number | null): AdminPart[] {
  return [
    { label: 'Przebiegi', to: '/admin', current: true },
    { label: 'Jakość ocen', to: '/admin/quality', current: false },
    { label: 'Nieudane posty', to: '/admin/posts', current: false, count: failedCount },
  ]
}

function mountNav(items: AdminPart[] = parts(7)) {
  return mount(AdminPartsNav, { props: { label: 'Panel admina', items }, global: { plugins: [router] } })
}

describe('AdminPartsNav', () => {
  it('is a navigation named by its label with a link to each part, in order', () => {
    const wrapper = mountNav()
    expect(wrapper.element.tagName).toBe('NAV')
    expect(wrapper.attributes('aria-label')).toBe('Panel admina')
    const links = wrapper.findAll('a')
    expect(links.map((link) => link.text())).toEqual(['Przebiegi', 'Jakość ocen', 'Nieudane posty 7'])
    expect(links.map((link) => link.attributes('href'))).toEqual(['/admin', '/admin/quality', '/admin/posts'])
  })

  it('marks only the current part with aria-current="page"', () => {
    const links = mountNav().findAll('a')
    expect(links.map((link) => link.attributes('aria-current'))).toEqual(['page', undefined, undefined])
  })

  it('marks the part the caller says, not the exact address', () => {
    const items = parts(7).map((item) => ({ ...item, current: item.label === 'Nieudane posty' }))
    const links = mountNav(items).findAll('a')
    expect(links.map((link) => link.attributes('aria-current'))).toEqual([undefined, undefined, 'page'])
  })

  it('shows a count above 0 as a warning badge in its link', () => {
    const badges = mountNav().findAll('.status-badge')
    expect(badges).toHaveLength(1)
    expect(badges[0].attributes('data-tone')).toBe('warning')
    expect(badges[0].text()).toBe('7')
  })

  it.each([0, null, undefined])('shows no badge for a count of %s', (count) => {
    const wrapper = mountNav(parts(count))
    expect(wrapper.find('.status-badge').exists()).toBe(false)
    expect(wrapper.findAll('a').map((link) => link.text())).toEqual(['Przebiegi', 'Jakość ocen', 'Nieudane posty'])
  })
})

import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import DataTable from './DataTable.vue'

const ROWS = `
  <thead><tr><th scope="col">Przebieg</th><th scope="col" class="num">Ocenione</th></tr></thead>
  <tbody>
    <tr class="is-selected"><th scope="row">20.10.2026, 02:30</th><td class="num">165</td></tr>
    <tr><th scope="row">19.10.2026, 02:30</th><td class="num zero">0</td></tr>
  </tbody>`

function mountTable() {
  return mount(DataTable, { props: { caption: 'Ostatnie przebiegi, od najnowszego' }, slots: { default: ROWS } })
}

describe('DataTable', () => {
  it('has the caption as the table\'s visually hidden <caption>', () => {
    const caption = mountTable().get('table > caption')
    expect(caption.text()).toBe('Ostatnie przebiegi, od najnowszego')
    expect(caption.classes()).toContain('visually-hidden')
  })

  it('frames the table in a focusable region named by the caption, so the keyboard can scroll it', () => {
    const wrapper = mountTable()
    const frame = wrapper.get('.data-table')
    expect(frame.attributes('role')).toBe('region')
    expect(frame.attributes('tabindex')).toBe('0')
    const name = frame.attributes('aria-labelledby')
    expect(name).toBeTruthy()
    expect(wrapper.get('caption').attributes('id')).toBe(name)
  })

  it('gives each table on a page its own caption id', () => {
    const page = mount({
      components: { DataTable },
      template: `<DataTable caption="Pierwsza">${ROWS}</DataTable><DataTable caption="Druga">${ROWS}</DataTable>`,
    })
    const ids = page.findAll('caption').map((caption) => caption.attributes('id'))
    expect(ids).toHaveLength(2)
    expect(ids[0]).not.toBe(ids[1])
    expect(page.findAll('.data-table').map((frame) => frame.attributes('aria-labelledby'))).toEqual(ids)
  })

  it('renders the caller\'s header and rows with their classes', () => {
    const table = mountTable().get('table')
    expect(table.findAll('thead th').map((cell) => cell.text())).toEqual(['Przebieg', 'Ocenione'])
    const rows = table.findAll('tbody tr')
    expect(rows).toHaveLength(2)
    expect(rows[0].classes()).toContain('is-selected')
    expect(rows[1].get('th').attributes('scope')).toBe('row')
    expect(rows[1].get('td').classes()).toEqual(['num', 'zero'])
  })
})

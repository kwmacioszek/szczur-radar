import { enableAutoUnmount, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import type { PostFilter } from '../api/posts'
import { MAX_QUERY_LENGTH } from '../feedFilter'
import FeedFilter from './FeedFilter.vue'

/** `attachTo`: in the document, for focus and for a button that submits its form. */
function mountFilter(filter: PostFilter = {}, attachTo?: HTMLElement) {
  return mount(FeedFilter, { props: { filter }, attachTo })
}

type Wrapper = ReturnType<typeof mountFilter>

function toggle(wrapper: Wrapper) {
  return wrapper.find('button[aria-expanded]')
}

function panel(wrapper: Wrapper) {
  return wrapper.find('[role="group"]')
}

function isOpen(wrapper: Wrapper): boolean {
  return (panel(wrapper).element as HTMLElement).style.display !== 'none'
}

function fields(wrapper: Wrapper) {
  const [kind, direction] = wrapper.findAll('select')
  return { text: wrapper.find('input[type="search"]'), kind: kind!, direction: direction! }
}

function button(wrapper: Wrapper, name: string) {
  const found = wrapper
    .findAll('button')
    .find((candidate) => candidate.text() === name || candidate.attributes('aria-label') === name)
  if (!found) throw new Error(`no button "${name}"`)
  return found
}

async function openPanel(wrapper: Wrapper): Promise<void> {
  await toggle(wrapper).trigger('click')
}

enableAutoUnmount(afterEach)

describe('FeedFilter', () => {
  it('is a search form with the button "Filtry" and, right after it, its closed panel', () => {
    const wrapper = mountFilter()
    const form = wrapper.find('form')
    expect(form.attributes('role')).toBe('search')
    expect(form.attributes('aria-label')).toBe('Filtr postów')
    expect(form.element.firstElementChild).toBe(toggle(wrapper).element)
    expect(toggle(wrapper).element.nextElementSibling).toBe(panel(wrapper).element)
    expect(toggle(wrapper).attributes('type')).toBe('button')
    expect(toggle(wrapper).text()).toBe('Filtry')
    expect(toggle(wrapper).find('use').attributes('href')).toBe('#i-list-filter')
    expect(toggle(wrapper).attributes('aria-expanded')).toBe('false')
    expect(toggle(wrapper).attributes('aria-controls')).toBe(panel(wrapper).attributes('id'))
    expect(isOpen(wrapper)).toBe(false)
  })

  it('counts the applied filter’s keys in the button, never an empty one', () => {
    expect(toggle(mountFilter({ q: 'orlen', direction: 'bullish' })).text()).toBe('Filtry: 2')
    expect(toggle(mountFilter({ q: 'orlen', kind: 'company', direction: 'bullish' })).text()).toBe('Filtry: 3')
    expect(toggle(mountFilter({ q: '' })).text()).toBe('Filtry')
  })

  it('opens a panel named "Filtry" with its heading, the three labelled fields and the buttons', async () => {
    const wrapper = mountFilter()
    await openPanel(wrapper)
    expect(isOpen(wrapper)).toBe(true)
    expect(toggle(wrapper).attributes('aria-expanded')).toBe('true')
    const group = panel(wrapper)
    const title = wrapper.find(`#${group.attributes('aria-labelledby')}`)
    expect(title.text()).toBe('Filtry')
    expect(group.findAll('label').map((label) => label.text())).toEqual(['Spółka lub temat', 'Rodzaj celu', 'Stanowisko'])
    for (const label of group.findAll('label')) {
      expect(group.find(`#${label.attributes('for')}`).exists()).toBe(true)
    }
    expect(fields(wrapper).text.attributes('maxlength')).toBe(String(MAX_QUERY_LENGTH))
    expect(group.findAll('button').map((candidate) => candidate.attributes('aria-label') ?? candidate.text())).toEqual([
      'Zamknij filtry',
      'Wyczyść filtry',
      'Filtruj',
    ])
    expect(button(wrapper, 'Filtruj').attributes('type')).toBe('submit')
    expect(button(wrapper, 'Wyczyść filtry').attributes('type')).toBe('button')
  })

  it('offers any kind and the six kinds, any stance and the six directions, in Polish', () => {
    const { kind, direction } = fields(mountFilter())
    expect(kind.findAll('option').map((option) => [option.attributes('value'), option.text()])).toEqual([
      ['', 'dowolny'],
      ['company', 'spółka'],
      ['asset', 'aktywo'],
      ['sector', 'sektor'],
      ['macro_topic', 'makro i rynek'],
      ['other', 'inne'],
      ['unknown', 'nieokreślony'],
    ])
    expect(direction.findAll('option').map((option) => [option.attributes('value'), option.text()])).toEqual([
      ['', 'dowolne'],
      ['bullish', 'wzrostowo'],
      ['bearish', 'spadkowo'],
      ['neutral', 'neutralnie'],
      ['mixed', 'mieszanie'],
      ['not_expressed', 'bez stanowiska'],
      ['unclear', 'niejasno'],
    ])
  })

  it('applies the trimmed text and the chosen values, without empty keys, closes and focuses "Filtry"', async () => {
    const wrapper = mountFilter({}, document.body)
    await openPanel(wrapper)
    const { text, direction } = fields(wrapper)
    await text.setValue('  CDR  ')
    await direction.setValue('bullish')
    await button(wrapper, 'Filtruj').trigger('click')
    expect(wrapper.emitted('apply')).toEqual([[{ q: 'CDR', direction: 'bullish' }]])
    expect(isOpen(wrapper)).toBe(false)
    expect(document.activeElement).toBe(toggle(wrapper).element)
  })

  it('applies an empty filter when nothing is chosen', async () => {
    const wrapper = mountFilter()
    await openPanel(wrapper)
    await fields(wrapper).text.setValue('   ')
    await wrapper.find('form').trigger('submit')
    expect(wrapper.emitted('apply')).toEqual([[{}]])
  })

  it('applies on Enter in the text field', async () => {
    const wrapper = mountFilter({}, document.body)
    await openPanel(wrapper)
    const { text, kind } = fields(wrapper)
    await text.setValue('orlen')
    await kind.setValue('macro_topic')
    // A form with a submit button submits on Enter in a text field (implicit submission).
    const submit = button(wrapper, 'Filtruj').element as HTMLButtonElement
    ;(text.element as HTMLInputElement).form!.requestSubmit(submit)
    expect(wrapper.emitted('apply')).toEqual([[{ q: 'orlen', kind: 'macro_topic' }]])
  })

  it('"Wyczyść filtry" emits clear, closes the panel and focuses "Filtry"', async () => {
    const wrapper = mountFilter({ kind: 'asset' }, document.body)
    await openPanel(wrapper)
    await button(wrapper, 'Wyczyść filtry').trigger('click')
    expect(wrapper.emitted('clear')).toHaveLength(1)
    expect(wrapper.emitted('apply')).toBeUndefined()
    expect(isOpen(wrapper)).toBe(false)
    expect(document.activeElement).toBe(toggle(wrapper).element)
  })

  it('closes on Escape without applying and gives the focus back to "Filtry"', async () => {
    const wrapper = mountFilter({}, document.body)
    await openPanel(wrapper)
    const { text } = fields(wrapper)
    await text.setValue('orlen')
    ;(text.element as HTMLInputElement).focus()

    text.element.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await wrapper.vm.$nextTick()

    expect(isOpen(wrapper)).toBe(false)
    expect(toggle(wrapper).attributes('aria-expanded')).toBe('false')
    expect(document.activeElement).toBe(toggle(wrapper).element)
    expect(wrapper.emitted('apply')).toBeUndefined()
  })

  it('closes on "Zamknij filtry" without applying and gives the focus back to "Filtry"', async () => {
    const wrapper = mountFilter({}, document.body)
    await openPanel(wrapper)
    await fields(wrapper).text.setValue('orlen')
    await button(wrapper, 'Zamknij filtry').trigger('click')
    expect(isOpen(wrapper)).toBe(false)
    expect(document.activeElement).toBe(toggle(wrapper).element)
    expect(wrapper.emitted('apply')).toBeUndefined()
  })

  it('closes on a click outside the form without applying', async () => {
    const wrapper = mountFilter({}, document.body)
    await openPanel(wrapper)
    await fields(wrapper).text.setValue('orlen')
    document.body.click()
    await wrapper.vm.$nextTick()
    expect(isOpen(wrapper)).toBe(false)
    expect(wrapper.emitted('apply')).toBeUndefined()
    // The click on the page itself left the focus nowhere: it goes back to the button.
    expect(document.activeElement).toBe(toggle(wrapper).element)
  })

  it('stays open on a click inside the panel and hears no Escape once closed', async () => {
    const wrapper = mountFilter({}, document.body)
    await openPanel(wrapper)
    ;(fields(wrapper).text.element as HTMLElement).click()
    await wrapper.vm.$nextTick()
    expect(isOpen(wrapper)).toBe(true)

    await toggle(wrapper).trigger('click')
    expect(isOpen(wrapper)).toBe(false)
    const outside = document.createElement('button')
    document.body.append(outside)
    outside.focus()
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    expect(document.activeElement).toBe(outside)
    outside.remove()
  })

  it('sets the fields to the applied filter each time the panel opens, dropping a closed draft', async () => {
    const wrapper = mountFilter({ q: 'cdr', kind: 'company' })
    await openPanel(wrapper)
    const { text, kind, direction } = fields(wrapper)
    expect((text.element as HTMLInputElement).value).toBe('cdr')
    expect((kind.element as HTMLSelectElement).value).toBe('company')
    expect((direction.element as HTMLSelectElement).value).toBe('')

    await text.setValue('xtb')
    await direction.setValue('bearish')
    await button(wrapper, 'Zamknij filtry').trigger('click')
    await openPanel(wrapper)

    expect((text.element as HTMLInputElement).value).toBe('cdr')
    expect((direction.element as HTMLSelectElement).value).toBe('')
  })

  it('fills the fields from the applied filter and follows its changes', async () => {
    const wrapper = mountFilter({ q: 'cdr', kind: 'company', direction: 'bearish' })
    const { text, kind, direction } = fields(wrapper)
    expect((text.element as HTMLInputElement).value).toBe('cdr')
    expect((kind.element as HTMLSelectElement).value).toBe('company')
    expect((direction.element as HTMLSelectElement).value).toBe('bearish')

    await wrapper.setProps({ filter: { q: 'xtb' } })
    expect((text.element as HTMLInputElement).value).toBe('xtb')
    expect((kind.element as HTMLSelectElement).value).toBe('')
    expect((direction.element as HTMLSelectElement).value).toBe('')

    await wrapper.setProps({ filter: {} })
    expect((text.element as HTMLInputElement).value).toBe('')
  })

  it('keeps a draft while open when the applied filter is replaced by an equal one', async () => {
    const wrapper = mountFilter({ q: 'cdr' })
    await openPanel(wrapper)
    const { text } = fields(wrapper)
    await text.setValue('xt')
    await wrapper.setProps({ filter: { q: 'cdr' } })
    expect((text.element as HTMLInputElement).value).toBe('xt')
  })
})

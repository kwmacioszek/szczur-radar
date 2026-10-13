import { enableAutoUnmount, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import type { PostFilter } from '../api/posts'
import FeedFilter from './FeedFilter.vue'

function mountFilter(filter: PostFilter = {}, attachTo?: HTMLElement) {
  return mount(FeedFilter, { props: { filter }, attachTo })
}

function fields(wrapper: ReturnType<typeof mountFilter>) {
  const [kind, direction] = wrapper.findAll('select')
  return { text: wrapper.find('input[type="search"]'), kind: kind!, direction: direction! }
}

function buttonTexts(wrapper: ReturnType<typeof mountFilter>): string[] {
  return wrapper.findAll('button').map((button) => button.text())
}

enableAutoUnmount(afterEach)

describe('FeedFilter', () => {
  it('is a search form with a labelled text field and two labelled choices', () => {
    const wrapper = mountFilter()
    const form = wrapper.find('form')
    expect(form.attributes('role')).toBe('search')
    expect(form.attributes('aria-label')).toBe('Filtr postów')
    const labels = wrapper.findAll('label')
    expect(labels.map((label) => label.element.firstChild?.textContent?.trim())).toEqual([
      'Spółka lub temat',
      'Rodzaj celu',
      'Stanowisko',
    ])
    expect(labels[0]!.find('input[type="search"]').attributes('maxlength')).toBe('100')
    expect(labels[1]!.find('select').exists()).toBe(true)
    expect(labels[2]!.find('select').exists()).toBe(true)
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

  it('applies the trimmed text and the chosen values, without empty keys', async () => {
    // In the document: a button submits only a connected form.
    const wrapper = mountFilter({}, document.body)
    const { text, direction } = fields(wrapper)
    await text.setValue('  CDR  ')
    await direction.setValue('bullish')
    await wrapper.find('button[type="submit"]').trigger('click')
    expect(wrapper.emitted('apply')).toEqual([[{ q: 'CDR', direction: 'bullish' }]])
    expect((text.element as HTMLInputElement).value).toBe('CDR')
  })

  it('applies an empty filter when nothing is chosen', async () => {
    const wrapper = mountFilter()
    await fields(wrapper).text.setValue('   ')
    await wrapper.find('form').trigger('submit')
    expect(wrapper.emitted('apply')).toEqual([[{}]])
  })

  it('applies on Enter in the text field', async () => {
    const wrapper = mountFilter({}, document.body)
    const { text, kind } = fields(wrapper)
    await text.setValue('orlen')
    await kind.setValue('macro_topic')
    // A form with a submit button submits on Enter in a text field (implicit submission).
    const submit = wrapper.find('button[type="submit"]').element as HTMLButtonElement
    ;(text.element as HTMLInputElement).form!.requestSubmit(submit)
    expect(wrapper.emitted('apply')).toEqual([[{ q: 'orlen', kind: 'macro_topic' }]])
  })

  it('shows "Wyczyść filtry" only for an active filter and emits clear', async () => {
    expect(buttonTexts(mountFilter())).toEqual(['Filtruj'])
    const wrapper = mountFilter({ kind: 'asset' })
    expect(buttonTexts(wrapper)).toEqual(['Filtruj', 'Wyczyść filtry'])
    const clear = wrapper.findAll('button').find((button) => button.text() === 'Wyczyść filtry')!
    expect(clear.attributes('type')).toBe('button')
    await clear.trigger('click')
    expect(wrapper.emitted('clear')).toHaveLength(1)
    expect(wrapper.emitted('apply')).toBeUndefined()
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

  it('keeps a draft when the applied filter is replaced by an equal one', async () => {
    const wrapper = mountFilter({ q: 'cdr' })
    const { text } = fields(wrapper)
    await text.setValue('xt')
    await wrapper.setProps({ filter: { q: 'cdr' } })
    expect((text.element as HTMLInputElement).value).toBe('xt')
  })
})

import { enableAutoUnmount, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import type { PostFilter } from '../api/posts'
import FilterChips from './FilterChips.vue'

function mountChips(filter: PostFilter, attachTo?: HTMLElement) {
  return mount(FilterChips, { props: { filter }, attachTo })
}

enableAutoUnmount(afterEach)

describe('FilterChips', () => {
  it('draws nothing without a filter', () => {
    expect(mountChips({}).find('ul').exists()).toBe(false)
    expect(mountChips({ q: '' }).find('ul').exists()).toBe(false)
  })

  it('is the list "Włączone filtry": a marker per key in the form’s order, then "Wyczyść filtry"', () => {
    const wrapper = mountChips({ direction: 'bullish', kind: 'macro_topic', q: 'Orlen' })
    const list = wrapper.find('ul')
    expect(list.attributes('aria-label')).toBe('Włączone filtry')
    const buttons = list.findAll('li > button')
    expect(buttons.map((button) => button.text())).toEqual([
      'Cel: Orlen',
      'Rodzaj: makro i rynek',
      'Stanowisko: wzrostowo',
      'Wyczyść filtry',
    ])
    expect(buttons.map((button) => button.attributes('aria-label'))).toEqual([
      'Usuń filtr: Cel: Orlen',
      'Usuń filtr: Rodzaj: makro i rynek',
      'Usuń filtr: Stanowisko: wzrostowo',
      undefined,
    ])
    for (const button of buttons) expect(button.attributes('type')).toBe('button')
    for (const button of buttons.slice(0, 3)) expect(button.find('use').attributes('href')).toBe('#i-x')
  })

  it('emits the key of the marker that is taken off, and clear for "Wyczyść filtry"', async () => {
    const wrapper = mountChips({ q: 'cdr', direction: 'bearish' })
    const [target, stance, clear] = wrapper.findAll('li > button')
    await stance!.trigger('click')
    await target!.trigger('click')
    expect(wrapper.emitted('remove')).toEqual([['direction'], ['q']])
    expect(wrapper.emitted('clear')).toBeUndefined()

    await clear!.trigger('click')
    expect(wrapper.emitted('clear')).toHaveLength(1)
  })

  it('puts the focus on the marker of a key, and says when there is none', () => {
    const wrapper = mountChips({ q: 'cdr', kind: 'asset' }, document.body)
    expect(wrapper.vm.focus('kind')).toBe(true)
    expect(document.activeElement?.getAttribute('aria-label')).toBe('Usuń filtr: Rodzaj: aktywo')
    expect(wrapper.vm.focus('direction')).toBe(false)
  })
})

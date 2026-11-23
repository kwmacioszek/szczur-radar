import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import LoadingState from './LoadingState.vue'

describe('LoadingState', () => {
  it('tells assistive technology "Wczytywanie…" and has no role or busy mark of its own', () => {
    const wrapper = mount(LoadingState, { props: { shape: 'rows' } })
    expect(wrapper.find('.visually-hidden').text()).toBe('Wczytywanie…')
    expect(wrapper.text()).toBe('Wczytywanie…')
    expect(wrapper.attributes('role')).toBeUndefined()
    expect(wrapper.attributes('aria-busy')).toBeUndefined()
  })

  it('takes another text for assistive technology', () => {
    const wrapper = mount(LoadingState, { props: { shape: 'lines', label: 'Wczytywanie postów' } })
    expect(wrapper.find('.visually-hidden').text()).toBe('Wczytywanie postów')
  })

  it('draws six rows of three bars by default and as many rows as asked', () => {
    const wrapper = mount(LoadingState, { props: { shape: 'rows' } })
    expect(wrapper.classes()).toContain('rows')
    expect(wrapper.findAll('.skeleton-row')).toHaveLength(6)
    expect(wrapper.findAll('.skeleton-row')[0].findAll('.skeleton')).toHaveLength(3)

    const three = mount(LoadingState, { props: { shape: 'rows', count: 3 } })
    expect(three.findAll('.skeleton-row')).toHaveLength(3)
    const widths = three.findAll('.skeleton-row')[0].findAll('.skeleton').map((bar) => bar.attributes('style'))
    expect(widths).toEqual(['width: 70%;', 'width: 85%;', 'width: 100%;'])

    expect(mount(LoadingState, { props: { shape: 'rows', count: 8 } }).findAll('.skeleton-row')).toHaveLength(8)
  })

  it('draws two lines of 70% and 50% by default, and as many as asked, in turn', () => {
    const wrapper = mount(LoadingState, { props: { shape: 'lines' } })
    expect(wrapper.classes()).toContain('lines')
    expect(wrapper.findAll('.skeleton').map((bar) => bar.attributes('style'))).toEqual(['width: 70%;', 'width: 50%;'])

    const four = mount(LoadingState, { props: { shape: 'lines', count: 4 } })
    expect(four.findAll('.skeleton').map((bar) => bar.attributes('style'))).toEqual([
      'width: 70%;',
      'width: 50%;',
      'width: 70%;',
      'width: 50%;',
    ])
  })

  it('draws a form as a row of two bars the height of a field and a shorter bar', () => {
    const wrapper = mount(LoadingState, { props: { shape: 'form' } })
    expect(wrapper.classes()).toContain('form')
    expect(wrapper.findAll('.form-row .skeleton.control')).toHaveLength(2)
    expect(wrapper.findAll('.skeleton')).toHaveLength(3)
    expect(wrapper.findAll('.skeleton')[2].attributes('style')).toBe('width: 40%;')
  })

  it('draws the bars as empty elements without a role', () => {
    const wrapper = mount(LoadingState, { props: { shape: 'rows', count: 2 } })
    for (const bar of wrapper.findAll('.skeleton')) {
      expect(bar.text()).toBe('')
      expect(bar.attributes('role')).toBeUndefined()
    }
  })
})

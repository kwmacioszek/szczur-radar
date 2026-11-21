import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import type { Direction, Stance } from '../api/posts'
import StanceText from './StanceText.vue'

function mountStance(stance: Stance, props: { tag?: 'span' | 'dd'; target?: string; kind?: string } = {}) {
  return mount(StanceText, { props: { stance, ...props } })
}

/** As the server allows (table §7.2 of the contract): a directional stance has an assertiveness (but mixed) and 1–4 claims. */
function stanceOf(direction: Direction, assertiveness: Stance['assertiveness'] = 'unhedged'): Stance {
  const directional = ['bullish', 'bearish', 'neutral', 'mixed'].includes(direction)
  return {
    direction,
    assertiveness: directional && direction !== 'mixed' ? assertiveness : null,
    claim_ids: directional ? ['c1'] : [],
  }
}

describe('StanceText', () => {
  it('adds "(ostrożnie)" to a hedged stance only', () => {
    expect(mountStance({ direction: 'bullish', assertiveness: 'hedged', claim_ids: ['c1'] }).text()).toBe('wzrostowo (ostrożnie)')
    expect(mountStance({ direction: 'bearish', assertiveness: 'unhedged', claim_ids: ['c1'] }).text()).toBe('spadkowo')
    expect(mountStance({ direction: 'mixed', assertiveness: null, claim_ids: ['c1'] }).text()).toBe('mieszanie')
  })

  it.each(['bullish', 'bearish', 'neutral', 'mixed', 'not_expressed', 'unclear'] as const)('marks the %s direction with its class', (direction) => {
    expect(mountStance(stanceOf(direction)).classes()).toContain(direction)
  })

  it.each([
    ['bullish', '#s-up'],
    ['bearish', '#s-down'],
    ['neutral', '#s-flat'],
    ['mixed', '#s-mixed'],
    ['not_expressed', '#s-none'],
    ['unclear', '#s-none'],
  ] as const)('draws the sign of the %s direction, hidden from assistive technology', (direction, href) => {
    const wrapper = mountStance(stanceOf(direction))
    expect(wrapper.find('use').attributes('href')).toBe(href)
    expect(wrapper.find('svg').attributes('aria-hidden')).toBe('true')
  })

  it('is a span by default and the given element otherwise', () => {
    const stance = stanceOf('neutral')
    expect(mountStance(stance).element.tagName).toBe('SPAN')
    expect(mountStance(stance, { tag: 'dd' }).element.tagName).toBe('DD')
  })

  it('is the stance alone without a target', () => {
    const wrapper = mountStance(stanceOf('bullish'))
    expect(wrapper.find('.t-name').exists()).toBe(false)
    expect(wrapper.find('.kind').exists()).toBe(false)
  })

  it('names the target first, then its kind, then the stance', () => {
    const wrapper = mountStance(stanceOf('bearish', 'hedged'), { target: 'CD Projekt', kind: 'spółka' })
    expect(wrapper.find('.t-name').text()).toBe('CD Projekt')
    expect(wrapper.find('.kind').text()).toBe('spółka')
    expect(wrapper.text()).toBe('CD Projektspółkaspadkowo (ostrożnie)')
    expect(wrapper.classes()).toContain('bearish')
  })

  it('names the target without its kind', () => {
    const wrapper = mountStance(stanceOf('neutral'), { target: 'złoto' })
    expect(wrapper.find('.kind').exists()).toBe(false)
    expect(wrapper.text()).toBe('złotoneutralnie')
  })
})

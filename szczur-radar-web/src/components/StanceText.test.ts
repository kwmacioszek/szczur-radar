import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import type { Stance } from '../api/posts'
import StanceText from './StanceText.vue'

function mountStance(stance: Stance, tag?: 'span' | 'dd') {
  return mount(StanceText, { props: tag ? { stance, tag } : { stance } })
}

describe('StanceText', () => {
  it('adds "(ostrożnie)" to a hedged stance only', () => {
    expect(mountStance({ direction: 'bullish', assertiveness: 'hedged', claim_ids: ['c1'] }).text()).toBe('wzrostowo (ostrożnie)')
    expect(mountStance({ direction: 'bearish', assertiveness: 'unhedged', claim_ids: ['c1'] }).text()).toBe('spadkowo')
    expect(mountStance({ direction: 'mixed', assertiveness: null, claim_ids: ['c1'] }).text()).toBe('mieszanie')
  })

  it.each(['bullish', 'bearish', 'neutral', 'mixed', 'not_expressed', 'unclear'] as const)('marks the %s direction with its class', (direction) => {
    // As the server allows (table §7.2 of the contract): a directional stance has an assertiveness (but mixed) and 1–4 claims.
    const directional = ['bullish', 'bearish', 'neutral', 'mixed'].includes(direction)
    const wrapper = mountStance({
      direction,
      assertiveness: directional && direction !== 'mixed' ? 'unhedged' : null,
      claim_ids: directional ? ['c1'] : [],
    })
    expect(wrapper.classes()).toContain(direction)
  })

  it('is a span by default and the given element otherwise', () => {
    const stance: Stance = { direction: 'neutral', assertiveness: 'unhedged', claim_ids: ['c1'] }
    expect(mountStance(stance).element.tagName).toBe('SPAN')
    expect(mountStance(stance, 'dd').element.tagName).toBe('DD')
  })
})

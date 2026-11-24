import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import type { AssessmentOutput, Claim, PostItem, Target } from '../api/posts'
import PostAssessment from './PostAssessment.vue'

type Assessment = NonNullable<PostItem['assessment']>

function target(id: string, quote: string, claimIds: string[]): Target {
  return {
    id,
    kind: 'company',
    anchor: { document: 'item', field: 'body', quote },
    stance: { direction: 'bullish', assertiveness: 'unhedged', claim_ids: claimIds },
    reference: 'explicit',
  }
}

function claim(id: string, targetIds: string[], overrides: Partial<Claim> = {}): Claim {
  return {
    id,
    kind: 'investment_thesis',
    attribution: 'author',
    target_ids: targetIds,
    evidence: { document: 'item', field: 'body', quote: `cytat ${id}` },
    statement: `teza ${id}`,
    ...overrides,
  }
}

function assessment(output: Partial<AssessmentOutput> = {}, resultSource: Assessment['resultSource'] = 'model'): Assessment {
  return {
    output: {
      relevance: 'investment_related',
      modifiers: [],
      targets: [target('t1', 'CDR', ['c1']), target('t2', 'PKO', ['c2'])],
      claims: [
        claim('c1', ['t1']),
        claim('c2', ['t1', 't2'], { kind: 'factual_claim', attribution: 'reported' }),
        claim('c3', [], { kind: 'opinion', attribution: 'unclear' }),
      ],
      flags: [],
      ...output,
    },
    resultSource,
    assessedAt: '2026-10-02T08:00:00Z',
  }
}

function mountAssessment(value: Assessment = assessment()) {
  return mount(PostAssessment, { props: { assessment: value } })
}

/** Each target's card: its heading with the statements of its claims. */
function cards(wrapper: ReturnType<typeof mountAssessment>) {
  return wrapper.findAll('article').map((card) => ({
    heading: card.find('h4').text(),
    statements: card.findAll('.statement').map((statement) => statement.element.firstChild?.textContent?.trim()),
  }))
}

describe('PostAssessment', () => {
  it('lists each claim under its target, a claim with two targets under both, in the model order', () => {
    expect(cards(mountAssessment()).slice(0, 2)).toEqual([
      { heading: 'CDR', statements: ['teza c1', 'teza c2'] },
      { heading: 'PKO', statements: ['teza c2'] },
    ])
  })

  it('heads each target with its name at level 4, its kind and its stance', () => {
    const card = mountAssessment().find('article')
    expect(card.find('h4').text()).toBe('CDR')
    expect(card.find('.kind').text()).toBe('spółka')
    expect(card.find('.stance').text()).toBe('wzrostowo')
  })

  it('says a target without claims is only mentioned', () => {
    const wrapper = mountAssessment(assessment({ targets: [target('t1', 'Lotos', [])], claims: [] }))
    const card = wrapper.find('article')
    expect(card.find('h4').text()).toBe('Lotos')
    expect(card.find('.claims').exists()).toBe(false)
    expect(card.find('.note').text()).toBe('Wspomniany, bez twierdzeń.')
    expect(mountAssessment().find('article .note').exists()).toBe(false)
  })

  it('lists the claims without a target under "Tezy ogólne", and only when there are such claims', () => {
    expect(cards(mountAssessment())[2]).toEqual({ heading: 'Tezy ogólne', statements: ['teza c3'] })
    const withoutGeneral = mountAssessment(assessment({ claims: [claim('c1', ['t1']), claim('c2', ['t2'])] }))
    expect(withoutGeneral.text()).not.toContain('Tezy ogólne')
  })

  it('notes after each statement its kind, whose it is when not the author\'s, a quote of the title and the basis of the stance', () => {
    const wrapper = mountAssessment(
      assessment({
        claims: [
          claim('c1', ['t1'], { evidence: { document: 'item', field: 'title', quote: 'cytat c1' } }),
          claim('c2', ['t1', 't2'], { kind: 'factual_claim', attribution: 'reported' }),
          claim('c3', [], { kind: 'opinion', attribution: 'unclear' }),
        ],
      }),
    )
    expect(wrapper.findAll('.claim-note').map((note) => note.text())).toEqual([
      'teza inwestycyjna, z tytułu, podstawa stanowiska',
      'informacja, przytoczone',
      'informacja, przytoczone, podstawa stanowiska',
      'opinia, nie wiadomo czyje',
    ])
  })

  it('shows each claim with its statement and its quote', () => {
    const claims = mountAssessment().findAll('.claim')
    expect(claims[0]!.find('.statement').text()).toBe('teza c1 teza inwestycyjna, podstawa stanowiska')
    expect(claims[0]!.find('blockquote').text()).toBe('cytat c1')
  })

  it('shows each modifier as a neutral badge', () => {
    const wrapper = mountAssessment(assessment({ modifiers: ['humor', 'promotion'] }))
    expect(wrapper.findAll('.modifiers [data-tone="neutral"]').map((badge) => badge.text())).toEqual(['humor', 'promocja'])
    expect(mountAssessment().find('.modifiers').exists()).toBe(false)
  })

  it('shows the flags as neutral badges under "Zastrzeżenia"', () => {
    const wrapper = mountAssessment(assessment({ flags: ['possible_sarcasm', 'missing_context'] }))
    const flags = wrapper.findAll('article').at(-1)!
    expect(flags.find('h4').text()).toBe('Zastrzeżenia')
    expect(flags.findAll('[data-tone="neutral"]').map((badge) => badge.text())).toEqual(['możliwy sarkazm', 'brak kontekstu'])
    expect(mountAssessment().text()).not.toContain('Zastrzeżenia')
  })

  it('says the model named no targets or claims when its assessment is empty', () => {
    const wrapper = mountAssessment(assessment({ targets: [], claims: [] }))
    expect(wrapper.find('.empty').text()).toBe('Model nie wskazał celów ani tez.')
    expect(wrapper.find('.note').exists()).toBe(false)
    expect(mountAssessment().find('.empty').exists()).toBe(false)
  })

  it('shows only the note for a result decided without the model', () => {
    const wrapper = mountAssessment(
      assessment({ relevance: 'unclear', targets: [], claims: [], flags: ['insufficient_content'] }, 'prefilter'),
    )
    expect(wrapper.find('.note').text()).toBe('za mało treści, bez oceny modelu')
    expect(wrapper.find('.empty').exists()).toBe(false)
  })
})

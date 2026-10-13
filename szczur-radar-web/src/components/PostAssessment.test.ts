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

/** Each section's heading with the statements of its claims. */
function sections(wrapper: ReturnType<typeof mountAssessment>) {
  return wrapper.findAll('section').map((section) => ({
    heading: section.find('h3').text(),
    statements: section.findAll('.statement').map((statement) => statement.text()),
  }))
}

describe('PostAssessment', () => {
  it('lists each claim under its target, a claim with two targets under both, in the model order', () => {
    expect(sections(mountAssessment()).slice(0, 2)).toEqual([
      { heading: 'CDR', statements: ['teza c1', 'teza c2'] },
      { heading: 'PKO', statements: ['teza c2'] },
    ])
  })

  it('shows the target kind, reference and stance under its heading', () => {
    const section = mountAssessment().find('section')
    expect(section.find('.target-meta').text()).toBe('spółka · nazwany wprost')
    expect(section.find('.stance').text()).toBe('wzrostowo')
  })

  it('lists the claims without a target under "Tezy ogólne", and only when there are such claims', () => {
    expect(sections(mountAssessment())[2]).toEqual({ heading: 'Tezy ogólne', statements: ['teza c3'] })
    const withoutGeneral = mountAssessment(assessment({ claims: [claim('c1', ['t1']), claim('c2', ['t2'])] }))
    expect(withoutGeneral.text()).not.toContain('Tezy ogólne')
  })

  it('notes "podstawa stanowiska" only for the claims in that target\'s stance.claim_ids', () => {
    const [cdr, pko, general] = mountAssessment().findAll('section')
    const basis = (section: typeof cdr) => section!.findAll('.claim').map((item) => item.find('.basis').exists())
    expect(basis(cdr)).toEqual([true, false])
    expect(basis(pko)).toEqual([true])
    expect(basis(general)).toEqual([false])
  })

  it('shows each claim with its kind, attribution, statement and quote', () => {
    const claims = mountAssessment().findAll('.claim')
    expect(claims.map((item) => item.find('.claim-meta').text())).toEqual([
      'teza inwestycyjna · autor · podstawa stanowiska',
      'informacja · przytoczone',
      'informacja · przytoczone · podstawa stanowiska',
      'opinia · nie wiadomo czyje',
    ])
    expect(claims[0]!.find('blockquote').text()).toBe('cytat c1')
  })

  it('shows the relevance and each modifier as neutral badges, with the assessment date', () => {
    const wrapper = mountAssessment(assessment({ modifiers: ['humor', 'promotion'] }))
    expect(wrapper.findAll('.meta [data-tone="neutral"]').map((badge) => badge.text())).toEqual([
      'inwestycyjny',
      'humor',
      'promocja',
    ])
    expect(wrapper.find('.meta time').text()).toBe('2.10.2026, 10:00')
    expect(wrapper.find('.meta').text()).toContain('oceniono 2.10.2026, 10:00')
  })

  it('shows the flags as neutral badges under "Zastrzeżenia"', () => {
    const wrapper = mountAssessment(assessment({ flags: ['possible_sarcasm', 'missing_context'] }))
    const flags = wrapper.findAll('section').at(-1)!
    expect(flags.find('h3').text()).toBe('Zastrzeżenia')
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
    expect(wrapper.find('.note').text()).toBe('bez treści, bez oceny modelu')
    expect(wrapper.find('.empty').exists()).toBe(false)
  })
})

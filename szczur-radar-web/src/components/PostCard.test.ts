import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { PostItem, Target } from '../api/posts'
import { routes } from '../router'
import PostCard from './PostCard.vue'

function post(overrides: Partial<PostItem> = {}): PostItem {
  return {
    id: '1',
    source: 'wykop',
    channel: 'gielda',
    itemType: 'post',
    publishedAt: '2026-10-01T07:15:00Z',
    title: null,
    body: 'Wpis o XTB',
    author: 'uzytkownik1',
    webUrl: null,
    assessment: {
      output: {
        relevance: 'investment_related',
        modifiers: [],
        targets: [
          {
            id: 't1',
            kind: 'company',
            anchor: { document: 'item', field: 'body', quote: 'XTB' },
            stance: { direction: 'bullish', assertiveness: 'hedged', claim_ids: ['c1'] },
            reference: 'explicit',
          },
        ],
        claims: [
          {
            id: 'c1',
            kind: 'investment_thesis',
            attribution: 'author',
            target_ids: ['t1'],
            evidence: { document: 'item', field: 'body', quote: 'XTB' },
            statement: 'Teza testowa.',
          },
        ],
        flags: ['possible_sarcasm', 'missing_context'],
      },
      resultSource: 'model',
      assessedAt: '2026-10-02T08:00:00Z',
    },
    ...overrides,
  }
}

/** In the app's routes, so the heading's link resolves as it does in the feed. */
function mountCard(item: PostItem = post()) {
  const router = createRouter({ history: createMemoryHistory(), routes })
  return mount(PostCard, { props: { post: item }, global: { plugins: [router] } })
}

describe('PostCard', () => {
  it('shows the date and time in one time element', () => {
    const time = mountCard().findAll('time')
    expect(time).toHaveLength(1)
    expect(time[0]!.attributes('datetime')).toBe('2026-10-01T07:15:00Z')
    expect(time[0]!.text()).toBe('1.10.2026, 09:15')
  })

  it('links the heading, and only the heading, to the post', () => {
    const links = mountCard().findAll('a')
    expect(links).toHaveLength(1)
    expect(links[0]!.element.closest('h2')).not.toBeNull()
    expect(links[0]!.attributes('href')).toBe('/posts/1')
    expect(links[0]!.text()).toBe('Wpis o XTB')
  })

  it('shows the source and channel right after the date', () => {
    const origin = mountCard().find('.meta .origin')
    expect(origin.text()).toBe('Wykop · #gielda')
    expect(origin.element.previousElementSibling?.tagName).toBe('TIME')
  })

  it('shows a Reddit post with its subreddit', () => {
    const wrapper = mountCard(post({ source: 'reddit', channel: 'ValueInvesting' }))
    expect(wrapper.find('.origin').text()).toBe('Reddit · r/ValueInvesting')
  })

  it('shows the post type as the text of its label', () => {
    expect(mountCard().find('.type').text()).toBe('wpis')
  })

  it('shows the relevance as a neutral badge', () => {
    const badge = mountCard().find('.meta [data-tone="neutral"]')
    expect(badge.text()).toBe('inwestycyjny')
  })

  it('lists each target as a term with its stance', () => {
    const wrapper = mountCard()
    expect(wrapper.findAll('dl dt').map((term) => term.text())).toEqual(['XTB · spółka'])
    const stance = wrapper.find('dl dd')
    expect(stance.classes()).toContain('bullish')
    expect(stance.text()).toBe('wzrostowo (ostrożnie)')
  })

  it('shows the kind of each target after its quote, as secondary text', () => {
    const base = post()
    const output = base.assessment!.output
    const wrapper = mountCard(
      post({
        assessment: {
          ...base.assessment!,
          output: {
            ...output,
            targets: [
              { ...output.targets[0]!, id: 't1', kind: 'macro_topic', anchor: { ...output.targets[0]!.anchor, quote: 'rynek' } },
              { ...output.targets[0]!, id: 't2', kind: 'asset', anchor: { ...output.targets[0]!.anchor, quote: 'ropa' } },
            ],
          },
        },
      }),
    )
    const terms = wrapper.findAll('dl dt')
    expect(terms.map((term) => term.text())).toEqual(['rynek · makro i rynek', 'ropa · aktywo'])
    expect(terms[0]!.find('.kind').text()).toBe('· makro i rynek')
  })

  it('shows an unknown kind as it came', () => {
    const base = post()
    const output = base.assessment!.output
    // A value the contract may add later: the type does not know it, the card still shows it.
    const target = { ...output.targets[0]!, kind: 'crypto_exchange' } as unknown as Target
    const wrapper = mountCard(post({ assessment: { ...base.assessment!, output: { ...output, targets: [target] } } }))
    expect(wrapper.find('dl dt').text()).toBe('XTB · crypto_exchange')
  })

  it('shows each flag as a neutral badge with its Polish label', () => {
    const flags = mountCard().findAll('.flags [data-tone="neutral"]')
    expect(flags.map((flag) => flag.text())).toEqual(['możliwy sarkazm', 'brak kontekstu'])
  })

  it('notes a result decided without the model', () => {
    const base = post()
    const prefiltered = mountCard(post({ assessment: { ...base.assessment!, resultSource: 'prefilter' } }))
    expect(prefiltered.find('.note').text()).toBe('bez treści, bez oceny modelu')
    expect(mountCard().find('.note').exists()).toBe(false)
  })

  it('shows a post without an assessment with its date, type and heading, without a relevance badge', () => {
    const wrapper = mountCard(post({ assessment: null }))
    expect(wrapper.find('time').text()).toBe('1.10.2026, 09:15')
    expect(wrapper.find('.type').text()).toBe('wpis')
    expect(wrapper.find('h2').text()).toBe('Wpis o XTB')
    expect(wrapper.find('[data-tone]').exists()).toBe(false)
    expect(wrapper.find('dl').exists()).toBe(false)
  })
})

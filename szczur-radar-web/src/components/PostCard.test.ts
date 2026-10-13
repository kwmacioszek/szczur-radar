import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { PostItem } from '../api/posts'
import { routes } from '../router'
import PostCard from './PostCard.vue'

function post(overrides: Partial<PostItem> = {}): PostItem {
  return {
    id: '1',
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
            stance: { direction: 'bullish', assertiveness: 'hedged', claim_ids: [] },
            reference: 'explicit',
          },
        ],
        claims: [],
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

  it('shows the post type as the text of its label', () => {
    expect(mountCard().find('.type').text()).toBe('wpis')
  })

  it('shows the relevance as a neutral badge', () => {
    const badge = mountCard().find('.meta [data-tone="neutral"]')
    expect(badge.text()).toBe('inwestycyjny')
  })

  it('lists each target as a term with its stance', () => {
    const wrapper = mountCard()
    expect(wrapper.findAll('dl dt').map((term) => term.text())).toEqual(['XTB'])
    const stance = wrapper.find('dl dd')
    expect(stance.classes()).toContain('bullish')
    expect(stance.text()).toBe('wzrostowo (ostrożnie)')
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

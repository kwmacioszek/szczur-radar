import { type VueWrapper, enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { type PostItem, fetchPostRaw } from '../api/posts'
import PostInspector from './PostInspector.vue'

vi.mock('../api/posts', () => ({ fetchPostRaw: vi.fn() }))

const LONG_BODY =
  'CDR po wynikach -8% na otwarciu, ktoś jeszcze trzyma?\nJa dobieram, bo wycena po spadku wygląda rozsądnie, a guidance na Q4 nie zmieniło się mimo opóźnienia premiery.'

function post(overrides: Partial<PostItem> = {}): PostItem {
  return {
    id: '1',
    source: 'wykop',
    channel: 'gielda',
    itemType: 'post',
    publishedAt: '2026-10-01T07:15:00Z',
    title: null,
    body: LONG_BODY,
    author: 'uzytkownik1',
    webUrl: 'https://wykop.pl/wpis/81234567',
    assessment: {
      output: {
        relevance: 'investment_related',
        modifiers: [],
        targets: [
          {
            id: 't1',
            kind: 'company',
            anchor: { document: 'item', field: 'body', quote: 'CDR' },
            stance: { direction: 'bullish', assertiveness: 'hedged', claim_ids: ['c1'] },
            reference: 'explicit',
          },
        ],
        claims: [
          {
            id: 'c1',
            kind: 'trade_action',
            attribution: 'author',
            target_ids: ['t1'],
            evidence: { document: 'item', field: 'body', quote: 'Ja dobieram' },
            statement: 'Autor dokupuje akcje CDR po spadku.',
          },
        ],
        flags: [],
      },
      resultSource: 'model',
      assessedAt: '2026-10-02T08:00:00Z',
    },
    ...overrides,
  }
}

const RAW = '{\n  "id" : 81234567,\n  "content" : "Zażółć gęślą jaźń"\n}'

function never<T>(): Promise<T> {
  return new Promise(() => {})
}

/** `attachTo`: in the document, for focus. */
function mountInspector(item: PostItem = post(), attachTo?: HTMLElement) {
  return mount(PostInspector, { props: { post: item, headingId: 'post-title' }, attachTo })
}

function rawButton(wrapper: VueWrapper) {
  const found = wrapper.findAll('button').find((candidate) => candidate.text() === 'Surowe dane')
  if (!found) throw new Error('no button "Surowe dane"')
  return found
}

enableAutoUnmount(afterEach)

describe('PostInspector', () => {
  beforeEach(() => {
    vi.mocked(fetchPostRaw).mockReset()
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it.each([
    ['the title', post({ itemType: 'link', title: 'Orlen ogłasza buyback za 4 mld zł' }), 'Orlen ogłasza buyback za 4 mld zł'],
    ['the type and author without a title', post(), 'Wpis użytkownika uzytkownik1'],
    ['the type alone without an author', post({ itemType: 'comment', author: null }), 'Komentarz'],
    ["Reddit's word without a title", post({ source: 'reddit', channel: 'stocks', itemType: 'link' }), 'Link użytkownika uzytkownik1'],
  ])('heads the post with %s, a level-2 heading the panel is named by and focuses', (_, item, heading) => {
    const title = mountInspector(item).find('h2')
    expect(title.text()).toBe(heading)
    expect(title.attributes('id')).toBe('post-title')
    expect(title.attributes('tabindex')).toBe('-1')
  })

  it('shows the author and the full text with its line breaks', () => {
    const wrapper = mountInspector()
    expect(wrapper.find('.author').text()).toBe('uzytkownik1')
    expect(wrapper.find('.body').element.textContent).toBe(LONG_BODY)
  })

  it('links the original in a new tab, named with its site', () => {
    const link = mountInspector().find('a.original')
    expect(link.text()).toBe('Otwórz oryginał')
    expect(link.attributes('href')).toBe('https://wykop.pl/wpis/81234567')
    expect(link.attributes('target')).toBe('_blank')
    expect(link.attributes('rel')).toBe('noopener noreferrer')
    expect(link.attributes('aria-label')).toBe('Otwórz oryginał na wykop.pl (otwiera się w nowej karcie)')
    expect(link.find('svg').attributes('aria-hidden')).toBe('true')
  })

  it.each([null, 'javascript:alert(1)', 'ftp://wykop.pl/wpis/1', 'not a url'])(
    'has no link to the original for the address %s',
    (webUrl) => {
      const wrapper = mountInspector(post({ webUrl }))
      expect(wrapper.find('a.original').exists()).toBe(false)
      expect(wrapper.find('h2').text()).toBe('Wpis użytkownika uzytkownik1')
    },
  )

  it('heads the assessment "Ocena" at level 3, with its relevance and date, and its targets at level 4', () => {
    const wrapper = mountInspector()
    const section = wrapper.find('section.assessment')
    expect(section.find('h3').text()).toBe('Ocena')
    expect(section.attributes('aria-labelledby')).toBe(section.find('h3').attributes('id'))
    expect(section.find('.assessment-meta').text()).toBe('Post inwestycyjny. Oceniono 2.10.2026, 10:00.')
    expect(section.find('h4').text()).toBe('CDR')
    expect(section.text()).toContain('Autor dokupuje akcje CDR po spadku.')
  })

  it.each([
    ['not_investment_related', 'Post nie inwestycyjny.'],
    ['unclear', 'Post niejasny.'],
  ] as const)('says a %s post is so', (relevance, text) => {
    const item = post()
    const wrapper = mountInspector({ ...item, assessment: { ...item.assessment!, output: { ...item.assessment!.output, relevance } } })
    expect(wrapper.find('.assessment-meta').text()).toContain(text)
  })

  it('says a post without an assessment has none', () => {
    const wrapper = mountInspector(post({ assessment: null }))
    expect(wrapper.find('.post-assessment').exists()).toBe(false)
    expect(wrapper.find('.assessment-meta').exists()).toBe(false)
    expect(wrapper.find('.none').text()).toBe('Brak oceny.')
  })

  it('says what the assessment is not', () => {
    expect(mountInspector().find('.assessment-foot').text()).toContain(
      'Ocena opisuje stanowisko autora posta. Nie jest prognozą ani rekomendacją.',
    )
  })

  describe('source data', () => {
    it('is collapsed and fetched only on the first opening, then shown in its own region', async () => {
      vi.mocked(fetchPostRaw).mockResolvedValue(RAW)
      const wrapper = mountInspector()
      const button = rawButton(wrapper)
      expect(button.attributes('aria-expanded')).toBe('false')
      expect(fetchPostRaw).not.toHaveBeenCalled()

      await button.trigger('click')
      await flushPromises()

      expect(button.attributes('aria-expanded')).toBe('true')
      expect(fetchPostRaw).toHaveBeenCalledExactlyOnceWith('1')
      const region = wrapper.find('pre')
      expect(region.attributes('role')).toBe('region')
      expect(region.attributes('aria-label')).toBe('Surowe dane posta')
      expect(region.attributes('tabindex')).toBe('0')
      expect(region.element.textContent).toBe(RAW)
      expect(button.attributes('aria-controls')).toBe(region.element.parentElement?.id)

      await button.trigger('click')
      expect(button.attributes('aria-expanded')).toBe('false')
      await button.trigger('click')
      await flushPromises()
      expect(fetchPostRaw).toHaveBeenCalledTimes(1)
    })

    it('shows a skeleton while it loads the data', async () => {
      vi.mocked(fetchPostRaw).mockReturnValue(never())
      const wrapper = mountInspector()
      await rawButton(wrapper).trigger('click')
      const skeleton = wrapper.find('.raw-area .loading-state')
      expect(skeleton.classes()).toContain('lines')
      expect(skeleton.text()).toBe('Wczytywanie…')
    })

    it('offers a retry after an error', async () => {
      vi.mocked(fetchPostRaw).mockRejectedValueOnce(new TypeError('Failed to fetch')).mockResolvedValueOnce(RAW)
      const wrapper = mountInspector()
      await rawButton(wrapper).trigger('click')
      await flushPromises()
      const alert = wrapper.find('.raw-area [role="alert"]')
      // A paragraph, not a heading: the section has its own.
      expect(alert.find('p.state-title').text()).toBe('Nie udało się wczytać danych źródła')
      expect(alert.find('.state-text').text()).toBe('Ocena posta zostaje bez zmian.')

      await alert.find('button').trigger('click')
      await flushPromises()

      expect(fetchPostRaw).toHaveBeenCalledTimes(2)
      expect(wrapper.find('.raw-area [role="alert"]').exists()).toBe(false)
      expect(wrapper.find('pre').element.textContent).toBe(RAW)
    })

    it('keeps the error busy during the retry, with the focus on "Surowe dane"', async () => {
      vi.mocked(fetchPostRaw).mockRejectedValueOnce(new TypeError('Failed to fetch')).mockReturnValue(never())
      const wrapper = mountInspector(post(), document.body)
      await rawButton(wrapper).trigger('click')
      await flushPromises()

      await wrapper.find('.raw-area button').trigger('click')
      await flushPromises()

      const error = wrapper.find('.raw-area .state-error')
      expect(error.attributes('role')).toBeUndefined()
      expect(error.find('button').attributes('aria-busy')).toBe('true')
      expect(wrapper.find('.raw-area .loading-state').exists()).toBe(false)
      expect(document.activeElement).toBe(rawButton(wrapper).element)
    })
  })
})

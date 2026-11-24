import { enableAutoUnmount, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { PostItem, Target } from '../api/posts'
import { routes } from '../router'
import PostRow from './PostRow.vue'

enableAutoUnmount(afterEach)

function target(id: string, quote: string, overrides: Partial<Target> = {}): Target {
  return {
    id,
    kind: 'company',
    anchor: { document: 'item', field: 'body', quote },
    stance: { direction: 'bullish', assertiveness: 'hedged', claim_ids: ['c1'] },
    reference: 'explicit',
    ...overrides,
  }
}

function post(overrides: Partial<PostItem> = {}, targets: Target[] = [target('t1', 'XTB')]): PostItem {
  return {
    id: '1',
    source: 'wykop',
    channel: 'gielda',
    itemType: 'post',
    publishedAt: '2026-10-20T07:41:00Z',
    title: null,
    body: 'Wpis o XTB',
    author: 'uzytkownik1',
    webUrl: null,
    assessment: {
      output: { relevance: 'investment_related', modifiers: [], targets, claims: [], flags: [] },
      resultSource: 'model',
      assessedAt: '2026-10-20T08:00:00Z',
    },
    ...overrides,
  }
}

/** `post` with its assessment's output changed. */
function withOutput(overrides: Partial<NonNullable<PostItem['assessment']>['output']>, base = post()): PostItem {
  return { ...base, assessment: { ...base.assessment!, output: { ...base.assessment!.output, ...overrides } } }
}

/** In a list and in the app's routes, so the row's link resolves as it does on the list. */
function mountRow(item: PostItem = post(), selected = false) {
  const router = createRouter({ history: createMemoryHistory(), routes })
  return mount(
    { components: { PostRow }, template: '<ol><PostRow :post="post" :to="to" :selected="selected" /></ol>' },
    {
      data: () => ({ post: item, to: { name: 'post', params: { id: item.id } }, selected }),
      global: { plugins: [router] },
    },
  )
}

describe('PostRow', () => {
  it('is an item of the list with one link to the post, around the whole row', () => {
    const wrapper = mountRow()
    const item = wrapper.get('li')
    const links = item.findAll('a')
    expect(links).toHaveLength(1)
    expect(links[0]!.attributes('href')).toBe('/posts/1')
    expect(links[0]!.element.parentElement).toBe(item.element)
    expect(links[0]!.text()).toContain('Wpis o XTB')
  })

  it('shows the Warsaw hour, then the source and channel, then the type for a screen reader', () => {
    const meta = mountRow().get('.meta')
    const time = meta.get('time')
    expect(time.attributes('datetime')).toBe('2026-10-20T07:41:00Z')
    expect(time.text()).toBe('09:41')
    const [first, origin, type] = meta.element.children
    expect(first).toBe(time.element)
    expect(origin!.textContent).toBe('Wykop · #gielda')
    expect(type!.textContent).toBe('wpis')
    expect(type!.classList).toContain('visually-hidden')
  })

  it.each([
    ['reddit', 'stocks', 'link', 'Reddit · r/stocks', 'link'],
    ['wykop', 'gielda', 'link', 'Wykop · #gielda', 'znalezisko'],
  ])('shows a %s %s in the words of its source', (source, channel, itemType, origin, word) => {
    const meta = mountRow(post({ source, channel, itemType } as Partial<PostItem>)).get('.meta')
    expect(meta.get('.origin').text()).toBe(origin)
    expect(meta.get('.visually-hidden').text()).toBe(word)
  })

  it('shows no origin for a post of an older API, deployed apart from the web', () => {
    const wrapper = mountRow(post({ source: undefined, channel: undefined } as unknown as Partial<PostItem>))
    expect(wrapper.find('.origin').exists()).toBe(false)
    expect(wrapper.get('.meta').text()).not.toContain('undefined')
  })

  it('shows the title as a title', () => {
    const title = mountRow(post({ title: 'Orlen ogłasza buyback za 4 mld zł', body: 'Treść' })).get('.title')
    expect(title.text()).toBe('Orlen ogłasza buyback za 4 mld zł')
    expect(title.classes()).not.toContain('excerpt')
  })

  it('shows the start of the text in place of a missing title, not as a title', () => {
    const title = mountRow().get('.title')
    expect(title.text()).toBe('Wpis o XTB')
    expect(title.classes()).toContain('excerpt')
  })

  it('cuts a long text at 200 characters', () => {
    const title = mountRow(post({ body: 'a'.repeat(250) })).get('.title')
    expect(title.text()).toBe(`${'a'.repeat(200)}…`)
  })

  it('shows a target with its kind and stance', () => {
    const stances = mountRow().findAll('.targets .stance')
    expect(stances.map((stance) => stance.text())).toEqual(['XTBspółkawzrostowo (ostrożnie)'])
    expect(stances[0]!.classes()).toContain('bullish')
  })

  it('shows two targets in the order of the model, with no "i N inne"', () => {
    const wrapper = mountRow(post({}, [target('t1', 'XTB'), target('t2', 'ropa', { kind: 'asset' })]))
    expect(wrapper.findAll('.targets .t-name').map((name) => name.text())).toEqual(['XTB', 'ropa'])
    expect(wrapper.findAll('.targets .kind').map((kind) => kind.text())).toEqual(['spółka', 'aktywo'])
    expect(wrapper.get('.targets').text()).not.toContain(' inn')
  })

  it.each([
    [3, 'i 1 inny'],
    [4, 'i 2 inne'],
    [5, 'i 3 inne'],
    [7, 'i 5 innych'],
  ])('shows the first two of %i targets and counts the rest: %s', (count, rest) => {
    const targets = Array.from({ length: count }, (_, index) => target(`t${index}`, `Cel ${index}`))
    const wrapper = mountRow(post({}, targets))
    expect(wrapper.findAll('.targets .t-name').map((name) => name.text())).toEqual(['Cel 0', 'Cel 1'])
    expect(wrapper.get('.targets .note').text()).toBe(rest)
  })

  it('shows each flag after the targets, in the warning tone', () => {
    const wrapper = mountRow(withOutput({ flags: ['possible_sarcasm', 'missing_context'] }))
    const flags = wrapper.findAll('.targets .flag')
    expect(flags.map((flag) => flag.text())).toEqual(['możliwy sarkazm', 'brak kontekstu'])
    expect(flags[0]!.element.previousElementSibling?.classList).toContain('stance')
  })

  it('says a post is not about investing', () => {
    const wrapper = mountRow(withOutput({ relevance: 'not_investment_related' }))
    expect(wrapper.get('.targets').text()).toContain('nie inwestycyjny')
    expect(mountRow().get('.targets').text()).not.toContain('inwestycyjny')
  })

  it('says a model found no targets', () => {
    const wrapper = mountRow(withOutput({ targets: [], flags: ['missing_context'] }))
    expect(wrapper.find('.targets .stance').exists()).toBe(false)
    expect(wrapper.findAll('.targets > span').map((part) => part.text())).toEqual(['brak kontekstu', 'bez celów'])
  })

  it('shows a result of the prefilter with its own flag, not "bez celów"', () => {
    const base = post()
    const prefiltered = mountRow({
      ...base,
      assessment: {
        output: { relevance: 'unclear', modifiers: [], targets: [], claims: [], flags: ['insufficient_content'] },
        resultSource: 'prefilter',
        assessedAt: base.assessment!.assessedAt,
      },
    })
    expect(prefiltered.findAll('.targets > span').map((part) => part.text())).toEqual(['za mało treści'])
  })

  it('shows only the hour, origin and title of a post without an assessment', () => {
    const wrapper = mountRow(post({ assessment: null }))
    expect(wrapper.find('.targets').exists()).toBe(false)
    expect(wrapper.get('.title').text()).toBe('Wpis o XTB')
    expect(wrapper.get('time').text()).toBe('09:41')
  })

  it('marks the selected row as the current one, and no other', () => {
    expect(mountRow(post(), true).get('a').attributes('aria-current')).toBe('true')
    expect(mountRow(post(), false).get('a').attributes('aria-current')).toBeUndefined()
  })
})

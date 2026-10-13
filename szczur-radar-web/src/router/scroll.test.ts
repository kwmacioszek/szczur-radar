import { afterEach, describe, expect, it, vi } from 'vitest'
import type { RouteLocationNormalized, RouteLocationNormalizedLoaded } from 'vue-router'
import { RESTORE_TIMEOUT_MS, reportFeedLoaded, scrollBehavior } from './scroll'

/** Only what scrollBehavior reads: the route's name and path. */
function at(name: 'home' | 'post' | 'admin', path: string): RouteLocationNormalized & RouteLocationNormalizedLoaded {
  return { name, path } as RouteLocationNormalized & RouteLocationNormalizedLoaded
}

const SAVED = { left: 0, top: 1840 }

/** Settles the behaviour's promise before the test looks at it. */
async function settled<T>(promise: Promise<T>): Promise<{ done: boolean; value?: T }> {
  let state: { done: boolean; value?: T } = { done: false }
  void promise.then((value) => (state = { done: true, value }))
  await Promise.resolve()
  await Promise.resolve()
  return state
}

describe('scrollBehavior', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('back from a post to the feed restores the position once the feed reports its page', async () => {
    const result = Promise.resolve(scrollBehavior(at('home', '/'), at('post', '/posts/1'), SAVED))
    expect((await settled(result)).done).toBe(false)

    reportFeedLoaded('ok')

    await expect(result).resolves.toEqual(SAVED)
  })

  it('back from a post to the feed focuses the link of the post just left, once the feed reports its page', async () => {
    const link = document.createElement('a')
    link.setAttribute('href', '/posts/1')
    document.body.append(link)
    try {
      const result = Promise.resolve(scrollBehavior(at('home', '/'), at('post', '/posts/1'), SAVED))
      expect(document.activeElement).not.toBe(link)

      reportFeedLoaded('ok')

      await expect(result).resolves.toEqual(SAVED)
      expect(document.activeElement).toBe(link)
    } finally {
      link.remove()
    }
  })

  it('leaves the focus alone when the feed fails to load', async () => {
    const link = document.createElement('a')
    link.setAttribute('href', '/posts/1')
    document.body.append(link)
    try {
      const result = Promise.resolve(scrollBehavior(at('home', '/'), at('post', '/posts/1'), SAVED))
      reportFeedLoaded('error')
      await expect(result).resolves.toBe(false)
      expect(document.activeElement).not.toBe(link)
    } finally {
      link.remove()
    }
  })

  it('back from a post to the feed does not scroll when the feed fails to load', async () => {
    const result = Promise.resolve(scrollBehavior(at('home', '/'), at('post', '/posts/1'), SAVED))
    reportFeedLoaded('error')
    await expect(result).resolves.toBe(false)
  })

  it('gives up restoring when the feed does not report in time', async () => {
    vi.useFakeTimers()
    const result = Promise.resolve(scrollBehavior(at('home', '/'), at('post', '/posts/1'), SAVED))
    vi.advanceTimersByTime(RESTORE_TIMEOUT_MS)
    await expect(result).resolves.toBe(false)
  })

  it('waits for the first report after the navigation, not an earlier one', async () => {
    reportFeedLoaded('ok')
    const result = Promise.resolve(scrollBehavior(at('home', '/'), at('post', '/posts/1'), SAVED))
    expect((await settled(result)).done).toBe(false)
    reportFeedLoaded('error')
    await expect(result).resolves.toBe(false)
  })

  it('goes to the top on another path', async () => {
    await expect(Promise.resolve(scrollBehavior(at('post', '/posts/1'), at('home', '/'), null))).resolves.toEqual({
      top: 0,
    })
    await expect(Promise.resolve(scrollBehavior(at('admin', '/admin'), at('post', '/posts/1'), SAVED))).resolves.toEqual({
      top: 0,
    })
  })

  it('does nothing on the same path with another ?page: the feed scrolls itself', async () => {
    await expect(Promise.resolve(scrollBehavior(at('home', '/'), at('home', '/'), SAVED))).resolves.toBe(false)
    await expect(Promise.resolve(scrollBehavior(at('home', '/'), at('home', '/'), null))).resolves.toBe(false)
  })
})

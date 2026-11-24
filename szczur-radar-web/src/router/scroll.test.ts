import { describe, expect, it } from 'vitest'
import type { RouteLocationNormalized, RouteLocationNormalizedLoaded } from 'vue-router'
import { scrollBehavior } from './scroll'

/** Only what scrollBehavior reads: the route's name and path. */
function at(name: 'posts' | 'post' | 'admin', path: string): RouteLocationNormalized & RouteLocationNormalizedLoaded {
  return { name, path } as RouteLocationNormalized & RouteLocationNormalizedLoaded
}

const SAVED = { left: 0, top: 1840 }

describe('scrollBehavior', () => {
  it.each([
    ['opening a post over the list', at('post', '/posts/1'), at('posts', '/posts')],
    ['closing it', at('posts', '/posts'), at('post', '/posts/1')],
    ['opening another post', at('post', '/posts/2'), at('post', '/posts/1')],
  ])('does not scroll when %s: the list stays under the panel', async (_, to, from) => {
    await expect(Promise.resolve(scrollBehavior(to, from, null))).resolves.toBe(false)
    await expect(Promise.resolve(scrollBehavior(to, from, SAVED))).resolves.toBe(false)
  })

  it('goes to the top on another path', async () => {
    await expect(Promise.resolve(scrollBehavior(at('admin', '/admin'), at('post', '/posts/1'), SAVED))).resolves.toEqual({
      top: 0,
    })
    await expect(Promise.resolve(scrollBehavior(at('posts', '/posts'), at('admin', '/admin'), SAVED))).resolves.toEqual({
      top: 0,
    })
  })

  it('does nothing on the same path with another ?page: the list scrolls itself', async () => {
    await expect(Promise.resolve(scrollBehavior(at('posts', '/posts'), at('posts', '/posts'), SAVED))).resolves.toBe(false)
    await expect(Promise.resolve(scrollBehavior(at('posts', '/posts'), at('posts', '/posts'), null))).resolves.toBe(false)
  })
})

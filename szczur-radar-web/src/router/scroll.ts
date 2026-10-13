// The router's scroll behaviour. Defining one makes vue-router set `history.scrollRestoration =
// 'manual'` for the whole app, so the browser restores nothing and every case is decided here:
// - back (or forward) from a post to the feed with a saved position: that position, but only once
//   the feed has loaded its page (FeedView reports it), so the list is there to scroll to; nothing
//   after an error or when no report comes within RESTORE_TIMEOUT_MS;
// - another path: the top of the page;
// - the same path (the feed's `?page`): nothing, FeedView scrolls and moves the focus itself.
import type { RouterScrollBehavior } from 'vue-router'

/** How a feed load ended, as FeedView reports it. */
export type FeedLoadOutcome = 'ok' | 'error'

/** How long a return to the feed waits for its page before it gives up restoring the position. */
export const RESTORE_TIMEOUT_MS = 5000

type Waiter = (outcome: FeedLoadOutcome | 'timeout') => void

/** The return to the feed that waits for the next report; a later navigation replaces it. */
let waiting: Waiter | null = null

/** FeedView: a load ended, its list on screen (`ok`) or its error shown (`error`). */
export function reportFeedLoaded(outcome: FeedLoadOutcome): void {
  waiting?.(outcome)
}

/** The first report after this call, or `timeout`. */
function nextFeedLoad(): Promise<FeedLoadOutcome | 'timeout'> {
  waiting?.('timeout')
  return new Promise((resolve) => {
    const finish: Waiter = (outcome) => {
      clearTimeout(timer)
      if (waiting === finish) waiting = null
      resolve(outcome)
    }
    const timer = setTimeout(() => finish('timeout'), RESTORE_TIMEOUT_MS)
    waiting = finish
  })
}

export const scrollBehavior: RouterScrollBehavior = async (to, from, savedPosition) => {
  if (savedPosition && from.name === 'post' && to.name === 'home') {
    return (await nextFeedLoad()) === 'ok' ? savedPosition : false
  }
  if (to.path !== from.path) return { top: 0 }
  return false
}

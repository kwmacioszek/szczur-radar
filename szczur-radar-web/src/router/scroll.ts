// The router's scroll behaviour. Defining one makes vue-router set `history.scrollRestoration =
// 'manual'` for the whole app, so the browser restores nothing and every case is decided here:
// - the same path (the list's `?page`): nothing, FeedView scrolls and moves the focus itself;
// - both ends on the posts screen (a post opened in its panel over the list, or closed): nothing, the list stays mounted
//   under the panel, where it was (router/screens.ts);
// - another path: the top of the page.
import type { RouterScrollBehavior } from 'vue-router'
import { withinPostsScreen } from './screens'

export const scrollBehavior: RouterScrollBehavior = (to, from) => {
  if (to.path === from.path || withinPostsScreen(to, from)) return false
  return { top: 0 }
}

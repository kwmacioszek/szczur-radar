// The screens of the router that more than one place has to tell apart. The posts screen is the list (`posts`) and a
// post open in its panel over the list (`post`, the list's child route): moving between them neither scrolls the page
// (router/scroll.ts) nor reads the admin's notifications again (components/NotificationBell.vue), because the list stays
// on the screen.
import type { RouteLocationNormalized } from 'vue-router'

/** The route is the list of posts or a post open over it. */
export function isPostsScreen(route: Pick<RouteLocationNormalized, 'name'>): boolean {
  return route.name === 'posts' || route.name === 'post'
}

/** Both ends of a navigation are on the posts screen: a post opened over the list, closed, or another one opened. */
export function withinPostsScreen(
  to: Pick<RouteLocationNormalized, 'name'>,
  from: Pick<RouteLocationNormalized, 'name'>,
): boolean {
  return isPostsScreen(to) && isPostsScreen(from)
}

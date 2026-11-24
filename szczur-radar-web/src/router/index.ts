// Every route except /login, /activate (and the dev-only /kitchen-sink) requires a session; a logged-in
// user never sees /login, and only an admin sees /admin and /admin/runs/:id. /activate stays open to a
// logged-in user: the link may be for another account (ActivateView ends that session after setting the
// password).
// History mode: Caddy (and Vite) serve index.html for deep links.
import {
  createRouter,
  createWebHistory,
  type RouteRecordRaw,
  type Router,
  type RouterHistory,
} from 'vue-router'
import { onUnauthorized } from '../api/client'
import { ensureSession } from '../auth/session'
import AppShell from '../components/AppShell.vue'
import ActivateView from '../views/ActivateView.vue'
import AdminView from '../views/AdminView.vue'
import FeedView from '../views/FeedView.vue'
import LoginView from '../views/LoginView.vue'
import PostPanel from '../views/PostPanel.vue'
import RunDetailView from '../views/RunDetailView.vue'
import { scrollBehavior } from './scroll'

export { safeRedirect } from './redirect'

/** The item of the main navigation a page belongs to (components/AppShell.vue marks it with aria-current). */
export type AppSection = 'posts' | 'admin'

/** The width of a shell page's column: the shell sets it (components/AppShell.vue), a view has no container of its own. */
export type PageColumn = 'narrow' | 'wide'

declare module 'vue-router' {
  interface RouteMeta {
    /** Reachable without a session. */
    public?: boolean
    /** Only for the admin role; others go to the list of posts (the API answers 403 anyway). */
    admin?: boolean
    /** The navigation item of the shell's pages; the other routes have none. */
    section?: AppSection
    /** The width of the shell page's column; without it the page spans the shell's whole column. */
    column?: PageColumn
  }
}

export const routes: RouteRecordRaw[] = [
  { path: '/login', name: 'login', component: LoginView, meta: { public: true } },
  // The link of the operator's CLI: /activate#<token>, the token only in the fragment.
  { path: '/activate', name: 'activate', component: ActivateView, meta: { public: true } },
  // The logged-in pages share one shell, mounted once: the bar on top, the page in its column. `to.meta` merges the
  // parent's and the child's, so the guard below reads `admin` as before.
  {
    path: '/',
    component: AppShell,
    children: [
      // The day's overview comes here with S-59; until then the old address of the list leads to it, its filter and
      // page kept.
      { path: '', redirect: (to) => ({ name: 'posts', query: to.query }) },
      // The compact list spans the shell's whole column (ui-design.md, "Szeroki i wąski ekran"): no `column`. A post
      // opens in a panel over the list, which stays mounted under it: its route is the list's child, which FeedView
      // renders with the rows of its page (views/PostPanel.vue).
      {
        path: 'posts',
        name: 'posts',
        component: FeedView,
        meta: { section: 'posts' },
        children: [{ path: ':id', name: 'post', component: PostPanel, meta: { section: 'posts' } }],
      },
      { path: 'admin', name: 'admin', component: AdminView, meta: { admin: true, section: 'admin', column: 'wide' } },
      {
        path: 'admin/runs/:id',
        name: 'admin-run',
        component: RunDetailView,
        meta: { admin: true, section: 'admin', column: 'narrow' },
      },
    ],
  },
  // Every component in every state, for screenshots. `npm run build` replaces import.meta.env.DEV
  // with false, so the route and its chunk are not in dist/.
  ...(import.meta.env.DEV
    ? [
        {
          path: '/kitchen-sink',
          name: 'kitchen-sink',
          component: () => import('../views/KitchenSinkView.vue'),
          meta: { public: true },
        },
      ]
    : []),
  { path: '/:pathMatch(.*)*', redirect: '/' },
]

export function createAppRouter(history: RouterHistory = createWebHistory()): Router {
  const router = createRouter({ history, routes, scrollBehavior })

  router.beforeEach(async (to) => {
    const user = await ensureSession()
    if (!to.meta.public && !user) {
      return { name: 'login', query: { redirect: to.fullPath } }
    }
    if (to.name === 'login' && user) {
      return { path: '/' }
    }
    if (to.meta.admin && user?.role !== 'admin') {
      return { path: '/' }
    }
    return true
  })

  // A session that expires while the app is open: back to the login screen.
  onUnauthorized(() => {
    const current = router.currentRoute.value
    if (!current.meta.public) {
      void router.push({ name: 'login', query: { redirect: current.fullPath } })
    }
  })

  return router
}

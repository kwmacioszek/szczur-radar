// Every route except /login, /activate (and the dev-only /kitchen-sink) requires a session; a logged-in
// user never sees /login, and only an admin sees /admin, its parts /admin/quality and /admin/posts, and
// /admin/runs/:id. /activate stays open to a logged-in user: the link may be for another account (ActivateView
// ends that session after setting the password).
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
import FeedView from '../views/FeedView.vue'
import LoginView from '../views/LoginView.vue'
import PostDetailView from '../views/PostDetailView.vue'
import RunDetailView from '../views/RunDetailView.vue'
import AdminFailedPostsView from '../views/admin/AdminFailedPostsView.vue'
import AdminPanelView from '../views/admin/AdminPanelView.vue'
import AdminQualityView from '../views/admin/AdminQualityView.vue'
import AdminRunsView from '../views/admin/AdminRunsView.vue'
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
    /** Only for the admin role; others go to the feed (the API answers 403 anyway). */
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
      { path: '', name: 'home', component: FeedView, meta: { section: 'posts', column: 'narrow' } },
      { path: 'posts/:id', name: 'post', component: PostDetailView, meta: { section: 'posts', column: 'narrow' } },
      // The admin panel: one layout (its heading and the bar of its parts) around the part of each address; the
      // children inherit its meta. A run's page is not a part: it has its own heading.
      {
        path: 'admin',
        component: AdminPanelView,
        meta: { admin: true, section: 'admin', column: 'wide' },
        children: [
          { path: '', name: 'admin', component: AdminRunsView },
          { path: 'quality', name: 'admin-quality', component: AdminQualityView },
          { path: 'posts', name: 'admin-posts', component: AdminFailedPostsView },
        ],
      },
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

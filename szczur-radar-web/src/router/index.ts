// Every route except /login requires a session; a logged-in user never sees
// /login. History mode: Caddy (and Vite) serve index.html for deep links.
import {
  createRouter,
  createWebHistory,
  type RouteRecordRaw,
  type Router,
  type RouterHistory,
} from 'vue-router'
import { onUnauthorized } from '../api/client'
import { ensureSession } from '../auth/session'
import HomeView from '../views/HomeView.vue'
import LoginView from '../views/LoginView.vue'

export { safeRedirect } from './redirect'

declare module 'vue-router' {
  interface RouteMeta {
    /** Reachable without a session. */
    public?: boolean
  }
}

export const routes: RouteRecordRaw[] = [
  { path: '/login', name: 'login', component: LoginView, meta: { public: true } },
  { path: '/', name: 'home', component: HomeView },
  { path: '/:pathMatch(.*)*', redirect: '/' },
]

export function createAppRouter(history: RouterHistory = createWebHistory()): Router {
  const router = createRouter({ history, routes })

  router.beforeEach(async (to) => {
    const user = await ensureSession()
    if (!to.meta.public && !user) {
      return { name: 'login', query: { redirect: to.fullPath } }
    }
    if (to.name === 'login' && user) {
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

// What the admin panel's layout (AdminPanelView) gives the part it shows: the count of failed posts in its bar, read
// again on a part's request, and the focus on the panel's heading once the part's first section is on screen.
import { type InjectionKey, inject } from 'vue'

export interface AdminPanel {
  /** Reads the count of failed posts in the bar again (after a restore removed posts from the list). */
  refreshFailedCount(): void
  /**
   * Puts the focus on the h1 "Panel admina" — only the first call after the layout mounted (the entry into the panel),
   * and none after a part change through the bar, where the focus stays on the clicked link.
   */
  focusTitle(): void
}

export const ADMIN_PANEL_KEY: InjectionKey<AdminPanel> = Symbol('admin-panel')

/**
 * The panel of the part that calls it (in its setup), with `sectionLoaded`: called whenever a section's read settles,
 * it asks for the focus on the title only once in the part's life, after the first one.
 */
export function useAdminPanel(): { refreshFailedCount(): void; sectionLoaded(): void } {
  const panel = inject(ADMIN_PANEL_KEY)
  if (!panel) throw new Error('A part of the admin panel renders inside AdminPanelView.')
  let loaded = false
  return {
    refreshFailedCount: () => panel.refreshFailedCount(),
    sectionLoaded(): void {
      if (loaded) return
      loaded = true
      panel.focusTitle()
    },
  }
}

// What every section of the admin panel's parts shares (S-48): who started a read and the focus its own retry owes.
import { type ShallowRef, nextTick } from 'vue'

/**
 * Every section reads its own data and has its own states (the shared ones): a skeleton while it loads with nothing on
 * screen, an error with its own "Spróbuj ponownie", an empty list. A section's error stays on screen, its button busy,
 * until the answer of a new read (`xRetrying`), whoever started it. `by` says who started a read: `retry` is that
 * button, `action` another control the user pressed ("Odśwież", "Uruchom", "Przywróć martwe"), none a read nobody
 * pressed for (mount, a run that ended, the poll).
 */
export type Read = { by?: 'retry' | 'action' }

/**
 * The focus a section's own retry owes: its busy button lost the focus, so the latest read of the section to settle
 * puts it on the heading (`tabindex="-1"`) after a success, or back on "Spróbuj ponownie" after another failure, also
 * when a read nobody pressed for superseded the retry. A read from another control leaves the focus on that control.
 */
export function sectionFocus(
  heading: Readonly<ShallowRef<HTMLElement | null>>,
  errorState: Readonly<ShallowRef<{ focus(): void } | null>>,
) {
  let owed = false
  return {
    claim({ by }: Read): void {
      if (by === 'retry') owed = true
      else if (by === 'action') owed = false
    },
    async settle(ok: boolean): Promise<void> {
      if (!owed) return
      owed = false
      await nextTick()
      if (ok) heading.value?.focus()
      else errorState.value?.focus()
    },
  }
}
